package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.InputGuard
import kotlin.math.*

/**
 * Doubly Reinforced Beam Design per SBC 304-2018
 *
 * When a singly reinforced section is insufficient (Mu > Mu_max for tension-controlled),
 * compression steel is added to increase the moment capacity.
 *
 * Key equations:
 * - Mu_max_singly = Mu_balanced
 * - If Mu > Mu_max_singly -> need compression steel
 * - As' = (Mu - Mu_balanced) / (fy/γs × (d - d'))
 * - As = As1 (concrete balanced) + As' (compression steel couple)
 *
 * Key SBC 304 differences from ACI 318:
 * - Uses fcu directly (cube strength), not f'c = 0.8×fcu
 * - Material safety factors: γc = 1.5, γs = 1.15
 * - Strength reduction: φ_flexure = 0.9, φ_shear = 0.75
 * - Load factors: 1.4DL + 1.6LL
 * - α = 0.85 (ECP factor for concrete stress block)
 *
 * References:
 *  - SBC 304-2018 §4-2-2-1: Flexural design
 *  - SBC 304-2018 §4-2-2-2: Doubly reinforced sections
 *  - SBC 304-2018 §4-2-3: Ductility requirements
 *  - SBC 304-2018 §3-2: Material safety factors (γc=1.5, γs=1.15)
 *  - SBC 304-2018 §3-2: Load factors (1.4DL + 1.6LL)
 */
