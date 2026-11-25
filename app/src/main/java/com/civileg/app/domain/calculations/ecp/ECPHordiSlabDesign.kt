package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.BarBondClass
import com.civileg.app.domain.calculations.base.HordiSlabDesign
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.SlabDesignResult
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * ECP 203-2020 Hordi/Ribbed/Joist slab rib estimate (supplied primary scan).
 *
 * Limit-state format with embedded material factors (γc, γs) — no ACI φ factors.
 *  - Flexure: §4-2-1-2-أ Eq. 4-1/4-2, equivalent block on the rectangular WEB
 *    (no T-flange credit in this estimate), lever-arm cap y ≤ 0.95d (doc 4-5),
 *    ductility c/d ≤ Table 4-1 (doc 4-5/4-6).
 *  - Minimum steel: §4-2-1-2-ج Eq. 4-9 via ECP2020JoistRules (doc 4-9).
 *  - Shear: §4-2-2-1 concrete-only basis Eq. 4-29 (doc 4-18) through
 *    ECP2020JoistRules, gated by the shallow-rib exception §4-2-2-1-6-د-2
 *    (doc 4-22). The exception only waives the link requirement when eligible;
 *    the code does not prohibit links. Shear reinforcement DESIGN is outside
 *    this engine's scope, so ribs outside the exception or beyond the
 *    concrete-only capacity cannot be qualified here.
 *
 * Geometry inputs (caller-supplied or interface defaults 25/8/12, not code
 * minima): cover to link outside, link diameter, main bar diameter.
 * Effective depth d = t − cover − link − bar/2.
 * Bond class is inferred from fy (≤240 mild) because the interface carries no
 * bar-type input; aggregate-dependent bar spacing is unchecked.
 * The per-case grounded review (ECP2020JoistCaseReview) owns declared
 * detailing; this estimate never authorizes an adopted schedule.
 */
