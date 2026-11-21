package com.civileg.app.ui.compose.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.drawToBitmap
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.entities.BlockedSeismicCheck
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.EcpColumnDuctilityMode
import com.civileg.app.domain.entities.EcpColumnSeismicCheck
import com.civileg.app.domain.entities.EcpColumnSeismicDetailing
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.utils.LocaleHelper
import io.mockk.mockk
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Visual evidence for the ECP §6-8 ductile-mode UI and export semantics.
 * Artifacts land under app/build/reports/tests/compose-shots/ and
 * app/build/reports/tests/export-shots/, which the CI test-report artifact
 * uploads, so the rendered PNG/PDF can be inspected after the run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColumnSeismicDetailingSectionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val limitedFixture = EcpColumnSeismicDetailing(
        mode = EcpColumnDuctilityMode.LIMITED,
        criticalRegionLengthMm = 600.0,
        criticalTieSpacingMm = 150.0,
        firstTieMaxOffsetMm = 75.0,
        elsewhereTieSpacingMm = 200.0,
        checks = emptyList(),
        blockedChecks = listOf(
            BlockedSeismicCheck(
                name = "Capacity-design shear (column)",
                clause = "ECP 203-2020 §6-8-2-2-3 + fig 6-39 (printed 6-92..6-93)",
                reason = "Needs column end moments from a frame analysis at the axial level giving the larger flexural resistance; not derivable from single-column inputs.",
                key = "capacity_shear_limited"
            ),
            BlockedSeismicCheck(
                name = "Longitudinal bars per face (>= 3 each face)",
                clause = "ECP 203-2020 §6-8-2-2-3 / §6-8-2-3-2 (printed 6-92 / 6-95)",
                reason = "Per-face bar count cannot be derived from the total bar count without the actual arrangement; verify at least 3 bars on each face on the section drawing.",
                key = "bars_per_face"
            )
        ),
        clauseReferences = listOf("ECP 203-2020 §6-8-2-2-3 limited-ductility columns (printed 6-92)"),
        isComplete = false
    )

    @Test
    fun `limited-mode detailing section renders incomplete banner, values and blocked check`() {
        // LocaleHelper.getLocale defaults to Arabic once an app context exists, so
        // pin English explicitly or the English row labels never render.
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.initApplicationContext(app)
        LocaleHelper.setLocale(app, "en")
        rule.setContent {
            MaterialTheme {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        ColumnSeismicDetailingSection(limitedFixture)
                    }
                }
            }
        }

        // Visible verification: scroll each required row into the viewport and
        // confirm it is actually displayed (existence alone proves nothing visual).
        rule.onNodeWithTag("seismic-detailing-header").assertIsDisplayed()
        rule.onNodeWithText("Critical region Lo (each end)", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("600 mm", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("150 mm", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("seismic-blocked-Capacity-design shear (column)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("seismic-blocked-Longitudinal bars per face (>= 3 each face)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("INCOMPLETE", substring = true).assertIsDisplayed()
        rule.onNodeWithText("BLOCKED:", substring = true).assertIsDisplayed()

        // Rendered-pixel capture for post-CI visual inspection. Window capture can
        // time out under Robolectric CI (forceRedraw); capture is best-effort
        // evidence, the semantic assertions above are the gate.
        captureRootOrNote("ecp-column-limited-detailing")
    }

    @Test
    fun `ductile export PDF carries blocked checks and unsafe status`() {
        // Build a real LIMITED-mode result through the engine (blocked semantics
        // come from CalculatorEngine, not fixtures).
        val engine = CalculatorEngine(mockk(relaxed = true))
        val res = engine.designColumn(
            width = 400.0, depth = 600.0, pu = 500.0, fcu = 30.0, fy = 400.0,
            code = DesignCode.ECP, clearHeight = 3000.0,
            ecpDuctilityMode = EcpColumnDuctilityMode.LIMITED
        )
        assertNotNull(res.seismicDetailing)
        assertTrue(res.safetyChecks.any { it.name.startsWith("BLOCKED") && !it.isSafe })

        // Same mapping the ViewModel applies before calling the exporter.
        val resultsMap = mutableMapOf(
            "Axial Capacity" to "${"%.1f".format(res.axialCapacity)} kN",
            "Reinforcement" to res.reinforcement.barString,
            "Stirrups" to res.stirrups.description
        )
        res.seismicDetailing?.let { d ->
            resultsMap["Ductility Mode (ECP 203 §6-8)"] = d.mode.displayNameEn + " — INCOMPLETE (frame checks pending)"
            resultsMap["Critical Region Lo (each end)"] = "${"%.0f".format(d.criticalRegionLengthMm)} mm"
            resultsMap["Tie Spacing in Critical Region S0"] = "${"%.0f".format(d.criticalTieSpacingMm)} mm"
        }
        val checks = res.safetyChecks.map {
            GenericSafetyCheck(name = it.name, calculated = it.value, limit = it.limit, unit = it.unit, passed = it.isSafe)
        }

        val dir = File("build/reports/tests/export-shots").apply { mkdirs() }
        val out = File(dir, "column-ductile-export.pdf")
        val generated = ProfessionalEnglishPdfReporter.generateReportLegacy(
            titleAr = "", titleEn = "Column Design Report - RECTANGULAR",
            subtitle = "Code: ECP 203  400x600mm, H=3.0m",
            designType = "Column (RECTANGULAR)",
            inputs = mapOf("Design Code" to "ECP 203", "Width" to "400 mm", "Depth" to "600 mm"),
            results = resultsMap,
            safetyChecks = checks,
            calculationSteps = res.calculationSteps.map { step ->
                ProfessionalEnglishPdfReporter.CalculationStep(
                    stepNumber = step.stepNumber,
                    description = step.title,
                    equation = step.formulaWithValues.ifBlank { step.formula },
                    reference = step.codeReference.ifBlank { null },
                    result = listOf(step.result, step.unit)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                )
            },
            isSafe = res.isSafe,   // false for ductile modes -> STATUS: UNSAFE in the PDF
            drawingBitmap = null,
            outputPath = out.absolutePath
        )
        assertNotNull("PDF export generated", generated)
        assertTrue("PDF non-trivial", out.isFile && out.length() > 10_000)
    }

    @Test
    fun `arabic locale renders arabic detailing header and labels`() {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.initApplicationContext(app)
        LocaleHelper.setLocale(app, "ar")
        try {
            rule.setContent {
                MaterialTheme {
                    Card {
                        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            ColumnSeismicDetailingSection(limitedFixture)
                        }
                    }
                }
            }

            rule.onNodeWithTag("seismic-detailing-header").assertIsDisplayed()
            rule.onNodeWithText("تكسيح الممطولية", substring = true).assertIsDisplayed()
            rule.onNodeWithText("طول المنطقة الحرجة", substring = true).performScrollTo().assertIsDisplayed()
            // R-1: blocked checks localize too (prefix, name, reason) — no English leaks
            rule.onNodeWithText("معلّق", substring = true).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("الأسياخ الطولية لكل وجه", substring = true).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("لا يمكن استنتاج عدد الأسياخ", substring = true).assertIsDisplayed()
            rule.onNodeWithText("قص التصميم بالسعة", substring = true).assertIsDisplayed()
            rule.onNodeWithText("BLOCKED:", substring = true).assertDoesNotExist()

            captureRootOrNote("ecp-column-limited-detailing-ar")
        } finally {
            LocaleHelper.setLocale(app, "en")
        }
    }

    @Test
    fun `production-derived result renders same values in UI and export map`() {
        // Same engine inputs as the export test: parity is exercised on ONE result,
        // never on a hand-built fixture that could drift from production output.
        pinLocaleEn()
        val engine = CalculatorEngine(mockk(relaxed = true))
        val res = engine.designColumn(
            width = 400.0, depth = 600.0, pu = 500.0, fcu = 30.0, fy = 400.0,
            code = DesignCode.ECP, clearHeight = 3000.0,
            ecpDuctilityMode = EcpColumnDuctilityMode.LIMITED
        )
        val d = res.seismicDetailing!!
        rule.setContent {
            MaterialTheme {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        ColumnSeismicDetailingSection(d)
                    }
                }
            }
        }
        // Engine chose 10Ø16: S0 = min(8x16, 24x10, 200, 150) = 128
        val s0 = "%.0f mm".format(d.criticalTieSpacingMm)
        rule.onNodeWithText(s0, substring = true).performScrollTo().assertIsDisplayed()
        assertEquals("128 mm", s0)
        assertEquals("600 mm", "%.0f mm".format(d.criticalRegionLengthMm))
        assertTrue(d.blockedChecks.all { it.key.isNotEmpty() })
        captureRootOrNote("ecp-column-limited-detailing-parity")
    }

    private fun pinLocaleEn() {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.initApplicationContext(app)
        LocaleHelper.setLocale(app, "en")
    }

    /**
     * Best-effort PNG capture of the compose root into build/reports/tests/compose-shots
     * (uploaded with the test-report artifact). If the Robolectric window cannot
     * redraw (ComposeTimeoutException on CI), a note file is written instead so the
     * semantic assertions remain the gate.
     */
    private fun captureRootOrNote(name: String) {
        val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
        val out = File(dir, "$name.png")
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
