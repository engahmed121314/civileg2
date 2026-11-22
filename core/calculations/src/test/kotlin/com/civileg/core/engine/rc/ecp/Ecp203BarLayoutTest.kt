package com.civileg.core.engine.rc.ecp

import kotlin.math.PI
import org.junit.Assert.*
import org.junit.Test

class Ecp203BarLayoutTest {
    private val e = Ecp203BarLayout
    private fun layout(layers: List<Ecp203BarLayout.Layer>, width: Double = 120.0,
        height: Double = 350.0, cover: Double = 20.0, links: Double = 8.0, aggregate: Double = 12.0) =
        e.assess(width, height, cover, links, aggregate, layers)
    private fun row(n: Int, phi: Double = 12.0) = Ecp203BarLayout.Layer(n, phi)

    @Test fun `actual cover link and main bar radius determine single-layer depth`() {
        val r = layout(listOf(row(2)))
        assertTrue(r.fitsInsideLinks)
        assertEquals(316.0, r.effectiveDepthMm!!, 1e-12)
        assertEquals(2.0 * PI * 36.0, r.providedSteelMm2, 1e-10)
        assertEquals(18.0, r.minimumClearGapMm, 0.0)
    }
    @Test fun `four bars do not fit merely because four was an old fixed cap`() {
        val r = layout(listOf(row(4)))
        assertFalse(r.fitsInsideLinks)
        assertNull(r.effectiveDepthMm)
    }
    @Test fun `two layers use area centroid rather than first-row effective depth`() {
        val r = layout(listOf(row(2), row(2)))
        assertEquals(listOf(34.0, 64.0), r.layerCentersFromTensionFaceMm)
        assertEquals(301.0, r.effectiveDepthMm!!, 1e-12)
    }
    @Test fun `aggregate controls horizontal and vertical clear gap`() {
        val r = layout(listOf(row(2), row(2)), aggregate = 16.0)
        assertEquals(24.0, r.minimumClearGapMm, 0.0)
        assertEquals(listOf(34.0, 70.0), r.layerCentersFromTensionFaceMm)
        assertEquals(298.0, r.effectiveDepthMm!!, 1e-12)
    }
    @Test fun `largest individual diameter can control gap without a guessed25mm floor`() {
        val r = layout(listOf(row(2, 20.0)), width = 150.0, aggregate = 8.0)
        assertEquals(20.0, r.minimumClearGapMm, 0.0)
        assertTrue(r.fitsInsideLinks)
    }
    @Test fun `unequal layers use their actual area weights`() {
        val r = layout(listOf(row(2, 12.0), row(1, 16.0)), aggregate = 8.0)
        // gap16; y1=34, y2=64; area weights288:256 before common pi/4.
        assertEquals(350.0 - (288.0 * 34.0 + 256.0 * 64.0) / 544.0, r.effectiveDepthMm!!, 1e-10)
    }
    @Test fun `too many vertical layers fail even with sufficient width`() {
        assertNull(layout(List(12) { row(2) }, width = 200.0).effectiveDepthMm)
    }
    @Test fun `cover is not silently ignored`() {
        val a = layout(listOf(row(2)), width = 160.0)
        val b = layout(listOf(row(2)), width = 160.0, cover = 30.0)
        assertEquals(a.effectiveDepthMm!! - 10.0, b.effectiveDepthMm!!, 1e-12)
        assertFalse(layout(listOf(row(2)), cover = 60.0).fitsInsideLinks)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `aggregate must be explicit and positive`() { layout(listOf(row(2)), aggregate = 0.0) }
    @Test(expected = IllegalArgumentException::class)
    fun `empty reinforcement is not a layout`() { layout(emptyList()) }
    @Test(expected = IllegalArgumentException::class)
    fun `zero count rejected`() { layout(listOf(row(0))) }
    @Test(expected = IllegalArgumentException::class)
    fun `nonfinite cover rejected`() { layout(listOf(row(2)), cover = Double.NaN) }
}
