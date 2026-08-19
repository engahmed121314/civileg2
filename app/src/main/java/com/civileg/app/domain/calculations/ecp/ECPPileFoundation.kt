package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.base.*
import kotlin.math.*

/**
 * Pile foundation design engine per Egyptian Code ECP 203-2020.
 * Covers:
 *  - Single pile capacity (shaft friction + end bearing) for driven, bored, CFA, micropile
 *  - Pile cap design: punching shear, beam shear, flexure
 *  - Pile group efficiency (Converse-Labarre formula)
 *  - Settlement calculation (Meyerhof's method)
 *  - Negative skin friction
 *  - Lateral load capacity (Broms method)
 *  - Pile structural reinforcement design
 *
 * References:
 *  - ECP 203-2020 Clause 7-2 (Pile Foundations)
 *  - ECP 203-2020 Clause 4-3-2 (Punching Shear)
 *  - Meyerhof (1951, 1956) bearing capacity & settlement
 *  - Broms (1964) lateral load analysis
 *  - Converse-Labarre (1937) group efficiency
 */
class ECPPileFoundation : PileFoundationDesign {

    companion object {
        // Material partial safety factors (ECP 203)
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val GAMMA_CONCRETE = 25.0       // kN/m³

        // ECP 203 minimum reinforcement ratios
        private const val MIN_LONGITUDINAL_RATIO = 0.005  // 0.5% for piles (ECP 203 Table 4-8)
        private const val MAX_LONGITUDINAL_RATIO = 0.04   // 4%

        // Bearing capacity factors (Terzaghi, modified for piles)
        private const val Nc = 9.0
        private const val Nq_SAND = 40.0
        private const val Nr = 0.0   // negligible for deep foundations

        // Settlement parameters
        private const val Es_SOIL = 25.0      // MPa (typical modulus for clay)
        private const val ALLOWABLE_SETTLEMENT = 25.0  // mm (typical)

        // Concrete cover for piles (ECP 203 Table 4-3)
        private const val PILE_COVER = 75.0   // mm

        // Minimum pile diameter per type (mm)
        private const val MIN_DIAMETER_MICROPILE = 150.0
        private const val MIN_DIAMETER_BORED = 450.0
        private const val MIN_DIAMETER_DRIVEN = 250.0

        // Steel density for weight calculation
        private const val STEEL_DENSITY = 7850.0  // kg/m³
    }

    // ══════════════════════════════════════════════════════════
    // MAIN DESIGN ENTRY POINT
    // ══════════════════════════════════════════════════════════

    override fun designPile(input: PileInput): PileDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // Validate minimum diameter
        val minDia = when (input.pileType) {
            PileType.MICROPILE -> MIN_DIAMETER_MICROPILE
            PileType.BORED -> MIN_DIAMETER_BORED
            PileType.DRIVEN -> MIN_DIAMETER_DRIVEN
            PileType.CFA -> MIN_DIAMETER_BORED
        }
        if (input.pileDiameter < minDia) {
            warnings.add("Pile diameter ${input.pileDiameter}mm < min ${minDia}mm for ${input.pileType.displayName}")
        }

        // 1. Single pile capacity
        val capacityResult = calculatePileCapacity(input)

        // 2. Pile group efficiency
        val groupInput = PileGroupInput(
            numberOfPiles = input.numberOfPiles,
            pileDiameter = input.pileDiameter,
            spacing = input.spacing,
            pattern = input.pileGroupPattern,
            soilType = input.soilType,
            pileLength = input.pileLength,
            pileType = input.pileType
        )
        val groupResult = checkGroupEfficiency(groupInput)

        // 3. Settlement
        val settlementResult = calculateSettlement(input)

        // 4. Pile cap design
        val capInput = PileCapInput(
            axialLoad = input.axialLoad,
            momentX = input.momentLoad,
            momentY = 0.0,
            lateralLoad = input.lateralLoad,
            numberOfPiles = input.numberOfPiles,
            pileDiameter = input.pileDiameter,
            pileSpacing = input.pileDiameter * input.spacing,
            columnWidth = input.columnWidth,
            columnLength = input.columnLength,
            fcu = input.fcu,
            fy = input.fy,
            cover = input.capConcreteCover,
            pileGroupPattern = input.pileGroupPattern,
            eccentricityX = input.eccentricityX,
            eccentricityY = input.eccentricityY
        )
        val capResult = designPileCap(capInput)

        // 5. Lateral capacity (Broms)
        val lateralResult = calculateLateralCapacity(input)

        // 6. Negative skin friction
        val negFriction = calculateNegativeSkinFriction(input)

        // 7. Structural pile reinforcement design
        val maxMomentOnPile = max(input.momentLoad / input.numberOfPiles, lateralResult.maxBendingMoment)
        val maxAxialOnPile = (input.axialLoad + negFriction) / input.numberOfPiles
        val pileReinforcement = designPileReinforcement(input, maxAxialOnPile, maxMomentOnPile)

        // 8. Overall utilization
        val totalGroupCapacity = groupResult.groupCapacity - negFriction
        val loadPerPile = input.axialLoad / input.numberOfPiles
        val utilizationRatio = loadPerPile / (totalGroupCapacity / input.numberOfPiles)

