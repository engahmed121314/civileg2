package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.HordiSlabDesign
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم البلاطات الهوردي (Joist / Ribbed Slab) حسب الكود السعودي SBC 304-2018
 *
 * هجين بين منهجية ECP (معاملات أمان γc, γs) ومراجع SBC 304:
 *  - SBC 304-2018 §8.4: Ribbed / Joist slab provisions
 *  - SBC 304-2018 §4.2: Flexural design (partial-factor format γc=1.5, γs=1.15)
 *  - SBC 304-2018 §4.3: Shear design
 *
 * SBC 304 adopts the ECP-style partial safety factor approach (γc=1.5, γs=1.15)
 * rather than the ACI φ-factor approach, so this implementation mirrors
 * ECPHordiSlabDesign but with SBC 304 code references and limits.
 */
class SBCJoistSlab : HordiSlabDesign {

    companion object {
        // SBC 304 partial safety factors (same values as ECP 203," +
        // " but mandated by SBC 304-2018 §4.2)
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

        // ── Design strength per* SBC 304 partial-factor2approach ──
        val fc = fcu / GAMMA_C          // fcd = fcu / γc  (SBC C304-4.2.2)
        val fsd = fy / GAMMA_S          // fsd = fy  / γs  (SBC C304-4.2.2)
        val d = totalThickness - MIN_COVER - HALF_BAR_DIA  // effective depth
        val ribDepth = totalThickness - toppingThickness

        // ── SBC 304-8.4 geometry checks ──
        val isGeometrySafe = ribWidth >= MIN_RIB_WIDTH &&
                             ribDepth <= MAX_RIB_DEPTH_RATIO * ribWidth &&
                             (ribSpacing - ribWidth) <= MAX_CLEAR_SPACING

        if (ribWidth < MIN_RIB_WIDTH) {
            warnings.add("SBC 304-8.4.2: Rib width ${String.format("%.0f", ribWidth)} mm < minimum ${String.format("%.0f", MIN_RIB_WIDTH)} mm")
        }
        if (ribDepth > MAX_RIB_DEPTH_RATIO * ribWidth) {
            warnings.add("SBC 304-8.4.3: Rib depth/width ratio exceeds ${MAX_RIB_DEPTH_RATIO}")
        }
        if ((ribSpacing - ribWidth) > MAX_CLEAR_SPACING) {
            warnings.add("SBC 304-8.4.1: Clear spacing between ribs ${String.format("%.0f", ribSpacing - ribWidth)} mm > maximum ${String.format("%.0f", MAX_CLEAR_SPACING)} mm")
        }

        // ── Flexure Design (SBC 304-4.2) ──
        val b = ribSpacing   // effective flange width for T-section design
        val bw = ribWidth    // rib (web) width
        val Mu = designMoment * 1e6  // convert kN·m → N·mm

        // Rn approach: Mu / (φ · bw · d²)
        val Rn = Mu / (PHI_FLEXURE * bw * d * d)

        // Reinforcement ratio via Rn-ρ method
        val m = fsd / (0.85 * fc)
        val discriminant = 1.0 - 2.0 * m * Rn / fsd
        val rho = if (discriminant > 0) {
            (1.0 - sqrt(discriminant)) / m
        } else {
            warnings.add("SBC 304: Compression failure — increase depth or fc")
            0.025  // cap to maximum practical ratio
        }
        val AsReq = rho * bw * d

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
        // Vc = 0.24 · √(fcu/γc) · bw · d / 1000  (kN)
        // Coefficient 0.24 per SBC 304 shear provision (consistent with ECP-style)
        val Vc = 0.24 * sqrt(fc) * bw * d / 1000.0 * PHI_SHEAR

        val isShearSafe = designShear <= Vc
        if (!isShearSafe) {
            warnings.add("SBC 304-4.3: Design shear ${String.format("%.1f", designShear)} kN exceeds capacity ${String.format("%.1f", Vc)} kN")
        }

        val isSafe = isGeometrySafe && isShearSafe && discriminant > 0
        val utilizationRatio = if (Vc > 0) designShear / Vc else 2.0

        // ── Code notes ──
        codeNotes.add("SBC 304-2018 §8.4: Hordi/Joist Slab Design")
        codeNotes.add(String.format("γc=%.2f, γs=%.2f (SBC 304-4.2.2)", GAMMA_C, GAMMA_S))
        codeNotes.add(String.format("fcu=%.0f MPa → fcd=%.1f MPa, fsd=%.1f MPa", fcu, fc, fsd))
        codeNotes.add(String.format("d=%.0f mm, As_req=%.1f mm², As_prov=%.1f mm² (%dΦ%.0f)", d, AsReq, AsProvided, nBars, barDia))

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
}
