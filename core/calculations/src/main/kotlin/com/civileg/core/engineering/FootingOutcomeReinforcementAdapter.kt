package com.civileg.core.engineering

/**
 * Isolated-footing reinforcement, fed to the DrawingModel footing builder.
 *
 * Carries both directional (bottom) selections — short/long × per metre —
 * plus the distribution (top) mesh. Everything is derived from the
 * [UnifiedFootingDesign.Outcome]; nothing is recomputed (spec §3): the bar
 * selections decided by the engine are read as-is, spacings are the engine's
 * own [FootingDirectionalReinf.spacingMm], and the outcome's sanity warnings
 * are carried through so nothing is silently lost.
 */
data class FootingReinforcementResult(
    val shortBarSelection: Pair<Int, Double>,   // (n, Ø) per metre — bottom, short dir
    val longBarSelection: Pair<Int, Double>,    // (n, Ø) per metre — bottom, long dir
    val shortSpacingMm: Double,
    val longSpacingMm: Double,
    val bottomAsProvided: Double,
    val distribution: FootingDistributionSteel?,   // top mesh (null when not required)
    val isSafe: Boolean,
    val warnings: List<String>
)

/**
 * Thin mapping from the unified footing outcome onto the drawing-model
 * contract. The bar selections and spacings decided by the engine are read
 * as-is (the engine already produced [FootingDirectionalReinf] quantities);
 * the outcome's own sanity warnings feed the model's warning surface.
 */
fun UnifiedFootingDesign.Outcome.toFootingReinforcement(): FootingReinforcementResult {
    val distribution = if (distribution.barsPerMeter > 0) distribution else null
    return FootingReinforcementResult(
        shortBarSelection = shortDir.barsPerMeter to shortDir.barDiameter,
        longBarSelection = longDir.barsPerMeter to longDir.barDiameter,
        shortSpacingMm = shortDir.spacingMm,
        longSpacingMm = longDir.spacingMm,
        bottomAsProvided = shortDir.astProvided + longDir.astProvided,
        distribution = distribution,
        isSafe = isSafe,
        warnings = sanity.warnings
    )
}