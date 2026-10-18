package com.civileg.app.utils

import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.SteelWarehouseInputs
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-010 regression guard for the warehouse entry points.
 *
 * `designSteelWarehouse` validates every numeric field it actually reads (geometry, roof
 * geometry, gravity loads, purlin spacing). `calculateSteelWarehousePro` reads the same fields
 * but deliberately inherits that validation — it calls `designSteelWarehouse` on its first line,
 * so an invalid input must throw from the Pro path too.
 */
class SteelWarehouseInputGuardTest {

    private val engine = CalculatorEngine(mockk(relaxed = true))

    private fun valid(over: (SteelWarehouseInputs) -> SteelWarehouseInputs = { it }) = over(
        SteelWarehouseInputs(
            span = 10.0, length = 12.0, eaveHeight = 5.0, ridgeHeight = 6.0, baySpacing = 1.0,
            code = DesignCode.ECP
        )
    )

    @Test
    fun `valid warehouse inputs compute successfully`() {
        val res = engine.designSteelWarehouse(valid())
        assertTrue("main frame moment must be > 0", res.mainFrame.maxMoment > 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designSteelWarehouse rejects zero span`() {
        engine.designSteelWarehouse(valid { it.copy(span = 0.0) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designSteelWarehouse rejects negative dead load`() {
        engine.designSteelWarehouse(valid { it.copy(deadLoad = -1.0) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designSteelWarehouse rejects NaN live load`() {
        engine.designSteelWarehouse(valid { it.copy(liveLoad = Double.NaN) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designSteelWarehouse rejects negative slope`() {
        engine.designSteelWarehouse(valid { it.copy(slope = -0.2) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designSteelWarehouse rejects negative purlin spacing`() {
        engine.designSteelWarehouse(valid { it.copy(purlinSpacing = -1.5) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designSteelWarehouse rejects non-finite ridge height`() {
        engine.designSteelWarehouse(valid { it.copy(ridgeHeight = Double.POSITIVE_INFINITY) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateSteelWarehousePro rejects negative dead load via inherited guard`() {
        engine.calculateSteelWarehousePro(valid { it.copy(deadLoad = -1.0) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateSteelWarehousePro rejects zero bay spacing via inherited guard`() {
        engine.calculateSteelWarehousePro(valid { it.copy(baySpacing = 0.0) })
    }

    @Test
    fun `calculateSteelWarehousePro produces a bill of materials for valid inputs`() {
        val res = engine.calculateSteelWarehousePro(valid())
        assertTrue("BoM must not be empty", res.billOfMaterials.isNotEmpty())
        assertTrue("total cost must be > 0", res.totalCost > 0.0)
    }
}
