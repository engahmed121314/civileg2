package com.civileg.app.utils

import kotlin.math.*

/**
 * Foundation Design Comparison Tool
 * Compares Isolated, Combined, Pile, Raft, and Strip foundations
 * for a given column load and soil conditions, then recommends the most economical option.
 */

enum class FoundationType(val displayName: String) {
    ISOLATED_FOOTING("Isolated Footing"),
    COMBINED_FOOTING("Combined Footing"),
    PILE_FOUNDATION("Pile Foundation"),
    RAFT("Raft Foundation"),
    STRIP_FOOTING("Strip Footing")
}

data class FoundationComparisonInput(
    val columnLoad: Double = 2000.0,              // kN
    val columnWidth: Double = 400.0,              // mm
    val columnLength: Double = 400.0,              // mm
    val momentX: Double = 0.0,                    // kN.m
    val momentY: Double = 0.0,                    // kN.m
    val allowableBearingPressure: Double = 150.0,  // kN/m2
    val fcu: Double = 30.0,                        // MPa
    val fy: Double = 400.0,                        // MPa
    val groundWaterLevel: Double = 2.0,           // m
    val frostDepth: Double = 0.0,                  // m
    val minFoundationDepth: Double = 1.0,          // m
    val adjacentFoundationDistance: Double = 3.0,  // m
    val soilType: String = "medium",              // "soft"/"medium"/"hard"/"rock"
    val numberOfColumns: Int = 1,
    val spacing: Double = 6.0                      // m (for combined/raft)
)

data class FoundationOption(
    val type: FoundationType,
    val isFeasible: Boolean,
    val requiredArea: Double,                      // m2
    val foundationDimensions: String,              // descriptive
    val estimatedConcreteVolume: Double,           // m3
    val estimatedSteelWeight: Double,              // kg
    val estimatedExcavationVolume: Double,        // m3
    val estimatedCost: Double,                     // relative cost units
    val advantages: List<String>,
    val disadvantages: List<String>,
    val settlement: Double,                        // mm
    val utilizationRatio: Double
)

data class FoundationComparisonResult(
    val options: List<FoundationOption>,
    val recommendedType: FoundationType,
    val recommendationReason: String
)

class FoundationDesignComparator {

    companion object {
        // Cost factors ($/unit)
        private const val CONCRETE_COST_PER_M3 = 800.0
        private const val STEEL_COST_PER_TON = 1200.0
        private const val EXCAVATION_COST_PER_M3 = 50.0

        // Soil bearing capacity estimates (kN/m2)
        private val soilBearingCapacities = mapOf(
            "very_soft" to 50.0, "soft" to 100.0, "medium" to 200.0,
            "stiff" to 300.0, "hard" to 500.0, "rock" to 1000.0
        )

        // Soil elastic modulus for settlement (kN/m2)
        private val soilModulus = mapOf(
            "very_soft" to 5000.0, "soft" to 10000.0, "medium" to 25000.0,
            "stiff" to 50000.0, "hard" to 100000.0, "rock" to 500000.0
        )

        // Pile capacity by soil type (kN per pile of 600mm dia x 15m)
        private val pileCapacityBySoil = mapOf(
            "very_soft" to 250.0, "soft" to 400.0, "medium" to 700.0,
            "stiff" to 1100.0, "hard" to 1600.0, "rock" to 3000.0
        )
    }

