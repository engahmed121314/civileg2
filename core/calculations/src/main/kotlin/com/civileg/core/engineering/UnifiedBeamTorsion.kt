package com.civileg.core.engineering

import com.civileg.core.math.SafeMath
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Unified beam torsion design (spec §20, §22.7 / ECP 203 §4-3-4).
 *
 * Space-truss model for closed rectangular sections, non-prestressed, no axial load:
 *  - Section properties:   Acp = b·h,  Pcp = 2(b+h),  Aoh = (b−2c)(h−2c),
 *                         Ao = 0.85·Aoh,  Ph = 2((b−2c)+(h−2c))        (ACI §22.7.6.1.1)
 *  - Threshold (neglect): Tu < φ·Tth  (ACI) / Tu < Tth (ECP),  Tth = 0.083·√f'·(Acp²/Pcp)
 *  - Cracking (redist.):  Tcr = 0.33·√f'·(Acp²/Pcp)  — Tu may be reduced to φ·Tcr in
 *                         statically-indeterminate structures (ACI §22.7.3.2)
 *  - Required stirrups:   At/s = Tu / (φ·2·Ao·fyt·cotθ)   (ACI),  φ = 1 for ECP (γs in fyt)
 *  - Longitudinal steel: Al = At·(fyt/fyl)·(Ao/Ph)·cot²θ,  distributed, ≥ one bar/corner
 *  - Section adequacy:    ECP Tu ≤ Tmax (0.333 form);  ACI §22.7.7.1a combined stress check
 *
 * Every check reports INPUT → FORMULA → SUBSTITUTION → RESULT → LIMIT → UTIL → STATUS
 * (spec §15/§62). Invalid inputs throw (no silent PASS).
 */
