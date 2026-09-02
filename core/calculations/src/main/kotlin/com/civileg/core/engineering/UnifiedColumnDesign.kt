package com.civileg.core.engineering

import com.civileg.core.math.SafeMath
import com.civileg.core.sanity.EngineeringSanityEngine
import com.civileg.core.sanity.SanityReport
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Unified column design (STEP 5 → Column DoD §82, spec §20/§81).
 *
 * ONE skeleton for tied/spiral reinforced-concrete columns — axial capacity,
 * combined axial+bending reinforcement (simplified interaction consistent
 * with the legacy benchmarks), ties, and shear — with the family rules
 * injected via [ConcreteCodeParams].
 *
 * Golden-gated against the app-level [com.civileg.app.domain.calculations.base.ColumnDesign]
 * implementations (ECPColumn / ACIColumn) for the exact axial-capacity and
 * simplified reinforcement paths.
 */
class UnifiedColumnDesign(
    private val params: ConcreteCodeParams,
    private val concrete: ConcreteMaterial,
    private val steel: SteelMaterial
) {

    /**
     * Exact nominal axial capacity (kN) for a given provided steel area.
     *
     * ECP 203 §4-2-2-2:  Pu = φ·α·[0.67fcu/γc·(Ag−Ast) + fy/γs·Ast]
     * ACI 318-19 §22.4.2: Pu = φ·cap·[0.85f'c·(Ag−Ast) + fy·Ast]
     */
    fun axialCapacityKn(sectionB: Double, sectionH: Double, astMm2: Double, isSpiral: Boolean): Double {
        val ag = sectionB * sectionH
        val ast = astMm2.coerceAtMost(ag * params.maxColumnSteelRatio())
        val phi = if (isSpiral) params.axialPhiSpiral else params.axialPhiTied
        return when (params.family) {
            CodeFamily.ECP_203 -> {
                val g = params.gammas!!
                val concreteStress = 0.67 * concrete.fcuMpa / g.gammaC
                val steelStress = steel.yieldMpa / g.gammaS
                val pn = concreteStress * (ag - ast) + steelStress * ast
                phi * (params.smallEccentricityFactor ?: 1.0) * pn / 1000.0
            }
            CodeFamily.ACI_318, CodeFamily.SBC_304 -> {
                val fc = concrete.cylinderStrengthMpa
                val p0 = 0.85 * fc * (ag - ast) + steel.yieldMpa * ast
                phi * params.maxAxialFactor(isSpiral) * p0 / 1000.0
            }
        }
    }

    /**
     * Full column design.
     *
     * @param b section width (mm) · h section depth (mm)
     * @param puKnm factored axial load (kN) (already load-factored)
     * @param momentXKnm factored moment about x-axis (kN·m)
     * @param momentYKnm factored moment about y-axis (kN·m)
     * @param isSpiral true for spiral, false for tied
     */
    fun design(
        b: Double, h: Double, puKnm: Double, momentXKnm: Double = 0.0, momentYKnm: Double = 0.0,
        isSpiral: Boolean = false
    ): Outcome {
        SafeMath.requirePositive(b, "b"); SafeMath.requirePositive(h, "h")
        SafeMath.requirePositive(puKnm, "Pu")
        val trace = CalculationTrace()
        val ref = params.reference
        val ag = b * h
        val puN = puKnm * 1000.0
        val muNmm = sqrt(momentXKnm * momentXKnm + momentYKnm * momentYKnm) * 1e6

        // ── 1. Axial capacity check (using a trial Ast from the simplified method) ──
        val gammas = params.gammas
        val concreteStress = when (params.family) {
            CodeFamily.ECP_203 -> 0.67 * concrete.fcuMpa / gammas!!.gammaC
            CodeFamily.ACI_318, CodeFamily.SBC_304 -> 0.85 * concrete.cylinderStrengthMpa
        }
        val steelStress = when (params.family) {
            CodeFamily.ECP_203 -> steel.yieldMpa / gammas!!.gammaS
            CodeFamily.ACI_318, CodeFamily.SBC_304 -> steel.yieldMpa
        }
        val alpha = params.smallEccentricityFactor ?: 1.0
        val phi = if (isSpiral) params.axialPhiSpiral else params.axialPhiTied

        // Simplified required steel (consistent with legacy benchmark):
        //    Pu = φ·α·[concreteStress·(Ag−Ast) + steelStress·Ast]   (ECP)
        //    Pu = φ·cap·[0.85f'c·(Ag−Ast) + fy·Ast]                   (ACI)
        // Solve for Ast.
        val capFactor = params.maxAxialFactor(isSpiral)
        val axialDenominator = steelStress - concreteStress
        var asRequired = if (axialDenominator != 0.0)
            (puN / (phi * alpha * capFactor) - concreteStress * ag) / axialDenominator
        else 0.0

        // ── 2. Moment (eccentricity) amplification — simplified interaction ──
        val eccentricity = if (puN > 0) muNmm / puN else 0.0
        val hMax = max(b, h)
        val momentFactor = if (eccentricity > 0.05 * hMax) 1.0 + 2.0 * eccentricity / hMax else 1.0
        if (momentFactor > 1.0) {
            asRequired *= momentFactor
            trace.add(
                title = "Moment (eccentricity) amplification",
                formula = "As × (1 + 2·e/h), e > 0.05h",
                substitution = "e = ${f(eccentricity)} mm ; h = ${f(hMax)} mm → ×${f(momentFactor)}",
                result = f(asRequired),
                limit = "simplified interaction",
                status = CheckStatus.PASS,
                codeReference = ref
            )
        }

        // ── 3. Min / max steel gates ──
        val minAs = params.minColumnSteelRatio() * ag
        val maxAs = params.maxColumnSteelRatio() * ag
        var governingNote: String? = null
        var gateStatus = CheckStatus.PASS
        when {
            asRequired < minAs -> { asRequired = minAs; governingNote = "minimum steel governs"; gateStatus = CheckStatus.WARNING }
            asRequired > maxAs -> { governingNote = "exceeds max ratio — increase section"; gateStatus = CheckStatus.FAIL }
        }
        trace.add(
            title = "Reinforcement limits",
            formula = "ρ ∈ [ρmin=${params.minColumnSteelRatio()}, ρmax=${params.maxColumnSteelRatio()}]",
            substitution = "As,req = ${f(asRequired)} mm² ; ρ = ${f(asRequired / ag)}",
            result = "${f(asRequired)} mm²${governingNote?.let { " ($it)" } ?: ""}",
            limit = "≤ ${f(maxAs)} mm²",
            status = gateStatus,
            codeReference = ref
        )

        // ── 4. Bar selection (family bar menu; columns allow up to 24 bars) ──
        val (n, dia) = RebarTable.select(asRequired, maxBars = 24, diameters = params.barMenuMm)
        val asProvided = n * RebarTable.area(dia)
        trace.add(
            title = "Longitudinal bar selection",
            formula = "n·πØ²/4 ≥ As,req",
            substitution = "${n}Ø${dia.toInt()} → ${f(asProvided)} mm²",
            result = f(asProvided),
            limit = "≥ ${f(asRequired)} mm²",
            status = if (asProvided >= asRequired - 1e-6) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = SafeMath.div(asRequired, asProvided)
        )

        // ── 5. Axial capacity verification with provided steel ──
        val capacityKn = axialCapacityKn(b, h, asProvided, isSpiral)
        val util = SafeMath.div(puKnm, capacityKn)
        trace.add(
            title = "Axial capacity verification",
            formula = "Pu ≤ φ·α·Pn (ECP) / φ·cap·Pn (ACI)",
            substitution = "Pu = ${f(puKnm)} kN ; Pn = ${f(capacityKn)} kN",
            result = f(puKnm),
            limit = "≤ ${f(capacityKn)} kN",
            status = if (util <= 1.0) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = util,
            codeReference = ref
        )

        // ── 6. Ties (transverse reinforcement) ──
        val tieDia = max(10.0, dia / 4.0).coerceAtLeast(8.0)
        val tieSpacing = params.tiesMaxSpacingMm(dia, tieDia, min(b, h)).coerceIn(40.0, 300.0)
        trace.add(
            title = "Ties spacing",
            formula = "s ≤ min(16db, 48dtie, least dim, 300)",
            substitution = "Ø${dia.toInt()} long.; Ø${tieDia.toInt()} tie",
            result = "Ø${tieDia.toInt()} @ ${f(tieSpacing)} mm",
            limit = "≤ ${f(params.tiesMaxSpacingMm(dia, tieDia, min(b, h)))} mm",
            status = CheckStatus.PASS,
            codeReference = ref
        )

        // ── 7. Shear (reuses family Vc; column spacing rule) ──
        val dEff = h - 40.0
        val vcKn = params.concreteShearCapacityKn(b, dEff, concrete)
        // Column shear is checked separately via designShear(); here we only report Vc.
        trace.add(
            title = "Concrete shear capacity Vc",
            formula = if (params.family == CodeFamily.ECP_203) "Vc = 0.24√(fcu/γc)·b·d" else "Vc = 0.17√f'c·b·d",
            substitution = "Vc = ${f(vcKn)} kN (b=$b, d=${f(dEff)})",
            result = f(vcKn),
            limit = "use designShear() for Vu check",
            status = CheckStatus.PASS,
            codeReference = ref
        )

        val outcome = Outcome(
            asRequiredMm2 = asRequired,
            asProvidedMm2 = asProvided,
            bars = "${n}Ø${dia.toInt()}",
            tieDiameterMm = tieDia,
            tieSpacingMm = tieSpacing,
            axialCapacityKn = capacityKn,
            utilization = util,
            governingNote = governingNote,
            isSafe = util <= 1.0 && gateStatus != CheckStatus.FAIL,
            overallStatus = trace.overall,
            trace = trace
        )
        val sanityReport = EngineeringSanityEngine.check(outcome)
        val overallStatus = when {
            sanityReport.hasError -> CheckStatus.FAIL
            sanityReport.hasWarning && trace.overall == CheckStatus.PASS -> CheckStatus.WARNING
            else -> trace.overall
        }
        return outcome.copy(sanity = sanityReport, overallStatus = overallStatus)
    }

    /**
     * Column shear check (ECP §4-2-5 / ACI §22.5).
     * @param vuKn factored shear (kN) · b width (mm) · h depth (mm) · cover (mm)
     */
    fun designShear(vuKn: Double, b: Double, h: Double, cover: Double = 40.0): ShearOutcome {
        SafeMath.requirePositive(vuKn, "Vu"); SafeMath.requirePositive(b, "b"); SafeMath.requirePositive(h, "h")
        val trace = CalculationTrace()
        val ref = params.reference
        val d = h - cover
        val vcKn = params.concreteShearCapacityKn(b, d, concrete)
        val needsStirrups = vuKn > vcKn

        val asvPerS = when (params.family) {
            CodeFamily.ECP_203 -> if (needsStirrups) (vuKn - vcKn) * 1000.0 / ((steel.yieldMpa / params.gammas!!.gammaS) * d) else 0.0
            CodeFamily.ACI_318, CodeFamily.SBC_304 -> {
                val phi = 0.75
                if (vuKn > phi * vcKn) (vuKn - phi * vcKn) * 1000.0 / (phi * steel.yieldMpa * d) else 0.0
            }
        }
        val minAsvPerS = when (params.family) {
            CodeFamily.ECP_203 -> if (needsStirrups) 0.0025 * b else 0.0
            CodeFamily.ACI_318, CodeFamily.SBC_304 -> {
                val fc = concrete.cylinderStrengthMpa
                max(0.062 * sqrt(fc) * b / steel.yieldMpa, 0.35 * b / steel.yieldMpa)
            }
        }
        val designAsvPerS = max(asvPerS, if (needsStirrups) minAsvPerS else 0.0)
        val maxSpacing = params.columnShearMaxSpacingMm(b, d).coerceAtLeast(40.0)
        val (tieDia, spacing) = selectTie(designAsvPerS, maxSpacing)

        val totalCap = vcKn + if (needsStirrups) steel.yieldMpa / (params.gammas?.gammaS ?: 1.0) * designAsvPerS * d / 1000.0 else 0.0
        val util = SafeMath.div(vuKn, vcKn + if (needsStirrups) designAsvPerS * steel.yieldMpa * d / 1000.0 else 0.0)

        trace.add(
            title = "Column shear",
            formula = if (params.family == CodeFamily.ECP_203) "Vc = 0.24√(fcu/γc)·b·d ; Asv/s = (Vu−Vc)/(fsd·d)"
            else "φVc = 0.75·0.17√f'c·b·d ; Asv/s = (Vu−φVc)/(φ·fy·d)",
            substitution = "Vc = ${f(vcKn)} kN ; Asv/s = ${f(designAsvPerS)} mm²/mm",
            result = "${f(vuKn)} kN applied",
            limit = "≤ ${f(vcKn)} kN (concrete)",
            status = if (needsStirrups) CheckStatus.WARNING else CheckStatus.PASS,
            utilization = SafeMath.div(vuKn, vcKn),
            codeReference = ref
        )
        trace.add(
            title = "Shear ties",
            formula = "s = Av·1000/Asv,s ≤ family cap",
            substitution = "Ø${tieDia.toInt()} → s = ${f(spacing)} mm",
            result = "Ø${tieDia.toInt()} @ ${f(spacing)} mm",
            limit = "≤ ${f(maxSpacing)} mm",
            status = CheckStatus.PASS,
            codeReference = ref
        )

        val outcome = ShearOutcome(
            vcKn = vcKn, asvPerSMm2 = designAsvPerS, tieDiameterMm = tieDia,
            spacingMm = spacing, needsStirrups = needsStirrups, isSafe = vuKn <= totalCap,
            utilization = util, overallStatus = trace.overall, trace = trace
        )
        val sanityReport = EngineeringSanityEngine.check(outcome)
        val overallStatus = when {
            sanityReport.hasError -> CheckStatus.FAIL
            sanityReport.hasWarning && outcome.trace.overall == CheckStatus.PASS -> CheckStatus.WARNING
            else -> outcome.trace.overall
        }
        return outcome.copy(sanity = sanityReport, overallStatus = overallStatus)
    }

    private fun selectTie(designAsvPerS: Double, maxSpacing: Double): Pair<Double, Double> {
        val available = listOf(8.0, 10.0, 12.0, 16.0)
        if (designAsvPerS <= 0.0) return 8.0 to maxSpacing
        for (dia in available) {
            val asv = 2.0 * PI * dia * dia / 4.0
            val s = asv / designAsvPerS
            if (s <= maxSpacing && s >= 40.0) return dia to min(s, maxSpacing)
        }
        return 16.0 to 40.0
    }

    data class Outcome(
        val asRequiredMm2: Double,
        val asProvidedMm2: Double,
        val bars: String,
        val tieDiameterMm: Double,
        val tieSpacingMm: Double,
        val axialCapacityKn: Double,
        val utilization: Double,
        val governingNote: String?,
        val isSafe: Boolean,
        val overallStatus: CheckStatus,
        val trace: CalculationTrace,
        val sanity: SanityReport = SanityReport("ColumnOutcome", emptyList())
    )

    data class ShearOutcome(
        val vcKn: Double,
        val asvPerSMm2: Double,
        val tieDiameterMm: Double,
        val spacingMm: Double,
        val needsStirrups: Boolean,
        val isSafe: Boolean,
        val utilization: Double,
        val overallStatus: CheckStatus,
        val trace: CalculationTrace,
        val sanity: SanityReport = SanityReport("ColumnShearOutcome", emptyList())
    )

    private fun f(v: Double) = String.format("%.3f", v).trimEnd('0').trimEnd('.')
}
