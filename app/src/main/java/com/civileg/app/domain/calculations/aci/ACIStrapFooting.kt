package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.StrapFootingDesign
import com.civileg.core.engineering.StrapFootingDesignEngine

class ACIStrapFooting : StrapFootingDesign {
    override fun design(inputs: StrapFootingDesignEngine.Inputs): StrapFootingDesignEngine.Result {
        // ── InputGuard: loud failures, no silent zeros (ADR-010) ──
        InputGuard.notNull("inputs", inputs)
        // ACI uses 1.2D + 1.6L, handled via inputs factors
        return StrapFootingDesignEngine.design(inputs)
    }
}
