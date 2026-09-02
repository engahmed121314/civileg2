package com.civileg.core.engineering

/**
 * Stair reinforcement, fed to the DrawingModel stair builder.
 *
 * The waist-slab bar layout differs from the beam/column contract (two families
 * of mesh, spacing-based rather than count-based), so a dedicated slim result
 * type is used instead of [com.civileg.core.calculations.entities.ReinforcementResult].
 * Everything is derived from [UnifiedStairDesign.Outcome]; nothing is recomputed
 * (spec §3): the bar strings decided by the engine ("Ø12 @ 150 mm c/c") are
 * parsed back into (diameter, spacing) — the exact inverse of the engine's own
 * formatter, NOT an independent design choice — and the outcome's sanity
 * warnings are carried through so nothing is silently lost.
 */
data class StairReinforcementResult(
    val mainDiameter: Double,          // mm
    val mainSpacingMm: Double,         // centre-to-centre along the flight
    val distributionDiameter: Double,  // mm
    val distributionSpacingMm: Double, // centre-to-centre across the width
    val isSafe: Boolean,
    val warnings: List<String>
)

/**
 * Thin mapping from the unified staircase outcome onto the drawing-model
 * contract. The bar/selections decided by the engine are read as-is; the
 * outcome's own sanity warnings feed the model's warning surface.
 */
fun UnifiedStairDesign.Outcome.toStairReinforcement(): StairReinforcementResult {
    val (mainDia, mainSpacing) = parseStairBar(mainRebar)
    val (distDia, distSpacing) = parseStairBar(distributionRebar)
    return StairReinforcementResult(
        mainDiameter = mainDia,
        mainSpacingMm = mainSpacing,
        distributionDiameter = distDia,
        distributionSpacingMm = distSpacing,
        isSafe = isSafe,
        warnings = sanity.warnings
    )
}

/**
 * Reverse of the engine's "Ød @ s mm c/c" formatter. Throws on a malformed bar
 * string (rule 1.4) — the adapter must never guess.
 */
private fun parseStairBar(barString: String): Pair<Double, Double> {
    if (barString.contains("None")) return 0.0 to 0.0
    try {
        val parts = barString.split("Ø", " @ ")
        if (parts.size >= 3) {
            val dia = parts[1].trim().split(" ")[0].toDouble()
            val spacing = parts[2].trim().split(" ")[0].toDouble()
            if (dia > 0 && spacing > 0) return dia to spacing
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid stair bar notation '$barString' | صيغة تسليح السلم غير مفهومة", e)
    }
    throw IllegalArgumentException("Invalid stair bar notation '$barString' | صيغة تسليح السلم غير مفهومة")
}