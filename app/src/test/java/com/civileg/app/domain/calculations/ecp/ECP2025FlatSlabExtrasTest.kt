package com.civileg.app.domain.calculations.ecp

import org.junit.Assert.*
import org.junit.Test
import com.civileg.app.domain.calculations.ecp.ECP2025FlatSlabExtras as X

class ECP2025FlatSlabExtrasTest {
    @Test fun simplySupportedStrip() {
        // 5 w L^4 / (384 E I), w=10 N/mm, L=6000, E=25000, I=1e9 -> 6.75 mm
        assertEquals(6.75, X.stripMidspanDeflection(10.0, 6000.0, 0.0, 0.0, 25000.0, 1e9), 1e-9)
    }
    @Test fun fixedEndStripIsWL4Over384EI() {
        val m = 10.0 * 6000.0 * 6000.0 / 12.0
        assertEquals(1.35, X.stripMidspanDeflection(10.0, 6000.0, m, m, 25000.0, 1e9), 1e-9)
    }
    @Test fun panelMidpointAverages() {
        val p = X.panelMidpoint(3.0, 4.0, 2.0, 5.0)
        assertEquals(8.0, p.viaX, 1e-12); assertEquals(6.0, p.viaY, 1e-12); assertEquals(7.0, p.average, 1e-12)
        assertTrue(p.label.contains("no flat slab"))
    }
    @Test fun estimateUsesLongSpanLimits() {
        val e = X.estimate(dD = 10.0, dDplusLs = 12.0, dDplusL = 15.0, longSpanMm = 7500.0, xi = 2.0, rhoPrime = 0.0)
        assertEquals(2.0, e.alpha, 1e-12)
        assertEquals(5.0, e.immediateLive, 1e-12)
        assertEquals(15.0 + 2.0 * 12.0, e.total, 1e-12)
        assertEquals(30.0, e.totalCheck.allowable, 1e-12)   // 7500/250
        assertFalse(e.totalCheck.ok)
        assertEquals(7500.0 / 360.0, e.liveCheck.allowable, 1e-12)
        assertTrue(e.liveCheck.ok)
    }
    @Test fun dropAndHeadLimits() {
        assertTrue(X.dropDimensionIssues(200.0, 50.0, 1000.0, 6000.0, 5000.0).isEmpty())
        assertEquals(1, X.dropDimensionIssues(200.0, 40.0, 1000.0, 6000.0, 5000.0).size)
        assertEquals(1, X.dropDimensionIssues(200.0, 50.0, 900.0, 6000.0, 5000.0).size)   // < 1000
        assertEquals(1, X.dropDimensionIssues(200.0, 50.0, 1300.0, 6000.0, 5000.0).size)  // > 1250
        assertTrue(X.columnHeadIssues(45.0, 1200.0, 5000.0).isEmpty())
        assertEquals(2, X.columnHeadIssues(50.0, 1300.0, 5000.0).size)
    }
    @Test fun outerSectionHandCalc() {
        // slab 200, cover 20, bar 16 -> d = 172; drop 2400 x 2400 interior: bo = 4 (2400+172) = 10288
        val s = X.punchingSections(500.0, 30.0, 200.0, 0.0 + 60.0, 400.0, 400.0, 2400.0, 2400.0, 20.0, 16.0, X.Position.INTERIOR)
        assertEquals(2, s.size)
        assertEquals(10288.0, s[1].bo, 1e-9)
        assertEquals(172.0, s[1].d, 1e-9)
        val qa = 0.8 * (4.0 * 172.0 / 10288.0 + 0.2) * Math.sqrt(30.0 / 1.5)   // Eq 4-46-a governs (about 0.955)
        assertEquals(qa, s[1].qcup, 1e-9)
        assertEquals(qa * 10288.0 * 172.0 / 1000.0, s[1].vcKn, 1e-6)
        assertEquals(232.0, s[0].d, 1e-9)                              // 200 + 60 - 20 - 8
    }
    @Test fun noDropGivesOneSection() {
        assertEquals(1, X.punchingSections(300.0, 30.0, 200.0, 0.0, 400.0, 400.0, 0.0, 0.0, 20.0, 16.0, X.Position.INTERIOR).size)
    }
}
