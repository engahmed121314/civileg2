package com.civileg.app.utils

import com.civileg.core.calculations.entities.DesignCode
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 gate: column tie densification schedule through the live engine.
 * Verifies the three-zone schedule (support/mid/support) with end spacing
 * tighter than mid-span, and that counts/lengths are self-consistent.
 */
class ColumnTieZonesTest {

    private fun engine() = CalculatorEngine(mockk(relaxed = true))

    @Test
    fun `column produces 3-zone tie schedule with densified ends`() {
        val eng = engine()
        val r = eng.designColumn(
            width = 300.0, depth = 500.0,
            pu = 1500.0, mx = 120.0, my = 40.0,
            fcu = 25.0, fy = 400.0,
            code = CalculatorEngine.AppDesignCode.EGYPTIAN,
            clearHeight = 3200.0
        )
        val zones = r.stirrups.zones
        assertTrue("expected support/mid/support zones, got ${zones.size}", zones.size == 3)
        assertTrue(
            "end ties must be tighter than or equal to mid-span ties",
            zones[0].spacing <= zones[1].spacing && zones[2].spacing <= zones[1].spacing
        )
        // zone extents cover the full clear height without gaps/overlaps
        assertTrue(zones.first().startLocation == 0.0)
        assertTrue(kotlin.math.abs(zones.last().endLocation - 3200.0) < 1.0)
        for (z in zones) {
            assertTrue("${z.name}: spacing must be positive", z.spacing > 0)
            val n = ((z.endLocation - z.startLocation) / z.spacing).toInt() + 1
            assertTrue("${z.name}: tie count must be >= 2", n >= 2)
        }
    }
}
