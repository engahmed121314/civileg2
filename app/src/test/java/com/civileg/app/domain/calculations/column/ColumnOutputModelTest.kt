package com.civileg.app.domain.calculations.column

import com.civileg.app.domain.calculations.utils.CalculatorEngine.ColumnResult
import com.civileg.app.domain.calculations.utils.CalculatorEngine.DesignSafetyCheck
import com.civileg.app.domain.calculations.utils.CalculatorEngine.ReinforcementBar
import com.civileg.app.domain.calculations.utils.CalculatorEngine.StirrupReinforcement
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected values are written out by hand, not read back from the code under test. */
class ColumnOutputModelTest {
    private fun result(
        checks: List<DesignSafetyCheck> = listOf(DesignSafetyCheck("Axial Capacity", 1000.0, 1500.0, "kN", true)),
        isSafe: Boolean = true, complete: Boolean = true, axialAssessed: Boolean = true,
        code: DesignCode = DesignCode.ECP
    ) = ColumnResult(
        width = 300.0, depth = 400.0, appliedAxial = 1234.56, code = code,
        reinforcement = ReinforcementBar(numBars = 8, diameter = 16),
        stirrups = StirrupReinforcement(description = "Ø8@150"),
        concreteVolume = 0.27, steelWeight = 52.349,
        safetyChecks = checks, isSafe = isSafe, assessmentComplete = complete, axialAssessed = axialAssessed
    )

    @Test fun coverIs50ForSbcAnd40ForEcpAndAci() {
        assertEquals(50.0, ColumnCover.mm(DesignCode.SBC), 0.0)
        assertEquals(40.0, ColumnCover.mm(DesignCode.ECP), 0.0)
        assertEquals(40.0, ColumnCover.mm(DesignCode.ACI), 0.0)
        assertEquals(50.0, ColumnOutputModel.from(result(code = DesignCode.SBC)).coverMm, 0.0)
    }

    @Test fun failedCheckOutranksIncompleteAssessment() {
        val failing = listOf(DesignSafetyCheck("Biaxial Moment", 120.0, 100.0, "%", false))
        assertEquals(ColumnStatus.FAIL, ColumnStatus.of(result(failing, isSafe = false, complete = false)))
        assertEquals(ColumnStatus.FAIL, ColumnStatus.of(result(failing, isSafe = false, axialAssessed = false)))
    }

    @Test fun passedButIncompleteReadsPreliminaryNeverSafe() {
        assertEquals(ColumnStatus.PRELIMINARY, ColumnStatus.of(result(isSafe = true, complete = false)))
    }

    @Test fun axialOutsideDomainWithNoFailedCheckIsNotAssessed() {
        assertEquals(ColumnStatus.NOT_ASSESSED, ColumnStatus.of(result(isSafe = false, axialAssessed = false)))
    }

    @Test fun completeAndPassedIsSafe() {
        assertEquals(ColumnStatus.SAFE, ColumnStatus.of(result()))
    }

    @Test fun slendernessRowAloneDoesNotFailAColumn() {
        val checks = listOf(DesignSafetyCheck("Slenderness", 40.0, 22.0, "", false))
        assertEquals(ColumnStatus.SAFE, ColumnStatus.of(result(checks)))
    }

    @Test fun failWithoutAnyFailedRowStillFails() {
        assertEquals(ColumnStatus.FAIL, ColumnStatus.of(result(isSafe = false)))
    }

    @Test fun modelPrintsTheSameValuesTheResultHolds() {
        val m = ColumnOutputModel.from(result())
        assertEquals("1234.6 kN", m.appliedPuText)
        assertEquals("8Ø16", m.reinforcementText)
        assertEquals("Ø8@150", m.stirrupsText)
        assertEquals("0.270 m3", m.concreteVolumeText)
        assertEquals("52.3 kg", m.steelWeightText)
    }

    @Test fun bbsIsNotIssuedAndQuotesTheSameNetSteel() {
        val text = ColumnOutputModel.from(result()).bbsStatusText
        assertTrue(text.contains("NOT ISSUED"))
        assertTrue(text.contains("52.3 kg"))
    }
}
