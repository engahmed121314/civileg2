package com.civileg.app.utils

import com.civileg.app.domain.drawing.*
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter as Reporter
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

/** Real iText output checks. These do not certify any structural-design method. */
class EnglishReportEvidenceTest {
    private fun report(
        drawings: DesignDrawingDocument? = null,
        steps: List<Reporter.CalculationStep> = emptyList(),
        checks: List<Reporter.SafetyCheck> = emptyList(),
        safe: Boolean = true,
        advice: List<Reporter.UnsafeDesignRecommendation> = emptyList()
    ): File {
        val file = File.createTempFile("report-evidence-", ".pdf")
        file.deleteOnExit()
        return requireNotNull(Reporter.generateReport(
            Reporter.ReportType.BEAM, "Rendering regression fixture", "Not an engineering design", "Fixture code",
            mapOf("Width" to "300 mm"), mapOf("Outcome" to "Fixture only"), checks, safe,
            calculationSteps = steps, outputPath = file.path, drawingDocument = drawings, recommendations = advice
        ))
    }

    private fun pages(file: File): List<String> = PdfDocument(PdfReader(file)).use { pdf ->
        (1..pdf.numberOfPages).map { PdfTextExtractor.getTextFromPage(pdf.getPage(it)) }
    }

    private fun drawingDocument(): DesignDrawingDocument {
        val kinds = listOf(DrawingViewKind.PLAN, DrawingViewKind.SIDE, DrawingViewKind.ELEVATION,
            DrawingViewKind.SECTION, DrawingViewKind.SECTION, DrawingViewKind.SECTION, DrawingViewKind.SECTION)
        val views = kinds.mapIndexed { index, kind ->
            DesignDrawingView("view-$index", kind, DrawingScene("Fixture view $index", Rect2D(0.0, 0.0, 120.0, 100.0), listOf(
                Primitive.Polyline(listOf(Point2D(10.0, 10.0), Point2D(110.0, 10.0), Point2D(110.0, 85.0), Point2D(10.0, 85.0)), true, DrawingLayer.OUTLINE),
                Primitive.Text("VIEW MARKER $index", Point2D(20.0, 70.0), 4.0, DrawingLayer.TEXT),
                Primitive.RebarDot(Point2D(30.0, 30.0), 6.0, DrawingLayer.REBAR_BOTTOM),
                Primitive.Dimension(Point2D(10.0, 5.0), Point2D(110.0, 5.0), Point2D(45.0, 6.0), "100 mm")
            )))
        }
        return DesignDrawingDocument("input-result-fixture-01", "B-FIXTURE", "Renderer regression only", views,
            listOf("Anchorage and bar placement not verified by this fixture"))
    }

    @Test fun `absent trace never becomes code-labelled generic equations`() {
        val text = pages(report()).joinToString("\n")
        assertTrue(text.contains("Calculation trace not supplied"))
        assertFalse(text.contains("Wu =")); assertFalse(text.contains("phi = 0.9"))
        assertFalse(text.contains("Design Complies with Code"))
        assertTrue(text.contains("CHECK EVIDENCE INCOMPLETE"))
        assertTrue(text.contains("Separate Plan, Side, Elevation"))
    }

    @Test fun `actual supplied trace and references are preserved`() {
        val step = Reporter.CalculationStep(7, "Supplied fixture trace", "x = 17 + 4", "Fixture source page 12", "21")
        val text = pages(report(steps = listOf(step))).joinToString("\n")
        assertTrue(text.contains("x = 17 + 4")); assertTrue(text.contains("Fixture source page 12"))
        assertFalse(text.contains("Calculation trace not supplied"))
    }

    @Test fun `failed supplied check overrides optimistic flag in report wording`() {
        val text = pages(report(checks = listOf(Reporter.SafetyCheck("Fixture failed check", 9.0, 8.0, "mm", false)))).joinToString("\n")
        assertTrue(text.contains("REVIEW REQUIRED")); assertFalse(text.contains("SUPPLIED CHECKS PASS"))
        assertTrue(text.contains("No automatic redesign"))
    }

