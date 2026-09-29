package com.civileg.app.domain.calculations.aci

import kotlin.math.*

/**
 * محرك حسابات أحمال الرياح للمنشآت المعدنية (ASCE 7-16)
 */
object SteelWindEngine {

    /**
     * حساب ضغط الرياح التصميمي (p) - kN/m²
     * p = qz * G * Cp - qi * (GCpi)
     */
    fun calculateWindPressure(
        velocityKmh: Double,
        heightM: Double,
        isWindward: Boolean,
        exposureCategory: String = "C"
    ): Double {
        // qz = 0.613 * Kz * Kzt * Kd * V² * 1e-3 (kN/m²)
        val v = velocityKmh / 3.6 // m/s
        val kz = calculateKz(heightM, exposureCategory)
        val qz = 0.613 * kz * 1.0 * 0.85 * v.pow(2) * 1e-3
        
        val g = 0.85 // Gust factor for rigid structures
        val cp = if (isWindward) 0.8 else -0.5
        
        return qz * g * cp
    }

    private fun calculateKz(z: Double, category: String): Double {
        val (alpha, zg) = when (category) {
            "B" -> Pair(7.0, 365.0)
            "C" -> Pair(9.5, 274.0)
            "D" -> Pair(11.5, 213.0)
            else -> Pair(9.5, 274.0)
        }
        val zh = z.coerceIn(4.5, zg)
        return 2.01 * (zh / zg).pow(2.0 / alpha)
    }
}
