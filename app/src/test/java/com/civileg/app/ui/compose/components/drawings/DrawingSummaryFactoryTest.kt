package com.civileg.app.ui.compose.components.drawings

import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.calculations.utils.BeamShearCodeReview
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
        assertEquals("Unverified edition", summary.designCodeLabel)
        assertEquals(2, summary.safetyChecks.size)
        assertEquals(1, summary.failedCheckCount)
        assertEquals(2.4, summary.concreteVolume, 0.0)
        assertEquals(180.0, summary.steelWeight, 0.0)
        assertEquals(listOf("note"), summary.codeNotes)
    }

    @Test
    fun `beam summary keeps reviewed edition separate from incomplete acceptance`() {
        val review = BeamShearCodeReview.review(
            DesignCode.ACI, 25.0, 360.0, 300.0, 600.0, 550.0, 20.0, 8.0, 2, 150.0
        )
        assertTrue(review.numericChecksPass)
        assertFalse(review.codeAcceptanceComplete)
        val result = CalculatorEngine.BeamResult(
            isSafe = true, code = DesignCode.ACI, shearCodeReview = review,
            codeAcceptanceComplete = false, warnings = listOf("Anchorage not verified")
        )
        val summary = beamDrawingSummary(result)
        assertEquals("ACI318M-14", summary.designCodeLabel)
        assertFalse(summary.isSafe)
        assertTrue(summary.codeNotes.contains("Anchorage not verified"))
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
        assertEquals(com.civileg.app.domain.entities.DesignCode.ECP.version, footingSummary.designCodeLabel)
        assertEquals(0, footingSummary.failedCheckCount)
        assertTrue(slabSummary.isSafe)
        assertEquals(com.civileg.app.domain.entities.DesignCode.SBC.version, slabSummary.designCodeLabel)
        assertEquals(1, slabSummary.safetyChecks.size)
    }

    @Test
    fun `stair summary carries code and checks`() {
        val result = CalculatorEngine.StairResult(
            type = CalculatorEngine.StairType.SINGLE_FLIGHT,
            thickness = 0.15,
            reinforcement = CalculatorEngine.ReinforcementBar(),
            distributionReinforcement = CalculatorEngine.ReinforcementBar(),
            isSafe = true,
            concreteVolume = 0.6,
            steelWeight = 30.0,
            cost = 0.0,
            code = DesignCode.ECP,
            utilizationRatio = 0.55,
            flexureRatio = 0.55, flexureRatioBasis = "K/K_bal",
            safetyChecks = listOf(check("Flexure", 30.0, 45.0, true))
        )

        val summary = stairDrawingSummary(result)

        assertTrue(summary.isSafe)
        assertEquals(com.civileg.app.domain.entities.DesignCode.ECP.version, summary.designCodeLabel)
        assertEquals("55.0%", summary.utilizationPercent)
        assertEquals("FLEXURE K/K_bal: 55%", summary.utilizationText)
        assertEquals(0, summary.failedCheckCount)
        // No measured flexure ratio: the legacy clamped scalar must not reach the strip.
        val none = stairDrawingSummary(result.copy(flexureRatio = null, flexureRatioBasis = ""))
        assertEquals("FLEXURE: n/a", none.utilizationText)
        assertEquals(0.0, none.utilizationRatio, 0.0)
    }
}
