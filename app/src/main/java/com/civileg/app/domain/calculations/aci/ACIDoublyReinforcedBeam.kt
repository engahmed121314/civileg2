package com.civileg.app.domain.calculations.aci

import kotlin.math.*

/**
 * Doubly Reinforced Beam Design per ACI 318-19
 *
 * When a singly reinforced section is insufficient (Mu > φMn_max for tension-controlled),
 * compression steel is added to increase the moment capacity.
 *
 * Key equations:
 * - φMn_max = φ * As_max * fy * (d - a/2)
 * - If Mu > φMn_max -> need compression steel
 * - As' = (Mu - φMn_max) / (fy * (d - d'))
 * - As = As1 (concrete balanced) + As' (compression steel couple)
 *
 * References:
 * - ACI 318-19 §9.3: Flexural design of beams
 * - ACI 318-19 §9.5: Minimum flexural reinforcement
 * - ACI 318-19 §9.6: Maximum reinforcement
 * - ACI 318-19 §22.4.2: Compression reinforcement
 */
data class DoublyReinforcedBeamResult(
    val mu: Double,                        // Applied ultimate moment (kN.m)
    val muMaxSingle: Double,               // Max moment for singly reinforced (kN.m)
    val needsCompressionSteel: Boolean,
    val k: Double,                         // Design factor K = Mu / (0.85*f'c*b*d^2)
    val kBal: Double,                      // Balanced K
    val r: Double,                         // R = Mu / (0.85*f'c*b*d^2)
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

class ACIDoublyReinforcedBeam {

    companion object {
        const val PHI_FLEXURE = 0.9       // ACI 318-19 §21.2.2.1
        const val PHI_SHEAR = 0.75        // ACI 318-19 §22.5.5.1
        const val BETA_1_LIMIT = 0.85     // ACI 318-19 §22.2.2.4.1
        const val E_S = 200000.0          // Modulus of elasticity of steel (MPa)
        const val EPSILON_CU = 0.003      // Maximum concrete strain at failure
    }

    /**
     * Design a doubly reinforced beam section
     *
     * @param mu Ultimate applied moment (kN.m)
     * @param b Beam width (mm)
     * @param h Beam total depth (mm)
     * @param fcPrime Concrete strength f'c (MPa)
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
        fcPrime: Double,
        fy: Double,
        cover: Double = 50.0,
        tensionBarDia: Int = 20,
        compBarDia: Int = 16
    ): DoublyReinforcedBeamResult {
        val warnings = mutableListOf<String>()

        // ==================== Section Geometry ====================
        val d = h - cover - tensionBarDia / 2.0     // Effective depth to tension steel
        val dPrime = cover + 16 / 2.0                // Depth to compression steel centroid (assuming 16mm)

        // ==================== Material Properties ====================
        val phi = PHI_FLEXURE
        val fcPrime = fcPrime                        // MPa (cylinder strength)
        val fs = fy                                  // MPa (ACI uses fy directly, no γs)

        // ==================== Convert Moment ====================
        val Mu = mu * 1e6  // kN.m -> N.mm

        // Max moment for singly reinforced section per ACI 318-19
        val rMax = calculateRMax(fcPrime, fy)
        val muMaxSingle = rMax * b * d * d / 1e6  // kN.m

        // ==================== Check if Compression Steel Needed ====================
        val needsCompressionSteel = mu > muMaxSingle

        // Min/Max Steel
        val asMinValue = max(0.25 * sqrt(fcPrime) / fy, 1.4 / fy) * b * d
        val asMaxValue = 0.025 * b * h

        if (!needsCompressionSteel) {
            // ========== SINGLY REINFORCED IS SUFFICIENT ==========
            val k = Mu / (PHI_FLEXURE * 0.85 * fcPrime * b * d * d)
            val discriminant = 1.0 - 2.0 * k
            val z = if (discriminant >= 0) {
                d * (1.0 - sqrt(discriminant)) / k.coerceAtLeast(1e-6)
            } else {
                d * 0.85
            }.coerceIn(0.7 * d, 0.95 * d)

            var asReq = Mu / (PHI_FLEXURE * fy * z)
            if (asReq < asMinValue) asReq = asMinValue

            val (tensCount, tensBarStr) = selectBars(asReq, 20)
            val utilizationRatio = (mu / muMaxSingle).coerceIn(0.01, 1.0)

            return DoublyReinforcedBeamResult(
                mu = mu,
                muMaxSingle = muMaxSingle,
                needsCompressionSteel = false,
                k = k,
                kBal = calculateKBal(fcPrime, fy),
                r = Mu / (b * d * d),
                rBal = rMax,
                asRequired = asReq / 100.0,
                asCompression = 0.0,
                asTensionFromConcrete = asReq / 100.0,
                asTensionFromCompression = 0.0,
                asMin = asMinValue / 100.0,
                asMax = asMaxValue / 100.0,
                tensionBars = tensBarStr,
                compressionBars = "None",
                tensionBarCount = tensCount,
                tensionBarDia = 20,
                compressionBarCount = 0,
                compressionBarDia = 0,
                d = d,
                dPrime = dPrime,
                na = 0.0,
                isSafe = true,
                utilizationRatio = utilizationRatio,
                warnings = mutableListOf()
            )
        }

        // ========== DOUBLY REINFORCED DESIGN ==========
        warnings.add("Mu > φMn_max: Compression steel required per ACI 318-19")

        val leverArmExcess = d - dPrime
        val muExcess = (mu - muMaxSingle) * 1e6 // N.mm

        val asCompressionValue = muExcess / (PHI_FLEXURE * fy * leverArmExcess)
        val asTensionConcrete = (muMaxSingle * 1e6) / (PHI_FLEXURE * fy * 0.85 * d)
        val asTensionFromCompValue = asCompressionValue
        var asTotal = asTensionConcrete + asTensionFromCompValue
        if (asTotal < asMinValue) asTotal = asMinValue

        val rVal = Mu / (b * d * d)
        val rBalVal = rMax

        // Apply minimum steel check
        if (asTotal < asMinValue) {
            asTotal = asMinValue
        }

        // Maximum steel check
        if (asTotal > asMaxValue) {
            warnings.add("Total steel ratio exceeds 2.5% per ACI 318-19 §9.6.1.2")
        }

        // ===== Select Bar Combinations =====
        val (tensCount, tensBarStr) = selectBars(asTotal, 20)
        val (compCount, compBarStr) = if (asCompressionValue > 0) {
            selectBars(asCompressionValue, 16)
        } else {
            0 to "None"
        }

        // Provided areas
        val tensBarArea = PI * (20.0 / 2.0).pow(2)
        val asTensionProvided = tensCount * tensBarArea

        val compBarArea = if (compBarDia > 0) PI * (16.0 / 2.0).pow(2) else 0.0
        val asCompProvided = compCount * compBarArea

        // Neutral Axis at Balanced Condition
        val na = EPSILON_CU / (EPSILON_CU + fy / E_S) * d

        // Capacity Check
        val capacityNmm = asTensionProvided * fy * d * (1.0 - 0.5 * asCompProvided / max(1.0, asTensionProvided)) + asCompProvided * fy * (d - dPrime)
        val capacity = capacityNmm / 1e6  // kN.m
        val utilizationRatio = if (capacity > 0) mu / capacity else 2.0

        val isSafe = utilizationRatio <= 1.0 && asTotal <= asMaxValue && leverArmExcess >= 40.0

        return DoublyReinforcedBeamResult(
            mu = mu,
            muMaxSingle = muMaxSingle,
            needsCompressionSteel = true,
            k = 0.0,
            kBal = calculateKBal(fcPrime, fy),
            r = rVal,
            rBal = rBalVal,
            asRequired = asTotal / 100.0,
            asCompression = asCompressionValue / 100.0,
            asTensionFromConcrete = asTensionConcrete / 100.0,
            asTensionFromCompression = asTensionFromCompValue / 100.0,
            asMin = asMinValue / 100.0,
            asMax = asMaxValue / 100.0,
            tensionBars = tensBarStr,
            compressionBars = compBarStr,
            tensionBarCount = tensCount,
            tensionBarDia = 20,
            compressionBarCount = compCount,
            compressionBarDia = 16,
            d = d,
            dPrime = dPrime,
            na = na,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio,
            warnings = warnings
        )
    }

    private fun selectBars(requiredArea: Double, barDia: Int): Pair<Int, String> {
        val barArea = PI * (barDia.toDouble() / 2.0).pow(2)
        val count = ceil(requiredArea / barArea).toInt().coerceIn(2, 12)
        return count to "${count}Ø$barDia"
    }

    fun calculateRMax(fcPrime: Double, fy: Double): Double {
        val beta1 = if (fcPrime <= 28.0) 0.85 else (0.85 - 0.05 * (fcPrime - 28.0) / 7.0).coerceAtLeast(0.65)
        val cOverDMax = EPSILON_CU / (EPSILON_CU + 0.005)
        val aOverD = beta1 * cOverDMax
        return PHI_FLEXURE * 0.85 * fcPrime * aOverD * (1.0 - 0.5 * aOverD)
    }

    fun calculateKBal(fcPrime: Double, fy: Double): Double {
        val beta1 = if (fcPrime <= 28.0) 0.85 else (0.85 - 0.05 * (fcPrime - 28.0) / 7.0).coerceAtLeast(0.65)
        val cOverDBal = EPSILON_CU / (EPSILON_CU + fy / E_S)
        return 0.85 * beta1 * (fcPrime / fy) * cOverDBal * (1.0 - 0.5 * 0.85 * cOverDBal)
    }
}