    fun compare(input: FoundationComparisonInput): FoundationComparisonResult {
        val options = listOf(
            evaluateIsolatedFooting(input),
            evaluateCombinedFooting(input),
            evaluatePileFoundation(input),
            evaluateRaftFoundation(input),
            evaluateStripFooting(input)
        )

        val feasibleOptions = options.filter { it.isFeasible }
        val recommended = if (feasibleOptions.isEmpty()) {
            options.minByOrNull { it.estimatedCost } ?: options.first()
        } else {
            feasibleOptions.minByOrNull { it.estimatedCost } ?: feasibleOptions.first()
        }

        val reason = buildString {
            append("Based on the analysis, ${recommended.type.displayName} is recommended.")
            append(" Estimated cost: ${"%,.0f".format(recommended.estimatedCost)} currency units.")
            if (!recommended.isFeasible) {
                append(" Note: This option may require special design considerations.")
            } else {
                val savings = feasibleOptions
                    .filter { it.type != recommended.type }
                    .maxByOrNull { it.estimatedCost }
                if (savings != null) {
                    val pct = ((savings.estimatedCost - recommended.estimatedCost) / savings.estimatedCost * 100)
                    append(" This saves approximately ${"%.0f".format(pct)}% compared to ${savings.type.displayName}.")
                }
            }
        }

        return FoundationComparisonResult(options, recommended.type, reason)
    }

    private fun evaluateIsolatedFooting(input: FoundationComparisonInput): FoundationOption {
        val P = input.columnLoad
        val qall = input.allowableBearingPressure
        val Df = maxOf(input.minFoundationDepth, input.frostDepth, 1.0)
        val gamma = 20.0 // kN/m3
        val netBearing = qall - gamma * Df

        // Required area considering moments
        val ex = if (P > 0) input.momentY / P * 1000 else 0.0  // mm eccentricity
        val ey = if (P > 0) input.momentX / P * 1000 else 0.0
        val colW = input.columnWidth / 1000.0
        val colL = input.columnLength / 1000.0

        // Required area with eccentricity
        val baseArea = if (netBearing > 0) P / netBearing else P / 50.0
        val eccentricityFactor = if (ex > 0 || ey > 0) 1.3 else 1.0
        val requiredArea = baseArea * eccentricityFactor

        // Footing dimensions (square or rectangular)
 val B = if (colW >= colL) sqrt(requiredArea * colL / colW) else sqrt(requiredArea)
        val L = requiredArea / B

        // Feasibility checks
        val maxDim = maxOf(B, L)
        val edgeDist = input.adjacentFoundationDistance - maxDim / 2
        val isFeasible = netBearing > 0 && maxDim <= 4.0 && edgeDist >= 0.5

        // Dimensions description
        val dimDesc = "${"%.2f".format(B)}m x ${"%.2f".format(L)}m x ${"%.2f".format(Df)}m deep"

        // Quantities
        val thickness = maxOf(Df + 0.15, maxOf(B, L) * 0.15, 0.3)
        val concreteVol = B * L * thickness
        val steelRatio = 0.008 // 0.8% average
        val steelWeight = concreteVol * 7850.0 * steelRatio // kg
        val excavationVol = B * L * (Df + 0.1)

        // Settlement
        val Es = soilModulus[input.soilType] ?: 25000.0
        val settlement = if (Es > 0) (P / Es) * (B * L).pow(0.5) * 1000 * 0.8 else 50.0

        // Utilization
        val actualPressure = P / (B * L)
        val utilization = if (qall > 0) actualPressure / qall else 1.0

        val cost = estimateCost(concreteVol, steelWeight, excavationVol)

        return FoundationOption(
            type = FoundationType.ISOLATED_FOOTING,
            isFeasible = isFeasible,
            requiredArea = B * L,
            foundationDimensions = dimDesc,
            estimatedConcreteVolume = concreteVol,
            estimatedSteelWeight = steelWeight,
            estimatedExcavationVolume = excavationVol,
            estimatedCost = cost,
            advantages = listOf(
                "Simple design and construction",
                "Most economical for moderate loads",
                "Easy quality control",
                "No special equipment needed"
            ),
            disadvantages = listOf(
                "Not suitable for high loads on weak soil",
                "Differential settlement possible",
                "Limited to small eccentricities"
            ),
            settlement = settlement,
            utilizationRatio = utilization
        )
    }

