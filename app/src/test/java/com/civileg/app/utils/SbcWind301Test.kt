package com.civileg.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hand values from SBC 301-CR-2018 Table 27-2 (p.253), Eq 27-1 (p.250), Figure 27-2 (p.255), 27.1.5.1 (p.249), 26.9 (p.238-239). */
class SbcWind301Test {
    @Test fun `Kz matches Table 27-2 rows`() {
        val tbl = listOf(
            Triple(SbcExposure.B, 10.0, 0.72), Triple(SbcExposure.B, 20.0, 0.88), Triple(SbcExposure.B, 40.0, 1.07), Triple(SbcExposure.B, 150.0, 1.56),
            Triple(SbcExposure.C, 10.0, 1.00), Triple(SbcExposure.C, 20.0, 1.16), Triple(SbcExposure.C, 40.0, 1.34), Triple(SbcExposure.C, 150.0, 1.77),
            Triple(SbcExposure.D, 10.0, 1.18), Triple(SbcExposure.D, 20.0, 1.33), Triple(SbcExposure.D, 40.0, 1.50), Triple(SbcExposure.D, 150.0, 1.89),
            Triple(SbcExposure.B, 3.0, 0.59), Triple(SbcExposure.C, 3.0, 0.86), Triple(SbcExposure.D, 3.0, 1.04)
        )
        for ((e, z, k) in tbl) assertEquals("$e z=$z", k, SbcWind301.kz(e, z), 0.006)
    }

    @Test fun `velocity pressure Eq 27-1`() {
        // exposure C, z = 10 m, V = 40 m/s, Kzt 1: 0.613 x 1.0004 x 0.85 x 1600 = 834 N/m2
        assertEquals(0.834, SbcWind301.qz(SbcExposure.C, 10.0, 40.0), 0.002)
    }

    @Test fun `leeward and side Cp from Figure 27-2`() {
        assertEquals(-0.5, SbcWind301.cpLeeward(0.7), 1e-9)
        assertEquals(-0.3, SbcWind301.cpLeeward(2.0), 1e-9)
        assertEquals(-0.2, SbcWind301.cpLeeward(5.0), 1e-9)
        assertEquals(-0.4, SbcWind301.cpLeeward(1.5), 1e-9)
    }

    @Test fun `rigid building uses G 0_85 and base shear obeys the 0_8 kN per m2 wall minimum`() {
        val r = WindLoadCalculator().calculate(WindLoadInput(basicWindSpeed = 20.0, terrainCategory = TerrainCategory.SUBURBAN,
            buildingHeight = 9.0, buildingWidth = 20.0, buildingDepth = 10.0, numberOfFloors = 3))
        assertEquals(0.85, r.gustFactor, 1e-9)
        assertTrue(r.totalBaseShear >= 0.8 * 20.0 * 9.0 - 1e-6)
    }

    @Test fun `windward pressure at roof uses qh G 0_8`() {
        val r = WindLoadCalculator().calculate(WindLoadInput(basicWindSpeed = 40.0, terrainCategory = TerrainCategory.OPEN_TERRAIN,
            buildingHeight = 10.0, buildingWidth = 20.0, buildingDepth = 20.0, numberOfFloors = 2))
        // exposure C, qh = 0.834 kN/m2; windward 0.834 x 0.85 x 0.8 = 0.567
        assertEquals(0.567, r.externalPressureWindward, 0.003)
        assertEquals(-0.5 * 0.834 * 0.85, r.externalPressureLeeward, 0.003)
        assertEquals(0.18 * 0.834, r.internalPressure, 0.002)
    }

    @Test fun `flexible gust factor is larger than the rigid value`() {
        val gf = SbcWind301.gustFlexible(SbcExposure.B, 40.0, 100.0, 30.0, 30.0, 0.3, 0.02)
        assertTrue("Gf=$gf", gf > 0.85 && gf < 3.0)
    }
}
