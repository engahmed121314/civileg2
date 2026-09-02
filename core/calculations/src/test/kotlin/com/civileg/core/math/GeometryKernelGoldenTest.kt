package com.civileg.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * PHASE 03 golden gates — polygon section properties, hand-derived.
 */
class GeometryKernelGoldenTest {

    @Test
    fun `rectangle 200x400 matches closed form`() {
        val poly = RectangleSection(b = 200.0, h = 400.0).toPolygon()
        assertEquals(80_000.0, poly.area, 1e-6)
        assertEquals(100.0, poly.centroidX, 1e-9)
        assertEquals(200.0, poly.centroidY, 1e-9)
        // Ix = b h^3 / 12 = 200 * 64e6 / 12
        assertEquals(200.0 * 400.0 * 400.0 * 400.0 / 12.0, poly.ixCentroidal, 1e-3)
        // Iy = h b^3 / 12 = 400 * 8e6 / 12
        assertEquals(400.0 * 200.0 * 200.0 * 200.0 / 12.0, poly.iyCentroidal, 1e-3)
        // symmetric -> zero product of inertia
        assertEquals(0.0, poly.ixyCentroidal, 1e-6)
    }

    @Test
    fun `right triangle 300x200 matches textbook values`() {
        // vertices (0,0), (300,0), (0,200)
        val tri = PolygonSection(listOf(Point2D(0.0, 0.0), Point2D(300.0, 0.0), Point2D(0.0, 200.0)))
        assertEquals(30_000.0, tri.area, 1e-6)
        assertEquals(100.0, tri.centroidX, 1e-9)          // a/3
        assertEquals(200.0 / 3.0, tri.centroidY, 1e-9)     // h/3
        // centroidal Ixc = b h^3 / 36 = 300 * 8e6 / 36
        assertEquals(300.0 * 200.0 * 200.0 * 200.0 / 36.0, tri.ixCentroidal, 1e-3)
        // centroidal Iyc = h a^3 / 36 = 200 * 27e6 / 36
        assertEquals(200.0 * 300.0 * 300.0 * 300.0 / 36.0, tri.iyCentroidal, 1e-3)
        // global about the base: b h^3 / 12
        assertEquals(2.0e8, tri.ixGlobal, 1e-3)
    }

    @Test
    fun `clockwise winding gives identical magnitudes`() {
        val ccw = PolygonSection(listOf(Point2D(0.0, 0.0), Point2D(300.0, 0.0), Point2D(0.0, 200.0)))
        val cw = PolygonSection(listOf(Point2D(0.0, 200.0), Point2D(300.0, 0.0), Point2D(0.0, 0.0)))
        assertEquals(ccw.area, cw.area, 1e-9)
        assertEquals(ccw.ixCentroidal, cw.ixCentroidal, 1e-6)
        assertEquals(ccw.centroidX, cw.centroidX, 1e-9)
    }

    @Test
    fun `closed-form sections match hand values`() {
        val rect = RectangleSection(250.0, 600.0)
        assertEquals(150_000.0, rect.area, 1e-9)
        assertEquals(250.0 * 600.0 * 600.0 * 600.0 / 12.0, rect.ix, 1e-6)
        assertEquals(250.0 * 600.0 * 600.0 / 6.0, rect.sx, 1e-6)

        val circ = CircleSection(500.0)
        assertEquals(kotlin.math.PI * 62_500.0, circ.area, 1e-6)
        assertEquals(kotlin.math.PI * 500.0 * 500.0 * 500.0 * 500.0 / 64.0, circ.ix, 1e-3)
    }

    @Test
    fun `degenerate polygons are rejected loudly`() {
        assertThrows(IllegalArgumentException::class.java) {
            PolygonSection(listOf(Point2D(0.0, 0.0), Point2D(100.0, 0.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PolygonSection(listOf(Point2D(0.0, 0.0), Point2D(10.0, 0.0), Point2D(20.0, 0.0)))
        }
    }
}
