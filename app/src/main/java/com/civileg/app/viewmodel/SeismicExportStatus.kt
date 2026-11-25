package com.civileg.app.viewmodel

import com.civileg.app.domain.calculations.base.SeismicBaseShearResult
import com.civileg.app.domain.entities.GenericSafetyCheck

/**
 * What a seismic export may say. The engines compute base shear and an equivalent-SDOF drift ESTIMATE
 * (ECPSeismic, SBCSeismic, ACISeismic). That estimate and its 0.005h reference are not a verified code
 * drift model, so the export never claims a drift pass or a drift failure. The report is always
 * PRELIMINARY, and the archived flag is never a pass.
 */
internal object SeismicExportStatus {
    /** Never a verified failure: the drift limit is not a verified code check. */
    @Suppress("UNUSED_PARAMETER")
    fun driftFailed(r: SeismicBaseShearResult): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun isSafe(r: SeismicBaseShearResult): Boolean = true

    @Suppress("UNUSED_PARAMETER")
    fun assessmentPending(r: SeismicBaseShearResult): Boolean = true

    /** An archived design is never stored as a verified pass. */
    @Suppress("UNUSED_PARAMETER")
    fun archivedIsSafe(r: SeismicBaseShearResult): Boolean = false

    /** No drift pass/fail row. The estimate is shown as an indicative number only, never as a check. */
    @Suppress("UNUSED_PARAMETER")
    fun checks(r: SeismicBaseShearResult): List<GenericSafetyCheck> = emptyList()
}
