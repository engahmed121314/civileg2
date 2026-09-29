package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.HordiSlabDesign
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم البلاطات الهوردي (Joist / Ribbed Slab) حسب الكود السعودي SBC 304-2018
 *
 * تنفيذ كامل لـ SBC 304-2018 مع مراعاة الفروق الجوهرية عن ECP و ACI:
 *  - SBC 304-2018 §8.4: Ribbed / Joist slab provisions
 *  - SBC 304-2018 §4.2: Flexural design (partial-factor format γc=1.5, γs=1.15)
 *  - SBC 304-2018 §4.3: Shear design (معامل 0.25 بدل 0.24)
 *  - SBC 304-2018 §8.4.5: Effective flange width للـ T-section
 *  - SBC 304-2018 §8.4.6: Joist shear enhancement factor
 *  - SBC 304-2018 §8.4.7: Minimum topping thickness
 *  - SBC 304-2018 §4.2.2: Maximum reinforcement ratio
 *
 * الفروق الرئيسية عن ECP 203:
 *  - معامل القص: 0.25 بدل 0.24 (SBC 304 §4.3.2)
 *  - عرض الجناح الفعال محسوب حسب SBC §8.4.5
 *  - معامل تعزيز القص للجويسات القريبة (§8.4.6)
 *  - فحص سمك التوبينغ الأدنى (§8.4.7)
 *  - نسبة تسليح قصوى حسب SBC §4.2.2
 */
class SBCJoistSlab : HordiSlabDesign {

    companion object {
        // SBC 304 partial safety factors (mandated by SBC 304-2018 §4.2)
        private const val GAMMA_C = 1.5    // SBC 304-4.2.2: γc for concrete
        private const val GAMMA_S = 1.15   // SBC 304-4.2.2: γs for steel

        // SBC 304 reduction factors (used for geometry/safety checks)
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75

        // SBC 304-8.4.2: Minimum rib width (mm)
        private const val MIN_RIB_WIDTH = 100.0

        // SBC 304-8.4.3: Maximum rib depth-to-width ratio
        private const val MAX_RIB_DEPTH_RATIO = 3.5

        // SBC 304-8.4.1: Maximum clear spacing between ribs (mm)
        private const val MAX_CLEAR_SPACING = 700.0

        // Cover to reinforcement (mm) — SBC 304-4.4.1
        private const val MIN_COVER = 20.0

        // Assumed half-bar-diameter for effective depth (mm)
        private const val HALF_BAR_DIA = 10.0

        // SBC 304-8.4.7: Minimum topping slab thickness (mm)
        private const val MIN_TOPPING_THICKNESS = 50.0

        // SBC 304-4.3.2: Shear coefficient (0.25 per SBC, vs 0.24 in ECP)
        private const val SBC_SHEAR_COEFF = 0.25

        // SBC 304-4.2.2: Maximum reinforcement ratio for tension-controlled
        private const val MAX_REIN_RATIO = 0.04  // 4% gross area (SBC upper bound)
    }

