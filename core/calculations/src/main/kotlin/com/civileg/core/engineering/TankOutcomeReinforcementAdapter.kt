package com.civileg.core.engineering

/**
 * Tank reinforcement, fed to the DrawingModel tank builder.
 *
 * A water-retaining tank has three reinforcement families: wall vertical,
 * wall horizontal, and base. Everything is derived from
 * [UnifiedTankDesign.Outcome]; nothing is recomputed (spec §3): the engine
 * already filled the wall [com.civileg.core.calculations.entities.ReinforcementResult]
 * (barDiameter = vertical bars, tiesDiameter = horizontal bars, spacing =
 * vertical spacing, tiesSpacing = horizontal spacing) and the base
 * ReinforcementResult (barDiameter + spacing). The outcome's own sanity
 * warnings are carried through so nothing is silently lost.
 */
data class TankReinforcementResult(
    val wallDiameter: Double,              // mm — vertical bars
    val wallSpacingMm: Double,             // mm — vertical bars centre-to-centre
    val wallHorizontalDiameter: Double,    // mm — horizontal bars
    val wallHorizontalSpacingMm: Double,   // mm — horizontal bars centre-to-centre
    val baseDiameter: Double,              // mm
    val baseSpacingMm: Double,             // mm
    val isSafe: Boolean,
    val warnings: List<String>
)

/**
 * Thin mapping from the unified tank outcome onto the drawing-model contract.
 * The engine's already-structured ReinforcementResult fields are read as-is
 * (pure passthrough — no parsing, no recompute); the outcome's own sanity
 * warnings feed the model's warning surface.
 */
fun UnifiedTankDesign.Outcome.toTankReinforcement(): TankReinforcementResult {
    return TankReinforcementResult(
        wallDiameter = wallReinforcement.barDiameter,
        wallSpacingMm = wallReinforcement.spacing,
        wallHorizontalDiameter = wallReinforcement.tiesDiameter,
        wallHorizontalSpacingMm = wallReinforcement.tiesSpacing,
        baseDiameter = baseReinforcement.barDiameter,
        baseSpacingMm = baseReinforcement.spacing,
        isSafe = isSafe,
        warnings = sanity.warnings
    )
}