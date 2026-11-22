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
import androidx.compose.ui.test.onAllNodesWithText
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
        assertEquals("SBC 304-CR-2018", inputs["Design Code"])
        assertEquals("NOT EVALUATED (shear blocked)", results["Shear Capacity"])
        assertEquals("N/E", results["Utilization"])
        assertEquals("NO", results["Is Safe"])

        // Methodology is the engine's own per-rib path, never the generic slab
        // template: the blocked shear step carries its source reason, no equation.
        assertEquals(9, res.calculationSteps.size)
        val shearStep = res.calculationSteps.single { it.title == "One-Way Shear" }
        assertTrue(shearStep.formulaWithValues.contains("22.5.5.1"))
        assertTrue(shearStep.result.contains("NOT EVALUATED"))
        res.calculationSteps.forEach { step ->
            assertFalse("no substitute shear equation", step.formula.contains("0.17"))
            assertFalse("no substitute shear equation", step.formulaWithValues.contains("0.17"))
            assertFalse("joist design is per-rib, never per-meter", step.formula.contains("per meter"))
            assertFalse("joist design is per-rib, never per-meter", step.formulaWithValues.contains("per meter"))
        }

        // Legacy project without a cylinder value: explicit MISSING, no conversion.
        val legacyInputs = HordiResultPresentation.exportInputsMap(
            fcu = 30.0, fy = 400.0, ribWidth = 150.0, ribSpacing = 650.0,
            totalThickness = 300.0, toppingThickness = 60.0, span = 6000.0,
            designMoment = 45.0, designShear = 30.0, cylinderStrength = null,
            loadCombinationDescription = LoadCombination.DEAD_LIVE.description,
            designCode = "SBC"
        )
        assertEquals("MISSING MPa", legacyInputs["f'c (cylinder)"])

        // Production export path: same ExportManager call the ViewModel makes.
        val dir = File("build/reports/tests/export-shots").apply { mkdirs() }
        val out = File(dir, "hordi-sbc-blocked-export.pdf")
        val generated = com.civileg.app.utils.ExportManager(RuntimeEnvironment.getApplication())
            .exportReportAndDxf(
                titleAr = "", titleEn = "Hordi Slab Design Report",
                subtitle = "Code: SBC 304-CR-2018  Span=6.0m",
                designType = "Hordi Slab",
                inputs = inputs,
                results = results,
                safetyChecks = emptyList(),
                isSafe = res.isSafe,
                drawingBitmap = null,
                calculationSteps = res.calculationSteps,
                outputPath = out.absolutePath
            ).pdfFile
        assertNotNull("PDF export generated", generated)
        assertTrue("PDF non-trivial", out.isFile && out.length() > 5_000)

        // Rendered-report verification: edition on cover, blocked methodology,
        // no generic slab template equations anywhere in the document.
        val pdfText = com.itextpdf.kernel.pdf.PdfDocument(
            com.itextpdf.kernel.pdf.PdfReader(out)
        ).use { doc ->
            (1..doc.numberOfPages).joinToString("\n") {
                com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(doc.getPage(it))
            }
        }
        assertTrue("edition named on report", pdfText.contains("SBC 304-CR-2018"))
        assertTrue("blocked shear in methodology", pdfText.contains("NOT EVALUATED (shear blocked)"))
        assertTrue("blocked reason printed", pdfText.contains("22.5.5.1"))
        assertFalse("no generic shear equation", pdfText.contains("0.17*sqrt"))
        assertFalse("no per-meter slab wording", pdfText.contains("per meter width"))
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
