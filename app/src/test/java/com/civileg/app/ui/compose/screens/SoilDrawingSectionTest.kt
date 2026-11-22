package com.civileg.app.ui.compose.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.civileg.app.domain.calculations.base.SoilStructureInteraction
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import com.civileg.app.utils.LocaleHelper
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
 * Real-screen gate for the soil results drawing zone: the production
 * SoilDrawingSection (mode chips + 350dp card + drawing) rendered at phone
 * width in EN and AR. The initial view must be a readable single mode
 * (elevation), not the legacy all-in-one composite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SoilDrawingSectionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val result = SoilStructureInteraction.Result(
        bearing = SoilStructureInteraction.BearingResult(133.0, 61.0, 97.0, 150.0, true),
        settlement = SoilStructureInteraction.SettlementResult(12.0, 25.0, true),
        stability = SoilStructureInteraction.StabilityResult(4.40, 2.10, 1.13, true),
        maxSoilPressureKpa = 133.0,
        minSoilPressureKpa = 61.0,
        isSafe = true
    )

    private fun pinLocale(lang: String) {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.initApplicationContext(app)
        LocaleHelper.setLocale(app, lang)
    }

    private fun setThemedContent(arabic: Boolean, content: @Composable () -> Unit) {
        rule.setContent {
            val themed: @Composable () -> Unit = {
                // Surface backdrop: without it the capture keeps transparent
                // pixels that preview as black and read as a contrast defect.
                CivilEngineerPremiumTheme(darkTheme = false) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background
                    ) { content() }
                }
            }
            if (arabic) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { themed() }
            } else {
                themed()
            }
        }
        rule.waitForIdle()
    }

    private fun capture(name: String) {
        val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
        val content = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val bitmap = content.drawToBitmap()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun assertTextPresent(text: String) {
        assertTrue(
            "expected visible text: $text",
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Composable
    private fun section() {
        Column(Modifier.width(320.dp)) {
            SoilDrawingSection(
                res = result,
                foundationLengthMm = "3000",
                foundationWidthMm = "2000",
                foundationThicknessMm = "500",
                columnLoadKN = "1200",
                momentKNm = "50",
                soilBearingCapacityKpa = "150",
                selectedCode = DesignCode.ECP
            )
        }
    }

    @Test
    fun soilSection_initial_view_is_readable_elevation_en() {
        pinLocale("en")
        setThemedContent(arabic = false) { section() }
        assertTextPresent("Elevation")
        assertTextPresent("Plan & Kern")
        assertTextPresent("Stress Bulb")
        assertTextPresent("Table")
        capture("soil-section-initial-en")
    }

    @Test
    fun soilSection_initial_view_is_readable_elevation_ar() {
        pinLocale("ar")
        setThemedContent(arabic = true) { section() }
        assertTextPresent("المنظور")
        assertTextPresent("الجدول")
        capture("soil-section-initial-ar")
    }
}
