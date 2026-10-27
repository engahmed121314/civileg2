package com.civileg.app.domain.calculations.pilegroup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PileGroupAnalysisTest {

    private lateinit var analysis: PileGroupAnalysis

    @Before
    fun setup() {
        analysis = PileGroupAnalysis()
    }

    private fun input(
        pilesX: Int = 3,
        pilesY: Int = 3,
        spacing: Double = 1500.0,
        diameter: Double = 500.0,
        length: Double = 15.0,
        capacity: Double = 1000.0,
        load: Double = 6000.0,
        mx: Double = 0.0,
        my: Double = 0.0,
        horizontal: Double = 0.0,
        allowable: Double = 25.0
    ) = PileGroupInput(
        pilesX = pilesX,
        pilesY = pilesY,
        spacingX = spacing,
        spacingY = spacing,
        pileDiameter = diameter,
        pileLength = length,
        pileCapacity = capacity,
        verticalLoad = load,
        momentX = mx,
        momentY = my,
        horizontalLoad = horizontal,
        allowableSettlement = allowable
    )

    // ── Group efficiency ──────────────────────────────────────────────

    @Test
    fun `Converse-Labarre efficiency for a three by three group`() {
        val efficiency = PileGroupAnalysis.converseLabarreEfficiency(
            pilesX = 3,
            pilesY = 3,
            pileDiameter = 500.0,
            spacing = 1500.0
        )
        assertEquals(0.72689, efficiency, 1e-4)
    }

    @Test
    fun `efficiency increases with spacing`() {
        val tight = analysis.analyze(input(spacing = 1000.0))
        val wide = analysis.analyze(input(spacing = 2000.0))
        assertTrue(wide.efficiency > tight.efficiency)
    }

    @Test
    fun `group capacity includes the efficiency factor`() {
        val result = analysis.analyze(input())
        assertEquals(0.72689 * 9.0 * 1000.0, result.groupCapacity, 0.1)
        assertEquals(9, result.numberOfPiles)
    }

    // ── Load distribution ─────────────────────────────────────────────

    @Test
    fun `symmetric load is distributed equally`() {
        val result = analysis.analyze(input())
        assertEquals(6000.0 / 9.0, result.averagePileLoad, 1e-6)
        assertEquals(6000.0 / 9.0, result.maxPileLoad, 1e-6)
        assertEquals(6000.0 / 9.0, result.minPileLoad, 1e-6)
    }

    @Test
    fun `moment distribution preserves the vertical load`() {
        val result = analysis.analyze(input(mx = 900.0))
        assertEquals(6000.0, result.pileLoads.sumOf { it.load }, 1e-6)
        assertEquals(6000.0 / 9.0 + 100.0, result.maxPileLoad, 1e-6)
        assertEquals(6000.0 / 9.0 - 100.0, result.minPileLoad, 1e-6)
    }

    @Test
    fun `moment about Y creates maximum load on the positive X side`() {
        val result = analysis.analyze(input(my = 900.0))
        val maxPile = result.pileLoads.maxByOrNull { it.load }!!
        assertEquals(1500.0, maxPile.x, 1e-9)
        val centerPile = result.pileLoads.first { it.x == 1500.0 && it.y == 0.0 }
        assertEquals(result.maxPileLoad, centerPile.load, 1e-9)
    }

    @Test
    fun `pile loads count matches the layout`() {
        val result = analysis.analyze(input(pilesX = 2, pilesY = 4))
        assertEquals(8, result.pileLoads.size)
    }

    // ── Cap moments ───────────────────────────────────────────────────

    @Test
    fun `cap moments are evaluated at the column face`() {
        val result = analysis.analyze(input())
        // 3 piles at x = 1500 mm, lever arm 1500 − 300 = 1200 mm.
        assertEquals(2400.0, result.capMomentX, 1e-6)
        assertEquals(2400.0, result.capMomentY, 1e-6)
    }

    @Test
    fun `cap moments increase with moment action`() {
        val pureAxial = analysis.analyze(input())
        val withMoment = analysis.analyze(input(mx = 900.0))
        assertTrue(withMoment.capMomentX > pureAxial.capMomentX)
    }

    // ── Settlement interaction ────────────────────────────────────────

    @Test
    fun `interaction factor is half at three diameters spacing`() {
        val result = analysis.analyze(input(spacing = 1500.0))
        assertEquals(0.5, result.interactionFactor, 1e-9)
        assertEquals(1.0 + 8.0 * 0.5, result.groupSettlementFactor, 1e-9)
    }

    @Test
    fun `group settlement exceeds the single pile settlement`() {
        val result = analysis.analyze(input())
        assertTrue(result.singlePileSettlement > 0.0)
        assertTrue(result.groupSettlement > result.singlePileSettlement)
    }

    @Test
    fun `larger spacing reduces interaction and settlement`() {
        val tight = analysis.analyze(input(spacing = 1500.0))
        val wide = analysis.analyze(input(spacing = 3000.0))
        assertTrue(wide.interactionFactor < tight.interactionFactor)
        assertTrue(wide.groupSettlement < tight.groupSettlement)
    }

    // ── Safety and validation ─────────────────────────────────────────

    @Test
    fun `well proportioned group is safe`() {
        val result = analysis.analyze(input())
        assertTrue(result.isSafe)
        assertTrue(result.checks.all { it.passed })
    }

    @Test
    fun `overloaded group is unsafe`() {
        val result = analysis.analyze(input(load = 9900.0))
        assertFalse(result.isSafe)
        assertTrue(result.maxPileUtilization > 1.0)
        assertTrue(result.warnings.any { it.contains("capacity") })
    }

    @Test
    fun `tension in a pile is flagged as uplift`() {
        val result = analysis.analyze(input(load = 600.0, mx = 900.0))
        assertTrue(result.minPileLoad < 0.0)
        assertFalse(result.isSafe)
        assertTrue(result.warnings.any { it.contains("Uplift") })
    }

    @Test
    fun `horizontal load is reported as a warning`() {
        val result = analysis.analyze(input(horizontal = 150.0))
        assertTrue(result.warnings.any { it.contains("Horizontal") })
    }

    @Test
    fun `invalid pile count is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            analysis.analyze(input(pilesX = 0))
        }
    }

    @Test
    fun `non-positive spacing is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            analysis.analyze(input(spacing = 0.0))
        }
    }

    @Test
    fun `non-positive allowable settlement is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            analysis.analyze(input(allowable = 0.0))
        }
    }
}
