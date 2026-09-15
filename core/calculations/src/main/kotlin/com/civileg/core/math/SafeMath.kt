package com.civileg.core.math

import kotlin.math.*

object SafeMath {
    fun safeDivide(a: Double, b: Double): Double = if (b == 0.0) 0.0 else a / b
    fun safeSqrt(a: Double): Double = if (a < 0) 0.0 else sqrt(a)
    
    fun requirePositive(v: Double, name: String) {
        require(v > 0.0) { "$name must be positive" }
    }
    
    fun requireNonNegative(v: Double, name: String) {
        require(v >= 0.0) { "$name must be non-negative" }
    }
}
