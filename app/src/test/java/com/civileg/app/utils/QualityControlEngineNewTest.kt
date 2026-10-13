package com.civileg.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [QualityControlEngineNew] — the 15 additional civil
 * engineering discipline QC engines (Earthworks … Fire Protection).
 */
class QualityControlEngineNewTest {

    // ==================================================================
    // EARTHWORKS
    // ==================================================================
    @Test
    fun earthworksQc_compliantBackfill_passes() {
        val outcome = QualityControlEngineNew.earthworksQc(
            excavationDepth = 3.0, excavationLength = 20.0, excavationWidth = 10.0,
            slopeAngle = 45.0, soilType = "SAND", groundwaterLevel = 5.0,
            backfillType = "Compacted Sand", compactionMethod = "Vibratory",
            layerThickness = 250.0, fieldDensity = 20.0, maxDryDensity = 20.0,
            optimumMoisture = 12.0, fieldMoisture = 12.0
        )
        assertTrue(outcome.passed)
        assertEquals(8, outcome.checks.size)
        assertEquals(1.0, outcome.utilizationRatio, 1e-9)
    }

    @Test
    fun earthworksQc_unsafeSlope_fails() {
        val outcome = QualityControlEngineNew.earthworksQc(
            excavationDepth = 3.0, excavationLength = 20.0, excavationWidth = 10.0,
            slopeAngle = 20.0, soilType = "SAND", groundwaterLevel = 5.0,
            backfillType = "Sand", compactionMethod = "Vibratory",
            layerThickness = 250.0, fieldDensity = 20.0, maxDryDensity = 20.0,
            optimumMoisture = 12.0, fieldMoisture = 12.0
        )
        assertFalse(outcome.passed)
        assertFalse(outcome.checks[1].passed)
    }

    // ==================================================================
    // PILING
    // ==================================================================
    @Test
    fun pilingQc_adequateCapacity_passes() {
        val outcome = QualityControlEngineNew.pilingQc(
            pileType = "BORED", pileDiameter = 600.0, pileLength = 15.0, numberOfPiles = 9,
            pileSpacing = 1.8, axialLoad = 400.0, lateralLoad = 80.0, momentLoad = 150.0,
            cu = 75.0, phi = 30.0, gammaSoil = 18.0, waterTableDepth = 3.0,
            safetyFactor = 2.5, pileGroupPattern = "3x3", scourDepth = 0.5,
            capConcreteCover = 75.0, columnWidth = 600.0, columnLength = 600.0
        )
        assertTrue(outcome.passed)
        assertEquals(7, outcome.checks.size)
    }

