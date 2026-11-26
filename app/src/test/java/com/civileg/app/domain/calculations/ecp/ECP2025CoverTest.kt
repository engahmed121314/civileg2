package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.ecp.ECP2025Cover.Exposure
import org.junit.Assert.*
import org.junit.Test

class ECP2025CoverTest {
    @Test fun exposureTables() {
        assertEquals(20.0, Exposure.VERY_DRY.minCoverMm, 0.0)
        assertEquals(25.0, Exposure.DRY.minCoverMm, 0.0)
        assertEquals(35.0, Exposure.MODERATE_HUMIDITY.minCoverMm, 0.0)
        assertEquals(40.0, Exposure.WET_DRY_CYCLES.minCoverMm, 0.0)
        assertEquals(60.0, Exposure.CHLORIDE_WET_DRY.minCoverMm, 0.0)
        assertEquals(60.0, Exposure.SEA_TIDAL_SPLASH.minCoverMm, 0.0)
    }
    @Test fun slabCoverIsMaxOfExposureAndFire() {
        assertEquals(25.0, ECP2025Cover.slabMinCover(Exposure.DRY, null), 0.0)
        assertEquals(30.0, ECP2025Cover.slabMinCover(Exposure.DRY, 2.0), 0.0)   // fire 30 > exposure 25
        assertEquals(35.0, ECP2025Cover.slabMinCover(Exposure.MODERATE_HUMIDITY, 2.0), 0.0) // exposure 35 > fire 30
        assertEquals(50.0, ECP2025Cover.slabMinCover(Exposure.DRY, 4.0), 0.0)
        assertNull(ECP2025Cover.slabFireCoverMm(2.5))
    }
}
