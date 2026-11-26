package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.FlatSlabDesign
import com.civileg.app.domain.calculations.base.WaffleSlabDesign
import com.civileg.app.domain.calculations.base.DesignCheckStatus
import com.civileg.app.domain.calculations.base.WaffleChecks
import com.civileg.app.domain.calculations.base.WaffleSlabDesign.WaffleSlabResult
import com.civileg.app.domain.calculations.base.WaffleSlabDesign.WaffleSlabInput
import com.civileg.app.domain.calculations.base.WaffleSlabDesign.RibDesignResult
import com.civileg.app.domain.calculations.base.WaffleSlabDesign.SolidHeadDesignResult
import com.civileg.app.domain.calculations.base.WaffleSlabDesign.PunchingShearCheck
import com.civileg.app.domain.RebarResult
import com.civileg.app.domain.SafetyCheckItem
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.entities.ShearReinforcementResult
import kotlin.math.*

/**
 * ACI 318 Waffle Slab Design Implementation
 *
 * References:
 *  - ACI 318 Ch.8: Two-way slabs (waffle slabs treated as two-way joist systems)
 *  - ACI 318 §9: Flexural design
 *  - ACI 318 §22: Shear design
 *  - ACI 318 §22.6: Punching shear
 *  - ACI 318 §24.2: Deflection control
 *
 * Key ACI differences from ECP:
 *  - Uses f'c (cylinder strength) = 0.8 * fcu
 *  - φ_flexure = 0.9, φ_shear = 0.75
 *  - Load combination: 1.2D + 1.6L
 *  - β1 = 0.85 for f'c ≤ 4000 psi (28 MPa)
 *  - A_s,min = max(0.25√f'c/fy, 1.4/fy) × b × d
 */
class ACIWaffleSlabDesign : WaffleSlabDesign {

    companion object {
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75
        private const val BETA_1 = 0.85
        private const val EPSILON_CU = 0.003
        private const val ES = 200000.0
        private const val CONCRETE_UNIT_WEIGHT = 24.0  // kN/m³ (ACI normal weight)
        private const val STEEL_UNIT_WEIGHT = 7850.0
        private const val PI = 3.141592653589793
    }

    override fun design(input: WaffleSlabInput): WaffleSlabResult {
        // ── InputGuard: loud failures, no silent zeros (ADR-010) ──
        InputGuard.notNull("input", input)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)
        InputGuard.positive("lx", input.lx)
        InputGuard.positive("ly", input.ly)
        InputGuard.positive("ribWidth", input.ribWidth)
        InputGuard.positive("ribHeight", input.ribHeight)
        InputGuard.positive("ribSpacing", input.ribSpacing)
        InputGuard.positive("columnWidth", input.columnWidth)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val safetyChecks = mutableListOf<SafetyCheckItem>()

        codeNotes.add(CodeReference.ACI.SLAB_TWO_WAY)
        codeNotes.add("ACI 318 Ch.8: Waffle Slab Design (Two-Way Joist System)")
        codeNotes.add("φ_flexure = $PHI_FLEXURE, φ_shear = $PHI_SHEAR")
        codeNotes.add("f'c = 0.8 × fcu = ${String.format("%.1f", input.fcu * 0.8)} MPa")

        val fc = input.fcu * 0.8  // cylinder strength

        // ── 1. Load combinations per ACI 318 §5.3.1 ───────────────
        val wu = 1.2 * input.deadLoad + 1.6 * input.liveLoad
        codeNotes.add("Wu = 1.2×DL + 1.6×LL = ${String.format("%.2f", wu)} kN/m²")

