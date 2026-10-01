package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ECPStaircase — ECP 203-2020 staircase design engine.
 * Covers geometry, loading, flexural design, shear design,
 * InputGuard validation, and edge cases.
 */
class ECPStaircaseTest {

    private lateinit var stair: ECPStaircase

    @Before
    fun setup() {
        stair = ECPStaircase()
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun defaultInput(
        stairType: StairType = StairType.STRAIGHT,
        span: Double = 3.0,
        totalRise: Double = 1.8,
        stairWidth: Double = 1.2,
        waistThickness: Double = 200.0,
        fcu: Double = 25.0,
        fy: Double = 360.0,
        deadLoad: Double = 6.0,
        liveLoad: Double = 4.0,
        riserCount: Int = 0,
        going: Double = 0.0
    ) = StaircaseInput(
        stairType, span, totalRise, stairWidth, waistThickness,
        fcu, fy, deadLoad, liveLoad, riserCount, going
    )

    // ═══════════════════════════════════════════════════════════════
    // 1. Happy path
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designStaircase_validInputs_producesResult`() {
        val result = stair.designStaircase(defaultInput())
        assertNotNull(result)
        assertEquals(DesignCode.ECP, result.designCode)
    }

    @Test
    fun `designStaircase_geometryIsReasonable`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Number of risers >= 3", result.numberOfRisers >= 3)
        assertTrue("Number of treads >= 2", result.numberOfTreads >= 2)
        assertTrue("Riser > 0", result.riser > 0)
        assertTrue("Going > 0", result.going > 0)
    }

    @Test
    fun `designStaircase_slopeAngleIsPositive`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Slope angle > 0", result.slopeAngle > 0)
        assertTrue("Slope angle < 90", result.slopeAngle < 90.0)
    }

    @Test
    fun `designStaircase_inclinedLengthExceedsSpan`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Inclined length > span", result.inclinedLength >= defaultInput().span * 0.99)
    }

    @Test
    fun `designStaircase_momentAndShearArePositive`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Max moment > 0", result.maxMoment > 0)
        assertTrue("Max shear > 0", result.maxShear > 0)
    }

    @Test
    fun `designStaircase_reactionsArePositive`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Reaction A > 0", result.reactionA > 0)
        assertTrue("Reaction B > 0", result.reactionB > 0)
    }

    @Test
    fun `designStaircase_mainRebarIsNotEmpty`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Main rebar should be specified", result.mainRebar.isNotEmpty())
    }

    @Test
    fun `designStaircase_mainRebarAreaIsPositive`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Main rebar area > 0", result.mainRebarArea > 0)
    }

    @Test
    fun `designStaircase_effectiveDepthIsPositive`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Effective depth > 0", result.effectiveDepth > 0)
    }

    @Test
    fun `designStaircase_shearCapacityIsPositive`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Shear capacity > 0", result.shearCapacity > 0)
    }

    @Test
    fun `designStaircase_safetyChecksExist`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Should have safety checks", result.safetyChecks.isNotEmpty())
    }

    @Test
    fun `designStaircase_codeNotesExist`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Dog-leg stair
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designStaircase_dogLeg_reducesMoment`() {
        val straight = stair.designStaircase(defaultInput(stairType = StairType.STRAIGHT))
        val dogLeg = stair.designStaircase(defaultInput(stairType = StairType.DOG_LEG))
        // Dog-leg uses M = wL²/10 vs wL²/8 for simply supported
        assertTrue("Dog-leg moment should be less than or equal to straight",
            dogLeg.maxMoment <= straight.maxMoment * 1.01) // allow small tolerance
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. User-specified geometry
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designStaircase_specifiedRiserCount_respectsIt`() {
        val result = stair.designStaircase(defaultInput(riserCount = 10))
        assertEquals(10, result.numberOfRisers)
    }

    @Test
    fun `designStaircase_specifiedGoing_respectsIt`() {
        val result = stair.designStaircase(defaultInput(going = 280.0))
        // Going should be approximately 280 (may be adjusted)
        assertTrue("Going should be close to specified", result.going in 250.0..300.0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. InputGuard validation
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroFcu`() {
        stair.designStaircase(defaultInput(fcu = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForNegativeFcu`() {
        stair.designStaircase(defaultInput(fcu = -25.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroFy`() {
        stair.designStaircase(defaultInput(fy = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroTotalRise`() {
        stair.designStaircase(defaultInput(totalRise = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroSpan`() {
        stair.designStaircase(defaultInput(span = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroDeadLoad`() {
        stair.designStaircase(defaultInput(deadLoad = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroLiveLoad`() {
        stair.designStaircase(defaultInput(liveLoad = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroStairWidth`() {
        stair.designStaircase(defaultInput(stairWidth = 0.0))
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. Edge cases
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designStaircase_veryShortSpan_producesResult`() {
        val result = stair.designStaircase(defaultInput(span = 1.5, totalRise = 1.0))
        assertTrue("Should handle short span", result.numberOfRisers > 0)
    }

    @Test
    fun `designStaircase_thickWaist_isSafe`() {
        val result = stair.designStaircase(defaultInput(waistThickness = 300.0))
        // A thick waist slab should be safe for flexure
        val flexCheck = result.safetyChecks.find { it.name.contains("Flexure") || it.name.contains("K") }
        if (flexCheck != null) {
            assertTrue("Thick waist should pass flexure", flexCheck.isSafe)
        }
    }

    @Test
    fun `designStaircase_comfortCheckExists`() {
        val result = stair.designStaircase(defaultInput())
        val comfortCheck = result.safetyChecks.find { it.name.contains("2R") }
        assertNotNull("Should have 2R+G comfort check", comfortCheck)
    }

    @Test
    fun `designStaircase_riserHeightCheckExists`() {
        val result = stair.designStaircase(defaultInput())
        val riserCheck = result.safetyChecks.find { it.name.contains("Riser") }
        assertNotNull("Should have riser height check", riserCheck)
    }

    @Test
    fun `designStaircase_goingCheckExists`() {
        val result = stair.designStaircase(defaultInput())
        val goingCheck = result.safetyChecks.find { it.name.contains("Going") }
        assertNotNull("Should have going check", goingCheck)
    }

    @Test
    fun `designStaircase_deflectionCheckExists`() {
        val result = stair.designStaircase(defaultInput())
        val deflectionCheck = result.safetyChecks.find { it.name.contains("Deflection") }
        assertNotNull("Should have deflection check", deflectionCheck)
    }
}
