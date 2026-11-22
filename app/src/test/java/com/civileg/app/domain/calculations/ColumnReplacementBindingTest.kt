package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.entities.DesignCode
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/** Containment only: unsupported requests must not inherit another layout's checks.
 * This does not certify the automatic column method or implement replacement design.
 */
class ColumnReplacementBindingTest {
    private val engine = CalculatorEngine(mockk(relaxed = true))

    private fun design(diameter: Int = 16, count: Int? = null, auto: Boolean = true) =
        engine.designColumn(width = 400.0, depth = 600.0, pu = 500.0,
            mx = 0.0, my = 0.0, fcu = 30.0, fy = 420.0, code = DesignCode.ECP,
            clearHeight = 1000.0, preferredDiameter = diameter,
            manualNumBars = count, autoOptimize = auto, autoIncludeSelfWeight = false)

    @Test fun `manual smaller diameter and count cannot inherit automatic checks`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            design(diameter = 12, count = 4, auto = false)
        }
        assertTrue(error.message.orEmpty().contains("has not been evaluated"))
    }

    @Test fun `manual count is rejected even with the automatic flag left on`() {
        assertThrows(IllegalArgumentException::class.java) { design(count = 4) }
    }

    @Test fun `optimization off without a verified candidate is not an approved alternative`() {
        assertThrows(IllegalArgumentException::class.java) { design(diameter = 25, auto = false) }
    }

    @Test fun `explicit Pro diameter request is rejected even when automatic sizing flag is true`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            engine.designColumn(width = 400.0, depth = 600.0, pu = 500.0,
                fcu = 30.0, fy = 420.0, code = DesignCode.ECP, preferredDiameter = 25,
                autoOptimize = true, manualNumBars = null,
                reinforcementIntent = com.civileg.app.domain.entities.ColumnReinforcementIntent.REQUESTED_LAYOUT)
        }
        assertTrue(error.message.orEmpty().contains("has not been evaluated"))
    }

    @Test fun `invalid preferred diameter is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { design(diameter = 0) }
    }

    @Test fun `automatic source selection still returns its actual bars`() {
        val result = design()
        assertTrue(result.reinforcement.numBars > 0)
        assertTrue(result.reinforcement.diameter > 0)
        assertEquals(result.reinforcement.area, result.reinforcementArea, 1e-8)
    }
}
