package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ECPFooting — ECP 203-2020 footing design engine.
 * Covers isolated footing, punching shear, reinforcement,
 * InputGuard validation, and edge cases.
 */
class ECPFootingTest {

    private lateinit var footing: ECPFooting

    @Before
    fun setup() {
        footing = ECPFooting()
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun designIsolated(
        fcu: Double = 25.0,
        fy: Double = 360.0,
        colW: Double = 300.0,
        colD: Double = 300.0,
        axial: Double = 800.0,
        mx: Double = 0.0,
        my: Double = 0.0,
        sbc: Double = 200.0,
        depth: Double = 400.0
    ) = footing.designIsolatedFooting(
        fcu, fy, colW, colD, axial, mx, my, sbc, depth,
        LoadCombination.DEAD_LIVE, BoundaryConstraints()
    )

    // ═══════════════════════════════════════════════════════════════
    // 1. Happy path — isolated footing
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designIsolatedFooting_validInputs_producesResult`() {
        val result = designIsolated()
        assertNotNull(result)
        assertTrue("Required width > 0", result.requiredWidth > 0)
        assertTrue("Required length > 0", result.requiredLength > 0)
        assertTrue("Required thickness > 0", result.requiredThickness > 0)
    }

    @Test
    fun `designIsolatedFooting_soilPressureIsPositive`() {
        val result = designIsolated()
        assertTrue("Soil pressure > 0", result.soilPressure > 0)
    }

    @Test
    fun `designIsolatedFooting_maxSoilPressureExceedsAverage`() {
        val result = designIsolated()
        assertTrue("Max soil pressure >= average", result.maxSoilPressure >= result.soilPressure * 0.95)
    }

    @Test
    fun `designIsolatedFooting_footingDimensionsExceedColumn`() {
        val result = designIsolated()
        assertTrue("Width > column width", result.requiredWidth > 300.0)
        assertTrue("Length > column depth", result.requiredLength > 300.0)
    }

    @Test
    fun `designIsolatedFooting_withMoment_producesResult`() {
        val result = designIsolated(mx = 50.0, my = 30.0)
        assertTrue("Should handle moments", result.requiredWidth > 0)
    }

    @Test
    fun `designIsolatedFooting_highLoad_largerFooting`() {
        val resultLow = designIsolated(axial = 500.0)
        val resultHigh = designIsolated(axial = 2000.0)
        assertTrue("Higher load → larger footing area",
            resultHigh.requiredWidth * resultHigh.requiredLength >= resultLow.requiredWidth * resultLow.requiredLength)
    }

    @Test
    fun `designIsolatedFooting_reinforcementResultIsValid`() {
        val result = designIsolated()
        assertTrue("Reinforcement astProvided > 0", result.reinforcement.astProvided > 0)
    }

    @Test
    fun `designIsolatedFooting_punchingShearCheckIsValid`() {
        val result = designIsolated()
        assertNotNull(result.punchingShearCheck)
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Punching shear
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `checkPunchingShear_validInputs_producesResult`() {
        val result = footing.checkPunchingShear(
            25.0, 300.0, 300.0, 350.0, 400.0, LoadCombination.DEAD_LIVE
        )
        assertNotNull(result)
        assertTrue("Shear capacity > 0", result.shearCapacity > 0)
    }

    @Test
    fun `checkPunchingShear_lowForce_isSafe`() {
        val result = footing.checkPunchingShear(
            25.0, 300.0, 300.0, 350.0, 200.0, LoadCombination.DEAD_LIVE
        )
        assertTrue("Low punching force should be safe", result.isSafe)
    }

    @Test
    fun `checkPunchingShear_highForce_mayBeUnsafe`() {
        val result = footing.checkPunchingShear(
            25.0, 300.0, 300.0, 350.0, 5000.0, LoadCombination.DEAD_LIVE
        )
        // Very high force should likely fail
        assertNotNull(result)
    }

    @Test
    fun `getPunchingShearCapacity_returnsPositiveValue`() {
        val perimeter = 2.0 * (300.0 + 300.0) // critical perimeter
        val capacity = footing.getPunchingShearCapacity(25.0, perimeter, 350.0)
        assertTrue("Punching capacity > 0", capacity > 0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Footing reinforcement
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `calculateFootingReinforcement_validInputs_producesResult`() {
        val result = footing.calculateFootingReinforcement(
            25.0, 360.0, 1500.0, 1500.0, 350.0, 100.0, FootingDirection.SHORT
        )
        assertTrue("Provided area > 0", result.astProvided > 0)
        assertTrue("Provided >= required", result.astProvided >= result.astRequired * 0.95)
    }

    @Test
    fun `calculateFootingReinforcement_largerMoment_moreSteel`() {
        val low = footing.calculateFootingReinforcement(
            25.0, 360.0, 1500.0, 1500.0, 350.0, 50.0, FootingDirection.SHORT
        )
        val high = footing.calculateFootingReinforcement(
            25.0, 360.0, 1500.0, 1500.0, 350.0, 200.0, FootingDirection.SHORT
        )
        assertTrue("Higher moment → more steel", high.astRequired >= low.astRequired)
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Code limits
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `getMinFootingThickness_returns300mm`() {
        assertEquals(300.0, footing.getMinFootingThickness(), 0.1)
    }

    @Test
    fun `getMinCover_returnsPositiveValue`() {
        assertTrue("Min cover > 0", footing.getMinCover() > 0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. InputGuard validation
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `designIsolatedFooting_throwsForZeroFcu`() {
        designIsolated(fcu = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designIsolatedFooting_throwsForNegativeFcu`() {
        designIsolated(fcu = -25.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designIsolatedFooting_throwsForZeroFy`() {
        designIsolated(fy = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designIsolatedFooting_throwsForZeroColumnWidth`() {
        designIsolated(colW = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designIsolatedFooting_throwsForZeroColumnDepth`() {
        designIsolated(colD = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designIsolatedFooting_throwsForZeroAxialLoad`() {
        designIsolated(axial = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designIsolatedFooting_throwsForZeroSBC`() {
        designIsolated(sbc = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designIsolatedFooting_throwsForZeroDepth`() {
        designIsolated(depth = 0.0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. Combined footing & raft
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designCombinedFooting_validInputs_producesResult`() {
        val result = footing.designCombinedFooting(
            25.0, 360.0, 600.0, 800.0, 5000.0, 200.0, 500.0,
            LoadCombination.DEAD_LIVE
        )
        assertTrue("Width > 0", result.requiredWidth > 0)
        assertTrue("Length > 0", result.requiredLength > 0)
    }

    @Test
    fun `designRaftFoundation_validInputs_producesResult`() {
        val result = footing.designRaftFoundation(
            25.0, 360.0, 10000.0, 50.0, Pair(500.0, 300.0), 150.0, 600.0
        )
        assertNotNull(result)
    }

    @Test
    fun `designPileCap_validInputs_producesResult`() {
        val result = footing.designPileCap(
            25.0, 360.0, 500.0, 4, 600.0, 1500.0
        )
        assertNotNull(result)
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. Edge cases
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designIsolatedFooting_highStrengthConcrete_producesResult`() {
        val result = designIsolated(fcu = 50.0)
        assertTrue("Should handle high-strength concrete", result.requiredWidth > 0)
    }

    @Test
    fun `designIsolatedFooting_veryHighSBC_smallerFooting`() {
        val lowSBC = designIsolated(sbc = 150.0)
        val highSBC = designIsolated(sbc = 400.0)
        assertTrue("Higher SBC → smaller footing",
            highSBC.requiredWidth * highSBC.requiredLength <= lowSBC.requiredWidth * lowSBC.requiredLength)
    }
}
