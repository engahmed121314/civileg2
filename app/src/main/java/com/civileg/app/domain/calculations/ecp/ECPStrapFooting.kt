package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.StrapFootingDesign
import com.civileg.core.engineering.StrapFootingDesignEngine

class ECPStrapFooting : StrapFootingDesign {
    override fun design(inputs: StrapFootingDesignEngine.Inputs): StrapFootingDesignEngine.Result {
        // ── InputGuard (ADR-010) — ECP 203 ──
        InputGuard.notNull("inputs", inputs)
        return StrapFootingDesignEngine.design(inputs)
    }
}
