package com.civileg.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DxfBitmapRendererTest {
    @Test
    fun parsesTheEntitiesOfATankSheet() {
        val drawing = ElementDxfDrawings.tankSheet(
            "TANK", true, 4460.0, 4460.0, 3500.0, 300.0, 400.0, 40.0, 14.0, 100.0, 12.0, 150.0
        )
        val prims = DxfBitmapRenderer.parse(DxfExporter().renderToString(drawing))
        assertTrue(prims.any { it is DxfBitmapRenderer.Prim.Circle })
        assertTrue(prims.any { it is DxfBitmapRenderer.Prim.Line })
        assertTrue(prims.filterIsInstance<DxfBitmapRenderer.Prim.Text>().any { it.s.contains("SECTION A-A") })
    }

    @Test
    fun parsesTextLineAndCircleValues() {
        val dxf = "0\nSECTION\n2\nENTITIES\n0\nLINE\n8\nWALLS\n10\n1\n20\n2\n30\n0\n11\n3\n21\n4\n31\n0\n0\nCIRCLE\n8\nREBAR\n10\n5\n20\n6\n40\n7\n0\nENDSEC\n0\nEOF\n"
        val p = DxfBitmapRenderer.parse(dxf)
        assertEquals(2, p.size)
        assertEquals(DxfBitmapRenderer.Prim.Line("WALLS", 1.0, 2.0, 3.0, 4.0), p[0])
        assertEquals(DxfBitmapRenderer.Prim.Circle("REBAR", 5.0, 6.0, 7.0), p[1])
    }
}
