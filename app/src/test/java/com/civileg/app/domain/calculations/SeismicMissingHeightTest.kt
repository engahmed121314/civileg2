package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACISeismic
import com.civileg.app.domain.calculations.ecp.ECPSeismic
import com.civileg.app.domain.calculations.sbc.SBCSeismic
import com.civileg.app.domain.calculations.base.*
import org.junit.Assert.*
import org.junit.Test

class SeismicMissingHeightTest {
    private val engines: List<Pair<String, SeismicDesign>> =
        listOf("ECP" to ECPSeismic(), "SBC" to SBCSeismic(), "ACI" to ACISeismic())

    @Test fun missingHeight_isNotAssessed_noSubstitutedPeriod() {
        for ((n, e) in engines) {
            val r = e.calculateBaseShear(10000.0, e.getSeismicZones()[2], SoilType.C, 1.0, 5.0, 0.0)
            assertFalse("$n assessed", r.assessed)
            assertEquals("$n base shear", 0.0, r.baseShear, 0.0)
            assertEquals("$n period must not be a substituted value", 0.0, r.fundamentalPeriod, 0.0)
            assertFalse("$n drift", r.driftAssessed)
        }
    }

    // H = 20 m: 20^0.75 = 9.4574 and 20^0.9 = 14.8263 (hand: exp(0.75 ln 20), exp(0.9 ln 20))
    @Test fun suppliedHeight_periodMatchesHandComputedFormula() {
        val z = { e: SeismicDesign -> e.getSeismicZones()[2] }
        val ecp = ECPSeismic().let { it.calculateBaseShear(10000.0, z(it), SoilType.C, 1.0, 5.0, 20.0) }
        val sbc = SBCSeismic().let { it.calculateBaseShear(10000.0, z(it), SoilType.C, 1.0, 5.0, 20.0) }
        assertTrue(ecp.assessed && sbc.assessed)
        assertEquals(0.075 * 9.4574, ecp.fundamentalPeriod, 2e-3)  // 0.7093 s
        assertEquals(0.08 * 9.4574, sbc.fundamentalPeriod, 2e-3)   // 0.7566 s
    }
}
