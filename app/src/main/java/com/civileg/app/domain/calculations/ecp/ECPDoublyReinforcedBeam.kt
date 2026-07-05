package com.civileg.app.domain.calculations.ecp

import kotlin.math.*

/**
 * Doubly Reinforced Beam Design per ECP 203-2020
 *
 * When a singly reinforced section is insufficient (Mu > Mu_max for tension-controlled),
 * compression steel is added to increase the moment capacity.
 *
 * Key equations:
 * - K_bal = 0.36 * (fcu / (fy * gamma_s)) * (gamma_c / (1 + (fy / (1.15 * 440))))
 * - R_bal = K_bal * (1 - 0.5 * K_bal)
 * - If R > R_bal -> need compression steel
 * - As' = (Mu - R_bal * fcu * b * d^2) / (fy/gamma_s * (d - d'))
 * - As = (K_bal * fcu * b * d) / (fy/gamma_s) + As'
 *
 * Where:
 * - fcu = concrete compressive strength (MPa)
 * - fy = steel yield strength (MPa)
 * - gamma_c = 1.5 (concrete safety factor per ECP)
 * - gamma_s = 1.15 (steel safety factor per ECP)
 * - d = effective depth
 * - d' = depth to compression steel
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
    val asRequired: Double,                // Total tension steel area (cm^2)
    val asCompression: Double,             // Compression steel area (cm^2)
    val asTensionFromConcrete: Double,     // Tension steel balanced with concrete (cm^2)
    val asTensionFromCompression: Double,  // Additional tension steel for compression steel (cm^2)
    val asMin: Double,                     // Minimum steel area (cm^2)
    val asMax: Double,                     // Maximum steel area (cm^2)
    val tensionBars: String,               // e.g. "5\u03A620"
    val compressionBars: String,           // e.g. "3\u03A616"
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
    }

    /**
     * Calculate K_balanced per ECP 203-2020
     * K_bal = 0.36 * (fcu / (fy/gamma_s)) * (gamma_c / (1 + fy/(gamma_s * 440)))
     *
     * This formula derives from the strain compatibility condition at the balanced
     * state, where the concrete reaches its ultimate strain (0.003) simultaneously
     * with the tension steel reaching its yield strain.
     */
    fun calculateKBal(fcu: Double, fy: Double): Double {
        val fyGammaS = fy / GAMMA_S
        val kBal = 0.36 * (fcu / fyGammaS) * (GAMMA_C / (1 + fy / (GAMMA_S * 440)))
        return kBal
    }

    /**
     * Calculate K_balanced using strain compatibility method (alternative, more rigorous).
     * ECP 203 Clause 4-2-2-1:
     *   K_bal = (0.67/gamma_c) * (a/d) * (1 - a/(2d))
     *   where a/d = beta * epsilon_cu / (epsilon_cu + fy/(Es * gamma_s))
     *
     * @return K_bal value
     */
    fun calculateKBalStrainCompatibility(fcu: Double, fy: Double): Double {
        val epsilonY = fy / (E_S * GAMMA_S)
        val cOverD = EPSILON_CU / (EPSILON_CU + epsilonY)
        val beta = 0.8  // Whitney stress block factor per ECP 203
        val aOverD = beta * cOverD
        return (0.67 / GAMMA_C) * aOverD * (1.0 - aOverD / 2.0)
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
        val warnings = mutableListOf<String>()

        // ==================== Section Geometry ====================
        val d = h - cover - tensionBarDia / 2.0     // Effective depth to tension steel
        val dPrime = cover + compBarDia / 2.0        // Depth to compression steel centroid

        // ==================== Material Properties ====================
        val fs = fy / GAMMA_S                          // Design yield stress (MPa)
        val fc = 0.67 * fcu / GAMMA_C                  // Design concrete strength (MPa)

        // ==================== Convert Moment ====================
        val Mu = mu * 1e6  // kN.m -> N.mm

        // ==================== K and R Factors ====================
        // K = Mu / (fcu * b * d^2) - standard K-factor per ECP 203
        val k = Mu / (fcu * b * d * d)

        // R = Mu / (fcu/gamma_c * b * d^2) - normalized moment coefficient
        val fcReduced = fcu / GAMMA_C
        val r = Mu / (fcReduced * b * d * d)

        // Balanced factors (dynamically calculated)
        val kBal = calculateKBal(fcu, fy)
        val rBal = calculateRBal(kBal)

        // ==================== Check if Compression Steel Needed ====================
        val needsCompressionSteel = r > rBal

        // Max moment for singly reinforced section
        val muMaxSingle = rBal * fcReduced * b * d * d / 1e6  // N.mm -> kN.m

        // ==================== Min/Max Steel ====================
        // ECP 203 Clause 4-2-1-2: As_min = max(0.26*fcu/fy, 0.0013) * b * d
        val asMinValue = max(0.26 * (fcu / fy), 0.0013) * b * d
        // ECP 203: As_max = 0.04 * b * h
        val asMaxValue = 0.04 * b * h

        // ==================== Neutral Axis Depth ====================
        val epsilonY = fy / (E_S * GAMMA_S)
        val naBalanced = (EPSILON_CU / (EPSILON_CU + epsilonY)) * d

        if (!needsCompressionSteel) {
            // ========== SINGLY REINFORCED IS SUFFICIENT ==========
            // Lever arm: z = d * (0.5 + sqrt(0.25 - K/1.25)) per ECP 203
            val discriminant = 0.25 - k / 1.25
            val z = if (discriminant >= 0) {
                d * (0.5 + sqrt(discriminant))
            } else {
                d * 0.7  // Fallback for numerical edge cases
            }

            // Required tension steel
            var asReq = Mu / (fs * z)

            // Apply minimum steel
            if (asReq < asMinValue) {
                asReq = asMinValue
                warnings.add("Minimum reinforcement applied per ECP 203 Clause 4-2-1-2")
            }

            // Neutral axis depth for current reinforcement
            val aSingly = (asReq * fs) / (0.67 * fcReduced * b)
            val na = aSingly / 0.8  // c = a / beta_1

            // Select bars
            val (tensCount, tensBarStr) = selectBars(asReq, tensionBarDia)

            // Utilization ratio
            val utilizationRatio = if (muMaxSingle > 0) mu / muMaxSingle else 2.0

            return DoublyReinforcedBeamResult(
                mu = mu,
                muMaxSingle = muMaxSingle,
                needsCompressionSteel = false,
                k = k,
                kBal = kBal,
                r = r,
                rBal = rBal,
                asRequired = asReq / 100.0,               // mm^2 -> cm^2
                asCompression = 0.0,
                asTensionFromConcrete = asReq / 100.0,
                asTensionFromCompression = 0.0,
                asMin = asMinValue / 100.0,
                asMax = asMaxValue / 100.0,
                tensionBars = tensBarStr,
                compressionBars = "None",
                tensionBarCount = tensCount,
                tensionBarDia = tensionBarDia,
                compressionBarCount = 0,
                compressionBarDia = 0,
                d = d,
                dPrime = dPrime,
                na = na,
                isSafe = utilizationRatio <= 1.0 && asReq <= asMaxValue,
                utilizationRatio = utilizationRatio,
                warnings = warnings
            )
        }

        // ========== DOUBLY REINFORCED DESIGN ==========
        warnings.add("K > K_bal: Compression steel required per ECP 203 Clause 4-2-2-2")

        // Check d - d' for effective compression steel action
        val leverArmExcess = d - dPrime
        if (leverArmExcess < 40.0) {
            warnings.add("d - d' = ${"%.0f".format(leverArmExcess)} mm is small; increase section depth or reduce cover")
        }

        // Balanced moment capacity (moment resisted by concrete alone at balance)
        val MuBalanced = rBal * fcReduced * b * d * d  // N.mm

        // Lever arm at balanced condition
        // From R_bal = K_bal * (1 - K_bal/2), the neutral axis ratio is K_bal
        // z_bal = d * (1 - K_bal / 2)
        val zBalanced = d * (1.0 - kBal / 2.0)

        // ===== Tension steel from concrete (balanced portion) =====
        // As1 = Mu_balanced / (fs * z_balanced)
        val asTensionConcrete = MuBalanced / (fs * zBalanced)

        // ===== Excess moment to be resisted by steel couple =====
        val MuExcess = Mu - MuBalanced

        // ===== Compression steel area =====
        // As' = Mu_excess / (fs * (d - d'))
        val asCompressionValue = if (leverArmExcess > 0) {
            MuExcess / (fs * leverArmExcess)
        } else {
            0.0
        }

        // Additional tension steel to balance compression steel
        val asTensionFromCompValue = asCompressionValue

        // Total tension steel
        var asTotal = asTensionConcrete + asTensionFromCompValue

        // Apply minimum steel check
        if (asTotal < asMinValue) {
            asTotal = asMinValue
            warnings.add("Minimum reinforcement applied per ECP 203 Clause 4-2-1-2")
        }

        // Maximum steel check
        if (asTotal > asMaxValue) {
            warnings.add("Total steel ratio exceeds 4% per ECP 203 - consider increasing section")
        }

        // ===== Select Bar Combinations =====
        val (tensCount, tensBarStr) = selectBars(asTotal, tensionBarDia)
        val (compCount, compBarStr) = if (asCompressionValue > 0) {
            selectBars(asCompressionValue, compBarDia)
        } else {
            0 to "None"
        }

        // Provided areas
        val tensBarArea = PI * (tensionBarDia.toDouble() / 2.0).pow(2)
        val asTensionProvided = tensCount * tensBarArea

        val compBarArea = if (compBarDia > 0) PI * (compBarDia.toDouble() / 2.0).pow(2) else 0.0
        val asCompProvided = compCount * compBarArea

        // ===== Neutral Axis at Balanced Condition =====
        // The neutral axis is maintained at the balanced depth (since K_bal is enforced)
        val na = naBalanced

        // ===== Capacity Check =====
        // phi*Mn = As1 * fs * z_bal + As' * fs * (d - d')
        val capacityNmm = asTensionConcrete * fs * zBalanced + asCompProvided * fs * leverArmExcess
        val capacity = capacityNmm / 1e6  // kN.m
        val utilizationRatio = if (capacity > 0) mu / capacity else 2.0

        // ===== Development Length Check =====
        // fbd = 0.6 * sqrt(fcu) for deformed bars per ECP 203
        val fbd = 0.6 * sqrt(fcu)
        if (tensionBarDia > 0) {
            val laTension = 0.5 * fy * tensionBarDia / fbd.coerceAtLeast(0.1)
            if (laTension > d * 0.8) {
                warnings.add("Tension development length L_a = ${"%.0f".format(laTension)} mm may be excessive")
            }
        }
        if (compBarDia > 0 && asCompressionValue > 0) {
            val laComp = 0.5 * fy * compBarDia / fbd.coerceAtLeast(0.1)
            if (laComp > d * 0.6) {
                warnings.add("Compression development length L_a = ${"%.0f".format(laComp)} mm - verify anchorage")
            }
        }

        // ===== Spacing Check =====
        val clearSpacing = if (tensCount > 1) {
            (b - 2 * cover - 2 * 10 - tensCount * tensionBarDia) / (tensCount - 1)
        } else {
            b - 2 * cover
        }
        if (clearSpacing < 25 || (tensCount > 1 && clearSpacing < tensionBarDia)) {
            warnings.add("Bar spacing ${"%.0f".format(clearSpacing)} mm is tight - consider two layers or larger bars")
        }

        val isSafe = utilizationRatio <= 1.0 && asTotal <= asMaxValue && leverArmExcess >= 40.0

        return DoublyReinforcedBeamResult(
            mu = mu,
            muMaxSingle = muMaxSingle,
            needsCompressionSteel = true,
            k = k,
            kBal = kBal,
            r = r,
            rBal = rBal,
            asRequired = asTotal / 100.0,                        // mm^2 -> cm^2
            asCompression = asCompressionValue / 100.0,           // mm^2 -> cm^2
            asTensionFromConcrete = asTensionConcrete / 100.0,    // mm^2 -> cm^2
            asTensionFromCompression = asTensionFromCompValue / 100.0, // mm^2 -> cm^2
            asMin = asMinValue / 100.0,
            asMax = asMaxValue / 100.0,
            tensionBars = tensBarStr,
            compressionBars = compBarStr,
            tensionBarCount = tensCount,
            tensionBarDia = tensionBarDia,
            compressionBarCount = compCount,
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
     * Check whether a given section and moment requires compression steel.
     * Useful for quick preliminary checks.
     *
     * @param mu Applied ultimate moment (kN.m)
     * @param b Section width (mm)
     * @param d Effective depth (mm)
     * @param fcu Concrete strength (MPa)
     * @param fy Steel yield strength (MPa)
     * @return true if compression steel is required
     */
    fun needsCompressionSteel(
        mu: Double,
        b: Double,
        d: Double,
        fcu: Double,
        fy: Double
    ): Boolean {
        val Mu = mu * 1e6
        val fcReduced = fcu / GAMMA_C
        val r = Mu / (fcReduced * b * d * d)
        val kBal = calculateKBal(fcu, fy)
        val rBal = calculateRBal(kBal)
        return r > rBal
    }

    /**
     * Quick capacity estimate for a doubly reinforced section.
     *
     * @param b Section width (mm)
     * @param d Effective depth (mm)
     * @param dPrime Depth to compression steel (mm)
     * @param fcu Concrete strength (MPa)
     * @param fy Steel yield strength (MPa)
     * @param asTension Provided tension steel area (mm^2)
     * @param asCompression Provided compression steel area (mm^2)
     * @return Moment capacity in kN.m
     */
    fun calculateCapacity(
        b: Double,
        d: Double,
        dPrime: Double,
        fcu: Double,
        fy: Double,
        asTension: Double,
        asCompression: Double
    ): Double {
        val fs = fy / GAMMA_S
        val fc = 0.67 * fcu / GAMMA_C

        // Compression force in concrete
        val a = (asTension * fs - asCompression * fs) / (fc * b).coerceAtLeast(1.0)
        val aClamped = a.coerceIn(0.0, 0.8 * d)

        // Lever arm
        val z = d - aClamped / 2.0

        // Moment capacity
        val Mn = asTension * fs * z + asCompression * fs * (d - dPrime)
        return Mn / 1e6  // kN.m
    }

    /**
     * Generate bar alternatives for a given required area.
     *
     * @param requiredArea Required steel area (mm^2)
     * @param preferredDia Preferred bar diameter (mm), default 0 = auto-select
     * @return List of alternative bar combinations as strings
     */
    fun getBarAlternatives(
        requiredArea: Double,
        preferredDia: Int = 0
    ): List<String> {
        val alternatives = mutableListOf<String>()
        val availableBars = listOf(10, 12, 14, 16, 18, 20, 22, 25, 28, 32)

        for (dia in availableBars) {
            val barArea = PI * (dia.toDouble() / 2.0).pow(2)
            val count = ceil(requiredArea / barArea).toInt().coerceIn(2, 12)
            val provided = count * barArea
            val ratio = requiredArea / provided
            if (ratio in 0.5..1.0) {
                alternatives.add("${count}\u03A6${dia} (${(ratio * 100).toInt()}% used)")
            }
        }

        return alternatives.take(5)
    }

    // ==================== Private Helpers ====================

    /**
     * Select bar combination for required area.
     * Returns (count, barString).
     */
    private fun selectBars(requiredArea: Double, barDia: Int): Pair<Int, String> {
        val barArea = PI * (barDia.toDouble() / 2.0).pow(2)  // mm^2
        val count = ceil(requiredArea / barArea).toInt().coerceAtLeast(2)
        return count to "${count}\u03A6${barDia}"
    }

    /**
     * Calculate bond stress per ECP 203.
     * fbd = 0.6 * sqrt(fcu) for deformed bars
     * fbd = 0.3 * sqrt(fcu) for plain bars
     */
    private fun calculateBondStress(fcu: Double, isDeformed: Boolean = true): Double {
        val factor = if (isDeformed) 0.6 else 0.3
        return factor * sqrt(fcu)
    }

    /**
     * Calculate development length per ECP 203.
     * La = 0.5 * fy * dia / fbd
     */
    private fun calculateDevelopmentLength(fy: Double, dia: Double, fcu: Double): Double {
        val fbd = calculateBondStress(fcu)
        return 0.5 * fy * dia / fbd.coerceAtLeast(0.1)
    }
}