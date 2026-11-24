package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.AdvancedColumnResult
import com.civileg.app.domain.entities.AdvancedSlabResult
import com.civileg.app.domain.entities.SlabType

/** Outcome of the checks returned by an engine, not certification of its model or code basis. */
enum class DisplayedCheckStatus { PASSED, FAILED, INCOMPLETE }

/** A known failure wins over missing evidence. Missing required evidence can never pass. */
internal fun displayedCheckStatus(vararg requiredChecks: Boolean?): DisplayedCheckStatus = when {
    requiredChecks.any { it == false } -> DisplayedCheckStatus.FAILED
    requiredChecks.isEmpty() || requiredChecks.any { it == null } -> DisplayedCheckStatus.INCOMPLETE
    else -> DisplayedCheckStatus.PASSED
}

fun AdvancedColumnResult.displayedCheckStatus(
    requiresBiaxial: Boolean,
    requiresPunching: Boolean
): DisplayedCheckStatus = displayedCheckStatus(
    reinforcementResult.isSafe,
    if (requiresBiaxial) biaxialCheck?.isSafe else biaxialCheck?.isSafe ?: true,
    if (requiresPunching) punchingCheck?.isSafe else punchingCheck?.isSafe ?: true
)

fun AdvancedSlabResult.displayedCheckStatus(): DisplayedCheckStatus = displayedCheckStatus(
    flexureResult.isSafe,
    shearCheck.isSafe,
    deflectionCheck.isSafe,
    if (slabType is SlabType.FlatPlate || slabType is SlabType.FlatSlab) {
        punchingShearCheck?.isSafe
    } else punchingShearCheck?.isSafe ?: true,
    if (slabType is SlabType.PostTensioned) {
        postTensionCalculations?.isSafe
    } else postTensionCalculations?.isSafe ?: true
)
