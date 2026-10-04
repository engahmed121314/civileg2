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
            FCU, FY, 2000.0, 2000.0, 440.0, 200.0, FootingDirection.SHORT
        )
        assertTrue("Higher moment should require at least as much reinforcement",
            resultHigh.astRequired >= resultLow.astRequired)
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
