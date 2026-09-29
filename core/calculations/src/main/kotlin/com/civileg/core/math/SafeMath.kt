package com.civileg.core.math

import kotlin.math.*

object SafeMath {
    /**
     * Safe division — throws ArithmeticException on division by zero.
     * Previously returned 0.0 silently, violating "loud failures, no silent zeros" principle.
     */
    fun safeDivide(a: Double, b: Double): Double {
        if (b == 0.0) throw ArithmeticException("Division by zero: $a / $b")
        return a / b
    }

    /**
     * Safe square root — throws ArithmeticException on negative input.
     * Previously returned 0.0 silently, violating "loud failures, no silent zeros" principle.
     */
    fun safeSqrt(a: Double): Double {
        if (a < 0) throw ArithmeticException("Square root of negative number: sqrt($a)")
        return sqrt(a)
    }
    
    fun requirePositive(v: Double, name: String) {
        require(v > 0.0) { "$name must be positive" }
    }
    
    fun requireNonNegative(v: Double, name: String) {
        require(v >= 0.0) { "$name must be non-negative" }
    }
}
