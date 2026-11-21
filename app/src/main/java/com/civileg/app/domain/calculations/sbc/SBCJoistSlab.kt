package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.HordiSlabDesign
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * One-way joist (ribbed / hordi) slab design per **SBC 304-CR-2018** (English
 * Code Requirements, Edition 200923).
 *
 * Clause provenance: docs/CLAUSE_LOG.md batch SJ-1…SJ-9 (2026-11-22), every
 * entry with the printed page of the owner's SBC 304-CR-18 PDF, cross-confirmed
 * against the owner's ACI 318M-14 PDF. The previous "SBC 304-2024" labelling
 * with ECP-style partial factors (γc/γs) and fabricated §8.4/§4.2/§4.3
 * numbering is removed wholesale.
 *
 * Edition policy (user decision 2026-11-22): implement the supplied English
 * 2018 copy now; edition differences isolated in the clause-referenced
 * constants below for a future 2024 upgrade. No 2024 compliance claim.
 *
 * HELD ITEM (SJ-7): the one-way shear capacity is NOT computed. The printed
 * Eq (22.5.5.1) (p. 174) duplicates the Eq (22.5.1.2) cross-section cap and is
 * circular as a definition of Vc; the discrepancy is unresolved (no official
 * erratum found). The result therefore carries shearCapacity = NaN,
 * isSafe = false and a BLOCKED warning: no overall design pass until SJ-7
 * resolves. The joist 1.1×Vc factor of §9.8.1.5 is likewise not applied.
 */
class SBCJoistSlab : HordiSlabDesign {