        // ── 2. Rib Design ───────────────────────────────────────────
        val ribDesign = designRib(
            fcu = input.fcu, fy = input.fy,
            ribWidth = input.ribWidth, ribHeight = input.ribHeight,
            ribSpacing = input.ribSpacing,
            clearSpan = input.lx,
            totalLoad = wu,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        // ── 3. Solid Head Design ────────────────────────────────────
        val solidHeadDesign = designSolidHead(
            fcu = input.fcu, fy = input.fy,
            solidHeadSize = input.solidHeadSize,
            ribWidth = input.ribWidth,
            columnSize = input.columnWidth,
            axialLoad = input.liveLoad * input.lx * input.ly / 1000000.0,
            moment = 0.0
        )

        // ── 4. Punching Shear Check ──────────────────────────────────
        val totalDepth = input.ribHeight + input.toppingThickness
        val punchVu = WaffleChecks.punchingDemand(wu, input.lx, input.ly, input.columnWidth, totalDepth, 25.0)
        val punchingShearCheck = checkPunchingShear(
            fcu = input.fcu, fy = input.fy,
            solidHeadSize = input.solidHeadSize,
            columnSize = input.columnWidth,
            axialLoad = punchVu,
            shear = punchVu,
            totalDepth = totalDepth
        )

        // ── 5. Deflection Check ──────────────────────────────────────
        val deflectionCheck = checkDeflection(
            span = input.lx,
            totalDepth = input.ribHeight + input.toppingThickness,
            ribWidth = input.ribWidth,
            ribHeight = input.ribHeight,
            fcu = input.fcu, fy = input.fy,
            providedAs = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        // ── 6. Quantities ────────────────────────────────────────────
        val numRibsX = max(1, (input.lx / input.ribSpacing).toInt())
        val numRibsY = max(1, (input.ly / input.ribSpacing).toInt())
        val ribVolume = (input.ribWidth * input.ribHeight * (input.lx + input.ly)) / 1e9
        val toppingVolume = (input.lx * input.ly * input.toppingThickness) / 1e9
        val solidHeadVolume = (input.solidHeadSize * input.solidHeadSize * 0.2) / 1e6 *
                (input.lx / input.ribSpacing * input.ly / input.ribSpacing)
        val concreteVolume = ribVolume + toppingVolume + solidHeadVolume
        val providedSteel = ribDesign.flexureReinforcement.providedArea
        val steelWeight = ((numRibsX * providedSteel * (input.ly / 1000.0) / 1e6) + (numRibsY * providedSteel * (input.lx / 1000.0) / 1e6)) * 7850.0

        val status = if (ribDesign.status == DesignCheckStatus.FAIL || punchingShearCheck.status == DesignCheckStatus.FAIL) DesignCheckStatus.FAIL else DesignCheckStatus.PRELIMINARY
        val notAssessed = mutableListOf(
            "Deflection: no supported method (the earlier fixed-section estimate was removed)",
            "Solid head flexure and reinforcement",
            "Punching at the solid-head edge, moment transfer, edge and corner columns"
        )
        if (ribDesign.status == DesignCheckStatus.PRELIMINARY || punchingShearCheck.status == DesignCheckStatus.PRELIMINARY) {
            notAssessed.add("ACI 318M coefficient tables (phi, Vc, vc, beta1, As,min) are not in the supplied text extract; constants kept from the existing code. Only 0.33 lambda sqrt(fc') (R22.6.5.2), eps_t >= 0.005 and eps_cu = 0.003 are in the supplied text.")
        }
        val punchUtil = if (punchingShearCheck.vc > 0) punchingShearCheck.vu / punchingShearCheck.vc else 0.0
        val utilizationRatio = maxOf(ribDesign.utilizationRatio, punchUtil)

        warnings.addAll(ribDesign.warnings)
        if (punchingShearCheck.note.isNotEmpty()) codeNotes.add("Punching: " + punchingShearCheck.note)
        codeNotes.addAll(ribDesign.notes)
        if (status == DesignCheckStatus.FAIL) warnings.add("FAIL: one or more computed ACI 318M checks failed")
        if (utilizationRatio > 1.0) warnings.add("Utilization ratio = ${String.format("%.2f", utilizationRatio)} > 1.0")
        notAssessed.forEach { warnings.add("NOT ASSESSED: " + it) }

        if (ribDesign.status != DesignCheckStatus.NOT_ASSESSED) {
            safetyChecks.add(SafetyCheckItem("Rib flexure and shear", ribDesign.utilizationRatio, 1.0, "ACI 318M (" + ribDesign.status.label + ")", passed = ribDesign.isSafe))
        }
        if (punchingShearCheck.status != DesignCheckStatus.NOT_ASSESSED) {
            safetyChecks.add(SafetyCheckItem("Punching shear (concentric)", punchUtil, 1.0, "ACI 318M (" + punchingShearCheck.status.label + ")", passed = punchingShearCheck.isSafe))
        }

        return WaffleSlabResult(
            isSafe = false, // never SAFE: deflection and solid head are not assessed
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes,
            safetyChecks = safetyChecks,
            ribDesign = ribDesign,
            solidHeadDesign = solidHeadDesign,
            punchingShearCheck = punchingShearCheck,
            deflectionCheck = deflectionCheck,
            concreteVolume = concreteVolume,
            steelWeight = steelWeight,
            cost = concreteVolume * 500.0 + steelWeight * 1.5,
            status = status,
            notAssessed = notAssessed,
            deflectionAssessed = false
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // RIB DESIGN — ACI 318
    // ════════════════════════════════════════════════════════════════════════════════════

    override fun designRib(
        fcu: Double,
        fy: Double,
        ribWidth: Double,
        ribHeight: Double,
        ribSpacing: Double,
        clearSpan: Double,
        totalLoad: Double,
        loadCombination: LoadCombination
    ): WaffleSlabDesign.RibDesignResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("ribWidth", ribWidth)
        InputGuard.positive("ribHeight", ribHeight)
        InputGuard.positive("ribSpacing", ribSpacing)
        InputGuard.positive("clearSpan", clearSpan)
        InputGuard.nonNegative("totalLoad", totalLoad)
        InputGuard.notNull("loadCombination", loadCombination)

        // totalLoad is the FACTORED panel load Wu in kN/m2 (already combined by design()); it is not factored again.
        val fc = fcu * 0.8
        val beta1 = if (fc <= 28.0) 0.85 else max(0.65, 0.85 - 0.05 * (fc - 28.0) / 7.0)
        return WaffleChecks.ribDesign(
            fc = fc, fy = fy, bw = ribWidth, h = ribHeight, ribSpacing = ribSpacing, spanMm = clearSpan,
            wuPanel = totalLoad, cover = 25.0, phiFlex = PHI_FLEXURE, phiShear = PHI_SHEAR,
            vcCoef = 0.17, vsMaxCoef = 0.66, beta1 = beta1,
            minAs = { _, b, dd -> max(0.25 * sqrt(fc) / fy, 1.4 / fy) * b * dd },
            passStatus = DesignCheckStatus.PRELIMINARY, codeRef = "ACI 318M"
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SOLID HEAD DESIGN
    // ════════════════════════════════════════════════════════════════════════════════════

    override fun designSolidHead(
        fcu: Double,
        fy: Double,
        solidHeadSize: Double,
        ribWidth: Double,
        columnSize: Double,
        axialLoad: Double,
        moment: Double
    ): WaffleSlabDesign.SolidHeadDesignResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("solidHeadSize", solidHeadSize)
        InputGuard.positive("columnSize", columnSize)
        InputGuard.nonNegative("axialLoad", axialLoad)
        // No supported solid-head method: report NOT ASSESSED with no adopted bars (no fixed or clamped values).
        return WaffleChecks.solidHeadNotAssessed()
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // PUNCHING SHEAR CHECK
    // ══════════════════════════════════════════════════════════════════════════════════════

    override fun checkPunchingShear(
        fcu: Double,
        fy: Double,
        solidHeadSize: Double,
        columnSize: Double,
        axialLoad: Double,
        shear: Double,
        totalDepth: Double
    ): WaffleSlabDesign.PunchingShearCheck {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("columnSize", columnSize)
        InputGuard.nonNegative("shear", shear)
        // `shear` is the factored punching demand Vu in kN; totalDepth is the slab depth at the column in mm.
        val fc = fcu * 0.8
        return WaffleChecks.punching(
            columnMm = columnSize, totalDepthMm = totalDepth, cover = 25.0, vuKN = shear, phiShear = PHI_SHEAR,
            nominalVc = { bo, d, beta -> minOf(0.33 * sqrt(fc) * bo * d / 1000.0, 0.17 * (1.0 + 2.0 / beta) * sqrt(fc) * bo * d / 1000.0, 0.083 * (2.0 + 40.0 * d / bo) * sqrt(fc) * bo * d / 1000.0) },
            passStatus = DesignCheckStatus.PRELIMINARY
        )
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // DEFLECTION CHECK
    // ═════════════════════════════════════════════════════════════════════════════════════

    override fun checkDeflection(
        span: Double,
        totalDepth: Double,
        ribWidth: Double,
        ribHeight: Double,
        fcu: Double,
        fy: Double,
        providedAs: Double,
        loadCombination: LoadCombination
    ): FlatSlabDesign.DeflectionResult {
        InputGuard.positive("span", span)
        InputGuard.positive("fcu", fcu)
        // No supported deflection method: the previous estimate used a fixed section and fixed loads.
        return WaffleChecks.deflectionNotAssessed()
    }
}