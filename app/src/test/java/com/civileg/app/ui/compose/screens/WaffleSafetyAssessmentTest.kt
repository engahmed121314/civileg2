package com.civileg.app.ui.compose.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.drawToBitmap
import com.civileg.app.R
import com.civileg.app.domain.calculations.base.WaffleSlabDesign
import com.civileg.app.domain.calculations.ecp.ECPWaffleSlabDesign
import com.civileg.app.ui.compose.components.drawings.*
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import com.civileg.app.utils.LocaleHelper
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WaffleSafetyAssessmentTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val result = ECPWaffleSlabDesign().design(WaffleSlabDesign.WaffleSlabInput(deadLoad = .1, liveLoad = .1))

    private fun capture(name: String) {
        rule.waitForIdle()
        val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
        val content = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        FileOutputStream(File(dir, "$name.png")).use {
            content.drawToBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun notice(lang: String) {
        val app = RuntimeEnvironment.getApplication<android.app.Application>()
        LocaleHelper.initApplicationContext(app)
        LocaleHelper.setLocale(app, lang)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                CivilEngineerPremiumTheme(darkTheme = false) {
                    Surface(Modifier.fillMaxSize()) { Column { WaffleSafetyAssessment(result, "ECP") } }
                }
            }
        }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText(app.getString(R.string.hordi_method_hold)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("SAFE").fetchSemanticsNodes().isEmpty())
        assertEquals("NOT VERIFIED", waffleSlabDrawingSummary(result).safetyLabel)
        capture("waffle-method-hold-$lang")
    }

    @Test fun noticeEnglish() = notice("en")
    @Test fun noticeArabic() = notice("ar")

    private fun drawing(mode: Int) {
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = false) {
                ProfessionalWaffleSlabDrawing(
                    lx = 6000.0, ly = 7500.0, ribSpacing = 600.0, ribWidth = 150.0,
                    ribHeight = 300.0, toppingThickness = 50.0, solidHeadSize = 1000.0,
                    columnWidth = 400.0, ribBottomDia = 16.0, ribBottomCount = 2,
                    ribTopDia = 16.0, ribTopCount = 2, headBottomDia = 16.0,
                    headBottomCount = 8, cover = 25.0, viewMode = mode,
                    resultSummary = waffleSlabDrawingSummary(result), modifier = Modifier.fillMaxSize()
                )
            }
        }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("METHOD NOT VERIFIED").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("NOT FOR EXECUTION").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("BBS SCHEDULE").fetchSemanticsNodes().isEmpty())
        capture("waffle-method-hold-drawing-mode$mode")
    }

    @Test fun allModeWithholdsInlineSchedule() = drawing(0)
    @Test fun tableModeWithholdsInlineSchedule() = drawing(4)
}
