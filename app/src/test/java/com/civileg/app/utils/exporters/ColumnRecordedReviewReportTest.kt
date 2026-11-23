package com.civileg.app.utils.exporters

import com.civileg.app.BuildConfig
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Synthetic presentation fixtures, not design-method or native application acceptance. */
class ColumnRecordedReviewReportTest {
    private val output get() = File("build/reports/tests/column-recorded-review/${BuildConfig.FLAVOR}").apply { mkdirs() }
    private fun pages(file: File): List<String> = PdfDocument(PdfReader(file)).use { pdf ->
        (1..pdf.numberOfPages).map {
            PdfTextExtractor.getTextFromPage(pdf.getPage(it)).replace(Regex("\\s+"), " ")
        }
    }

    @Test fun `recorded mode preserves both flags and supplied trace without certifying either`() {
        for (flag in listOf(false, true)) for (hasTrace in listOf(false, true)) {
            val trace = if (hasTrace) listOf(ProfessionalEnglishPdfReporter.CalculationStep(1,
                "Synthetic recorded trace marker", "recorded equation marker", "unverified reference marker", "17"))
                else emptyList()
            val file = requireNotNull(ProfessionalEnglishPdfReporter.generateReport(
                reportType = ProfessionalEnglishPdfReporter.ReportType.COLUMN,
                title = "SYNTHETIC COLUMN RECORDED REVIEW FIXTURE",
                subtitle = "Presentation regression only, not an engine result",
                designCode = "ECP 203 requested",
                inputs = mapOf("Code Edition Status" to "Not verified"),
                results = mapOf("Raw scalar" to "17"),
                safetyChecks = listOf(ProfessionalEnglishPdfReporter.SafetyCheck("Raw synthetic check", 17.0, 20.0, "kN", flag)),
                isSafe = flag,
                calculationSteps = trace,
                bbsRows = listOf(ProfessionalEnglishPdfReporter.BbsRow("C1", "B1", 20, 0, 9, 4200.0, 94.0)),
                boqRows = listOf(ProfessionalEnglishPdfReporter.BoqRow("1", "Raw divergent steel estimate", "kg", 72.55)),
                config = ProfessionalEnglishPdfReporter.ReportConfig(showSignature = true),
                outputPath = File(output, "synthetic-flag-$flag-trace-$hasTrace.pdf").absolutePath,
                presentation = ProfessionalEnglishPdfReporter.ReportPresentation.COLUMN_RECORDED_REVIEW
            ))
            val pageText = pages(file)
            val text = pageText.joinToString(" ")
            assertTrue(text, text.contains("Raw engine isSafe=$flag"))
            assertTrue(text, text.contains("Recorded $flag"))
            assertFalse(text, text.contains("Design Complies with Code"))
            assertFalse(text, text.contains("STATUS: UNSAFE"))
            assertFalse(text, text.contains("Applicable design basis"))
            assertFalse(text, text.contains("Engineering Detail"))
            assertFalse(text, text.contains("include the generated detail drawing"))
            assertFalse(text, text.contains("Approval & Signature"))
            assertTrue(text, text.contains("Requested code"))
            assertTrue(text, text.contains("UNAVAILABLE"))
            assertTrue(text, text.contains("Recorded BBS Estimates (UNVERIFIED)"))
            assertTrue(text, text.contains("Recorded BOQ Estimates (UNVERIFIED)"))
            assertTrue(text, text.contains("94.00"))
            assertTrue(text, text.contains("72.55"))
            assertTrue(pageText.toString(), pageText.all { it.contains("NOT FOR CONSTRUCTION") })
            pageText.forEachIndexed { index, page -> assertTrue(page, page.contains("Page ${index + 1}")) }
            if (hasTrace) {
                assertTrue(text, text.contains("Synthetic recorded trace marker"))
                assertTrue(text, text.contains("recorded equation marker"))
                assertTrue(text, text.contains("Recorded unverified ref:"))
            } else {
                assertTrue(text, text.contains("No calculation trace was supplied"))
                assertFalse(text, text.contains("Longitudinal Reinforcement"))
            }
        }
    }

    @Test fun `unverified quantity warnings repeat on continuation pages`() {
        val file = requireNotNull(ProfessionalEnglishPdfReporter.generateReport(
            reportType = ProfessionalEnglishPdfReporter.ReportType.COLUMN,
            title = "SYNTHETIC LONG RECORDED REVIEW", subtitle = "Pagination fixture only",
            designCode = "Requested code", inputs = emptyMap(), results = emptyMap(),
            safetyChecks = emptyList(), isSafe = true,
            bbsRows = (1..80).map { ProfessionalEnglishPdfReporter.BbsRow("R$it", "B$it", 20, 0, 9, 4200.0, 94.0) },
            boqRows = (1..80).map { ProfessionalEnglishPdfReporter.BoqRow(it.toString(), "Synthetic record $it", "kg", 72.55) },
            outputPath = File(output, "synthetic-long-schedule.pdf").absolutePath,
            presentation = ProfessionalEnglishPdfReporter.ReportPresentation.COLUMN_RECORDED_REVIEW
        ))
        val pageText = pages(file)
        assertTrue(pageText.toString(), pageText.count { it.contains("UNVERIFIED legacy BBS estimates") } >= 2)
        assertTrue(pageText.toString(), pageText.count { it.contains("UNVERIFIED legacy BOQ estimates") } >= 2)
        assertTrue(pageText.toString(), pageText.all { it.contains("NOT FOR CONSTRUCTION") })
        pageText.forEachIndexed { index, page -> assertTrue(page, page.contains("Page ${index + 1}")) }
    }

    @Test fun `legacy adapter does not invent an edition when recorded code is missing`() {
        val file = requireNotNull(ProfessionalEnglishPdfReporter.generateReportLegacy(
            titleAr = "", titleEn = "SYNTHETIC COLUMN REVIEW", subtitle = "Presentation fixture",
            designType = "Column", inputs = emptyMap(), results = emptyMap(),
            safetyChecks = emptyList<GenericSafetyCheck>(), isSafe = true, drawingBitmap = null,
            outputPath = File(output, "synthetic-legacy-no-code.pdf").absolutePath,
            presentation = ProfessionalEnglishPdfReporter.ReportPresentation.COLUMN_RECORDED_REVIEW
        ))
        val text = pages(file).joinToString(" ")
        assertTrue(text, text.contains("Requested code not recorded"))
        assertFalse(text, text.contains("ACI 318-19 / ECP 203-2025"))
    }

    @Test fun `non opted in report behavior remains unchanged for both raw flags`() {
        for (flag in listOf(false, true)) {
            val file = requireNotNull(ProfessionalEnglishPdfReporter.generateReport(
                reportType = ProfessionalEnglishPdfReporter.ReportType.BEAM, title = "SYNTHETIC DEFAULT BEAM FIXTURE",
                subtitle = "Backward compatibility fixture", designCode = "Requested test code",
                inputs = emptyMap(), results = emptyMap(), safetyChecks = emptyList(), isSafe = flag,
                outputPath = File(output, "synthetic-default-$flag.pdf").absolutePath
            ))
            val text = pages(file).joinToString(" ")
            assertTrue(text, text.contains(if (flag) "STATUS: SAFE - Design Complies with Code" else "STATUS: UNSAFE - Design Review Required"))
            assertTrue(text, text.contains("Applicable design basis"))
            assertFalse(text, text.contains("RECORDED COLUMN ONLY"))
        }
    }
}
