package com.civileg.core.engine.rc.ecp

import org.junit.Assert.*
import org.junit.Test

class Ecp203AdoptedBarPlacementTest {
    private fun placed(layers: List<Ecp203BarLayout.Layer>, width: Double = 200.0): Pair<Ecp203BarLayout.Assessment, List<Ecp203BarLayout.PlacedBar>> {
        val assessed = Ecp203BarLayout.assess(width, 350.0, 25.0, 8.0, 20.0, layers)
        return assessed to requireNotNull(Ecp203BarLayout.adoptSymmetricRows(width, 25.0, 8.0, layers, assessed))
    }

    @Test fun `two bars adopt symmetric cage limits with the original calculated depth`() {
        val (layout, bars) = placed(listOf(Ecp203BarLayout.Layer(2, 20.0)))
        assertEquals(listOf(43.0, 157.0), bars.map { it.xFromWebLeftMm })
        assertEquals(listOf(43.0, 43.0), bars.map { it.distanceFromTensionFaceMm })
        assertEquals(307.0, layout.effectiveDepthMm!!, 0.0)
        assertEquals(listOf(0, 1), bars.map { it.indexInLayer })
    }

    @Test fun `single upper bar is centered without inventing two extra bars`() {
        val (layout, bars) = placed(listOf(Ecp203BarLayout.Layer(3, 20.0), Ecp203BarLayout.Layer(1, 20.0)))
        assertEquals(4, bars.size)
        assertEquals(listOf(43.0, 100.0, 157.0, 100.0), bars.map { it.xFromWebLeftMm })
        assertEquals(listOf(43.0, 43.0, 43.0, 93.0), bars.map { it.distanceFromTensionFaceMm })
        val area = bars.sumOf { Math.PI * it.diameterMm * it.diameterMm / 4.0 }
        val centroid = bars.sumOf { Math.PI * it.diameterMm * it.diameterMm / 4.0 * it.distanceFromTensionFaceMm } / area
        assertEquals(layout.providedSteelMm2, area, 1e-9)
        assertEquals(layout.effectiveDepthMm!!, 350.0 - centroid, 1e-9)
    }

    @Test fun `different bar diameters keep their own cage inset and exact assessed layer`() {
        val (layout, bars) = placed(listOf(Ecp203BarLayout.Layer(2, 20.0), Ecp203BarLayout.Layer(2, 16.0)))
        assertEquals(listOf(43.0, 157.0, 41.0, 159.0), bars.map { it.xFromWebLeftMm })
        assertEquals(listOf(43.0, 43.0, 91.0, 91.0), bars.map { it.distanceFromTensionFaceMm })
        bars.groupBy { it.layerIndex }.forEach { (_, row) ->
            assertEquals(200.0, row.first().xFromWebLeftMm + row.last().xFromWebLeftMm, 0.0)
            assertTrue(row.last().xFromWebLeftMm - row.first().xFromWebLeftMm - row.first().diameterMm >= layout.minimumClearGapMm)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a failed fit cannot yield fabricated coordinates`() {
        val layers = listOf(Ecp203BarLayout.Layer(8, 20.0))
        val bad = Ecp203BarLayout.assess(200.0, 350.0, 25.0, 8.0, 20.0, layers)
        assertFalse(bad.fitsInsideLinks)
        Ecp203BarLayout.adoptSymmetricRows(200.0, 25.0, 8.0, layers, bad)
    }

    @Test fun `coordinate budget is unknown output not a changed numerical assessment`() {
        val layers = listOf(Ecp203BarLayout.Layer(4097, 4.0))
        val layout = Ecp203BarLayout.assess(300000.0, 500.0, 25.0, 8.0, 4.0, layers)
        assertTrue(layout.fitsInsideLinks)
        val original = layout.providedSteelMm2
        assertNull(Ecp203BarLayout.adoptSymmetricRows(300000.0, 25.0, 8.0, layers, layout))
        assertEquals(original, layout.providedSteelMm2, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched layer list is rejected rather than truncated`() {
        val layers = listOf(Ecp203BarLayout.Layer(2, 20.0))
        val layout = Ecp203BarLayout.assess(200.0, 350.0, 25.0, 8.0, 20.0, layers)
        Ecp203BarLayout.adoptSymmetricRows(200.0, 25.0, 8.0, layers + layers, layout)
    }
}
