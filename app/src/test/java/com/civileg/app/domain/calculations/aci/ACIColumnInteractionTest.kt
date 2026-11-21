package com.civileg.app.domain.calculations.aci

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

/**
 * Golden vectors for the F-08 strain-compatibility interaction in [ACIColumn.checkInteraction].
 * ACI 318M-14 §22.2.1/§22.2.2/§22.2.2.4 + Table 22.2.2.4.3 (pp.552-554), §20.2.2 (p.522),
 * §21.2.2.1 + Table 21.2.2 (pp.545-547). Hand-computed; see docs/CLAUSE_LOG.md F-08/A-19.
 */
class ACIColumnInteractionTest {

    private val col = ACIColumn()
    private val ab20 = PI * 20.0 * 20.0 / 4.0

    private fun ordinates(nBars: Int, w: Double, h: Double, dp: Double) =
        ACIColumn().run {
            val layout = layoutPerimeterBars(nBars, w, h, dp)
            layout.groupBy { it.second }.toSortedMap().map { (y, bars) -> y to bars.size * ab20 }
        }

    @Test
    fun `8T20 400x400 at Pu 1500 kN - compression-controlled branch`() {
        val r = col.checkInteraction(30.0, 400.0, 400.0, 400.0, ordinates(8, 400.0, 400.0, 60.0), 1500e3)!!
        assertEquals(283.3103, r.neutralAxis, 0.01)
        assertEquals(0.65, r.phi, 1e-9)
        assertEquals(1500.0, r.phiPn / 1000.0, 0.01)
        assertEquals(144.5159, r.phiMn / 1e6, 0.01)
    }

    @Test
    fun `8T20 400x400 at Pu 2000 kN - descending branch loses moment capacity`() {
        val r = col.checkInteraction(30.0, 400.0, 400.0, 400.0, ordinates(8, 400.0, 400.0, 60.0), 2000e3)!!
        assertEquals(364.0150, r.neutralAxis, 0.01)
        assertEquals(103.4942, r.phiMn / 1e6, 0.01)
    }

    @Test
    fun `10T20 400x400 at Pu 1500 kN - added steel raises moment capacity`() {
        val r = col.checkInteraction(30.0, 400.0, 400.0, 400.0, ordinates(10, 400.0, 400.0, 60.0), 1500e3)!!
        assertEquals(276.0161, r.neutralAxis, 0.01)
        assertEquals(162.2166, r.phiMn / 1e6, 0.01)
    }

    @Test
    fun `8T20 400x400 at Pu 400 kN - tension-controlled phi 0_90 per Table 21_2_2`() {
        val r = col.checkInteraction(30.0, 400.0, 400.0, 400.0, ordinates(8, 400.0, 400.0, 60.0), 400e3)!!
        assertEquals(117.5300, r.neutralAxis, 0.01)
        assertEquals(0.90, r.phi, 1e-9)
        assertEquals(190.0422, r.phiMn / 1e6, 0.01)
    }

    @Test
    fun `returns null when Pu exceeds phiPo of the section`() {
        // 0.65 * Po(8T20, 400x400, fc=24) = 2741.7 kN < 2800 kN
        assertNull(col.checkInteraction(30.0, 400.0, 400.0, 400.0, ordinates(8, 400.0, 400.0, 60.0), 2800e3))
    }

    @Test
    fun `calculateReinforcement raises steel above axial seed when moment demands it`() {
        // Old 1+2e/h heuristic gave 12 bars here; strain compatibility requires 18 (CLAUSE_LOG F-08).
        val res = col.calculateReinforcement(30.0, 400.0, 400.0, 400.0, 1500.0, 150.0, 0.0, stubCombo())
        assertEquals(14.0, res.barDiameter, 1e-9)
        assertEquals(18, res.numberOfBars)
        assertTrue(res.isSafe)
    }

    @Test
    fun `calculateReinforcement pure axial picks 10T29 at the Table 22_4_2_1 cap`() {
        val res = col.calculateReinforcement(30.0, 400.0, 400.0, 400.0, 3000.0, 0.0, 0.0, stubCombo())
        assertEquals(29.0, res.barDiameter, 1e-9)
        assertEquals(10, res.numberOfBars)
        assertTrue(res.isSafe)
    }

    private fun stubCombo() = com.civileg.app.domain.entities.LoadCombination.DEAD_LIVE
}
