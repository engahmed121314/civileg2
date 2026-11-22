package com.civileg.app.utils.exporters

import com.civileg.app.domain.calculations.ColumnDesignEngine
import com.civileg.app.domain.entities.CalculationStep
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.EnglishFontProvider
import com.civileg.app.utils.ExportManager
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Export defect register guards:
 * R2 - embedded Unicode font keeps Greek/math/symbol glyphs (base-14 dropped them)
 * R3 - unit suffix not duplicated when the result text already carries it,
 *      and default steps skip titles the inputs table already shows
 * R6 - footer page numbers follow the true page sequence
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfessionalEnglishPdfReporterTest {

    @Before
    fun setUp() {
        EnglishFontProvider.init(RuntimeEnvironment.getApplication())
    }

    /** Copy into the test-report artifact dir for pixel inspection (pdftoppm). */
    private fun stage(pdf: File, name: String): File {
        val dir = File("build/reports/tests/pdf-shots").apply { mkdirs() }
        val out = File(dir, name)
        pdf.copyTo(out, overwrite = true)
        return out
    }

    private fun pageTexts(pdf: File): List<String> {
        val reader = PdfReader(pdf)
        val doc = PdfDocument(reader)
        return try {
            (1..doc.numberOfPages).map { PdfTextExtractor.getTextFromPage(doc.getPage(it)) }
        } finally {
            doc.close()
        }
    }

    @Test
    fun `embedded font keeps greek and symbol glyphs`() {
        assertTrue("test requires embedded font assets", EnglishFontProvider.isReady)
        val out = File.createTempFile("glyphs", ".pdf")
        val file = ProfessionalEnglishPdfReporter.generateReport(
            outputPath = out.absolutePath,
            title = "Glyph Guard",
            subtitle = "R2",
            reportType = ProfessionalEnglishPdfReporter.ReportType.GENERIC,
            safetyChecks = emptyList(),
            isSafe = true,
            designCode = "ECP 203-2020",
            inputs = mapOf("Section" to "300 x 800 mm"),
            results = mapOf("ρ (rho)" to "0.0125"),
            calculationSteps = listOf(
                ProfessionalEnglishPdfReporter.CalculationStep(
                    1, "Slenderness",
                    "λ = k·lu/r = 42.5, ρ = 0.0125, δ = 3.2 mm, φ = 0.65, √fcu = 6.32, ⌀16 mm, As = 1000 mm², Mu×φ = 210 kN·m",
                    "ECP 203 step", "OK"
                )
            )
        )
        assertNotNull(file)
        val f = file!!
        stage(f, "glyphs.pdf")
        val all = pageTexts(f).joinToString("\n")
        // The actual glyphs the base-14 font dropped; spelled-out names prove nothing.
        for (sym in listOf("λ", "ρ", "δ", "φ", "√", "⌀", "×", "mm²", "kN·m")) {
            assertTrue("glyph <$sym> lost from extracted text", all.contains(sym))
        }
    }

    @Test
    fun `footer page numbers follow the true sequence`() {
        assertTrue(EnglishFontProvider.isReady)
        val out = File.createTempFile("pages", ".pdf")
        val steps = (1..30).map {
            ProfessionalEnglishPdfReporter.CalculationStep(
                it, "Step $it body text to fill pages with real content lines " + "x".repeat(120),
                "value = $it", "ref $it", "result $it"
            )
        }
        val file = ProfessionalEnglishPdfReporter.generateReport(
            outputPath = out.absolutePath,
            title = "Paging Guard",
            subtitle = "R6",
            reportType = ProfessionalEnglishPdfReporter.ReportType.GENERIC,
            safetyChecks = emptyList(),
            isSafe = true,
            designCode = "ECP 203-2020",
            inputs = mapOf("A" to "1"),
            results = mapOf("B" to "2"),
            calculationSteps = steps
        )
        assertNotNull(file)
        val f = file!!
        stage(f, "paging.pdf")
        val pages = pageTexts(f)
        assertTrue("expected at least 3 pages, got ${pages.size}", pages.size >= 3)
        pages.forEachIndexed { i, text ->
            val n = i + 1
            assertTrue("page $n footer wrong or missing", text.contains("Page $n"))
        }
    }

    @Test
    fun `default steps skip titles already shown in the inputs table`() {
        assertTrue(EnglishFontProvider.isReady)
        val out = File.createTempFile("dupe", ".pdf")
        val file = ProfessionalEnglishPdfReporter.generateReport(
            outputPath = out.absolutePath,
            title = "Dupe Guard",
            subtitle = "R3",
            reportType = ProfessionalEnglishPdfReporter.ReportType.GENERIC,
            safetyChecks = emptyList(),
            isSafe = true,
            designCode = "ECP 203-2020",
            inputs = mapOf("Load Combination" to "1.4DL + 1.6LL (actual)"),
            results = mapOf("As" to "1000 mm2")
        )
        assertNotNull(file)
        val f = file!!
        stage(f, "dedupe.pdf")
        val all = pageTexts(f).joinToString("\n")
        assertTrue("inputs row lost", all.contains("1.4DL + 1.6LL (actual)"))
        assertFalse(
            "default step duplicated the load-combination text",
            all.contains("Wu = 1.4*DL + 1.6*LL (kN/m^2)")
        )
    }

    @Test
    fun `export manager does not duplicate a unit already in the result text`() {
        assertTrue(EnglishFontProvider.isReady)
        val out = File.createTempFile("units", ".pdf")
        val context = RuntimeEnvironment.getApplication()
        val bundle = ExportManager(context).exportReportAndDxf(
            titleAr = "units",
            titleEn = "Unit Guard",
            designType = "Column",
            inputs = mapOf("A" to "1"),
            results = mapOf("B" to "2"),
            calculationSteps = listOf(
                CalculationStep(
                    stepNumber = 1,
                    title = "Effective length",
                    formula = "Ho = H - hb",
                    formulaWithValues = "Ho = 3000 - 3000 = 0mm",
                    result = "Ho = 0mm",
                    unit = "mm",
                    codeReference = "ECP 203"
                ),
                CalculationStep(
                    stepNumber = 2,
                    title = "Axial",
                    formula = "Pu",
                    formulaWithValues = "Pu = 1500 kN",
                    result = "Pu = 1500 kN",
                    unit = "kN",
                    codeReference = "ECP 203"
                )
            ),
            outputPath = out.absolutePath
        )
        assertNotNull(bundle.pdfFile)
        val f = bundle.pdfFile!!
        stage(f, "units.pdf")
        val all = pageTexts(f).joinToString("\n")
        assertTrue("result text lost", all.contains("Ho = 0mm"))
        assertFalse("unit duplicated as '0mm mm'", all.contains("0mm mm"))
        assertFalse("unit duplicated as 'kN kN'", all.contains("kN kN"))
    }

    /**
     * Representative end-to-end export: run the real column engine and push its
     * steps through the production ExportManager path, not a handcrafted fixture.
     */
    @Test
    fun `integrated column engine export produces a real report`() {
        assertTrue(EnglishFontProvider.isReady)
        val result = ColumnDesignEngine.designColumn(
            b = 400.0, t = 400.0, H = 3000.0,
            beamDepthIn = 600.0, beamDepthOut = 600.0,
            cover = 40.0,
            Pu = 1500.0, MextIn = 50.0, MextOut = 0.0,
            fcu = 30.0, fy = 400.0,
            isBraced = true,
            topCond = 1, botCond = 1,
            preferredDia = 20,
            code = DesignCode.ECP
        )
        val out = File.createTempFile("integrated", ".pdf")
        val context = RuntimeEnvironment.getApplication()
        val bundle = ExportManager(context).exportReportAndDxf(
            titleAr = "عمود C1",
            titleEn = "Column C1 - Integrated Engine Export",
            designType = "Column",
            inputs = mapOf(
                "Section b x t" to "400 x 400 mm",
                "Height H" to "3000 mm",
                "Pu" to "1500 kN",
                "fcu" to "30 MPa",
                "fy" to "400 MPa"
            ),
            results = mapOf(
                "Classification" to result.columnClassification,
                "As required" to "${result.AsRequired} mm2",
                "Bars" to result.finalBars
            ),
            calculationSteps = result.calculationSteps,
            outputPath = out.absolutePath
        )
        assertNotNull(bundle.pdfFile)
        val f = bundle.pdfFile!!
        stage(f, "integrated.pdf")
        val all = pageTexts(f).joinToString("\n")
        assertTrue("engine steps lost", all.contains("Effective Length Factors"))
        assertTrue("code reference lost", all.contains("ECP 203"))
        assertTrue("result lost", all.contains(result.columnClassification))
        // The printed basis must be the editions the app is verified against,
        // never an edition string the project has no source copy of.
        assertTrue("basis missing", all.contains("ACI 318M-14 / ECP 203-2020"))
        assertFalse("unverified edition label", all.contains("ECP 203-2025"))
        assertFalse("unverified edition label", all.contains("ACI 318-19"))
    }
}
