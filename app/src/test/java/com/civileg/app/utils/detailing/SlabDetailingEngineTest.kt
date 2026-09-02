package com.civileg.app.utils.detailing

import com.civileg.app.utils.CalculatorEngine
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** §18 gate — slab zones coverage, flat-slab strips/punching, openings trimmers. */
class SlabDetailingEngineTest {

    private fun eng() = CalculatorEngine(mockk(relaxed = true))

    private fun solidResult() = eng().designSlab(
        lx = 5000.0, ly = 4000.0,
        deadLoad = 4.5, liveLoad = 2.0,
        fcu = 25.0, fy = 400.0,
        ts = 180.0, preferredDiameter = 12,
        code = CalculatorEngine.AppDesignCode.EGYPTIAN
    )

    private fun input(isFlat: Boolean, openings: Int = 0) =
        SlabDetailingEngine.SlabInput(
            lxMm = 5000.0, lyMm = 4000.0, coverMm = 25.0,
            codeName = "ECP 203-2020",
            isFlatSlab = isFlat, openingCount = openings
        )

    @Test
    fun `bottom zones cover the full field in both directions`() {
        val d = SlabDetailingEngine.build(solidResult(), input(false))
        assertEquals(0.0, d.bottomXZone.xStartMm, 1e-6)
        assertTrue(d.bottomXZone.barLengthMm >= 5000.0)
        assertTrue(d.bottomYZone.barLengthMm >= 4000.0)
        // bar counts must be positive and derived from spacing
        assertTrue(d.bottomXZone.count > 10)
    }

    @Test
    fun `marks are unique across all zones`() {
        val d = SlabDetailingEngine.build(solidResult(), input(false))
        val marks = listOf(d.bottomXZone.mark, d.bottomYZone.mark) +
            d.topZones.map { it.mark }
        assertEquals(marks.size, marks.toSet().size)
    }

    @Test
    fun `openings generate trimmer rows in BBS`() {
        val d = SlabDetailingEngine.build(solidResult(), input(false, openings = 2))
        assertEquals(2, d.openings.size)
        val oRows = d.schedule.rows.filter { it.memberLocation.startsWith("OPENING") }
        assertEquals(2, oRows.size)
        assertTrue(oRows.all { it.qty >= 4 && it.cuttingLengthMm > 0 })
    }

    @Test
    fun `flat slab exposes strips and punching perimeter when engine provides them`() {
        // Drive the FLAT-SLAB path of the real engine so drop/strip fields populate.
        val flatRes = eng().designSlab(
            lx = 6000.0, ly = 6000.0,
            deadLoad = 5.0, liveLoad = 3.0,
            fcu = 25.0, fy = 400.0,
            ts = 250.0, preferredDiameter = 16,
            code = CalculatorEngine.AppDesignCode.EGYPTIAN
        )
        val d = SlabDetailingEngine.build(
            flatRes,
            SlabDetailingEngine.SlabInput(
                lxMm = 6000.0, lyMm = 6000.0, coverMm = 30.0,
                codeName = "ECP 203-2020", isFlatSlab = true
            )
        )
        if (flatRes.columnStripSteelX.isNotEmpty()) {
            assertTrue("column strip band expected", d.strips.isNotEmpty())
            assertTrue("punching perimeter expected", (d.punchingPerimeterMm ?: 0.0) > 0)
        }
    }
}
