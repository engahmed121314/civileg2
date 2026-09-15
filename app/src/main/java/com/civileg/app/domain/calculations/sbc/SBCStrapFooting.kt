package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.StrapFootingDesign
import com.civileg.core.engineering.StrapFootingDesignEngine

class SBCStrapFooting : StrapFootingDesign {
    override fun design(inputs: StrapFootingDesignEngine.Inputs): StrapFootingDesignEngine.Result {
        return StrapFootingDesignEngine.design(inputs)
    }
}
