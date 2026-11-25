package com.civileg.app.domain.calculations.utils

import com.civileg.app.domain.calculations.base.StairAnalysisStatus
import com.civileg.app.domain.entities.DesignCode
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
        deadLoad = 5.0, liveLoad = 4.0, fcu = 25.0, fy = 360.0,
        preferredDiameter = 12, code = DesignCode.ECP
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

/** Consumers that run after a cantilever result exists must not throw (they run during composition). */
class StairUnsupportedConsumersTest {
    private val engine = CalculatorEngine(mockk(relaxed = true))

    @Test
    fun `cantilever result can be validated and summarised without throwing`() {
        val res = engine.designStaircase(
            type = CalculatorEngine.StairType.CANTILEVER, span = 4.0, riser = 150.0, tread = 300.0,
            deadLoad = 5.0, liveLoad = 4.0, fcu = 25.0, fy = 360.0,
            preferredDiameter = 12, code = DesignCode.ECP
        )
        CalculationValidator.validateStair(res)
        com.civileg.app.ui.compose.components.drawings.stairDrawingSummary(res)
        com.civileg.app.domain.calculations.utils.StairFlightGeometry.from(res.span, res.riser, res.tread)
        // The BBS and BOQ builders reject an unsupported case explicitly (deliberate contract, see below).
        assertTrue(!StairResultGate.isDesignUsable(res.analysisStatus))
    }

    private fun unsupported() = engine.designStaircase(
        type = CalculatorEngine.StairType.CANTILEVER, span = 4.0, riser = 150.0, tread = 300.0,
        deadLoad = 5.0, liveLoad = 4.0, fcu = 25.0, fy = 360.0,
        preferredDiameter = 12, code = DesignCode.ECP
    )

    @Test
    fun `BBS builder rejects an unsupported case with an explicit message`() {
        val e = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            BbsGenerator.generateStairBbs(mark = "ST1", result = unsupported(), stairWidthMm = 1200.0)
        }
        assertTrue(e.message.orEmpty().contains("not an assessed design"))
    }

    @Test
    fun `BOQ builder rejects an unsupported case with an explicit message`() {
        val e = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            BoqEstimator.stairRows(unsupported(), 1.2, "ST1")
        }
        assertTrue(e.message.orEmpty().contains("not an assessed design"))
    }

    @Test
    fun `save never reaches the repository for an unsupported case`() {
        val repo = mockk<com.civileg.app.db.DesignRepository>(relaxed = true)
        val vm = com.civileg.app.viewmodel.StairViewModel(repo, engine)
        vm.saveStair(1L, "x", unsupported())
        io.mockk.coVerify(exactly = 0) { repo.saveStairDesign(any(), any(), any()) }
    }

    @Test
    fun `supported single flight still builds BBS and BOQ`() {
        val ok = engine.designStaircase(
            type = CalculatorEngine.StairType.SINGLE_FLIGHT, span = 4.0, riser = 150.0, tread = 300.0,
            deadLoad = 5.0, liveLoad = 4.0, fcu = 25.0, fy = 360.0,
            preferredDiameter = 12, code = DesignCode.ECP
        )
        BbsGenerator.generateStairBbs(mark = "ST1", result = ok, stairWidthMm = 1200.0)
        assertTrue(BoqEstimator.stairRows(ok, 1.2, "ST1").isNotEmpty())
    }
}
