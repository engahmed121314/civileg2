package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ECPSeismic — ECP 201-2020 seismic design engine.
 * Covers base shear, response spectrum, force distribution,
 * InputGuard validation, and edge cases.
 */
class ECPSeismicTest {

    private lateinit var seismic: ECPSeismic

    @Before
    fun setup() {
        seismic = ECPSeismic()
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. Happy path — base shear
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `calculateBaseShear_validInputs_producesResult`() {
        val result = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 30.0
        )
        assertNotNull(result)
        assertTrue("Base shear > 0", result.baseShear > 0)
    }

    @Test
    fun `calculateBaseShear_zoneFactorIsSet`() {
        val result = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 30.0
        )
        assertEquals(0.15, result.zoneFactor, 0.001) // Zone 3 = 0.15
    }

    @Test
    fun `calculateBaseShear_soilFactorIsSet`() {
        val result = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 30.0
        )
        assertEquals(1.5, result.soilFactor, 0.001) // Soil C = 1.5
    }

    @Test
    fun `calculateBaseShear_importanceFactorIsPreserved`() {
        val result = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_3, SoilType.C, 1.2, 5.0, 30.0
        )
        assertEquals(1.2, result.importanceFactor, 0.001)
    }

    @Test
    fun `calculateBaseShear_responseModificationIsPreserved`() {
        val result = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 30.0
        )
        assertEquals(5.0, result.responseModification, 0.001)
    }

    @Test
    fun `calculateBaseShear_proportionalToWeight`() {
        val light = seismic.calculateBaseShear(
            5000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 30.0
        )
        val heavy = seismic.calculateBaseShear(
            20000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 30.0
        )
        val ratio = heavy.baseShear / light.baseShear
        assertEquals(4.0, ratio, 0.1) // 20000/5000 = 4
    }

    @Test
    fun `calculateBaseShear_higherZone_largerShear`() {
        val zone1 = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_1, SoilType.C, 1.0, 5.0, 30.0
        )
        val zone5 = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_5, SoilType.C, 1.0, 5.0, 30.0
        )
        assertTrue("Higher zone → larger base shear", zone5.baseShear > zone1.baseShear)
    }

    @Test
    fun `calculateBaseShear_softSoil_largerShear`() {
        val rock = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_3, SoilType.A, 1.0, 5.0, 30.0
        )
        val soft = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_3, SoilType.D, 1.0, 5.0, 30.0
        )
        assertTrue("Soft soil → larger base shear", soft.baseShear >= rock.baseShear)
    }

    @Test
    fun `calculateBaseShear_meetsMinimumTwoPercent`() {
        val result = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_1, SoilType.A, 1.0, 5.0, 30.0
        )
        val minShear = 0.02 * 10000.0
        assertTrue("Base shear >= 2% of weight", result.baseShear >= minShear * 0.99)
    }

    @Test
    fun `calculateBaseShear_codeReferenceIsSet`() {
        val result = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 30.0
        )
        assertTrue("Code reference should be set", result.codeReference.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Response spectrum
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `getResponseSpectrum_validInputs_producesResult`() {
        val result = seismic.getResponseSpectrum(0.5, 0.05, SoilType.C, 0.15, 1.0)
        assertNotNull(result)
        assertTrue("Spectral acceleration > 0", result.spectralAcceleration > 0)
    }

    @Test
    fun `getResponseSpectrum_shortPeriod_highAcceleration`() {
        val shortT = seismic.getResponseSpectrum(0.1, 0.05, SoilType.C, 0.15, 1.0)
        val longT = seismic.getResponseSpectrum(2.0, 0.05, SoilType.C, 0.15, 1.0)
        assertTrue("Short period should have higher acceleration", shortT.spectralAcceleration >= longT.spectralAcceleration)
    }

    @Test
    fun `getResponseSpectrum_dampingRatioAffectsResult`() {
        val lowDamp = seismic.getResponseSpectrum(0.2, 0.02, SoilType.C, 0.15, 1.0)
        val highDamp = seismic.getResponseSpectrum(0.2, 0.20, SoilType.C, 0.15, 1.0)
        assertTrue("Higher damping → lower acceleration", highDamp.spectralAcceleration <= lowDamp.spectralAcceleration)
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Force distribution
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `distributeSeismicForces_validInputs_producesResults`() {
        val weights = listOf(2000.0, 2000.0, 1500.0, 1000.0)
        val heights = listOf(3.0, 6.0, 9.0, 12.0)
        val results = seismic.distributeSeismicForces(500.0, weights, heights)
        assertEquals(4, results.size)
    }

    @Test
    fun `distributeSeismicForces_sumOfForcesEqualsBaseShear`() {
        val weights = listOf(2000.0, 2000.0, 1500.0, 1000.0)
        val heights = listOf(3.0, 6.0, 9.0, 12.0)
        val baseShear = 500.0
        val results = seismic.distributeSeismicForces(baseShear, weights, heights)
        val sumForces = results.sumOf { it.lateralForce }
        assertEquals(baseShear, sumForces, 0.1)
    }

    @Test
    fun `distributeSeismicForces_topFloorLargestForce`() {
        val weights = listOf(2000.0, 2000.0, 2000.0, 2000.0)
        val heights = listOf(3.0, 6.0, 9.0, 12.0)
        val results = seismic.distributeSeismicForces(500.0, weights, heights)
        // For equal weights, the top floor (highest) should get the largest force
        val topForce = results.last().lateralForce
        val bottomForce = results.first().lateralForce
        assertTrue("Top floor should have larger force", topForce >= bottomForce)
    }

    @Test
    fun `distributeSeismicForces_storyShearDecreasesUpward`() {
        val weights = listOf(2000.0, 2000.0, 2000.0, 2000.0)
        val heights = listOf(3.0, 6.0, 9.0, 12.0)
        val results = seismic.distributeSeismicForces(500.0, weights, heights)
        // Story shear at base should equal base shear, then decrease going up
        val baseStoryShear = results.first().storyShear
        val topStoryShear = results.last().storyShear
        assertTrue("Base story shear >= top story shear", baseStoryShear >= topStoryShear)
    }

    @Test
    fun `distributeSeismicForces_emptyLists_returnsEmpty`() {
        val results = seismic.distributeSeismicForces(500.0, emptyList(), emptyList())
        assertTrue("Should return empty for empty inputs", results.isEmpty())
    }

    @Test
    fun `distributeSeismicForces_mismatchedSizes_returnsEmpty`() {
        val results = seismic.distributeSeismicForces(
            500.0, listOf(2000.0, 2000.0), listOf(3.0)
        )
        assertTrue("Should return empty for mismatched sizes", results.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Zone and soil information
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `getCodeName_returnsECP`() {
        assertEquals(DesignCode.ECP, seismic.getCodeName())
    }

    @Test
    fun `getSeismicZones_returnsAllZones`() {
        val zones = seismic.getSeismicZones()
        assertEquals(5, zones.size)
    }

    @Test
    fun `getZoneFactors_returnsAllFactors`() {
        val factors = seismic.getZoneFactors()
        assertEquals(5, factors.size)
        assertTrue("Zone 1 factor < Zone 5 factor", factors[SeismicZone.ZONE_1]!! < factors[SeismicZone.ZONE_5]!!)
    }

    @Test
    fun `getSoilFactors_returnsAllTypes`() {
        val factors = seismic.getSoilFactors()
        assertEquals(5, factors.size)
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. InputGuard validation
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `calculateBaseShear_throwsForZeroWeight`() {
        seismic.calculateBaseShear(0.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 30.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateBaseShear_throwsForNegativeWeight`() {
        seismic.calculateBaseShear(-10000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 30.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateBaseShear_throwsForZeroImportanceFactor`() {
        seismic.calculateBaseShear(10000.0, SeismicZone.ZONE_3, SoilType.C, 0.0, 5.0, 30.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateBaseShear_throwsForZeroResponseModification`() {
        seismic.calculateBaseShear(10000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 0.0, 30.0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. Edge cases
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `calculateBaseShear_veryLowWeight_producesSmallShear`() {
        val result = seismic.calculateBaseShear(
            100.0, SeismicZone.ZONE_1, SoilType.A, 1.0, 8.0, 30.0
        )
        assertTrue("Small weight → small base shear", result.baseShear < 100.0)
    }

    @Test
    fun `calculateBaseShear_withoutBuildingHeight_estimatesHeight`() {
        val result = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 0.0
        )
        assertTrue("Should estimate height and produce result", result.baseShear > 0)
        assertTrue("Should have warning about estimated height", result.warnings.isNotEmpty())
    }

    @Test
    fun `calculateBaseShear_rockSoil_lowerShearThanSoftSoil`() {
        val rock = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_3, SoilType.B, 1.0, 5.0, 30.0
        )
        val soft = seismic.calculateBaseShear(
            10000.0, SeismicZone.ZONE_3, SoilType.D, 1.0, 5.0, 30.0
        )
        assertTrue("Rock should give lower shear than soft soil", rock.baseShear <= soft.baseShear)
    }
}
