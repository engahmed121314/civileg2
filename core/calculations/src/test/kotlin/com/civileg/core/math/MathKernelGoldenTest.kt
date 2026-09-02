package com.civileg.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 02 golden gates — every expected value hand-derived.
 */
class MathKernelGoldenTest {

    // ---------------- Tolerance ----------------

    @Test
    fun `tolerance equality is relative-scaled`() {
        assertTrue(Tolerance.eq(1e9 + 1.0, 1e9))          // 1 unit at scale 1e9
        assertTrue(!Tolerance.eq(1.0, 1.0 + 1e-6))         // 1e-6 at scale ~1
        assertTrue(Tolerance.leq(100.0, 100.0000000001))   // utilization-style
        // diff 1e-9 vs relTol*scale = 2e-9 -> within tolerance, so NOT greater
        assertTrue(!Tolerance.gt(2.0, 1.999999999))
        assertTrue(Tolerance.gt(2.0, 1.99))
    }

    // ---------------- SafeMath ----------------

    @Test
    fun `safe division rejects near-zero denominator loudly`() {
        assertEquals(5.0, SafeMath.div(10.0, 2.0), 0.0)
        assertThrows(ArithmeticException::class.java) { SafeMath.div(10.0, 0.0) }
        assertThrows(ArithmeticException::class.java) { SafeMath.div(1.0, 1e-15) }
    }

    @Test
    fun `safe sqrt rejects negatives`() {
        assertEquals(3.0, SafeMath.sqrt(9.0), 0.0)
        assertThrows(ArithmeticException::class.java) { SafeMath.sqrt(-1e-9) }
    }

    // ---------------- Root finding ----------------

    @Test
    fun `bisection finds x2-4 root and refuses unbracketed calls`() {
        val root = RootFinder.bisection({ it * it - 4.0 }, 0.0, 3.0)
        assertEquals(2.0, root, 1e-9)
        assertThrows(IllegalArgumentException::class.java) {
            RootFinder.bisection({ it * it + 1.0 }, 0.0, 3.0) // no sign change
        }
    }

    @Test
    fun `newton with bracket safeguard converges on cubic`() {
        // f(x) = x^3 - 8 ; root = 2 ; f' = 3x^2
        val root = RootFinder.newton(
            f = { it * it * it - 8.0 },
            df = { 3.0 * it * it },
            x0 = 5.0, lo = 0.0, hi = 4.0
        )
        assertEquals(2.0, root, 1e-7)
    }

    @Test
    fun `interpolation is exact on nodes and linear between them`() {
        val xs = listOf(0.0, 10.0, 30.0)
        val ys = listOf(0.0, 20.0, 40.0)
        assertEquals(0.0, RootFinder.interpolate(xs, ys, 0.0), 0.0)
        assertEquals(20.0, RootFinder.interpolate(xs, ys, 10.0), 0.0)
        assertEquals(10.0, RootFinder.interpolate(xs, ys, 5.0), 1e-12)
        assertEquals(35.0, RootFinder.interpolate(xs, ys, 25.0), 1e-12)
        assertThrows(IllegalArgumentException::class.java) {
            RootFinder.interpolate(xs, ys, 31.0) // extrapolation forbidden
        }
    }

    // ---------------- Units ----------------

    @Test
    fun `length conversions are factor-exact`() {
        val l = Length.of(2.5, LengthUnit.M)
        assertEquals(2500.0, l.asMm, 0.0)
        assertEquals(250.0, l.value(LengthUnit.CM), 0.0)
    }

    @Test
    fun `force times length gives moment in canonical base`() {
        val m = Force.of(10.0, ForceUnit.KN) * Length.of(3.0, LengthUnit.M)
        assertEquals(10_000.0 * 3000.0, m.asNmm, 0.0)
        assertEquals(30.0, m.value(MomentUnit.KNM), 1e-9)
        // Moment / lever = Force back
        val f = m / Length.of(1500.0, LengthUnit.MM)
        assertEquals(20.0, f.value(ForceUnit.KN), 1e-9)
    }

    @Test
    fun `stress is force over area and area from demand over capacity`() {
        val s = Force.of(500.0, ForceUnit.KN) / Area.ofMm2(250_000.0)
        assertEquals(2.0, s.asMpa, 1e-12)   // 500e3 N / 250e3 mm² = 2 MPa
        val a = forceOverStressToArea(Force.of(600.0, ForceUnit.KN), Stress.of(25.0, StressUnit.MPA))
        assertEquals(24_000.0, a.asMm2(), 1e-9)  // As = P/f = 24000 mm²
    }
}