    private fun evaluateCombinedFooting(input: FoundationComparisonInput): FoundationOption {
        val P1 = input.columnLoad
        val P2 = input.columnLoad * 0.8 // assume adjacent column has 80% load
        val totalLoad = P1 + P2
        val qall = input.allowableBearingPressure
        val Df = maxOf(input.minFoundationDepth, 1.0)
        val gamma = 20.0
        val netBearing = qall - gamma * Df
        val S = input.spacing

        // For combined footing, find centroid to balance loads
        val x1 = 0.0
        val x2 = S
        val centroid = (P1 * x1 + P2 * x2) / totalLoad
        val L = centroid * 2 + maxOf(input.columnWidth, input.columnLength) / 1000.0 + 0.5
        val B = if (netBearing > 0) totalLoad / (netBearing * L) else 4.0

        val isFeasible = netBearing > 0 && B <= 6.0 && L <= 12.0 && input.numberOfColumns >= 2

        val thickness = maxOf(0.4, L * 0.08)
        val concreteVol = B * L * thickness
        val steelWeight = concreteVol * 7850.0 * 0.012
        val excavationVol = B * L * (Df + 0.1)

        val Es = soilModulus[input.soilType] ?: 25000.0
        val settlement = if (Es > 0) (totalLoad / Es) * (B * L).pow(0.5) * 1000 * 0.5 else 30.0
        val utilization = if (qall > 0) (totalLoad / (B * L * qall)) else 1.0

        val cost = estimateCost(concreteVol, steelWeight, excavationVol) * 1.1 // 10% premium

        return FoundationOption(
            type = FoundationType.COMBINED_FOOTING,
            isFeasible = isFeasible,
            requiredArea = B * L,
            foundationDimensions = "${"%.2f".format(B)}m x ${"%.2f".format(L)}m x ${"%.2f".format(thickness)}m",
            estimatedConcreteVolume = concreteVol,
            estimatedSteelWeight = steelWeight,
            estimatedExcavationVolume = excavationVol,
            estimatedCost = cost,
            advantages = listOf(
                "Supports two columns on one footing",
                "Balances unbalanced loads",
                "Reduces differential settlement",
                "Uses column spacing effectively"
            ),
            disadvantages = listOf(
                "More complex design than isolated",
                "Requires careful centroid calculation",
                "Higher concrete volume",
                "More reinforcement needed"
            ),
            settlement = settlement,
            utilizationRatio = utilization
        )
    }

    private fun evaluatePileFoundation(input: FoundationComparisonInput): FoundationOption {
        val P = input.columnLoad
        val singlePileCap = pileCapacityBySoil[input.soilType] ?: 700.0
        val numPiles = maxOf(4, ceil(P / (singlePileCap * 0.8)).toInt())
        val actualCapPerPile = P / numPiles
        val utilization = actualCapPerPile / singlePileCap

        // Pile dimensions
        val pileDia = when {
            singlePileCap < 300 -> 0.4
            singlePileCap < 800 -> 0.5
            singlePileCap < 1500 -> 0.6
            else -> 0.75
        }
        val pileLength = when (input.soilType) {
            "very_soft", "soft" -> 20.0
            "medium" -> 15.0
            "stiff", "hard" -> 12.0
            "rock" -> 6.0
            else -> 15.0
        }

        // Pile cap
        val pileSpacing = 3.0 * pileDia
        val cols = ceil(sqrt(numPiles.toDouble())).toInt()
        val rows = ceil(numPiles.toDouble() / cols).toInt()
        val capWidth = (cols - 1) * pileSpacing + 2 * pileDia + 0.1
        val capLength = (rows - 1) * pileSpacing + 2 * pileDia + 0.1
        val capThickness = maxOf(0.6, capWidth * 0.2)

        // Always feasible if soil is weak enough
        val isFeasible = input.allowableBearingPressure < 200 || P > 3000

        // Quantities
        val pileConcrete = numPiles * PI * (pileDia / 2).pow(2) * pileLength
        val capConcrete = capWidth * capLength * capThickness
        val totalConcrete = pileConcrete + capConcrete
        val steelRatio = 0.02
        val steelWeight = (pileConcrete * 7850 * 0.01 + capConcrete * 7850 * steelRatio)
        val excavationVol = capWidth * capLength * (capThickness + 0.3) + numPiles * PI * (pileDia / 2).pow(2) * 0.5

        // Settlement
        val settlement = when (input.soilType) {
            "very_soft", "soft" -> 15.0
            "medium" -> 8.0
            "stiff" -> 4.0
            "hard", "rock" -> 2.0
            else -> 10.0
        }

        val cost = estimateCost(totalConcrete, steelWeight, excavationVol) * 2.5 // 2.5x premium for piling

        return FoundationOption(
            type = FoundationType.PILE_FOUNDATION,
            isFeasible = isFeasible,
            requiredArea = capWidth * capLength,
            foundationDimensions = "${numPiles}x ⌀${"%.0f".format(pileDia * 1000)}mm x ${"%.1f".format(pileLength)}m, Cap: ${"%.2f".format(capWidth)}x${"%.2f".format(capLength)}x${"%.2f".format(capThickness)}m",
            estimatedConcreteVolume = totalConcrete,
            estimatedSteelWeight = steelWeight,
            estimatedExcavationVolume = excavationVol,
            estimatedCost = cost,
            advantages = listOf(
                "Suitable for weak soils",
                "High load capacity",
                "Minimal settlement",
                "Proven track record"
            ),
            disadvantages = listOf(
                "Most expensive option",
                "Requires specialized equipment",
                "Noise and vibration during driving",
                "Quality control challenges underground"
            ),
            settlement = settlement,
            utilizationRatio = utilization
        )
    }

