package com.civileg.app.utils.exporters

import com.civileg.app.domain.entities.CalculationStep
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
            results = mapOf("rho" to "0.0125"),
            calculationSteps = listOf(
                ProfessionalEnglishPdfReporter.CalculationStep(
                    1, "Slenderness",
                    "lambda = kl-u-r = 42.5, rho = 0.0125, delta = 3.2 mm, phi = 0.65, sqrt(fcu) = 6.32, dia 16 mm",
                    "ECP 203 step", "OK"
                )
            )
        )
        assertNotNull(file)
        val f = file!!
        stage(f, "glyphs.pdf")
        val all = pageTexts(f).joinToString("\n")
        assertTrue("rho text lost", all.contains("rho"))
        assertTrue("delta text lost", all.contains("delta"))
        assertTrue("kl-u-r text lost", all.contains("kl-u-r"))
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
}
