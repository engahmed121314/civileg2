package com.civileg.core.output.drawing

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DrawingScene and DrawingSceneDxfRenderer.
 */
class DrawingSceneTest {

    @Test
    fun drawingScene_totalPrimitivesCount_sumsAcrossViews() {
        val view1 = DrawingView(
            name = "Plan View",
            primitives = listOf(
                DrawingPrimitive.Line(0.0, 0.0, 250.0, 0.0),
                DrawingPrimitive.Circle(125.0, 125.0, 50.0)
            )
        )
        val view2 = DrawingView(
            name = "Cross Section",
            primitives = listOf(
                DrawingPrimitive.Line(0.0, 0.0, 250.0, 500.0),
                DrawingPrimitive.RebarDot(50.0, 50.0),
                DrawingPrimitive.Text("2x16", 50.0, 60.0)
            )
        )

        val scene = DrawingScene(
            sheetTitle = "BEAM B1 DETAILED DRAWING",
            views = listOf(view1, view2)
        )

        assertEquals(5, scene.totalPrimitivesCount)
    }

    @Test
    fun renderToDxf_producesValidDxfContent() {
        val view = DrawingView(
            name = "Elevation",
            primitives = listOf(
                DrawingPrimitive.Line(0.0, 0.0, 6000.0, 0.0, DrawingLayer.CONCRETE),
                DrawingPrimitive.Circle(3000.0, 250.0, 10.0, DrawingLayer.REBAR_MAIN),
                DrawingPrimitive.Text("BEAM B1", 0.0, 600.0, 30.0, DrawingLayer.TEXT),
                DrawingPrimitive.Dimension(0.0, 0.0, 6000.0, 0.0, "6000 mm", DrawingLayer.DIMENSION)
            )
        )

        val scene = DrawingScene(
            sheetTitle = "BEAM B1",
            views = listOf(view)
        )

        val dxfText = DrawingSceneDxfRenderer.renderToDxf(scene)
        assertNotNull(dxfText)
        assertTrue("DXF should contain HEADER", dxfText.contains("HEADER"))
        assertTrue("DXF should contain ENTITIES", dxfText.contains("ENTITIES"))
        assertTrue("DXF should contain EOF", dxfText.contains("EOF"))
        assertTrue("DXF should contain layer name", dxfText.contains("0_CONCRETE"))
        assertTrue("DXF should contain layer name", dxfText.contains("0_REBAR_MAIN"))
    }
}
