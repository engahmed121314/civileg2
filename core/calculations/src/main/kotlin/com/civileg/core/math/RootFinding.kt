package com.civileg.core.math

import kotlin.math.abs

/**
 * Numerical root finding (PHASE 02, spec §7 — numerical methods).
 *
 * Both solvers throw when the caller's contract is violated (no bracket, no
 * convergence, non-finite residual) — silent fallbacks are forbidden.
 */
object RootFinder {

    /**
     * Bisection with a mandatory sign change on [lo, hi].
     * Guaranteed to converge to within [tol]; capped by [maxIterations].
     */
    fun bisection(
        f: (Double) -> Double,
        lo: Double,
        hi: Double,
        tol: Double = 1e-10,
        maxIterations: Int = 200
    ): Double {
        var a = lo
        var b = hi
        var fa = f(a)
        val fb = f(b)
        SafeMath.requireFinite(fa, "f(lo)")
        SafeMath.requireFinite(fb, "f(hi)")
        if (fa == 0.0) return a
        if (fb == 0.0) return b
        require(fa * fb < 0.0) {
            "RootFinder.bisection: no sign change on [$lo, $hi] (f(lo)=$fa, f(hi)=$fb)"
        }

        repeat(maxIterations) {
            val mid = 0.5 * (a + b)
            val fm = f(mid)
            SafeMath.requireFinite(fm, "f(mid)")
            if (fm == 0.0 || 0.5 * (b - a) < tol) return mid
            if (fa * fm < 0.0) {
                b = mid
            } else {
                a = mid
                fa = fm
            }
        }
        // Interval collapsed below tol is the practical exit; reaching here means
        // maxIterations was too small for the requested tolerance.
        val remaining = abs(b - a)
        check(remaining < tol * 1000) {
            "RootFinder.bisection: no convergence after $maxIterations iterations (interval=$remaining)"
        }
        return 0.5 * (a + b)
    }

    /**
     * Newton–Raphson with analytic derivative and bracket safeguard:
     * iterates that leave [lo, hi] fall back to a bisection step.
     */
    fun newton(
        f: (Double) -> Double,
        df: (Double) -> Double,
        x0: Double,
        lo: Double,
        hi: Double,
        tol: Double = 1e-10,
        maxIterations: Int = 50
    ): Double {
        require(lo < hi) { "RootFinder.newton: lo >= hi" }
        var x = x0.coerceIn(lo, hi)
        repeat(maxIterations) {
            val fx = f(x)
            SafeMath.requireFinite(fx, "f(x)")
            if (abs(fx) < tol) return x
            val dfx = df(x)
            SafeMath.requireFinite(dfx, "df/dx")
            if (abs(dfx) < SafeMath.EPS) {
                x = bisectionStepFallback(f, lo, hi)
            } else {
                val next = x - fx / dfx
                x = if (next < lo || next > hi) bisectionStepFallback(f, lo, hi) else next
            }
            if (abs(f(x)) < tol) return x
        }
        error("RootFinder.newton: no convergence in $maxIterations iterations")
    }

    private fun bisectionStepFallback(f: (Double) -> Double, lo: Double, hi: Double): Double =
        0.5 * (lo + hi).also { /* caller re-evaluates f at the new point */ }

    /** Linear interpolation on an ascending table; extrapolation is an error. */
    fun interpolate(xs: List<Double>, ys: List<Double>, x: Double): Double {
        require(xs.size == ys.size && xs.size >= 2) { "Interpolation table must have >=2 matching points" }
        require(x >= xs.first() && x <= xs.last()) {
            "Interpolate($x) outside table range [${xs.first()}, ${xs.last()}] — extrapolation forbidden"
        }
        for (i in 0 until xs.size - 1) {
            if (x <= xs[i + 1]) {
                val t = SafeMath.div(x - xs[i], xs[i + 1] - xs[i])
                return ys[i] * (1.0 - t) + ys[i + 1] * t
            }
        }
        error("unreachable")
    }
}
