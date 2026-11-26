package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.SupportCondition
import com.civileg.app.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins serviceability values to ECP 203-2025: Eq 4-74/4-75 (p.4-63) and Table 4-12 (p.4-69). */
class ECP2025ServiceabilityTest {
    private val beam = ECPBeam()

    @Test
    fun cantileverDeflectionLimitIsSpanOver450_Eq4_75() {
        val r = beam.checkDeflection(2.0, 400.0, 0.01, SupportCondition.CANTILEVER, 420.0)
        assertEquals(2000.0 / 450.0, r.allowableDeflection, 1e-9)
    }

    @Test
    fun simpleBeamDeflectionLimitIsSpanOver250_Eq4_74() {
        val r = beam.checkDeflection(5.0, 500.0, 0.01, SupportCondition.SIMPLY_SUPPORTED, 420.0)
        assertEquals(5000.0 / 250.0, r.allowableDeflection, 1e-9)
    }

    @Test
    fun waterRetainingCrackLimitIsSection3_Table4_12() {
        assertEquals(0.15, Constants.CRACK_WIDTH_WATER_RETAINING, 0.0)
    }

    @Test
    fun cantileverSolidSlabRatioIsTable4_10() {
        assertEquals(150.0, ECPSlab().getMinSlabThickness(1500.0, SupportCondition.CANTILEVER), 1e-9)
    }
}
