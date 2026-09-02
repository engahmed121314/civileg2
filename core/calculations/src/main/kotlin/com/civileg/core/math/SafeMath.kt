package com.civileg.core.math

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Guarded arithmetic for engineering kernels (PHASE 02).
 *
 * Spec rule 1.4 / §7: NaN, Infinity, division by zero and invalid inputs must
 * fail LOUDLY at the kernel boundary — never propagate silently into results.
 */
object SafeMath {

    const val EPS: Double = 1e-12

    /** Division that refuses near-zero denominators. */
    fun div(numerator: Double, denominator: Double, minDenominator: Double = EPS): Double {
        if (abs(denominator) < minDenominator)
            throw ArithmeticException("SafeMath.div: |denominator|=${abs(denominator)} < $minDenominator")
        return numerator / denominator
    }

    /** Square root of a non-negative value; negative input is an error, not a NaN. */
    fun sqrt(nonNegative: Double): Double {
        if (nonNegative < 0.0)
            throw ArithmeticException("SafeMath.sqrt: negative operand $nonNegative")
        // qualified call — an unqualified sqrt() would resolve to this member
        return kotlin.math.sqrt(nonNegative)
    }

    /** Ratio clamped to (0, 1] — used for utilization-style quantities. */
    fun unitRatio(numerator: Double, denominator: Double): Double =
        div(numerator, denominator).coerceIn(Double.MIN_VALUE, 1.0)

    fun requireFinite(value: Double, name: String): Double {
        if (value.isNaN() || value.isInfinite())
            throw ArithmeticException("SafeMath.requireFinite: $name = $value")
        return value
    }

    fun requirePositive(value: Double, name: String): Double {
        requireFinite(value, name)
        if (value <= 0.0)
            throw ArithmeticException("SafeMath.requirePositive: $name = $value")
        return value
    }

    /** Non-negative guard (0 is valid — e.g. pure-torsion with no shear). */
    fun requireNonNegative(value: Double, name: String): Double {
        requireFinite(value, name)
        if (value < 0.0)
            throw ArithmeticException("SafeMath.requireNonNegative: $name = $value")
        return value
    }
}
