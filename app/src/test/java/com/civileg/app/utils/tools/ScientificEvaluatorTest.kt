package com.civileg.app.utils.tools

import com.civileg.app.utils.tools.ScientificEvaluator.AngleMode
import com.civileg.app.utils.tools.ScientificEvaluator.ErrorKind
import com.civileg.app.utils.tools.ScientificEvaluator.Result
import org.junit.Assert.*
import org.junit.Test

class ScientificEvaluatorTest {
    private fun v(e: String, a: AngleMode = AngleMode.DEG): Double {
        val r = ScientificEvaluator.evaluate(e, a)
        assertTrue("$e -> $r", r is Result.Value)
        return (r as Result.Value).value
    }
    private fun err(e: String, kind: ErrorKind, a: AngleMode = AngleMode.DEG) {
        val r = ScientificEvaluator.evaluate(e, a)
        assertTrue("$e -> $r", r is Result.Error)
        assertEquals(e, kind, (r as Result.Error).kind)
    }

    @Test fun arithmeticAndPrecedence() {
        assertEquals(14.0, v("2+3×4"), 0.0)
        assertEquals(20.0, v("(2+3)×4"), 0.0)
        assertEquals(2.5, v("5÷2"), 0.0)
        assertEquals(-4.0, v("-2^2"), 0.0)
        assertEquals(0.5, v("2^-1"), 0.0)
        assertEquals(512.0, v("2^3^2"), 0.0) // right associative
        assertEquals(7.0, v("10-2-1"), 0.0)
    }

    @Test fun constantsAndImplicitMultiplication() {
        assertEquals(2 * Math.PI, v("2π"), 1e-12)
        assertEquals(Math.E, v("e"), 1e-15)
        assertEquals(15.0, v("3(4+1)"), 0.0)
        assertEquals(1.0, v("2sin(30)"), 1e-12)
    }

    @Test fun sqrtButtonSymbolWorks() {
        assertEquals(3.0, v("√9"), 0.0)
        assertEquals(3.0, v("√(9)"), 0.0)
        assertEquals(2.0, v("∛8"), 1e-12)
    }

    @Test fun exactQuarterTurnsInEveryAngleMode() {
        assertEquals(0.0, v("sin(180)"), 0.0); assertEquals(1.0, v("sin(90)"), 0.0)
        assertEquals(0.0, v("cos(90)"), 0.0); assertEquals(-1.0, v("cos(180)"), 0.0)
        assertEquals(0.0, v("sin(200)", AngleMode.GRAD), 0.0); assertEquals(1.0, v("sin(100)", AngleMode.GRAD), 0.0)
        assertEquals(1.0, v("sin(π/2)", AngleMode.RAD), 1e-15)
        assertEquals(0.5, v("sin(30)"), 1e-12)
        assertEquals(1.0, v("tan(45)"), 1e-12)
        assertEquals(-1.0, v("sin(-90)"), 0.0)
        assertEquals(-1.0, v("sin(270)"), 0.0)
    }

    @Test fun inverseTrigReturnsInCurrentAngleMode() {
        assertEquals(30.0, v("asin(0.5)"), 1e-9)
        assertEquals(Math.PI / 6, v("asin(0.5)", AngleMode.RAD), 1e-12)
        assertEquals(50.0, v("atan(1)", AngleMode.GRAD), 1e-9) // 45 degrees = 50 grad
    }

    @Test fun tanSingularitiesAreModeSpecific() {
        // DEG: 90, 270. GRAD: 100, 300. The number 90 is only singular in DEG.
        err("tan(90)", ErrorKind.DOMAIN); err("tan(270)", ErrorKind.DOMAIN)
        err("tan(100)", ErrorKind.DOMAIN, AngleMode.GRAD); err("tan(300)", ErrorKind.DOMAIN, AngleMode.GRAD)
        assertTrue(v("tan(90)", AngleMode.GRAD).isFinite())
        assertTrue(v("tan(100)").isFinite())
        assertEquals(1.0, v("tan(50)", AngleMode.GRAD), 1e-12)
    }

    @Test fun logsAndRoots() {
        assertEquals(3.0, v("log(1000)"), 1e-12)
        assertEquals(1.0, v("ln(e)"), 1e-15)
        assertEquals(5.0, v("abs(-5)"), 0.0)
    }

    @Test fun factorial() {
        assertEquals(120.0, v("5!"), 0.0); assertEquals(1.0, v("0!"), 0.0)
        assertEquals(6.0, v("(1+2)!"), 0.0)
        err("3.5!", ErrorKind.DOMAIN); err("(-3)!", ErrorKind.DOMAIN)
        assertTrue(v("170!").isFinite())
        err("171!", ErrorKind.OVERFLOW)
        err("10000000000!", ErrorKind.OVERFLOW) // must return at once, not loop
    }

    @Test fun domainAndZeroErrors() {
        err("1÷0", ErrorKind.DIVIDE_BY_ZERO); err("0÷0", ErrorKind.DIVIDE_BY_ZERO)
        err("√(-1)", ErrorKind.DOMAIN); err("ln(0)", ErrorKind.DOMAIN); err("log(-5)", ErrorKind.DOMAIN)
        err("asin(2)", ErrorKind.DOMAIN); err("acos(-1.5)", ErrorKind.DOMAIN)
        err("tan(90)", ErrorKind.DOMAIN); err("tan(270)", ErrorKind.DOMAIN); err("tan(100)", ErrorKind.DOMAIN, AngleMode.GRAD)
        err("(-8)^0.5", ErrorKind.DOMAIN); err("0^-1", ErrorKind.DIVIDE_BY_ZERO)
        err("10^400", ErrorKind.OVERFLOW)
    }