    @Test
    fun pilingQc_overloadedPile_fails() {
        val outcome = QualityControlEngineNew.pilingQc(
            pileType = "BORED", pileDiameter = 600.0, pileLength = 15.0, numberOfPiles = 9,
            pileSpacing = 1.8, axialLoad = 1000.0, lateralLoad = 80.0, momentLoad = 150.0,
            cu = 75.0, phi = 30.0, gammaSoil = 18.0, waterTableDepth = 3.0,
            safetyFactor = 2.5, pileGroupPattern = "3x3", scourDepth = 0.5,
            capConcreteCover = 75.0, columnWidth = 600.0, columnLength = 600.0
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // RETAINING WALLS
    // ==================================================================
    @Test
    fun retainingWallQc_stableWall_passes() {
        val outcome = QualityControlEngineNew.retainingWallQc(
            wallHeight = 5.0, wallThickness = 300.0, soilType = "SAND",
            backfillUnitWeight = 18.0, backfillFrictionAngle = 30.0, backfillCohesion = 0.0,
            surchargeLoad = 10.0, groundwaterLevel = 3.0, foundationDepth = 1.5,
            foundationWidth = 4.0, foundationSoilBearing = 200.0,
            concreteStrength = 30.0, steelGrade = 400.0, seismicCoefficient = 0.1
        )
        assertTrue(outcome.passed)
        assertEquals(7, outcome.checks.size)
    }

    @Test
    fun retainingWallQc_thinWallOverturns_fails() {
        val outcome = QualityControlEngineNew.retainingWallQc(
            wallHeight = 5.0, wallThickness = 100.0, soilType = "SAND",
            backfillUnitWeight = 18.0, backfillFrictionAngle = 30.0, backfillCohesion = 0.0,
            surchargeLoad = 10.0, groundwaterLevel = 3.0, foundationDepth = 1.5,
            foundationWidth = 1.5, foundationSoilBearing = 200.0,
            concreteStrength = 30.0, steelGrade = 400.0, seismicCoefficient = 0.1
        )
        assertFalse(outcome.passed)
        assertFalse(outcome.checks[0].passed)
    }

    // ==================================================================
    // WATERPROOFING
    // ==================================================================
    @Test
    fun waterproofingQc_compliantSystem_passes() {
        val outcome = QualityControlEngineNew.waterproofingQc(
            waterproofingType = "Bituminous Membrane", membraneThickness = 2.0,
            coatingThickness = 1.5, adhesionStrength = 0.6, overlapWidth = 100.0,
            leakTestResult = true, cornerDetails = true, drainageLayer = true,
            protectionLayer = true, substrateCondition = "Dry",
            applicationTemperature = 25.0, curingTime = 24.0
        )
        assertTrue(outcome.passed)
        assertEquals(8, outcome.checks.size)
    }

    @Test
    fun waterproofingQc_leakTestFailure_fails() {
        val outcome = QualityControlEngineNew.waterproofingQc(
            waterproofingType = "Membrane", membraneThickness = 2.0,
            coatingThickness = 1.5, adhesionStrength = 0.6, overlapWidth = 100.0,
            leakTestResult = false, cornerDetails = true, drainageLayer = true,
            protectionLayer = true, substrateCondition = "Dry",
            applicationTemperature = 25.0, curingTime = 24.0
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // MASONRY
    // ==================================================================
    @Test
    fun masonryQc_withinTolerances_passes() {
        val outcome = QualityControlEngineNew.masonryQc(
            masonryType = "Block", blockDimensions = "200x200x400", mortarType = "M10",
            mortarStrength = 10.0, wallThickness = 200.0, plasterThickness = 15.0,
            plasterType = "Cement", surfaceFlatness = 2.0, verticality = 3.0,
            adhesionStrength = 0.4, curingMethod = "Wet", jointThickness = 10.0
        )
        assertTrue(outcome.passed)
        assertEquals(7, outcome.checks.size)
    }

    @Test
    fun masonryQc_excessiveFlatnessDeviation_fails() {
        val outcome = QualityControlEngineNew.masonryQc(
            masonryType = "Block", blockDimensions = "200x200x400", mortarType = "M10",
            mortarStrength = 10.0, wallThickness = 200.0, plasterThickness = 15.0,
            plasterType = "Cement", surfaceFlatness = 5.0, verticality = 3.0,
            adhesionStrength = 0.4, curingMethod = "Wet", jointThickness = 10.0
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // FLOORING
    // ==================================================================
    @Test
    fun flooringQc_withinTolerances_passes() {
        val outcome = QualityControlEngineNew.flooringQc(
            flooringType = "Granite", slabThickness = 150.0, surfaceFlatness = 2.0,
            compressiveStrength = 30.0, tensileStrength = 3.0, jointSpacing = 5.0,
            jointWidth = 6.0, finishType = "Polished", curingMethod = "Wet", levelness = 5.0
        )
        assertTrue(outcome.passed)
        assertEquals(8, outcome.checks.size)
    }

    @Test
    fun flooringQc_poorLevelness_fails() {
        val outcome = QualityControlEngineNew.flooringQc(
            flooringType = "Granite", slabThickness = 150.0, surfaceFlatness = 2.0,
            compressiveStrength = 30.0, tensileStrength = 3.0, jointSpacing = 5.0,
            jointWidth = 6.0, finishType = "Polished", curingMethod = "Wet", levelness = 15.0
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // PRECAST
    // ==================================================================
    @Test
    fun precastQc_soundElement_passes() {
        val outcome = QualityControlEngineNew.precastQc(
            elementType = "Beam", elementDimensions = "300x600x6000", concreteStrength = 40.0,
            steelGrade = 400.0, coverThickness = 40.0, surfaceFinish = "Fair",
            liftingAnchors = true, connectionDetails = "Bolted", transportDamage = false,
            erectionTolerance = 5.0, groutStrength = 50.0
        )
        assertTrue(outcome.passed)
        assertEquals(8, outcome.checks.size)
    }

    @Test
    fun precastQc_transportDamage_fails() {
        val outcome = QualityControlEngineNew.precastQc(
            elementType = "Beam", elementDimensions = "300x600x6000", concreteStrength = 40.0,
            steelGrade = 400.0, coverThickness = 40.0, surfaceFinish = "Fair",
            liftingAnchors = true, connectionDetails = "Bolted", transportDamage = true,
            erectionTolerance = 5.0, groutStrength = 50.0
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // POST-TENSION
    // ==================================================================
    @Test
    fun postTensionQc_withinElongationTolerance_passes() {
        val outcome = QualityControlEngineNew.postTensionQc(
            tendonType = "7-wire strand", tendonDiameter = 15.2, jackingForce = 1500.0,
            elongation = 180.0, expectedElongation = 180.0, groutStrength = 35.0,
            groutFlow = 30.0, anchorageType = "Wedge", ductMaterial = "HDPE",
            corrosionProtection = "Galvanized", stressLoss = 15.0,
            frictionCoefficient = 0.20, wobbleCoefficient = 0.002
        )
        assertTrue(outcome.passed)
        assertEquals(8, outcome.checks.size)
    }

    @Test
    fun postTensionQc_excessElongationDeviation_fails() {
        val outcome = QualityControlEngineNew.postTensionQc(
            tendonType = "7-wire strand", tendonDiameter = 15.2, jackingForce = 1500.0,
            elongation = 210.0, expectedElongation = 180.0, groutStrength = 35.0,
            groutFlow = 30.0, anchorageType = "Wedge", ductMaterial = "HDPE",
            corrosionProtection = "Galvanized", stressLoss = 15.0,
            frictionCoefficient = 0.20, wobbleCoefficient = 0.002
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // GROUTING
    // ==================================================================
    @Test
    fun groutingQc_compliantGrout_passes() {
        val outcome = QualityControlEngineNew.groutingQc(
            groutType = "Cementitious", flowTime = 30.0, shrinkage = 0.05,
            compressiveStrength = 40.0, viscosity = 500.0, gelTime = 10.0,
            bondStrength = 2.0, injectionPressure = 0.35, injectionVolume = 100.0,
            curingTime = 7.0, substrateCondition = "Clean"
        )
        assertTrue(outcome.passed)
        assertEquals(8, outcome.checks.size)
    }

    @Test
    fun groutingQc_lowFlowTime_fails() {
        val outcome = QualityControlEngineNew.groutingQc(
            groutType = "Cementitious", flowTime = 10.0, shrinkage = 0.05,
            compressiveStrength = 40.0, viscosity = 500.0, gelTime = 10.0,
            bondStrength = 2.0, injectionPressure = 0.35, injectionVolume = 100.0,
            curingTime = 7.0, substrateCondition = "Clean"
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // SOIL IMPROVEMENT
    // ==================================================================
    @Test
    fun soilImprovementQc_successfulTreatment_passes() {
        val outcome = QualityControlEngineNew.soilImprovementQc(
            improvementType = "Stone Columns", improvementDepth = 8.0,
            improvementDiameter = 0.8, spacing = 2.0, preImprovementBearing = 80.0,
            postImprovementBearing = 160.0, preImprovementSPT = 8, postImprovementSPT = 18,
            settlementBefore = 50.0, settlementAfter = 20.0,
            improvementMaterial = "Crushed Stone", qualityControlTest = "Plate Load"
        )
        assertTrue(outcome.passed)
        assertEquals(8, outcome.checks.size)
    }

    @Test
    fun soilImprovementQc_excessSettlement_fails() {
        val outcome = QualityControlEngineNew.soilImprovementQc(
            improvementType = "Stone Columns", improvementDepth = 8.0,
            improvementDiameter = 0.8, spacing = 2.0, preImprovementBearing = 80.0,
            postImprovementBearing = 160.0, preImprovementSPT = 8, postImprovementSPT = 18,
            settlementBefore = 50.0, settlementAfter = 40.0,
            improvementMaterial = "Crushed Stone", qualityControlTest = "Plate Load"
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // WATER TANKS
    // ==================================================================
    @Test
    fun waterTanksQc_tightTank_passes() {
        val outcome = QualityControlEngineNew.waterTanksQc(
            tankType = "Rectangular", tankCapacity = 500.0, tankDimensions = "20x10x3",
            concreteStrength = 30.0, steelGrade = 400.0, wallThickness = 250.0,
            floorThickness = 200.0, roofThickness = 150.0, waterproofingType = "Crystalline",
            leakTestResult = true, hydrostaticTest = true, valveType = "Gate",
            drainageSystem = true
        )
        assertTrue(outcome.passed)
        assertEquals(8, outcome.checks.size)
    }

    @Test
    fun waterTanksQc_hydrostaticTestFailure_fails() {
        val outcome = QualityControlEngineNew.waterTanksQc(
            tankType = "Rectangular", tankCapacity = 500.0, tankDimensions = "20x10x3",
            concreteStrength = 30.0, steelGrade = 400.0, wallThickness = 250.0,
            floorThickness = 200.0, roofThickness = 150.0, waterproofingType = "Crystalline",
            leakTestResult = true, hydrostaticTest = false, valveType = "Gate",
            drainageSystem = true
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // DRAINAGE
    // ==================================================================
    @Test
    fun drainageQc_adequateSlope_passes() {
        val outcome = QualityControlEngineNew.drainageQc(
            pipeMaterial = "RCP", pipeDiameter = 600.0, pipeLength = 100.0, slope = 1.5,
            jointType = "Rubber Gasket", leakTestResult = true, manholeSpacing = 40.0,
            manholeDepth = 3.0, flowRate = 150.0, invertLevel = 0.0, backfillCompaction = 96.0
        )
        assertTrue(outcome.passed)
        assertEquals(8, outcome.checks.size)
    }

    @Test
    fun drainageQc_insufficientSlope_fails() {
        val outcome = QualityControlEngineNew.drainageQc(
            pipeMaterial = "RCP", pipeDiameter = 600.0, pipeLength = 100.0, slope = 0.5,
            jointType = "Rubber Gasket", leakTestResult = true, manholeSpacing = 40.0,
            manholeDepth = 3.0, flowRate = 150.0, invertLevel = 0.0, backfillCompaction = 96.0
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // PAINTING
    // ==================================================================
    @Test
    fun paintingQc_compliantCoating_passes() {
        val outcome = QualityControlEngineNew.paintingQc(
            paintType = "Epoxy", dft = 150.0, adhesionStrength = 2.0, colorUniformity = true,
            surfacePreparation = "SSPC-SP6", numberOfCoats = 2, coatingThickness = 0.15,
            fireRating = "None", curingTime = 24.0, ambientTemperature = 25.0, humidity = 60.0
        )
        assertTrue(outcome.passed)
        assertEquals(8, outcome.checks.size)
    }

    @Test
    fun paintingQc_lowAdhesion_fails() {
        val outcome = QualityControlEngineNew.paintingQc(
            paintType = "Epoxy", dft = 150.0, adhesionStrength = 1.0, colorUniformity = true,
            surfacePreparation = "SSPC-SP6", numberOfCoats = 2, coatingThickness = 0.15,
            fireRating = "None", curingTime = 24.0, ambientTemperature = 25.0, humidity = 60.0
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // SITE WORKS
    // ==================================================================
    @Test
    fun siteWorksQc_completeInstallations_passes() {
        val outcome = QualityControlEngineNew.siteWorksQc(
            gradingLevel = 100.0, slope = 0.5, fenceType = "Chain Link", gateType = "Swing",
            lightingType = "LED", landscapingType = "Grass", pavementType = "Interlock",
            drainageSystem = true, safetySigns = true, fireExtinguishers = true, firstAidKit = true
        )
        assertTrue(outcome.passed)
        assertEquals(8, outcome.checks.size)
    }

    @Test
    fun siteWorksQc_missingSafetySigns_fails() {
        val outcome = QualityControlEngineNew.siteWorksQc(
            gradingLevel = 100.0, slope = 0.5, fenceType = "Chain Link", gateType = "Swing",
            lightingType = "LED", landscapingType = "Grass", pavementType = "Interlock",
            drainageSystem = true, safetySigns = false, fireExtinguishers = true, firstAidKit = true
        )
        assertFalse(outcome.passed)
    }

    // ==================================================================
    // FIRE PROTECTION
    // ==================================================================
    @Test
    fun fireProtectionQc_compliantSystem_passes() {
        val outcome = QualityControlEngineNew.fireProtectionQc(
            protectionType = "Intumescent", coatingThickness = 0.8, adhesionStrength = 1.2,
            fireRating = "2 hr", coverageArea = 500.0, applicationMethod = "Spray",
            curingTime = 24.0, ambientTemperature = 25.0, humidity = 60.0,
            surfacePreparation = "SSPC-SP6"
        )
        assertTrue(outcome.passed)
        assertEquals(8, outcome.checks.size)
    }

    @Test
    fun fireProtectionQc_extremeTemperature_fails() {
        val outcome = QualityControlEngineNew.fireProtectionQc(
            protectionType = "Intumescent", coatingThickness = 0.8, adhesionStrength = 1.2,
            fireRating = "2 hr", coverageArea = 500.0, applicationMethod = "Spray",
            curingTime = 24.0, ambientTemperature = 50.0, humidity = 60.0,
            surfacePreparation = "SSPC-SP6"
        )
        assertFalse(outcome.passed)
    }
}
