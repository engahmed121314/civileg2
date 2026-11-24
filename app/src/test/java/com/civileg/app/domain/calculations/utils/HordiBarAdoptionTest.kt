package com.civileg.app.domain.calculations.utils

import org.junit.Assert.*
import org.junit.Test

class HordiBarAdoptionTest {
    @Test fun `integral adopted bars are not confused with area demand`() {
        assertEquals(4, HordiBarAdoption.adoptedCount(4 * Math.PI * 12 * 12 / 4, 12.0))
        assertNull(HordiBarAdoption.adoptedCount(475.6, 12.0))
    }
    @Test fun `non finite or absent bar adoption rejected`() {
        assertNull(HordiBarAdoption.adoptedCount(Double.NaN, 12.0))
        assertNull(HordiBarAdoption.adoptedCount(Double.POSITIVE_INFINITY, 12.0))
        assertNull(HordiBarAdoption.adoptedCount(0.0, 12.0))
        assertNull(HordiBarAdoption.adoptedCount(452.0, 0.0))
    }
    @Test fun `known false safe engine case fails area consistency`() {
        assertFalse(HordiBarAdoption.areaIsSufficient(1721.25, 452.389342))
        assertTrue(HordiBarAdoption.areaIsSufficient(400.0, 452.389342))
        assertFalse(HordiBarAdoption.areaIsSufficient(Double.NaN, 452.389342))
    }
}