    @Test fun syntaxErrors() {
        for (e in listOf("", "  ", "2+", "*3", "(2+3", "2+3)", "1.2.3", ".", "foo(2)", "sin", "2 3 +", "sin 30")) err(e, ErrorKind.SYNTAX)
    }

    @Test fun formatPlainAndScientific() {
        assertEquals("0", ScientificEvaluator.format(0.0)); assertEquals("0", ScientificEvaluator.format(-0.0))
        assertEquals("3", ScientificEvaluator.format(3.0)); assertEquals("-2.5", ScientificEvaluator.format(-2.5))
        assertEquals("3.141592654", ScientificEvaluator.format(Math.PI))
        assertEquals("0.5", ScientificEvaluator.format(0.1 + 0.4))
        assertEquals("3000000000", ScientificEvaluator.format(3e9)) // beyond Int range, no overflow
        assertEquals("1e+10", ScientificEvaluator.format(1e10))
        assertEquals("1.5e-05", ScientificEvaluator.format(1.5e-5))
        assertEquals("-1.234567891e+20", ScientificEvaluator.format(-1.2345678912e20))
        assertEquals("0.0001", ScientificEvaluator.format(1e-4))
    }

    @Test fun scientificNotationIsParsedAndReusable() {
        assertEquals(1.5e-5, v("1.5e-05"), 0.0)
        assertEquals(2000.0, v("2e3"), 0.0)
        assertEquals(1e10, v("1e+10"), 0.0)
        assertEquals(3.0e-5, v("1.5E-05×2"), 1e-20)
        // Not an exponent: no digit after e, so it stays 2 x e.
        assertEquals(2 * Math.E, v("2e"), 1e-12)
        assertEquals(2 * Math.E, v("2e^1"), 1e-12)
        assertEquals(2.0 * Math.exp(1.0), v("2exp(1)"), 1e-12)
        // Every formatted result parses back to the same displayed text.
        for (x in listOf(1.5e-5, -1.234567891e20, 1e10, 123456.789, 0.0001, 3e9, -7.5e-12)) {
            val text = ScientificEvaluator.format(x)
            assertEquals(text, ScientificEvaluator.format(v(text)))
        }
        err("1e999", ErrorKind.OVERFLOW)
    }

    @Test fun radTangentPoleVersusNearbyAngles() {
        val rad = AngleMode.RAD
        err("tan(π/2)", ErrorKind.DOMAIN, rad); err("tan(3π/2)", ErrorKind.DOMAIN, rad)
        err("tan(-π/2)", ErrorKind.DOMAIN, rad); err("tan(5π/2)", ErrorKind.DOMAIN, rad)
        // Valid, not a pole: well away, and very near but beyond floating-point resolution of the pole.
        assertTrue(v("tan(1.57)", rad).isFinite()); assertEquals(Math.tan(1.57), v("tan(1.57)", rad), 0.0)
        assertTrue(v("tan(1.5707963267)", rad).isFinite())
        assertEquals(0.0, v("tan(0)", rad), 0.0)
        assertEquals(1.0, v("tan(π/4)", rad), 1e-12)
        assertEquals(0.0, v("tan(π)", rad), 1e-12)
    }

    @Test fun inputLengthAndDepthAreExplicitErrors() {
        err("1+".repeat(ScientificEvaluator.MAX_INPUT_LENGTH) + "1", ErrorKind.LIMIT)
        assertTrue(ScientificEvaluator.evaluate("1".repeat(ScientificEvaluator.MAX_INPUT_LENGTH)) !is Result.Error ||
            (ScientificEvaluator.evaluate("1".repeat(ScientificEvaluator.MAX_INPUT_LENGTH)) as Result.Error).kind != ErrorKind.LIMIT)
        // Deep nesting inside the length limit must be a LIMIT error, never a StackOverflowError.
        err("(".repeat(240) + "1" + ")".repeat(240), ErrorKind.LIMIT)
        err("-".repeat(400) + "1", ErrorKind.LIMIT)
        err("2" + "^2".repeat(230), ErrorKind.LIMIT)
        // Reasonable nesting still works.
        assertEquals(1.0, v("(".repeat(40) + "1" + ")".repeat(40)), 0.0)
        assertEquals(1.0, v("-".repeat(40) + "1"), 0.0)
    }

    @Test fun historyIsBoundedKeepingNewest() {
        var h = emptyList<String>()
        for (i in 1..(CalculatorHistory.MAX_ENTRIES + 20)) h = CalculatorHistory.append(h, "e$i")
        assertEquals(CalculatorHistory.MAX_ENTRIES, h.size)
        assertEquals("e${CalculatorHistory.MAX_ENTRIES + 20}", h.last())
        assertEquals("e21", h.first())
        assertEquals(listOf("b", "c"), CalculatorHistory.append(listOf("a", "b"), "c", 2))
    }
}
