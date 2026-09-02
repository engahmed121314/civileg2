package com.civileg.core.math

import kotlin.math.abs

/**
 * Engineering tolerance comparisons (PHASE 02 kernel).
 *
 * Rule: two doubles are equal when they differ by less than
 * max(absTol, relTol * scale) where scale = max(|a|, |b|).
 * Never use raw `==` on computed engineering results.
 */
object Tolerance {
    const val DEFAULT_REL: Double = 1e-9
    const val DEFAULT_ABS: Double = 1e-12

    fun eq(a: Double, b: Double, rel: Double = DEFAULT_REL, absTol: Double = DEFAULT_ABS): Boolean =
        abs(a - b) <= maxOf(absTol, rel * maxOf(abs(a), abs(b)))

    fun lt(a: Double, b: Double, rel: Double = DEFAULT_REL, absTol: Double = DEFAULT_ABS): Boolean =
        a < b && !eq(a, b, rel, absTol)

    fun gt(a: Double, b: Double, rel: Double = DEFAULT_REL, absTol: Double = DEFAULT_ABS): Boolean =
        a > b && !eq(a, b, rel, absTol)

    /** Utilization-style comparison: demand <= capacity within tolerance. */
    fun leq(a: Double, b: Double, rel: Double = DEFAULT_REL, absTol: Double = DEFAULT_ABS): Boolean =
        !gt(a, b, rel, absTol)

    fun geq(a: Double, b: Double, rel: Double = DEFAULT_REL, absTol: Double = DEFAULT_ABS): Boolean =
        !lt(a, b, rel, absTol)
}
