package com.civileg.app.utils

import com.civileg.app.domain.calculations.base.StairAnalysisStatus

/** Single gate for every consumer (result card, drawing, quantities, save, PDF) of a stair result. */
object StairResultGate {
    /** A stair result may be presented as a design only when an analysis model exists for the case. */
    fun isDesignUsable(status: StairAnalysisStatus): Boolean = status != StairAnalysisStatus.UNSUPPORTED_CASE
}
