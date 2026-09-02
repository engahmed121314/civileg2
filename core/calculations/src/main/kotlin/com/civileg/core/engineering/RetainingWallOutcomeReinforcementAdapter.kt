package com.civileg.core.engineering

/**
 * Earth-retaining wall reinforcement, fed to the DrawingModel wall builder.
 *
 * A cantilever retaining wall has four bar families: stem main (vertical
 * flexure on the earth face), stem distribution (transverse), toe (bottom
 * flexure) and heel (top flexure). Everything is a pure passthrough of the
 * counts/diameters/spacings already computed by [UnifiedRetainingWallDesign]
 * — no parse, no recompute (spec §3). The outcome's own sanity warnings feed
 * the model's warning surface so nothing is silently lost.
 */
data class RetainingWallReinforcementResult(
    val stemMainCount: Int,                // bars over the 1 m run
    val stemMainDiameter: Double,          // mm
    val stemMainSpacingMm: Double,         // mm centre-to-centre
    val distributionBarsCount: Int,
    val distributionDiameter: Double,      // mm
    val distributionSpacingMm: Double,     // mm centre-to-centre
    val toeBarsCount: Int,
    val toeDiameter: Double,               // mm
    val toeSpacingMm: Double,              // mm centre-to-centre
    val heelBarsCount: Int,
    val heelDiameter: Double,              // mm
    val heelSpacingMm: Double,             // mm centre-to-centre
    val isSafe: Boolean,
    val warnings: List<String>
)

/**
 * Thin mapping from the unified wall outcome onto the drawing-model contract.
 * The engine has already arranged the bars (count + diameter + spacing for
 * every family); this adapter reads them as-is.
 */
fun UnifiedRetainingWallDesign.Outcome.toRetainingWallReinforcement(): RetainingWallReinforcementResult {
    return RetainingWallReinforcementResult(
        stemMainCount = stemMainRebarCount,
        stemMainDiameter = stemMainRebarDiameter,
        stemMainSpacingMm = stemMainRebarSpacingMm,
        distributionBarsCount = distributionBarsCount,
        distributionDiameter = distributionBarsDiameter,
        distributionSpacingMm = distributionSpacingMm,
        toeBarsCount = toeRebarCount,
        toeDiameter = toeRebarDiameter,
        toeSpacingMm = toeSpacingMm,
        heelBarsCount = heelRebarCount,
        heelDiameter = heelRebarDiameter,
        heelSpacingMm = heelSpacingMm,
        isSafe = isSafe,
        warnings = sanity.warnings
    )
}