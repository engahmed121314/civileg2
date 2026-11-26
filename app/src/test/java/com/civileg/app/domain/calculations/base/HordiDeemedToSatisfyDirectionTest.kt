package com.civileg.app.domain.calculations.base

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fy other than 420 MPa: ECP 203-2020 Eq. 4-81 multiplies the allowed Ln/t by (0.40 + fy/700),
 * while ACI 318M-14 / SBC 304-CR-2018 9.3.1.1.1 multiply the minimum depth h by (0.4 + fy/700),
 * so the allowed l/h is divided by it. Hand cases (simply supported, ordinary loading):
 *  fy 500: factor 1.1143. ECP limit 20 x 1.1143 = 22.29; ACI/SBC limit 16 / 1.1143 = 14.36.
 *  fy 360: factor 0.9143. ACI/SBC limit 16 / 0.9143 = 17.50.
 */
class HordiDeemedToSatisfyDirectionTest {
    private val ss = SlabSupportCondition.SIMPLY_SUPPORTED
    private val ok = DeflectionLoadingApplicability.ORDINARY_NO_SENSITIVE_ELEMENTS

    @Test
    fun `fy 500 ratio 16_7 passes ECP but fails ACI and SBC`() {
        assertTrue(HordiDeemedToSatisfy.ecp(5000.0, 300.0, 500.0, ss, ok).safe)
        assertFalse(HordiDeemedToSatisfy.aci(5000.0, 300.0, 500.0, ss, ok).safe)
        assertFalse(HordiDeemedToSatisfy.sbc(5000.0, 300.0, 500.0, ss, ok).safe)
    }

    @Test
    fun `fy 360 ratio 17 passes ACI and SBC`() {
        assertTrue(HordiDeemedToSatisfy.aci(5100.0, 300.0, 360.0, ss, ok).safe)
        assertTrue(HordiDeemedToSatisfy.sbc(5100.0, 300.0, 360.0, ss, ok).safe)
    }
}
