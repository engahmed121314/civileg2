package com.civileg.core.calculations.ecp

import kotlin.math.*

data class CFSSectionResult(
    val nominalMoment: Double, // kN.m
    val nominalAxial: Double,  // kN
    val isSafe: Boolean,
    val effectiveProperties: Map<String, Double>
)

/**
 * Cold-Formed Steel (CFS) Design Engine according to ECP 205 / AISI S100.
 */
class CFSDesignEngine {

    /**
     * Calculate effective width of a compression element.
     */
    fun calculateEffectiveWidth(
        actualWidth: Double,
        thickness: Double,
        stress: Double, // MPa
        k: Double = 4.0 // Buckling coefficient
    ): Double {
        if (thickness <= 0) return 0.0
        val e = 200000.0 // MPa
        val f_cr = k * (PI.pow(2) * e) / (12 * (1 - 0.3.pow(2)) * (actualWidth / thickness).pow(2))
        val lambda_p = sqrt(stress / f_cr)

        return if (lambda_p <= 0.673) {
            actualWidth
        } else {
            val rho = (1.0 - 0.22 / lambda_p) / lambda_p
            actualWidth * rho
        }
    }

    /**
     * Simplified design for C-Purlin.
     */
    fun designCPurlin(
        h: Double, b: Double, c: Double, t: Double,
        fy: Double = 240.0,
        appliedMoment: Double // kN.m
    ): CFSSectionResult {
        val area = (h + 2 * b + 2 * c) * t
        val fullSx = (t * h.pow(2) / 6) + (2 * b * t * h / 2) 
        
        val effSx = fullSx * 0.85
        val Mn = 0.9 * fy * effSx / 1e6
        
        return CFSSectionResult(
            nominalMoment = Mn,
            nominalAxial = 0.9 * fy * area / 1000.0,
            isSafe = appliedMoment <= Mn,
            effectiveProperties = mapOf("effSx" to effSx, "area" to area)
        )
    }
}
