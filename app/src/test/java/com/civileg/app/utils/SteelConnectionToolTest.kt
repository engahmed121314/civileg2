package com.civileg.app.utils

import com.civileg.app.domain.entities.BoltGrade
import com.civileg.app.domain.entities.ElectrodeType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Connection quick-tools must route through the code-specific engines.
 * ACI/SBC follow AISC 360 (identical); ECP uses its own connection engine
 * (threaded bolt area + ECP factors), so it differs.
 */
class SteelConnectionToolTest {

    private val engine = CalculatorEngine(mockk(relaxed = true))
    private val boltDia = 20.0
    private val boltGrade = BoltGrade.GRADE_8_8

    @Test
    fun `ACI and SBC bolt capacities match (both AISC 360 based)`() {
        val aci = engine.calculateBoltCapacity(boltDia, boltGrade, 4, CalculatorEngine.DesignCode.ACI)
        val sbc = engine.calculateBoltCapacity(boltDia, boltGrade, 4, CalculatorEngine.DesignCode.SAUDI)
        assertTrue("ACI bolt capacity must be > 0", aci > 0.0)
        assertEquals("ACI and SBC use the same AISC bolt rule", aci, sbc, 1e-6)
    }

    @Test
    fun `ECP bolt capacity differs from AISC (threaded area + ECP factors)`() {
        val aci = engine.calculateBoltCapacity(boltDia, boltGrade, 1, CalculatorEngine.DesignCode.ACI)
        val ecp = engine.calculateBoltCapacity(boltDia, boltGrade, 1, CalculatorEngine.DesignCode.EGYPTIAN)
        assertTrue("ECP bolt capacity must be > 0", ecp > 0.0)
        assertNotEquals("ECP must use its own connection engine", aci, ecp, 1e-6)
    }

    @Test
    fun `bolt capacity scales linearly with count`() {
        val one = engine.calculateBoltCapacity(boltDia, boltGrade, 1, CalculatorEngine.DesignCode.ACI)
        val three = engine.calculateBoltCapacity(boltDia, boltGrade, 3, CalculatorEngine.DesignCode.ACI)
        assertEquals(3 * one, three, 1e-6)
    }

    @Test
    fun `weld capacity is positive for every code`() {
        for (code in listOf(
            CalculatorEngine.DesignCode.EGYPTIAN,
            CalculatorEngine.DesignCode.ACI,
            CalculatorEngine.DesignCode.SAUDI
        )) {
            val c = engine.calculateWeldCapacity(8.0, 200.0, ElectrodeType.E70XX, code)
            assertTrue("$code weld capacity must be > 0", c > 0.0)
        }
    }
}
