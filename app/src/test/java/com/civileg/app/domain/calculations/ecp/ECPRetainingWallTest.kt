package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ECPRetainingWall — ECP 203-2020 retaining wall design engine.
 * Covers stability checks, stem/toe/heel design,
 * InputGuard validation, and edge cases.
 */
class ECPRetainingWallTest {

    private lateinit var wall: ECPRetainingWall

    @Before
    fun setup() {
        wall = ECPRetainingWall()
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun defaultInput(
        wallHeight: Double = 4.0,
        stemBaseThickness: Double = 0.4,
        stemTopThickness: Double = 0.25,
        baseWidth: Double = 2.8,
        baseThickness: Double = 0.4,
        toeLength: Double = 0.6,
        heelLength: Double = 1.8,
        soilDensity: Double = 18.0,
        frictionAngle: Double = 30.0,
        surchargeLoad: Double = 10.0,
        waterTableDepth: Double = 10.0,
        fcu: Double = 25.0,
        fy: Double = 360.0,
        baseFrictionCoeff: Double = 0.5,
        soilBearingCapacity: Double = 200.0
    ) = RetainingWallInput(
        wallHeight, stemBaseThickness, stemTopThickness,
        baseWidth, baseThickness, toeLength, heelLength,
        soilDensity, frictionAngle, surchargeLoad, waterTableDepth,
        fcu, fy, baseFrictionCoeff, soilBearingCapacity
    )

    // ═══════════════════════════════════════════════════════════════
    // 1. Happy path
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designRetainingWall_validInputs_producesResult`() {
        val result = wall.designRetainingWall(defaultInput())
        assertNotNull(result)
        assertEquals(DesignCode.ECP, result.designCode)
    }

    @Test
    fun `designRetainingWall_overturningFSIsPositive`() {
        val result = wall.designRetainingWall(defaultInput())
        assertTrue("Overturning FS > 0", result.overturningFS > 0)
    }

    @Test
    fun `designRetainingWall_slidingFSIsPositive`() {
        val result = wall.designRetainingWall(defaultInput())
        assertTrue("Sliding FS > 0", result.slidingFS > 0)
    }

    @Test
    fun `designRetainingWall_bearingFSIsPositive`() {
        val result = wall.designRetainingWall(defaultInput())
        assertTrue("Bearing FS > 0", result.bearingFS > 0)
    }

    @Test
    fun `designRetainingWall_maxBearingPressureIsPositive`() {
        val result = wall.designRetainingWall(defaultInput())
        assertTrue("Max bearing pressure > 0", result.maxBearingPressure > 0)
    }

    @Test
    fun `designRetainingWall_stemMomentIsPositive`() {
        val result = wall.designRetainingWall(defaultInput())
        assertTrue("Stem moment > 0", result.stemMoment > 0)
    }

    @Test
    fun `designRetainingWall_stemShearIsPositive`() {
        val result = wall.designRetainingWall(defaultInput())
        assertTrue("Stem shear > 0", result.stemShear > 0)
    }

    @Test
    fun `designRetainingWall_stemMainRebarAreaIsPositive`() {
        val result = wall.designRetainingWall(defaultInput())
        assertTrue("Stem rebar area > 0", result.stemMainRebarArea > 0)
    }

    @Test
    fun `designRetainingWall_safetyChecksExist`() {
        val result = wall.designRetainingWall(defaultInput())
        assertTrue("Should have safety checks", result.safetyChecks.isNotEmpty())
    }

    @Test
    fun `designRetainingWall_toeAndHeelMomentsArePositive`() {
        val result = wall.designRetainingWall(defaultInput())
        assertTrue("Toe moment > 0", result.toeMoment > 0)
        assertTrue("Heel moment > 0", result.heelMoment > 0)
    }

    @Test
    fun `designRetainingWall_codeNotesExist`() {
        val result = wall.designRetainingWall(defaultInput())
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Stability behavior
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designRetainingWall_higherWall_lowerOverturningFS`() {
        val lowWall = wall.designRetainingWall(defaultInput(wallHeight = 3.0))
        val highWall = wall.designRetainingWall(defaultInput(wallHeight = 6.0))
        assertTrue("Higher wall should have lower OT FS",
            highWall.overturningFS < lowWall.overturningFS)
    }

    @Test
    fun `designRetainingWall_widerBase_higherOverturningFS`() {
        val narrowBase = wall.designRetainingWall(defaultInput(baseWidth = 2.0, heelLength = 1.2))
        val wideBase = wall.designRetainingWall(defaultInput(baseWidth = 3.5, heelLength = 2.5))
        assertTrue("Wider base should have higher OT FS",
            wideBase.overturningFS > narrowBase.overturningFS)
    }

    @Test
    fun `designRetainingWall_highFrictionAngle_higherSlidingFS`() {
        val lowPhi = wall.designRetainingWall(defaultInput(frictionAngle = 25.0))
        val highPhi = wall.designRetainingWall(defaultInput(frictionAngle = 35.0))
        assertTrue("Higher friction angle → higher sliding FS",
            highPhi.slidingFS > lowPhi.slidingFS)
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Water table effect
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designRetainingWall_highWaterTable_reducesStability`() {
        val noWater = wall.designRetainingWall(defaultInput(waterTableDepth = 10.0))
        val withWater = wall.designRetainingWall(defaultInput(waterTableDepth = 2.0))
        // Water pressure should reduce overturning stability
        assertTrue("Water table should be accounted for",
            withWater.overturningFS <= noWater.overturningFS * 1.01)
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. InputGuard validation
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `designRetainingWall_throwsForZeroWallHeight`() {
        wall.designRetainingWall(defaultInput(wallHeight = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designRetainingWall_throwsForNegativeWallHeight`() {
        wall.designRetainingWall(defaultInput(wallHeight = -4.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designRetainingWall_throwsForZeroStemBaseThickness`() {
        wall.designRetainingWall(defaultInput(stemBaseThickness = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designRetainingWall_throwsForZeroBaseWidth`() {
        wall.designRetainingWall(defaultInput(baseWidth = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designRetainingWall_throwsForZeroBaseThickness`() {
        wall.designRetainingWall(defaultInput(baseThickness = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designRetainingWall_throwsForZeroSoilDensity`() {
        wall.designRetainingWall(defaultInput(soilDensity = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designRetainingWall_throwsForZeroFrictionAngle`() {
        wall.designRetainingWall(defaultInput(frictionAngle = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designRetainingWall_throwsForZeroFcu`() {
        wall.designRetainingWall(defaultInput(fcu = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designRetainingWall_throwsForZeroFy`() {
        wall.designRetainingWall(defaultInput(fy = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designRetainingWall_throwsForZeroSBC`() {
        wall.designRetainingWall(defaultInput(soilBearingCapacity = 0.0))
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. Edge cases
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designRetainingWall_highSBC_isSafe`() {
        val result = wall.designRetainingWall(defaultInput(soilBearingCapacity = 300.0))
        val bearingCheck = result.safetyChecks.find { it.name == "Bearing" }
        if (bearingCheck != null) {
            assertTrue("High SBC should pass bearing check", bearingCheck.isSafe)
        }
    }

    @Test
    fun `designRetainingWall_noSurcharge_reducesMoment`() {
        val withSurcharge = wall.designRetainingWall(defaultInput(surchargeLoad = 20.0))
        val noSurcharge = wall.designRetainingWall(defaultInput(surchargeLoad = 0.0))
        assertTrue("No surcharge should reduce stem moment",
            noSurcharge.stemMoment <= withSurcharge.stemMoment)
    }

    @Test
    fun `designRetainingWall_minBearingPressureNonNegative`() {
        val result = wall.designRetainingWall(defaultInput())
        assertTrue("Min bearing pressure >= 0", result.minBearingPressure >= 0)
    }

    // ═══════════════════════════════════════════════════════════════
    // W5: water table model, factored toe, labels
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `lateralLoads_dry_matchesRankineTriangleAndSurcharge`() {
        val ka = 1.0 / 3.0
        val l = wall.lateralLoads(h = 3.0, zwt = 10.0, gamma = 18.0, ka = ka, q = 10.0)
        val tri = 0.5 * ka * 18.0 * 9.0
        val sur = ka * 10.0 * 3.0
        assertEquals(tri + sur, l.force, 1e-9)
        assertEquals(tri * 1.0 + sur * 1.5, l.moment, 1e-9)
    }

    @Test
    fun `lateralLoads_waterAtBase_equalsDry`() {
        val ka = 1.0 / 3.0
        val dry = wall.lateralLoads(3.0, 10.0, 18.0, ka, 10.0)
        val atBase = wall.lateralLoads(3.0, 3.0, 18.0, ka, 10.0)
        assertEquals(dry.force, atBase.force, 1e-9)
        assertEquals(dry.moment, atBase.moment, 1e-9)
    }

    @Test
    fun `lateralLoads_partialWaterTable_increasesForceAndMoment`() {
        val ka = 1.0 / 3.0
        val dry = wall.lateralLoads(3.0, 10.0, 18.0, ka, 0.0)
        val wet = wall.lateralLoads(3.0, 1.0, 18.0, ka, 0.0)
        assertTrue(wet.force > dry.force)
        assertTrue(wet.moment > dry.moment)
    }

    @Test
    fun `designRetainingWall_stemMomentWithWaterTable_exceedsDry`() {
        val dry = wall.designRetainingWall(defaultInput(waterTableDepth = 10.0))
        val wet = wall.designRetainingWall(defaultInput(waterTableDepth = 1.0))
        assertTrue(wet.stemMoment > dry.stemMoment)
    }

    @Test
    fun `designRetainingWall_shearCheckLimitIsQcu`() {
        val shear = wall.designRetainingWall(defaultInput()).safetyChecks.first { it.name == "Shear" }
        assertEquals(0.24 * Math.sqrt(25.0 / 1.5), shear.limit, 1e-9)
    }

    @Test
    fun `designRetainingWall_hasFootingFlexureAndShearChecks`() {
        val names = wall.designRetainingWall(defaultInput()).safetyChecks.map { it.name }
        assertTrue(names.containsAll(listOf("Toe Flexure", "Heel Flexure", "Toe Shear", "Heel Shear")))
    }

    @Test
    fun `designRetainingWall_toeMomentAndShearAreFactored`() {
        val r = wall.designRetainingWall(defaultInput())
        // Factored toe shear must be 1.4x the average bearing pressure times toe length.
        val avg = (r.maxBearingPressure + r.minBearingPressure) / 2.0
        assertEquals(1.4 * avg * 0.6, r.toeShear, 1e-6)
    }

    @Test
    fun `designRetainingWall_unsourcedItemsAreMarkedNotAssessed`() {
        val notes = wall.designRetainingWall(defaultInput()).codeNotes.joinToString(" ")
        assertTrue(notes.contains("NOT ASSESSED"))
    }
}
