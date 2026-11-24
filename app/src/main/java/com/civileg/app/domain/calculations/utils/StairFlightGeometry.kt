package com.civileg.app.domain.calculations.utils

import kotlin.math.roundToInt

/** Matches the calculation's discrete tread convention. All drawing lengths are mm. */
data class StairFlightGeometry(val treadCount: Int, val riserCount: Int, val runMm: Double, val riseMm: Double) {
    companion object {
        fun from(spanM: Double, riserMm: Double, treadMm: Double): StairFlightGeometry {
            require(listOf(spanM, riserMm, treadMm).all { it.isFinite() && it > 0 })
            val count = spanM * 1000.0 / treadMm
            require(count < Int.MAX_VALUE - 1.0) { "Stair tread count exceeds supported range" }
            val treads = maxOf(1, count.roundToInt())
            return StairFlightGeometry(treads, treads + 1, treads * treadMm, (treads + 1) * riserMm)
        }
    }
}
