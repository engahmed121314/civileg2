package com.civileg.app.domain.calculations.base

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unassessed is not a pass, and an approximate FAILING ratio must still fail. */
class HordiDeemedSeparationTest {
    @Test fun undeclaredInputsAreNotEvaluated() {
        val r = HordiDeemedToSatisfy.sbc(4000.0, 250.0, 420.0, SlabSupportCondition.UNSPECIFIED, DeflectionLoadingApplicability.UNDECLARED)
        assertFalse(r.evaluated)
    }
    @Test fun approximateFailingRatioStaysEvaluatedAndFailing() {
        val r = HordiDeemedToSatisfy.sbc(9000.0, 150.0, 420.0, SlabSupportCondition.SIMPLY_SUPPORTED, DeflectionLoadingApplicability.ORDINARY_NO_SENSITIVE_ELEMENTS)
        assertTrue(r.evaluated)
        assertFalse(r.safe)
    }
}