        val isSafe = capacityResult.allowableCapacity * groupResult.efficiencyFactor >= loadPerPile
                && settlementResult.isOk
                && capResult.punchingShearOk
                && capResult.beamShearOk
                && pileReinforcement.isSafe
                && lateralResult.allowableLateralCapacity >= input.lateralLoad

        codeNotes.add("ECP 203-2020: Pile Foundation Design")
        codeNotes.add("Pile: ${input.pileType.displayName}, Ø${input.pileDiameter.toInt()}mm x ${input.pileLength}m")
        codeNotes.add("Soil: ${input.soilType.displayName}, FS=${input.safetyFactor}")
        codeNotes.add("Ultimate capacity: ${"%.1f".format(capacityResult.ultimateCapacity)} kN")
        codeNotes.add("Allowable capacity: ${"%.1f".format(capacityResult.allowableCapacity)} kN")
        codeNotes.add("Group efficiency: ${"%.2f".format(groupResult.efficiencyFactor)}")
        codeNotes.add("Pile reinforcement: ${pileReinforcement.barString}")
        if (negFriction > 0) {
            codeNotes.add("Negative skin friction: ${"%.1f".format(negFriction)} kN")
            warnings.add("Negative skin friction reduces net capacity by ${"%.1f".format(negFriction)} kN")
        }

