package com.civileg.app

import android.app.UiAutomation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.civileg.app.ui.theme.ArabicUiFontFamily
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Typography evidence on the real MainActivity. Output: filesDir/drawing-samples/typography.
 * For each Design Hub page (Stair excluded, it has its own sample) in Arabic:
 *  - portrait and landscape screenshots (landscape asserted from the screenshot's own size);
 *  - every Arabic text layout on screen is checked for the bundled IBM Plex Sans Arabic family.
 * Also covers the in-app language switch: English must not use Plex, switching back to Arabic must, and the
 * choice must be persisted. Screenshots are evidence for human reading; the assertions do not judge layout.
 */
class TypographySampleUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val out by lazy { File(context.filesDir, "drawing-samples/typography").apply { mkdirs() } }

    private fun str(lang: String, @StringRes id: Int): String {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale(lang))
        return context.createConfigurationContext(config).getString(id)
    }
    private fun ar(@StringRes id: Int) = str("ar", id)

    private fun isArabic(text: String) = text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC }

    private fun scroll(text: String) {
        rule.waitUntil(20000) {
            runCatching { rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text)) }
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    /** Only named SystemUI / Pixel Launcher prompts are dismissed. Any other ANR, including Civil's, fails. */
    private fun assertNoBlockingAnr() {
        val root = instrumentation.uiAutomation.rootInActiveWindow
        if (root != null) {
            val launcher = root.findAccessibilityNodeInfosByText("Pixel Launcher isn't responding").isNotEmpty()
            val systemUi = root.findAccessibilityNodeInfosByText("System UI isn't responding").isNotEmpty()
            if (launcher || systemUi) {
                root.findAccessibilityNodeInfosByText(if (systemUi) "Wait" else "Close app").firstOrNull()
                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                android.os.SystemClock.sleep(1000)
            }
        }
        val active = instrumentation.uiAutomation.rootInActiveWindow
        assertTrue("An ANR obstructs the sample", active?.findAccessibilityNodeInfosByText("isn't responding").isNullOrEmpty())
    }

    private fun shot(name: String): Bitmap {
        assertNoBlockingAnr()
        val bmp = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "no screenshot for $name" }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun rotate(landscape: Boolean) {
        instrumentation.uiAutomation.setRotation(
            if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        rule.waitForIdle()
        android.os.SystemClock.sleep(800)
        rule.waitForIdle()
    }

    private data class Layout(val text: String, val plex: Boolean)

    /** Every on-screen text layout that contains Arabic script, and whether Plex is applied to it. */
    private fun arabicLayouts(): List<Layout> =
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().mapNotNull { node ->
                val fn = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return@mapNotNull null
                val results = ArrayList<TextLayoutResult>()
                if (!fn(results) || results.isEmpty()) return@mapNotNull null
                val layout = results[0]
                val text = layout.layoutInput.text
                if (!isArabic(text.text)) return@mapNotNull null
                val plex = layout.layoutInput.style.fontFamily == ArabicUiFontFamily ||
                    text.spanStyles.any { it.item.fontFamily == ArabicUiFontFamily }
                Layout(text.text.take(60), plex)
            }

    @Before fun dismissPrompt() {
        rule.waitForIdle()
        if (rule.onAllNodesWithText(ar(R.string.donation_decline)).fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithText(ar(R.string.donation_decline)).performClick()
    }

    private val pages = listOf(
        "beam" to R.string.home_beam, "column" to R.string.home_column, "slab" to R.string.home_slab,
        "footing" to R.string.home_footing, "tank" to R.string.home_tank, "retaining_wall" to R.string.home_retaining_wall,
        "seismic" to R.string.home_seismic, "frame" to R.string.home_frame, "column_biaxial" to R.string.home_column_biaxial,
        "pile" to R.string.home_pile, "strap_footing" to R.string.home_strap_footing, "flat_slab" to R.string.home_flat_slab,
        "waffle_slab" to R.string.home_waffle_slab, "hordi_slab" to R.string.home_hordi_slab, "shear_wall" to R.string.home_shear_wall,
        "wall_opening" to R.string.home_wall_opening, "steel" to R.string.home_steel,
        "quality_engineer" to R.string.home_quality_engineer, "post_tensioned" to R.string.home_post_tensioned,
        "advanced_design" to R.string.home_advanced_design
    )

    @Test fun arabicPagesUsePlexInPortraitAndLandscape() {
        val report = StringBuilder("language=ar (default on fresh install)\nscope=Design Hub pages except Stair, initial state only (no calculation run)\n")
        var violations = 0
        try {
            for ((slug, id) in pages) {
                val title = ar(id)
                rule.onNodeWithText(ar(R.string.nav_design_hub)).performClick()
                scroll(title)
                rule.onNodeWithText(title).performClick()
                rule.waitForIdle()
                android.os.SystemClock.sleep(600)
                val portrait = shot("${slug}-portrait")
                assertTrue("$slug portrait is not portrait", portrait.height > portrait.width)
                val pNodes = arabicLayouts()
                rotate(true)
                val land = shot("${slug}-landscape")
                assertTrue("$slug landscape is not landscape", land.width > land.height)
                val lNodes = arabicLayouts()
                rotate(false)
                val bad = (pNodes + lNodes).filter { !it.plex }
                violations += bad.size
                report.append("$slug: arabicTextsPortrait=${pNodes.size} landscape=${lNodes.size} withoutPlex=${bad.size}")
                if (bad.isNotEmpty()) report.append(" e.g. ").append(bad.take(3).joinToString(" | ") { it.text })
                report.append('\n')
                assertTrue("$slug shows no Arabic text at all", pNodes.isNotEmpty())
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                rule.waitForIdle()
            }
        } finally {
            rotate(false)
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            File(out, "arabic-pages-report.txt").writeText(report.toString())
        }
        assertEquals("Arabic text without the Plex family, see arabic-pages-report.txt", 0, violations)
    }

    @Test fun languageSwitchEnglishThenArabicIsApplied_andPersisted() {
        fun prefLang() = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString("language", null)
        val report = StringBuilder()
        try {
            rule.activity.runOnUiThread { rule.activity.setLocale("en") }
            val enTitle = str("en", R.string.nav_design_hub)
            rule.waitUntil(20000) { rule.onAllNodesWithText(enTitle).fetchSemanticsNodes().isNotEmpty() }
            rule.waitForIdle()
            assertEquals("en", prefLang())
            shot("language-en")
            val englishPlex = arabicLayouts().size
            val enUsesPlex = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes().count { node ->
                    val r = ArrayList<TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(r)
                    r.firstOrNull()?.layoutInput?.style?.fontFamily == ArabicUiFontFamily
                }
            report.append("english: arabicTexts=$englishPlex textsUsingPlex=$enUsesPlex\n")
            assertEquals("English UI must not use the Arabic font", 0, enUsesPlex)

            rule.activity.runOnUiThread { rule.activity.setLocale("ar") }
            val arTitle = ar(R.string.nav_design_hub)
            rule.waitUntil(20000) { rule.onAllNodesWithText(arTitle).fetchSemanticsNodes().isNotEmpty() }
            rule.waitForIdle()
            assertEquals("ar", prefLang())
            shot("language-ar")
            val arTexts = arabicLayouts()
            report.append("arabic after switch: arabicTexts=${arTexts.size} withoutPlex=${arTexts.count { !it.plex }}\n")
            assertTrue("no Arabic text after switching back", arTexts.isNotEmpty())
            assertTrue("Arabic without Plex after switching back", arTexts.all { it.plex })
        } finally {
            File(out, "language-switch-report.txt").writeText(report.toString())
        }
    }
}
