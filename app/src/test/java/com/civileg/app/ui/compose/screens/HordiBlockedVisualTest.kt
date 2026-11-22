package com.civileg.app.ui.compose.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.civileg.app.R
import com.civileg.app.domain.calculations.sbc.SBCJoistSlab
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.SlabDesignResult
import com.civileg.app.ui.compose.components.PremiumInputField
import com.civileg.app.ui.compose.components.ResultDataCard
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * Visual gate for the SBC joist blocked-shear schema (SJ-7/SJ-8): the same
 * composables and presentation helpers HordiSlabScreen uses, rendered in both
 * UI languages, plus the always-English export rows the ViewModel now builds
 * through HordiResultPresentation. A not-evaluated state must be explicit —
 * never a 0% gauge, a fake capacity, or a PASS.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HordiBlockedVisualTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun pinLocale(lang: String) {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.initApplicationContext(app)
        LocaleHelper.setLocale(app, lang)
    }

    /** Engine-built blocked SBC result: explicit cylinder in, shear NOT EVALUATED out. */
    private fun blockedSbcResult(): SlabDesignResult = SBCJoistSlab().designHordiSlab(
        fcu = 30.0, fy = 400.0, ribWidth = 150.0, ribSpacing = 650.0,
        totalThickness = 300.0, toppingThickness = 60.0, span = 6000.0,
        designMoment = 45.0, designShear = 30.0,
        loadCombination = LoadCombination.DEAD_LIVE,
        cylinderStrengthMPa = 24.0
    )

    /** Mirrors the blocked-state cards in HordiSlabScreen (no gauge when null). */
    @Composable
    private fun BlockedResultCards(res: SlabDesignResult, ar: Boolean) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ResultDataCard(
                stringResource(R.string.hordi_slab_safety_title, "SBC"),
                listOf("Status" to HordiResultPresentation.statusText(res.isSafe, false, ar)),
                accentColor = Color(0xFFE53935)
            )
            Spacer(Modifier.height(8.dp))
            ResultDataCard(
                stringResource(R.string.hordi_slab_shear_check),
                listOf(
                    "Shear Capacity" to HordiResultPresentation.shearCapacityText(res.shearCapacity, ar),
                    "Min Thickness" to String.format("%.0f mm", res.minThickness),
                    "Utilization" to HordiResultPresentation.utilizationText(res.utilizationRatio, ar),
                    "Status" to HordiResultPresentation.statusText(res.isSafe, res.utilizationRatio != null, ar)
                ),
                accentColor = Color(0xFFE53935)
            )
        }
    }

    @Test
    fun `blocked SBC result renders explicit not-evaluated states in English`() {
        pinLocale("en")
        val res = blockedSbcResult()
        assertNull("blocked shear must be null, never a number", res.shearCapacity)
        assertNull("blocked utilization must be null, never 0", res.utilizationRatio)
        assertFalse("blocked design is never safe", res.isSafe)

        rule.setContent { MaterialTheme { BlockedResultCards(res, LocaleHelper.isArabic()) } }

        rule.onNodeWithText("NOT EVALUATED", substring = true).assertIsDisplayed()
        rule.onNodeWithText("N/E", substring = true).assertIsDisplayed()
        rule.onAllNodesWithText("INCOMPLETE", substring = true)[0].performScrollTo().assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("PASS", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("0%", substring = true).fetchSemanticsNodes().size)
        captureRootOrNote("hordi-sbc-blocked-en")
    }

    @Test
    fun `blocked SBC result renders explicit not-evaluated states in Arabic`() {
        pinLocale("ar")
        val res = blockedSbcResult()

        rule.setContent { MaterialTheme { BlockedResultCards(res, LocaleHelper.isArabic()) } }

        rule.onAllNodesWithText("غير مقيّمة", substring = true)[0].performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("غير مكتمل", substring = true)[0].performScrollTo().assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("آمن ✓", substring = true).fetchSemanticsNodes().size)
        captureRootOrNote("hordi-sbc-blocked-ar")
    }

    @Test
    fun `SBC cylinder input field renders its label in English`() {
        pinLocale("en")
        rule.setContent {
            MaterialTheme {
                Column(Modifier.padding(16.dp)) {
                    PremiumInputField(
                        "", stringResource(R.string.hordi_slab_fc_cylinder), {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        rule.onNodeWithText("required for SBC", substring = true).assertIsDisplayed()
        captureRootOrNote("hordi-sbc-cylinder-input-en")
    }

    @Test
    fun `SBC cylinder input field renders its label in Arabic`() {
        pinLocale("ar")
        rule.setContent {
            MaterialTheme {
                Column(Modifier.padding(16.dp)) {
                    PremiumInputField(
                        "", stringResource(R.string.hordi_slab_fc_cylinder), {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        rule.onNodeWithText("مطلوبة للكود السعودي", substring = true).assertIsDisplayed()
        captureRootOrNote("hordi-sbc-cylinder-input-ar")
    }

    @Test
    fun `english export rows and PDF carry NOT EVALUATED and never present blocked as safe`() {
        val res = blockedSbcResult()
        val inputs = HordiResultPresentation.exportInputsMap(
            fcu = 30.0, fy = 400.0, ribWidth = 150.0, ribSpacing = 650.0,
            totalThickness = 300.0, toppingThickness = 60.0, span = 6000.0,
            designMoment = 45.0, designShear = 30.0, cylinderStrength = 24.0,
            loadCombinationDescription = LoadCombination.DEAD_LIVE.description,
            designCode = "SBC"
        )
        val results = HordiResultPresentation.exportResultsMap(
            requiredReinforcement = res.requiredReinforcement,
            providedReinforcement = res.providedReinforcement,
            barDiameter = res.barDiameter, barSpacing = res.barSpacing,
            minThickness = res.minThickness,
            shearCapacity = res.shearCapacity,
            utilizationRatio = res.utilizationRatio,
            isSafe = res.isSafe
        )
        assertEquals("24.0 MPa", inputs["f'c (cylinder)"])
        assertEquals("NOT EVALUATED (shear blocked)", results["Shear Capacity"])
        assertEquals("N/E", results["Utilization"])
        assertEquals("NO", results["Is Safe"])

        // Legacy project without a cylinder value: explicit MISSING, no conversion.
        val legacyInputs = HordiResultPresentation.exportInputsMap(
            fcu = 30.0, fy = 400.0, ribWidth = 150.0, ribSpacing = 650.0,
            totalThickness = 300.0, toppingThickness = 60.0, span = 6000.0,
            designMoment = 45.0, designShear = 30.0, cylinderStrength = null,
            loadCombinationDescription = LoadCombination.DEAD_LIVE.description,
            designCode = "SBC"
        )
        assertEquals("MISSING MPa", legacyInputs["f'c (cylinder)"])

        val dir = File("build/reports/tests/export-shots").apply { mkdirs() }
        val out = File(dir, "hordi-sbc-blocked-export.pdf")
        val generated = ProfessionalEnglishPdfReporter.generateReportLegacy(
            titleAr = "", titleEn = "Hordi Slab Design Report",
            subtitle = "Code: SBC  Span=6.0m",
            designType = "Hordi Slab",
            inputs = inputs,
            results = results,
            safetyChecks = emptyList(),
            isSafe = res.isSafe,
            drawingBitmap = null,
            outputPath = out.absolutePath
        )
        assertNotNull("PDF export generated", generated)
        assertTrue("PDF non-trivial", out.isFile && out.length() > 5_000)
    }

    /** Best-effort PNG capture of the compose root for post-CI pixel inspection. */
    private fun captureRootOrNote(name: String) {
        val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
        val out = File(dir, "$name.png")
        // NATIVE graphics mode makes the window capture real; semantics
        // captureToImage times out under Robolectric (verified on CI).
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
}
