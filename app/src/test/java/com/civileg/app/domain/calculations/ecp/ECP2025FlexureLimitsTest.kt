package com.civileg.app.domain.calculations.ecp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ECP2025FlexureLimitsTest {
    @Test fun `Table 4-1 c_max over d rows and interpolation`() {
        assertEquals(0.50, ECP2025FlexureLimits.cMaxOverD(240.0), 1e-9)
        assertEquals(0.41, ECP2025FlexureLimits.cMaxOverD(420.0), 1e-9)
        assertEquals(0.444, ECP2025FlexureLimits.cMaxOverD(360.0), 1e-9)
    }
    @Test fun `K max matches Rmax over gamma_c of Table 4-1`() {
        assertEquals(0.186 / 1.5, ECP2025FlexureLimits.kMax(420.0), 0.002)
        assertEquals(0.214 / 1.5, ECP2025FlexureLimits.kMax(240.0), 0.002)
        assertEquals(0.1305, ECP2025FlexureLimits.kMax(360.0), 5e-4)
        assertTrue("old balanced K of 0.186 at fy 360 was unconservative", ECP2025FlexureLimits.kMax(360.0) < 0.15)
    }
}
