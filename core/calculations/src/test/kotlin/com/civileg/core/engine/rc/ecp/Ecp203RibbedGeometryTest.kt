package com.civileg.core.engine.rc.ecp

import org.junit.Assert.*
import org.junit.Test

class Ecp203RibbedGeometryTest {
    private val e = Ecp203RibbedGeometry

    @Test fun `clear gap is center spacing minus web rather than center spacing`() {
        val a = e.assess(150.0, 850.0, 300.0, 70.0)
        assertEquals(700.0, a.clearRibGapMm, 0.0)
        assertTrue(a.geometryChecksPass)
        assertFalse(e.assess(150.0, 850.1, 300.0, 70.01).geometryChecksPass)
    }
    @Test fun `web floor includes total depth divided by three`() {
        assertTrue(e.assess(100.0, 600.0, 300.0, 50.0).geometryChecksPass)
        assertFalse(e.assess(100.0, 600.0, 301.0, 50.0).geometryChecksPass)
        assertEquals(150.0, e.assess(150.0, 650.0, 450.0, 50.0).minimumRibWidthMm, 0.0)
    }
    @Test fun `topping floor is max50 and clear gap divided by ten`() {
        assertTrue(e.assess(150.0, 850.0, 300.0, 70.0).geometryChecksPass)
        assertFalse(e.assess(150.0, 850.0, 300.0, 69.9).geometryChecksPass)
        assertFalse(e.assess(150.0, 550.0, 300.0, 49.9).geometryChecksPass)
    }
    @Test fun `overlapping ribs and no rib depth are not ribbed geometry`() {
        assertFalse(e.assess(150.0, 150.0, 300.0, 60.0).geometryChecksPass)
        assertFalse(e.assess(150.0, 600.0, 300.0, 300.0).geometryChecksPass)
    }
    @Test fun `ordinary live load cross-rib threshold is span greater than five metres`() {
        assertEquals(Ecp203RibbedGeometry.CrossRibs.NONE, e.requiredOneWayCrossRibs(5000.0, 3.0))
        assertEquals(Ecp203RibbedGeometry.CrossRibs.ONE_AT_MIDSPAN, e.requiredOneWayCrossRibs(5000.1, 3.0))
        assertEquals(Ecp203RibbedGeometry.CrossRibs.ONE_AT_MIDSPAN, e.requiredOneWayCrossRibs(8000.0, 2.0))
    }
    @Test fun `heavy live load has distinct four and seven metre thresholds`() {
        assertEquals(Ecp203RibbedGeometry.CrossRibs.NONE, e.requiredOneWayCrossRibs(3999.9, 3.1))
        assertEquals(Ecp203RibbedGeometry.CrossRibs.ONE_AT_MIDSPAN, e.requiredOneWayCrossRibs(4000.0, 3.1))
        assertEquals(Ecp203RibbedGeometry.CrossRibs.ONE_AT_MIDSPAN, e.requiredOneWayCrossRibs(4999.0, 3.1))
        assertEquals(Ecp203RibbedGeometry.CrossRibs.ONE_AT_MIDSPAN, e.requiredOneWayCrossRibs(5000.0, 3.1))
        assertEquals(Ecp203RibbedGeometry.CrossRibs.ONE_AT_MIDSPAN, e.requiredOneWayCrossRibs(7000.0, 3.1))
        assertEquals(Ecp203RibbedGeometry.CrossRibs.THREE, e.requiredOneWayCrossRibs(7000.1, 3.1))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `negative live load rejected`() { e.requiredOneWayCrossRibs(6000.0, -1.0) }
    @Test(expected = IllegalArgumentException::class)
    fun `nonfinite spacing rejected`() { e.assess(150.0, Double.NaN, 300.0, 60.0) }
}