data class DoublyReinforcedBeamResult(
    val mu: Double,                        // Applied ultimate moment (kN.m)
    val muMaxSingle: Double,               // Max moment for singly reinforced (kN.m)
    val needsCompressionSteel: Boolean,
    val k: Double,                         // Design factor K = Mu / (fcu * b * d^2)
    val kBal: Double,                      // Balanced K
    val r: Double,                         // R = Mu / (fcu*b*d^2)
    val rBal: Double,                      // Balanced R
    val asRequired: Double,                // Total tension steel area (cm^2)
    val asCompression: Double,             // Compression steel area (cm^2)
    val asTensionFromConcrete: Double,     // Tension steel balanced with concrete (cm^2)
    val asTensionFromCompression: Double,  // Additional tension steel for compression steel (cm^2)
    val asMin: Double,                     // Minimum steel area (cm^2)
    val asMax: Double,                     // Maximum steel area (cm^2)
    val tensionBars: String,               // e.g. "5Ø20"
    val compressionBars: String,           // e.g. "3Ø16"
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

class SBCDoublyReinforcedBeam {

    companion object {
        // SBC 304 material safety factors
        const val GAMMA_C = 1.5   // Concrete safety factor
        const val GAMMA_S = 1.15  // Steel safety factor
        const val ALPHA = 0.85    // Concrete stress block factor
        const val EPSILON_CU = 0.003  // Maximum concrete strain
        const val E_S = 200000.0  // Steel modulus of elasticity (MPa)
        private const val PI = 3.141592653589793
    }

    /**
     * Calculate K_balanced per SBC 304
     * K_bal = α * ε_cu / (ε_cu + ε_y) * (1 - 0.5 * α * ε_cu / (ε_cu + ε_y))
     * Where α = 0.85 (stress block factor)
     */
    fun calculateKBal(fcu: Double, fy: Double): Double {
        // ── InputGuard: loud failures, no silent zeros (ADR-010) ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)

        val epsilonY = fy / (E_S * GAMMA_S)
        val aOverDBal = ALPHA * EPSILON_CU / (EPSILON_CU + fy / (E_S * GAMMA_S))
        val kBal = ALPHA * aOverDBal * (1.0 - 0.5 * ALPHA * aOverDBal)
        return kBal
    }

    /**
     * Calculate R_balanced = K_bal * (1 - 0.5 * K_bal)
     * This represents the normalized balanced moment coefficient.
     */
    fun calculateRBal(kBal: Double): Double {
        // ── InputGuard: loud failures, no silent zeros (ADR-010) ──
        InputGuard.positive("kBal", kBal)

        return kBal * (1.0 - 0.5 * kBal)
    }

    /**
     * Design a doubly reinforced beam section
     *
     * @param mu Ultimate applied moment (kN.m)
     * @param b Beam width (mm)
     * @param h Beam total depth (mm)
     * @param fcu Concrete strength (MPa)
     * @param fy Steel yield strength (MPa)
     * @param cover Concrete cover to tension steel (mm)
     * @param tensionBarDia Assumed tension bar diameter (mm)
     * @param compBarDia Assumed compression bar diameter (mm)
     * @return DoublyReinforcedBeamResult
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
        // ── InputGuard: loud failures, no silent zeros (ADR-010) ──
        InputGuard.positive("mu", mu)
        InputGuard.positive("b", b)
        InputGuard.positive("h", h)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("cover", cover)
        InputGuard.positive("tensionBarDia", tensionBarDia)
        InputGuard.positive("compBarDia", compBarDia)

        val warnings = mutableListOf<String>()

        // ==================== Section Geometry ====================
        val d = h - cover - tensionBarDia / 2.0     // Effective depth to tension steel
        val dPrime = cover + compBarDia / 2.0        // Depth to compression steel centroid

        // ==================== Material Properties ====================
        val fs = fy / GAMMA_S  // Design steel stress (MPa)

        // ==================== Convert Moment ====================
        val Mu = mu * 1e6  // kN.m -> N.mm

        // ==================== K and R Factors ====================
        // K = Mu / (fcu * b * d^2) - standard K-factor per SBC
        val k = Mu / (fcu * b * d * d)
        val kMax = 0.194 // Max K-factor for singly reinforced section per SBC 304

        // K_bal dynamic per SBC 304
        val kBal = calculateKBal(fcu, fy)
        val rBal = calculateRBal(kBal)

        // ==================== Check if compression steel needed ====================
        val needsCompSteel = k > kMax

        // ==================== Maximum singly reinforced moment ====================
        val muMaxSingle = kMax * fcu * b * d * d / 1e6  // kN.m

        // ==================== Calculate Steel Areas ====================
        var asTensionFromConcrete = 0.0
        var asTensionFromCompression = 0.0
        var asCompression = 0.0
        var asTotal = 0.0

        if (needsCompSteel) {
            // Moment carried by concrete in max singly reinforced section
            val muBal = kMax * fcu * b * d * d / 1e6  // kN.m
            val muExcess = mu - muBal  // kN.m
            val MuExcess = muExcess * 1e6  // N.mm

            // Compression steel force
            // Check if compression steel yields
            val epsilonSPrime = EPSILON_CU * (1.0 - dPrime / (kMax * d / ALPHA))
            val fsPrime = minOf(epsilonSPrime * E_S, fs)

            // Compression steel area
            asCompression = MuExcess / (fsPrime * (d - dPrime)) / 100.0  // cm^2

            // Additional tension steel to balance compression steel
            asTensionFromCompression = asCompression * fsPrime / fs

            // Tension steel for concrete balanced moment
            asTensionFromConcrete = rBal * fcu * b * d / fs / 100.0  // cm^2

            asTotal = asTensionFromConcrete + asTensionFromCompression
        } else {
            // Singly reinforced - use standard design
            val aOverD = (1.0 - sqrt(max(0.0, 1.0 - 2.0 * k / ALPHA))) / ALPHA
            val na = aOverD * d / ALPHA
            asTotal = k * fcu * b * d / fs / 100.0  // cm^2
            asTensionFromConcrete = asTotal
        }

        // ==================== Min/Max Steel ====================
        val asMin = max(0.15 / 100.0 * b * d, 0.13 * fy / fcu * b * d / 100.0)  // cm^2
        val asMax = 0.04 * b * d / 100.0  // cm^2 (4% limit)

        // Adjust if below min
        if (asTotal < asMin) {
            asTotal = asMin
            warnings.add("As required less than As_min, increased to minimum")
        }
        if (asTotal > asMax) {
            warnings.add("As required exceeds As_max (4% limit)")
        }

        // ==================== Bar Selection ====================
        val tensionBarArea = PI * tensionBarDia * tensionBarDia / 4.0 / 100.0  // cm^2
        val compBarArea = PI * compBarDia * compBarDia / 4.0 / 100.0  // cm^2

        val nTension = ceil(asTotal / tensionBarArea).toInt().coerceIn(2, 8)
        val nComp = if (needsCompSteel) ceil(asCompression / compBarArea).toInt().coerceIn(2, 4) else 0

        val asProvidedTension = nTension * tensionBarArea
        val asProvidedComp = nComp * compBarArea

        val tensionBars = "${nTension}Ø${tensionBarDia}"
        val compressionBars = if (needsCompSteel) "${nComp}Ø${compBarDia}" else "None"

        // ==================== Neutral Axis ====================
        val aOverD = (1.0 - sqrt(max(0.0, 1.0 - 2.0 * k / ALPHA))) / ALPHA
        val na = aOverD * d / ALPHA

        // ==================== Safety Check ====================
        val utilizationRatio = if (kBal > 0) k / kBal else 2.0
        val isSafe = k <= kBal || needsCompSteel

        if (!isSafe) {
            warnings.add("Section over-reinforced: K > K_bal")
        }

        return DoublyReinforcedBeamResult(
            mu = mu,
            muMaxSingle = muMaxSingle,
            needsCompressionSteel = needsCompSteel,
            k = k,
            kBal = kBal,
            r = k,
            rBal = rBal,
            asRequired = asTotal,
            asCompression = if (needsCompSteel) asCompression else 0.0,
            asTensionFromConcrete = asTensionFromConcrete,
            asTensionFromCompression = if (needsCompSteel) asTensionFromCompression else 0.0,
            asMin = asMin,
            asMax = asMax,
            tensionBars = tensionBars,
            compressionBars = compressionBars,
            tensionBarCount = nTension,
            tensionBarDia = tensionBarDia,
            compressionBarCount = nComp,
            compressionBarDia = compBarDia,
            d = d,
            dPrime = dPrime,
            na = na,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio,
            warnings = warnings
        )
    }

    /**
     * Get minimum reinforcement ratio per SBC 304
     */
    fun getMinReinforcementRatio(fy: Double): Double {
        // ── InputGuard: loud failures, no silent zeros (ADR-010) ──
        InputGuard.positive("fy", fy)

        return 0.15 / 100.0  // ρ_min = 0.15% per SBC 304
    }
}