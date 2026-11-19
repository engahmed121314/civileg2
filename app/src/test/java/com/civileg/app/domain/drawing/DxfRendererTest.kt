package com.civileg.app.domain.drawing

import org.junit.Assert.*
import org.junit.Test

class DxfRendererTest {

    @Test
    fun `test DxfRenderer produces valid DXF structure with English-only text`() {
        val scene = DrawingScene(
            title = "Test Beam",
            bounds = Rect2D(0.0, 0.0, 420.0, 297.0),
            primitives = listOf(
                Primitive.Line(
                    Point2D(10.0, 10.0), Point2D(100.0, 10.0), DrawingLayer.OUTLINE
                ),
                Primitive.Text(
                    "SECTION 1-1", Point2D(20.0, 20.0), 3.5, DrawingLayer.TEXT
                ),
                Primitive.Text(
                    "SECTION 2-2", Point2D(20.0, 40.0), 3.5, DrawingLayer.TEXT
                ),
                Primitive.Text(
                    "SECTION 3-3", Point2D(20.0, 60.0), 3.5, DrawingLayer.TEXT
                ),
                Primitive.Circle(
                    Point2D(50.0, 50.0), 8.0, DrawingLayer.REBAR_BOTTOM
                )
            )
        )

        val dxf = DxfRenderer.renderToDxf(scene)

        // DXF structure markers
        assertTrue("DXF should contain HEADER section", dxf.contains("SECTION"))
        assertTrue("DXF should contain HEADER", dxf.contains("HEADER"))
        assertTrue("DXF should contain TABLES", dxf.contains("TABLES"))
        assertTrue("DXF should contain LAYER", dxf.contains("LAYER"))
        assertTrue("DXF should contain ENTITIES", dxf.contains("ENTITIES"))
        assertTrue("DXF should end with EOF", dxf.contains("EOF"))

        // Layer table entries
        assertTrue("DXF should contain OUTLINE layer", dxf.contains("0_OUTLINE"))
        assertTrue("DXF should contain REBAR_BOTTOM layer", dxf.contains("0_REBAR_BOTTOM"))
        assertTrue("DXF should contain TEXT layer", dxf.contains("0_TEXT"))

        // Entity types
        assertTrue("DXF should contain LINE entity", dxf.contains("LINE"))
        assertTrue("DXF should contain CIRCLE entity", dxf.contains("CIRCLE"))
        assertTrue("DXF should contain TEXT entity", dxf.contains("TEXT"))

        // English-only: no Arabic glyphs
        val arabicRegex = Regex("[؀-ۿ]")
        assertFalse("DXF must not contain Arabic text (R5)", arabicRegex.containsMatchIn(dxf))

        // Section titles present
        assertTrue("DXF should contain SECTION 1-1", dxf.contains("SECTION 1-1"))
        assertTrue("DXF should contain SECTION 2-2", dxf.contains("SECTION 2-2"))
        assertTrue("DXF should contain SECTION 3-3", dxf.contains("SECTION 3-3"))
    }

    @Test
    fun `test DxfRenderer layer codes are correct`() {
        assertEquals(7, DxfRenderer.layerCode(DrawingLayer.OUTLINE))
        assertEquals(3, DxfRenderer.layerCode(DrawingLayer.REBAR_BOTTOM))
        assertEquals(5, DxfRenderer.layerCode(DrawingLayer.REBAR_TOP))
        assertEquals(1, DxfRenderer.layerCode(DrawingLayer.REBAR_STIRRUP))
        assertEquals(2, DxfRenderer.layerCode(DrawingLayer.DIMENSION))
        assertEquals(7, DxfRenderer.layerCode(DrawingLayer.TEXT))
        assertEquals(8, DxfRenderer.layerCode(DrawingLayer.HATCH))
        assertEquals(1, DxfRenderer.layerCode(DrawingLayer.CENTERLINE))
        assertEquals(7, DxfRenderer.layerCode(DrawingLayer.TITLE_BLOCK))
    }

    @Test
    fun `test DxfRenderer empty scene produces valid minimal DXF`() {
        val scene = DrawingScene(
            title = "Empty",
            bounds = Rect2D(0.0, 0.0, 420.0, 297.0),
            primitives = emptyList()
        )

        val dxf = DxfRenderer.renderToDxf(scene)
        assertTrue(dxf.contains("SECTION"))
        assertTrue(dxf.contains("ENTITIES"))
        assertTrue(dxf.contains("EOF"))
    }
}