    @Test fun `passing finite checks are bounded not code certification`() {
        val text = pages(report(checks = listOf(Reporter.SafetyCheck("Fixture check", 7.0, 8.0, "mm", true)))).joinToString("\n")
        assertTrue(text.contains("SUPPLIED CHECKS PASS")); assertFalse(text.contains("Complies with Code"))
    }

    @Test fun `nonfinite checks cannot receive passing overall banner`() {
        val text = pages(report(checks = listOf(Reporter.SafetyCheck("Missing", Double.NaN, 8.0, "mm", true)))).joinToString("\n")
        assertTrue(text.contains("CHECK EVIDENCE INCOMPLETE")); assertFalse(text.contains("SUPPLIED CHECKS PASS"))
    }

    @Test fun `source-based action is output verbatim without inferred advice`() {
        val text = pages(report(safe = false, advice = listOf(Reporter.UnsafeDesignRecommendation(
            "Fixture failed check", "Fixture action supplied by caller", "Fixture source p.14"
        )))).joinToString("\n")
        assertTrue(text.contains("Fixture action supplied by caller")); assertTrue(text.contains("Fixture source p.14"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unreferenced advice cannot be represented as source based`() {
        Reporter.UnsafeDesignRecommendation("Shear", "Increase section", "")
    }

    @Test fun `primary views and unlimited sections occupy separate full pages`() {
        val pages = pages(report(drawings = drawingDocument()))
        val drawingPages = pages.filter { it.contains("VIEW MARKER") }
        assertEquals(7, drawingPages.size)
        drawingPages.forEachIndexed { index, text ->
            assertTrue(text.contains("VIEW MARKER $index"))
            assertEquals(1, Regex("VIEW MARKER").findAll(text).count())
            assertTrue(text.contains("input-result-fixture-01")); assertTrue(text.contains("OPEN CHECKS"))
        }
        assertTrue(drawingPages[0].contains("Plan - B-FIXTURE"))
        assertTrue(drawingPages[1].contains("Side - B-FIXTURE"))
        assertTrue(drawingPages[2].contains("Elevation - B-FIXTURE"))
    }

    @Test fun `page numbers identify each physical page rather than total pages at flush`() {
        val pages = pages(report(drawings = drawingDocument()))
        pages.forEachIndexed { index, text -> assertTrue("Wrong page label on ${index + 1}", text.contains("Page ${index + 1}")) }
    }

    @Test fun `long provenance and open checks cannot spill into full-page views`() {
        val base = drawingDocument()
        val document = DesignDrawingDocument(base.revisionId, base.memberMark,
            "Fixture provenance statement. ".repeat(60), base.views,
            (1..70).map { "Fixture unresolved check $it, full detail retained, not structural advice" })
        val pages = pages(report(drawings = document))
        assertEquals(7, pages.count { it.contains("VIEW MARKER") })
        assertTrue(pages.joinToString("\n").contains("Fixture unresolved check 70"))
        pages.filter { it.contains("VIEW MARKER") }.forEach { assertEquals(1, Regex("VIEW MARKER").findAll(it).count()) }
    }

    @Test fun `legacy reports do not invent a missing design code edition`() {
        val file = File.createTempFile("legacy-report-", ".pdf").apply { deleteOnExit() }
        requireNotNull(Reporter.generateReportLegacy("", "Legacy fixture", "Not a design", "Beam", emptyMap(), emptyMap(),
            emptyList(), true, null, file.path))
        val text = pages(file).joinToString("\n")
        assertTrue(text.contains("Design Code: Not supplied")); assertFalse(text.contains("203-2025"))
    }

    @Test fun `Arabic device locale does not change report headings or numeric checks`() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale("ar", "EG"))
            val text = pages(report(checks = listOf(Reporter.SafetyCheck("Fixture check", 7.25, 8.0, "mm", true)))).joinToString("\n")
            assertTrue(text.contains("Design Parameters")); assertTrue(text.contains("7.25"))
            assertFalse(text.any { it in '\u0600'..'\u06FF' })
        } finally { Locale.setDefault(old) }
    }
}
