package com.civileg.app.viewmodel

import com.civileg.app.domain.calculations.base.SeismicBaseShearResult
import com.civileg.app.domain.entities.GenericSafetyCheck

/**
 * What a seismic export may say. The tool computes base shear and an equivalent-SDOF drift estimate. It
 * does not check the whole building, so the report is PRELIMINARY unless the drift check failed. A failed
 * drift check is always kept as a failure. A drift check that was never computed is not a pass.
 */
internal object SeismicExportStatus {
    fun driftFailed(r: SeismicBaseShearResult): Boolean = r.driftAssessed && !r.driftIsSafe

    /** Overall flag for the report: false only for a known failure, with pending set for every other case. */
    fun isSafe(r: SeismicBaseShearResult): Boolean = !driftFailed(r)

    fun assessmentPending(r: SeismicBaseShearResult): Boolean = !driftFailed(r)

    /** The flag stored with an archived design: true only when drift was computed and passed. */
    fun archivedIsSafe(r: SeismicBaseShearResult): Boolean = r.driftAssessed && r.driftIsSafe && r.warnings.isEmpty()

    fun checks(r: SeismicBaseShearResult): List<GenericSafetyCheck> =
        if (!r.driftAssessed) emptyList()
        else listOf(GenericSafetyCheck("Story drift", r.maxStoryDrift, r.allowableDrift, "mm", r.driftIsSafe))
}
