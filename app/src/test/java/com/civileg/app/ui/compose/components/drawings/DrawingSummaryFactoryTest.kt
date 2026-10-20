package com.civileg.app.ui.compose.components.drawings

import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.CalculatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingSummaryFactoryTest {

    private fun check(name: String, value: Double, limit: Double, safe: Boolean) =
        CalculatorEngine.DesignSafetyCheck(name, value, limit, "kN", safe)

    @Test
    fun `beam summary maps checks code and quantities`() {
        val result = CalculatorEngine.BeamResult(
            isSafe = false,
            utilizationRatio = 1.25,
            concreteVolume = 2.4,
            steelWeight = 180.0,
            code = DesignCode.ACI,
            safetyChecks = listOf(
                check("Flexure", 90.0, 120.0, true),
                check("Shear", 80.0, 60.0, false)
            ),
            codeNotes = listOf("note")
        )

        val summary = beamDrawingSummary(result)

        assertFalse(summary.isSafe)
        assertEquals("125.0%", summary.utilizationPercent)
        assertEquals("ACI 318-19", summary.designCodeLabel)
        assertEquals(2, summary.safetyChecks.size)
        assertEquals(1, summary.failedCheckCount)
        assertEquals(2.4, summary.concreteVolume, 0.0)
        assertEquals(180.0, summary.steelWeight, 0.0)
        assertEquals(listOf("note"), summary.codeNotes)
    }

    @Test
    fun `column summary exposes axial load ratio`() {
        val result = CalculatorEngine.ColumnResult(
            isSafe = true,
            axialCapacity = 4000.0,
            appliedAxial = 2500.0,
            safetyChecks = emptyList()
        )

        val summary = columnDrawingSummary(result)

        assertTrue(summary.isSafe)
        assertEquals(0.625, summary.axialLoadRatio, 1e-9)
        assertEquals("OK (0.63)", summary.axialLoadStatus)
    }

    @Test
    fun `footing and slab summaries keep the engine safety verdict`() {
        val footing = CalculatorEngine.FootingResult(
            type = CalculatorEngine.FootingType.ISOLATED,
            width = 1.5,
            length = 1.5,
            thickness = 0.4,
            soilPressure = 120.0,
            allowablePressure = 150.0,
            reinforcementBottom = CalculatorEngine.ReinforcementBar(),
            isSafe = true,
            code = DesignCode.ECP,
            concreteVolume = 0.9,
            steelWeight = 45.0,
            cost = 0.0,
            utilizationRatio = 0.8,
            safetyChecks = listOf(check("Bearing", 120.0, 150.0, true))
        )
        val slab = CalculatorEngine.SlabResult(
            thickness = 0.2,
            isSafe = true,
            concreteVolume = 1.2,
            steelWeight = 60.0,
            code = DesignCode.SBC,
            utilizationRatio = 0.7,
            safetyChecks = listOf(check("Punching", 40.0, 55.0, true))
        )

        val footingSummary = footingDrawingSummary(footing)
        val slabSummary = slabDrawingSummary(slab)

        assertTrue(footingSummary.isSafe)
        assertEquals("ECP 203-2020", footingSummary.designCodeLabel)
        assertEquals(0, footingSummary.failedCheckCount)
        assertTrue(slabSummary.isSafe)
        assertEquals("SBC 304-2018", slabSummary.designCodeLabel)
        assertEquals(1, slabSummary.safetyChecks.size)
    }
}
