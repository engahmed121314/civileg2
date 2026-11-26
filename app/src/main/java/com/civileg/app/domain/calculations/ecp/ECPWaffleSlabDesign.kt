package com.civileg.app.domain.calculations.ecp

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
 * ECP 203 Waffle Slab Design Implementation
 *
 * References:
 *  - ECP 203 §6-4: Waffle slabs (ribbed slabs with solid heads)
 *  - ECP 203 §4-2: Flexural design
 *  - ECP 203 §4-3: Shear design
 *  - ECP 203 §6-3: Deflection control
 *
 * Waffle slabs are two-way ribbed slabs with ribs in two directions and solid heads at columns.
 * Design includes:
 *  - Rib design (flexure, shear)
 *  - Solid head design (column heads)
 *  - Topping/slab design
 *  - Punching shear at column heads
 *  - Deflection check
 */
class ECPWaffleSlabDesign : WaffleSlabDesign {

    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75
        private const val EPSILON_CU = 0.003
        private const val ES = 200000.0
        private const val CONCRETE_UNIT_WEIGHT = 25.0
        private const val STEEL_UNIT_WEIGHT = 7850.0
        private const val MIN_RIB_WIDTH = 100.0
        private const val MIN_TOPPING = 50.0
        private const val MAX_RIB_SPACING = 600.0
        private const val COVER = 20.0
        private const val PI = 3.141592653589793
    }

    override fun design(input: WaffleSlabInput): WaffleSlabResult {
        // ── InputGuard (ADR-010) — ECP 203 ──
        InputGuard.notNull("input", input)
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)
        InputGuard.positive("lx", input.lx)
        InputGuard.positive("ly", input.ly)
        InputGuard.positive("ribHeight", input.ribHeight)
        InputGuard.positive("ribWidth", input.ribWidth)
        InputGuard.nonNegative("deadLoad", input.deadLoad)
        InputGuard.nonNegative("liveLoad", input.liveLoad)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val safetyChecks = mutableListOf<SafetyCheckItem>()

        codeNotes.add(CodeReference.ECP.SLAB_TWO_WAY)
        codeNotes.add("ECP 203 §6-4: Waffle Slab Design")
        codeNotes.add("γc = $GAMMA_C, γs = $GAMMA_S, φ_flex = $PHI_FLEXURE, φ_shear = $PHI_SHEAR")

        // ── 1. Input validation ─────────────────────────────────────
        if (input.lx <= 0 || input.ly <= 0) {
            return WaffleSlabResult(
                isSafe = false, utilizationRatio = 2.0,
                warnings = listOf("Invalid spans"),
                concreteVolume = 0.0, steelWeight = 0.0, cost = 0.0
            )
        }

        // ── 2. Geometry validation ──────────────────────────────────
        val ribSpacing = input.ribSpacing
        if (input.ribSpacing > 600) {
            warnings.add("Rib spacing ${input.ribSpacing.toInt()} mm exceeds ECP 203 limit of 600 mm")
        }

        // ── 3. Load calculations ────────────────────────────────────
        val wu = getFactoredLoad(input.deadLoad, input.liveLoad) // kN/m²
        codeNotes.add("Wu = 1.4×DL + 1.6×LL = ${String.format("%.2f", wu)} kN/m²")

        // ── 4. Rib Design ───────────────────────────────────────────
        val ribDesign = designRib(
            fcu = input.fcu,
            fy = input.fy,
            ribWidth = input.ribWidth,
            ribHeight = input.ribHeight,
            ribSpacing = input.ribSpacing,
            clearSpan = input.lx,
            totalLoad = wu,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        // ── 5. Solid Head Design ────────────────────────────────────
        val solidHeadDesign = designSolidHead(
            fcu = input.fcu,
            fy = input.fy,
            solidHeadSize = input.solidHeadSize,
            ribWidth = input.ribWidth,
            columnSize = input.columnWidth,
            axialLoad = input.liveLoad * input.lx * input.ly / 1000000.0,
            moment = 0.0
        )

        // ── 6. Punching Shear Check ──────────────────────────────────
        val totalDepth = input.ribHeight + input.toppingThickness
        val punchVu = WaffleChecks.punchingDemand(wu, input.lx, input.ly, input.columnWidth, totalDepth, COVER)
        val punchingShearCheck = checkPunchingShear(
            fcu = input.fcu, fy = input.fy,
            solidHeadSize = input.solidHeadSize,
            columnSize = input.columnWidth,
            axialLoad = punchVu,
            shear = punchVu,
            totalDepth = totalDepth
        )

        // ── 7. Deflection Check ──────────────────────────────────────
        val deflectionCheck = checkDeflection(
            span = input.lx,
            totalDepth = input.ribHeight + input.toppingThickness,
            ribWidth = input.ribWidth,
            ribHeight = input.ribHeight,
            fcu = input.fcu,
            fy = input.fy,
            providedAs = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        // ── 8. Quantities ────────────────────────────────────────────
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
        if (ribDesign.status == DesignCheckStatus.NOT_ASSESSED) {
            notAssessed.add("Rib flexure and shear: ECP 203 clauses are not in the supplied reference text; values are indicative only")
        }
        if (punchingShearCheck.status == DesignCheckStatus.NOT_ASSESSED) {
            notAssessed.add("Punching shear: ECP 203 clauses are not in the supplied reference text")
        }
        val punchUtil = if (punchingShearCheck.vc > 0) punchingShearCheck.vu / punchingShearCheck.vc else 0.0
        val utilizationRatio = maxOf(ribDesign.utilizationRatio, punchUtil)

        warnings.addAll(ribDesign.warnings)
        if (punchingShearCheck.note.isNotEmpty()) codeNotes.add("Punching: " + punchingShearCheck.note)
        codeNotes.addAll(ribDesign.notes)
        if (status == DesignCheckStatus.FAIL) warnings.add("FAIL: one or more computed ECP 203 checks failed")
        if (utilizationRatio > 1.0) warnings.add("Utilization ratio = ${String.format("%.2f", utilizationRatio)} > 1.0")
        notAssessed.forEach { warnings.add("NOT ASSESSED: " + it) }

        if (ribDesign.status != DesignCheckStatus.NOT_ASSESSED) {
            safetyChecks.add(SafetyCheckItem("Rib flexure and shear", ribDesign.utilizationRatio, 1.0, "ECP 203 (" + ribDesign.status.label + ")", passed = ribDesign.isSafe))
        }
        if (punchingShearCheck.status != DesignCheckStatus.NOT_ASSESSED) {
            safetyChecks.add(SafetyCheckItem("Punching shear (concentric)", punchUtil, 1.0, "ECP 203 (" + punchingShearCheck.status.label + ")", passed = punchingShearCheck.isSafe))
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
    // RIB DESIGN — ECP 203
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
        val fc = fcu / GAMMA_C
        return WaffleChecks.ribDesign(
            fc = fc, fy = fy, bw = ribWidth, h = ribHeight, ribSpacing = ribSpacing, spanMm = clearSpan,
            wuPanel = totalLoad, cover = COVER, phiFlex = PHI_FLEXURE, phiShear = PHI_SHEAR,
            vcCoef = 0.24, vsMaxCoef = null, beta1 = null,
            minAs = { _, b, dd -> max(0.15 / 100.0 * b * dd, 0.0) },
            passStatus = DesignCheckStatus.NOT_ASSESSED, codeRef = "ECP 203"
        )
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // SOLID HEAD DESIGN
    // ═══════════════════════════════════════════════════════════════════════════════════

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
    // ═════════════════════════════════════════════════════════════════════════════════════

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
        val fc = fcu / GAMMA_C
        return WaffleChecks.punching(
            columnMm = columnSize, totalDepthMm = totalDepth, cover = COVER, vuKN = shear, phiShear = PHI_SHEAR,
            nominalVc = { bo, d, beta -> minOf(0.31 * sqrt(fc) * bo * d / 1000.0, 0.15 * (1.0 + 2.0 / beta) * sqrt(fc) * bo * d / 1000.0, 0.08 * (2.0 + 40.0 * d / bo) * sqrt(fc) * bo * d / 1000.0) },
            passStatus = DesignCheckStatus.NOT_ASSESSED
        )
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // DEFLECTION CHECK
    // ════════════════════════════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ════════════════════════════════════════════════════════════════════════════════════

    private fun getFactoredLoad(deadLoad: Double, liveLoad: Double): Double {
        return 1.4 * deadLoad + 1.6 * liveLoad
    }
}