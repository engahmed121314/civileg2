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

    // ══════════════════════════════════════════════════════════════════════
    // GOLDEN VECTORS — moment magnification §6.2.5 + §6.6.4.4/§6.6.4.5
    // (F-09 fix; CLAUSE_LOG A-17, printed pp.103-104, 122-125)
    // 400×400, fcu=30 → fc'=24, Ec=23025.2, Ig=2.13333e9 mm⁴
    // Pc(kLu=6000) = 5386.6584 kN; Pc(3000) = 21546.6335 kN
    // Case 1 single curvature M1=-50,M2=100: Cm=0.8
    //   δ = 0.8/(1−2000/(0.75×5386.6584)) = 1.584316
    // Case 2 double curvature M1=+50: Cm=0.4 → 0.7922 < 1 → δ=1.0
    // Case 3 braced short kLu=3000: 25 ≤ 34+12(−0.5)=28 → neglect → 1.0
    // Case 4 unbraced kLu=3000: 25 > 22; Cm=1.0 (M1=−100)
    //   δ = 1/(1−2000/(0.75×21546.6335)) = 1.141243
    // Case 5 creep βdns=0.6: EI=0.25EcIg, Pc=3366.6615
    //   δ = 0.8/(1−2000/(0.75×3366.6615)) = 3.847642
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun goldenMagnification_singleCurvature() {
        val (d, m2) = column.calculateMomentMagnification(
            Pu = 2000.0, M1 = -50.0, M2 = 100.0, kLu = 6000.0,
            width = 400.0, depth = 400.0, fcu = 30.0
        )
        assertEquals(1.584316, d, 0.001)
        assertEquals(100.0, m2, 0.001)  // M2=100 > M2,min=54, unchanged
    }

    @Test
    fun goldenMagnification_doubleCurvatureFloorsAtOne() {
        val (d, _) = column.calculateMomentMagnification(
            Pu = 2000.0, M1 = 50.0, M2 = 100.0, kLu = 6000.0,
            width = 400.0, depth = 400.0, fcu = 30.0
        )
        assertEquals(1.0, d, 0.001)
    }

    @Test
    fun goldenMagnification_bracedShortColumnNeglected() {
        val (d, _) = column.calculateMomentMagnification(
            Pu = 2000.0, M1 = -50.0, M2 = 100.0, kLu = 3000.0,
            width = 400.0, depth = 400.0, fcu = 30.0
        )
        assertEquals(1.0, d, 0.001)
    }

    // Sway vectors — §6.6.4.6 (printed pp.125-126): story of 4 identical
    // columns (Pc each = 21546.6335 at kLu=3000) → ΣPc = 86186.5342
    // δs = 1/(1 − ΣPu/(0.75ΣPc)): ΣPu=8000 → 1.141243; ΣPu=20000 → 1.448030
    // M = Mns + δs×Ms with Mns=40, Ms=60: 108.474591 / 126.881780 kNm

    @Test
    fun goldenSwayMagnification_sumPcMethod() {
        val (ds, m) = column.calculateSwayMagnification(
            Mns = 40.0, Ms = 60.0, sumPu = 8000.0, sumPc = 86186.5342
        )
        assertEquals(1.141243, ds, 0.001)
        assertEquals(108.474591, m, 0.001)
    }

    @Test
    fun goldenSwayMagnification_heavyStory() {
        val (ds, m) = column.calculateSwayMagnification(
            Mns = 40.0, Ms = 60.0, sumPu = 20000.0, sumPc = 86186.5342
        )
        assertEquals(1.448030, ds, 0.001)
        assertEquals(126.881780, m, 0.001)
    }

    @Test
    fun goldenSwayMagnification_qMethod() {
        val (ds, m) = column.calculateSwayMagnification(
            Mns = 40.0, Ms = 60.0, sumPu = 8000.0, sumPc = 86186.5342,
            stabilityIndexQ = 0.1237683
        )
        assertEquals(1.141243, ds, 0.001)
        assertEquals(108.474591, m, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun swayMagnification_qMethodBeyond15_throws() {
        column.calculateSwayMagnification(
            Mns = 40.0, Ms = 60.0, sumPu = 8000.0, sumPc = 86186.5342,
            stabilityIndexQ = 0.5
        )
    }

    @Test
    fun goldenMagnification_creepBetaDns() {
        val (d, _) = column.calculateMomentMagnification(
            Pu = 2000.0, M1 = -50.0, M2 = 100.0, kLu = 6000.0,
            width = 400.0, depth = 400.0, fcu = 30.0, betaDns = 0.6
        )
        assertEquals(3.847642, d, 0.001)
    }

    // 6.6.4.5.4 minimum-moment vector: Pu=2000, h=400 → M2,min = 54 kNm > M2=30
    // → M2 replaced by 54, Cm=1.0, δ = 1/(1−2000/(0.75×5386.6584)) = 1.980395

    @Test
    fun goldenMagnification_minimumMomentGoverns() {
        val (d, m2) = column.calculateMomentMagnification(
            Pu = 2000.0, M1 = 0.0, M2 = 30.0, kLu = 6000.0,
            width = 400.0, depth = 400.0, fcu = 30.0
        )
        assertEquals(1.980395, d, 0.001)
        assertEquals(54.0, m2, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun momentMagnification_m2SmallerThanM1_throws() {
        column.calculateMomentMagnification(
            Pu = 2000.0, M1 = -150.0, M2 = 100.0, kLu = 6000.0,
            width = 400.0, depth = 400.0, fcu = 30.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun momentMagnification_zeroLength_throws() {
        column.calculateMomentMagnification(
            Pu = 2000.0, M1 = -50.0, M2 = 100.0, kLu = 0.0,
            width = 400.0, depth = 400.0, fcu = 30.0
        )
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