    companion object {
        // SJ-4 (Tables 21.2.1/21.2.2, printed p. 169)
        private const val PHI_FLEXURE = 0.90   // tension-controlled, Table 21.2.2 (e)/(f)

        // SJ-1 (§9.8.1.2-9.8.1.4, printed p. 72)
        private const val MIN_RIB_WIDTH = 100.0        // §9.8.1.2 (mm)
        private const val MAX_RIB_DEPTH_RATIO = 3.5    // §9.8.1.3
        private const val MAX_CLEAR_SPACING = 750.0    // §9.8.1.4 (mm)

        // SJ-2 (§9.8.2.1.1 / §9.8.3.1, printed p. 72)
        private const val MIN_TOPPING_OTHER_FORMS = 50.0      // §9.8.3.1 (mm)
        private const val MIN_TOPPING_STRUCT_FILLERS = 40.0   // §9.8.2.1.1 (mm)

        // Assumed construction values (not clause-verified in this batch)
        private const val MIN_COVER = 20.0       // UNVERIFIED: §20.6.1 not yet opened
        private const val HALF_BAR_DIA = 10.0    // assumed half bar diameter (mm)

        // SJ-8: SBC 304 is specified on cylinder strength fc′; the engine input
        // is cube strength fcu (UI semantics preserved). 0.8 is an approximation,
        // flagged in every result — not a code clause.
        private const val CUBE_TO_CYLINDER = 0.8

        // Bar diameters tried in order for the rib bottom reinforcement
        private val BAR_DIAMETERS = listOf(12.0, 14.0, 16.0, 18.0, 20.0)
        private const val MAX_BARS_PER_RIB = 4
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

        // ── Materials (SJ-8: cube input → cylinder approximation) ──
        val fcPrime = CUBE_TO_CYLINDER * fcu
        warnings.add(String.format(
            "SJ-8: SBC 304-CR-2018 is specified on cylinder strength fc′; input fcu=%.0f MPa (cube) converted with the 0.8 approximation (fc′=%.1f MPa) — UNVERIFIED conversion, enter the cylinder-equivalent strength where known",
            fcu, fcPrime))

        val d = totalThickness - MIN_COVER - HALF_BAR_DIA   // effective depth (mm)
        val hf = toppingThickness                            // flange thickness (mm)
        val bw = ribWidth                                  // web width (mm)
        val ln = span                                      // span is mm (interface contract)
        val clearSpacing = ribSpacing - ribWidth

        // ── SJ-3: effective flange width, Table 6.3.2.1 (T-beam, each side of web) ──
        val overhang = minOf(8.0 * hf, (ribSpacing - ribWidth) / 2.0, ln / 8.0)
        val beff = min(ribSpacing, bw + 2.0 * overhang)

        // ── SJ-1: §9.8 geometry checks ──
        val ribWidthOk = ribWidth >= MIN_RIB_WIDTH
        val ribDepthOk = (totalThickness - toppingThickness) <= MAX_RIB_DEPTH_RATIO * ribWidth
        val spacingOk = clearSpacing <= MAX_CLEAR_SPACING
        if (!ribWidthOk) {
            warnings.add(String.format("SBC 304-CR-18 §9.8.1.2: rib width %.0f mm < minimum 100 mm", ribWidth))
        }
        if (!ribDepthOk) {
            warnings.add(String.format("SBC 304-CR-18 §9.8.1.3: rib depth %.0f mm > 3.5 × width %.0f mm", totalThickness - toppingThickness, ribWidth))
        }
        if (!spacingOk) {
            warnings.add(String.format("SBC 304-CR-18 §9.8.1.4: clear rib spacing %.0f mm > maximum 750 mm", clearSpacing))
        }
        if (!ribWidthOk || !ribDepthOk || !spacingOk) {
            codeNotes.add("§9.8.1.8: joist construction outside 9.8.1.1-9.8.1.4 limits shall be designed as slabs and beams")
        }

        // ── SJ-2: topping thickness (no filler-type input → other-forms rule governs) ──
        val toppingRequired = max(clearSpacing / 12.0, MIN_TOPPING_OTHER_FORMS)   // §9.8.3.1
        val toppingOk = toppingThickness >= toppingRequired
        if (!toppingOk) {
            warnings.add(String.format("SBC 304-CR-18 §9.8.3.1: topping %.0f mm < required %.1f mm (greater of clear/12 and 50 mm)", toppingThickness, toppingRequired))
        }
        codeNotes.add(String.format("Topping rule applied: §9.8.3.1 (other forms) ≥ max(clear/12, 50 mm); with permanent structural fillers §9.8.2.1.1 permits ≥ max(clear/12, %.0f mm)", MIN_TOPPING_STRUCT_FILLERS))

        // ── Flexure per Ch 22 (SJ-4/SJ-6), strength format φMn ≥ Mu ──
        val beta1 = calculateBeta1(fcPrime)   // Table 22.2.2.4.3 (SJ-6)
        val mu = designMoment * 1e6           // kN·m → N·mm
        val mnReq = mu / PHI_FLEXURE

        val asReq: Double
        val aEff: Double   // stress-block depth of the governing case
        val aFlangeOnly = d - sqrt(max(0.0, d * d - 2.0 * mnReq / (0.85 * fcPrime * beff)))
        if (aFlangeOnly <= hf) {
            // Neutral axis in flange → rectangular section of width beff
            aEff = aFlangeOnly
            asReq = 0.85 * fcPrime * beff * aEff / fy
            codeNotes.add("Flexure: neutral axis in flange — rectangular section, beff per Table 6.3.2.1")
        } else {
            // T-section: overhang contribution + web contribution
            val cf = 0.85 * fcPrime * (beff - bw) * hf
            val mnWeb = mnReq - cf * (d - hf / 2.0)
            if (mnWeb <= 0) {
                aEff = hf
                asReq = cf / fy
                codeNotes.add("Flexure: flange overhangs alone carry the moment")
            } else {
                val aWeb = d - sqrt(max(0.0, d * d - 2.0 * mnWeb / (0.85 * fcPrime * bw)))
                aEff = (cf + 0.85 * fcPrime * bw * aWeb) / (0.85 * fcPrime * beff)
                asReq = (cf + 0.85 * fcPrime * bw * aWeb) / fy
                codeNotes.add("Flexure: T-section, neutral axis in web")
            }
        }

        // Tension-controlled check (Table 21.2.2: φ = 0.90 requires εt ≥ 0.005)
        val c = (if (aFlangeOnly <= hf) aFlangeOnly else aEff) / beta1
        val epsT = if (c > 0) 0.003 * (d - c) / c else 0.0
        val tensionControlled = epsT >= 0.005
        if (!tensionControlled) {
            warnings.add(String.format("Table 21.2.2: εt=%.4f < 0.005 — section not tension-controlled; φ=0.90 is unconservative, increase depth", epsT))
        }

        // ── SJ-5: minimum reinforcement §9.6.1.2 with the §9.6.1.3 exemption ──
        val asMin = max(0.25 * sqrt(fcPrime) / fy, 1.4 / fy) * bw * d
        val asDesign = max(asReq, min(asMin, 4.0 / 3.0 * asReq))

        // Tension-controlled upper bound (c/dt ≤ 0.375)
        val aMax = 0.375 * beta1 * d
        val asMax = if (aMax <= hf) {
            0.85 * fcPrime * beff * aMax / fy
        } else {
            (0.85 * fcPrime * (beff - bw) * hf + 0.85 * fcPrime * bw * aMax) / fy
        }
        val rhoOk = asDesign <= asMax
        if (!rhoOk) {
            warnings.add(String.format("Section over-reinforced for tension control: As=%.0f mm² > As,max=%.0f mm² — increase rib size", asDesign, asMax))
        }

        // ── Bar selection: smallest listed diameter giving ≤ 4 bars/rib ──
        var barDia = BAR_DIAMETERS.last()
        var nBars = 0
        for (dia in BAR_DIAMETERS) {
            val n = ceil(asDesign / (PI * dia * dia / 4.0)).toInt()
            if (n <= MAX_BARS_PER_RIB) {
                barDia = dia
                nBars = max(n, 1)
                break
            }
        }
        if (nBars == 0) {
            nBars = ceil(asDesign / (PI * barDia * barDia / 4.0)).toInt()
            warnings.add(String.format("Required steel needs %d nos. Φ%.0f in one rib — revise joist geometry", nBars, barDia))
        }
        val asProvided = nBars * PI * barDia * barDia / 4.0

        // ── SJ-7: shear BLOCKED — printed Eq (22.5.5.1) discrepancy unresolved ──
        warnings.add("BLOCKED (SJ-7): one-way shear capacity not computed — SBC 304-CR-18 printed Eq (22.5.5.1) (p. 174) duplicates the Eq (22.5.1.2) cap and is circular as a definition of Vc; the §9.8.1.5 joist 1.1×Vc factor is therefore not applied. No design pass until the discrepancy resolves (see CLAUSE_LOG SJ-7).")
        val shearCapacity = Double.NaN
        val utilization = Double.NaN

        // No overall pass while shear is blocked (and only if every other check passes)
        val geometrySafe = ribWidthOk && ribDepthOk && spacingOk && toppingOk
        val flexureSafe = tensionControlled && rhoOk
        val isSafe = false   // SJ-7 hold: never a design pass
        codeNotes.add(String.format("Sub-check status (not a pass): geometry=%s, flexure=%s, shear=BLOCKED(SJ-7)", if (geometrySafe) "ok" else "FAIL", if (flexureSafe) "ok" else "FAIL"))

        // ── SJ-9: detailing carried as notes (not computed) ──
        codeNotes.add("§9.8.1.6: provide ≥ 1 continuous bottom bar per joist, developed to fy at support faces")
        codeNotes.add("§9.8.1.7: provide reinforcement perpendicular to ribs per §24.4 (shrinkage/temperature)")

        codeNotes.add(0, "SBC 304-CR-2018 §9.8 one-way joist design (English CR; 2024 upgrade pending — no 2024 claim)")
        codeNotes.add(1, String.format("fc′=%.1f MPa (0.8·fcu approx), fy=%.0f MPa, φ=%.2f, β1=%.3f", fcPrime, fy, PHI_FLEXURE, beta1))
        codeNotes.add(2, String.format("beff=%.0f mm (Table 6.3.2.1), d=%.0f mm, As,req=%.0f mm², As,min=%.0f mm², As,prov=%.0f mm² (%dΦ%.0f)", beff, d, asReq, asMin, asProvided, nBars, barDia))

        return SlabDesignResult(
            requiredReinforcement = asReq,
            providedReinforcement = asProvided,
            barDiameter = barDia,
            barSpacing = ribSpacing,
            minThickness = totalThickness,
            shearCapacity = shearCapacity,
            isSafe = isSafe,
            utilizationRatio = utilization,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /** SJ-6: β1 per Table 22.2.2.4.3 (cylinder strength fc′, MPa). */
    private fun calculateBeta1(fcPrime: Double): Double = when {
        fcPrime <= 28.0 -> 0.85
        fcPrime >= 55.0 -> 0.65
        else -> 0.85 - 0.05 * (fcPrime - 28.0) / 7.0
    }
}
