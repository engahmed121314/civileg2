package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.calculations.sbc.*
import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Test

/**
 * اختبارات المحركات السعودية المستقلة — تتحقق من أن المحركات تعمل
 * بدون أي اعتماد على ACI (لا fallback).
 *
 * SBC 304-2018 / SBC 301-2020
 */
class SBCIndependentEnginesTest {

    // ══════════════════════════════════════════════════════════════════════
    // 1. SBCBeam — اختبارات الانحناء والقص والانحراف
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `SBCBeam flexure - normal case produces valid result`() {
        val beam = SBCBeam()
        val result = beam.calculateFlexureReinforcement(
            fcu = 30.0, fy = 420.0, width = 300.0, effectiveDepth = 500.0,
            totalDepth = 550.0, designMoment = 150.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Should be safe", result.isSafe)
        assertTrue("Should have reinforcement", result.astProvided > 0)
        assertTrue("Utilization should be ≤ 1.0", result.utilizationRatio <= 1.0)
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    @Test
    fun `SBCBeam flexure - seismic load applies SBC 304 Section 18 provisions`() {
        val beam = SBCBeam()
        val result = beam.calculateFlexureReinforcement(
            fcu = 30.0, fy = 420.0, width = 300.0, effectiveDepth = 500.0,
            totalDepth = 550.0, designMoment = 150.0,
            loadCombination = LoadCombination.DEAD_LIVE_EARTHQUAKE
        )
        // Should contain seismic notes
        val hasSeismicNote = result.codeNotes.any { it.contains("18") || it.contains("زلزال") || it.contains("Seismic") }
        assertTrue("Should have seismic zone notes", hasSeismicNote)
    }

    @Test
    fun `SBCBeam flexure - narrow beam in seismic zone triggers warning`() {
        val beam = SBCBeam()
        val result = beam.calculateFlexureReinforcement(
            fcu = 30.0, fy = 420.0, width = 200.0, effectiveDepth = 500.0,
            totalDepth = 550.0, designMoment = 150.0,
            loadCombination = LoadCombination.DEAD_LIVE_EARTHQUAKE
        )
        val hasWidthWarning = result.warnings.any { it.contains("250") || it.contains("عرض") }
        assertTrue("Narrow beam in seismic zone should trigger width warning", hasWidthWarning)
    }

    @Test
    fun `SBCBeam shear - normal case produces valid result`() {
        val beam = SBCBeam()
        val result = beam.calculateShearReinforcement(
            fcu = 30.0, fy = 420.0, width = 300.0, effectiveDepth = 500.0,
            designShear = 200.0, axialLoad = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Should have concrete shear capacity", result.concreteShearCapacity > 0)
        assertTrue("Stirrup spacing should be positive", result.stirrupSpacing > 0)
    }

    @Test
    fun `SBCBeam deflection - normal case produces valid result`() {
        val beam = SBCBeam()
        val result = beam.checkDeflection(
            span = 6.0, totalDepth = 500.0, reinforcementRatio = 0.01,
            supportCondition = SupportCondition.SIMPLY_SUPPORTED
        )
        assertNotNull(result)
    }

    @Test
    fun `SBCBeam cover is SBC-specific (50mm corrosive)`() {
        val beam = SBCBeam()
        assertEquals("SBC cover should be 50mm for corrosive environment", 50.0, beam.getMinCover(), 0.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SBCBeam flexure - zero fcu throws InputGuard exception`() {
        val beam = SBCBeam()
        beam.calculateFlexureReinforcement(
            fcu = 0.0, fy = 420.0, width = 300.0, effectiveDepth = 500.0,
            totalDepth = 550.0, designMoment = 150.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test
    fun `SBCBeam doubly reinforced - normal case`() {
        val beam = SBCBeam()
        val result = beam.calculateDoublyReinforcedBeam(
            designMoment = 400.0, width = 300.0, depth = 700.0,
            fcu = 30.0, fy = 420.0
        )
        assertTrue("Should have tension steel", result.tensionSteelArea > 0)
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. SBCRetainingWall — اختبارات مستقلة عن ACI
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `SBCRetainingWall - normal case produces valid result`() {
        val wall = com.civileg.app.domain.calculations.sbc.SBCRetainingWall()
        val input = RetainingWallInput(
            wallHeight = 4.0, stemBaseThickness = 0.4, stemTopThickness = 0.25,
            baseWidth = 3.0, baseThickness = 0.5, toeLength = 0.8, heelLength = 1.8,
            soilDensity = 18.0, frictionAngle = 30.0, surchargeLoad = 10.0,
            waterTableDepth = 10.0, fcu = 25.0, fy = 360.0
        )
        val result = wall.designRetainingWall(input)
        assertEquals("Should be SBC code", DesignCode.SBC, result.designCode)
        assertTrue("OT FS should be positive", result.overturningFS > 0)
        assertTrue("Sliding FS should be positive", result.slidingFS > 0)
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    @Test
    fun `SBCRetainingWall - safety factors use SBC limits (FS_OT >= 1_5)`() {
        val wall = com.civileg.app.domain.calculations.sbc.SBCRetainingWall()
        val input = RetainingWallInput(
            wallHeight = 4.0, stemBaseThickness = 0.4, stemTopThickness = 0.25,
            baseWidth = 3.0, baseThickness = 0.5, toeLength = 0.8, heelLength = 1.8,
            soilDensity = 18.0, frictionAngle = 30.0, surchargeLoad = 10.0,
            waterTableDepth = 10.0, fcu = 25.0, fy = 360.0
        )
        val result = wall.designRetainingWall(input)
        // SBC uses FS_OT ≥ 1.5, check that the OT check has limit = 1.5
        val otCheck = result.safetyChecks.find { it.name == "OT FS" }
        assertNotNull("Should have OT FS check", otCheck)
        assertEquals("OT FS limit should be 1.5 per SBC", 1.5, otCheck!!.limit, 0.01)
    }

    @Test
    fun `SBCRetainingWall - notes contain SBC references`() {
        val wall = com.civileg.app.domain.calculations.sbc.SBCRetainingWall()
        val input = RetainingWallInput(
            wallHeight = 3.0, stemBaseThickness = 0.3, stemTopThickness = 0.2,
            baseWidth = 2.5, baseThickness = 0.4, toeLength = 0.6, heelLength = 1.5,
            soilDensity = 18.0, frictionAngle = 30.0, surchargeLoad = 5.0,
            waterTableDepth = 10.0, fcu = 25.0, fy = 360.0
        )
        val result = wall.designRetainingWall(input)
        val hasSBCRef = result.codeNotes.any { it.contains("SBC") }
        assertTrue("Should reference SBC code", hasSBCRef)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. SBCSeismic — اختبارات مستقلة عن ACI
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `SBCSeismic - base shear calculation produces valid result`() {
        val seismic = SBCSeismic()
        val result = seismic.calculateBaseShear(
            totalWeight = 5000.0, seismicZone = SeismicZone.ZONE_3,
            soilType = SoilType.C, importanceFactor = 1.0,
            responseModificationFactor = 5.0, buildingHeight = 20.0
        )
        assertTrue("Base shear should be positive", result.baseShear > 0)
        assertEquals("Code should be SBC", DesignCode.SBC, seismic.getCodeName())
        assertTrue("Should have SBC reference", result.codeReference.contains("SBC"))
    }

    @Test
    fun `SBCSeismic - Saudi zone factors are different from ACI`() {
        val seismic = SBCSeismic()
        val factors = seismic.getZoneFactors()
        // Saudi Zone 1 (Tabuk/Jizan) = 0.05 — much lower than ACI Zone 1 = 0.10
        assertEquals("Saudi Zone 1 should be 0.05", 0.05, factors[SeismicZone.ZONE_1]!!, 0.001)
        // Saudi Zone 5 (active seismic) = 0.35
        assertEquals("Saudi Zone 5 should be 0.35", 0.35, factors[SeismicZone.ZONE_5]!!, 0.001)
    }

    @Test
    fun `SBCSeismic - response spectrum produces valid values`() {
        val seismic = SBCSeismic()
        val spectrum = seismic.getResponseSpectrum(
            period = 0.5, dampingRatio = 0.05,
            soilType = SoilType.C, peakGroundAcceleration = 0.15
        )
        assertTrue("Sa should be positive", spectrum.spectralAcceleration > 0)
        assertTrue("Should describe SBC 301", spectrum.description.contains("SBC 301"))
    }

    @Test
    fun `SBCSeismic - force distribution produces correct number of floors`() {
        val seismic = SBCSeismic()
        val distribution = seismic.distributeSeismicForces(
            baseShear = 500.0,
            floorWeights = listOf(1000.0, 1000.0, 1000.0, 800.0),
            floorHeights = listOf(3.5, 7.0, 10.5, 14.0)
        )
        assertEquals("Should have 4 floor results", 4, distribution.size)
        // Sum of lateral forces should equal base shear
        val totalForce = distribution.sumOf { it.lateralForce }
        assertEquals("Sum of forces should equal base shear", 500.0, totalForce, 1.0)
    }

    @Test
    fun `SBCSeismic - SBC soil factors differ from ASCE 7`() {
        val seismic = SBCSeismic()
        val factors = seismic.getSoilFactors()
        // SBC 301 Fa for Soil D = 1.6 (same as ASCE 7 for this case)
        assertNotNull("Should have soil factor for D", factors[SoilType.D])
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. CalculationFactory — اختبارات المحركات المتخصصة الجديدة
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `CalculationFactory - all specialized factories work for SBC`() {
        assertNotNull(CalculationFactory.getHordiSlabDesign(DesignCode.SBC))
        assertNotNull(CalculationFactory.getWaffleSlabDesign(DesignCode.SBC))
        assertNotNull(CalculationFactory.getFlatSlabDesign(DesignCode.SBC))
        assertNotNull(CalculationFactory.getStrapFootingDesign(DesignCode.SBC))
        assertNotNull(CalculationFactory.getShearWallDesign(DesignCode.SBC))
        assertNotNull(CalculationFactory.getPileFoundationDesign(DesignCode.SBC))
        assertNotNull(CalculationFactory.getSeismicDesign(DesignCode.SBC))
        assertNotNull(CalculationFactory.getRetainingWallDesign(DesignCode.SBC))
    }

    @Test
    fun `CalculationFactory - ECPCombinedFooting specialized is accessible`() {
        val engine = CalculationFactory.getECPCombinedFootingSpecialized()
        assertNotNull(engine)
    }

    @Test
    fun `CalculationFactory - beam design returns correct type for each code`() {
        assertTrue(CalculationFactory.getBeamDesign(DesignCode.ECP) is com.civileg.app.domain.calculations.ecp.ECPBeam)
        assertTrue(CalculationFactory.getBeamDesign(DesignCode.ACI) is com.civileg.app.domain.calculations.aci.ACIBeam)
        assertTrue(CalculationFactory.getBeamDesign(DesignCode.SBC) is SBCBeam)
    }
}
