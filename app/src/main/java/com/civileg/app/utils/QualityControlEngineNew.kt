package com.civileg.app.utils

import com.civileg.app.domain.entities.DesignCode
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
            GenericSafetyCheck("Excavation depth > 0", excavationDepth, 0.0, "m",
                excavationDepth > 0.0, "AASHTO T99"),
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
            GenericSafetyCheck("Backfill type provided", 1.0, 1.0, "-",
                backfillType.isNotBlank() && !backfillType.equals("none", true), "Project Specs"),
            GenericSafetyCheck("Compaction method provided", 1.0, 1.0, "-",
                compactionMethod.isNotBlank(), "Project Specs")
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
        val undrainedCapacity = shaftResistance + endBearing

        // Drained (beta-method) capacity: uses phi, gamma and the groundwater table.
        val waterDepth = waterTableDepth.coerceIn(0.0, pileLength)
        val gammaEff = (gammaSoil - 9.81).coerceAtLeast(0.0)
        val sigmaVToe = gammaSoil * waterDepth + gammaEff * (pileLength - waterDepth)
        val sigmaVAvg = if (pileLength > 0.0) {
            (0.5 * gammaSoil * waterDepth * waterDepth +
                gammaSoil * waterDepth * (pileLength - waterDepth) +
                0.5 * gammaEff * (pileLength - waterDepth).pow(2)) / pileLength
        } else 0.0
        val delta = Math.toRadians(phi.coerceIn(0.0, 45.0) * 0.75)
        val shaftDrained = Math.tan(delta) * sigmaVAvg * PI * diameterM * pileLength
        val nq = Math.exp(PI * Math.tan(Math.toRadians(phi.coerceIn(0.0, 45.0)))) *
            Math.tan(Math.toRadians(45.0 + phi.coerceIn(0.0, 45.0) / 2.0)).pow(2)
        val endDrained = nq * sigmaVToe * PI * diameterM.pow(2) / 4.0
        val drainedCapacity = shaftDrained + endDrained
        val ultimateCapacity = maxOf(undrainedCapacity, drainedCapacity)
        val allowableCapacity = ultimateCapacity / safetyFactor

        val minSpacing = 3.0 * diameterM
        val safePileCount = maxOf(numberOfPiles, 1)
        val safeSpacing = if (pileSpacing > 0.0) pileSpacing else 1.0
        val groupEfficiency = (1.0 - (safePileCount - 1) * diameterM / (4.0 * safeSpacing * safePileCount))
            .coerceIn(0.5, 1.0)
        val embedmentBelowScour = pileLength - scourDepth

        // Lateral capacity - Broms (short pile, cohesive soil).
        val lateralCapacity = maxOf(0.0, 9.0 * cu * diameterM * (pileLength - 1.5 * diameterM))
        // Simplified P-M interaction bound for the pile section.
        val momentCapacity = 0.25 * ultimateCapacity * diameterM

        // Pile group pattern must describe the same number of piles entered.
        val patternParts = pileGroupPattern.lowercase()
            .split(Regex("[xX*]"))
            .mapNotNull { it.trim().toIntOrNull() }
        val patternCount = if (patternParts.size == 2) patternParts[0] * patternParts[1] else 0
        val patternMatches = patternCount == numberOfPiles
        val groupRows = patternParts.getOrNull(0) ?: 1
        val groupCols = patternParts.getOrNull(1) ?: 1
        val footprintWidth = (maxOf(groupCols - 1, 0)) * safeSpacing + diameterM
        val footprintLength = (maxOf(groupRows - 1, 0)) * safeSpacing + diameterM
        val columnWidthM = columnWidth / 1000.0
        val columnLengthM = columnLength / 1000.0
        val columnFits = columnWidthM <= footprintWidth && columnLengthM <= footprintLength

        val checks = listOf(
            GenericSafetyCheck("Ultimate capacity ≥ axial load x FS", ultimateCapacity, axialLoad * safetyFactor, "kN",
                ultimateCapacity >= axialLoad * safetyFactor, "AASHTO 10.7"),
            GenericSafetyCheck("Allowable capacity ≥ axial load", allowableCapacity, axialLoad, "kN",
                allowableCapacity >= axialLoad, "AASHTO 10.7"),
            GenericSafetyCheck("Pile spacing ≥ 3d", pileSpacing, minSpacing, "m",
                pileSpacing >= minSpacing, "AASHTO 10.7"),
            GenericSafetyCheck("Embedment below scour ≥ 5d", embedmentBelowScour, 5.0 * diameterM, "m",
                embedmentBelowScour >= 5.0 * diameterM, "Design Drawings"),
            GenericSafetyCheck("Scour depth within limit", scourDepth, pileLength * 0.1, "m",
                scourDepth <= pileLength * 0.1, "AASHTO"),
            GenericSafetyCheck("Cap concrete cover ≥ 75 mm", capConcreteCover, 75.0, "mm",
                capConcreteCover >= 75.0, "ACI 318-19 20.6"),
            GenericSafetyCheck("Group efficiency ≥ 0.7", groupEfficiency, 0.7, "-",
                groupEfficiency >= 0.7, "AASHTO 10.7"),
            GenericSafetyCheck("Lateral capacity ≥ lateral load", lateralCapacity, lateralLoad, "kN",
                lateralCapacity >= lateralLoad, "Broms method"),
            GenericSafetyCheck("Moment load ≤ 0.25 P_u d", momentLoad, momentCapacity, "kN·m",
                momentLoad <= momentCapacity, "P-M interaction"),
            GenericSafetyCheck("Group pattern matches pile count", patternCount.toDouble(),
                numberOfPiles.toDouble(), "piles", patternMatches, "AASHTO 10.7"),
            GenericSafetyCheck("Column fits within pile group", maxOf(columnWidthM, columnLengthM),
                minOf(footprintWidth, footprintLength), "m", columnFits, "Design Drawings")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Pile type" to pileType,
            "Pile diameter" to "%.0f mm".format(pileDiameter),
            "Pile length" to "%.1f m".format(pileLength),
            "Number of piles" to numberOfPiles.toString(),
            "Pile spacing" to "%.2f m".format(pileSpacing),
            "Undrained capacity" to "%.0f kN".format(undrainedCapacity),
            "Drained capacity" to "%.0f kN".format(drainedCapacity),
            "Governing ultimate capacity" to "%.0f kN".format(ultimateCapacity),
            "Allowable capacity" to "%.0f kN".format(allowableCapacity),
            "Axial load" to "%.0f kN".format(axialLoad),
            "Lateral capacity" to "%.0f kN".format(lateralCapacity),
            "Group pattern" to pileGroupPattern
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
        seismicCoefficient: Double,
        designCode: DesignCode = DesignCode.ECP
    ): QcOutcome {
        val minConcreteStrength = if (designCode == DesignCode.ACI) 25.0 else 30.0
        val ka = (1.0 - Math.sin(Math.toRadians(backfillFrictionAngle))) /
            (1.0 + Math.sin(Math.toRadians(backfillFrictionAngle)))
        val thicknessM = wallThickness / 1000.0
        val gammaConcrete = 24.0
        // Cohesion reduces the active thrust (Bell's equation): pa = ka*gamma*z - 2c*sqrt(ka)
        val cohesionTerm = 2.0 * backfillCohesion * Math.sqrt(ka) * wallHeight
        val activePressure = (ka * backfillUnitWeight * wallHeight.pow(2) / 2.0 +
            ka * surchargeLoad * wallHeight - cohesionTerm).coerceAtLeast(0.0)
        // Mononobe-Okabe simplified seismic increment.
        val seismicIncrement = 0.5 * seismicCoefficient.coerceAtLeast(0.0) *
            backfillUnitWeight * wallHeight.pow(2)
        val seismicPressure = activePressure + seismicIncrement
        val seismicOverturningMoment = seismicIncrement * wallHeight * 0.6
        val overturningMoment = activePressure * wallHeight / 3.0 + seismicOverturningMoment
        val stemWeight = gammaConcrete * thicknessM * wallHeight
        val baseWeight = gammaConcrete * foundationWidth * 0.5
        val soilOnHeel = (foundationWidth - thicknessM).coerceAtLeast(0.0) *
            foundationDepth * backfillUnitWeight
        val totalWeight = stemWeight + baseWeight + soilOnHeel
        val resistingMoment = totalWeight * (foundationWidth / 2.0)
        val fsOverturning = resistingMoment / overturningMoment
        val frictionResistance = totalWeight * Math.tan(Math.toRadians(backfillFrictionAngle))
        val passiveResistance = 0.5 * 3.0 * backfillUnitWeight * foundationDepth.pow(2)
        val slidingResistance = frictionResistance + passiveResistance
        val fsSliding = slidingResistance / activePressure
        val fsSlidingSeismic = slidingResistance / seismicPressure
        val actualBearingPressure = totalWeight / foundationWidth

        // Backfill classification must be consistent with the strength parameters.
        val soilClassOk = when (soilType.uppercase()) {
            "SAND", "GRAVEL" -> backfillFrictionAngle >= 28.0
            "CLAY", "SILT" -> backfillCohesion > 0.0
            "ROCK" -> true
            else -> false
        }

        val checks = listOf(
            GenericSafetyCheck("FS overturning ≥ 1.5", fsOverturning, 1.5, "-",
                fsOverturning >= 1.5, "AASHTO 11"),
            GenericSafetyCheck("FS sliding ≥ 1.5", fsSliding, 1.5, "-",
                fsSliding >= 1.5, "AASHTO 11"),
            GenericSafetyCheck("Foundation bearing ≥ actual", foundationSoilBearing, actualBearingPressure, "kPa",
                foundationSoilBearing >= actualBearingPressure, "AASHTO 10.6"),
            GenericSafetyCheck("Concrete strength ≥ code minimum", concreteStrength, minConcreteStrength, "MPa",
                concreteStrength >= minConcreteStrength, designCode.version),
            GenericSafetyCheck("Wall thickness ≥ 300 mm", wallThickness, 300.0, "mm",
                wallThickness >= 300.0, "ACI 318"),
            GenericSafetyCheck("Groundwater below wall base", groundwaterLevel, foundationDepth, "m",
                groundwaterLevel >= foundationDepth, "AASHTO 11"),
            GenericSafetyCheck("Wall height/thickness ≤ 20", wallHeight / thicknessM, 20.0, "-",
                wallHeight / thicknessM <= 20.0, "ACI 318 / ECP 203"),
            GenericSafetyCheck("Backfill classification consistent", if (soilClassOk) 1.0 else 0.0, 1.0, "-",
                soilClassOk, "AASHTO 11 / ECP 200"),
            GenericSafetyCheck("Steel grade ≥ 400 MPa", steelGrade, 400.0, "MPa",
                steelGrade >= 400.0, "ECP 203 / ACI 318"),
            GenericSafetyCheck("Seismic sliding FS ≥ 1.1", fsSlidingSeismic, 1.1, "-",
                fsSlidingSeismic >= 1.1, "AASHTO 11 / Mononobe-Okabe")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Wall height" to "%.2f m".format(wallHeight),
            "Wall thickness" to "%.0f mm".format(wallThickness),
            "Design code" to "${designCode.displayName} (${designCode.version})",
            "Active earth pressure" to "%.1f kN/m".format(activePressure),
            "Seismic overturning moment" to "%.1f kN.m/m".format(seismicOverturningMoment),
            "Seismic pressure increment" to "%.1f kN/m".format(seismicIncrement),
            "FS overturning" to "%.2f".format(fsOverturning),
            "FS sliding (static)" to "%.2f".format(fsSliding),
            "FS sliding (seismic)" to "%.2f".format(fsSlidingSeismic),
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
        val substrateOk = substrateCondition.trim().lowercase() in
            setOf("dry", "clean", "sound", "dry and clean", "prepared")
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
                applicationTemperature in 5.0..35.0, "Product Specs"),
            GenericSafetyCheck("Coating thickness ≥ 1.0 mm", coatingThickness, 1.0, "mm",
                coatingThickness >= 1.0, "BS 8102"),
            GenericSafetyCheck("Substrate dry and sound", if (substrateOk) 1.0 else 0.0, 1.0, "-",
                substrateOk, "BS 8102"),
            GenericSafetyCheck("Curing time ≥ 24 h", curingTime, 24.0, "h",
                curingTime >= 24.0, "Product Specs")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Waterproofing type" to waterproofingType,
            "Membrane thickness" to "%.1f mm".format(membraneThickness),
            "Adhesion strength" to "%.2f MPa".format(adhesionStrength),
            "Overlap width" to "%.0f mm".format(overlapWidth),
            "Leak test" to if (leakTestResult) "PASS" else "FAIL",
            "Coating thickness" to "%.2f mm".format(coatingThickness),
            "Substrate condition" to substrateCondition,
            "Curing time" to "%.0f h".format(curingTime)
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
        val blockDims = blockDimensions.split(Regex("[xX*]")).mapNotNull { it.trim().toDoubleOrNull() }
        val blockValid = blockDims.size == 3 && blockDims.all { it > 0.0 } &&
            (blockDims.maxOrNull() ?: 0.0) / (blockDims.minOrNull() ?: 1.0) <= 8.0
        val mortarRecognized = Regex("(?i)^M\\s?(2\\.5|5|7\\.5|10|12\\.5|15|20)$").matches(mortarType.trim())
        val checks = listOf(
            GenericSafetyCheck("Surface flatness ≤ 3 mm/m", surfaceFlatness, 3.0, "mm/m",
                surfaceFlatness <= 3.0, "ASTM C926"),
            GenericSafetyCheck("Verticality ≤ 5 mm/m", verticality, 5.0, "mm/m",
                verticality <= 5.0, "ASTM C926"),
            GenericSafetyCheck("Adhesion strength ≥ 0.3 MPa", adhesionStrength, 0.3, "MPa",
                adhesionStrength >= 0.3, "ASTM C926"),
            GenericSafetyCheck("Mortar strength ≥ 5 MPa", mortarStrength, 5.0, "MPa",
                mortarStrength >= 5.0, "ECP 204"),
            GenericSafetyCheck("Joint thickness 10 ± 2 mm", jointThickness, 12.0, "mm",
                jointThickness in 8.0..12.0, "ECP 204"),
            GenericSafetyCheck("Curing method provided", 1.0, 1.0, "-",
                curingMethod.isNotBlank() && !curingMethod.equals("none", true), "ASTM C926"),
            GenericSafetyCheck("Plaster type provided", 1.0, 1.0, "-",
                plasterType.isNotBlank() && !plasterType.equals("none", true), "Visual Inspection"),
            GenericSafetyCheck("Block dimensions valid", if (blockValid) 1.0 else 0.0, 1.0, "-",
                blockValid, "ASTM C90"),
            GenericSafetyCheck("Mortar type recognized", if (mortarRecognized) 1.0 else 0.0, 1.0, "-",
                mortarRecognized, "ASTM C270"),
            GenericSafetyCheck("Wall thickness ≥ 100 mm", wallThickness, 100.0, "mm",
                wallThickness >= 100.0, "ECP 204 / ACI 530"),
            GenericSafetyCheck("Plaster thickness 10-25 mm", plasterThickness, 25.0, "mm",
                plasterThickness in 10.0..25.0, "ECP 204"),
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Masonry type" to masonryType,
            "Block dimensions" to blockDimensions,
            "Mortar type" to mortarType,
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
        val finishRecognized = finishType.trim().lowercase() in setOf(
            "polished", "honed", "matte", "epoxy", "tiles", "vinyl", "wood", "concrete", "granite", "marble"
        )
        val checks = listOf(
            GenericSafetyCheck("Surface flatness ≤ 3 mm/2m", surfaceFlatness, 3.0, "mm/2m",
                surfaceFlatness <= 3.0, "ASTM E1155"),
            GenericSafetyCheck("Compressive strength ≥ 25 MPa", compressiveStrength, 25.0, "MPa",
                compressiveStrength >= 25.0, "ACI 302"),
            GenericSafetyCheck("Tensile strength ≥ 2 MPa", tensileStrength, 2.0, "MPa",
                tensileStrength >= 2.0, "ACI 302"),
            GenericSafetyCheck("Joint spacing ≤ 6 m", jointSpacing, 6.0, "m",
                jointSpacing <= 6.0, "ACI 302"),
            GenericSafetyCheck("Joint width 6 ± 2 mm", jointWidth, 8.0, "mm",
                jointWidth in 4.0..8.0, "ACI 302"),
            GenericSafetyCheck("Curing method provided", 1.0, 1.0, "-",
                curingMethod.isNotBlank() && !curingMethod.equals("none", true), "ACI 302"),
            GenericSafetyCheck("Slab thickness ≥ 100 mm", slabThickness, 100.0, "mm",
                slabThickness >= 100.0, "ACI 302"),
            GenericSafetyCheck("Levelness within ≤10 mm", levelness, 10.0, "mm",
                levelness <= 10.0, "ECP 203"),
            GenericSafetyCheck("Finish type recognized", if (finishRecognized) 1.0 else 0.0, 1.0, "-",
                finishRecognized, "Project Specs")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Flooring type" to flooringType,
            "Finish type" to finishType,
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
        groutStrength: Double,
        designCode: DesignCode = DesignCode.ECP
    ): QcOutcome {
        val dimensionsValid = elementDimensions
            .split(Regex("[xX*]"))
            .mapNotNull { it.trim().toDoubleOrNull() }
            .let { dims -> dims.size >= 2 && dims.all { it > 0.0 } }
        val minConcreteStrength = if (designCode == DesignCode.ACI) 25.0 else 30.0
        val finishAcceptable = surfaceFinish.trim().lowercase() in
            setOf("fair", "good", "excellent", "smooth", "standard")
        val checks = listOf(
            GenericSafetyCheck("Element dimensions valid", if (dimensionsValid) 1.0 else 0.0, 1.0, "-",
                dimensionsValid, "ACI 318 §17 / PCI"),
            GenericSafetyCheck("Concrete strength ≥ code minimum", concreteStrength, minConcreteStrength, "MPa",
                concreteStrength >= minConcreteStrength, designCode.version),
            GenericSafetyCheck("Cover ≥ required", coverThickness, 40.0, "mm",
                coverThickness >= 40.0, "ACI 318 §20.6"),
            GenericSafetyCheck("No cracks or damage", 1.0, 1.0, "-",
                !transportDamage, "PCI"),
            GenericSafetyCheck("Lifting anchors present", 1.0, 1.0, "-",
                liftingAnchors, "PCI"),
            GenericSafetyCheck("Connection details provided", 1.0, 1.0, "-",
                connectionDetails.isNotBlank() && !connectionDetails.equals("none", true),
                "ACI 318 §17"),
            GenericSafetyCheck("Erection tolerance ≤ ±10 mm", erectionTolerance, 10.0, "mm",
                erectionTolerance <= 10.0, "PCI"),
            GenericSafetyCheck("Grout strength ≥ 30 MPa", groutStrength, 30.0, "MPa",
                groutStrength >= 30.0, "ACI 318"),
            GenericSafetyCheck("Steel grade ≥ 400 MPa", steelGrade, 400.0, "MPa",
                steelGrade >= 400.0, "ECP 203 / ACI 318"),
            GenericSafetyCheck("Surface finish acceptable", if (finishAcceptable) 1.0 else 0.0, 1.0, "-",
                finishAcceptable, "PCI")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Element type" to elementType,
            "Steel grade" to "%.0f MPa".format(steelGrade),
            "Surface finish" to surfaceFinish,
            "Dimensions" to elementDimensions,
            "Concrete strength" to "%.1f MPa".format(concreteStrength),
            "Design code" to "${designCode.displayName} (${designCode.version})",
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
        val tendonAreaMm2 = PI * tendonDiameter * tendonDiameter / 4.0
        val fpuMpa = if (tendonType.contains("strand", true)) 1860.0 else if (tendonType.contains("wire", true)) 1670.0 else if (tendonType.contains("bar", true)) 1030.0 else 1860.0
        val maxJackingForceKn = 0.80 * fpuMpa * tendonAreaMm2 / 1000.0
        val elongationDiff = if (expectedElongation > 0) {
            Math.abs(elongation - expectedElongation) / expectedElongation * 100.0
        } else {
            Double.POSITIVE_INFINITY
        }
        val anchorageOk = anchorageType.trim().lowercase() in
            setOf("wedge", "barrel", "gripper", "nut", "button head", "dead end")
        val ductOk = ductMaterial.trim().lowercase() in
            setOf("hdpe", "plastic", "galvanized", "steel", "corrugated")

        val checks = listOf(
            GenericSafetyCheck("Jacking force ≤ 0.80 Fpu", jackingForce, maxJackingForceKn, "kN",
                jackingForce > 0.0 && jackingForce <= maxJackingForceKn, "ACI 318 §20 / PTI"),
            GenericSafetyCheck("Elongation within ±5%", elongationDiff, 5.0, "%",
                expectedElongation > 0.0 && elongationDiff <= 5.0, "ACI 318 §20"),
            GenericSafetyCheck("Grout strength ≥ 30 MPa", groutStrength, 30.0, "MPa",
                groutStrength >= 30.0, "ACI 318 §20"),
            GenericSafetyCheck("Grout flow time 10-25 sec", groutFlow, 25.0, "sec",
                groutFlow in 10.0..25.0, "ASTM C939"),
            GenericSafetyCheck("Stress loss ≤ 20%", stressLoss, 20.0, "%",
                stressLoss <= 20.0, "ACI 318 §20"),
            GenericSafetyCheck("Friction coefficient 0.15-0.25", frictionCoefficient, 0.25, "-",
                frictionCoefficient in 0.15..0.25, "PTI"),
            GenericSafetyCheck("Wobble coefficient 0.001-0.005", wobbleCoefficient, 0.005, "-",
                wobbleCoefficient in 0.001..0.005, "PTI"),
            GenericSafetyCheck("Corrosion protection present", 1.0, 1.0, "-",
                corrosionProtection.isNotBlank() && !corrosionProtection.equals("none", true),
                "ACI 318 20"),
            GenericSafetyCheck("Anchorage type recognized", if (anchorageOk) 1.0 else 0.0, 1.0, "-",
                anchorageOk, "PTI"),
            GenericSafetyCheck("Duct material corrosion-resistant", if (ductOk) 1.0 else 0.0, 1.0, "-",
                ductOk, "PTI")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Tendon type" to tendonType,
            "Tendon fpu" to "%.0f MPa".format(fpuMpa),
            "Anchorage type" to anchorageType,
            "Duct material" to ductMaterial,
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
        val substrateOk = substrateCondition.trim().lowercase() in
            setOf("clean", "dry", "sound", "prepared", "saturated")
        val checks = listOf(
            GenericSafetyCheck("Flow time 10-25 sec", flowTime, 25.0, "sec",
                flowTime in 10.0..25.0, "ASTM C939"),
            GenericSafetyCheck("Shrinkage ≤ 0.1%", shrinkage, 0.1, "%",
                shrinkage <= 0.1, "ASTM C939"),
            GenericSafetyCheck("Compressive strength ≥ 30 MPa", compressiveStrength, 30.0, "MPa",
                compressiveStrength >= 30.0, "ACI 304"),
            GenericSafetyCheck("Viscosity 100-1000 cP", viscosity, 1000.0, "cP",
                viscosity in 100.0..1000.0, "Product Specs"),
            GenericSafetyCheck("Gel time 5-30 min", gelTime, 30.0, "min",
                gelTime in 5.0..30.0, "Product Specs"),
            GenericSafetyCheck("Bond strength ≥ 1.5 MPa", bondStrength, 1.5, "MPa",
                bondStrength >= 1.5, "ACI 304"),
            GenericSafetyCheck("Injection pressure 0.2-0.5 MPa", injectionPressure, 0.5, "MPa",
                injectionPressure in 0.2..0.5, "ACI 304"),
            GenericSafetyCheck("Injection volume > 0", injectionVolume, 0.0, "L",
                injectionVolume > 0.0, "Design"),
            GenericSafetyCheck("Curing time ≥ 7 days", curingTime, 7.0, "days",
                curingTime >= 7.0, "ACI 304"),
            GenericSafetyCheck("Substrate clean and sound", if (substrateOk) 1.0 else 0.0, 1.0, "-",
                substrateOk, "ACI 304")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Grout type" to groutType,
            "Curing time" to "%.0f days".format(curingTime),
            "Substrate condition" to substrateCondition,
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
        val settlementReduction = if (settlementBefore > 0) (settlementBefore - settlementAfter) / settlementBefore * 100.0 else 0.0
        val verificationTestOk = qualityControlTest.isNotBlank() && !qualityControlTest.equals("none", true)

        val checks = listOf(
            GenericSafetyCheck("Post-improvement bearing ≥ pre-improvement", postImprovementBearing, preImprovementBearing, "kPa",
                postImprovementBearing >= preImprovementBearing, "AASHTO / FHWA"),
            GenericSafetyCheck("Bearing improvement ≥ 50%", bearingImprovement, 50.0, "%",
                bearingImprovement >= 50.0, "FHWA"),
            GenericSafetyCheck("SPT improvement ≥ 50%", sptImprovement, 50.0, "%",
                sptImprovement >= 50.0, "FHWA"),
            GenericSafetyCheck("Settlement after ≤ 25 mm", settlementAfter, 25.0, "mm",
                settlementAfter <= 25.0, "AASHTO"),
            GenericSafetyCheck("Improvement depth > 0", improvementDepth, 0.0, "m",
                improvementDepth > 0.0, "Design"),
            GenericSafetyCheck("Improvement diameter > 0", improvementDiameter, 0.0, "m",
                improvementDiameter > 0.0, "Design"),
            GenericSafetyCheck("Spacing ≥ 2 x diameter", spacing, 2.0 * improvementDiameter, "m",
                improvementDiameter > 0.0 && spacing >= 2.0 * improvementDiameter, "Design"),
            GenericSafetyCheck("Improvement material provided", 1.0, 1.0, "-",
                improvementMaterial.isNotBlank() && !improvementMaterial.equals("none", true),
                "Project Specs"),
            GenericSafetyCheck("Settlement reduction ≥ 30%", settlementReduction, 30.0, "%",
                settlementReduction >= 30.0, "FHWA"),
            GenericSafetyCheck("Verification test specified", if (verificationTestOk) 1.0 else 0.0, 1.0, "-",
                verificationTestOk, "FHWA / ASTM")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Improvement type" to improvementType,
            "Settlement reduction" to "%.1f%%".format(settlementReduction),
            "Verification test" to qualityControlTest,
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
        drainageSystem: Boolean,
        designCode: DesignCode = DesignCode.ECP
    ): QcOutcome {
        val minConcreteStrength = if (designCode == DesignCode.ACI) 25.0 else 30.0
        val minWallThickness = if (designCode == DesignCode.ACI) 200.0 else 250.0
        val dims = tankDimensions.split(Regex("[xX*]")).mapNotNull { it.trim().toDoubleOrNull() }
        val volumeFromDims = if (dims.size >= 3) dims[0] * dims[1] * dims[2] else 0.0
        val dimsMatch = tankCapacity > 0.0 && volumeFromDims > 0.0 &&
            Math.abs(volumeFromDims - tankCapacity) / tankCapacity <= 0.25
        val checks = listOf(
            GenericSafetyCheck("Leak test passed", 1.0, 1.0, "-",
                leakTestResult, "ACI 350 / AWWA D100"),
            GenericSafetyCheck("Hydrostatic test passed", 1.0, 1.0, "-",
                hydrostaticTest, "ACI 350"),
            GenericSafetyCheck("Concrete strength ≥ code minimum", concreteStrength, minConcreteStrength, "MPa",
                concreteStrength >= minConcreteStrength, designCode.version),
            GenericSafetyCheck("Wall thickness ≥ code minimum", wallThickness, minWallThickness, "mm",
                wallThickness >= minWallThickness, "ACI 350 / ECP"),
            GenericSafetyCheck("Floor thickness ≥ 150 mm", floorThickness, 150.0, "mm",
                floorThickness >= 150.0, "ACI 350"),
            GenericSafetyCheck("Waterproofing present", 1.0, 1.0, "-",
                waterproofingType.isNotBlank() && !waterproofingType.equals("none", true),
                "ACI 350"),
            GenericSafetyCheck("Valves present and functional", 1.0, 1.0, "-",
                valveType.isNotBlank() && !valveType.equals("none", true), "AWWA D100"),
            GenericSafetyCheck("Drainage system present", 1.0, 1.0, "-",
                drainageSystem, "ACI 350"),
            GenericSafetyCheck("Dimensions match capacity ±25%", volumeFromDims, tankCapacity, "m3",
                dimsMatch, "ACI 350"),
            GenericSafetyCheck("Steel grade ≥ 400 MPa", steelGrade, 400.0, "MPa",
                steelGrade >= 400.0, "ECP 203 / ACI 318"),
            GenericSafetyCheck("Roof thickness ≥ 150 mm", roofThickness, 150.0, "mm",
                roofThickness >= 150.0, "ACI 350")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Tank type" to tankType,
            "Dimensions" to tankDimensions,
            "Steel grade" to "%.0f MPa".format(steelGrade),
            "Roof thickness" to "%.0f mm".format(roofThickness),
            "Capacity" to "%.1f m³".format(tankCapacity),
            "Design code" to "${designCode.displayName} (${designCode.version})",
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
        val jointOk = jointType.trim().lowercase() in
            setOf("rubber gasket", "mortar", "solvent", "welded", "mechanical", "flanged", "push-on")
        val pipeMaterialOk = pipeMaterial.trim().lowercase() in
            setOf("rcp", "reinforced concrete", "concrete", "pvc", "upvc", "hdpe", "di", "ductile iron", "steel", "grp", "vitrified clay")
        val checks = listOf(
            GenericSafetyCheck("Leak test passed", 1.0, 1.0, "-",
                leakTestResult, "ASTM C923"),
            GenericSafetyCheck("Slope ≥ 1%", slope, 1.0, "%",
                slope >= 1.0, "AASHTO / ECP 204"),
            GenericSafetyCheck("Manhole spacing ≤ 50 m", manholeSpacing, 50.0, "m",
                manholeSpacing <= 50.0, "ECP 204"),
            GenericSafetyCheck("Manhole depth > 0", manholeDepth, 0.0, "m",
                manholeDepth > 0.0, "ECP 204"),
            GenericSafetyCheck("Pipe diameter > 0", pipeDiameter, 0.0, "mm",
                pipeDiameter > 0.0, "Design"),
            GenericSafetyCheck("Backfill compaction ≥ 95%", backfillCompaction, 95.0, "%",
                backfillCompaction >= 95.0, "AASHTO T99"),
            GenericSafetyCheck("Flow rate > 0", flowRate, 0.0, "L/s",
                flowRate > 0.0, "Design"),
            GenericSafetyCheck("Pipe length > 0", pipeLength, 0.0, "m",
                pipeLength > 0.0, "ECP 204"),
            GenericSafetyCheck("Joint type suitable", if (jointOk) 1.0 else 0.0, 1.0, "-",
                jointOk, "ASTM C923 / ECP 204"),
            GenericSafetyCheck("Pipe material recognized", if (pipeMaterialOk) 1.0 else 0.0, 1.0, "-",
                pipeMaterialOk, "ECP 204 / ASTM C76")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Pipe material" to pipeMaterial,
            "Joint type" to jointType,
            "Invert level" to "%.0f mm".format(invertLevel),
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
        val fireRatingRequired = paintType.contains("intumescent", true) || paintType.contains("fire", true)
        val fireRatingOk = !fireRatingRequired ||
            (fireRating.isNotBlank() && !fireRating.equals("none", true))
        val checks = listOf(
            GenericSafetyCheck("DFT ≥ 100 μm", dft, 100.0, "μm",
                dft >= 100.0, "ASTM D3359"),
            GenericSafetyCheck("Adhesion ≥ 1.5 MPa", adhesionStrength, 1.5, "MPa",
                adhesionStrength >= 1.5, "ASTM D3359"),
            GenericSafetyCheck("Color uniformity", 1.0, 1.0, "-",
                colorUniformity, "Visual"),
            GenericSafetyCheck("Surface preparation provided", 1.0, 1.0, "-",
                surfacePreparation.isNotBlank() && !surfacePreparation.equals("none", true), "SSPC"),
            GenericSafetyCheck("Number of coats ≥ 1", numberOfCoats.toDouble(), 1.0, "-",
                numberOfCoats >= 1, "Product Specs"),
            GenericSafetyCheck("Coating thickness > 0", coatingThickness, 0.0, "mm",
                coatingThickness > 0.0, "ASTM D3359"),
            GenericSafetyCheck("Curing time > 0", curingTime, 0.0, "hours",
                curingTime > 0.0, "ASTM E119"),
            GenericSafetyCheck("Application temperature 5-40 °C", ambientTemperature, 40.0, "°C",
                ambientTemperature in 5.0..40.0, "Product Specs"),
            GenericSafetyCheck("Fire rating provided where required", if (fireRatingOk) 1.0 else 0.0, 1.0, "-",
                fireRatingOk, "ASTM E119"),
            GenericSafetyCheck("Humidity 40-85%", humidity, 85.0, "%",
                humidity in 40.0..85.0, "Product Specs")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Paint type" to paintType,
            "Fire rating" to fireRating,
            "Humidity" to "%.0f %%".format(humidity),
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
            GenericSafetyCheck("Grading level finite", gradingLevel, 0.0, "m",
                gradingLevel.isFinite(), "Project Specs"),
            GenericSafetyCheck("Slope within ±1%", slope, 1.0, "%",
                slope in -1.0..1.0, "Project Specs"),
            GenericSafetyCheck("Fences present and sound", 1.0, 1.0, "-",
                fenceType.isNotBlank() && !fenceType.equals("none", true), "Project Specs"),
            GenericSafetyCheck("Gates present and functional", 1.0, 1.0, "-",
                gateType.isNotBlank() && !gateType.equals("none", true), "Project Specs"),
            GenericSafetyCheck("Lighting present and functional", 1.0, 1.0, "-",
                lightingType.isNotBlank() && !lightingType.equals("none", true), "Project Specs"),
            GenericSafetyCheck("Drainage system present", 1.0, 1.0, "-",
                drainageSystem, "Project Specs"),
            GenericSafetyCheck("Safety signs present", 1.0, 1.0, "-",
                safetySigns, "Project Specs"),
            GenericSafetyCheck("Fire extinguishers present", 1.0, 1.0, "-",
                fireExtinguishers, "Project Specs"),
            GenericSafetyCheck("Pavement type specified", 1.0, 1.0, "-",
                pavementType.isNotBlank() && !pavementType.equals("none", true), "Project Specs"),
            GenericSafetyCheck("Landscaping specified", 1.0, 1.0, "-",
                landscapingType.isNotBlank() && !landscapingType.equals("none", true), "Project Specs"),
            GenericSafetyCheck("First aid kit available", 1.0, 1.0, "-",
                firstAidKit, "HSE Plan")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Grading level" to "%.2f m".format(gradingLevel),
            "Slope" to "%.2f%%".format(slope),
            "Fence type" to fenceType,
            "Pavement type" to pavementType,
            "Landscaping" to landscapingType,
            "First aid kit" to if (firstAidKit) "YES" else "NO",
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
            GenericSafetyCheck("Coating thickness > 0", coatingThickness, 0.0, "mm",
                coatingThickness > 0.0, "ASTM E119 / ACI 216"),
            GenericSafetyCheck("Adhesion ≥ 1.0 MPa", adhesionStrength, 1.0, "MPa",
                adhesionStrength >= 1.0, "ASTM E119"),
            GenericSafetyCheck("Fire rating provided", 1.0, 1.0, "-",
                fireRating.isNotBlank() && !fireRating.equals("none", true), "ASTM E119"),
            GenericSafetyCheck("Coverage area > 0", coverageArea, 0.0, "m²",
                coverageArea > 0.0, "Design"),
            GenericSafetyCheck("Application method provided", 1.0, 1.0, "-",
                applicationMethod.isNotBlank(), "Product Specs"),
            GenericSafetyCheck("Curing time > 0", curingTime, 0.0, "hours",
                curingTime > 0.0, "Product Specs"),
            GenericSafetyCheck("Application temperature 5-40 °C", ambientTemperature, 40.0, "°C",
                ambientTemperature in 5.0..40.0, "Product Specs"),
            GenericSafetyCheck("Surface preparation provided", 1.0, 1.0, "-",
                surfacePreparation.isNotBlank(), "SSPC"),
            GenericSafetyCheck("Humidity 40-85%", humidity, 85.0, "%",
                humidity in 40.0..85.0, "Product Specs")
        )

        val passed = checks.all { it.passed }
        val details = listOf(
            "Protection type" to protectionType,
            "Humidity" to "%.0f %%".format(humidity),
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