    private fun evaluateRaftFoundation(input: FoundationComparisonInput): FoundationOption {
        val totalLoad = input.columnLoad * input.numberOfColumns
        val qall = input.allowableBearingPressure
        val Df = maxOf(input.minFoundationDepth, 0.75)
        val gamma = 20.0
        val netBearing = qall - gamma * Df

        // Building footprint (approximate)
 val buildingWidth = (input.numberOfColumns - 1) * input.spacing + input.columnLength / 1000.0 + 2.0
        val buildingLength = buildingWidth * 1.2
        val raftArea = if (netBearing > 0) totalLoad / netBearing else totalLoad / 50.0
        val coverageRatio = raftArea / (buildingWidth * buildingLength)

        val B = maxOf(buildingWidth, sqrt(raftArea))
        val L = raftArea / B
        val thickness = maxOf(0.3, B * 0.08, 0.5)

        // Feasible when isolated footings would cover > 50% of footprint
        val isolatedArea = if (netBearing > 0) input.columnLoad / netBearing else 10.0
        val totalIsolatedArea = isolatedArea * input.numberOfColumns
        val isFeasible = coverageRatio >= 0.5 || input.numberOfColumns >= 4 || qall < 100

        val concreteVol = B * L * thickness
        val steelWeight = concreteVol * 7850.0 * 0.015
        val excavationVol = B * L * (Df + 0.1)

        val Es = soilModulus[input.soilType] ?: 25000.0
        val settlement = if (Es > 0) (totalLoad / Es) * (B * L).pow(0.5) * 1000 * 0.3 else 20.0
        val utilization = if (qall > 0) (totalLoad / (B * L * qall)) else 1.0

        val cost = estimateCost(concreteVol, steelWeight, excavationVol) * 1.3

        return FoundationOption(
            type = FoundationType.RAFT,
            isFeasible = isFeasible,
            requiredArea = B * L,
            foundationDimensions = "${"%.2f".format(B)}m x ${"%.2f".format(L)}m x ${"%.2f".format(thickness)}m",
            estimatedConcreteVolume = concreteVol,
            estimatedSteelWeight = steelWeight,
            estimatedExcavationVolume = excavationVol,
            estimatedCost = cost,
            advantages = listOf(
                "Uniform settlement distribution",
                "Supports all columns on one slab",
                "Good for variable soil conditions",
                "Can accommodate basements"
            ),
            disadvantages = listOf(
                "High concrete volume",
                "Complex reinforcement layout",
                "May need thickened sections under columns",
                "Higher formwork cost"
            ),
            settlement = settlement,
            utilizationRatio = utilization
        )
    }

