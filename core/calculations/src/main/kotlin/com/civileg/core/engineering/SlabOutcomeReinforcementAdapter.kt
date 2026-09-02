package com.civileg.core.engineering

/**
 * Directional slab reinforcement, fed to the DrawingModel slab builder.
 *
 * A slab needs four directional groups (short/long × top/bottom), unlike the
 * single-group beam/column contract — hence a dedicated slim result type
 * instead of reusing [com.civileg.core.calculations.entities.ReinforcementResult].
 * Everything is derived from [UnifiedSlabDesign.Outcome] (no recomputation).
 */
data class SlabReinforcementResult(
    val shortBarSelection: Pair<Int, Double>,   // (n, Ø) per metre, short dir
    val longBarSelection: Pair<Int, Double>,    // (n, Ø) per metre, long dir
    val shortSpacingMm: Double,
    val longSpacingMm: Double,
    val isSafe: Boolean,
    val warnings: List<String>
)

/**
 * Thin mapping from the unified two-way slab outcome onto the drawing-model
 * contract. The bar selections decided by the shared flexure engine (per 1 m
 * strip) are read as-is and converted to a centre-to-centre spacing
 * (s = 1000/n). The outcome's sanity warnings are carried through.
 */
fun UnifiedSlabDesign.Outcome.toSlabReinforcement(): SlabReinforcementResult {
    val (ns, ds) = parseBarSelection(shortDir.bars)
    val (nl, dl) = parseBarSelection(longDir.bars)
    return SlabReinforcementResult(
        shortBarSelection = ns to ds,
        longBarSelection = nl to dl,
        shortSpacingMm = 1000.0 / ns,
        longSpacingMm = 1000.0 / nl,
        isSafe = isSafe,
        warnings = sanity.warnings
    )
}