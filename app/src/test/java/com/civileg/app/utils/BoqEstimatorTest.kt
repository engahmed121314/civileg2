package com.civileg.app.utils

import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.CalculatorEngine.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoqEstimatorTest {

    @Test
    fun `beam boq contains concrete steel and formwork`() {
        val result = BeamResult(
            width = 300.0, depth = 600.0, span = 6.0,
            concreteVolume = 1.08, steelWeight = 120.0,
            isSafe = true, code = DesignCode.ECP
        )

        val rows = BoqEstimator.beamRows(result, "B1")

        assertEquals(3, rows.size)
        assertEquals(listOf("m3", "kg", "m2"), rows.map { it.unit })
        assertEquals(1.08, rows[0].quantity, 1e-9)
        // 6.0 * (2 * 600 + 300) / 1000 = 9.0 m2
        assertEquals(9.0, rows[2].quantity, 1e-9)
        assertTrue(rows.all { it.quantity >= 0.0 })
    }

    @Test
    fun `tank boq includes wall and base formwork`() {
        val result = TankResult(
            type = TankType.UNDERGROUND,
            length = 4.0, width = 3.0, height = 2.5,
            wallThickness = 0.25, baseThickness = 0.3,
            wallReinforcement = ReinforcementBar(diameter = 12, spacing = 200.0, numBars = 0),
            isSafe = true, concreteVolume = 12.0, steelWeight = 600.0,
            cost = 0.0, code = DesignCode.ECP
        )

        val rows = BoqEstimator.tankRows(result, "T1")

        assertEquals(4, rows.size)
        // 2 * (4 + 3) * 2.5 = 35 m2 walls
        assertEquals(35.0, rows[2].quantity, 1e-9)
        // 4 * 3 = 12 m2 base
        assertEquals(12.0, rows[3].quantity, 1e-9)
    }

    @Test
    fun `retaining wall boq scales with wall length`() {
        val result = RetainingWallResult(
            height = 4.0, stemThickness = 300.0, baseWidth = 2500.0,
            stemReinforcement = ReinforcementBar(diameter = 16, spacing = 150.0, numBars = 0),
            baseReinforcement = ReinforcementBar(diameter = 16, spacing = 200.0, numBars = 0),
            isSafe = true, concreteVolume = 3.0, steelWeight = 200.0,
            cost = 0.0, code = DesignCode.ECP
        )

        val perMetre = BoqEstimator.retainingWallRows(result, 1.0, "RW1")
        val perTenMetres = BoqEstimator.retainingWallRows(result, 10.0, "RW1")

        assertEquals(3.0, perMetre[0].quantity, 1e-9)
        assertEquals(30.0, perTenMetres[0].quantity, 1e-9)
        assertEquals(200.0, perMetre[1].quantity, 1e-9)
        assertEquals(2000.0, perTenMetres[1].quantity, 1e-9)
        assertEquals(8.0, perMetre[2].quantity, 1e-9)
        assertEquals(80.0, perTenMetres[2].quantity, 1e-9)
    }

    @Test
    fun `slab boq derives soffit formwork from panel dimensions`() {
        val result = SlabResult(
            thickness = 200.0,
            reinforcementMain = ReinforcementBar(diameter = 12, spacing = 150.0, numBars = 0),
            reinforcementSecondary = ReinforcementBar(diameter = 10, spacing = 200.0, numBars = 0),
            isSafe = true, concreteVolume = 4.0, steelWeight = 300.0, cost = 0.0,
            code = DesignCode.ECP
        )

        val rows = BoqEstimator.slabRows(result, 4.0, 5.0, "S1")

        assertEquals(3, rows.size)
        assertEquals(20.0, rows[2].quantity, 1e-9)
    }
}
