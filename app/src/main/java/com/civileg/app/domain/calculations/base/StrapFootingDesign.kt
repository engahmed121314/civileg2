package com.civileg.app.domain.calculations.base

import com.civileg.core.engineering.StrapFootingDesignEngine

/**
 * Interface for Strap Footing design across different codes.
 */
interface StrapFootingDesign {
    fun design(inputs: StrapFootingDesignEngine.Inputs): StrapFootingDesignEngine.Result
}
