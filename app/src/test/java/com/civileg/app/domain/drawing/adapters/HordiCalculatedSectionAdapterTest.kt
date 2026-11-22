package com.civileg.app.domain.drawing.adapters

import com.civileg.app.domain.drawing.*
import com.civileg.app.domain.entities.HordiCalculatedSectionSnapshot
import com.civileg.app.domain.entities.SlabCalculatedSection
import com.google.gson.Gson
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

/** Calculation-produced synthetic fixtures, not a user's project or as-built geometry. */
class HordiCalculatedSectionAdapterTest {
    internal data class Fixture(
        val revisionId: String,
        val requiredSteelMm2: Double,
        val providedSteelMm2: Double,
        val methodReference: String,
        val section: SlabCalculatedSection,
        val incompleteChecks: List<String>
    ) {
        fun handoff(sectionOverride: SlabCalculatedSection = section, area: Double = providedSteelMm2) =
            HordiCalculatedSectionSnapshot(revisionId, sectionOverride, requiredSteelMm2, area, methodReference, incompleteChecks)
    }

    internal fun fixture(name: String): Fixture {
        val path = "/drawing/hordi/$name-output.json"
        val text = requireNotNull(javaClass.getResourceAsStream(path)) { path }.bufferedReader().use { it.readText() }
        return Gson().fromJson(text, Fixture::class.java)
    }

    @Test fun `bottom bars retain exact frozen web-relative positions and diameter`() {
        val f = fixture("bottom-two-bars")
        val drawing = HordiCalculatedSectionAdapter.build(f.handoff(), "H1")
        val dots = drawing.view.scene.primitives.filterIsInstance<Primitive.RebarDot>()
        assertEquals(listOf(Point2D(243.0, 43.0), Point2D(357.0, 43.0)), dots.map { it.center })
        assertEquals(listOf(20.0, 20.0), dots.map { it.diameterMm })
        assertEquals(f.revisionId, drawing.revisionId)
        assertEquals(f.methodReference, drawing.methodReference)
        assertEquals(DrawingViewKind.SECTION, drawing.view.kind)
        assertFalse(drawing.executionExportAllowed)
    }

    @Test fun `negative moment does not shrink physical topping to analytical width`() {
        val f = fixture("top-two-bars")
        val drawing = HordiCalculatedSectionAdapter.build(f.handoff(), "H2")
        assertEquals(200.0, f.section.analyticalFlangeWidthMm, 0.0)
        assertEquals(600.0, f.section.enteredRibPitchMm, 0.0)
        assertTrue(drawing.view.scene.primitives.any { it is Primitive.Line && it.p1 == Point2D(0.0, 350.0) && it.p2 == Point2D(600.0, 350.0) })
        val dots = drawing.view.scene.primitives.filterIsInstance<Primitive.RebarDot>()
        assertEquals(listOf(Point2D(243.0, 307.0), Point2D(357.0, 307.0)), dots.map { it.center })
        assertTrue(dots.all { it.layer == DrawingLayer.REBAR_TOP })
    }

    @Test fun `multiple layers are not flattened or replaced with default rows`() {
        val f = fixture("bottom-two-layers")
        val dots = HordiCalculatedSectionAdapter.build(f.handoff(), "H3").view.scene.primitives.filterIsInstance<Primitive.RebarDot>()
        assertEquals(4, dots.size)
        assertEquals(listOf(Point2D(243.0, 43.0), Point2D(300.0, 43.0), Point2D(357.0, 43.0), Point2D(300.0, 93.0)), dots.map { it.center })
    }

    @Test fun `all source holds survive and no closed link or hook path is invented`() {
        val f = fixture("bottom-two-bars")
        val drawing = HordiCalculatedSectionAdapter.build(f.handoff(), "H1")
        assertTrue(drawing.incompleteChecks.containsAll(f.incompleteChecks))
        assertTrue(drawing.incompleteChecks.containsAll(f.section.incompleteGeometry))
        assertFalse(drawing.view.scene.primitives.any { it.layer == DrawingLayer.REBAR_STIRRUP })
        assertFalse(drawing.view.scene.primitives.filterIsInstance<Primitive.Polyline>().any { it.isClosed })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing coordinate budget output cannot silently draw default steel`() {
        val f = fixture("bottom-two-bars")
        HordiCalculatedSectionAdapter.build(f.handoff(f.section.copy(bars = null)), "H1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `inconsistent coordinate area cannot be presented as the frozen steel result`() {
        val f = fixture("bottom-two-bars")
        HordiCalculatedSectionAdapter.build(f.handoff(area = 1000.0), "H1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonfinite coordinates are rejected`() {
        val f = fixture("bottom-two-bars")
        val bars = requireNotNull(f.section.bars).toMutableList()
        bars[0] = bars[0].copy(xFromWebLeftMm = Double.NaN)
        HordiCalculatedSectionAdapter.build(f.handoff(f.section.copy(bars = bars)), "H1")
    }

    @Test fun `review PDF is one SECTION with digest and every hold never primary views`() {
        for (name in listOf("bottom-two-bars", "top-two-bars", "bottom-two-layers")) {
            val f = fixture(name)
            val bytes = HordiCalculatedSectionAdapter.renderReviewPdfBytes(f.handoff(), "H1")
            PdfDocument(PdfReader(ByteArrayInputStream(bytes))).use { pdf ->
                assertEquals(1, pdf.numberOfPages)
                val text = PdfTextExtractor.getTextFromPage(pdf.getPage(1), SimpleTextExtractionStrategy())
                    .replace(Regex("-\\s*\\n\\s*"), "-").replace(Regex("\\s+"), " ")
                assertTrue(text.contains(f.revisionId))
                assertTrue(text.contains("REVIEW ONLY, NOT FOR CONSTRUCTION"))
                assertTrue(text.contains("Execution exports remain blocked"))
                assertTrue(text.contains("Required As"))
                assertTrue(text.contains("provided As"))
                assertTrue(text.contains("SUPPLIED LINKS"))
                (f.incompleteChecks + f.section.incompleteGeometry).distinct().forEach { assertTrue("Missing hold: $it", text.contains(it)) }
                assertFalse(text.contains("Plan -")); assertFalse(text.contains("Side -")); assertFalse(text.contains("Elevation -"))
            }
        }
    }
}
