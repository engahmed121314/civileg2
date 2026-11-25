package com.civileg.app.domain.calculations.utils

import kotlin.math.pow

/** Validates the existing contour check without treating missing capacity as zero demand. */
object ColumnMomentReview {
    data class Review(val utilization: Double, val passed: Boolean, val assessed: Boolean)
    fun assess(mx: Double, my: Double, mxCapacity: Double, myCapacity: Double): Review {
        require(mx.isFinite() && my.isFinite() && mx >= 0.0 && my >= 0.0)
        fun ratio(demand: Double, capacity: Double): Double? = when {
            demand == 0.0 -> 0.0
            !capacity.isFinite() || capacity <= 0.0 -> null
            else -> (demand / capacity).pow(1.5)
        }
        val x = ratio(mx,mxCapacity)
        val y = ratio(my,myCapacity)
        if (x == null || y == null) return Review(Double.POSITIVE_INFINITY,false,false)
        val u = x + y
        return Review(u,u.isFinite() && u <= 1.0 + 1e-9,true)
    }
}
