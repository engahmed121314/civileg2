package com.civileg.app.domain.calculations.utils

import kotlin.math.abs
import kotlin.math.roundToInt

/** Checks an engine's physical bar adoption without rounding an area demand
 * into bars. This is an internal consistency check, not code compliance.
 */
object HordiBarAdoption {
    fun adoptedCount(providedAreaMm2: Double, diameterMm: Double): Int? {
        if (!providedAreaMm2.isFinite() || providedAreaMm2 <= 0 ||
            !diameterMm.isFinite() || diameterMm <= 0) return null
        val area = Math.PI * diameterMm * diameterMm / 4.0
        val exact = providedAreaMm2 / area
        if (exact > Int.MAX_VALUE || exact < 1) return null
        val count = exact.roundToInt()
        // Float evaluation of n*pi*d^2/4 is the only tolerance here.
        return count.takeIf { abs(providedAreaMm2 - count * area) <= 1e-8 * maxOf(1.0, providedAreaMm2) }
    }

    fun areaIsSufficient(requiredMm2: Double, providedMm2: Double): Boolean =
        requiredMm2.isFinite() && requiredMm2 >= 0 && providedMm2.isFinite() &&
            providedMm2 >= requiredMm2
}
