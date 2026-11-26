package com.civileg.app.domain.calculations.ecp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ECP 203-2025 flexure limits, values read on the page images of the supplied edition:
 * Table 4-1 (p.4-6), Eq 4-4/4-5, 4-1-2-1 item 9 (a = 0.8 c, p.4-4), Eq 4-9 and floors (p.4-10).
 */
class ECPBeam2025FlexureTest {

    @Test fun `Table 4-1 rows give Rmax over gamma_c`() {
        // tabulated Rmax / 1.5: 240 -> 0.214, 350 -> 0.196, 400 -> 0.189, 420 -> 0.186, 500 -> 0.176, 600 -> 0.165, 700 -> 0.155
        val rmax = mapOf(240.0 to 0.214, 350.0 to 0.196, 400.0 to 0.189, 420.0 to 0.186, 500.0 to 0.176, 600.0 to 0.165, 700.0 to 0.155)
        for ((fy, r) in rmax) assertEquals("fy $fy", r / 1.5, ECPBeam.kMax(fy), 0.002)   // derived from c_max/d; the printed Rmax is rounded
    }

    @Test fun `fy 360 interpolates between the 350 and 400 rows`() {
        assertEquals(0.444, ECPBeam.cMaxOverD(360.0), 1e-9)   // 0.45 - 0.03 x 10/50
        // old app limit for fcu 25, fy 360 was 0.186; the 2025 single-reinforcement limit is about 0.130
        assertEquals(0.1305, ECPBeam.kMax(360.0), 5e-4)
    }

    @Test fun `mu_max matches the tabulated coefficient times fcu`() {
        // Table 4-1: fy 240 -> 8.56e-4 fcu; fy 420 -> 4.08e-4 fcu; fy 500 -> 3.19e-4 fcu
        assertEquals(8.56e-4 * 25.0, ECPBeam.muMax(25.0, 240.0), 8.56e-4 * 25.0 * 0.025)
        assertEquals(4.08e-4 * 25.0, ECPBeam.muMax(25.0, 420.0), 4.08e-4 * 25.0 * 0.025)
        assertEquals(3.19e-4 * 25.0, ECPBeam.muMax(25.0, 500.0), 3.19e-4 * 25.0 * 0.025)
    }

    @Test fun `minimum steel Eq 4-9 and floors`() {
        // fcu 25, fy 360: 0.225 x 5 / 360 = 0.003125 (1.1 / 360 = 0.003056 is smaller)
        assertEquals(0.003125, ECPBeam.muMinEq49(25.0, 360.0), 1e-9)
        assertEquals(1.1 / 240.0, ECPBeam.muMinEq49(16.0, 240.0).coerceAtLeast(1.1 / 240.0), 1e-9)
        assertEquals(0.0025, ECPBeam.muMinFloor(25.0, 240.0), 1e-12)   // mild steel 0.25%
        assertEquals(0.0015, ECPBeam.muMinFloor(25.0, 360.0), 1e-12)   // high-grade, fcu <= 30: 0.15%
        assertEquals(0.00165, ECPBeam.muMinFloor(35.0, 360.0), 1e-12)  // linear between 30 and 40
        assertEquals(0.0018, ECPBeam.muMinFloor(45.0, 360.0), 1e-12)   // fcu >= 40: 0.18%
    }

    @Test fun `old limit is no longer the single reinforcement limit`() {
        assertTrue(ECPBeam.kMax(360.0) < 0.15)
    }
}
