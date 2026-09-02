package com.civileg.core.engineering

import com.civileg.core.calculations.entities.ReinforcementResult
import com.civileg.core.calculations.entities.StirrupZone
import kotlin.math.min

/**
 * Thin, auditable mapping from the unified beam facade outcome onto the
 * DrawingModel [ReinforcementResult] contract (Pillar 2 "feeds" link).
 *
 * A deliberate adapter: NO formula is repeated. The bar selection decided by
 * [UnifiedBeamFlexure] (`bars`, the single producer of "nØd") and the stirrup
 * sizing decided by [UnifiedBeamShear] are read as-is, and the facade's own
 * sanity warnings are carried through so nothing is silently lost.
 *
 * Stirrup zones (R2/P044): when the geometry is supplied ([hMm]/[dMm]/[spanMm]
 * all > 0) the adapter emits the SAME three confinement zones the live engine
 * produces — Support Left 0..min(2h,L/4), Mid, Support Right — so the facade
 * BBS shows the dense-support / relaxed-mid distribution too. This is pure
 * layout, Pillar-2 safe:
 *  - the confinement support spacing IS [UnifiedBeamShear.Outcome.spacingMm]
 *    (the engine's single critical-section value, passed through verbatim,
 *    never recomputed);
 *  - the relaxed mid span uses the engine's own relaxation rule
 *    `min(200, sup·1.5).coerceAtLeast(min(200, d/2))` (CalculatorEngine §R2),
 *    generalised to `maxOf(sup, ...)` so the mid zone is never DENSER than the
 *    support band (matters only when a facade code cap already exceeds 200 mm);
 *    a placement policy, not a strength formula;
 *  - legs stay 2 because [UnifiedBeamShear] derives every value from a 2-leg
 *    stirrup.
 * When geometry is absent (defaults left at 0) the zones stay empty and
 * [buildBeam] keeps the conservative uniform densest fallback — e.g. any
 * caller that only has the outcome, not the member size.
 */
fun BeamDesignFacade.BeamOutcome.toReinforcementResult(
    hMm: Double = 0.0,
    dMm: Double = 0.0,
    spanMm: Double = 0.0
): ReinforcementResult {
    val (n, dia) = parseBarSelection(flexure.bars)
    val zones = if (hMm > 0.0 && dMm > 0.0 && spanMm > 0.0) {
        beamStirrupZones(
            spanMm = spanMm, hMm = hMm, dMm = dMm,
            supportSpacing = shear.spacingMm, diameter = shear.stirrupDiaMm
        )
    } else emptyList()
    return ReinforcementResult(
        astRequired = flexure.asRequiredMm2,
        astProvided = flexure.asProvidedMm2,
        barDiameter = dia,
        numberOfBars = n,
        tiesDiameter = shear.stirrupDiaMm,
        tiesSpacing = shear.spacingMm,
        numLegs = 2,
        zones = zones,
        isSafe = isSafe,
        utilizationRatio = shear.utilization,
        warnings = sanity.warnings
    )
}

/**
 * Build the engine-style 3‑zone confinement layout. Pure placement: the
 * support spacing is the engine's critical-section value passed through
 * verbatim; the mid spacing is the engine's own relaxation rule; the zone
 * band follows the engine's `min(2h, L/4)` confinement length.
 */
internal fun beamStirrupZones(
    spanMm: Double,
    hMm: Double,
    dMm: Double,
    supportSpacing: Double,
    diameter: Double
): List<StirrupZone> {
    val confinementLength = min(2 * hMm, spanMm / 4.0)
    // Engine relaxation rule (CalculatorEngine §R2), generalised so the result
    // is never DENSER than the support band. For an engine-style support
    // spacing (≤ min(200, d/2)) this reduces exactly to the engine's own
    // `min(200, s·1.5).coerceAtLeast(min(200, d/2))`; when a facade's code cap
    // already exceeds 200 mm (e.g. ACI s_max = d/2 = 225) it keeps ONE spacing
    // across the member instead of emitting an inverted mid < support.
    val relaxed = min(200.0, supportSpacing * 1.5).coerceAtLeast(min(200.0, dMm / 2.0))
    val midSpacing = maxOf(supportSpacing, relaxed)
    val dia = diameter.toInt()
    val shape = "2-Leg (Closed 135°)"
    fun desc(s: Double) = "\u00D8${dia} @ ${s.toInt()} mm c/c · $shape"
    return listOf(
        StirrupZone("Support Zone (Left)", 0.0, confinementLength, supportSpacing, 2, dia, desc(supportSpacing)),
        StirrupZone("Mid-Span Zone", confinementLength, spanMm - confinementLength, midSpacing, 2, dia, desc(midSpacing)),
        StirrupZone("Support Zone (Right)", spanMm - confinementLength, spanMm, supportSpacing, 2, dia, desc(supportSpacing))
    )
}

/**
 * Parse the "nØd" bar selection emitted by [RebarTable.select] (used by both
 * the beam flexure engine and the column engine) into (count, diameter).
 * Strict on purpose: the upstream format is fixed, so any deviation is a real
 * breakage, not something to guess around. Shared (`internal`) by the beam and
 * column adapters in this package.
 */
internal fun parseBarSelection(bars: String): Pair<Int, Double> {
    val m = Regex("^(\\d+)Ø(\\d+(?:\\.\\d+)?)$").matchEntire(bars.trim())
        ?: error("toReinforcementResult: unexpected bar selection '$bars' (expected 'nØd' from UnifiedBeamFlexure)")
    return m.groupValues[1].toInt() to m.groupValues[2].toDouble()
}