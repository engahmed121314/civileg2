package com.civileg.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.1 AI Checker gate — rule classification on hand-built results (pure JVM).
 * No fabricated engineering: results mirror real engine output shapes.
 */
class AiCheckerEngineTest {

    private fun beam(
        util: Double, safe: Boolean = true,
        rho: Double = 1.2, cantilever: Boolean = false,
        topBars: Int = 2, topDia: Int = 12
    ) = CalculatorEngine.BeamResult(
        width = 250.0, depth = 500.0, mu = 150.0, vu = 80.0,
        reinforcementBottom = CalculatorEngine.ReinforcementBar(4, 16),
        reinforcementTop = CalculatorEngine.ReinforcementBar(topBars, topDia),
        stirrups = CalculatorEngine.StirrupReinforcement(8, 150.0),
        isSafe = safe,
        concreteVolume = 0.625, steelWeight = 45.0, cost = 1200.0,
        code = CalculatorEngine.AppDesignCode.EGYPTIAN,
        appliedMoment = 150.0, appliedShear = 80.0,
        supportType = if (cantilever) CalculatorEngine.SupportType.CANTILEVER
                      else CalculatorEngine.SupportType.HINGED_HINGED,
        momentCapacity = if (util > 0) 150.0 / util else 999.0,
        utilizationRatio = util, steelRatio = rho
    )

    @Test
    fun `healthy beam scores high with no criticals`() {
        val r = AiCheckerEngine.checkBeam(beam(util = 0.70))
        assertTrue(r.passed)
        assertTrue("score ${r.score} should be >= 85", r.score >= 85)
    }

    @Test
    fun `over-utilized beam raises critical`() {
        val r = AiCheckerEngine.checkBeam(beam(util = 1.05))
        assertTrue(r.findings.any { it.code == "UTIL_OVER" && it.severity == AiChecker.Severity.CRITICAL })
        assertTrue(!r.passed)
        assertTrue(r.score < 80)
    }

    @Test
    fun `under-utilized beam yields optimization hint`() {
        val r = AiCheckerEngine.checkBeam(beam(util = 0.20))
        assertTrue(r.optimizations.any { it.code == "UTIL_LOW" })
        assertTrue(r.passed) // optimization never blocks
    }

    @Test
    fun `cantilever with undersized top bars warns`() {
        val r = AiCheckerEngine.checkBeam(beam(util = 0.7, cantilever = true, topDia = 10))
        // bottom Ø16 > top Ø10 → CANT_DIA warning expected
        assertTrue(r.warningsList.any { it.code == "CANT_DIA" })
    }

    @Test
    fun `column below min ratio fails and above max fails`() {
        val base = CalculatorEngine.ColumnResult(
            width = 300.0, depth = 300.0, pu = 1000.0,
            muX = 0.0, muY = 0.0,
            reinforcement = CalculatorEngine.ReinforcementBar(8, 16),
            stirrups = CalculatorEngine.StirrupReinforcement(8, 150.0),
            isSafe = true, concreteVolume = 0.09, steelWeight = 30.0, cost = 800.0,
            code = CalculatorEngine.AppDesignCode.EGYPTIAN,
            axialCapacity = 2000.0,
            reinforcementRatio = 0.8   // < 1% minimum
        )
        val low = AiCheckerEngine.checkColumn(base)
        assertTrue(low.criticals.any { it.code == "RHO_MIN" })

        val over = base.copy(reinforcementRatio = 8.5)
        val hi = AiCheckerEngine.checkColumn(over)
        assertTrue(hi.criticals.any { it.code == "RHO_MAX" })

        val econ = base.copy(reinforcementRatio = 1.3)
        assertTrue(AiCheckerEngine.checkColumn(econ).optimizations.any { it.code == "RHO_ECON" })
    }

    @Test
    fun `material windows flag implausible strengths`() {
        val f = AiChecker.checkMaterial(fcu = 90.0, fy = 250.0)
        assertTrue(f.any { it.code == "MAT_FCU" })
        assertTrue(f.any { it.code == "MAT_FY" })
        assertTrue(AiChecker.checkMaterial(fcu = 30.0, fy = 400.0).isEmpty())
    }

    @Test
    fun `merge aggregates severity-sorted findings`() {
        val a = AiChecker.AiReport("BEAM", listOf(
            AiChecker.Finding("A", AiChecker.Severity.WARNING, "w", "w", "d")
        ))
        val b = AiChecker.AiReport("COLUMN", listOf(
            AiChecker.Finding("B", AiChecker.Severity.CRITICAL, "c", "c", "d"),
            AiChecker.Finding("C", AiChecker.Severity.OPTIMIZATION, "o", "o", "d")
        ))
        val merged = AiChecker.AiReport.merge(a, b)
        assertEquals(3, merged.findings.size)
        assertEquals(AiChecker.Severity.CRITICAL, merged.findings.first().severity)
        assertTrue(merged.score <= 75 && merged.score >= 60)
    }
}