    override fun designHordiSlab(
        fcu: Double,
        fy: Double,
        ribWidth: Double,
        ribSpacing: Double,
        totalThickness: Double,
        toppingThickness: Double,
        span: Double,
        designMoment: Double,
        designShear: Double,
        loadCombination: LoadCombination
    ): SlabDesignResult {
        // ── InputGuard: loud failures, no silent zeros (ADR-010) ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("ribWidth", ribWidth)
        InputGuard.positive("ribSpacing", ribSpacing)
        InputGuard.positive("totalThickness", totalThickness)
        InputGuard.positive("toppingThickness", toppingThickness)
        InputGuard.positive("span", span)
        InputGuard.nonNegative("designMoment", designMoment)
        InputGuard.nonNegative("designShear", designShear)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // ── Design strength per SBC 304 partial-factor approach ──
        val fc = fcu / GAMMA_C          // fcd = fcu / γc  (SBC 304-4.2.2)
        val fsd = fy / GAMMA_S          // fsd = fy  / γs  (SBC 304-4.2.2)
        val d = totalThickness - MIN_COVER - HALF_BAR_DIA  // effective depth
        val ribDepth = totalThickness - toppingThickness
        val hf = toppingThickness       // flange (topping) thickness

        // ── SBC 304-8.4.5: Effective flange width (T-section) ──
        // beff = min(ribSpacing, span/4 + ribWidth) per SBC 304 §8.4.5
        val beff = min(ribSpacing, span * 1000.0 / 4.0 + ribWidth)

        // ── SBC 304-8.4 geometry checks ──
        val isGeometrySafe = ribWidth >= MIN_RIB_WIDTH &&
                             ribDepth <= MAX_RIB_DEPTH_RATIO * ribWidth &&
                             (ribSpacing - ribWidth) <= MAX_CLEAR_SPACING &&
                             toppingThickness >= MIN_TOPPING_THICKNESS

        if (ribWidth < MIN_RIB_WIDTH) {
            warnings.add("SBC 304-8.4.2: Rib width ${String.format("%.0f", ribWidth)} mm < minimum ${String.format("%.0f", MIN_RIB_WIDTH)} mm")
        }
        if (ribDepth > MAX_RIB_DEPTH_RATIO * ribWidth) {
            warnings.add("SBC 304-8.4.3: Rib depth/width ratio exceeds ${MAX_RIB_DEPTH_RATIO}")
        }
        if ((ribSpacing - ribWidth) > MAX_CLEAR_SPACING) {
            warnings.add("SBC 304-8.4.1: Clear spacing between ribs ${String.format("%.0f", ribSpacing - ribWidth)} mm > maximum ${String.format("%.0f", MAX_CLEAR_SPACING)} mm")
        }

        // SBC 304-8.4.7: Minimum topping thickness check
        if (toppingThickness < MIN_TOPPING_THICKNESS) {
            warnings.add("SBC 304-8.4.7: Topping thickness ${String.format("%.0f", toppingThickness)} mm < minimum ${String.format("%.0f", MIN_TOPPING_THICKNESS)} mm")
        }

        // ── Flexure Design (SBC 304-4.2) with T-section logic ──
        val bw = ribWidth    // rib (web) width
        val Mu = designMoment * 1e6  // convert kN·m → N·mm

        // Check if neutral axis is in flange (rectangular section behavior)
        // Mu_flange = φ × 0.85 × fc × beff × hf × (d - hf/2)
        val MuFlange = PHI_FLEXURE * 0.85 * fc * beff * hf * (d - hf / 2.0)
        val isNeutralAxisInFlange = Mu <= MuFlange

        val AsReq: Double
        val rho: Double

        if (isNeutralAxisInFlange) {
            // ── Case 1: Neutral axis in flange → design as rectangular section with beff ──
            codeNotes.add("SBC 304-4.2: Neutral axis in flange — rectangular section with beff")

            val Rn = Mu / (PHI_FLEXURE * beff * d * d)
            val m = fsd / (0.85 * fc)
            val discriminant = 1.0 - 2.0 * m * Rn / fsd

            if (discriminant > 0) {
                rho = (1.0 - sqrt(discriminant)) / m
                AsReq = rho * beff * d
            } else {
                warnings.add("SBC 304: Compression failure — increase depth or fc")
                rho = 0.025
                AsReq = rho * beff * d
            }
        } else {
            // ── Case 2: Neutral axis in web → T-section design (SBC 304-4.2.3) ──
            codeNotes.add("SBC 304-4.2.3: Neutral axis in web — T-section design")

            // Moment capacity of flange: M_flange = 0.85 × fc × (beff - bw) × hf × (d - hf/2)
            val Mflange = 0.85 * fc * (beff - bw) * hf * (d - hf / 2.0)
            // Remaining moment on web: M_web = Mu/φ - M_flange
            val MuWeb = Mu / PHI_FLEXURE - Mflange

            if (MuWeb > 0) {
                // Design web as rectangular section for remaining moment
                val RnWeb = MuWeb / (bw * d * d)
                val m = fsd / (0.85 * fc)
                val discriminant = 1.0 - 2.0 * m * RnWeb / fsd

                if (discriminant > 0) {
                    val rhoWeb = (1.0 - sqrt(discriminant)) / m
                    val AsWeb = rhoWeb * bw * d

                    // Steel for flange: As_flange = 0.85 × fc × (beff - bw) × hf / fsd
                    val AsFlange = 0.85 * fc * (beff - bw) * hf / fsd

                    AsReq = AsWeb + AsFlange
                    rho = AsReq / (bw * d)
                } else {
                    warnings.add("SBC 304-4.2.3: Web compression failure — increase rib width or depth")
                    AsReq = 0.025 * bw * d + 0.85 * fc * (beff - bw) * hf / fsd
                    rho = AsReq / (bw * d)
                }
            } else {
                // Flange alone can carry the moment (unusual but possible)
                val Rn = Mu / (PHI_FLEXURE * beff * d * d)
                val m = fsd / (0.85 * fc)
                val discriminant = 1.0 - 2.0 * m * Rn / fsd
                rho = if (discriminant > 0) (1.0 - sqrt(discriminant)) / m else 0.025
                AsReq = rho * beff * d
            }
        }

        // ── SBC 304-4.2.2: Maximum reinforcement ratio check ──
        val rhoMax = min(MAX_REIN_RATIO, 0.375 * calculateBeta1(fcu) * fc / fsd)
        if (rho > rhoMax) {
            warnings.add(String.format("SBC 304-4.2.2: ρ=%.4f > ρ_max=%.4f — section may be over-reinforced", rho, rhoMax))
        }

        // ── Minimum reinforcement (SBC 304-8.4.4) ──
        // SBC 304 adopts: min As = max(0.15%·bw·d, 1.3%·As_required)
        val minAs = max(0.0015 * bw * d, 0.013 * AsReq)
        val AsDesign = maxOf(AsReq, minAs)

        // ── Bar selection ──
        val barDia = 12.0  // default bar diameter (mm)
        val barArea = PI * barDia * barDia / 4.0
        val nBars = ceil(AsDesign / barArea).toInt().coerceIn(1, 4)
        val AsProvided = nBars * barArea

        // ── Shear Design (SBC 304-4.3) ──
        // Vc = SBC_SHEAR_COEFF × √(fcu/γc) × bw × d / 1000 × φ_shear
        // Coefficient 0.25 per SBC 304 §4.3.2 (differs from ECP's 0.24)

        // SBC 304-8.4.6: Joist shear enhancement factor
        // When rib spacing ≤ 2 × rib width, Vc may be increased by up to 10%
        val joistShearFactor = if (ribSpacing <= 2.0 * ribWidth) {
            val factor = 1.0 + 0.1 * (2.0 * ribWidth - ribSpacing) / ribWidth
            min(factor, 1.1)  // cap at 1.1
        } else 1.0

        val Vc = SBC_SHEAR_COEFF * sqrt(fc) * bw * d / 1000.0 * PHI_SHEAR * joistShearFactor

        val isShearSafe = designShear <= Vc
        if (!isShearSafe) {
            warnings.add("SBC 304-4.3: Design shear ${String.format("%.1f", designShear)} kN exceeds capacity ${String.format("%.1f", Vc)} kN")
        }

        val isSafe = isGeometrySafe && isShearSafe && rho <= rhoMax
        val utilizationRatio = if (Vc > 0) designShear / Vc else 2.0

        // ── Code notes ──
        codeNotes.add("SBC 304-2018 §8.4: Hordi/Joist Slab Design")
        codeNotes.add(String.format("γc=%.2f, γs=%.2f (SBC 304-4.2.2)", GAMMA_C, GAMMA_S))
        codeNotes.add(String.format("fcu=%.0f MPa → fcd=%.1f MPa, fsd=%.1f MPa", fcu, fc, fsd))
        codeNotes.add(String.format("beff=%.0f mm (SBC 304-8.4.5), bw=%.0f mm", beff, bw))
        codeNotes.add(String.format("d=%.0f mm, As_req=%.1f mm², As_prov=%.1f mm² (%dΦ%.0f)", d, AsReq, AsProvided, nBars, barDia))
        codeNotes.add(String.format("Shear coeff=%.2f (SBC 304-4.3.2), joist factor=%.2f", SBC_SHEAR_COEFF, joistShearFactor))

        return SlabDesignResult(
            requiredReinforcement = AsReq,
            providedReinforcement = AsProvided,
            barDiameter = barDia,
            barSpacing = ribSpacing,
            minThickness = totalThickness,
            shearCapacity = Vc,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /**
     * β₁ factor per SBC 304 / ACI 318-19 §22.2.2.4.1
     * β₁ = 0.85 for fc' ≤ 28 MPa, reduces by 0.05 per 7 MPa above 28, min 0.65
     */
    private fun calculateBeta1(fcu: Double): Double {
        val fcPrime = 0.8 * fcu  // cylinder strength approximation
        return if (fcPrime <= 28.0) 0.85
        else (0.85 - 0.05 * (fcPrime - 28.0) / 7.0).coerceAtLeast(0.65)
    }
}
