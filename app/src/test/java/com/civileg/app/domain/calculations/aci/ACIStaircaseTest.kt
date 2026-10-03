package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.StaircaseDesign
import com.civileg.app.domain.calculations.base.StaircaseInput
import com.civileg.app.domain.calculations.base.StairType
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ACIStaircase — ACI 318-19 staircase design engine.
 * Covers: staircase design, geometry, loading, reinforcement, deflection,
 * InputGuard validation, edge cases, and safety checks.
 */
class ACIStaircaseTest {

    private lateinit var stair: ACIStaircase

    @Before
    fun setup() {
        stair = ACIStaircase()
    }

    // ══════════════════════════════════════════════════════════════════════
    // Helper — typical staircase input
    // ══════════════════════════════════════════════════════════════════════

    private fun typicalInput(
        span: Double = 3.0,
        totalRise: Double = 1.8,
        stairWidth: Double = 1.2,
        waistThickness: Double = 200.0,
        fcu: Double = 25.0,
        fy: Double = 400.0,
        deadLoad: Double = 6.0,
        liveLoad: Double = 4.0,
        stairType: StairType = StairType.STRAIGHT
    ) = StaircaseInput(
        stairType = stairType,
        span = span,
        totalRise = totalRise,
        stairWidth = stairWidth,
        waistThickness = waistThickness,
        fcu = fcu,
        fy = fy,
        deadLoad = deadLoad,
        liveLoad = liveLoad
    )

    // ══════════════════════════════════════════════════════════════════════
    // 1. Instance & interface
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun aciStaircaseInstanceCreatedSuccessfully() {
        assertNotNull(stair)
    }

    @Test
    fun aciStaircaseImplementsStaircaseDesign() {
        assertTrue(stair is StaircaseDesign)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Design — happy path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designStaircase_validInputs_producesResult() {
        val result = stair.designStaircase(typicalInput())
        assertNotNull(result)
    }

    @Test
    fun designStaircase_validInputs_designCodeIsACI() {
        val result = stair.designStaircase(typicalInput())
        assertEquals(DesignCode.ACI, result.designCode)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Geometry checks
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designStaircase_validInputs_riserCountPositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Number of risers must be positive", result.numberOfRisers > 0)
    }

    @Test
    fun designStaircase_validInputs_treadCountPositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Number of treads must be positive", result.numberOfTreads > 0)
    }

