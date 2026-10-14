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
        // ── InputGuard ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)

        val epsilonY = fy / (E_S * GAMMA_S)
        val aOverDBal = ALPHA * EPSILON_CU / (EPSILON_CU + epsilonY)
        val kBal = (0.67 / GAMMA_C) * aOverDBal * (1.0 - aOverDBal / 2.0)
        return kBal
    }

    /**
     * Calculate R_balanced = K_bal * (1 - 0.5 * K_bal)
     * This represents the normalized balanced moment coefficient.
     */
    fun calculateRBal(kBal: Double): Double {
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
        // ── InputGuard (ADR-010) — SBC 304-2018 ──
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

        // K_bal dynamic per SBC 304 (strain compatibility)
        val kBal = calculateKBal(fcu, fy)
        val rBal = calculateRBal(kBal)

        // ==================== Check if compression steel needed ====================
        val needsCompSteel = k > kBal

        // ==================== Maximum singly reinforced moment ====================
        val muMaxSingle = kBal * fcu * b * d * d / 1e6  // kN.m

        // ==================== Calculate Steel Areas ====================
        var asTensionFromConcrete = 0.0
        var asTensionFromCompression = 0.0
        var asCompression = 0.0
        var asTotal = 0.0

        if (needsCompSteel) {
            // Balanced moment resisted by concrete alone (K-method)
            val MuBalanced = kBal * fcu * b * d * d  // N.mm
            val zBalanced = d * (0.5 + sqrt(max(0.0, 0.25 - kBal / 0.893)))
            val asTensionConcrete = MuBalanced / (fs * zBalanced)  // mm^2

            // Excess moment resisted by the compression/tension steel couple
            val MuExcess = Mu - MuBalanced  // N.mm
            val leverArmExcess = d - dPrime
            // Compression steel stress by strain compatibility at balanced NA
            val cBalanced = (EPSILON_CU / (EPSILON_CU + fy / (E_S * GAMMA_S))) * d
            val fsPrime = minOf(EPSILON_CU * (1.0 - dPrime / cBalanced) * E_S, fs)

            val asComp = if (leverArmExcess > 0) MuExcess / (fsPrime * leverArmExcess) else 0.0  // mm^2

            asCompression = asComp / 100.0                              // cm^2
            asTensionFromCompression = (asComp * fsPrime / fs) / 100.0  // cm^2
            asTensionFromConcrete = asTensionConcrete / 100.0           // cm^2
            asTotal = asTensionFromConcrete + asTensionFromCompression
        } else {
            // Singly reinforced - lever arm per SBC K-method (0.893 = γc/(2×0.67))
            val z = d * (0.5 + sqrt(max(0.0, 0.25 - k / 0.893)))
            asTotal = Mu / (fs * z) / 100.0  // cm^2
            asTensionFromConcrete = asTotal
        }

        // ==================== Min/Max Steel ====================
        val asMin = max(0.26 * sqrt(fcu) / fy, 0.0013) * b * d / 100.0  // cm^2 (SBC 304 flexural minimum)
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

        // ==================== Neutral Axis (from concrete stress block) ====================
        val fcd = 0.67 * fcu / GAMMA_C  // design concrete strength (MPa)
        val compressionForce = if (needsCompSteel) asProvidedComp * 100.0 * fs else 0.0
        val netTensionForce = asProvidedTension * 100.0 * fs - compressionForce
        val na = max(0.0, netTensionForce / (fcd * b)) / 0.8

        // ==================== Safety Check ====================
        val utilizationRatio = if (kBal > 0) k / kBal else 2.0
        val isSafe = asTotal <= asMax

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
        return 0.15 / 100.0  // ρ_min = 0.15% per SBC 304
    }
}