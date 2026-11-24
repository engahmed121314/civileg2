package com.civileg.app.domain.calculations.utils
import org.junit.Assert.*
import org.junit.Test
class StairFlightGeometryTest {
    @Test fun nonIntegerSpanUsesEngineDiscreteConvention() {
        val g = StairFlightGeometry.from(4.0, 150.0, 300.0)
        assertEquals(13, g.treadCount); assertEquals(14, g.riserCount)
        assertEquals(3900.0, g.runMm, 0.0); assertEquals(2100.0, g.riseMm, 0.0)
    }
    @Test fun halfTreadRoundsUpAsEngineDoes() {
        val g = StairFlightGeometry.from(3.15, 170.0, 300.0)
        assertEquals(11, g.treadCount); assertEquals(2040.0, g.riseMm, 0.0)
    }
    @Test fun bbsAndFormworkUseSameInclineAsDrawing() {
        val r = CalculatorEngine.StairResult(
            type = CalculatorEngine.StairType.SINGLE_FLIGHT, span = 4.0, riser = 150.0, tread = 300.0,
            thickness = 160.0,
            reinforcement = CalculatorEngine.ReinforcementBar(diameter = 12, spacing = 150.0),
            distributionReinforcement = CalculatorEngine.ReinforcementBar(diameter = 8, spacing = 200.0),
            code = com.civileg.app.domain.entities.DesignCode.ECP, isSafe = false, concreteVolume = 0.0, steelWeight = 0.0, cost = 0.0
        )
        val inclineMm = kotlin.math.hypot(3900.0, 2100.0)
        assertEquals(inclineMm, BbsGenerator.generateStairBbs("ST1", r, 1200.0).first().lengthA, 1e-6)
        assertEquals(inclineMm / 1000.0 * 1.2, BoqEstimator.stairRows(r, 1.2).last().quantity, 1e-6)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonFinite() { StairFlightGeometry.from(Double.NaN, 150.0, 300.0) }
    @Test(expected = IllegalArgumentException::class) fun rejectsZeroTread() { StairFlightGeometry.from(4.0, 150.0, 0.0) }
}
