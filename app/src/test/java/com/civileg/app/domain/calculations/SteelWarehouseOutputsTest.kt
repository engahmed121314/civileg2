package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.entities.*
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class SteelWarehouseOutputsTest {
    private val engine = CalculatorEngine(mockk(relaxed = true))
    private val col = SteelSectionType.ISection(h = 300.0, bf = 300.0, tf = 14.0, tw = 8.5, grade = SteelGrade.ST37, customName = "COL")
    private val raf = SteelSectionType.ISection(h = 330.0, bf = 160.0, tf = 11.5, tw = 7.5, grade = SteelGrade.ST37, customName = "RAF")

    private fun run(span: Double = 20.0, live: Double = 0.5) = engine.designSteelWarehouse(
        SteelWarehouseInputs(span = span, length = 60.0, eaveHeight = 6.0, ridgeHeight = 7.0, baySpacing = 6.0,
            liveLoad = live, overrideColumnSection = col, overrideRafterSection = raf)
    )

    @Test fun takeoffIsRealPerMemberWeightNotFiftyFifty() {
        val r = run()
        val c = r.materialTakeoff.entries.first { it.key.startsWith("Columns") }.value
        val f = r.materialTakeoff.entries.first { it.key.startsWith("Rafters") }.value
        // 11 frames (60 m / 6 m + 1); columns 2 x 6 m each; hand: area*7850 kg/m3
        assertEquals(2 * 6.0 * col.area * 7850.0 / 1e9 * 11, c, 1e-9)
        assertNotEquals(c, f, 1e-6)
        val sum = r.materialTakeoff.values.sum()
        assertEquals(r.totalWeight, sum, 1e-9)
        assertTrue(r.materialTakeoff.keys.any { it.contains("COL") })
    }

    @Test fun overloadedUtilizationIsNotClampedTo1_5() {
        val r = run(span = 40.0, live = 20.0)
        assertTrue(r.mainFrame.utilizationMoment > 1.5)
    }

    @Test fun noInventedConnections() {
        assertTrue(run().connections.isEmpty())
    }
}
