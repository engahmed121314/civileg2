package com.civileg.app.domain.calculations.utils

import com.civileg.app.domain.calculations.base.StairAnalysisStatus
import com.civileg.app.utils.StairResultGate
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unsupported stair cases must never be offered as a usable design by any consumer. */
class StairUnsupportedGateTest {

    private val engine = CalculatorEngine(mockk(relaxed = true))

    private fun design(type: CalculatorEngine.StairType) = engine.designStaircase(
        type = type, span = 4.0, riser = 150.0, tread = 300.0,
        deadLoad = 5.0, liveLoad = 4.0, fcu = 25.0, fy = 360.0
    )

    @Test
    fun `cantilever and spiral are gated as not usable`() {
        for (t in listOf(CalculatorEngine.StairType.CANTILEVER, CalculatorEngine.StairType.SPIRAL)) {
            val res = design(t)
            assertTrue("$t must report UNSUPPORTED_CASE", res.analysisStatus == StairAnalysisStatus.UNSUPPORTED_CASE)
            assertFalse("$t must not be usable", StairResultGate.isDesignUsable(res.analysisStatus))
            assertFalse("$t must never claim code acceptance", res.codeAcceptanceComplete)
        }
    }

    @Test
    fun `single flight with a supported model stays usable`() {
        val res = design(CalculatorEngine.StairType.SINGLE_FLIGHT)
        assertTrue(StairResultGate.isDesignUsable(res.analysisStatus))
    }

    @Test
    fun `gate blocks only the unsupported status`() {
        for (s in StairAnalysisStatus.values()) {
            assertTrue(StairResultGate.isDesignUsable(s) == (s != StairAnalysisStatus.UNSUPPORTED_CASE))
        }
    }
}
