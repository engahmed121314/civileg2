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
}
