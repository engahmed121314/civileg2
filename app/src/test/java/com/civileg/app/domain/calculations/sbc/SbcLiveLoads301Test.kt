package com.civileg.app.domain.calculations.sbc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Values read from SBC 301-CR-2018 Table 4-1 (printed pp.30-32) and Table 3-2 (printed p.22). */
class SbcLiveLoads301Test {
    @Test fun `table values`() {
        assertEquals(2.5, SbcLiveLoads301.byId("office").loKnM2, 0.0)
        assertEquals(5.0, SbcLiveLoads301.byId("mosque").loKnM2, 0.0)
        assertEquals(3.0, SbcLiveLoads301.byId("school_classroom").loKnM2, 0.0)
        assertEquals(2.0, SbcLiveLoads301.byId("residential_private").loKnM2, 0.0)
        assertEquals(5.0, SbcLiveLoads301.byId("residential_public").loKnM2, 0.0)
        assertEquals(24.0, SbcLiveLoads301.REINFORCED_CONCRETE_STONE_KN_M3, 0.0)
        assertEquals(0.75, SbcLiveLoads301.PARTITION_MIN_KN_M2, 0.0)
        assertTrue(SbcLiveLoads301.byId("mosque").reductionNotPermitted)
    }
    @Test fun `ids are unique`() = assertEquals(SbcLiveLoads301.all.size, SbcLiveLoads301.all.map { it.id }.toSet().size)
}
