package com.civileg.core.engineering

import com.civileg.core.calculations.entities.ReinforcementResult

/**
 * Thin, auditable mapping from the unified column design outcome onto the
 * DrawingModel [ReinforcementResult] contract (Pillar 2 "feeds" link).
 *
 * Mirrors [BeamDesignFacade.BeamOutcome.toReinforcementResult]: NO formula is
 * repeated. The longitudinal bar selection decided by [UnifiedColumnDesign]
 * (`bars`, from [RebarTable.select]) and the ties sizing are read as-is, and
 * the outcome's own sanity warnings are carried through — nothing silently lost.
 *
 * No tie zones are emitted here (same policy as the beam adapter): the unified
 * engines produce a single tie spacing, so the facade path falls back to a
 * uniform layout. The real per-zone tie layout lives in the app-side
 * ColumnDetailingEngine (P018/P028), not in this core mapping.
 */
fun UnifiedColumnDesign.Outcome.toReinforcementResult(): ReinforcementResult {
    val (n, dia) = parseBarSelection(bars)
    return ReinforcementResult(
        astRequired = asRequiredMm2,
        astProvided = asProvidedMm2,
        barDiameter = dia,
        numberOfBars = n,
        tiesDiameter = tieDiameterMm,
        tiesSpacing = tieSpacingMm,
        numLegs = 2,
        isSafe = isSafe,
        utilizationRatio = utilization,
        warnings = sanity.warnings
    )
}