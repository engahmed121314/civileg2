package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.BoundaryConstraints
import com.civileg.app.domain.calculations.base.FootingDesign
import com.civileg.app.domain.calculations.base.FootingDirection
import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ACIFooting — ACI 318-19 footing design engine.
 * Covers: isolated footing, punching shear, reinforcement, combined footing,
 * InputGuard validation, edge cases, and safety checks.
 */
class ACIFootingTest {

    private lateinit var footing: ACIFooting

    @Before
    fun setup() {
        footing = ACIFooting()
    }

    companion object {
        private const val FCU = 25.0        // MPa
        private const val FY = 400.0        // MPa
        private const val COL_W = 300.0     // mm
        private const val COL_D = 300.0     // mm
        private const val AXIAL = 800.0     // kN
        private const val SBC = 200.0       // kPa
        private const val FOOTING_D = 500.0 // mm
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Instance & interface
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun aciFootingInstanceCreatedSuccessfully() {
        assertNotNull(footing)
    }

    @Test
    fun aciFootingImplementsFootingDesign() {
        assertTrue(footing is FootingDesign)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Isolated Footing — happy path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designIsolatedFooting_validInputs_producesResult() {
        val result = footing.designIsolatedFooting(
            FCU, FY, COL_W, COL_D, AXIAL, 0.0, 0.0, SBC, FOOTING_D,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
        assertNotNull(result)
        assertTrue("Width must be positive", result.requiredWidth > 0)
        assertTrue("Length must be positive", result.requiredLength > 0)
        assertTrue("Thickness must be positive", result.requiredThickness > 0)
    }

    @Test
    fun designIsolatedFooting_validInputs_soilPressurePositive() {
        val result = footing.designIsolatedFooting(
            FCU, FY, COL_W, COL_D, AXIAL, 0.0, 0.0, SBC, FOOTING_D,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
        assertTrue("Soil pressure must be positive", result.soilPressure > 0)
    }

    @Test
    fun designIsolatedFooting_validInputs_footingLargerThanColumn() {
        val result = footing.designIsolatedFooting(
            FCU, FY, COL_W, COL_D, AXIAL, 0.0, 0.0, SBC, FOOTING_D,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
        assertTrue("Footing width must exceed column width",
            result.requiredWidth > COL_W)
        assertTrue("Footing length must exceed column depth",
            result.requiredLength > COL_D)
    }

    @Test
    fun designIsolatedFooting_higherLoad_largerFooting() {
        val resultLow = footing.designIsolatedFooting(
            FCU, FY, COL_W, COL_D, 500.0, 0.0, 0.0, SBC, FOOTING_D,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
        val resultHigh = footing.designIsolatedFooting(
            FCU, FY, COL_W, COL_D, 2000.0, 0.0, 0.0, SBC, FOOTING_D,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
        val areaLow = resultLow.requiredWidth * resultLow.requiredLength
        val areaHigh = resultHigh.requiredWidth * resultHigh.requiredLength
        assertTrue("Higher load should require larger footing area", areaHigh > areaLow)
    }

    @Test
    fun designIsolatedFooting_reinforcementProvided() {
        val result = footing.designIsolatedFooting(
            FCU, FY, COL_W, COL_D, AXIAL, 0.0, 0.0, SBC, FOOTING_D,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
        assertTrue("Required reinforcement must be positive",
            result.reinforcement.astRequired > 0)
        assertTrue("Provided reinforcement must be positive",
            result.reinforcement.astProvided > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Isolated Footing — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun designIsolatedFooting_negativeFcu_throwsException() {
        footing.designIsolatedFooting(
            -25.0, FY, COL_W, COL_D, AXIAL, 0.0, 0.0, SBC, FOOTING_D,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun designIsolatedFooting_zeroFy_throwsException() {
        footing.designIsolatedFooting(
            FCU, 0.0, COL_W, COL_D, AXIAL, 0.0, 0.0, SBC, FOOTING_D,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun designIsolatedFooting_zeroColumnWidth_throwsException() {
        footing.designIsolatedFooting(
            FCU, FY, 0.0, COL_D, AXIAL, 0.0, 0.0, SBC, FOOTING_D,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun designIsolatedFooting_zeroSBC_throwsException() {
        footing.designIsolatedFooting(
            FCU, FY, COL_W, COL_D, AXIAL, 0.0, 0.0, 0.0, FOOTING_D,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun designIsolatedFooting_zeroFootingDepth_throwsException() {
        footing.designIsolatedFooting(
            FCU, FY, COL_W, COL_D, AXIAL, 0.0, 0.0, SBC, 0.0,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Punching Shear
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun checkPunchingShear_validInputs_producesResult() {
        val result = footing.checkPunchingShear(
            FCU, COL_W, COL_D, 440.0, 500.0, LoadCombination.DEAD_LIVE
        )
        assertNotNull(result)
        assertTrue("Shear capacity must be positive", result.shearCapacity > 0)
    }

    @Test
    fun checkPunchingShear_lowForce_isSafe() {
        val result = footing.checkPunchingShear(
            FCU, COL_W, COL_D, 440.0, 100.0, LoadCombination.DEAD_LIVE
        )
        assertTrue("Low punching force should be safe", result.isSafe)
    }

    @Test
    fun checkPunchingShear_highForce_mayBeUnsafe() {
        val result = footing.checkPunchingShear(
            FCU, COL_W, COL_D, 440.0, 5000.0, LoadCombination.DEAD_LIVE
        )
        // Very high force may exceed punching capacity
        assertFalse("Very high punching force should be unsafe", result.isSafe)
    }

    @Test
    fun checkPunchingShear_largerDepth_higherCapacity() {
        val resultShallow = footing.checkPunchingShear(
            FCU, COL_W, COL_D, 300.0, 500.0, LoadCombination.DEAD_LIVE
        )
        val resultDeep = footing.checkPunchingShear(
            FCU, COL_W, COL_D, 500.0, 500.0, LoadCombination.DEAD_LIVE
        )
        assertTrue("Deeper effective depth should have higher punching capacity",
            resultDeep.shearCapacity > resultShallow.shearCapacity)
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkPunchingShear_zeroFcu_throwsException() {
        footing.checkPunchingShear(0.0, COL_W, COL_D, 440.0, 500.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkPunchingShear_zeroColumnWidth_throwsException() {
        footing.checkPunchingShear(FCU, 0.0, COL_D, 440.0, 500.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkPunchingShear_zeroEffectiveDepth_throwsException() {
        footing.checkPunchingShear(FCU, COL_W, COL_D, 0.0, 500.0, LoadCombination.DEAD_LIVE)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Footing Reinforcement
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun calculateFootingReinforcement_validInputs_producesResult() {
        val result = footing.calculateFootingReinforcement(
            FCU, FY, 2000.0, 2000.0, 440.0, 100.0, FootingDirection.SHORT
        )
        assertNotNull(result)
        assertTrue("Required As must be positive", result.astRequired > 0)
        assertTrue("Provided As must be positive", result.astProvided > 0)
    }

    @Test
    fun calculateFootingReinforcement_largerMoment_moreReinforcement() {
        val resultLow = footing.calculateFootingReinforcement(
            FCU, FY, 2000.0, 2000.0, 440.0, 50.0, FootingDirection.SHORT
        )
        val resultHigh = footing.calculateFootingReinforcement(
            FCU, FY, 2000.0, 2000.0, 440.0, 1200.0, FootingDirection.SHORT
        )
        assertTrue("Higher moment should require more reinforcement",
            resultHigh.astRequired > resultLow.astRequired)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateFootingReinforcement_zeroFcu_throwsException() {
        footing.calculateFootingReinforcement(
            0.0, FY, 2000.0, 2000.0, 440.0, 100.0, FootingDirection.SHORT
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateFootingReinforcement_zeroEffectiveDepth_throwsException() {
        footing.calculateFootingReinforcement(
            FCU, FY, 2000.0, 2000.0, 0.0, 100.0, FootingDirection.SHORT
        )
    }

    /**
     * Golden vector (square, no moment): hand-computed per CLAUSE_LOG A-02/A-03/
     * A-05/F-01. P = 1500 kN factored, 400×400 column, qa = 200 kPa, h = 600 mm
     * (d = 515), DEAD_LIVE (factor 1.4): P_serv = 1071.43, netSBC = 185,
     * A = 5.79 m² → B = L = 2450 mm, q = 178.50 kPa, qf = 249.90,
     * Mu = 321.62 kN·m (131.27 kN·m/m) → strength 719.9 < min →
     * As = 0.0020×1000×600 = 1200 mm²/m; Vu = 469.9 ≤ φVc = 719.4 kN;
     * punching φVn = 2086.3 kN.
     */
    @Test
    fun designIsolatedFooting_goldenVector_squareNoMoment() {
        val result = footing.designIsolatedFooting(
            25.0, 400.0, 400.0, 400.0, 1500.0, 0.0, 0.0, 200.0, 600.0,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
        assertEquals(2450.0, result.requiredWidth, 1.0)
        assertEquals(2450.0, result.requiredLength, 1.0)
        assertEquals(178.50, result.soilPressure, 0.01)
        assertEquals(1200.0, result.reinforcement.astRequired, 0.5)
        assertEquals(2086.3, result.punchingShearCheck.shearCapacity, 1.0)
        assertTrue(result.punchingShearCheck.isSafe)
        assertTrue(result.isSafe)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. Combined Footing
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designCombinedFooting_validInputs_producesResult() {
        val result = footing.designCombinedFooting(
            FCU, FY, 600.0, 400.0, 4000.0, SBC, FOOTING_D,
            LoadCombination.DEAD_LIVE, COL_W, COL_D
        )
        assertNotNull(result)
        assertTrue("Width must be positive", result.requiredWidth > 0)
        assertTrue("Length must be positive", result.requiredLength > 0)
    }

    /**
     * Golden vector A (symmetric): hand-computed per CLAUSE_LOG A-05/CF batch.
     * P1 = P2 = 1400 kN factored, S = 3 m, qa = 200 kPa, h = 700 mm (d = 615),
     * 400×400 columns, DEAD_LIVE (factor 1.4) → P_work = 1000 kN each,
     * xR = 1500 mm, L = 3700 mm, B = 2750 mm, q = 196.56 kPa,
     * w = 756.757 kN/m, M+ = 805.00 kN·m at ξ* = 1.85 m, M− = −8.51 kN·m.
     */
    @Test
    fun designCombinedFooting_goldenVector_symmetric() {
        val result = footing.designCombinedFooting(
            30.0, 400.0, 1400.0, 1400.0, 3000.0, 200.0, 700.0,
            LoadCombination.DEAD_LIVE, 400.0, 400.0, 400.0, 400.0
        )
        assertEquals(3700.0, result.requiredLength, 1.0)
        assertEquals(2750.0, result.requiredWidth, 1.0)
        assertEquals(196.56, result.soilPressure, 0.01)
        assertEquals(result.soilPressure, result.maxSoilPressure, 1e-9)
        // Bottom steel: strength needs 1351 mm²/m but Table 7.6.1.1 min governs:
        // As,min = 0.0020 × 1000 × 700 = 1400 mm²/m
        assertEquals(1400.0, result.reinforcement.astRequired, 0.5)
        // Hogging exists (overhang cantilever) → top steel at the minimum
        assertNotNull(result.topReinforcement)
        assertEquals(1400.0, result.topReinforcement!!.astRequired, 0.5)
        // Two transverse strips, strength-governed: ρ = 0.002610 → 1604.9 mm²/m
        assertEquals(2, result.transverseReinforcement.size)
        assertEquals(1604.9, result.transverseReinforcement[0].astRequired, 1.0)
        assertEquals(1604.9, result.transverseReinforcement[1].astRequired, 1.0)
        // One-way shear: Vu = 518.4 kN ≤ φVc = 1056.4 kN; punching φVn = 3027.5 kN
        assertTrue(result.punchingShearCheck.isSafe)
        assertEquals(3027.5, result.punchingShearCheck.shearCapacity, 1.0)
        assertTrue(result.isSafe)
    }

    /**
     * Golden vector B (unequal loads, P1 ≫ P2): exercises CF-05 — the footing
     * must extend past the lightly loaded column. Hand-computed: xR = 1117.2 mm,
     * L = 2·max(xR + s1, S − xR + s2) = 5665.5 mm, B = 2450 mm,
     * w = 511.87 kN/m, M+ = 476.22 kN·m at ξ* = 3.907 m, M− = −549.68 kN·m at
     * column 1 left face, Vu_max = 627.9 kN ≤ φVc = 998.8 kN.
     */
    @Test
    fun designCombinedFooting_goldenVector_unequalLoads() {
        val result = footing.designCombinedFooting(
            25.0, 400.0, 2000.0, 900.0, 3600.0, 150.0, 800.0,
            LoadCombination.DEAD_LIVE, 500.0, 500.0, 400.0, 400.0
        )
        assertEquals(5665.5, result.requiredLength, 0.5)
        assertEquals(2450.0, result.requiredWidth, 1.0)
        assertEquals(149.23, result.soilPressure, 0.01)
        // Both bottom and top land on Table 7.6.1.1 min: 0.0020 × 1000 × 800
        assertEquals(1600.0, result.reinforcement.astRequired, 0.5)
        assertNotNull(result.topReinforcement)
        assertEquals(1600.0, result.topReinforcement!!.astRequired, 0.5)
        assertEquals(2, result.transverseReinforcement.size)
        assertTrue(result.isSafe)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designCombinedFooting_zeroDistance_throwsException() {
        footing.designCombinedFooting(
            FCU, FY, 600.0, 400.0, 0.0, SBC, FOOTING_D,
            LoadCombination.DEAD_LIVE, COL_W, COL_D
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun designCombinedFooting_zeroSBC_throwsException() {
        footing.designCombinedFooting(
            FCU, FY, 600.0, 400.0, 4000.0, 0.0, FOOTING_D,
            LoadCombination.DEAD_LIVE, COL_W, COL_D
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. Raft Foundation
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designRaftFoundation_validInputs_producesResult() {
        val result = footing.designRaftFoundation(
            FCU, FY, 5000.0, 50.0, Pair(100.0, 100.0), SBC, 600.0
        )
        assertNotNull(result)
        assertTrue("Soil pressure must be positive", result.soilPressure > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designRaftFoundation_zeroTotalArea_throwsException() {
        footing.designRaftFoundation(
            FCU, FY, 5000.0, 0.0, Pair(100.0, 100.0), SBC, 600.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun designRaftFoundation_zeroSBC_throwsException() {
        footing.designRaftFoundation(
            FCU, FY, 5000.0, 50.0, Pair(100.0, 100.0), 0.0, 600.0
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 8. Pile Cap
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designPileCap_validInputs_producesResult() {
        val result = footing.designPileCap(
            FCU, FY, 400.0, 4, 400.0, 1200.0
        )
        assertNotNull(result)
        assertTrue("Width must be positive", result.requiredWidth > 0)
        assertTrue("Length must be positive", result.requiredLength > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designPileCap_zeroPiles_throwsException() {
        footing.designPileCap(FCU, FY, 400.0, 0, 400.0, 1200.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designPileCap_zeroDiameter_throwsException() {
        footing.designPileCap(FCU, FY, 400.0, 4, 0.0, 1200.0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 9. Code limits
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun minFootingThickness_is300mm() {
        assertEquals(300.0, footing.getMinFootingThickness(), 1e-6)
    }

    @Test
    fun minCover_is75mm() {
        assertEquals(75.0, footing.getMinCover(), 1e-6)
    }

    @Test
    fun punchingShearCapacity_validInputs_returnsPositive() {
        val cap = footing.getPunchingShearCapacity(FCU, 3000.0, 440.0)
        assertTrue("Punching shear capacity must be positive", cap > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun punchingShearCapacity_zeroPerimeter_throwsException() {
        footing.getPunchingShearCapacity(FCU, 0.0, 440.0)
    }
}
