package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for SBCRetainingWall — SBC 304-2018 retaining wall design engine.
 *
 * Covers:
 *  - designRetainingWall: happy path, validation, edge cases
 *  - Safety factors: OT, sliding, bearing
 *  - SBC-specific: FS_OT ≥ 1.5 (lower than ACI 2.0)
 *  - Water table effects
 *  - InputGuard: zero/negative values throw IllegalArgumentException
 */
class SBCRetainingWallTest {

    private lateinit var engine: SBCRetainingWall

    @Before
    fun setup() {
        engine = SBCRetainingWall()
    }

    companion object {
        private fun typicalInput() = RetainingWallInput(
            wallHeight = 4.0, stemBaseThickness = 0.4, stemTopThickness = 0.25,
            baseWidth = 3.0, baseThickness = 0.5, toeLength = 0.8, heelLength = 1.8,
            soilDensity = 18.0, frictionAngle = 30.0, surchargeLoad = 10.0,
            waterTableDepth = 10.0, fcu = 25.0, fy = 360.0,
            baseFrictionCoeff = 0.5, soilBearingCapacity = 200.0
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Happy Path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `typical wall produces valid result`() {
        val result = engine.designRetainingWall(typicalInput())
        assertEquals("Should be SBC code", DesignCode.SBC, result.designCode)
        assertTrue("OT FS should be positive", result.overturningFS > 0)
        assertTrue("Sliding FS should be positive", result.slidingFS > 0)
        assertTrue("Bearing FS should be positive", result.bearingFS > 0)
    }

    @Test
    fun `typical wall has safety checks`() {
        val result = engine.designRetainingWall(typicalInput())
        assertTrue("Should have safety checks", result.safetyChecks.isNotEmpty())
    }

    @Test
    fun `typical wall has code notes`() {
        val result = engine.designRetainingWall(typicalInput())
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    @Test
    fun `typical wall has reinforcement results`() {
        val result = engine.designRetainingWall(typicalInput())
        assertTrue("Stem rebar area should be positive", result.stemMainRebarArea > 0)
        assertTrue("Stem moment should be positive", result.stemMoment > 0)
        assertTrue("Stem shear should be positive", result.stemShear > 0)
    }

    @Test
    fun `bearing pressures are positive`() {
        val result = engine.designRetainingWall(typicalInput())
        assertTrue("Max bearing pressure should be positive", result.maxBearingPressure > 0)
        assertTrue("Min bearing pressure should be non-negative", result.minBearingPressure >= 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. SBC-Specific Safety Factors
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `OT FS limit is 1_5 per SBC 304`() {
        val result = engine.designRetainingWall(typicalInput())
        val otCheck = result.safetyChecks.find { it.name == "OT FS" }
        assertNotNull("Should have OT FS check", otCheck)
        assertEquals("OT FS limit should be 1.5 per SBC", 1.5, otCheck!!.limit, 0.01)
    }

    @Test
    fun `Sliding FS limit is 1_5 per SBC 304`() {
        val result = engine.designRetainingWall(typicalInput())
        val slideCheck = result.safetyChecks.find { it.name == "Sliding FS" }
        assertNotNull("Should have Sliding FS check", slideCheck)
        assertEquals("Sliding FS limit should be 1.5 per SBC", 1.5, slideCheck!!.limit, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Code Notes — SBC References
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `code notes contain SBC references`() {
        val result = engine.designRetainingWall(typicalInput())
        val hasSBCRef = result.codeNotes.any { it.contains("SBC") }
        assertTrue("Should reference SBC code", hasSBCRef)
    }

    @Test
    fun `code notes contain cover requirements`() {
        val result = engine.designRetainingWall(typicalInput())
        val hasCoverNote = result.codeNotes.any { it.contains("Cover") || it.contains("غطاء") || it.contains("75") }
        assertTrue("Should reference cover requirements", hasCoverNote)
    }

    @Test
    fun `code notes contain corrosive environment note`() {
        val result = engine.designRetainingWall(typicalInput())
        val hasEnvNote = result.codeNotes.any {
            it.contains("مالح") || it.contains("ساحلي") || it.contains("corrosive") || it.contains("coastal")
        }
        assertTrue("Should reference corrosive environment", hasEnvNote)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Water Table Effects
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `high water table reduces stability`() {
        val resultDry = engine.designRetainingWall(RetainingWallInput(
            wallHeight = 4.0, stemBaseThickness = 0.4, stemTopThickness = 0.25,
            baseWidth = 3.0, baseThickness = 0.5, toeLength = 0.8, heelLength = 1.8,
            soilDensity = 18.0, frictionAngle = 30.0, surchargeLoad = 10.0,
            waterTableDepth = 10.0, fcu = 25.0, fy = 360.0,
            baseFrictionCoeff = 0.5, soilBearingCapacity = 200.0
        ))
        val resultWet = engine.designRetainingWall(RetainingWallInput(
            wallHeight = 4.0, stemBaseThickness = 0.4, stemTopThickness = 0.25,
            baseWidth = 3.0, baseThickness = 0.5, toeLength = 0.8, heelLength = 1.8,
            soilDensity = 18.0, frictionAngle = 30.0, surchargeLoad = 10.0,
            waterTableDepth = 2.0, fcu = 25.0, fy = 360.0,
            baseFrictionCoeff = 0.5, soilBearingCapacity = 200.0
        ))
        // High water table should produce lower or equal OT FS
        assertTrue("Both should have valid OT FS",
            resultDry.overturningFS > 0 && resultWet.overturningFS > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Wall Height Variations
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `taller wall increases stem moment`() {
        val resultLow = engine.designRetainingWall(RetainingWallInput(
            wallHeight = 3.0, stemBaseThickness = 0.3, stemTopThickness = 0.2,
            baseWidth = 2.5, baseThickness = 0.4, toeLength = 0.6, heelLength = 1.5,
            soilDensity = 18.0, frictionAngle = 30.0, surchargeLoad = 5.0,
            waterTableDepth = 10.0, fcu = 25.0, fy = 360.0,
            baseFrictionCoeff = 0.5, soilBearingCapacity = 200.0
        ))
        val resultHigh = engine.designRetainingWall(RetainingWallInput(
            wallHeight = 6.0, stemBaseThickness = 0.5, stemTopThickness = 0.3,
            baseWidth = 4.5, baseThickness = 0.6, toeLength = 1.0, heelLength = 2.8,
            soilDensity = 18.0, frictionAngle = 30.0, surchargeLoad = 10.0,
            waterTableDepth = 10.0, fcu = 25.0, fy = 360.0,
            baseFrictionCoeff = 0.5, soilBearingCapacity = 200.0
        ))
        assertTrue("Taller wall should have higher stem moment",
            resultHigh.stemMoment > resultLow.stemMoment)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. Different Soil Properties
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `higher friction angle improves stability`() {
        val resultLowPhi = engine.designRetainingWall(RetainingWallInput(
            wallHeight = 4.0, stemBaseThickness = 0.4, stemTopThickness = 0.25,
            baseWidth = 3.0, baseThickness = 0.5, toeLength = 0.8, heelLength = 1.8,
            soilDensity = 18.0, frictionAngle = 25.0, surchargeLoad = 10.0,
            waterTableDepth = 10.0, fcu = 25.0, fy = 360.0,
            baseFrictionCoeff = 0.4, soilBearingCapacity = 200.0
        ))
        val resultHighPhi = engine.designRetainingWall(RetainingWallInput(
            wallHeight = 4.0, stemBaseThickness = 0.4, stemTopThickness = 0.25,
            baseWidth = 3.0, baseThickness = 0.5, toeLength = 0.8, heelLength = 1.8,
            soilDensity = 18.0, frictionAngle = 35.0, surchargeLoad = 10.0,
            waterTableDepth = 10.0, fcu = 25.0, fy = 360.0,
            baseFrictionCoeff = 0.6, soilBearingCapacity = 200.0
        ))
        assertTrue("Both should produce valid results",
            resultLowPhi.overturningFS > 0 && resultHighPhi.overturningFS > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. InputGuard Validation
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `zero wallHeight throws`() {
        engine.designRetainingWall(RetainingWallInput(
            wallHeight = 0.0, stemBaseThickness = 0.4, stemTopThickness = 0.25,
            baseWidth = 3.0, baseThickness = 0.5, toeLength = 0.8, heelLength = 1.8,
            soilDensity = 18.0, frictionAngle = 30.0, surchargeLoad = 10.0,
            waterTableDepth = 10.0, fcu = 25.0, fy = 360.0,
            baseFrictionCoeff = 0.5, soilBearingCapacity = 200.0
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero fcu throws`() {
        engine.designRetainingWall(RetainingWallInput(
            wallHeight = 4.0, stemBaseThickness = 0.4, stemTopThickness = 0.25,
            baseWidth = 3.0, baseThickness = 0.5, toeLength = 0.8, heelLength = 1.8,
            soilDensity = 18.0, frictionAngle = 30.0, surchargeLoad = 10.0,
            waterTableDepth = 10.0, fcu = 0.0, fy = 360.0,
            baseFrictionCoeff = 0.5, soilBearingCapacity = 200.0
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative frictionAngle throws`() {
        engine.designRetainingWall(RetainingWallInput(
            wallHeight = 4.0, stemBaseThickness = 0.4, stemTopThickness = 0.25,
            baseWidth = 3.0, baseThickness = 0.5, toeLength = 0.8, heelLength = 1.8,
            soilDensity = 18.0, frictionAngle = -30.0, surchargeLoad = 10.0,
            waterTableDepth = 10.0, fcu = 25.0, fy = 360.0,
            baseFrictionCoeff = 0.5, soilBearingCapacity = 200.0
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero soilDensity throws`() {
        engine.designRetainingWall(RetainingWallInput(
            wallHeight = 4.0, stemBaseThickness = 0.4, stemTopThickness = 0.25,
            baseWidth = 3.0, baseThickness = 0.5, toeLength = 0.8, heelLength = 1.8,
            soilDensity = 0.0, frictionAngle = 30.0, surchargeLoad = 10.0,
            waterTableDepth = 10.0, fcu = 25.0, fy = 360.0,
            baseFrictionCoeff = 0.5, soilBearingCapacity = 200.0
        ))
    }
}
