package com.civileg.app.domain.drawing

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class DrawingFidelityTest {
    private fun scene(primitives: List<Primitive> = listOf(
        Primitive.Line(Point2D(10.0, 10.0), Point2D(100.0, 100.0), DrawingLayer.OUTLINE)
    )) = DrawingScene("Render fixture, not an engineering design", Rect2D(0.0, 0.0, 120.0, 120.0), primitives)

    private fun primary() = listOf(DrawingViewKind.PLAN, DrawingViewKind.SIDE, DrawingViewKind.ELEVATION)
        .map { DesignDrawingView(it.name, it, scene()) }

    @Test fun `offset bounds fit uniformly without stretching`() {
        val viewport = DrawingGeometry.fit(Rect2D(-50.0, 20.0, 150.0, 120.0), 400.0, 400.0)
        assertEquals(Point2D(0.0, 100.0), viewport.map(Point2D(-50.0, 20.0)))
        assertEquals(Point2D(400.0, 300.0), viewport.map(Point2D(150.0, 120.0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid bounds cannot generate a blank successful PDF`() {
        PdfRenderer.renderToPdfBytes(scene().copy(bounds = Rect2D(0.0, 0.0, 0.0, 100.0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonfinite primitive rejected`() {
        DrawingGeometry.validate(scene(listOf(Primitive.Circle(Point2D(Double.NaN, 0.0), 3.0, DrawingLayer.OUTLINE))))
    }

    @Test fun `arc wraps counterclockwise through zero`() {
        val arc = Primitive.Arc(Point2D(0.0, 0.0), 10.0, 350.0, 10.0, DrawingLayer.REBAR_STIRRUP)
        val points = DrawingGeometry.arcPoints(arc)
        assertTrue(points.size > 2)
        assertEquals(10.0, points[points.size / 2].x, 0.01)
        assertTrue(points.first().y < 0 && points.last().y > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `three cross sections cannot masquerade as primary views`() {
        DesignDrawingDocument("input-result-1", "B1", "Fixture method", (1..3).map {
            DesignDrawingView("S$it", DrawingViewKind.SECTION, scene())
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `annotation-only primary view cannot claim a design drawing`() {
        DesignDrawingView("P", DrawingViewKind.PLAN, scene(listOf(
            Primitive.Text("Plan", Point2D(10.0, 10.0), 3.0, DrawingLayer.TEXT),
            Primitive.Dimension(Point2D(10.0, 20.0), Point2D(100.0, 20.0), Point2D(40.0, 22.0), "90 mm")
        )))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `geometry outside declared bounds is rejected rather than cropped`() {
        DrawingGeometry.validate(scene(listOf(Primitive.Circle(Point2D(119.0, 119.0), 10.0, DrawingLayer.OUTLINE))))
    }

    @Test fun `more than three reinforcement sections are allowed`() {
        val sections = (1..5).map { DesignDrawingView("S$it", DrawingViewKind.SECTION, scene()) }
        val document = DesignDrawingDocument("input-result-1", "B1", "Fixture method", primary().reversed() + sections)
        assertEquals(8, document.views.size)
        assertEquals(listOf(DrawingViewKind.PLAN, DrawingViewKind.SIDE, DrawingViewKind.ELEVATION), document.orderedViews().take(3).map { it.kind })
    }

    @Test fun `caller mutation cannot change snapshotted primitive lists`() {
        val points = mutableListOf(Point2D(0.0, 0.0), Point2D(10.0, 10.0))
        val primitives = mutableListOf<Primitive>(Primitive.Polyline(points, false, DrawingLayer.OUTLINE))
        val view = DesignDrawingView("P", DrawingViewKind.PLAN, scene(primitives))
        points.clear(); primitives.clear()
        assertEquals(2, (view.scene.primitives.single() as Primitive.Polyline).points.size)
    }

    @Test fun `document copies source views and open checks`() {
        val views = primary().toMutableList()
        val open = mutableListOf("Anchorage not calculated")
        val document = DesignDrawingDocument("input-result-1", "B1", "Fixture method", views, open)
        views.clear(); open.clear()
        assertEquals(3, document.views.size)
        assertEquals(listOf("Anchorage not calculated"), document.incompleteChecks)
    }

    @Test fun `PDF contains positioned vectors and labels for every primitive`() {
        val fixture = scene(listOf(
            Primitive.Line(Point2D(5.0, 5.0), Point2D(115.0, 5.0), DrawingLayer.OUTLINE),
            Primitive.Polyline(listOf(Point2D(10.0, 20.0), Point2D(50.0, 20.0), Point2D(50.0, 55.0)), true, DrawingLayer.OUTLINE),
            Primitive.Circle(Point2D(30.0, 40.0), 8.0, DrawingLayer.REBAR_BOTTOM),
            Primitive.Arc(Point2D(80.0, 40.0), 10.0, 0.0, 180.0, DrawingLayer.REBAR_STIRRUP),
            Primitive.RebarDot(Point2D(30.0, 40.0), 4.0, DrawingLayer.REBAR_TOP),
            Primitive.Text("TEXT AT POSITION", Point2D(5.0, 100.0), 3.5, DrawingLayer.TEXT, rotationDeg = 10.0),
            Primitive.Dimension(Point2D(10.0, 70.0), Point2D(100.0, 70.0), Point2D(40.0, 73.0), "90 mm"),
            Primitive.Leader(listOf(Point2D(80.0, 40.0), Point2D(85.0, 55.0)), "LINK")
        ))
        PdfDocument(PdfReader(ByteArrayInputStream(PdfRenderer.renderToPdfBytes(fixture)))).use { pdf ->
            assertEquals(1, pdf.numberOfPages)
            val text = PdfTextExtractor.getTextFromPage(pdf.getPage(1))
            assertTrue(text.contains("TEXT AT POSITION")); assertTrue(text.contains("90 mm")); assertTrue(text.contains("LINK"))
            val stream = String(pdf.getPage(1).contentBytes, Charsets.ISO_8859_1)
            assertTrue(stream.contains(" m\n")); assertTrue(stream.contains(" l\n"))
            assertTrue(stream.contains(" c\n")); assertTrue(stream.contains("f\n")); assertTrue(stream.contains(" cm\n"))
        }
    }
}
