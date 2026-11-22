package com.civileg.app.ui.compose.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.drawToBitmap
import com.civileg.app.R
import com.civileg.app.domain.calculations.ecp.ECPHordiSlabDesign
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.ui.compose.components.drawings.hordiSlabDrawingSummary
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
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HordiSafetyAssessmentTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val result = ECPHordiSlabDesign().designHordiSlab(
        25.0, 360.0, 150.0, 600.0, 300.0, 60.0, 5000.0,
        10.0, 1.0, LoadCombination.DEAD_LIVE
    )

    private fun verify(lang: String, shared: Boolean = false) {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.initApplicationContext(app)
        LocaleHelper.setLocale(app, lang)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides
                if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                CivilEngineerPremiumTheme(darkTheme = false) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        androidx.compose.foundation.layout.Column {
                            if (shared) SlabResultCard(com.civileg.app.domain.calculations.utils.CalculatorEngine.SlabResult(
                                type = com.civileg.app.domain.calculations.utils.CalculatorEngine.SlabType.HOLLOW_BLOCK,
                                safetyAssessmentBlocked = true
                            )) else HordiSafetyAssessment(result, "ECP")
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val expected = app.getString(R.string.hordi_method_hold)
        assertTrue(rule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("SAFE").fetchSemanticsNodes().isEmpty())
        assertEquals("NOT VERIFIED", hordiSlabDrawingSummary(result).safetyLabel)
        val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
        val content = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        FileOutputStream(File(dir, "hordi-method-hold-${if (shared) "shared-" else ""}$lang.png")).use {
            content.drawToBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun methodHoldEnglish() { verify("en") }
    @Test fun methodHoldArabic() { verify("ar") }

    @Test fun blockedDrawingStrip() {
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = true) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    com.civileg.app.ui.compose.components.drawings.ProfessionalHordiSlabDrawing(
                        span = 5000.0, ribWidth = 150.0, ribSpacing = 600.0,
                        totalThickness = 300.0, toppingThickness = 60.0,
                        ribBottomDia = 12.0, ribBottomCount = 2,
                        stirrupDia = 8.0, stirrupSpacing = 200.0, cover = 25.0,
                        viewMode = 1, resultSummary = hordiSlabDrawingSummary(result),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        rule.waitForIdle()
        val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
        val content = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        FileOutputStream(File(dir, "hordi-method-hold-drawing.png")).use {
            content.drawToBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun shared_slab_result_shows_method_hold_en() = verify("en", shared = true)
    @Test fun shared_slab_result_shows_method_hold_ar() = verify("ar", shared = true)

}
