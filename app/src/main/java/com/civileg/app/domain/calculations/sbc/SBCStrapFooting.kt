package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.StrapFootingDesign
import com.civileg.core.engineering.StrapFootingDesignEngine

class SBCStrapFooting : StrapFootingDesign {
    override fun design(inputs: StrapFootingDesignEngine.Inputs): StrapFootingDesignEngine.Result {
        // ── InputGuard: loud failures, no silent zeros (ADR-010) ──
        InputGuard.notNull("inputs", inputs)

        return StrapFootingDesignEngine.design(inputs)
    }
}
