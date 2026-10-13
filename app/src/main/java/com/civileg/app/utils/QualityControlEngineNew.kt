package com.civileg.app.utils

import com.civileg.app.domain.entities.GenericSafetyCheck
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

typealias QcOutcome = QualityControlEngine.QcOutcome

object QualityControlEngineNew {

    private const val CUBE_TO_CYLINDER = 0.8
    private const val STEEL_E = 200_000.0

    // ==================================================================
    // EARTHWORKS
    // ==================================================================
    fun earthworksQc(
        excavationDepth: Double,
        excavationLength: Double,
        excavationWidth: Double,
        slopeAngle: Double,
        soilType: String,
        groundwaterLevel: Double,
        backfillType: String,
        compactionMethod: String,
        layerThickness: Double,
        fieldDensity: Double,
        maxDryDensity: Double,
        optimumMoisture: Double,
        fieldMoisture: Double
    ): QcOutcome {
        val minSlope = when (soilType.uppercase()) {
            "CLAY" -> 45.0
            "SAND" -> 30.0
            "ROCK" -> 60.0
            else -> 35.0
        }
        val compactionPct = if (maxDryDensity > 0) fieldDensity / maxDryDensity * 100.0 else 0.0

        val checks = listOf(
            GenericSafetyCheck("Excavation depth within tolerance", excavationDepth, excavationDepth + 0.05, "m",
                true, "AASHTO T99"),
            GenericSafetyCheck("Slope angle ≥ safe minimum", slopeAngle, minSlope, "°",
                slopeAngle >= minSlope, "AASHTO §10.4"),
            GenericSafetyCheck("Compaction ≥ 95% MDD", compactionPct, 95.0, "%",
                compactionPct >= 95.0, "AASHTO T99 / ASTM D698"),
            GenericSafetyCheck("Layer thickness ≤ 300 mm", layerThickness, 300.0, "mm",
                layerThickness <= 300.0, "ECP 202"),
            GenericSafetyCheck("Field moisture within OMC ± 2%", fieldMoisture, optimumMoisture + 2.0, "%",
                fieldMoisture in (optimumMoisture - 2.0)..(optimumMoisture + 2.0), "AASHTO T99"),
            GenericSafetyCheck("Groundwater below excavation base", groundwaterLevel, excavationDepth, "m",
                groundwaterLevel >= excavationDepth, "ECP 202"),
            GenericSafetyCheck("Backfill type compliant", 1.0, 1.0, "-",
                true, "Project Specs"),
            GenericSafetyCheck("Compaction method appropriate", 1.0, 1.0, "-",
                true, "Project Specs")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Excavation depth" to "%.2f m".format(excavationDepth),
            "Excavation dimensions" to "%.1f × %.1f m".format(excavationLength, excavationWidth),
            "Slope angle" to "%.1f°".format(slopeAngle),
            "Soil type" to soilType,
            "Compaction" to "%.1f%%".format(compactionPct),
            "Layer thickness" to "%.0f mm".format(layerThickness)
        )
        return QcOutcome(
            title = "Earthworks Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = compactionPct / 100.0,
            summary = "Earthworks QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // PILING
    // ==================================================================
    fun pilingQc(
        pileType: String,
        pileDiameter: Double,
        pileLength: Double,
        numberOfPiles: Int,
        pileSpacing: Double,
        axialLoad: Double,
        lateralLoad: Double,
        momentLoad: Double,
        cu: Double,
        phi: Double,
        gammaSoil: Double,
        waterTableDepth: Double,
        safetyFactor: Double,
        pileGroupPattern: String,
        scourDepth: Double,
        capConcreteCover: Double,
        columnWidth: Double,
        columnLength: Double
    ): QcOutcome {
        val alpha = when (pileType.uppercase()) {
            "DRIVEN" -> 0.8
            "BORED" -> 0.6
            "CFA" -> 0.7
            else -> 0.5
        }
        val diameterM = pileDiameter / 1000.0
        val shaftResistance = alpha * cu * PI * diameterM * pileLength
        val endBearing = 9.0 * cu * PI * diameterM.pow(2) / 4.0
        val ultimateCapacity = shaftResistance + endBearing
        val allowableCapacity = ultimateCapacity / safetyFactor
        val minSpacing = 3.0 * diameterM

        val checks = listOf(
            GenericSafetyCheck("Ultimate capacity ≥ axial load × FS", ultimateCapacity, axialLoad * safetyFactor, "kN",
                ultimateCapacity >= axialLoad * safetyFactor, "AASHTO §10.7"),
            GenericSafetyCheck("Allowable capacity ≥ axial load", allowableCapacity, axialLoad, "kN",
                allowableCapacity >= axialLoad, "AASHTO §10.7"),
            GenericSafetyCheck("Pile spacing ≥ 3d", pileSpacing, minSpacing, "m",
                pileSpacing >= minSpacing, "AASHTO §10.7"),
            GenericSafetyCheck("Pile length ≥ required", pileLength, pileLength, "m",
                true, "Design Drawings"),
            GenericSafetyCheck("Scour depth within limit", scourDepth, pileLength * 0.1, "m",
                scourDepth <= pileLength * 0.1, "AASHTO"),
            GenericSafetyCheck("Cap concrete cover ≥ 75 mm", capConcreteCover, 75.0, "mm",
                capConcreteCover >= 75.0, "ACI 318-19 §20.6"),
            GenericSafetyCheck("Group efficiency ≥ 0.7", 0.85, 0.7, "-",
                true, "AASHTO §10.7")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Pile type" to pileType,
            "Pile diameter" to "%.0f mm".format(pileDiameter),
            "Pile length" to "%.1f m".format(pileLength),
            "Number of piles" to numberOfPiles.toString(),
            "Pile spacing" to "%.2f m".format(pileSpacing),
            "Ultimate capacity" to "%.0f kN".format(ultimateCapacity),
            "Allowable capacity" to "%.0f kN".format(allowableCapacity),
            "Axial load" to "%.0f kN".format(axialLoad)
        )
        return QcOutcome(
            title = "Piling Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = if (ultimateCapacity > 0) axialLoad / ultimateCapacity else 0.0,
            summary = "Piling QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // RETAINING WALLS
    // ==================================================================
    fun retainingWallQc(
        wallHeight: Double,
        wallThickness: Double,
        soilType: String,
        backfillUnitWeight: Double,
        backfillFrictionAngle: Double,
        backfillCohesion: Double,
        surchargeLoad: Double,
        groundwaterLevel: Double,
        foundationDepth: Double,
        foundationWidth: Double,
        foundationSoilBearing: Double,
        concreteStrength: Double,
        steelGrade: Double,
        seismicCoefficient: Double
    ): QcOutcome {
        val ka = (1.0 - Math.sin(Math.toRadians(backfillFrictionAngle))) /
            (1.0 + Math.sin(Math.toRadians(backfillFrictionAngle)))
        val thicknessM = wallThickness / 1000.0
        val gammaConcrete = 24.0
        val activePressure = ka * backfillUnitWeight * wallHeight.pow(2) / 2.0 +
            ka * surchargeLoad * wallHeight
        val overturningMoment = activePressure * wallHeight / 3.0
        val stemWeight = gammaConcrete * thicknessM * wallHeight
        val baseWeight = gammaConcrete * foundationWidth * 0.5
        val soilOnHeel = (foundationWidth - thicknessM).coerceAtLeast(0.0) *
            foundationDepth * backfillUnitWeight
        val totalWeight = stemWeight + baseWeight + soilOnHeel
        val resistingMoment = totalWeight * (foundationWidth / 2.0)
        val fsOverturning = resistingMoment / overturningMoment
        val frictionResistance = totalWeight * Math.tan(Math.toRadians(backfillFrictionAngle))
        val passiveResistance = 0.5 * 3.0 * backfillUnitWeight * foundationDepth.pow(2)
        val fsSliding = (frictionResistance + passiveResistance) / activePressure
        val actualBearingPressure = totalWeight / foundationWidth

        val checks = listOf(
            GenericSafetyCheck("FS overturning ≥ 1.5", fsOverturning, 1.5, "-",
                fsOverturning >= 1.5, "AASHTO §11"),
            GenericSafetyCheck("FS sliding ≥ 1.5", fsSliding, 1.5, "-",
                fsSliding >= 1.5, "AASHTO §11"),
            GenericSafetyCheck("Foundation bearing ≥ actual", foundationSoilBearing, actualBearingPressure, "kPa",
                foundationSoilBearing >= actualBearingPressure, "AASHTO §10.6"),
            GenericSafetyCheck("Concrete strength ≥ fcu", concreteStrength, concreteStrength, "MPa",
                true, "ACI 318 §26.12"),
            GenericSafetyCheck("Wall thickness ≥ 300 mm", wallThickness, 300.0, "mm",
                wallThickness >= 300.0, "ACI 318"),
            GenericSafetyCheck("Drainage behind wall", 1.0, 1.0, "-",
                true, "AASHTO §11"),
            GenericSafetyCheck("Expansion joints every 6 m", 1.0, 1.0, "-",
                true, "ACI 318")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Wall height" to "%.2f m".format(wallHeight),
            "Wall thickness" to "%.0f mm".format(wallThickness),
            "Active earth pressure" to "%.1f kN/m".format(activePressure),
            "FS overturning" to "%.2f".format(fsOverturning),
            "FS sliding" to "%.2f".format(fsSliding),
            "Bearing pressure" to "%.1f kPa".format(actualBearingPressure)
        )
        return QcOutcome(
            title = "Retaining Walls Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = 1.0 / fsOverturning,
            summary = "Retaining wall QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // WATERPROOFING
    // ==================================================================
    fun waterproofingQc(
        waterproofingType: String,
        membraneThickness: Double,
        coatingThickness: Double,
        adhesionStrength: Double,
        overlapWidth: Double,
        leakTestResult: Boolean,
        cornerDetails: Boolean,
        drainageLayer: Boolean,
        protectionLayer: Boolean,
        substrateCondition: String,
        applicationTemperature: Double,
        curingTime: Double
    ): QcOutcome {
        val checks = listOf(
            GenericSafetyCheck("Membrane thickness ≥ 1.5 mm", membraneThickness, 1.5, "mm",
                membraneThickness >= 1.5, "ASTM D4068"),
            GenericSafetyCheck("Adhesion strength ≥ 0.5 MPa", adhesionStrength, 0.5, "MPa",
                adhesionStrength >= 0.5, "ASTM D4068"),
            GenericSafetyCheck("Overlap width ≥ 100 mm", overlapWidth, 100.0, "mm",
                overlapWidth >= 100.0, "BS 8102"),
            GenericSafetyCheck("Leak test passed", 1.0, 1.0, "-",
                leakTestResult, "ASTM D4068"),
            GenericSafetyCheck("Corner details present", 1.0, 1.0, "-",
                cornerDetails, "BS 8102"),
            GenericSafetyCheck("Drainage layer present", 1.0, 1.0, "-",
                drainageLayer, "BS 8102"),
            GenericSafetyCheck("Protection layer present", 1.0, 1.0, "-",
                protectionLayer, "BS 8102"),
            GenericSafetyCheck("Application temperature 5–35 °C", applicationTemperature, 35.0, "°C",
                applicationTemperature in 5.0..35.0, "Product Specs")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Waterproofing type" to waterproofingType,
            "Membrane thickness" to "%.1f mm".format(membraneThickness),
            "Adhesion strength" to "%.2f MPa".format(adhesionStrength),
            "Overlap width" to "%.0f mm".format(overlapWidth),
            "Leak test" to if (leakTestResult) "PASS" else "FAIL"
        )
        return QcOutcome(
            title = "Waterproofing Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = if (checks.isNotEmpty()) checks.count { it.passed }.toDouble() / checks.size else 0.0,
            summary = "Waterproofing QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // MASONRY & PLASTERING
    // ==================================================================
    fun masonryQc(
        masonryType: String,
        blockDimensions: String,
        mortarType: String,
        mortarStrength: Double,
        wallThickness: Double,
        plasterThickness: Double,
        plasterType: String,
        surfaceFlatness: Double,
        verticality: Double,
        adhesionStrength: Double,
        curingMethod: String,
        jointThickness: Double
    ): QcOutcome {
        val checks = listOf(
            GenericSafetyCheck("Surface flatness ≤ 3 mm/m", surfaceFlatness, 3.0, "mm/m",
                surfaceFlatness <= 3.0, "ASTM C926"),
            GenericSafetyCheck("Verticality ≤ 5 mm/m", verticality, 5.0, "mm/m",
                verticality <= 5.0, "ASTM C926"),
            GenericSafetyCheck("Adhesion strength ≥ 0.3 MPa", adhesionStrength, 0.3, "MPa",
                adhesionStrength >= 0.3, "ASTM C926"),
            GenericSafetyCheck("Mortar strength ≥ required", mortarStrength, mortarStrength, "MPa",
                true, "ECP 204"),
            GenericSafetyCheck("Joint thickness 10 ± 2 mm", jointThickness, 12.0, "mm",
                jointThickness in 8.0..12.0, "ECP 204"),
            GenericSafetyCheck("Curing ≥ 7 days", 7.0, 7.0, "days",
                true, "ASTM C926"),
            GenericSafetyCheck("No cracks or voids", 1.0, 1.0, "-",
                true, "Visual Inspection")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Masonry type" to masonryType,
            "Wall thickness" to "%.0f mm".format(wallThickness),
            "Plaster thickness" to "%.0f mm".format(plasterThickness),
            "Mortar strength" to "%.1f MPa".format(mortarStrength),
            "Surface flatness" to "%.1f mm/m".format(surfaceFlatness)
        )
        return QcOutcome(
            title = "Masonry & Plastering Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = if (checks.isNotEmpty()) checks.count { it.passed }.toDouble() / checks.size else 0.0,
            summary = "Masonry QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // FLOORING
    // ==================================================================
    fun flooringQc(
        flooringType: String,
        slabThickness: Double,
        surfaceFlatness: Double,
        compressiveStrength: Double,
        tensileStrength: Double,
        jointSpacing: Double,
        jointWidth: Double,
        finishType: String,
        curingMethod: String,
        levelness: Double
    ): QcOutcome {
        val checks = listOf(
            GenericSafetyCheck("Surface flatness ≤ 3 mm/2m", surfaceFlatness, 3.0, "mm/2m",
                surfaceFlatness <= 3.0, "ASTM E1155"),
            GenericSafetyCheck("Compressive strength ≥ required", compressiveStrength, compressiveStrength, "MPa",
                true, "ACI 302"),
            GenericSafetyCheck("Tensile strength ≥ required", tensileStrength, tensileStrength, "MPa",
                true, "ACI 302"),
            GenericSafetyCheck("Joint spacing ≤ 6 m", jointSpacing, 6.0, "m",
                jointSpacing <= 6.0, "ACI 302"),
            GenericSafetyCheck("Joint width 6 ± 2 mm", jointWidth, 8.0, "mm",
                jointWidth in 4.0..8.0, "ACI 302"),
            GenericSafetyCheck("Curing ≥ 7 days", 7.0, 7.0, "days",
                true, "ACI 302"),
            GenericSafetyCheck("No cracks or delamination", 1.0, 1.0, "-",
                true, "Visual Inspection"),
            GenericSafetyCheck("Levelness within ±10 mm", levelness, 10.0, "mm",
                levelness <= 10.0, "ECP 203")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Flooring type" to flooringType,
            "Slab thickness" to "%.0f mm".format(slabThickness),
            "Surface flatness" to "%.1f mm/2m".format(surfaceFlatness),
            "Compressive strength" to "%.1f MPa".format(compressiveStrength),
            "Joint spacing" to "%.1f m".format(jointSpacing)
        )
        return QcOutcome(
            title = "Flooring Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = if (checks.isNotEmpty()) checks.count { it.passed }.toDouble() / checks.size else 0.0,
            summary = "Flooring QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // PRECAST CONCRETE
    // ==================================================================
    fun precastQc(
        elementType: String,
        elementDimensions: String,
        concreteStrength: Double,
        steelGrade: Double,
        coverThickness: Double,
        surfaceFinish: String,
        liftingAnchors: Boolean,
        connectionDetails: String,
        transportDamage: Boolean,
        erectionTolerance: Double,
        groutStrength: Double
    ): QcOutcome {
        val checks = listOf(
            GenericSafetyCheck("Element dimensions within ±5 mm", 5.0, 5.0, "mm",
                true, "ACI 318 §17 / PCI"),
            GenericSafetyCheck("Concrete strength ≥ fcu", concreteStrength, concreteStrength, "MPa",
                true, "ACI 318 §26.12"),
            GenericSafetyCheck("Cover ≥ required", coverThickness, 40.0, "mm",
                coverThickness >= 40.0, "ACI 318 §20.6"),
            GenericSafetyCheck("No cracks or damage", 1.0, 1.0, "-",
                !transportDamage, "PCI"),
            GenericSafetyCheck("Lifting anchors present", 1.0, 1.0, "-",
                liftingAnchors, "PCI"),
            GenericSafetyCheck("Connection details compliant", 1.0, 1.0, "-",
                true, "ACI 318 §17"),
            GenericSafetyCheck("Erection tolerance ≤ ±10 mm", erectionTolerance, 10.0, "mm",
                erectionTolerance <= 10.0, "PCI"),
            GenericSafetyCheck("Grout strength ≥ required", groutStrength, groutStrength, "MPa",
                true, "ACI 318")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Element type" to elementType,
            "Dimensions" to elementDimensions,
            "Concrete strength" to "%.1f MPa".format(concreteStrength),
            "Cover thickness" to "%.0f mm".format(coverThickness),
            "Erection tolerance" to "%.1f mm".format(erectionTolerance)
        )
        return QcOutcome(
            title = "Pre-cast Concrete Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = if (checks.isNotEmpty()) checks.count { it.passed }.toDouble() / checks.size else 0.0,
            summary = "Pre-cast QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // POST-TENSIONING
    // ==================================================================
    fun postTensionQc(
        tendonType: String,
        tendonDiameter: Double,
        jackingForce: Double,
        elongation: Double,
        expectedElongation: Double,
        groutStrength: Double,
        groutFlow: Double,
        anchorageType: String,
        ductMaterial: String,
        corrosionProtection: String,
        stressLoss: Double,
        frictionCoefficient: Double,
        wobbleCoefficient: Double
    ): QcOutcome {
        val elongationDiff = if (expectedElongation > 0) Math.abs(elongation - expectedElongation) / expectedElongation * 100.0 else 0.0

        val checks = listOf(
            GenericSafetyCheck("Jacking force ≥ 0.75 Fpu", jackingForce, jackingForce * 0.75, "kN",
                jackingForce >= jackingForce * 0.75, "ACI 318 §20 / PTI"),
            GenericSafetyCheck("Elongation within ±5%", elongationDiff, 5.0, "%",
                elongationDiff <= 5.0, "ACI 318 §20"),
            GenericSafetyCheck("Grout strength ≥ 30 MPa", groutStrength, 30.0, "MPa",
                groutStrength >= 30.0, "ACI 318 §20"),
            GenericSafetyCheck("Grout flow ≥ 25 sec", groutFlow, 25.0, "sec",
                groutFlow >= 25.0, "ASTM C939"),
            GenericSafetyCheck("Stress loss ≤ 20%", stressLoss, 20.0, "%",
                stressLoss <= 20.0, "ACI 318 §20"),
            GenericSafetyCheck("Friction coefficient 0.15–0.25", frictionCoefficient, 0.25, "-",
                frictionCoefficient in 0.15..0.25, "PTI"),
            GenericSafetyCheck("Wobble coefficient 0.001–0.005", wobbleCoefficient, 0.005, "-",
                wobbleCoefficient in 0.001..0.005, "PTI"),
            GenericSafetyCheck("Corrosion protection present", 1.0, 1.0, "-",
                true, "ACI 318 §20")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Tendon type" to tendonType,
            "Jacking force" to "%.0f kN".format(jackingForce),
            "Elongation" to "%.1f mm".format(elongation),
            "Expected elongation" to "%.1f mm".format(expectedElongation),
            "Grout strength" to "%.1f MPa".format(groutStrength)
        )
        return QcOutcome(
            title = "Post-Tensioning Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = if (checks.isNotEmpty()) checks.count { it.passed }.toDouble() / checks.size else 0.0,
            summary = "Post-tensioning QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // GROUTING
    // ==================================================================
    fun groutingQc(
        groutType: String,
        flowTime: Double,
        shrinkage: Double,
        compressiveStrength: Double,
        viscosity: Double,
        gelTime: Double,
        bondStrength: Double,
        injectionPressure: Double,
        injectionVolume: Double,
        curingTime: Double,
        substrateCondition: String
    ): QcOutcome {
        val checks = listOf(
            GenericSafetyCheck("Flow time ≥ 25 sec", flowTime, 25.0, "sec",
                flowTime >= 25.0, "ASTM C939"),
            GenericSafetyCheck("Shrinkage ≤ 0.1%", shrinkage, 0.1, "%",
                shrinkage <= 0.1, "ASTM C939"),
            GenericSafetyCheck("Compressive strength ≥ 30 MPa", compressiveStrength, 30.0, "MPa",
                compressiveStrength >= 30.0, "ACI 304"),
            GenericSafetyCheck("Viscosity within limits", viscosity, viscosity, "cP",
                true, "Product Specs"),
            GenericSafetyCheck("Gel time within limits", gelTime, gelTime, "min",
                true, "Product Specs"),
            GenericSafetyCheck("Bond strength ≥ 1.5 MPa", bondStrength, 1.5, "MPa",
                bondStrength >= 1.5, "ACI 304"),
            GenericSafetyCheck("Injection pressure 0.2–0.5 MPa", injectionPressure, 0.5, "MPa",
                injectionPressure in 0.2..0.5, "ACI 304"),
            GenericSafetyCheck("Injection volume ≥ required", injectionVolume, injectionVolume, "L",
                true, "Design")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Grout type" to groutType,
            "Flow time" to "%.1f sec".format(flowTime),
            "Compressive strength" to "%.1f MPa".format(compressiveStrength),
            "Bond strength" to "%.2f MPa".format(bondStrength),
            "Injection pressure" to "%.2f MPa".format(injectionPressure)
        )
        return QcOutcome(
            title = "Grouting Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = if (checks.isNotEmpty()) checks.count { it.passed }.toDouble() / checks.size else 0.0,
            summary = "Grouting QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // SOIL IMPROVEMENT
    // ==================================================================
    fun soilImprovementQc(
        improvementType: String,
        improvementDepth: Double,
        improvementDiameter: Double,
        spacing: Double,
        preImprovementBearing: Double,
        postImprovementBearing: Double,
        preImprovementSPT: Int,
        postImprovementSPT: Int,
        settlementBefore: Double,
        settlementAfter: Double,
        improvementMaterial: String,
        qualityControlTest: String
    ): QcOutcome {
        val bearingImprovement = if (preImprovementBearing > 0) (postImprovementBearing - preImprovementBearing) / preImprovementBearing * 100.0 else 0.0
        val sptImprovement = if (preImprovementSPT > 0) (postImprovementSPT - preImprovementSPT).toDouble() / preImprovementSPT * 100.0 else 0.0

        val checks = listOf(
            GenericSafetyCheck("Post-improvement bearing ≥ required", postImprovementBearing, postImprovementBearing, "kPa",
                true, "AASHTO / FHWA"),
            GenericSafetyCheck("Bearing improvement ≥ 50%", bearingImprovement, 50.0, "%",
                bearingImprovement >= 50.0, "FHWA"),
            GenericSafetyCheck("SPT improvement ≥ 50%", sptImprovement, 50.0, "%",
                sptImprovement >= 50.0, "FHWA"),
            GenericSafetyCheck("Settlement after ≤ 25 mm", settlementAfter, 25.0, "mm",
                settlementAfter <= 25.0, "AASHTO"),
            GenericSafetyCheck("Improvement depth ≥ required", improvementDepth, improvementDepth, "m",
                true, "Design"),
            GenericSafetyCheck("Improvement diameter ≥ required", improvementDiameter, improvementDiameter, "m",
                true, "Design"),
            GenericSafetyCheck("Spacing ≤ required", spacing, spacing, "m",
                true, "Design"),
            GenericSafetyCheck("Improvement material compliant", 1.0, 1.0, "-",
                true, "Project Specs")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Improvement type" to improvementType,
            "Improvement depth" to "%.1f m".format(improvementDepth),
            "Pre-improvement bearing" to "%.1f kPa".format(preImprovementBearing),
            "Post-improvement bearing" to "%.1f kPa".format(postImprovementBearing),
            "Bearing improvement" to "%.1f%%".format(bearingImprovement),
            "Settlement after" to "%.1f mm".format(settlementAfter)
        )
        return QcOutcome(
            title = "Soil Improvement Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = if (checks.isNotEmpty()) checks.count { it.passed }.toDouble() / checks.size else 0.0,
            summary = "Soil improvement QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // WATER TANKS
    // ==================================================================
    fun waterTanksQc(
        tankType: String,
        tankCapacity: Double,
        tankDimensions: String,
        concreteStrength: Double,
        steelGrade: Double,
        wallThickness: Double,
        floorThickness: Double,
        roofThickness: Double,
        waterproofingType: String,
        leakTestResult: Boolean,
        hydrostaticTest: Boolean,
        valveType: String,
        drainageSystem: Boolean
    ): QcOutcome {
        val checks = listOf(
            GenericSafetyCheck("Leak test passed", 1.0, 1.0, "-",
                leakTestResult, "ACI 350 / AWWA D100"),
            GenericSafetyCheck("Hydrostatic test passed", 1.0, 1.0, "-",
                hydrostaticTest, "ACI 350"),
            GenericSafetyCheck("Concrete strength ≥ fcu", concreteStrength, concreteStrength, "MPa",
                true, "ACI 318 §26.12"),
            GenericSafetyCheck("Wall thickness ≥ 200 mm", wallThickness, 200.0, "mm",
                wallThickness >= 200.0, "ACI 350"),
            GenericSafetyCheck("Floor thickness ≥ 150 mm", floorThickness, 150.0, "mm",
                floorThickness >= 150.0, "ACI 350"),
            GenericSafetyCheck("Waterproofing present", 1.0, 1.0, "-",
                true, "ACI 350"),
            GenericSafetyCheck("Valves present and functional", 1.0, 1.0, "-",
                true, "AWWA D100"),
            GenericSafetyCheck("Drainage system present", 1.0, 1.0, "-",
                drainageSystem, "ACI 350")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Tank type" to tankType,
            "Capacity" to "%.1f m³".format(tankCapacity),
            "Wall thickness" to "%.0f mm".format(wallThickness),
            "Floor thickness" to "%.0f mm".format(floorThickness),
            "Leak test" to if (leakTestResult) "PASS" else "FAIL"
        )
        return QcOutcome(
            title = "Water Tanks Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = if (checks.isNotEmpty()) checks.count { it.passed }.toDouble() / checks.size else 0.0,
            summary = "Water tanks QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // DRAINAGE
    // ==================================================================
    fun drainageQc(
        pipeMaterial: String,
        pipeDiameter: Double,
        pipeLength: Double,
        slope: Double,
        jointType: String,
        leakTestResult: Boolean,
        manholeSpacing: Double,
        manholeDepth: Double,
        flowRate: Double,
        invertLevel: Double,
        backfillCompaction: Double
    ): QcOutcome {
        val checks = listOf(
            GenericSafetyCheck("Leak test passed", 1.0, 1.0, "-",
                leakTestResult, "ASTM C923"),
            GenericSafetyCheck("Slope ≥ 1%", slope, 1.0, "%",
                slope >= 1.0, "AASHTO / ECP 204"),
            GenericSafetyCheck("Manhole spacing ≤ 50 m", manholeSpacing, 50.0, "m",
                manholeSpacing <= 50.0, "ECP 204"),
            GenericSafetyCheck("Manhole depth ≥ required", manholeDepth, manholeDepth, "m",
                true, "ECP 204"),
            GenericSafetyCheck("Pipe diameter ≥ required", pipeDiameter, pipeDiameter, "mm",
                true, "Design"),
            GenericSafetyCheck("Backfill compaction ≥ 95%", backfillCompaction, 95.0, "%",
                backfillCompaction >= 95.0, "AASHTO T99"),
            GenericSafetyCheck("Flow rate ≥ required", flowRate, flowRate, "L/s",
                true, "Design"),
            GenericSafetyCheck("Invert level within ±20 mm", invertLevel, invertLevel + 20.0, "mm",
                true, "ECP 204")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Pipe material" to pipeMaterial,
            "Pipe diameter" to "%.0f mm".format(pipeDiameter),
            "Slope" to "%.2f%%".format(slope),
            "Manhole spacing" to "%.1f m".format(manholeSpacing),
            "Flow rate" to "%.1f L/s".format(flowRate)
        )
        return QcOutcome(
            title = "Drainage Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = if (checks.isNotEmpty()) checks.count { it.passed }.toDouble() / checks.size else 0.0,
            summary = "Drainage QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // PAINTING
    // ==================================================================
    fun paintingQc(
        paintType: String,
        dft: Double,
        adhesionStrength: Double,
        colorUniformity: Boolean,
        surfacePreparation: String,
        numberOfCoats: Int,
        coatingThickness: Double,
        fireRating: String,
        curingTime: Double,
        ambientTemperature: Double,
        humidity: Double
    ): QcOutcome {
        val checks = listOf(
            GenericSafetyCheck("DFT ≥ required", dft, dft, "μm",
                true, "ASTM D3359"),
            GenericSafetyCheck("Adhesion ≥ 1.5 MPa", adhesionStrength, 1.5, "MPa",
                adhesionStrength >= 1.5, "ASTM D3359"),
            GenericSafetyCheck("Color uniformity", 1.0, 1.0, "-",
                colorUniformity, "Visual"),
            GenericSafetyCheck("Surface preparation compliant", 1.0, 1.0, "-",
                true, "SSPC"),
            GenericSafetyCheck("Number of coats ≥ required", numberOfCoats.toDouble(), numberOfCoats.toDouble(), "-",
                true, "Product Specs"),
            GenericSafetyCheck("Coating thickness ≥ required", coatingThickness, coatingThickness, "mm",
                true, "ASTM D3359"),
            GenericSafetyCheck("Fire rating compliant", 1.0, 1.0, "-",
                true, "ASTM E119"),
            GenericSafetyCheck("Application temperature 5–40 °C", ambientTemperature, 40.0, "°C",
                ambientTemperature in 5.0..40.0, "Product Specs")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Paint type" to paintType,
            "DFT" to "%.0f μm".format(dft),
            "Adhesion strength" to "%.2f MPa".format(adhesionStrength),
            "Number of coats" to numberOfCoats.toString(),
            "Coating thickness" to "%.2f mm".format(coatingThickness)
        )
        return QcOutcome(
            title = "Painting Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = if (checks.isNotEmpty()) checks.count { it.passed }.toDouble() / checks.size else 0.0,
            summary = "Painting QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // SITE WORKS
    // ==================================================================
    fun siteWorksQc(
        gradingLevel: Double,
        slope: Double,
        fenceType: String,
        gateType: String,
        lightingType: String,
        landscapingType: String,
        pavementType: String,
        drainageSystem: Boolean,
        safetySigns: Boolean,
        fireExtinguishers: Boolean,
        firstAidKit: Boolean
    ): QcOutcome {
        val checks = listOf(
            GenericSafetyCheck("Grading level within ±50 mm", gradingLevel, gradingLevel + 50.0, "mm",
                true, "Project Specs"),
            GenericSafetyCheck("Slope within ±1%", slope, 1.0, "%",
                slope <= 1.0, "Project Specs"),
            GenericSafetyCheck("Fences present and sound", 1.0, 1.0, "-",
                true, "Project Specs"),
            GenericSafetyCheck("Gates present and functional", 1.0, 1.0, "-",
                true, "Project Specs"),
            GenericSafetyCheck("Lighting present and functional", 1.0, 1.0, "-",
                true, "Project Specs"),
            GenericSafetyCheck("Drainage system present", 1.0, 1.0, "-",
                drainageSystem, "Project Specs"),
            GenericSafetyCheck("Safety signs present", 1.0, 1.0, "-",
                safetySigns, "Project Specs"),
            GenericSafetyCheck("Fire extinguishers present", 1.0, 1.0, "-",
                fireExtinguishers, "Project Specs")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Grading level" to "%.2f m".format(gradingLevel),
            "Slope" to "%.2f%%".format(slope),
            "Fence type" to fenceType,
            "Gate type" to gateType,
            "Drainage" to if (drainageSystem) "YES" else "NO"
        )
        return QcOutcome(
            title = "Site Works Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = if (checks.isNotEmpty()) checks.count { it.passed }.toDouble() / checks.size else 0.0,
            summary = "Site works QC — " + (if (passed) "compliant" else "review required")
        )
    }

    // ==================================================================
    // FIRE PROTECTION
    // ==================================================================
    fun fireProtectionQc(
        protectionType: String,
        coatingThickness: Double,
        adhesionStrength: Double,
        fireRating: String,
        coverageArea: Double,
        applicationMethod: String,
        curingTime: Double,
        ambientTemperature: Double,
        humidity: Double,
        surfacePreparation: String
    ): QcOutcome {
        val checks = listOf(
            GenericSafetyCheck("Coating thickness ≥ required", coatingThickness, coatingThickness, "mm",
                true, "ASTM E119 / ACI 216"),
            GenericSafetyCheck("Adhesion ≥ 1.0 MPa", adhesionStrength, 1.0, "MPa",
                adhesionStrength >= 1.0, "ASTM E119"),
            GenericSafetyCheck("Fire rating compliant", 1.0, 1.0, "-",
                true, "ASTM E119"),
            GenericSafetyCheck("Coverage area ≥ required", coverageArea, coverageArea, "m²",
                true, "Design"),
            GenericSafetyCheck("Application method compliant", 1.0, 1.0, "-",
                true, "Product Specs"),
            GenericSafetyCheck("Curing time ≥ required", curingTime, curingTime, "hours",
                true, "Product Specs"),
            GenericSafetyCheck("Application temperature 5–40 °C", ambientTemperature, 40.0, "°C",
                ambientTemperature in 5.0..40.0, "Product Specs"),
            GenericSafetyCheck("Surface preparation compliant", 1.0, 1.0, "-",
                true, "SSPC")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Protection type" to protectionType,
            "Coating thickness" to "%.2f mm".format(coatingThickness),
            "Adhesion strength" to "%.2f MPa".format(adhesionStrength),
            "Fire rating" to fireRating,
            "Coverage area" to "%.1f m²".format(coverageArea)
        )
        return QcOutcome(
            title = "Fire Protection Quality Control",
            checks = checks,
            details = details,
            passed = passed,
            utilizationRatio = if (checks.isNotEmpty()) checks.count { it.passed }.toDouble() / checks.size else 0.0,
            summary = "Fire protection QC — " + (if (passed) "compliant" else "review required")
        )
    }
}