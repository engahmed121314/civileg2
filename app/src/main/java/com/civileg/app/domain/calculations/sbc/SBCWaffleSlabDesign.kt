package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.FlatSlabDesign
import com.civileg.app.domain.calculations.base.WaffleSlabDesign
import com.civileg.app.domain.calculations.base.CheckStatus
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
 * SBC 304 Waffle Slab Design Implementation
 *
 * References:
 *  - SBC 304 §6-4: Waffle slabs (ribbed slabs with solid heads)
 *  - SBC 304 §4-2: Flexural design
 *  - SBC 304 §4-3: Shear design
 *  - SBC 304 §6-3: Deflection control
 *
 * Key SBC 304 differences from ACI:
 *  - Uses fcu directly (cube strength), not f'c = 0.8×fcu
 *  - Material safety factors: γc = 1.5, γs = 1.15
 *  - Strength reduction: φ_flexure = 0.9, φ_shear = 0.75
 *  - Load factors: 1.4DL + 1.6LL (SBC 304 §3-2)
 */
class SBCWaffleSlabDesign : WaffleSlabDesign {

    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75
        private const val EPSILON_CU = 0.003
        private const val ES = 200000.0
        private const val CONCRETE_UNIT_WEIGHT = 25.0
        private const val STEEL_UNIT_WEIGHT = 7850.0
        private const val COVER = 20.0
        private const val PI = 3.141592653589793
        private const val CONCRETE_COST_PER_M3 = 500.0
        private const val STEEL_COST_PER_KG = 1.5
    }

    override fun design(input: WaffleSlabInput): WaffleSlabResult {
        // ── InputGuard (ADR-010) — SBC 304 ──
        InputGuard.notNull("input", input)
        InputGuard.positive("input.fcu", input.fcu)
        InputGuard.positive("input.fy", input.fy)
        InputGuard.positive("input.lx", input.lx)
        InputGuard.positive("input.ly", input.ly)
        InputGuard.positive("input.ribWidth", input.ribWidth)
        InputGuard.positive("input.ribHeight", input.ribHeight)
        InputGuard.positive("input.ribSpacing", input.ribSpacing)
        InputGuard.positive("input.columnWidth", input.columnWidth)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val safetyChecks = mutableListOf<SafetyCheckItem>()

        codeNotes.add(CodeReference.SBC.SLAB_TWO_WAY)
        codeNotes.add("SBC 304 §6-4: Waffle Slab Design")
        codeNotes.add("γc = $GAMMA_C, γs = $GAMMA_S, φ_flex = $PHI_FLEXURE, φ_shear = $PHI_SHEAR")
        codeNotes.add("Load: Wu = 1.4×DL + 1.6×LL (SBC 304 §3-2)")

        val fc = input.fcu

        // ── 1. Load combinations per SBC 304 §3-2 ───────────────
        val wu = 1.4 * input.deadLoad + 1.6 * input.liveLoad
        codeNotes.add("Wu = 1.4×DL + 1.6×LL = ${String.format("%.2f", wu)} kN/m²")

        // ── 2. Rib Design ───────────────────────────────────────────
        val ribDesign = designRib(
            fcu = input.fcu, fy = input.fy,
            ribWidth = input.ribWidth, ribHeight = input.ribHeight,
            ribSpacing = input.ribSpacing,
            clearSpan = input.lx,
            totalLoad = wu,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        // ── 3. Column strip moment for solid head ─────────────────────
        val axialLoad = wu * input.lx * input.ly / 1e6  // kN (total factored load)
        val columnStripMoment = wu * input.lx * input.ly / 1e6 * input.lx / 1000.0 / 32.0  // kN.m approx
        val columnShear = axialLoad  // kN (punching shear demand ≈ Vu)

        // ── 4. Solid Head Design ────────────────────────────────────
        val solidHeadDesign = designSolidHead(
            fcu = input.fcu, fy = input.fy,
            solidHeadSize = input.solidHeadSize,
            ribWidth = input.ribWidth,
            columnSize = input.columnWidth,
            axialLoad = axialLoad,
            moment = columnStripMoment
        )

        // ── 5. Punching Shear Check ──────────────────────────────────
        val punchingShearCheck = checkPunchingShear(
            fcu = input.fcu, fy = input.fy,
            solidHeadSize = input.solidHeadSize,
            columnSize = input.columnWidth,
            axialLoad = axialLoad,
            shear = columnShear
        )

        // ── 6. Deflection Check ──────────────────────────────────────
        val providedAs = ribDesign.flexureReinforcement.providedArea
        val deflectionCheck = checkDeflection(
            span = input.lx,
            totalDepth = input.ribHeight + input.toppingThickness,
            ribWidth = input.ribWidth,
            ribHeight = input.ribHeight,
            fcu = input.fcu, fy = input.fy,
            providedAs = providedAs,
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
        val steelWeight = ((numRibsX * providedSteel * (input.ly / 1000.0) / 1e6) + (numRibsY * providedSteel * (input.lx / 1000.0) / 1e6)) * STEEL_UNIT_WEIGHT

        // ── 7. Aggregate safety from all sub-checks ───────────────────
        // SBC solid head, punching and deflection keep their existing functions, but none of them has a verified
        // SBC 304 clause in the supplied text (solid head uses an unsourced /32 column-strip factor and bar clamps, deflection
        // uses fixed service loads and an unsourced L/250 limit), so they cannot support a pass.
        val punchStatus = if (!punchingShearCheck.isSafe) CheckStatus.FAIL else CheckStatus.NOT_ASSESSED
        val punchChecked = punchingShearCheck.copy(status = punchStatus)
        val headChecked = solidHeadDesign.copy(status = if (!solidHeadDesign.isSafe) CheckStatus.FAIL else CheckStatus.NOT_ASSESSED)
        val headFlexNote = "Solid head: SBC 304 method not verified (unsourced column-strip factor, bar-count clamps); result indicative only"
        val status = if (ribDesign.status == CheckStatus.FAIL || punchStatus == CheckStatus.FAIL || !solidHeadDesign.isSafe) CheckStatus.FAIL else CheckStatus.PRELIMINARY
        val notAssessed = mutableListOf(
            "Deflection: SBC 304 method not verified (fixed service loads and limit); result not used for the verdict",
            headFlexNote,
            "Rib flexure and shear: SBC 304 clauses are not in the supplied reference text; values are indicative only",
            "Punching shear: SBC 304 clauses are not in the supplied reference text; solid-head edge, moment transfer, edge and corner columns not checked"
        )
        val punchUtil = punchingShearCheck.utilizationRatio
        val utilizationRatio = maxOf(ribDesign.utilizationRatio, punchUtil)

        warnings.addAll(ribDesign.warnings)
        codeNotes.addAll(ribDesign.notes)
        if (status == CheckStatus.FAIL) warnings.add("FAIL: one or more computed SBC 304 checks failed")
        if (utilizationRatio > 1.0) warnings.add("Utilization ratio = ${String.format("%.2f", utilizationRatio)} > 1.0")
        notAssessed.forEach { warnings.add("NOT ASSESSED: " + it) }

        safetyChecks.add(SafetyCheckItem("Rib flexure and shear", ribDesign.utilizationRatio, 1.0, "SBC 304 (" + ribDesign.status.label + ")", passed = ribDesign.isSafe || ribDesign.status == CheckStatus.NOT_ASSESSED))
        safetyChecks.add(SafetyCheckItem("Solid head", 0.0, 1.0, "SBC 304 (indicative)", passed = solidHeadDesign.isSafe))
        safetyChecks.add(SafetyCheckItem("Punching shear", punchUtil, 1.0, "SBC 304 (" + punchStatus.label + ")", passed = punchingShearCheck.isSafe))

        return WaffleSlabResult(
            isSafe = false, // never SAFE: not every check has a verified method
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes,
            safetyChecks = safetyChecks,
            ribDesign = ribDesign,
            solidHeadDesign = headChecked,
            punchingShearCheck = punchChecked,
            deflectionCheck = deflectionCheck,
            concreteVolume = concreteVolume,
            steelWeight = steelWeight,
            cost = concreteVolume * CONCRETE_COST_PER_M3 + steelWeight * STEEL_COST_PER_KG,
            status = status,
            notAssessed = notAssessed,
            deflectionAssessed = false
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // RIB DESIGN — SBC 304
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
            passStatus = CheckStatus.NOT_ASSESSED, codeRef = "SBC 304"
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
        // ── InputGuard (ADR-010) — SBC 304 ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("solidHeadSize", solidHeadSize)
        InputGuard.positive("columnSize", columnSize)
        InputGuard.nonNegative("axialLoad", axialLoad)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val fc = fcu / GAMMA_C  // cylinder strength
        val solidDepth = solidHeadSize.coerceAtMost(400.0)  // depth of solid head (mm)
        val d = solidDepth - COVER - 10.0  // effective depth
        val b = solidHeadSize  // width of solid head section

        // ── Flexural design of solid head — SBC 304 §4-2 ──
        val Mu = max(moment, axialLoad * columnSize / 1000.0 / 6.0) * 1e6  // N.mm (use moment or eccentricity)
        val Rn = if (b > 0 && d > 0) Mu / (PHI_FLEXURE * b * d * d) else 0.0
        val m = fy / (0.85 * fc.coerceAtLeast(1.0))
        val discriminant = 1.0 - 2.0 * m * Rn / fy.coerceAtLeast(1.0)
        val rho = if (discriminant > 0) (1.0 - sqrt(discriminant)) / m else 0.0
        val As = rho * b * d

        // Min steel per SBC 304 §9.6.1
        val minAs = max(0.15 / 100.0 * b * d, 1.3 * As / 100.0)
        val AsProvided = maxOf(As, minAs)

        // Bar selection — Saudi market sizes
        val barDia = 16.0
        val barArea = PI * barDia * barDia / 4.0
        val nBars = ceil(AsProvided / barArea).toInt().coerceIn(4, 12)
        val spacing = ((b - 2 * COVER) / (nBars - 1)).toInt().coerceIn(100, 250)

        val flexure = RebarResult(
            bars = nBars,
            diameter = barDia.toInt(),
            spacing = spacing,
            providedArea = nBars * barArea,
            requiredArea = As,
            ratio = nBars * barArea / (b * d)
        )

        // ── Punching shear at solid head — SBC 304 §4-3 ──
        val bo = 2.0 * (solidHeadSize + columnSize)  // critical perimeter (mm)
        val beta = max(solidHeadSize, columnSize) / min(solidHeadSize, columnSize).coerceAtLeast(1.0)
        val vc1 = 0.31 * sqrt(fc.coerceAtLeast(1.0)) * bo * d / 1000.0  // kN
        val vc2 = 0.15 * (1.0 + 2.0 / beta) * sqrt(fc.coerceAtLeast(1.0)) * bo * d / 1000.0  // kN
        val vc3 = 0.08 * (2.0 + 40.0 * d / bo.coerceAtLeast(1.0)) * sqrt(fc.coerceAtLeast(1.0)) * bo * d / 1000.0  // kN
        val vc = minOf(vc1, vc2, vc3) * PHI_SHEAR
        val vu = axialLoad  // punching shear demand ≈ Vu from column reaction
        val punchingIsSafe = vu <= vc
        val punchingUtil = if (vc > 0) vu / vc else 2.0

        val punching = PunchingShearCheck(
            vu = vu, vc = vc,
            isSafe = punchingIsSafe,
            utilizationRatio = punchingUtil
        )

        val headIsSafe = punchingIsSafe && (nBars * barArea >= As)

        return WaffleSlabDesign.SolidHeadDesignResult(
            flexureReinforcement = flexure,
            punchingShear = punching,
            isSafe = headIsSafe
        )
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // PUNCHING SHEAR CHECK
    // ════════════════════════════════════════════════════════════════════════════════════

    override fun checkPunchingShear(
        fcu: Double,
        fy: Double,
        solidHeadSize: Double,
        columnSize: Double,
        axialLoad: Double,
        shear: Double,
        totalDepth: Double
    ): WaffleSlabDesign.PunchingShearCheck {
        // ── InputGuard (ADR-010) — SBC 304 ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("solidHeadSize", solidHeadSize)
        InputGuard.positive("columnSize", columnSize)
        InputGuard.nonNegative("axialLoad", axialLoad)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val fc = fcu / GAMMA_C
        // Critical section perimeter at d/2 from column face — SBC 304 §4-3
        val c1 = columnSize
        val c2 = columnSize
        val d = solidHeadSize.coerceAtMost(400.0) - COVER - 10.0  // effective depth of solid head
        val bo = 2.0 * (c1 + c2) + 4.0 * d  // perimeter of critical section at d/2

        val beta = max(c1, c2) / min(c1, c2).coerceAtLeast(1.0)
        val vc1 = 0.31 * sqrt(fc.coerceAtLeast(1.0)) * bo * d / 1000.0
        val vc2 = 0.15 * (1.0 + 2.0 / beta) * sqrt(fc.coerceAtLeast(1.0)) * bo * d / 1000.0
        val vc3 = 0.08 * (2.0 + 40.0 * d / bo.coerceAtLeast(1.0)) * sqrt(fc.coerceAtLeast(1.0)) * bo * d / 1000.0
        val vc = minOf(vc1, vc2, vc3) * PHI_SHEAR

        // Punching shear demand: Vu = axialLoad from column reaction
        val vu = max(axialLoad, shear)  // use the larger of direct axial or shear demand
        val isSafe = vu <= vc

        return PunchingShearCheck(
            vu = vu, vc = vc, isSafe = isSafe,
            utilizationRatio = if (vc > 0) vu / vc else 2.0
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
        // ── InputGuard (ADR-010) — SBC 304 ──
        InputGuard.positive("span", span)
        InputGuard.positive("totalDepth", totalDepth)
        InputGuard.positive("ribWidth", ribWidth)
        InputGuard.positive("ribHeight", ribHeight)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.nonNegative("providedAs", providedAs)
        InputGuard.notNull("loadCombination", loadCombination)

        val fc = fcu / GAMMA_C
        val Ec = 4400.0 * sqrt(fc.coerceAtLeast(1.0))

        // Gross moment of inertia of T-section (rib + topping flange)
        val bf = min(ribWidth * 4.0, span / 4.0)  // effective flange width — SBC 304 §6-2 (≈4×ribWidth typical spacing)
        val h = totalDepth  // total depth (rib + topping)
        val hf = h - ribHeight  // topping thickness
        val bw = ribWidth  // rib width
        // T-section Ig by composite area
        val flangeArea = bf * hf
        val ribArea = bw * (h - hf)
        val totalArea = flangeArea + ribArea
        val yBar = if (totalArea > 0) (flangeArea * (h - hf / 2.0) + ribArea * (h - hf) / 2.0) / totalArea else h / 2.0
        val Iflange = bf * hf * hf * hf / 12.0 + flangeArea * (h - hf / 2.0 - yBar).pow(2)
        val Irib = bw * (h - hf).pow(3) / 12.0 + ribArea * ((h - hf) / 2.0 - yBar).pow(2)
        val Ig = Iflange + Irib

        // Cracked moment of inertia
        val n = ES / Ec.coerceAtLeast(1.0)
        val d = h - COVER - 10.0  // effective depth
        // Cracked neutral axis depth (compression only in flange assumed for T-section)
        val bEff = if (bf > bw) bf else bw
        val aGuess = if (providedAs > 0 && n > 0) {
            // Solve quadratic: bEff*x²/2 = n*As*(d-x)
            val a = bEff / 2.0
            val bCoeff = n * providedAs
            val cCoeff = -n * providedAs * d
            val disc = bCoeff * bCoeff - 4.0 * a * cCoeff
            if (disc > 0) (-bCoeff + sqrt(disc)) / (2.0 * a) else 0.4 * d
        } else 0.4 * d
        val Icr = if (aGuess > 0 && d > aGuess) {
            bEff * aGuess * aGuess * aGuess / 3.0 + n * providedAs * (d - aGuess).pow(2)
        } else 0.35 * Ig  // fallback if calculation is invalid

        // Effective moment of inertia — Branson's equation (SBC 304 §6-3)
        val Mcr = if (Ig > 0 && yBar > 0 && yBar < h) {
            (0.62 * sqrt(fc.coerceAtLeast(1.0)) * Ig / yBar) / 1e6  // kN.m
        } else 0.0
        // ── Service load estimation from geometry (SBC 304 §6-3) ──
        // Self-weight: concrete × [topping + rib fraction]
        //   topping = (totalDepth - ribHeight) in meters
        //   rib contribution ≈ 35% of ribHeight (typical waffle rib/solid ratio)
        val toppingThickness_m = (totalDepth - ribHeight) / 1000.0
        val ribFraction = 0.35  // typical rib-to-solid area ratio for waffle slabs
        val selfWeight = CONCRETE_UNIT_WEIGHT * (toppingThickness_m + ribFraction * ribHeight / 1000.0)  // kN/m²
        val superimposedDL = 3.0   // kN/m² — typical SBC: finishes + partitions
        val liveLoad = 3.0         // kN/m² — typical SBC office/residential
        val wServiceTotal = selfWeight + superimposedDL + liveLoad  // kN/m² total service load

        val Ma = if (span > 0) {
            // Service moment: w×L²/16 (approximate for interior span)
            wServiceTotal * (span / 1000.0).pow(2) / 16.0  // kN.m
        } else 1.0
        val ratioMaMcr = if (Mcr > 0) (Ma / Mcr).pow(3) else 1.0
        val Ie = if (Ma >= Mcr && Icr > 0) {
            (ratioMaMcr * Ig + (1.0 - ratioMaMcr) * Icr).coerceIn(minOf(Icr, Ig), maxOf(Icr, Ig))
        } else Ig

        // Immediate deflection — 5wL⁴/(384EI) — SBC 304 §6-3
        // w = total service load (kN/m²) × rib spacing (≈4×ribWidth typical)
        val L_mm = span
        val ribSpacingEstimate = min(4.0 * ribWidth, span / 4.0)  // mm — effective spacing
        val w_kN_per_mm = wServiceTotal * (ribSpacingEstimate / 1000.0) / 1000.0  // kN/mm (load per rib)
        val immediate = if (Ec > 0 && Ie > 0) {
            5.0 * w_kN_per_mm * L_mm.pow(4) / (384.0 * Ec * Ie) * 1000.0  // mm
        } else 0.0

        // Long-term deflection with creep — SBC 304 §6-3
        val creepFactor = 2.0  // conservative for 5-year+ sustained load
        val longTerm = immediate * creepFactor
        val allowable = span / 250.0  // SBC 304 §6-3: L/250 for total deflection
        val ratio = longTerm / allowable
        val isSafe = longTerm <= allowable

        return FlatSlabDesign.DeflectionResult(
            immediate = immediate,
            longTerm = longTerm,
            allowable = allowable,
            isSafe = isSafe,
            ratio = ratio
        )
    }
}