        return PileDesignResult(
            pileType = input.pileType.displayName,
            soilType = input.soilType.displayName,
            pileDiameterMm = input.pileDiameter,
            pileLengthM = input.pileLength,
            numberOfPiles = input.numberOfPiles,
            fcu = input.fcu,
            fy = input.fy,
            capacityResult = capacityResult,
            groupResult = groupResult,
            settlementResult = settlementResult,
            capResult = capResult,
            lateralCapacity = lateralResult.allowableLateralCapacity,
            lateralUtilizationRatio = input.lateralLoad / lateralResult.allowableLateralCapacity,
            negativeSkinFriction = negFriction,
            pileReinforcement = pileReinforcement,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ══════════════════════════════════════════════════════════
    // SINGLE PILE GEOTECHNICAL CAPACITY
    // ══════════════════════════════════════════════════════════

    override fun calculatePileCapacity(input: PileInput): PileCapacityResult {
        val D = input.pileDiameter / 1000.0  // m
        val L = input.pileLength             // m
        val perimeter = PI * D               // m
        val area = PI * D * D / 4.0          // m²

        // Effective length below scour depth
        val effectiveLength = max(L - input.scourDepth, 1.0)

        // Shaft resistance and end bearing depend on soil type
        val (shaftResistance, endBearingResistance) = when (input.soilType) {
            SoilType.CLAY -> calculateClayCapacity(
                input = input,
                D = D,
                L = effectiveLength,
                perimeter = perimeter,
                area = area
            )
            SoilType.SAND -> calculateSandCapacity(
                input = input,
                D = D,
                L = effectiveLength,
                perimeter = perimeter,
                area = area
            )
            SoilType.ROCK -> calculateRockCapacity(
                input = input,
                D = D,
                area = area
            )
            SoilType.MIXED -> {
                // Split: upper half sand, lower half clay
                val midLength = effectiveLength / 2.0
                val (shaftSand, endSand) = calculateSandCapacity(
                    input = input, D = D, L = midLength,
                    perimeter = perimeter, area = area
                )
                val (shaftClay, endClay) = calculateClayCapacity(
                    input = input, D = D, L = effectiveLength - midLength,
                    perimeter = perimeter, area = area
                )
                // End bearing uses the stronger layer
                Pair(shaftSand + shaftClay, max(endSand, endClay))
            }
        }

        val ultimateCapacity = shaftResistance + endBearingResistance
        val allowableCapacity = ultimateCapacity / input.safetyFactor
        val utilizationRatio = input.axialLoad / (allowableCapacity * input.numberOfPiles)

        return PileCapacityResult(
            ultimateCapacity = ultimateCapacity,
            allowableCapacity = allowableCapacity,
            shaftResistance = shaftResistance,
            endBearingResistance = endBearingResistance,
            fs = input.safetyFactor,
            utilizationRatio = utilizationRatio
        )
    }

    /**
     * Clay capacity: alpha-method for shaft, Nc*cu for end bearing.
     * ECP 203 references Meyerhof (1951) for cohesive soils.
     */
    private fun calculateClayCapacity(
        input: PileInput,
        D: Double, L: Double,
        perimeter: Double, area: Double
    ): Pair<Double, Double> {
        val alpha = input.pileType.alpha
        val cu = input.cu  // kPa

        // Average undrained shear strength along shaft (simplified: uniform)
        // For layered soil, integrate cu(z) over length
        val shaftResistance = alpha * cu * perimeter * L  // kN

        // End bearing: 9*cu for deep foundations (ECP 203/7-2)
        val embedmentFactor = min(input.embedmentDepth / (5.0 * D), 1.0)
        val endBearing = Nc * cu * area * embedmentFactor  // kN

        return Pair(shaftResistance, endBearing)
    }

    /**
     * Sand capacity: beta-method for shaft, Nq*sigma_v' for end bearing.
     * Uses effective stress analysis per ECP 203/7-2.
     */
    private fun calculateSandCapacity(
        input: PileInput,
        D: Double, L: Double,
        perimeter: Double, area: Double
    ): Pair<Double, Double> {
        val phi = input.phi  // degrees
        val gamma = input.gammaSoil  // kN/m³
        val K = when (input.pileType) {
            PileType.DRIVEN -> 1.0 + 0.2 * (phi - 25.0) / 15.0   // K = 1.0 to 1.4
            PileType.BORED -> 0.5 + 0.1 * (phi - 25.0) / 15.0   // K = 0.5 to 0.8
            PileType.CFA -> 0.7 + 0.15 * (phi - 25.0) / 15.0   // K = 0.7 to 1.0
            PileType.MICROPILE -> 0.4 + 0.1 * (phi - 25.0) / 15.0
        }
        val beta = K * tan(toRadians(phi)) * input.pileType.betaFactor

        // Shaft resistance: integrate effective stress over length
        // Account for water table
        val gammaSub = gamma - 9.81  // submerged unit weight
        val wtDepth = input.waterTableDepth

        var shaftResistance = 0.0
        val nSegments = 20
        val dz = L / nSegments
        for (i in 1..nSegments) {
            val z = i * dz  // depth from top of pile (m)
            val effectiveStress = if (z <= wtDepth) {
                gamma * z
            } else {
                gamma * wtDepth + gammaSub * (z - wtDepth)
            }
            shaftResistance += beta * effectiveStress * perimeter * dz
        }

        // End bearing: Nq * sigma'_v at pile tip
        val zTip = L
        val sigmaVTIP = if (zTip <= wtDepth) {
            gamma * zTip
        } else {
            gamma * wtDepth + gammaSub * (zTip - wtDepth)
        }

        // Nq increases with phi (Berezantzev values for piles)
        val Nq = when {
            phi >= 40 -> 120.0
            phi >= 35 -> 80.0
            phi >= 30 -> Nq_SAND
            phi >= 25 -> 20.0
            else -> 12.0
        }

        val embedmentFactor = min(input.embedmentDepth / (5.0 * D), 1.0)
        val endBearing = Nq * sigmaVTIP * area * embedmentFactor  // kN

        return Pair(shaftResistance, endBearing)
    }

    /**
     * Rock capacity: based on rock quality and embedment.
     * Uses simplified approach per ECP 203/7-2.
     */
    private fun calculateRockCapacity(
        input: PileInput,
        D: Double, area: Double
    ): Pair<Double, Double> {
        val cu = input.cu  // kPa (used as rock UCS proxy)

        // Shaft resistance in rock socket (typically 10-25% of rock UCS)
        val rockSocketLength = min(input.embedmentDepth, 3.0 * D)
        val shaftStress = 0.15 * cu * input.pileType.betaFactor  // kPa
        val shaftResistance = shaftStress * PI * D * rockSocketLength  // kN

        // End bearing: 2-5 x UCS for rock (use 3x conservative)
        val endBearingStress = 3.0 * cu  // kPa
        val endBearing = endBearingStress * area  // kN

        return Pair(shaftResistance, endBearing)
    }

    // ══════════════════════════════════════════════════════════
    // PILE CAP DESIGN (ECP 203 Clause 7-2-4)
    // ══════════════════════════════════════════════════════════

    override fun designPileCap(input: PileCapInput): PileCapResult {
        val warnings = mutableListOf<String>()

        val n = input.numberOfPiles
        val Dp = input.pileDiameter
        val spacing = input.pileSpacing
        val colW = input.columnWidth
        val colL = input.columnLength

        // 1. Determine cap dimensions based on pile layout
        val (nCols, nRows) = parsePattern(input.pileGroupPattern, n)
        val edgeDist = (Dp / 2.0 + 100.0).coerceAtLeast(200.0)  // min 200mm edge

        val capWidth = when (nRows) {
            1 -> Dp + 2 * edgeDist
            else -> ((nRows - 1) * spacing + Dp + 2 * edgeDist)
        }.let { ceil(it / 50.0) * 50.0 }

        val capLength = when (nCols) {
            1 -> Dp + 2 * edgeDist
            else -> ((nCols - 1) * spacing + Dp + 2 * edgeDist)
        }.let { ceil(it / 50.0) * 50.0 }

        // 2. Cap thickness: max(spacing/3, 400mm) per ECP 203/7-2-4
        val minThickness = max(spacing / 3.0, 400.0)
        val capThickness = ceil(minThickness / 50.0) * 50.0
        val d = capThickness - input.cover - 10.0  // effective depth

        // 3. Calculate pile loads with eccentricity
        val loadPerPile = input.axialLoad / n
        val Mx = input.momentX
        val My = input.momentY
        val ex = input.eccentricityX
        val ey = input.eccentricityY

        // Moment due to eccentricity
        val Mex = input.axialLoad * ex
        val Mey = input.axialLoad * ey
        val totalMx = Mx + Mex
        val totalMy = My + Mey

        // Pile group centroid
        val Ixx = when (nRows) {
            1 -> 0.0
            else -> {
                val s = spacing / 1000.0
                val countPerRow = n
                val rowSpan = if (nRows > 1) ((nRows - 1) * s / 2.0) else 0.0
                nCols * (nRows.toDouble() / 3.0) * rowSpan.pow(2)
            }
        }

        // Max pile reaction (corner pile gets maximum)
        val maxPileReaction = if (n > 1 && (abs(totalMx) > 0.01 || abs(totalMy) > 0.01)) {
            val sx = if (nCols > 1) (nCols - 1) * spacing / 1000.0 / 2.0 else 0.0
            val sy = if (nRows > 1) (nRows - 1) * spacing / 1000.0 / 2.0 else 0.0
            val denom = if (Ixx > 0.01) Ixx else 1.0
            loadPerPile + abs(totalMx) * sx / denom + abs(totalMy) * sy / denom
        } else {
            loadPerPile
        }

        // 4. Punching shear check (ECP 203 Clause 4-3-2)
        // Critical perimeter at d/2 from column face
        val bo = 2.0 * (colW + colL) + 4.0 * d  // mm
        val punchArea = (colW + 2.0 * d) * (colL + 2.0 * d)  // mm²
        val Vpunch = input.axialLoad * 0.9  // kN (net punching after soil/pile reaction)
        val punchingStress = (Vpunch * 1000.0) / (bo * d)  // MPa
        val punchingCapacity = 0.316 * sqrt(input.fcu / GAMMA_C)  // MPa
        val punchingOk = punchingStress <= punchingCapacity

        if (!punchingOk) {
            warnings.add("Punching shear ${"%.2f".format(punchingStress)} > ${"%.2f".format(punchingCapacity)} MPa")
        }

        // 5. Beam shear check (one-way shear)
        // Critical section at d from column face
        val shearSpanX = (capLength - colL) / 2.0 - d  // mm
        val shearSpanY = (capWidth - colW) / 2.0 - d  // mm

        // Shear at critical section = sum of pile reactions beyond d
        val nPilesBeyondDx = countPilesBeyondShearSpan(nCols, nRows, spacing, shearSpanX, 'x')
        val nPilesBeyondDy = countPilesBeyondShearSpan(nCols, nRows, spacing, shearSpanY, 'y')

        val Vu_x = nPilesBeyondDx * maxPileReaction  // kN
        val Vu_y = nPilesBeyondDy * maxPileReaction  // kN

        // One-way shear capacity per ECP 203: qcu = 0.24 * sqrt(fcu/gamma_c)
        val qcu = 0.24 * sqrt(input.fcu / GAMMA_C)  // MPa
        val beamShearStress = max(
            (Vu_x * 1000.0) / (capLength * d),
            (Vu_y * 1000.0) / (capWidth * d)
        )
        val beamShearOk = beamShearStress <= qcu

        if (!beamShearOk) {
            warnings.add("Beam shear ${"%.2f".format(beamShearStress)} > ${"%.2f".format(qcu)} MPa")
        }

        // 6. Flexural reinforcement design (ECP 203 K-method)
        // Max moment at face of column
        val cantileverX = ((capLength - colL) / 2.0 + Dp / 2.0) / 1000.0  // m (from col face to pile center)
        val cantileverY = ((capWidth - colW) / 2.0 + Dp / 2.0) / 1000.0

        // Number of piles contributing to moment
        val nPilesMomentX = if (nCols > 1) nCols / 2 else 1
        val nPilesMomentY = if (nRows > 1) nRows / 2 else 1

        val Mu_x = nPilesMomentX * maxPileReaction * cantileverX  // kN.m
        val Mu_y = nPilesMomentY * maxPileReaction * cantileverY  // kN.m

        // Design each direction
        val reinfX = designCapReinforcement(input.fcu, input.fy, Mu_x, capWidth, d)
        val reinfY = designCapReinforcement(input.fcu, input.fy, Mu_y, capLength, d)

        // Use the larger reinforcement as flexural
        val flexuralReinf = if (reinfX.requiredArea >= reinfY.requiredArea) reinfX else reinfY

        // 7. Punching shear reinforcement (if needed)
        val punchingReinf = if (!punchingOk) {
            val vpExcess = (punchingStress - punchingCapacity) * bo * d / 1000.0  // kN
            val asvRequired = vpExcess * 1000.0 / (input.fy / GAMMA_S * d)  // mm²
            val linksDia = selectLinksDiameter(asvRequired)
            val linksArea = PI * linksDia * linksDia / 4.0
            val nLinks = max(4, ceil(asvRequired / linksArea).toInt())
            val linksSpacing = 75  // mm (close spacing for punching)
            RebarDetail(
                bars = nLinks,
                diameter = linksDia,
                spacing = linksSpacing,
                area = nLinks * linksArea,
                requiredArea = asvRequired,
                ratio = (nLinks * linksArea) / max(asvRequired, 1.0)
            )
        } else null

        // 8. Quantities
        val concreteVolume = capWidth * capLength * capThickness / 1e9  // m³
        val totalRebarLength = (reinfX.bars * capLength + reinfY.bars * capWidth +
                (reinfX.bars + reinfY.bars) * 50 * 2) / 1000.0  // m approx
        val rebarDia = max(reinfX.diameter, reinfY.diameter)
        val rebarAreaM = PI * (rebarDia.toDouble() / 1000.0).pow(2) / 4.0
        val steelWeight = totalRebarLength * rebarAreaM * STEEL_DENSITY

        return PileCapResult(
            capWidth = capWidth,
            capLength = capLength,
            capThickness = capThickness,
            punchingShearOk = punchingOk,
            punchingShearStress = punchingStress,
            punchingShearCapacity = punchingCapacity,
            beamShearOk = beamShearOk,
            beamShearStress = beamShearStress,
            beamShearCapacity = qcu,
            flexuralReinforcement = flexuralReinf,
            punchingReinforcement = punchingReinf,
            concreteVolume = concreteVolume,
            steelWeight = steelWeight
        )
    }

    /**
     * Design reinforcement for pile cap using ECP 203 K-method.
     */
    private fun designCapReinforcement(
        fcu: Double, fy: Double,
        moment: Double, width: Double, d: Double
    ): RebarDetail {
        val Mu = moment * 1e6  // N.mm
        val b = 1000.0  // mm per meter width

        // K = Mu / (fcu * b * d²)
        val K = Mu / (fcu * b * d * d)
        val K_bal = 0.186

        // z = d * (0.5 + sqrt(0.25 - K/1.25))
        val z = d * (0.5 + sqrt(max(0.0, 0.25 - K / 1.25)))

        // As = Mu / (fy/gamma_s * z)
        val asRequired = Mu / (fy / GAMMA_S * z)
        val asMin = 0.0015 * b * d  // 0.15% min for foundations
        val asFinal = max(asRequired, asMin)

        // Select bars
        val barDia = selectBarDiameter(asFinal)
        val barArea = PI * barDia * barDia / 4.0
        val span = width / 1000.0  // m
        val nBars = ceil(asFinal / barArea).toInt().coerceAtLeast(4)
        val spacing = floor(1000.0 / (width / nBars)).toInt().coerceIn(100, 250)
        val actualBars = ceil(width / spacing).toInt()
        val asProvided = actualBars * barArea

        return RebarDetail(
            bars = actualBars,
            diameter = barDia.toInt(),
            spacing = spacing,
            area = asProvided,
            requiredArea = asFinal,
            ratio = asProvided / max(asFinal, 1.0)
        )
    }

    /**
     * Parse pile group pattern string like "2x2", "3x3", "2x3".
     */
    private fun parsePattern(pattern: String, totalPiles: Int): Pair<Int, Int> {
        return try {
            val parts = pattern.lowercase().split("x")
            if (parts.size == 2) {
                val cols = parts[0].trim().toInt().coerceAtLeast(1)
                val rows = parts[1].trim().toInt().coerceAtLeast(1)
                Pair(cols, rows)
            } else {
                // Infer pattern from number of piles
                when {
                    totalPiles <= 1 -> Pair(1, 1)
                    totalPiles <= 2 -> Pair(2, 1)
                    totalPiles <= 4 -> Pair(2, 2)
                    totalPiles <= 6 -> Pair(3, 2)
                    totalPiles <= 9 -> Pair(3, 3)
                    else -> Pair(4, 4)
                }
            }
        } catch (e: Exception) {
            Pair(2, 2)
        }
    }

    /**
     * Count piles beyond the shear critical section.
     */
    private fun countPilesBeyondShearSpan(
        nCols: Int, nRows: Int,
        spacing: Double, shearSpan: Double, direction: Char
    ): Int {
        if (shearSpan <= 0) return 0
        val dim = if (direction == 'x') nCols else nRows
        return max(0, dim / 2)
    }

    // ══════════════════════════════════════════════════════════
    // SETTLEMENT CALCULATION (Meyerhof's Method)
    // ══════════════════════════════════════════════════════════

    override fun calculateSettlement(input: PileInput): PileSettlementResult {
        val D = input.pileDiameter / 1000.0  // m
        val L = input.pileLength
        val loadPerPile = input.axialLoad / input.numberOfPiles

        // Immediate settlement (Meyerhof, 1956)
        // Si = Q * sqrt(D) / (Es * L)
        // Using an average soil modulus based on soil type
        val Es = when (input.soilType) {
            SoilType.CLAY -> Es_SOIL
            SoilType.SAND -> 50.0      // MPa (stiffer)
            SoilType.ROCK -> 500.0     // MPa (very stiff)
            SoilType.MIXED -> 35.0
        }

        // Correction factor for pile type
        val typeFactor = when (input.pileType) {
            PileType.DRIVEN -> 0.8
            PileType.BORED -> 1.2
            PileType.CFA -> 1.0
            PileType.MICROPILE -> 1.5
        }

        val immediateSettlement = loadPerPile * sqrt(D) / (Es * L) * 1000.0 * typeFactor  // mm

        // Consolidation settlement (for clay layers)
        val consolidationSettlement = if (input.soilType == SoilType.CLAY || input.soilType == SoilType.MIXED) {
            // Sc = Cc / (1+e0) * H * log10((sigma0 + delta_sigma)/sigma0)
            val Cc = 0.009 * (input.cu - 25.0)  // compression index (simplified)
            val e0 = 0.8  // initial void ratio
            val H = 2.0   // compressible layer thickness below tip (m)
            val sigma0 = input.gammaSoil * (L + H / 2.0)  // average effective stress
            val deltaSigma = loadPerPile / (PI * D * D / 4.0 * 4.0)  // stress increase at midpoint
            val ratio = (sigma0 + deltaSigma) / sigma0
            if (ratio > 1.0) Cc / (1 + e0) * H * log10(ratio) * 1000.0 else 0.0  // mm
        } else {
            0.0
        }

        val totalSettlement = immediateSettlement + consolidationSettlement

        return PileSettlementResult(
            immediateSettlement = immediateSettlement,
            consolidationSettlement = consolidationSettlement,
            totalSettlement = totalSettlement,
            allowableSettlement = ALLOWABLE_SETTLEMENT,
            isOk = totalSettlement <= ALLOWABLE_SETTLEMENT
        )
    }

    // ══════════════════════════════════════════════════════════
    // PILE GROUP EFFICIENCY (Converse-Labarre Formula)
    // ══════════════════════════════════════════════════════════

    override fun checkGroupEfficiency(input: PileGroupInput): PileGroupResult {
        val n = input.numberOfPiles
        val D = input.pileDiameter / 1000.0
        val spacing = D * input.spacing  // actual spacing in m

        // Converse-Labarre formula:
        // η = 1 - (θ/90) * [(d * (n-1) + (m-1) * sqrt(2) * d) / (m * n * s)]
        // Simplified: η = 1 - (arctan(D/s) in degrees / 90) * (n-1)/(n) * correction

        val (nCols, nRows) = parsePattern(input.pattern, n)
        val m = nCols
        val nn = nRows
        val totalPiles = m * nn

        val theta = toDegrees(atan(D / spacing))
        val groupTerm1 = if (m > 1) (m - 1) else 0.0
        val groupTerm2 = if (nn > 1) (nn - 1) else 0.0
        val numerator = D * (groupTerm1 + groupTerm2) +
                D * sqrt(2.0) * groupTerm1 * groupTerm2
        val denominator = totalPiles * spacing

        val efficiency = if (denominator > 0) {
            (1.0 - (theta / 90.0) * (numerator / denominator)).coerceIn(0.5, 1.0)
        } else {
            1.0
        }

        // Single pile capacity for reference
        val singlePileInput = PileInput(
            pileType = input.pileType,
            pileDiameter = input.pileDiameter,
            pileLength = input.pileLength,
            numberOfPiles = 1,
            spacing = input.spacing,
            axialLoad = 0.0,
            soilType = input.soilType,
            cu = 50.0,
            phi = 30.0,
            gammaSoil = 18.0,
            safetyFactor = 3.0
        )
        val singleCapacity = calculatePileCapacity(singlePileInput)
        val individualCapacity = singleCapacity.allowableCapacity
        val groupCapacity = individualCapacity * totalPiles * efficiency

        return PileGroupResult(
            efficiencyFactor = efficiency,
            groupCapacity = groupCapacity,
            individualCapacity = individualCapacity,
            spacing = spacing * 1000.0,  // mm
            numberOfPiles = totalPiles,
            pattern = input.pattern
        )
    }

    // ══════════════════════════════════════════════════════════
    // LATERAL LOAD CAPACITY (Broms Method, 1964)
    // ══════════════════════════════════════════════════════════

    override fun calculateLateralCapacity(input: PileInput): LateralLoadResult {
        val D = input.pileDiameter / 1000.0  // m
        val L = input.pileLength
        val H = input.lateralLoad             // kN

        // Broms method differentiates between cohesive and cohesionless soil
        val result = when (input.soilType) {
            SoilType.CLAY, SoilType.MIXED -> bromsCohesive(input, D, L, H)
            SoilType.SAND, SoilType.ROCK -> bromsCohesionless(input, D, L, H)
        }

        return result
    }

    /**
     * Broms method for piles in cohesive soil (clay).
     * Uses undrained shear strength (cu).
     */
    private fun bromsCohesive(input: PileInput, D: Double, L: Double, H: Double): LateralLoadResult {
        val cu = input.cu / 1000.0  // convert kPa to MPa... no, keep kN/m²
        // cu in kN/m²
        val cu_kPa = input.cu  // kPa = kN/m²

        // Soil resistance per unit length: p = 9 * cu * D (for clay)
        val p = 9.0 * cu_kPa * D  // kN/m

        // Ultimate lateral capacity (free head, short pile)
        // Hu = 2 * p * L (for short pile, rotation about pile tip)
        // For long pile: Hu = p * L² / (e + 1.5 * D + 0.5 * L)
        // Simplified: use both and take minimum
        val Hu_short = 2.0 * p * L  // short pile assumption
        val f = 1.5 * D
        val Hu_long = p * L * L / (f + L / 2.0)  // long pile assumption

        val Hu = min(Hu_short, Hu_long)
        val allowableHu = Hu / 2.5  // FS = 2.5 for lateral

        // Maximum bending moment (at depth of zero shear)
        val depthToMaxMoment = if (Hu_short <= Hu_long) {
            L * 0.5  // short pile: max moment at mid-depth
        } else {
            sqrt(2.0 * H * f / p).coerceAtMost(L * 0.7)  // long pile
        }
        val maxMoment = H * (input.momentLoad / max(H, 1.0) + depthToMaxMoment)

        // Depth to fixity
        val depthToFixity = 1.5 * D * sqrt(p / (H + 1.0))
            .coerceIn(1.5 * D, L * 0.8)

        // Deflection at pile head (simplified elastic approach)
        val Ep = 30.0 * 1e6 * PI * D * D / 4.0 * (D / 2.0) * (D / 2.0)  // EI (kN.m²)
        // EI = E * I = 30e6 kPa * π/64 * D⁴
        val EI = 30e6 * PI * D.pow(4) / 64.0  // kN.m²
        val kh = 5.0 * cu_kPa / D  // subgrade modulus kN/m³
        val nh = kh / D  // rate of increase
        val deflection = if (nh > 0.01) {
            (H * L * L * L / (3.0 * EI) * 1000.0).coerceAtMost(25.0)
        } else {
            (H * L * L * L / (3.0 * EI) * 1000.0).coerceAtMost(25.0)
        }

        return LateralLoadResult(
            ultimateLateralCapacity = Hu,
            allowableLateralCapacity = allowableHu,
            maxBendingMoment = maxMoment,
            depthToFixity = depthToFixity,
            deflectionAtHead = deflection
        )
    }

    /**
     * Broms method for piles in cohesionless soil (sand).
     * Uses friction angle and unit weight.
     */
    private fun bromsCohesionless(input: PileInput, D: Double, L: Double, H: Double): LateralLoadResult {
        val phi = input.phi
        val gamma = input.gammaSoil
        val Kp = (1.0 + sin(toRadians(phi))) / (1.0 - sin(toRadians(phi)))  // passive earth pressure coeff

        // Soil resistance per unit length increases linearly: p = 3 * Kp * gamma * z * D
        // At depth z: p(z) = 3 * Kp * gamma * z * D
        // Average over pile length: p_avg = 3 * Kp * gamma * L/2 * D = 1.5 * Kp * gamma * L * D
        val pAvg = 1.5 * Kp * gamma * L * D  // kN/m (average soil resistance)

        // Ultimate lateral capacity
        val Hu_short = 1.5 * pAvg * L  // short pile
        val f = 1.5 * D
        val Hu_long = pAvg * L * L / (f + L / 2.0)
        val Hu = min(Hu_short, Hu_long)
        val allowableHu = Hu / 2.5

        // Maximum bending moment
        val depthToMaxMoment = L * 0.4
        val maxMoment = H * (input.momentLoad / max(H, 1.0) + depthToMaxMoment)

        // Depth to fixity
        val depthToFixity = (2.0 * H / (3.0 * Kp * gamma * D)).pow(1.0 / 3.0)
            .coerceIn(1.5 * D, L * 0.8)

        // Deflection
        val EI = 30e6 * PI * D.pow(4) / 64.0
        val deflection = (H * L * L * L / (3.0 * EI) * 1000.0).coerceAtMost(25.0)

        return LateralLoadResult(
            ultimateLateralCapacity = Hu,
            allowableLateralCapacity = allowableHu,
            maxBendingMoment = maxMoment,
            depthToFixity = depthToFixity,
            deflectionAtHead = deflection
        )
    }

    // ══════════════════════════════════════════════════════════
    // NEGATIVE SKIN FRICTION
    // ══════════════════════════════════════════════════════════

    override fun calculateNegativeSkinFriction(input: PileInput): Double {
        // Negative skin friction occurs when soil settles more than the pile
        // This typically happens in compressible soil layers (clay, fill)
        // Only applies if there is a compressible layer above the bearing stratum

        if (input.soilType == SoilType.ROCK) return 0.0

        val D = input.pileDiameter / 1000.0
        val perimeter = PI * D

        // Compressible layer depth (from ground surface to top of bearing layer)
        val compressibleDepth = input.pileLength - input.embedmentDepth
        if (compressibleDepth <= 0) return 0.0

        // Average effective vertical stress in compressible layer
        val gammaSub = input.gammaSoil - 9.81  // submerged unit weight
        val wtDepth = input.waterTableDepth
        val midDepth = compressibleDepth / 2.0

        val sigmaV = if (midDepth <= wtDepth) {
            input.gammaSoil * midDepth
        } else {
            input.gammaSoil * wtDepth + gammaSub * (midDepth - wtDepth)
        }

        // Negative skin friction per ECP 203/7-2-3
        // K * sigma_v' * tan(delta)
        // K = 0.5 to 1.0 (use 0.7 typical for bored, 1.0 for driven)
        val K = when (input.pileType) {
            PileType.DRIVEN -> 1.0
            PileType.BORED -> 0.7
            PileType.CFA -> 0.8
            PileType.MICROPILE -> 0.6
        }

        // Delta = friction angle between pile and soil
        val deltaFactor = when (input.soilType) {
            SoilType.CLAY -> 0.5  // delta = 0.5 * cu approach
            SoilType.SAND -> tan(toRadians(input.phi * 0.67))  // delta ≈ 2/3 * phi
            SoilType.MIXED -> 0.4
            SoilType.ROCK -> 0.0
        }

        // Unit negative skin friction (kN/m²)
        val nsfStress = K * sigmaV * deltaFactor

        // Total negative skin friction force
        val negativeFriction = nsfStress * perimeter * compressibleDepth  // kN

        return negativeFriction
    }

    // ══════════════════════════════════════════════════════════
    // PILE STRUCTURAL REINFORCEMENT DESIGN (ECP 203)
    // ══════════════════════════════════════════════════════════

    override fun designPileReinforcement(
        input: PileInput,
        axialLoad: Double,  // kN per pile
        moment: Double      // kN.m per pile
    ): PileReinforcementResult {
        val D = input.pileDiameter  // mm
        val d = D - PILE_COVER - 20.0  // effective depth (mm), assuming 20mm bar + cover
        val Ag = PI * D * D / 4.0    // gross area mm²
        val fcu = input.fcu
        val fy = input.fyp

        // Convert to N and N.mm
        val Nu = axialLoad * 1000.0 * GAMMA_C  // factored axial load, N
        val Mu = moment * 1e6 * GAMMA_C         // factored moment, N.mm

        // Eccentricity
        val e = if (Nu > 0) abs(Mu) / Nu else 0.0  // mm

        // Check if the section is compression-controlled or tension-controlled
        // Using simplified interaction approach per ECP 203

        // Required area of longitudinal reinforcement
        // For combined axial + bending (simplified column approach)
        // As = (Nu * e - 0.45 * fcu * Ag * (D/2 - d')) / (fy/gamma_s * (d - d'))
        // where d' = cover + bar/2
        val dPrime = PILE_COVER + 10.0  // mm

        // Concrete contribution
        val concreteCapacity = 0.45 * fcu * Ag / GAMMA_C  // N

        // Required steel area
        val leverArm = (d - dPrime).coerceAtLeast(50.0)  // mm
        val steelDemand = (abs(Mu) - concreteCapacity * leverArm / 2.0).coerceAtLeast(0.0)
        val asRequired = steelDemand / (fy / GAMMA_S * leverArm)

        // Minimum reinforcement per ECP 203 Table 4-8
        val asMin = MIN_LONGITUDINAL_RATIO * Ag

        // Maximum reinforcement
        val asMax = MAX_LONGITUDINAL_RATIO * Ag

        // Final area
        val asFinal = asRequired.coerceIn(asMin, asMax)

        // Select longitudinal bars (minimum 6 for piles per ECP 203)
        val barDia = selectPileBarDiameter(asFinal, D)
        val barArea = PI * barDia * barDia / 4.0
        val nBars = max(6, ceil(asFinal / barArea).toInt())
        val asProvided = nBars * barArea

        // Ties/spirals (ECP 203 Clause 7-2-3-2)
        // Ties diameter >= max(6mm, longitudinal/4)
        val tiesDia = max(8, barDia / 4).toInt().coerceAtLeast(8)
        // Ties spacing <= min(16*barDia, 48*tiesDia, 0.75*D)
        val tiesSpacing = minOf(
            (16 * barDia).toInt(),
            48 * tiesDia,
            (0.75 * D).toInt()
        ).coerceAtLeast(100)

        val ratio = asProvided / Ag
        val isSafe = asProvided >= asMin && ratio <= MAX_LONGITUDINAL_RATIO

        return PileReinforcementResult(
            longitudinalBars = nBars,
            longitudinalDiameter = barDia.toInt(),
            longitudinalArea = asProvided,
            requiredLongitudinalArea = asFinal,
            tiesDiameter = tiesDia,
            tiesSpacing = tiesSpacing,
            isSafe = isSafe,
            ratio = ratio
        )
    }

    // ══════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════

    /**
     * Select bar diameter for pile cap reinforcement.
     */
    private fun selectBarDiameter(asRequired: Double): Double {
        val availableBars = listOf(12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
        return availableBars.firstOrNull { dia ->
            val area = PI * dia * dia / 4
            val barsPerMeter = ceil(asRequired / area).toInt()
            barsPerMeter in 4..20
        } ?: 16.0
    }

    /**
     * Select bar diameter for pile longitudinal reinforcement.
     */
    private fun selectPileBarDiameter(asRequired: Double, pileDia: Double): Double {
        val availableBars = listOf(16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
        val maxBars = (pileDia - 2 * PILE_COVER) / (3.0 * availableBars.first())  // min 3x bar spacing
        return availableBars.firstOrNull { dia ->
            val area = PI * dia * dia / 4
            val barsNeeded = ceil(asRequired / area).toInt().coerceAtLeast(6)
            barsNeeded <= maxBars
        } ?: 20.0
    }

    /**
     * Select links diameter for punching shear reinforcement.
     */
    private fun selectLinksDiameter(asvRequired: Double): Int {
        val available = listOf(8, 10, 12, 14, 16)
        return available.firstOrNull { dia ->
            val area = PI * dia * dia / 4.0
            area * 4 >= asvRequired  // minimum 4 legs
        } ?: 10
    }
}