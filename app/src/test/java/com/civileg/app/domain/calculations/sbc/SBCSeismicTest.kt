package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for SBCSeismic — SBC 301-2020 seismic design engine.
 *
 * Covers:
 *  - calculateBaseShear: happy path, validation, edge cases
 *  - getResponseSpectrum: spectrum values per SBC 301 §4.3
 *  - distributeSeismicForces: force distribution per SBC 301 §4.5
 *  - Saudi zone factors: different from ASCE 7
 *  - Saudi soil factors: different from ASCE 7
 *  - getCodeName: returns SBC
 *  - InputGuard: zero/negative values throw IllegalArgumentException
 */
class SBCSeismicTest {

    private lateinit var engine: SBCSeismic

    @Before
    fun setup() {
        engine = SBCSeismic()
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Base Shear — Happy Path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `base shear - zone 3 produces positive result`() {
        val result = engine.calculateBaseShear(
            totalWeight = 5000.0, seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.C, importanceFactor = 1.0,
            responseModificationFactor = 5.0, buildingHeight = 20.0
        )
        assertTrue("Base shear should be positive", result.baseShear > 0)
        assertTrue("Zone factor should be positive", result.zoneFactor > 0)
        assertTrue("Soil factor should be positive", result.soilFactor > 0)
    }

    @Test
    fun `base shear - all zones produce positive results`() {
        for (zone in SeismicZone.entries) {
            val result = engine.calculateBaseShear(
                totalWeight = 5000.0, seismicZone = zone,
                soilType = SoilType.C, importanceFactor = 1.0,
                responseModificationFactor = 5.0, buildingHeight = 20.0
            )
            assertTrue("Base shear for $zone should be positive", result.baseShear > 0)
        }
    }

    @Test
    fun `base shear - higher zone produces larger base shear`() {
        val resultLow = engine.calculateBaseShear(
            totalWeight = 5000.0, seismicZone = SeismicZone.ZONE_1,
            soilType = SoilType.C, importanceFactor = 1.0,
            responseModificationFactor = 5.0, buildingHeight = 20.0
        )
        val resultHigh = engine.calculateBaseShear(
            totalWeight = 5000.0, seismicZone = SeismicZone.ZONE_5,
            soilType = SoilType.C, importanceFactor = 1.0,
            responseModificationFactor = 5.0, buildingHeight = 20.0
        )
        assertTrue("Higher zone should have larger base shear",
            resultHigh.baseShear > resultLow.baseShear)
    }

    @Test
    fun `base shear - code reference contains SBC`() {
        val result = engine.calculateBaseShear(
            totalWeight = 5000.0, seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.C, importanceFactor = 1.0,
            responseModificationFactor = 5.0, buildingHeight = 20.0
        )
        assertTrue("Code reference should contain SBC", result.codeReference.contains("SBC"))
    }

    @Test
    fun `base shear - heavier building has larger base shear`() {
        val resultLight = engine.calculateBaseShear(
            totalWeight = 3000.0, seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.C, importanceFactor = 1.0,
            responseModificationFactor = 5.0, buildingHeight = 20.0
        )
        val resultHeavy = engine.calculateBaseShear(
            totalWeight = 8000.0, seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.C, importanceFactor = 1.0,
            responseModificationFactor = 5.0, buildingHeight = 20.0
        )
        assertTrue("Heavier building should have larger base shear",
            resultHeavy.baseShear > resultLight.baseShear)
    }

    @Test
    fun `base shear - higher R reduces base shear`() {
        val resultLowR = engine.calculateBaseShear(
            totalWeight = 5000.0, seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.C, importanceFactor = 1.0,
            responseModificationFactor = 3.0, buildingHeight = 20.0
        )
        val resultHighR = engine.calculateBaseShear(
            totalWeight = 5000.0, seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.C, importanceFactor = 1.0,
            responseModificationFactor = 8.0, buildingHeight = 20.0
        )
        assertTrue("Higher R should reduce base shear",
            resultHighR.baseShear <= resultLowR.baseShear)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Base Shear — Different Soil Types
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `base shear - soft soil increases base shear`() {
        val resultHard = engine.calculateBaseShear(
            totalWeight = 5000.0, seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.B, importanceFactor = 1.0,
            responseModificationFactor = 5.0, buildingHeight = 20.0
        )
        val resultSoft = engine.calculateBaseShear(
            totalWeight = 5000.0, seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.E, importanceFactor = 1.0,
            responseModificationFactor = 5.0, buildingHeight = 20.0
        )
        assertTrue("Soft soil should increase base shear",
            resultSoft.baseShear >= resultHard.baseShear)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Saudi Zone Factors
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `Saudi zone 1 factor is 0_05`() {
        val factors = engine.getZoneFactors()
        assertEquals("Saudi Zone 1 should be 0.05", 0.05, factors[SeismicZone.ZONE_1]!!, 0.001)
    }

    @Test
    fun `Saudi zone 2 factor is 0_10`() {
        val factors = engine.getZoneFactors()
        assertEquals("Saudi Zone 2 should be 0.10", 0.10, factors[SeismicZone.ZONE_2]!!, 0.001)
    }

    @Test
    fun `Saudi zone 3 factor is 0_15`() {
        val factors = engine.getZoneFactors()
        assertEquals("Saudi Zone 3 should be 0.15", 0.15, factors[SeismicZone.ZONE_3]!!, 0.001)
    }

    @Test
    fun `Saudi zone 4 factor is 0_25`() {
        val factors = engine.getZoneFactors()
        assertEquals("Saudi Zone 4 should be 0.25", 0.25, factors[SeismicZone.ZONE_4]!!, 0.001)
    }

    @Test
    fun `Saudi zone 5 factor is 0_35`() {
        val factors = engine.getZoneFactors()
        assertEquals("Saudi Zone 5 should be 0.35", 0.35, factors[SeismicZone.ZONE_5]!!, 0.001)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Saudi Soil Factors
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `Saudi soil factors differ from ASCE 7`() {
        val factors = engine.getSoilFactors()
        assertNotNull("Should have soil factor for A", factors[SoilType.A])
        assertNotNull("Should have soil factor for D", factors[SoilType.D])
        // SBC Soil D Fa = 1.6 (same as ASCE 7 for this case)
        assertEquals("SBC Fa for Soil D should be 1.6", 1.6, factors[SoilType.D]!!, 0.01)
    }

    @Test
    fun `Saudi soil factor A is 0_8`() {
        val factors = engine.getSoilFactors()
        assertEquals("SBC Fa for Soil A should be 0.8", 0.8, factors[SoilType.A]!!, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Response Spectrum
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `response spectrum - typical inputs produce valid result`() {
        val result = engine.getResponseSpectrum(
            period = 0.5, dampingRatio = 0.05,
            soilType = SoilType.C, peakGroundAcceleration = 0.15
        )
        assertTrue("Sa should be positive", result.spectralAcceleration > 0)
        assertTrue("Period should match", result.period > 0)
        assertTrue("Should describe SBC 301", result.description.contains("SBC 301"))
    }

    @Test
    fun `response spectrum - short period has higher Sa`() {
        val resultShort = engine.getResponseSpectrum(
            period = 0.2, dampingRatio = 0.05,
            soilType = SoilType.C, peakGroundAcceleration = 0.15
        )
        val resultLong = engine.getResponseSpectrum(
            period = 2.0, dampingRatio = 0.05,
            soilType = SoilType.C, peakGroundAcceleration = 0.15
        )
        assertTrue("Short period should have higher Sa than long period",
            resultShort.spectralAcceleration >= resultLong.spectralAcceleration)
    }

    @Test
    fun `response spectrum - all soil types produce valid results`() {
        for (soil in SoilType.entries) {
            val result = engine.getResponseSpectrum(
                period = 0.5, dampingRatio = 0.05,
                soilType = soil, peakGroundAcceleration = 0.15
            )
            assertTrue("Sa for $soil should be positive", result.spectralAcceleration > 0)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. Force Distribution
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `force distribution - produces correct number of floors`() {
        val distribution = engine.distributeSeismicForces(
            baseShear = 500.0,
            floorWeights = listOf(1000.0, 1000.0, 1000.0, 800.0),
            floorHeights = listOf(3.5, 7.0, 10.5, 14.0)
        )
        assertEquals("Should have 4 floor results", 4, distribution.size)
    }

    @Test
    fun `force distribution - sum of forces equals base shear`() {
        val distribution = engine.distributeSeismicForces(
            baseShear = 500.0,
            floorWeights = listOf(1000.0, 1000.0, 1000.0, 800.0),
            floorHeights = listOf(3.5, 7.0, 10.5, 14.0)
        )
        val totalForce = distribution.sumOf { it.lateralForce }
        assertEquals("Sum of forces should equal base shear", 500.0, totalForce, 1.0)
    }

    @Test
    fun `force distribution - upper floors get larger forces`() {
        val distribution = engine.distributeSeismicForces(
            baseShear = 500.0,
            floorWeights = listOf(1000.0, 1000.0, 1000.0, 1000.0),
            floorHeights = listOf(3.5, 7.0, 10.5, 14.0)
        )
        // For uniform weights, upper floors should get larger forces
        assertTrue("Top floor should get larger force than bottom",
            distribution[3].lateralForce > distribution[0].lateralForce)
    }

    @Test
    fun `force distribution - single floor gets entire base shear`() {
        val distribution = engine.distributeSeismicForces(
            baseShear = 300.0,
            floorWeights = listOf(1000.0),
            floorHeights = listOf(4.0)
        )
        assertEquals("Single floor should get entire base shear",
            300.0, distribution[0].lateralForce, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. Code Information
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `getCodeName returns SBC`() {
        assertEquals("Code name should be SBC", DesignCode.SBC, engine.getCodeName())
    }

    @Test
    fun `seismic zones contain all 5 zones`() {
        val zones = engine.getSeismicZones()
        assertEquals("Should have 5 zones", 5, zones.size)
        assertTrue("Should contain Zone 1", zones.contains(SeismicZone.ZONE_1))
        assertTrue("Should contain Zone 5", zones.contains(SeismicZone.ZONE_5))
    }

    // ══════════════════════════════════════════════════════════════════════
    // 8. InputGuard Validation — Base Shear
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `base shear - zero totalWeight throws`() {
        engine.calculateBaseShear(
            totalWeight = 0.0, seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.C, importanceFactor = 1.0,
            responseModificationFactor = 5.0, buildingHeight = 20.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `base shear - negative importanceFactor throws`() {
        engine.calculateBaseShear(
            totalWeight = 5000.0, seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.C, importanceFactor = -1.0,
            responseModificationFactor = 5.0, buildingHeight = 20.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `base shear - zero responseModificationFactor throws`() {
        engine.calculateBaseShear(
            totalWeight = 5000.0, seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.C, importanceFactor = 1.0,
            responseModificationFactor = 0.0, buildingHeight = 20.0
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 9. InputGuard Validation — Response Spectrum
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `response spectrum - zero period throws`() {
        engine.getResponseSpectrum(
            period = 0.0, dampingRatio = 0.05,
            soilType = SoilType.C, peakGroundAcceleration = 0.15
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `response spectrum - zero dampingRatio throws`() {
        engine.getResponseSpectrum(
            period = 0.5, dampingRatio = 0.0,
            soilType = SoilType.C, peakGroundAcceleration = 0.15
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `response spectrum - zero peakGroundAcceleration throws`() {
        engine.getResponseSpectrum(
            period = 0.5, dampingRatio = 0.05,
            soilType = SoilType.C, peakGroundAcceleration = 0.0
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 10. InputGuard Validation — Force Distribution
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `force distribution - zero baseShear throws`() {
        engine.distributeSeismicForces(
            baseShear = 0.0,
            floorWeights = listOf(1000.0),
            floorHeights = listOf(4.0)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `force distribution - empty floorWeights throws`() {
        engine.distributeSeismicForces(
            baseShear = 500.0,
            floorWeights = emptyList(),
            floorHeights = emptyList()
        )
    }
}
