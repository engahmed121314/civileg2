package com.civileg.app.domain.calculations.ecp

import kotlin.math.sqrt

/**
 * ECP 203-2025 wall shear stresses (scan PDF file-6f2d5588.pdf, read as images).
 *  Eq 6-65 (PDF p.285, printed 6-92): qumax = 0.9 ac sqrt(fcu/gc) + mu_st fy/gs <= 0.55 sqrt(fcu/gc)
 *   ac = 0.18 for hw/Lw <= 1.5; 0.133 for hw/Lw >= 2; linear between (PDF p.286, printed 6-93).
 *  6-8-3-5-c (PDF p.286): concrete shear ignored in critical zones when axial compression under the
 *   seismic combination is below 0.05 fcu Ac. Applied here to the whole wall (conservative), and the app's
 *   axial load is assumed to be that combination: UNVERIFIED.
 *  Ordinary walls: Eq 4-30/4-31 (PDF p.101, printed 4-21), cracked qcu = 0.12 sqrt(fcu/gc), dc = 1+0.07 Pu/Ac <= 1.5.
 */
object ECP2025WallShear {
    fun alphaC(hw: Double, lw: Double): Double {
        val r = hw / lw
        return when {
            r <= 1.5 -> 0.18
            r >= 2.0 -> 0.133
            else -> 0.18 + (0.133 - 0.18) * (r - 1.5) / 0.5
        }
    }

    fun maxShearStress(fcu: Double, gc: Double): Double = 0.55 * sqrt(fcu / gc)

    /** Concrete term of Eq 6-65, N/mm2. nuN in N (compression positive), acMm2 gross area. */
    fun ductileConcreteStress(fcu: Double, hw: Double, lw: Double, nuN: Double, acMm2: Double, gc: Double): Double {
        if (nuN < 0.05 * fcu * acMm2) return 0.0
        return 0.9 * alphaC(hw, lw) * sqrt(fcu / gc)
    }

    fun ordinaryConcreteStress(fcu: Double, axialStressNmm2: Double, gc: Double): Double {
        val deltaC = minOf(1.5, 1.0 + 0.07 * axialStressNmm2.coerceAtLeast(0.0))
        return 0.12 * sqrt(fcu / gc) * deltaC
    }
}
