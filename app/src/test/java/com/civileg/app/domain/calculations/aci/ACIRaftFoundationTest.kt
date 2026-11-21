package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.aci.ACIFooting.RaftColumn
import com.civileg.app.domain.calculations.aci.ACIFooting.RaftPunchingPosition
import org.junit.Assert.*
import org.junit.Test

/**
 * Golden vectors for the F-05 rigid-raft rebuild (docs/CLAUSE_LOG.md F-05, A-20a..e).
 * Method: ACI 318M-14 §13.2.6.2/§13.3.4.3 elastic pressure field; three-moment strips
 * (§13.3.4.2 prohibits DDM); punching per actual column (§22.6.4.1 minimum perimeter,
 * Table 22.6.5.2, §22.6.5.3); min steel §13.3.4.4 → Table 8.6.1.1.
 * Values hand-derived in CLAUSE_LOG F-05 (V1 cross-checked by hand arithmetic).
 */
class ACIRaftFoundationTest {

    private val footing = ACIFooting()

    private fun v1Columns() = listOf(
        RaftColumn(0.0, 0.0, 400.0, 400.0, 1200.0, 1500.0),
        RaftColumn(6000.0, 0.0, 400.0, 400.0, 1200.0, 1500.0),
        RaftColumn(0.0, 6000.0, 400.0, 400.0, 1200.0, 1500.0),
        RaftColumn(6000.0, 6000.0, 400.0, 400.0, 1200.0, 1500.0)
    )

    @Test
    fun `V1 - 2x2 symmetric grid - uniform pressure, exact strip moments, interior punching`() {
        val r = footing.designRaftFoundation(30.0, 400.0, v1Columns(), 1000.0, 200.0, 500.0)
        assertEquals(8400.0, r.lengthX, 1e-6)
        assertEquals(8400.0, r.widthY, 1e-6)
        assertEquals(68.0272, r.qMaxService, 0.001)
        assertEquals(68.0272, r.qMinService, 0.001)
        assertTrue(r.fullContact)
        assertTrue(r.equilibriumCheck)
        assertEquals(4, r.strips.size)
        val strip = r.strips.first { it.tag == "X-strip 1" }
        assertEquals(4200.0, strip.tributaryWidth, 1e-6)
        assertEquals(357.1429, strip.lineLoad, 0.001)
        assertEquals(-257.143, strip.supportMoments[0], 0.01)  // raft BOTTOM steel at the line
        assertEquals(1350.0, strip.spanMoments[0], 0.5)        // raft TOP steel in the span
        assertEquals(9546.6, strip.asTop, 5.0)
        assertEquals(4200.0, strip.asBottom, 1e-6)             // Table 8.6.1.1 minimum governs
        val p = r.punching[0]
        assertEquals(RaftPunchingPosition.INTERIOR, p.position)
        assertEquals(3260.0, p.criticalPerimeter, 0.5)
        assertEquals(1443.52, p.vu, 0.1)
        assertEquals(1640.39, p.phiVc, 0.1)
        assertEquals(0.8800, p.utilization, 0.001)
        assertTrue(r.isSafe)
    }

    @Test
    fun `V2 - 2x3 eccentric grid - trapezoid pressure and unequal strips`() {
        val cols = listOf(
            RaftColumn(0.0, 0.0, 400.0, 400.0, 800.0, 1000.0),
            RaftColumn(5000.0, 0.0, 400.0, 400.0, 800.0, 1000.0),
            RaftColumn(0.0, 4000.0, 400.0, 400.0, 800.0, 1000.0),
            RaftColumn(5000.0, 4000.0, 400.0, 400.0, 800.0, 1000.0),
            RaftColumn(0.0, 8000.0, 400.0, 400.0, 1600.0, 2000.0),
            RaftColumn(5000.0, 8000.0, 400.0, 400.0, 1600.0, 2000.0)
        )
        val r = footing.designRaftFoundation(30.0, 400.0, cols, 1000.0, 200.0, 500.0)
        assertEquals(7400.0, r.lengthX, 1e-6)
        assertEquals(10400.0, r.widthY, 1e-6)
        assertEquals(131.1371, r.qMaxService, 0.01)
        assertEquals(35.1831, r.qMinService, 0.01)
        assertTrue(r.fullContact)
        assertTrue(r.equilibriumCheck)
        val x3 = r.strips.first { it.tag == "X-strip 3" }
        assertEquals(465.4996, x3.lineLoad, 0.01)
        val y1 = r.strips.first { it.tag == "Y-strip 1" }
        assertEquals(3, y1.supportMoments.size)
        assertEquals(-276.923, y1.supportMoments[0], 0.01)
        assertEquals(-630.769, y1.supportMoments[1], 0.01)
        assertEquals(-276.923, y1.supportMoments[2], 0.01)
        assertTrue(r.isSafe)
    }

    @Test
    fun `V3 - tight edge projection - corner punching with reduced perimeter fails as derived`() {
        val r = footing.designRaftFoundation(30.0, 400.0, v1Columns(), 100.0, 200.0, 500.0)
        val p = r.punching[0]
        assertEquals(RaftPunchingPosition.CORNER, p.position)
        assertEquals(2830.0, p.criticalPerimeter, 0.5)
        assertEquals(1431.05, p.vu, 0.1)
        assertEquals(1424.02, p.phiVc, 0.1)
        assertEquals(1.0049, p.utilization, 0.001)
        assertFalse(p.isSafe)
        assertFalse(r.isSafe)
    }

    @Test
    fun `V4 - uplift case is rejected as outside the rigid method scope`() {
        val cols = listOf(
            RaftColumn(0.0, 0.0, 400.0, 400.0, 400.0, 500.0),
            RaftColumn(6000.0, 0.0, 400.0, 400.0, 400.0, 500.0),
            RaftColumn(0.0, 6000.0, 400.0, 400.0, 2400.0, 3000.0),
            RaftColumn(6000.0, 6000.0, 400.0, 400.0, 2400.0, 3000.0)
        )
        val r = footing.designRaftFoundation(30.0, 400.0, cols, 1000.0, 300.0, 500.0)
        assertFalse(r.fullContact)
        assertTrue(r.qMinService < 0.0)
        assertFalse(r.isSafe)
        assertTrue(r.warnings.any { it.contains("outside the rigid-raft method scope") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `single column line is outside the strip method scope`() {
        footing.designRaftFoundation(
            30.0, 400.0,
            listOf(
                RaftColumn(0.0, 0.0, 400.0, 400.0, 1200.0, 1500.0),
                RaftColumn(6000.0, 0.0, 400.0, 400.0, 1200.0, 1500.0)
            ),
            1000.0, 200.0, 500.0
        )
    }
}