class ECPHordiSlabDesign : HordiSlabDesign {

    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
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
        loadCombination: LoadCombination,
        coverMm: Double,
        linkDiaMm: Double,
        mainBarDiaMm: Double,
        barBondClass: BarBondClass
    ): SlabDesignResult {
        // ── InputGuard (ADR-010) — ECP 203 ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("ribWidth", ribWidth)
        InputGuard.positive("ribSpacing", ribSpacing)
        InputGuard.positive("totalThickness", totalThickness)
        InputGuard.positive("toppingThickness", toppingThickness)
        InputGuard.nonNegative("designMoment", designMoment)
        InputGuard.nonNegative("designShear", designShear)
        InputGuard.require(toppingThickness < totalThickness, "Topping must be thinner than total slab thickness")
        InputGuard.positive("span", span)
        InputGuard.notNull("loadCombination", loadCombination)
        InputGuard.positive("coverMm", coverMm)
        InputGuard.positive("linkDiaMm", linkDiaMm)
        InputGuard.positive("mainBarDiaMm", mainBarDiaMm)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        codeNotes.add("${ECP2020JoistRules.EDITION} limit-state rib estimate; γc=$GAMMA_C, γs=$GAMMA_S embedded, no φ factors")
        codeNotes.add("Geometry inputs: cover ${coverMm.toInt()} mm to link outside, ${linkDiaMm.toInt()} mm link, ${mainBarDiaMm.toInt()} mm main bars — caller inputs or interface defaults, not code minima")

        // Bar surface/bond classification is an explicit input. fy alone does
        // not establish it; UNKNOWN takes the conservative Eq. 4-9 plain-bar
        // floor and is reported as an ESTIMATE, never as acceptance.
        val deformed = when (barBondClass) {
            BarBondClass.DEFORMED -> true
            BarBondClass.PLAIN -> false
            BarBondClass.UNKNOWN -> false // conservative envelope
        }
        when (barBondClass) {
            BarBondClass.UNKNOWN -> codeNotes.add("ESTIMATE, not acceptance: bar surface/bond UNKNOWN — Eq. 4-9 applies the conservative plain-bar floor (0.25%) for fy = ${fy.toInt()} N/mm²; declare barBondClass to qualify")
            else -> codeNotes.add("Bar surface declared by caller: $barBondClass — Eq. 4-9 ${if (deformed) "deformed-bar" else "plain-bar"} floor applied")
        }

        // Effective depth to the main-bar centre inside the link.
        val d = totalThickness - coverMm - linkDiaMm - mainBarDiaMm / 2.0
        InputGuard.positive("effectiveDepth(t - cover - link - bar/2)", d)

        val fc = fcu / GAMMA_C
        val fyd = min(fy, 500.0) / GAMMA_S

        // ── Flexure — §4-2-1-2-أ, Eq. 4-1/4-2 (rectangular web, no flange credit) ──
        val Mu = designMoment * 1e6 // N.mm
        val blockK = 0.67 * fc * ribWidth // 0.67·(fcu/γc)·b  [N/mm]
        val disc = blockK * d * blockK * d - 2.0 * blockK * Mu
        var flexureSafe = true
        val asCalc: Double
        if (disc < 0.0) {
            flexureSafe = false
            asCalc = Double.POSITIVE_INFINITY
            warnings.add("Flexure inadequate: applied Mu exceeds the singly reinforced web resistance per Eq. 4-1/4-2 — enlarge rib")
        } else {
            var solved = (blockK * d - sqrt(disc)) / fyd
            // Lever-arm cap y ≤ 0.95d (doc 4-5): shallow stress blocks use y = 0.95d.
            val aSolved = solved * fyd / blockK
            if (aSolved < 0.1 * d) solved = Mu / (fyd * 0.95 * d)
            asCalc = solved
        }

        // ── Minimum flexural steel — §4-2-1-2-ج, Eq. 4-9 (doc 4-9) ──
        val asMin = if (asCalc.isFinite()) {
            ECP2020JoistRules.minimumFlexuralSteelMm2(fcu, fy, ribWidth, totalThickness, d, asCalc, deformed)
        } else Double.POSITIVE_INFINITY
        val asRequired = max(asCalc, asMin)

        // ── Ductility / neutral-axis limit — Table 4-1 (doc 4-5/4-6) ──
        val cMaxOverD = when {
            fy <= 240.0 -> 0.50
            fy <= 350.0 -> 0.45
            fy <= 400.0 -> 0.42
            fy <= 420.0 -> 0.41
            else -> 0.39
        }
        if (asRequired.isFinite()) {
            val cOverD = asRequired * fyd / blockK / (0.8 * d)
            if (cOverD > cMaxOverD) {
                flexureSafe = false
                warnings.add("Rib exceeds the ductility neutral-axis limit: c/d = ${String.format("%.3f", cOverD)} > $cMaxOverD (ECP 203-2020 Table 4-1) — enlarge rib")
            }
        }

        // ── Discrete bar adoption — no silent count cap ──
        val barArea = PI * mainBarDiaMm * mainBarDiaMm / 4.0
        val nBars: Int
        val asProvided: Double
        if (asRequired.isFinite()) {
            nBars = ceil(asRequired / barArea).toInt().coerceAtLeast(1)
            asProvided = nBars * barArea
        } else {
            nBars = 0
            asProvided = 0.0
        }

        // ── Adopted-layout ductility — Table 4-1 re-evaluated on asProvided ──
        // Discrete bar rounding raises As above the required area; a required
        // area just under the c/d limit can round up across it (lead review).
        if (flexureSafe && asRequired.isFinite() && asProvided > 0.0) {
            val cOverDAdopted = asProvided * fyd / blockK / (0.8 * d)
            if (cOverDAdopted > cMaxOverD) {
                flexureSafe = false
                warnings.add("Adopted layout $nBars × ${mainBarDiaMm.toInt()} mm raises c/d to ${String.format("%.3f", cOverDAdopted)} > $cMaxOverD (ECP 203-2020 Table 4-1) although the required area passed — enlarge rib; compression reinforcement not modelled")
            }
        }

        // ── One-layer bar fit inside the adopted link — §7-3-3-1 bar-diameter term only ──
        if (asRequired.isFinite() && flexureSafe) {
            val insideWidth = ribWidth - 2.0 * (coverMm + linkDiaMm)
            val requiredWidth = nBars * mainBarDiaMm + (nBars - 1) * mainBarDiaMm
            if (requiredWidth > insideWidth) {
                flexureSafe = false
                warnings.add("$nBars × ${mainBarDiaMm.toInt()} mm bars need ${String.format("%.0f", requiredWidth)} mm > ${String.format("%.0f", insideWidth)} mm inside a ${ribWidth.toInt()} mm rib (clear spacing ≥ bar dia, §7-3-3-1) — multi-layer detailing not modelled")
            } else {
                codeNotes.add("One-layer bar fit checked against clear spacing ≥ bar dia only (§7-3-3-1); the 1.5×aggregate term is unchecked (no aggregate input)")
            }
        }

        // ── Shear — §4-2-2-1 concrete-only basis, shallow-rib exception §4-2-2-1-6-د-2 ──
        val concreteOnlyEligible = ECP2020JoistRules.concreteOnlyGeometryEligible(totalThickness, toppingThickness, ribWidth)
        val vc = ECP2020JoistRules.concreteOnlyShearKn(fcu, ribWidth, d) // Eq. 4-29
        var shearSafe = true
        if (!concreteOnlyEligible) {
            shearSafe = false
            warnings.add("Rib depth ${totalThickness.toInt()} mm exceeds the concrete-only exception of §4-2-2-1-6-د-2 (max(250, 2.5×topping, bw/2)); the exception only waives links when eligible — the code does not prohibit them, but shear reinforcement design is outside this engine's scope, so the rib cannot be qualified here")
        } else if (designShear > vc) {
            shearSafe = false
            warnings.add("Shear demand ${String.format("%.1f", designShear)} kN exceeds the concrete-only capacity ${String.format("%.1f", vc)} kN (Eq. 4-29); §4-2-2-1-6-د-2 does not prohibit links, but designing them is outside this engine's scope — enlarge rib")
        } else {
            codeNotes.add("Concrete-only shear basis (Eq. 4-29) under §4-2-2-1-6-د-2 shallow-rib exception")
        }

        val areaSufficient = com.civileg.app.domain.calculations.utils.HordiBarAdoption
            .areaIsSufficient(asRequired, asProvided)
        val shearUtilization = if (vc > 0) designShear / vc else Double.POSITIVE_INFINITY
        val areaUtilization = if (asProvided > 0) asRequired / asProvided else Double.POSITIVE_INFINITY
        val utilizationRatio = maxOf(shearUtilization, areaUtilization, if (flexureSafe) 0.0 else 2.0)
        // UNKNOWN bond class is an unassessed material input: it gates isSafe
        // (machine-readable), not just the notes — an estimate cannot be accepted.
        val bondAssessed = barBondClass != BarBondClass.UNKNOWN
        if (!bondAssessed) {
            warnings.add("Bar surface/bond class UNKNOWN — result is a conservative estimate and cannot be accepted; declare barBondClass to qualify")
        }
        val isSafe = flexureSafe && shearSafe && areaSufficient && bondAssessed

        if (!areaSufficient) warnings.add("Adopted main bars provide less than the calculated required steel area")
        codeNotes.add("Estimate scope: positive-moment rectangular-web rib section + concrete-only shear. Anchorage, T-flange credit, negative moment, serviceability and adopted detailing are outside this estimate — see ECP2020JoistCaseReview for declared cases.")
        codeNotes.add("Deflection/serviceability NOT assessed — explicit absence, not a pass (serviceabilityAssessed=false)")

        return SlabDesignResult(
            requiredReinforcement = if (asRequired.isFinite()) asRequired else Double.POSITIVE_INFINITY,
            providedReinforcement = asProvided,
            barDiameter = mainBarDiaMm,
            barSpacing = ribSpacing,
            minThickness = totalThickness,
            shearCapacity = vc,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes,
            bondClassAssessed = bondAssessed,
            barBondClass = barBondClass,
            serviceabilityAssessed = false
        )
    }
}
