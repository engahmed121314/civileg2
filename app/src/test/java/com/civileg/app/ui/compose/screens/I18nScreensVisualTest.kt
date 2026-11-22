package com.civileg.app.ui.compose.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.core.view.drawToBitmap
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.viewmodel.SoilStructureViewModel
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
 * Rendered-locale gate for PR-i18n-1: the three screens whose hardcoded Arabic
 * literals were extracted to paired resources, rendered through the REAL screen
 * composables under the real app theme, in English and Arabic, with pixel
 * captures for post-CI inspection. Arabic strings are verbatim from before the
 * extraction, so AR frames must look identical to the pre-change UI and EN
 * frames must show real English, not Arabic fallbacks.
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

    @Test
    fun `soil structure screen renders English resources in English locale`() {
        pinLocale("en")
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = false) {
                SoilStructureScreen(onNavigateBack = {}, viewModel = SoilStructureViewModel())
            }
        }
        rule.waitForIdle()
        assertTextPresent("Soil-Structure Interaction")
        assertTextPresent("Foundation length")
        assertTextAbsent("تفاعل المنشأ")
        capture("i18n-soil-en")
    }

    @Test
    fun `soil structure screen renders Arabic resources in Arabic locale`() {
        pinLocale("ar")
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = false) {
                SoilStructureScreen(onNavigateBack = {}, viewModel = SoilStructureViewModel())
            }
        }
        rule.waitForIdle()
        assertTextPresent("تفاعل المنشأ مع التربة")
        assertTextPresent("طول الأساس")
        assertTextAbsent("Soil-Structure Interaction")
        capture("i18n-soil-ar")
    }

    @Test
    fun `quality compliance screen renders English resources in English locale`() {
        pinLocale("en")
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = false) { QualityComplianceScreen() }
        }
        rule.waitForIdle()
        assertTextPresent("Compliance Checklists")
        assertTextPresent("Concrete")
        assertTextAbsent("لوائح الامتثال")
        capture("i18n-qc-en")
    }

    @Test
    fun `quality compliance screen renders Arabic resources in Arabic locale`() {
        pinLocale("ar")
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = false) { QualityComplianceScreen() }
        }
        rule.waitForIdle()
        assertTextPresent("لوائح الامتثال التنظيمية")
        assertTextPresent("خرسانة")
        assertTextAbsent("Compliance Checklists")
        capture("i18n-qc-ar")
    }

    @Test
    fun `building designer screen renders English resources in English locale`() {
        pinLocale("en")
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = false) { BuildingDesignerScreen() }
        }
        rule.waitForIdle()
        assertTextPresent("Structural Designer")
        assertTextPresent("Run Design & Analysis")
        assertTextAbsent("المصمم الإنشائي")
        capture("i18n-bd-en")
    }

    @Test
    fun `building designer screen renders Arabic resources in Arabic locale`() {
        pinLocale("ar")
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = false) { BuildingDesignerScreen() }
        }
        rule.waitForIdle()
        assertTextPresent("المصمم الإنشائي")
        assertTextPresent("تشغيل التصميم والتحليل")
        assertTextAbsent("Structural Designer")
        capture("i18n-bd-ar")
    }
}