    private fun evaluateStripFooting(input: FoundationComparisonInput): FoundationOption {
        val P = input.columnLoad
        val qall = input.allowableBearingPressure
        val Df = maxOf(input.minFoundationDepth, 0.6)
        val gamma = 20.0
        val netBearing = qall - gamma * Df
        val wallLength = input.spacing
        val loadPerMeter = P / wallLength

        val B = if (netBearing > 0) loadPerMeter / netBearing else 3.0
        val thickness = maxOf(0.25, B * 0.3)

        val isFeasible = B <= 2.5 && B >= 0.5 && input.numberOfColumns >= 2 && netBearing > 0

        val concreteVol = B * wallLength * thickness
        val steelWeight = concreteVol * 7850.0 * 0.005
        val excavationVol = B * wallLength * (Df + 0.1)

        val Es = soilModulus[input.soilType] ?: 25000.0
        val settlement = if (Es > 0) (loadPerMeter / Es) * B.pow(0.5) * 1000 else 25.0
        val utilization = if (qall > 0) loadPerMeter / (B * qall) else 1.0

        val cost = estimateCost(concreteVol, steelWeight, excavationVol) * 0.9

        return FoundationOption(
            type = FoundationType.STRIP_FOOTING,
            isFeasible = isFeasible,
            requiredArea = B * wallLength,
            foundationDimensions = "${"%.2f".format(B)}m wide x ${"%.1f".format(wallLength)}m long x ${"%.2f".format(thickness)}m deep",
            estimatedConcreteVolume = concreteVol,
            estimatedSteelWeight = steelWeight,
            estimatedExcavationVolume = excavationVol,
            estimatedCost = cost,
            advantages = listOf(
                "Simple and economical for wall loads",
                "Continuous support reduces differential settlement",
                "Easy to construct",
                "Minimal reinforcement needed"
            ),
            disadvantages = listOf(
                "Only suitable for continuous wall loads",
                "Not efficient for point loads",
                "Limited width capacity",
                "Requires wall to be load-bearing"
            ),
            settlement = settlement,
            utilizationRatio = utilization
        )
    }

    private fun estimateCost(concreteVol: Double, steelWeight: Double, excavationVol: Double): Double {
        val concreteCost = concreteVol * CONCRETE_COST_PER_M3
        val steelCost = (steelWeight / 1000.0) * STEEL_COST_PER_TON
        val excavationCost = excavationVol * EXCAVATION_COST_PER_M3
        return concreteCost + steelCost + excavationCost
    }

    /**
     * Quick soil bearing estimate from SPT N-value
     */
    fun estimateBearingFromSPT(nValue: Int, foundationWidth: Double, foundationDepth: Double): Double {
        // Meyerhof's formula: qall = N/K * B/Df * (for B <= 1.2m)
        // For B > 1.2m: qall = N/K * 3.28 * (B + 0.3)² / B * Df_factor
        val K = when {
            nValue < 10 -> 4.0
            nValue < 30 -> 3.0
            nValue < 50 -> 2.0
            else -> 1.5
        }
        val widthFactor = if (foundationWidth <= 1.2) {
            foundationWidth
        } else {
            3.28 * (foundationWidth + 0.3).pow(2) / foundationWidth
        }
        val depthFactor = 1.0 + 0.2 * foundationDepth
        return (nValue.toDouble() / K) * widthFactor * depthFactor // kN/m2
    }

    /**
     * Estimate pile capacity from SPT N-value (simplified)
     */
    fun estimatePileCapacityFromSPT(
        nAverage: Int, pileDiameter: Double, pileLength: Double, pileType: String = "bored"
    ): Double {
        // Q = 400 * N * Ap + 2 * N * As  (for driven piles, kN)
        // For bored: reduce by 0.6
        val factor = if (pileType == "driven") 1.0 else 0.6
        val Ap = PI * (pileDiameter / 2000.0).pow(2) // m2 (end area)
        val As = PI * (pileDiameter / 1000.0) * pileLength // m2 (shaft area)
        return (400.0 * nAverage * Ap + 2.0 * nAverage * As) * factor // kN
    }
}
