package com.civileg.app.domain.calculations.ecp

import kotlin.math.*

/**
 * Doubly Reinforced Beam Design per ECP 203-2020
 *
 * When a singly reinforced section is insufficient (Mu > Mu_max for tension-controlled),
 * compression steel is added to increase the moment capacity.
 *
 * Reference: ECP 203-2020 Clause 4-2-2-2
 */
data class DoublyReinforcedBeamResult(
    val mu: Double,                        // Applied ultimate moment (kN.m)
    val muMaxSingle: Double,               // Max moment for singly reinforced (kN.m)
    val needsCompressionSteel: Boolean,
    val k: Double,                         // Design factor K = Mu / (fcu * b * d^2)
    val kBal: Double,                      // Balanced K
    val r: Double,                         // R = Mu / (fcu/gamma_c * b * d^2)
    val rBal: Double,                      // Balanced R
    val asRequired: Double,                // Total tension steel area (mm^2)
    val asCompression: Double,             // Compression steel area (mm^2)
    val asTensionFromConcrete: Double,     // Tension steel balanced with concrete (mm^2)
    val asTensionFromCompression: Double,  // Additional tension steel for compression steel (mm^2)
    val asMin: Double,                     // Minimum steel area (mm^2)
    val asMax: Double,                     // Maximum steel area (mm^2)
    val tensionBars: String,               // e.g. "5Φ20"
    val compressionBars: String,           // e.g. "3Φ16"
    val tensionBarCount: Int,
    val tensionBarDia: Int,
    val compressionBarCount: Int,
    val compressionBarDia: Int,
    val d: Double,                         // effective depth (mm)
    val dPrime: Double,                    // depth to compression steel (mm)
    val na: Double,                        // Neutral axis depth (mm)
    val isSafe: Boolean,
    val utilizationRatio: Double,
    val warnings: List<String>
)

class ECPDoublyReinforcedBeam {

    companion object {
        const val GAMMA_C = 1.5   // ECP 203 concrete safety factor
        const val GAMMA_S = 1.15  // ECP 203 steel safety factor
        private const val E_S = 200000.0  // Modulus of elasticity of steel (MPa)
        private const val EPSILON_CU = 0.003  // Maximum concrete strain at failure
        private const val BETA_1 = 0.9    // ECP 203 Whitney block factor
    }

    /**
     * Calculate balanced moment coefficient K_bal per ECP 203-2020
     */
    fun calculateKBal(fcu: Double, fy: Double): Double {
        val epsilonY = fy / (E_S * GAMMA_S)
        val cOverD = EPSILON_CU / (EPSILON_CU + epsilonY)
        val aOverD = BETA_1 * cOverD
        return (0.67 / GAMMA_C) * aOverD * (1.0 - aOverD / 2.0)
    }

    /**
     * Design a doubly reinforced beam section
     */
    fun design(
        mu: Double,
        b: Double,
        h: Double,
        fcu: Double,
        fy: Double,
        cover: Double = 50.0,
        tensionBarDia: Int = 20,
        compBarDia: Int = 16
    ): DoublyReinforcedBeamResult {
        val warnings = mutableListOf<String>()
        val d = h - cover - tensionBarDia / 2.0
        val dPrime = cover + compBarDia / 2.0
        val fs = fy / GAMMA_S
        val Mu = mu * 1e6

        val k = Mu / (fcu * b * d * d)
        val kBal = calculateKBal(fcu, fy)

        val needsCompressionSteel = k > kBal
        val muMaxSingle = kBal * fcu * b * d * d / 1e6

        val asMin = max(0.25 * sqrt(fcu) / fy, 0.0013) * b * d
        val asMax = 0.04 * b * h

        if (!needsCompressionSteel) {
            val z = d * (0.5 + sqrt(max(0.0, 0.25 - k / 0.893)))
            var asReq = Mu / (fs * z)
            if (asReq < asMin) asReq = asMin
            val (tCount, tStr) = selectBars(asReq, tensionBarDia)
            return DoublyReinforcedBeamResult(
                mu, muMaxSingle, false, k, kBal, k, kBal, asReq, 0.0, asReq, 0.0, asMin, asMax,
                tStr, "None", tCount, tensionBarDia, 0, 0, d, dPrime, 0.0, true, mu / muMaxSingle, warnings
            )
        }

        // DOUBLY REINFORCED
        val Mu1 = muMaxSingle * 1e6
        val z1 = d * (0.5 + sqrt(max(0.0, 0.25 - kBal / 0.893)))
        val as1 = Mu1 / (fs * z1)
        
        val Mu2 = Mu - Mu1
        val asPrime = Mu2 / (fs * (d - dPrime))
        val as2 = asPrime
        
        val asTotal = as1 + as2
        val (tCount, tStr) = selectBars(asTotal, tensionBarDia)
        val (cCount, cStr) = selectBars(asPrime, compBarDia)

        val isSafe = asTotal <= asMax
        return DoublyReinforcedBeamResult(
            mu, muMaxSingle, true, k, kBal, k, kBal, asTotal, asPrime, as1, as2, asMin, asMax,
            tStr, cStr, tCount, tensionBarDia, cCount, compBarDia, d, dPrime, 0.0, isSafe, Mu / (Mu1 + Mu2), warnings
        )
    }

    private fun selectBars(area: Double, dia: Int): Pair<Int, String> {
        val barArea = PI * dia * dia / 4.0
        val count = ceil(area / barArea).toInt().coerceAtLeast(2)
        return count to "${count}Φ$dia"
    }
}
