package com.civileg.app.ui.compose.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.drawToBitmap
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.viewmodel.SoilStructureViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Rendered-locale + layout-direction gate for PR-i18n-1: the three screens whose
 * hardcoded Arabic literals were extracted, rendered through the REAL screen
 * composables under the real app theme, in English and Arabic. Arabic runs wrap
 * content in LayoutDirection.Rtl, mirroring what production gets from
 * MainActivity.attachBaseContext -> LocaleHelper.wrapContext (configuration
 * layout direction). Captures cover multiple viewports (scroll + post-Calculate)
 * so the audit is the full screen, not the first viewport.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class I18nScreensVisualTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun pinLocale(lang: String) {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.initApplicationContext(app)
        LocaleHelper.setLocale(app, lang)
    }

    /** Mirrors production: wrapContext puts RTL in the configuration for Arabic. */
    private fun setThemedContent(arabic: Boolean, content: @Composable () -> Unit) {
        rule.setContent {
            val themed: @Composable () -> Unit = {
                CivilEngineerPremiumTheme(darkTheme = false, content = content)
            }
            if (arabic) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { themed() }
            } else {
                themed()
            }
        }
        rule.waitForIdle()
    }

    private fun assertTextPresent(text: String) {
        assertTrue(
            "expected visible text: $text",
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun assertTextAbsent(text: String) {
        assertEquals(
            "unexpected text present: $text",
            0,
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
        )
    }

    private fun capture(name: String) {
        val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
        val out = File(dir, "$name.png")
        rule.waitForIdle()
        try {
            val content = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            val bitmap = content.drawToBitmap()
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
            File(dir, "$name-capture-failed.txt").writeText(
                "window capture unavailable: ${e.javaClass.simpleName}: ${e.message?.take(120)}"
            )
        }
    }

    private fun scrollAndCapture(name: String) {
        try {
            rule.onRoot().performTouchInput { swipeUp() }
        } catch (_: Exception) {
            // non-scrollable viewport; capture anyway
        }
        capture(name)
    }

    private fun soilAudits(calculateLabel: String, tag: String) {
        capture("i18n-soil-$tag-v1")
        scrollAndCapture("i18n-soil-$tag-v2")
        scrollAndCapture("i18n-soil-$tag-v3")
        // Run the real calculation and capture the results viewports.
        try {
            rule.onAllNodesWithText(calculateLabel, substring = true)[0].performClick()
            rule.waitForIdle()
            capture("i18n-soil-$tag-results-v1")
            scrollAndCapture("i18n-soil-$tag-results-v2")
            scrollAndCapture("i18n-soil-$tag-results-v3")
        } catch (e: Exception) {
            val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
            File(dir, "i18n-soil-$tag-results-failed.txt").writeText(
                "calculate/capture failed: ${e.javaClass.simpleName}: ${e.message?.take(160)}"
            )
        }
    }

    @Test
    fun `soil structure screen renders English resources in English locale`() {
        pinLocale("en")
        setThemedContent(arabic = false) {
            SoilStructureScreen(onNavigateBack = {}, viewModel = SoilStructureViewModel())
        }
        assertTextPresent("Soil-Structure Interaction")
        assertTextPresent("Foundation length")
        assertTextAbsent("تفاعل المنشأ")
        soilAudits("Calculate", "en")
    }

    @Test
    fun `soil structure screen renders Arabic resources and RTL layout in Arabic locale`() {
        pinLocale("ar")
        setThemedContent(arabic = true) {
            SoilStructureScreen(onNavigateBack = {}, viewModel = SoilStructureViewModel())
        }
        assertTextPresent("تفاعل المنشأ مع التربة")
        assertTextPresent("طول الأساس")
        assertTextAbsent("Soil-Structure Interaction")
        soilAudits("حساب", "ar")
    }

    @Test
    fun `quality compliance screen renders English resources in English locale`() {
        pinLocale("en")
        setThemedContent(arabic = false) { QualityComplianceScreen() }
        assertTextPresent("Compliance Checklists")
        assertTextPresent("All disciplines")
        assertTextAbsent("لوائح الامتثال")
        capture("i18n-qc-en-v1")
        scrollAndCapture("i18n-qc-en-v2")
        scrollAndCapture("i18n-qc-en-v3")
    }

    @Test
    fun `quality compliance screen renders Arabic resources and RTL layout in Arabic locale`() {
        pinLocale("ar")
        setThemedContent(arabic = true) { QualityComplianceScreen() }
        assertTextPresent("لوائح الامتثال التنظيمية")
        assertTextPresent("كل التخصصات")
        assertTextAbsent("Compliance Checklists")
        capture("i18n-qc-ar-v1")
        scrollAndCapture("i18n-qc-ar-v2")
        scrollAndCapture("i18n-qc-ar-v3")
    }

    @Test
    fun `building designer screen renders English resources in English locale`() {
        pinLocale("en")
        setThemedContent(arabic = false) { BuildingDesignerScreen() }
        assertTextPresent("Structural Designer")
        assertTextPresent("Run Design & Analysis")
        assertTextPresent("Footing")
        assertTextAbsent("المصمم الإنشائي")
        assertTextAbsent("قاعدة")
        capture("i18n-bd-en-v1")
    }

    @Test
    fun `building designer screen renders Arabic resources and RTL layout in Arabic locale`() {
        pinLocale("ar")
        setThemedContent(arabic = true) { BuildingDesignerScreen() }
        assertTextPresent("المصمم الإنشائي")
        assertTextPresent("تشغيل التصميم والتحليل")
        assertTextPresent("قاعدة")
        assertTextAbsent("Structural Designer")
        assertTextAbsent("Footing")
        capture("i18n-bd-ar-v1")
    }
}