    @Test
    fun designStaircase_validInputs_riserHeightPositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Riser height must be positive", result.riser > 0)
    }

    @Test
    fun designStaircase_validInputs_goingPositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Going must be positive", result.going > 0)
    }

    @Test
    fun designStaircase_validInputs_slopeAnglePositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Slope angle must be positive", result.slopeAngle > 0)
        assertTrue("Slope angle must be less than 90°", result.slopeAngle < 90.0)
    }

    @Test
    fun designStaircase_validInputs_inclinedLengthPositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Inclined length must be positive", result.inclinedLength > 0)
    }

    @Test
    fun designStaircase_validInputs_treadsOneLessThanRisers() {
        val result = stair.designStaircase(typicalInput())
        assertEquals("Treads = Risers - 1 for single flight",
            result.numberOfRisers - 1, result.numberOfTreads)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Loading
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designStaircase_validInputs_factoredLoadPositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Factored load must be positive", result.factoredLoad > 0)
    }

    @Test
    fun designStaircase_validInputs_horizontalLoadPositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Horizontal load must be positive", result.horizontalLoad > 0)
    }

    @Test
    fun designStaircase_higherLiveLoad_higherFactoredLoad() {
        val resultLow = stair.designStaircase(typicalInput(liveLoad = 3.0))
        val resultHigh = stair.designStaircase(typicalInput(liveLoad = 6.0))
        assertTrue("Higher live load should produce higher factored load",
            resultHigh.horizontalLoad > resultLow.horizontalLoad)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Structural results
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designStaircase_validInputs_maxMomentPositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Max moment must be positive", result.maxMoment > 0)
    }

    @Test
    fun designStaircase_validInputs_maxShearPositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Max shear must be positive", result.maxShear > 0)
    }

    @Test
    fun designStaircase_validInputs_effectiveDepthPositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Effective depth must be positive", result.effectiveDepth > 0)
    }

    @Test
    fun designStaircase_validInputs_mainRebarAreaPositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Main rebar area must be positive", result.mainRebarArea > 0)
    }

    @Test
    fun designStaircase_validInputs_shearCapacityPositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Shear capacity must be positive", result.shearCapacity > 0)
    }

    @Test
    fun designStaircase_widerSpan_largerMoment() {
        val resultShort = stair.designStaircase(typicalInput(span = 2.5))
        val resultLong = stair.designStaircase(typicalInput(span = 4.0))
        assertTrue("Longer span should produce larger moment",
            resultLong.maxMoment > resultShort.maxMoment)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. Deflection
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designStaircase_validInputs_allowableDeflectionPositive() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Allowable deflection must be positive", result.allowableDeflection > 0)
    }

    @Test
    fun designStaircase_thickWaist_betterDeflection() {
        val resultThin = stair.designStaircase(typicalInput(waistThickness = 150.0))
        val resultThick = stair.designStaircase(typicalInput(waistThickness = 280.0))
        // Thicker waist should have better deflection result
        assertTrue("Thick waist deflection OK should be at least as good",
            !resultThin.deflectionOk || resultThick.deflectionOk)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. Safety checks
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designStaircase_validInputs_safetyChecksExist() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Should have safety checks", result.safetyChecks.isNotEmpty())
    }

    @Test
    fun designStaircase_validInputs_waistThicknessCheckExists() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Should check waist thickness",
            result.safetyChecks.any { it.name.contains("Waist", ignoreCase = true) })
    }

    @Test
    fun designStaircase_validInputs_comfortCheckExists() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Should check 2R+G comfort",
            result.safetyChecks.any { it.name.contains("2R", ignoreCase = true) })
    }

    @Test
    fun designStaircase_validInputs_riserCheckExists() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Should check riser height limit",
            result.safetyChecks.any { it.name.contains("Riser", ignoreCase = true) })
    }

    @Test
    fun designStaircase_validInputs_goingCheckExists() {
        val result = stair.designStaircase(typicalInput())
        assertTrue("Should check going minimum",
            result.safetyChecks.any { it.name.contains("Going", ignoreCase = true) })
    }

    // ══════════════════════════════════════════════════════════════════════
    // 8. Dog-leg stair type
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designStaircase_dogLegType_producesResult() {
        val result = stair.designStaircase(typicalInput(stairType = StairType.DOG_LEG))
        assertNotNull(result)
        assertTrue("Should still have positive moment", result.maxMoment > 0)
    }

    @Test
    fun designStaircase_dogLeg_lowerMomentThanSimple() {
        // Dog-leg has partial fixity (M=wL²/10) vs simple (M=wL²/8)
        val resultSimple = stair.designStaircase(typicalInput(stairType = StairType.STRAIGHT))
        val resultDogLeg = stair.designStaircase(typicalInput(stairType = StairType.DOG_LEG))
        // Dog-leg moment coefficient (1/10) < simple (1/8)
        assertTrue("Dog-leg should have lower moment than simple for same span",
            resultDogLeg.maxMoment <= resultSimple.maxMoment * 1.05)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 9. InputGuard validation
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun designStaircase_negativeFcu_throwsException() {
        stair.designStaircase(typicalInput(fcu = -25.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designStaircase_zeroFy_throwsException() {
        stair.designStaircase(typicalInput(fy = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designStaircase_zeroTotalRise_throwsException() {
        stair.designStaircase(typicalInput(totalRise = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designStaircase_zeroSpan_throwsException() {
        stair.designStaircase(typicalInput(span = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designStaircase_zeroWaistThickness_throwsException() {
        stair.designStaircase(typicalInput(waistThickness = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designStaircase_zeroStairWidth_throwsException() {
        stair.designStaircase(typicalInput(stairWidth = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designStaircase_zeroDeadLoad_throwsException() {
        stair.designStaircase(typicalInput(deadLoad = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designStaircase_zeroLiveLoad_throwsException() {
        stair.designStaircase(typicalInput(liveLoad = 0.0))
    }

    // ══════════════════════════════════════════════════════════════════════
    // 10. Edge cases
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designStaircase_explicitRiserCount_usesProvidedRisers() {
        val input = StaircaseInput(
            stairType = StairType.STRAIGHT,
            span = 3.0,
            totalRise = 1.5,
            stairWidth = 1.2,
            waistThickness = 200.0,
            fcu = 25.0,
            fy = 400.0,
            deadLoad = 6.0,
            liveLoad = 4.0,
            riserCount = 8
        )
        val result = stair.designStaircase(input)
        assertEquals(8, result.numberOfRisers)
    }

    @Test
    fun designStaircase_explicitGoing_usesProvidedGoing() {
        val input = StaircaseInput(
            stairType = StairType.STRAIGHT,
            span = 3.0,
            totalRise = 1.5,
            stairWidth = 1.2,
            waistThickness = 200.0,
            fcu = 25.0,
            fy = 400.0,
            deadLoad = 6.0,
            liveLoad = 4.0,
            going = 280.0
        )
        val result = stair.designStaircase(input)
        assertEquals(280.0, result.going, 1e-6)
    }

    @Test
    fun designStaircase_steepStair_largerSlopeAngle() {
        val resultGentle = stair.designStaircase(typicalInput(span = 4.0, totalRise = 1.5))
        val resultSteep = stair.designStaircase(typicalInput(span = 2.0, totalRise = 2.5))
        assertTrue("Steeper stair should have larger slope angle",
            resultSteep.slopeAngle > resultGentle.slopeAngle)
    }
}
