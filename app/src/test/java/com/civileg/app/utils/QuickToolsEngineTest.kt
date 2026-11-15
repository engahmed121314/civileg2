package com.civileg.app.utils

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class QuickToolsEngineTest {

    @Test
    fun testRebarWeightCalculation_diameter16() {
        val result = QuickToolsEngine.calculateRebarWeight(
            diameterMm = 16.0,
            lengthPerBarM = 12.0,
            numberOfBars = 10
        )
        // Theoretical weight of 16mm rebar ~ 1.578 kg/m
        assertEquals(16.0, result.diameterMm, 0.01)
        assertEquals(120.0, result.totalLengthM, 0.01)
        assertTrue(abs(result.weightPerMeterKg - 1.578) < 0.05)
        assertTrue(result.totalWeightKg > 185.0 && result.totalWeightKg < 195.0)
        assertEquals(10, result.numberOfBars)
        // 12m bar of 16mm is ~ 18.9 kg, so ~52 bars per ton
        assertTrue(result.bars12mPerTon in 50..55)
    }

    @Test
    fun testRebarSubstitution_equivalentArea() {
        // 6 bars of 16mm = 6 * 201.06 = 1206.37 mm2
        // If substituting with 12mm bars (area = 113.1 mm2), needed count = ceil(1206.37 / 113.1) = 11 bars
        val sub = QuickToolsEngine.calculateRebarSubstitution(
            origCount = 6,
            origDiameterMm = 16.0,
            newDiameterMm = 12.0
        )
        assertEquals(11, sub.requiredCount)
        assertTrue(sub.isSafe)
        assertTrue(sub.providedAreaMm2 >= sub.origAreaMm2)
    }

    @Test
    fun testDevelopmentLengthAndSplice() {
        val ecpRes = QuickToolsEngine.calculateDevelopmentAndSplice(
            code = QuickToolsEngine.CodeStandard.ECP_203,
            diameterMm = 16.0,
            fcOrFcuMpa = 25.0,
            fyMpa = 360.0,
            stressType = QuickToolsEngine.StressType.TENSION,
            barPosition = QuickToolsEngine.BarPosition.BOTTOM
        )
        // Basic Ld for 16mm deformed bar should be at least 40 * 16mm = 640mm = 64cm
        assertTrue(ecpRes.basicLdCm >= 64.0)
        assertTrue(ecpRes.tensionSpliceClassBCm >= ecpRes.tensionSpliceClassACm)
        assertTrue(ecpRes.standard90HookMm > 0)
    }

    @Test
    fun testConcreteBatchEstimator() {
        val batch = QuickToolsEngine.calculateConcreteBatch(
            volumeM3 = 10.0,
            cementBagsPerM3 = 7.0 // 350 kg/m3
        )
        assertEquals(10.0, batch.volumeM3, 0.01)
        assertEquals(70, batch.totalCementBags50Kg)
        assertEquals(3500.0, batch.totalCementKg, 0.1)
        assertEquals(3.5, batch.totalCementTon, 0.01)
        assertTrue(batch.sandVolumeM3 > 4.0)
        assertTrue(batch.gravelVolumeM3 > 8.0)
        assertTrue(batch.waterLiters > 1500.0)
    }

    @Test
    fun testPreDimensioning_beamAndSlab() {
        val beam = QuickToolsEngine.calculatePreDimensioning(
            memberType = QuickToolsEngine.StructuralMemberType.BEAM_SIMPLE,
            spanM = 6.0
        )
        // L/10 = 60cm
        assertEquals(60.0, beam.recommendedDepthCm, 1.0)
        assertEquals(65.0, beam.recommendedTotalThicknessCm, 1.0)

        val slab = QuickToolsEngine.calculatePreDimensioning(
            memberType = QuickToolsEngine.StructuralMemberType.SOLID_SLAB_ONE_WAY_SIMPLE,
            spanM = 4.0
        )
        // 400cm / 30 = 13.33 -> 14cm
        assertTrue(slab.recommendedTotalThicknessCm >= 13.0)
    }

    @Test
    fun testSlopeAndLevelDifference() {
        val slope = QuickToolsEngine.calculateSlope(
            level1M = 10.0,
            level2M = 10.5,
            horizontalDistanceM = 25.0
        )
        assertEquals(0.5, slope.elevationDifferenceM, 0.001)
        assertEquals(50.0, slope.elevationDifferenceCm, 0.1)
        assertEquals(2.0, slope.slopePercentage, 0.01) // 0.5 / 25 * 100 = 2%
        assertEquals(50.0, slope.slopeRatio1ToX, 0.1) // 1 : 50
        assertEquals(QuickToolsEngine.SlopeDirection.ASCENDING, slope.direction)
    }

    @Test
    fun testMasonryEstimator() {
        val masonry = QuickToolsEngine.calculateMasonry(
            wallAreaM2 = 20.0,
            brickType = QuickToolsEngine.BrickType.RED_BRICK_25_12_6,
            wallThickness = QuickToolsEngine.WallThickness.HALF_BRICK_12CM
        )
        // ~58 bricks per m2 for 12cm wall -> ~1160 bricks
        assertTrue(masonry.numberOfBricks in 1100..1200)
        assertTrue(masonry.mortarVolumeM3 > 0.4)
        assertTrue(masonry.cementBags50Kg >= 3)
    }

    @Test
    fun testFormworkStrippingTime() {
        val vertical = QuickToolsEngine.calculateFormworkStrippingTime(
            elementType = QuickToolsEngine.FormworkElementType.VERTICAL_SIDES
        )
        assertEquals(2, vertical.minimumDaysOrdinaryCement)

        val slab = QuickToolsEngine.calculateFormworkStrippingTime(
            elementType = QuickToolsEngine.FormworkElementType.SLAB_BEAM_SOFFIT,
            spanM = 5.0
        )
        // 2*5 + 2 = 12 days
        assertEquals(12, slab.minimumDaysOrdinaryCement)

        val cantilever = QuickToolsEngine.calculateFormworkStrippingTime(
            elementType = QuickToolsEngine.FormworkElementType.CANTILEVER,
            spanM = 2.0
        )
        // 4*2 + 2 = 10, but minimum is 14 days
        assertEquals(14, cantilever.minimumDaysOrdinaryCement)
    }

    @Test
    fun testUnitConverter_lengthAndStress() {
        // 1 m = 3.28084 ft
        val ft = QuickToolsEngine.convertUnit(
            QuickToolsEngine.QuantityType.LENGTH, 1.0, "m", "ft"
        )
        assertEquals(3.28084, ft, 0.001)

        // 1 psi = 0.00689476 MPa
        val mpa = QuickToolsEngine.convertUnit(
            QuickToolsEngine.QuantityType.STRESS, 145.0, "psi", "MPa"
        )
        assertEquals(1.0, mpa, 0.005)

        // Round trip: 10 ton -> kg -> ton
        val kg = QuickToolsEngine.convertUnit(QuickToolsEngine.QuantityType.MASS, 10.0, "ton", "kg")
        assertEquals(10000.0, kg, 0.01)
        val back = QuickToolsEngine.convertUnit(QuickToolsEngine.QuantityType.MASS, kg, "kg", "ton")
        assertEquals(10.0, back, 0.001)

        assertTrue(QuickToolsEngine.unitsFor(QuickToolsEngine.QuantityType.LENGTH).contains("mm"))
    }

    @Test
    fun testEarthworkVolume_averageEndAreaAndPrismoidal() {
        val r = QuickToolsEngine.calculateEarthworkVolume(
            endArea1M2 = 12.0,
            endArea2M2 = 16.0,
            stationDistanceM = 50.0
        )
        // Average end area: (12+16)/2 * 50 = 700
        assertEquals(700.0, r.averageEndAreaVolumeM3, 0.01)
        // Prismoidal without mid area = same as average end area
        assertEquals(r.averageEndAreaVolumeM3, r.prismoidalVolumeM3, 0.01)

        // With mid area 20 m2: prismoidal = 50/6*(12 + 4*20 + 16) = 50/6*108 = 900
        val r2 = QuickToolsEngine.calculateEarthworkVolume(12.0, 16.0, 50.0, 20.0)
        assertEquals(900.0, r2.prismoidalVolumeM3, 0.01)
        assertTrue(r2.prismoidalCorrectionPct > 0.0)
        assertEquals(900.0, r2.recommendedVolumeM3, 0.01)
    }

    @Test
    fun testPlasterAndPaint() {
        val r = QuickToolsEngine.calculatePlasterAndPaint(
            wallAreaM2 = 100.0,
            openingsAreaM2 = 15.0,
            plasterThicknessCm = 2.0,
            paintCoats = 2,
            paintCoverageM2PerL = 10.0
        )
        assertEquals(85.0, r.netAreaM2, 0.01)
        // Mortar = 85 * 0.02 = 1.7 m3
        assertEquals(1.7, r.plasterMortarVolumeM3, 0.01)
        // Cement bags = ceil(1.7*7) = 12
        assertEquals(12, r.plasterCementBags50Kg)
        // Paint = 85*2/10 * 1.1 = 18.7 L
        assertEquals(18.7, r.paintRequiredLiters, 0.15)
        // Openings cannot exceed 90% of wall
        val clamped = QuickToolsEngine.calculatePlasterAndPaint(wallAreaM2 = 10.0, openingsAreaM2 = 9.9)
        assertTrue(clamped.netAreaM2 >= 1.0)
    }
}
