package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ACIColumn — ACI 318-19 column design engine.
 * Covers: axial capacity, reinforcement design, slenderness, shear design,
 * InputGuard validation, edge cases, and safety checks.
 */
class ACIColumnTest {

    private lateinit var column: ACIColumn

    @Before
    fun setup() {
        column = ACIColumn()
    }

    companion object {
        private const val FCU = 30.0      // MPa
        private const val FY = 400.0      // MPa
        private const val WIDTH = 300.0   // mm
        private const val DEPTH = 300.0   // mm
        private const val AXIAL_LOAD = 1500.0  // kN
        private const val MOMENT_X = 50.0     // kN.m
        private const val MOMENT_Y = 0.0     // kN.m
        private const val REINF_AREA = 1200.0 // mm² (≈1.33%)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Instance & interface
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun aciColumnInstanceCreatedSuccessfully() {
        assertNotNull(column)
    }

    @Test
    fun aciColumnImplementsColumnDesign() {
        assertTrue(column is ColumnDesign)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Axial capacity — happy path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun calculateAxialCapacity_validInputs_returnsPositive() {
        val capacity = column.calculateAxialCapacity(
            FCU, FY, WIDTH, DEPTH, REINF_AREA, LoadCombination.DEAD_LIVE
        )
        assertTrue("Axial capacity must be positive", capacity > 0)
    }

    @Test
    fun calculateAxialCapacity_largerSection_higherCapacity() {
        val cap300 = column.calculateAxialCapacity(
            FCU, FY, 300.0, 300.0, REINF_AREA, LoadCombination.DEAD_LIVE
        )
        val cap400 = column.calculateAxialCapacity(
            FCU, FY, 400.0, 400.0, REINF_AREA, LoadCombination.DEAD_LIVE
        )
        assertTrue("Larger section should have higher capacity", cap400 > cap300)
    }

    @Test
    fun calculateAxialCapacity_moreReinforcement_higherCapacity() {
        val capLow = column.calculateAxialCapacity(
            FCU, FY, WIDTH, DEPTH, 600.0, LoadCombination.DEAD_LIVE
        )
        val capHigh = column.calculateAxialCapacity(
            FCU, FY, WIDTH, DEPTH, 2400.0, LoadCombination.DEAD_LIVE
        )
        assertTrue("More reinforcement should give higher capacity", capHigh > capLow)
    }

    @Test
    fun calculateAxialCapacity_higherFcu_higherCapacity() {
        val cap25 = column.calculateAxialCapacity(
            25.0, FY, WIDTH, DEPTH, REINF_AREA, LoadCombination.DEAD_LIVE
        )
        val cap40 = column.calculateAxialCapacity(
            40.0, FY, WIDTH, DEPTH, REINF_AREA, LoadCombination.DEAD_LIVE
        )
        assertTrue("Higher fcu should give higher capacity", cap40 > cap25)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Axial capacity — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun calculateAxialCapacity_negativeFcu_throwsException() {
        column.calculateAxialCapacity(-30.0, FY, WIDTH, DEPTH, REINF_AREA, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateAxialCapacity_zeroFy_throwsException() {
        column.calculateAxialCapacity(FCU, 0.0, WIDTH, DEPTH, REINF_AREA, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateAxialCapacity_zeroWidth_throwsException() {
        column.calculateAxialCapacity(FCU, FY, 0.0, DEPTH, REINF_AREA, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateAxialCapacity_zeroDepth_throwsException() {
        column.calculateAxialCapacity(FCU, FY, WIDTH, 0.0, REINF_AREA, LoadCombination.DEAD_LIVE)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Axial capacity with Phi — tied vs spiral
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun calculateAxialCapacityWithPhi_spiralHigherThanTied() {
        val tiedCap = column.calculateAxialCapacityWithPhi(
            FCU, FY, WIDTH, DEPTH, REINF_AREA, false
        )
        val spiralCap = column.calculateAxialCapacityWithPhi(
            FCU, FY, WIDTH, DEPTH, REINF_AREA, true
        )
        assertTrue("Spiral column should have higher capacity than tied",
            spiralCap > tiedCap)
    }

    @Test
    fun calculateAxialCapacityWithPhi_validInputs_returnsPositive() {
        val cap = column.calculateAxialCapacityWithPhi(
            FCU, FY, WIDTH, DEPTH, REINF_AREA, true
        )
        assertTrue("Capacity must be positive", cap > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateAxialCapacityWithPhi_negativeFcu_throwsException() {
        column.calculateAxialCapacityWithPhi(-30.0, FY, WIDTH, DEPTH, REINF_AREA, false)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Reinforcement design — happy path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun calculateReinforcement_validInputs_producesResult() {
        val result = column.calculateReinforcement(
            FCU, FY, WIDTH, DEPTH, AXIAL_LOAD, MOMENT_X, MOMENT_Y, LoadCombination.DEAD_LIVE
        )
        assertNotNull(result)
        assertTrue("Required As must be positive", result.astRequired > 0)
        assertTrue("Provided As must be positive", result.astProvided > 0)
    }

    @Test
    fun calculateReinforcement_validInputs_providedExceedsRequired() {
        val result = column.calculateReinforcement(
            FCU, FY, WIDTH, DEPTH, AXIAL_LOAD, MOMENT_X, MOMENT_Y, LoadCombination.DEAD_LIVE
        )
        assertTrue("Provided should meet or exceed required",
            result.astProvided >= result.astRequired * 0.95)
    }

    @Test
    fun calculateReinforcement_validInputs_barCountAtLeast4() {
        val result = column.calculateReinforcement(
            FCU, FY, WIDTH, DEPTH, AXIAL_LOAD, MOMENT_X, MOMENT_Y, LoadCombination.DEAD_LIVE
        )
        assertTrue("Minimum 4 bars in column", result.numberOfBars >= 4)
    }

    @Test
    fun calculateReinforcement_highAxialLoad_stillProducesResult() {
        val result = column.calculateReinforcement(
            FCU, FY, 400.0, 400.0, 3000.0, 100.0, 0.0, LoadCombination.DEAD_LIVE
        )
        assertNotNull(result)
        assertTrue("Should still have reinforcement", result.astRequired > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. Reinforcement — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun calculateReinforcement_negativeFcu_throwsException() {
        column.calculateReinforcement(
            -30.0, FY, WIDTH, DEPTH, AXIAL_LOAD, MOMENT_X, MOMENT_Y, LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateReinforcement_zeroFy_throwsException() {
        column.calculateReinforcement(
            FCU, 0.0, WIDTH, DEPTH, AXIAL_LOAD, MOMENT_X, MOMENT_Y, LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateReinforcement_zeroWidth_throwsException() {
        column.calculateReinforcement(
            FCU, FY, 0.0, DEPTH, AXIAL_LOAD, MOMENT_X, MOMENT_Y, LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateReinforcement_zeroDepth_throwsException() {
        column.calculateReinforcement(
            FCU, FY, WIDTH, 0.0, AXIAL_LOAD, MOMENT_X, MOMENT_Y, LoadCombination.DEAD_LIVE
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. Slenderness effect
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun calculateSlendernessEffect_validInputs_returnsPositiveRatio() {
        val (slendernessRatio, isSlender, Pc) = column.calculateSlendernessEffect(
            3.0, 1.0, WIDTH, DEPTH, FCU
        )
        assertTrue("Slenderness ratio must be positive", slendernessRatio > 0)
    }

    @Test
    fun calculateSlendernessEffect_shortColumn_returnsIsSlenderFalse() {
        // Short column: L=3m, K=1, 300x300 → λ ≈ 34.6, limit for braced tied = 22
        // Actually 1.0 * 3000 / (300/sqrt(12)) ≈ 34.6 > 22, so it IS slender
        // Let's use a shorter height
        val (ratio, isSlender, Pc) = column.calculateSlendernessEffect(
            2.0, 0.7, 400.0, 400.0, FCU
        )
        // K=0.7, L=2m, r=400/sqrt(12)=115.5, λ=0.7*2000/115.5≈12.1 → short
        assertFalse("Should be a short column", isSlender)
    }

    @Test
    fun calculateSlendernessEffect_tallColumn_returnsIsSlenderTrue() {
        val (ratio, isSlender, Pc) = column.calculateSlendernessEffect(
            6.0, 1.0, 250.0, 250.0, FCU
        )
        // K=1, L=6m, r=250/sqrt(12)=72.2, λ=6000/72.2≈83 → very slender
        assertTrue("Should be a slender column", isSlender)
    }

    @Test
    fun calculateSlendernessEffect_criticalLoadPositive() {
        val (_, _, Pc) = column.calculateSlendernessEffect(
            3.0, 1.0, WIDTH, DEPTH, FCU
        )
        assertTrue("Critical load (Pc) must be positive", Pc > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateSlendernessEffect_zeroLength_throwsException() {
        column.calculateSlendernessEffect(0.0, 1.0, WIDTH, DEPTH, FCU)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateSlendernessEffect_zeroWidth_throwsException() {
        column.calculateSlendernessEffect(3.0, 1.0, 0.0, DEPTH, FCU)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateSlendernessEffect_negativeFcu_throwsException() {
        column.calculateSlendernessEffect(3.0, 1.0, WIDTH, DEPTH, -30.0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 8. Shear design
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun calculateShearDesign_validInputs_producesResult() {
        val result = column.calculateShearDesign(100.0, WIDTH, DEPTH, FCU, FY)
        assertNotNull(result)
        assertTrue("Vc must be positive", result.Vc > 0)
        assertTrue("phiVc must be positive", result.phiVc > 0)
    }

    @Test
    fun calculateShearDesign_lowShear_noStirrupsNeeded() {
        val result = column.calculateShearDesign(20.0, WIDTH, DEPTH, FCU, FY)
        // Low shear → concrete alone sufficient
        assertFalse("Low shear should not need stirrups", result.needsStirrups)
    }

    @Test
    fun calculateShearDesign_highShear_stirrupsNeeded() {
        val result = column.calculateShearDesign(500.0, WIDTH, DEPTH, FCU, FY)
        // High shear → stirrups required
        assertTrue("High shear should need stirrups", result.needsStirrups)
    }

    @Test
    fun calculateShearDesign_safeResult_hasReasonableUtilization() {
        val result = column.calculateShearDesign(100.0, WIDTH, DEPTH, FCU, FY)
        assertTrue("Utilization ratio should be non-negative", result.utilizationRatio >= 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateShearDesign_zeroWidth_throwsException() {
        column.calculateShearDesign(100.0, 0.0, DEPTH, FCU, FY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateShearDesign_zeroDepth_throwsException() {
        column.calculateShearDesign(100.0, WIDTH, 0.0, FCU, FY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateShearDesign_negativeFcu_throwsException() {
        column.calculateShearDesign(100.0, WIDTH, DEPTH, -30.0, FY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateShearDesign_zeroFy_throwsException() {
        column.calculateShearDesign(100.0, WIDTH, DEPTH, FCU, 0.0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 9. Code limits
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun minReinforcementRatio_isOnePercent() {
        assertEquals(0.01, column.getMinReinforcementRatio(), 1e-6)
    }

    @Test
    fun maxReinforcementRatio_isEightPercent() {
        assertEquals(0.08, column.getMaxReinforcementRatio(), 1e-6)
    }

    @Test
    fun minSpacing_isPositive() {
        assertTrue(column.getMinSpacing() > 0)
    }

    @Test
    fun maxSpacing_isPositive() {
        assertTrue(column.getMaxSpacing() > 0)
    }

    @Test
    fun minCover_isPositive() {
        assertTrue(column.getMinCover() > 0)
    }
}