class UnifiedBeamTorsion(
    private val params: ConcreteCodeParams
) {
    private val ref = params.reference

    data class Input(
        val bMm: Double,          // overall section width (mm)
        val hMm: Double,          // overall section depth (mm)
        val coverMm: Double,      // cover to stirrup centreline (mm)
        val concrete: ConcreteMaterial,
        val steel: SteelMaterial, // longitudinal + transverse yield (same grade assumed)
        val tuKnm: Double,        // factored torsional moment (kN·m)
        val vuKn: Double = 0.0,   // factored shear (kN) — for combined §22.7.7.1a check
        val dMm: Double = 0.0,    // effective depth (mm) — for shear term (defaults h−cover)
        val thetaDeg: Double = 45.0
    )

    data class Outcome(
        val acpMm2: Double,
        val pcpMm: Double,
        val aohMm2: Double,
        val aoMm2: Double,
        val phMm: Double,
        val tuKnm: Double,
        val tuThKnm: Double,
        val tuCrKnm: Double,
        val tuMaxKnm: Double,
        val torsionState: TorsionState,
        val sectionAdequate: Boolean,
        val atOverSReq: Double,   // mm²/mm required (equilibrium)
        val atOverSMin: Double,   // mm²/mm code minimum
        val atOverSProvided: Double,
        val stirrupDiaMm: Double,
        val stirrupSpacingMm: Double,
        val longitudinalAreaMm2: Double,   // total Al (mm²)
        val longitudinalBars: Int,
        val longitudinalDiaMm: Double,
        val combinedStressUtil: Double?,  // ACI §22.7.7.1a (null for ECP)
        val utilization: Double,
        val isSafe: Boolean,
        val trace: CalculationTrace
    )

    enum class TorsionState { NONE, MINIMUM_ONLY, FULL_DESIGN }

    fun design(input: Input): Outcome {
        val (b, h, c) = Triple(input.bMm, input.hMm, input.coverMm)
        SafeMath.requirePositive(b, "b"); SafeMath.requirePositive(h, "h"); SafeMath.requirePositive(c, "cover")
        SafeMath.requirePositive(input.tuKnm, "Tu")
        val d = if (input.dMm > 0) input.dMm else (h - c)
        SafeMath.requirePositive(d, "d")

        val trace = CalculationTrace()
        val root = params.torsionConcreteRootMpa(input.concrete)
        val phi = params.torsionPhi ?: 1.0   // ECP uses γs instead of φ

        // ── Section properties ──
        val acp = b * h
        val pcp = 2.0 * (b + h)
        val aoh = (b - 2 * c) * (h - 2 * c)
        val ao = 0.85 * aoh
        val ph = 2.0 * ((b - 2 * c) + (h - 2 * c))
        trace.add(
            title = "Torsion — section properties",
            formula = "Acp=b·h, Pcp=2(b+h), Aoh=(b−2c)(h−2c), Ao=0.85·Aoh, Ph=2((b−2c)+(h−2c))",
            substitution = "b=$b, h=$h, c=$c → Acp=${f(acp)}, Pcp=${f(pcp)}, Aoh=${f(aoh)}, Ao=${f(ao)}, Ph=${f(ph)}",
            result = "Ao=${f(ao)} mm², Ph=${f(ph)} mm",
            limit = "—", status = CheckStatus.PASS, utilization = null, codeReference = "$ref §22.7.6.1.1"
        )

        // ── Threshold / cracking / max ──
        val tuNmm = input.tuKnm * 1e6
        val tuTh = params.torsionThresholdTorqueNmm(b, h, input.concrete)
        val tuCr = params.torsionCrackingTorqueNmm(b, h, input.concrete)
        val tuMax = params.torsionMaxTorqueNmm(b, h, input.concrete)
        val neglect = phi * tuTh          // Tu < neglect → no torsion
        val redist = phi * tuCr            // Tu ≥ redist → full design + redistribution

        val state = when {
            tuNmm < neglect -> TorsionState.NONE
            tuNmm < redist -> TorsionState.MINIMUM_ONLY
            else -> TorsionState.FULL_DESIGN
        }
        trace.add(
            title = "Torsion — threshold / cracking",
            formula = "Tth=0.083√f'·(Acp²/Pcp), Tcr=0.33√f'·(Acp²/Pcp); neglect if Tu<${if (phi < 1) "φ·" else ""}Tth",
            substitution = "Tu=${f(input.tuKnm)} kN·m, Tth=${f(tuTh / 1e6)} kN·m, Tcr=${f(tuCr / 1e6)} kN·m" +
                (if (phi < 1) ", φ=$phi → neglect=${f(neglect / 1e6)} kN·m" else ""),
            result = "state=${state.name}",
            limit = "Tu ≥ ${f(neglect / 1e6)} kN·m → design",
            status = CheckStatus.PASS, utilization = null,
            codeReference = "$ref §22.7.4.1(a) / §22.7.5.1"
        )

        if (state == TorsionState.NONE) {
            trace.add(
                title = "Torsion — conclusion",
                formula = "Tu < ${if (phi < 1) "φ·" else ""}Tth → torsion neglected",
                substitution = "Tu=${f(input.tuKnm)} kN·m < ${f(neglect / 1e6)} kN·m",
                result = "no torsion reinforcement required",
                limit = "—", status = CheckStatus.PASS, utilization = 0.0, codeReference = "$ref §22.7.1.1"
            )
            return Outcome(
                acpMm2 = acp, pcpMm = pcp, aohMm2 = aoh, aoMm2 = ao, phMm = ph,
                tuKnm = input.tuKnm, tuThKnm = tuTh / 1e6, tuCrKnm = tuCr / 1e6,                 tuMaxKnm = tuMax / 1e6,
                torsionState = state, sectionAdequate = true, atOverSReq = 0.0, atOverSMin = 0.0, atOverSProvided = 0.0,
                stirrupDiaMm = 0.0, stirrupSpacingMm = 0.0, longitudinalAreaMm2 = 0.0,
                longitudinalBars = 0, longitudinalDiaMm = 0.0, combinedStressUtil = null,
                utilization = 0.0, isSafe = true, trace = trace
            )
        }

        // ── Section adequacy (upper limit) ──
        val (sectionAdequate, sectionUtil) = when {
            params.torsionPhi == null -> {  // ECP — Tmax bound
                val util = SafeMath.div(tuNmm, tuMax)
                val ok = tuNmm <= tuMax
                trace.add(
                    title = "Torsion — section adequacy (ECP max)",
                    formula = "Tu ≤ Tmax,  Tmax=0.333√f'·(Acp²/Pcp)",
                    substitution = "Tu=${f(input.tuKnm)} kN·m, Tmax=${f(tuMax / 1e6)} kN·m",
                    result = f(input.tuKnm), limit = "≤ ${f(tuMax / 1e6)} kN·m",
                    status = if (ok) CheckStatus.PASS else CheckStatus.FAIL,
                    utilization = util, codeReference = "$ref §4-3-4"
                )
                Pair(ok, util)
            }
            else -> {  // ACI — §22.7.7.1a combined stress (solid, nonprestressed)
                val vuN = input.vuKn * 1e3
                val shearStress = if (vuN > 0) SafeMath.div(vuN, b * d) else 0.0
                val torsionStress = SafeMath.div(tuNmm * pcp, 1.7 * acp * acp)
                val demand = shearStress + torsionStress
                val util = SafeMath.div(demand, root)
                val ok = demand <= root
                trace.add(
                    title = "Torsion — section adequacy (ACI §22.7.7.1a)",
                    formula = "(Vu/(b·d) + Tu·Pcp/(1.7·Acp²)) ≤ √f'c",
                    substitution = "Vu=${f(input.vuKn)} kN, b·d=${(b * d)} mm², Tu·Pcp/(1.7·Acp²)=${f(torsionStress)} MPa" +
                        (if (vuN > 0) ", Vu/(b·d)=${f(shearStress)} MPa" else ""),
                    result = f(demand), limit = "≤ ${f(root)} MPa",
                    status = if (ok) CheckStatus.PASS else CheckStatus.FAIL,
                    utilization = util, codeReference = "ACI 318-19 §22.7.7.1a"
                )
                val tuUtil = SafeMath.div(tuNmm, tuMax)
                val tuOk = tuNmm <= tuMax
                trace.add(
                    title = "Torsion — crushing bound",
                    formula = "Tu ≤ 1.7·√f'c·(Acp²/Pcp)",
                    substitution = "Tu=${f(input.tuKnm)} kN·m, bound=${f(tuMax / 1e6)} kN·m",
                    result = f(input.tuKnm), limit = "≤ ${f(tuMax / 1e6)} kN·m",
                    status = if (tuOk) CheckStatus.PASS else CheckStatus.FAIL,
                    utilization = tuUtil, codeReference = "ACI 318-19 §22.7.7.1"
                )
                Pair(ok && tuOk, max(util, tuUtil))
            }
        }

        // ── Required transverse (stirrup) steel At/s ──
        val cotTheta = 1.0 / tan(Math.toRadians(input.thetaDeg))
        val fytDesign = params.torsionTransverseDesignMpa(input.steel)
        val atOverSMin = params.torsionMinTransverseMm2PerMm(b, input.concrete, input.steel)
        val atOverSeq = SafeMath.div(tuNmm, phi * 2.0 * ao * fytDesign * cotTheta)  // equilibrium demand
        val atOverSReq = if (state == TorsionState.FULL_DESIGN) max(atOverSeq, atOverSMin) else atOverSMin

        trace.add(
            title = "Torsion — required transverse steel At/s",
            formula = "At/s = Tu / (${if (phi < 1) "φ·" else ""}2·Ao·fyt·cotθ)" + (if (state != TorsionState.FULL_DESIGN) "  (minimum only)" else ""),
            substitution = "Tu=${f(tuNmm)} N·mm, Ao=${f(ao)}, fyt(d)=${f(fytDesign)}, cotθ=${f(cotTheta)}" +
                (if (phi < 1) ", φ=$phi" else ""),
            result = f(atOverSReq), limit = "≥ ${f(atOverSMin)} mm²/mm (min)",
            status = CheckStatus.PASS, utilization = null,
            codeReference = "$ref §22.7.6.1(a)"
        )

        // ── Select closed-stirrup diameter + spacing ──
        // Feasible if area/maxSpacing ≥ required (smallest dia that still meets demand at
        // the maximum allowed spacing); then spacing = min(area/required, maxSpacing).
        val minSpacing = 75.0
        val maxSpacing = when {
            params.torsionPhi == null -> min(ph / 8.0, 200.0)   // ECP §4-3-4
            else -> min(ph / 8.0, 300.0)                        // ACI §9.7.6.3.3
        }
        val stirrupMenu = listOf(8.0, 10.0, 12.0, 14.0, 16.0)
        var selectedDia = stirrupMenu.last()
        var spacing = minSpacing
        var spacingWarn = false
        for (dia in stirrupMenu) {
            val area = PI * dia * dia / 4.0
            if (SafeMath.div(area, maxSpacing) >= atOverSReq - 1e-9) {
                selectedDia = dia
                spacing = (area / atOverSReq).coerceAtMost(maxSpacing).coerceAtLeast(minSpacing)
                break
            }
        }
        if (SafeMath.div(PI * selectedDia * selectedDia / 4.0, maxSpacing) < atOverSReq - 1e-9) {
            // even the largest stirrup at minimum spacing cannot meet demand → section must grow
            spacing = minSpacing
            spacingWarn = true
        }
        val providedAtOverS = (PI * selectedDia * selectedDia / 4.0) / spacing
        if (spacingWarn) {
            trace.add(
                title = "Torsion — stirrup selection",
                formula = "choose closed stirrup Ø; spacing = A_t/(At/s) within [75, ${f(maxSpacing)}] mm",
                substitution = "At/s=${f(atOverSReq)} mm²/mm → Ø$selectedDia @ ${f(spacing)} mm",
                result = "spacing exceeds limit",
                limit = "≤ ${f(maxSpacing)} mm",
                status = CheckStatus.WARNING,
                utilization = null, codeReference = "$ref §22.7.6 / §9.7.6"
            )
        } else {
            trace.add(
                title = "Torsion — stirrup selection",
                formula = "spacing = A_t/(At/s),  min(Ph/8, ${if (params.torsionPhi == null) "200" else "300"})",
                substitution = "At/s=${f(atOverSReq)} → Ø$selectedDia @ ${f(spacing)} mm (provided At/s=${f(providedAtOverS)})",
                result = "Ø$selectedDia @ ${f(spacing)} mm",
                limit = "within [75, ${f(maxSpacing)}] mm",
                status = CheckStatus.PASS, utilization = null, codeReference = "$ref §22.7.6"
            )
        }

        // ── Required longitudinal torsion steel Al ──
        val alEquilibrium = providedAtOverS * spacing * (ao / ph) * cotTheta * cotTheta
        val alMin = 0.0025 * b * h   // 0.25 % of gross area (governing floor, §22.7.6.1(b)/ECP §4-3-4)
        val alTotal = max(alEquilibrium, alMin)
        val longMenu = params.barMenuMm
        var longDia = longMenu.last()
        var longBars = 4
        for (dia in longMenu) {
            val area = PI * dia * dia / 4.0
            val n = max(4, ceil(SafeMath.div(alTotal, area)).toInt())
            if (n <= 12) { longDia = dia; longBars = n; break }
        }
        val longAreaProvided = longBars * (PI * longDia * longDia / 4.0)
        val alUtil = SafeMath.div(longAreaProvided, alTotal)
        trace.add(
            title = "Torsion — longitudinal steel Al",
            formula = "Al = At·(fyt/fyl)·(Ao/Ph)·cot²θ  (≥ 0.25 %·b·h, ≥1 bar/corner)",
            substitution = "At/s·s·(Ao/Ph)·cot²θ=${f(alEquilibrium)}, Al,min=${f(alMin)} → Al=${f(alTotal)} mm²",
            result = "${longBars}Ø$longDia (${f(longAreaProvided)} mm²)",
            limit = "≥ ${f(alTotal)} mm²",
            status = if (longAreaProvided + 1e-6 >= alTotal) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = alUtil, codeReference = "$ref §22.7.6.1(b)"
        )

        val utilization = max(sectionUtil, alUtil)
        val isSafe = sectionAdequate && longAreaProvided + 1e-6 >= alTotal && !spacingWarn

        return Outcome(
            acpMm2 = acp, pcpMm = pcp, aohMm2 = aoh, aoMm2 = ao, phMm = ph,
            tuKnm = input.tuKnm, tuThKnm = tuTh / 1e6, tuCrKnm = tuCr / 1e6,             tuMaxKnm = tuMax / 1e6,
            torsionState = state, sectionAdequate = sectionAdequate, atOverSReq = atOverSReq, atOverSMin = atOverSMin,
            atOverSProvided = providedAtOverS, stirrupDiaMm = selectedDia, stirrupSpacingMm = spacing,
            longitudinalAreaMm2 = alTotal, longitudinalBars = longBars, longitudinalDiaMm = longDia,
            combinedStressUtil = if (params.torsionPhi != null) sectionUtil else null,
            utilization = utilization, isSafe = isSafe, trace = trace
        )
    }

    private fun f(v: Double) = String.format("%.4f", v).trimEnd('0').trimEnd('.')
}
