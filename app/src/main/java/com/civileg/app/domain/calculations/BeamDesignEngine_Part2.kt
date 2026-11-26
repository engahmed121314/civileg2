package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.calculations.beam.RcDevelopmentLength
import kotlin.math.*

/**
 * Part 2 of BeamDesignEngine - Shear, Torsion, Deflection, Crack Width, Development Length
 * These helper functions are called from BeamDesignEngine.designBeam()
 */
object BeamDesignEnginePart2 {

    private const val GAMMA_C_ECP = 1.5
    private const val GAMMA_S_ECP = 1.15
    private const val BETA_WHITNEY = 0.8
    private const val PHI_SHEAR_ACI = 0.75
    private const val PI = 3.141592653589793

    // ==================== SHEAR DESIGN (PDF 09) ====================

    data class ShearDesignResult(
        val vc: Double, val vcFormula: String,
        val maxStress: Double, val appliedStress: Double,
        val vs: Double, val avRequired: Double, val avProvided: Double,
        val stirrupDia: Int, val legs: Int,
        val spacingSupport: Double, val spacingMidspan: Double,
        val condensationZone: Double,
        val avMin: Double, val maxSpacing: Double, val minSpacing: Double,
        val steps: List<CalculationStep>,
        val warnings: List<String>, val codeNotes: List<String>
    )

    fun designShear(
        b: Double, d: Double, vu: Double,
        fcu: Double, fy: Double,
        code: DesignCode, span: Double
    ): ShearDesignResult {
        // ── InputGuard (ADR-010) ──
        InputGuard.positive("b", b)
        InputGuard.positive("d", d)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("span", span)
        InputGuard.nonNegative("vu", vu)

        val steps = mutableListOf<CalculationStep>()
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // Shear stress at critical section (at d from face of support)
        val qu = (vu * 1000.0) / (b * d) // MPa

        // Concrete shear capacity
        val (vc, vcFormula) = when (code) {
            DesignCode.ECP -> {
                // ECP 203: qcu = 0.24√(f'cu/γc)  (PDF-09)
                val vcVal = 0.24 * sqrt(fcu / GAMMA_C_ECP)
                Pair(vcVal, "qcu = 0.24√(f'cu/γc) = 0.24×√${String.format("%.2f", fcu/GAMMA_C_ECP)} = ${String.format("%.3f", vcVal)} MPa")
            }
            else -> {
                // ACI 318: Vc = 0.17λ√f'c  (λ=1 for normal weight)
                val fcPrime = fcu * 0.8
                val vcVal = 0.17 * sqrt(fcPrime)
                Pair(vcVal, "Vc = 0.17√f'c = 0.17×√${String.format("%.1f", fcPrime)} = ${String.format("%.3f", vcVal)} MPa")
            }
        }

        // Maximum shear stress
        val quMax = when (code) {
            DesignCode.ECP -> 0.7 * sqrt(fcu / GAMMA_C_ECP)  // ECP 203
            else -> 0.5 * sqrt(fcu * 0.8) // ACI 318 simplified
        }

        steps.add(CalculationStep(
            1, "Shear Design (PDF-09)",
            codeReference = when(code) { DesignCode.ECP -> "ECP 203 §4-2-6" else -> "ACI 318 §22.5" },
            formula = "qu = Vu/(b×d), qcu = concrete capacity, qu,max = max allowed",
            formulaWithValues = "qu = ${String.format("%.1f", vu)}×1000/($b × ${String.format("%.0f", d)}) = ${String.format("%.3f", qu)} MPa\n$vcFormula\nqu,max = ${String.format("%.3f", quMax)} MPa",
            result = "qu = ${String.format("%.3f", qu)} MPa ${if (qu <= vc) "≤ qcu = ${String.format("%.3f", vc)} → No stirrups needed (min only)" else if (qu <= quMax) "→ Stirrups required" else "> qu,max → SECTION TOO SMALL"}",
            unit = "MPa",
            isPass = qu <= quMax,
            status = if (qu <= quMax) StepStatus.CALCULATION else StepStatus.CHECK_FAIL
        ))

        // Stirrup design
        var vs = 0.0
        var avRequired = 0.0
        var stirrupDia = 8
        var spacingSupport = 200.0
        var spacingMidspan = 250.0
        val legs = 2

        if (qu > vc) {
            vs = qu - vc / 2.0 // ECP approach: vs = qu - qcu/2
            avRequired = vs * b / (fy / when(code) { DesignCode.ECP -> GAMMA_S_ECP else -> 1.0 }) * spacingSupport

            // Try 8mm stirrups first
            val av8 = legs * PI * 8.0.pow(2) / 4.0
            var s8 = (av8 * (fy / when(code) { DesignCode.ECP -> GAMMA_S_ECP else -> 1.0 }) * d) / (vs * b)
            val limit8 = if (code == DesignCode.ECP) minOf(d / 2.0, 200.0) else minOf(d / 2.0, 300.0)
            s8 = minOf(s8, limit8)
            s8 = floor(s8 / 25.0) * 25.0 // round down to 25mm
            s8 = maxOf(s8, 75.0)

            if (s8 >= 75.0) {
                stirrupDia = 8
                spacingSupport = s8
            } else {
                // Try 10mm
                val av10 = legs * PI * 10.0.pow(2) / 4.0
                var s10 = (av10 * (fy / when(code) { DesignCode.ECP -> GAMMA_S_ECP else -> 1.0 }) * d) / (vs * b)
                val limit10 = if (code == DesignCode.ECP) minOf(d / 2.0, 200.0) else minOf(d / 2.0, 300.0)
                s10 = minOf(s10, limit10)
                s10 = floor(s10 / 25.0) * 25.0
                s10 = maxOf(s10, 75.0)
                stirrupDia = 10
                spacingSupport = s10
            }
        } else {
            // Minimum shear reinforcement
            spacingSupport = if (code == DesignCode.ECP) minOf(d / 2.0, 200.0) else minOf(d / 2.0, 300.0)
        }

        // Midspan spacing (reduced shear at midspan for SS/FH)
        val limitMid = if (code == DesignCode.ECP) 250.0 else 300.0
        spacingMidspan = minOf(spacingSupport * 1.5, limitMid)
        spacingMidspan = floor(spacingMidspan / 25.0) * 25.0

        val condensationZone = minOf(d, span / 4.0 * 1000.0) // d or L/4
        val avProvided = legs * PI * stirrupDia.toDouble().pow(2) / 4.0
        val avMin = when(code) {
            DesignCode.ECP -> 0.4 * b / (fy / GAMMA_S_ECP)
            else -> 0.062 * sqrt(fcu * 0.8) * b / fy
        }
        val maxSpacing = if (code == DesignCode.ECP) minOf(d / 2.0, 200.0) else minOf(d / 2.0, 300.0)
        val minSpacing = maxOf(75.0, if (code == DesignCode.SBC) 100.0 else 75.0)

        val stirrupDesc = "${ceil(span*1000.0/spacingSupport).toInt()}Ø$stirrupDia @ ${spacingSupport.toInt()}mm c/c"
        codeNotes.add(when(code) { DesignCode.ECP -> "ECP 203 §4-2-6-2"; DesignCode.ACI -> "ACI 318 §25.5"; else -> "SBC 304" })

        steps.add(CalculationStep(
            2, "Stirrup Design",
            formula = when(code) { DesignCode.ECP -> "Av/s = (qu - qcu/2) × b / (fy/γs)" else -> "Av/s = (Vu - φVc) / (φ×fy×d)" },
            formulaWithValues = "Stirrups: $stirrupDesc\nAv = $legs×Ø$stirrupDia = ${String.format("%.1f", avProvided)} mm²\nAv/min = ${String.format("%.2f", avMin)} mm²/m",
            result = "Support zone: Ø$stirrupDia @ ${spacingSupport.toInt()}mm c/c\nMidspan: Ø$stirrupDia @ ${spacingMidspan.toInt()}mm c/c\nCondensation zone: ${String.format("%.0f", condensationZone)}mm from supports",
            unit = "mm"
        ))

        if (qu > quMax) warnings.add("Shear stress exceeds maximum - increase section")

        return ShearDesignResult(
            vc = vc, vcFormula = vcFormula, maxStress = quMax, appliedStress = qu,
            vs = vs, avRequired = avRequired, avProvided = avProvided,
            stirrupDia = stirrupDia, legs = legs,
            spacingSupport = spacingSupport, spacingMidspan = spacingMidspan,
            condensationZone = condensationZone,
            avMin = avMin, maxSpacing = maxSpacing, minSpacing = minSpacing,
            steps = steps, warnings = warnings, codeNotes = codeNotes
        )
    }

    // ==================== TORSION DESIGN (PDF 10) ====================

    data class TorsionDesignResult(
        val threshold: Double = 0.0,
        val combinedStress: Double = 0.0, val combinedCapacity: Double = 0.0,
        val isSafe: Boolean = true,
        val reinforcementDesc: String = "",
        val stirrupSpacing: Double = 0.0,
        val longitudinalAs: Double = 0.0,
        val longitudinalBarsDesc: String = "",
        val steps: List<CalculationStep> = emptyList()
    )

    fun designTorsion(
        b: Double, h: Double, d: Double,
        Tu: Double, Vu: Double,
        fcu: Double, fy: Double,
        code: DesignCode, cover: Double
    ): TorsionDesignResult {
        // ── InputGuard (ADR-010) ──
        InputGuard.positive("b", b)
        InputGuard.positive("h", h)
        InputGuard.positive("d", d)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.nonNegative("Tu", Tu)
        InputGuard.nonNegative("Vu", Vu)
        InputGuard.nonNegative("cover", cover)

        val steps = mutableListOf<CalculationStep>()

        // Torsional threshold (below which torsion can be neglected)
        val TuTh = when(code) {
            DesignCode.ECP -> 0.083 * sqrt(fcu / GAMMA_C_ECP) * b * b * d / 1e6 // kN.m
            else -> 0.083 * sqrt(fcu * 0.8) * b * b * d / 1e6 * 0.75
        }

        if (Tu <= TuTh) {
            steps.add(CalculationStep(
                1, "Torsion Check (PDF-10)",
                formula = "Tu,th = 0.083√(f'cu/γc)×b²×d / 10⁶",
                formulaWithValues = "Tu,th = ${String.format("%.3f", TuTh)} kN.m ≥ Tu = ${String.format("%.3f", Tu)} kN.m → Torsion neglected",
                result = "No torsion design needed",
                unit = "kN.m",
                status = StepStatus.INFO
            ))
            return TorsionDesignResult(threshold = TuTh, steps = steps)
        }

        // Combined shear + torsion
        val vt = Tu * 1e6 * 2.0 / (b * b * (d - cover)) // torsional shear stress
        val qu = Vu * 1000.0 / (b * d)
        val combined = qu + vt
        val capacity = when(code) {
            DesignCode.ECP -> 0.7 * sqrt(fcu / GAMMA_C_ECP)
            else -> 0.5 * sqrt(fcu * 0.8)
        }

        // Torsional reinforcement
        val Aoh = (b - 2*cover) * (d - 2*cover) // area inside stirrups
        val Ao = 0.85 * Aoh
        val theta = 45.0 // degrees
        // FIX: Use code-appropriate steel stress for torsion (ECP uses fy/γs, ACI uses fy directly with φ)
        val fsTorsion = when(code) { DesignCode.ECP -> fy / GAMMA_S_ECP else -> fy }
        val AtOverS = (Tu * 1e6) / (2.0 * Ao * fsTorsion * sin(theta * PI / 180.0))
        val Al = (AtOverS * 2.0 * (b + d) * fsTorsion) / fsTorsion
        val stirrupSpacingTorsion = (PI * 8.0.pow(2) / 4.0) / AtOverS
        val clampedSpacing = maxOf(75.0, minOf(stirrupSpacingTorsion, b / 4.0, 200.0))

        val longBarCount = max(4, ceil(Al / (PI * 12.0.pow(2) / 4.0)).toInt())
        val longBarDia = 12

        steps.add(CalculationStep(
            1, "Torsion Design (PDF-10)",
            codeReference = when(code) { DesignCode.ECP -> "ECP 203 §4-2-7" else -> "ACI 318 §22.7" },
            formula = "τt + τv ≤ τmax\nAt/s = Tu/(2×Ao×fs×sinθ)\nAl = At/s × 2×(b+d)×(fs_t/fs_l)",
            formulaWithValues = "τv = ${String.format("%.3f", qu)} MPa\nτt = ${String.format("%.3f", vt)} MPa\nτv + τt = ${String.format("%.3f", combined)} MPa ≤ ${String.format("%.3f", capacity)} MPa → ${if (combined <= capacity) "OK" else "FAIL"}",
            result = "Torsion stirrups: additional legs required\nLongitudinal torsion: ${longBarCount}Ø${longBarDia}",
            unit = "MPa",
            isPass = combined <= capacity,
            status = if (combined <= capacity) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
        ))

        return TorsionDesignResult(
            threshold = TuTh, combinedStress = combined, combinedCapacity = capacity,
            isSafe = combined <= capacity,
            reinforcementDesc = "Additional torsional legs + ${longBarCount}Ø${longBarDia} longitudinal",
            stirrupSpacing = clampedSpacing,
            longitudinalAs = Al, longitudinalBarsDesc = "${longBarCount}Ø$longBarDia",
            steps = steps
        )
    }

    // ==================== DEFLECTION CHECK (Enhanced Ie Method) ====================

    data class DeflectionResult(
        val Ig: Double, val Icr: Double, val Ie: Double,
        val Ec: Double, val Mcr: Double,
        val immediate: Double, val longTerm: Double, val allowable: Double,
        val basicRatio: Double, val modifiedRatio: Double,
        val ratioDesc: String,
        val steps: List<CalculationStep>,
        /** ECP only: true when the deflection value was computed (Eq 4-70, 4-73); false when only Table 4-10 applies. */
        val computed: Boolean = true,
        /** ECP Table 4-10 allowable L/t (null when the table does not apply: span > 6 m, cantilever >= 2 m or fy > 500). */
        val tableAllowableRatio: Double? = null,
        val tableActualRatio: Double = 0.0,
        /** True when the ECP deflection check cannot be made (no computation for this support type and Table 4-10 not applicable). */
        val notAssessed: Boolean = false
    )

    fun checkDeflection(
        b: Double, h: Double, d: Double, span: Double,
        mu: Double, fcu: Double, fy: Double,
        code: DesignCode, asProvided: Double,
        nADepth: Double,
        serviceRatio: Double = 1.0,
        sustainedFraction: Double = 1.0,
        supportType: String = "SS",
        flangeWidth: Double = 0.0
    ): DeflectionResult {
        if (code == DesignCode.ECP) return checkDeflectionEcp2025(b, h, d, span, mu, fcu, fy, asProvided, nADepth, serviceRatio, sustainedFraction, supportType, flangeWidth)
        // ── InputGuard (ADR-010) ──
        InputGuard.positive("b", b)
        InputGuard.positive("h", h)
        InputGuard.positive("d", d)
        InputGuard.positive("span", span)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.nonNegative("mu", mu)
        InputGuard.nonNegative("asProvided", asProvided)

        val steps = mutableListOf<CalculationStep>()

        val Ec = when(code) {
            DesignCode.ECP -> 4400.0 * sqrt(fcu)
            else -> 4700.0 * sqrt(fcu * 0.8)
        }
        val Ig = b * h.pow(3) / 12.0 // gross moment of inertia

        // Cracked moment of inertia (simplified)
        val n = 200000.0 / Ec // modular ratio
        val x = nADepth // neutral axis depth from flexure design
        val Icr = b * x.pow(3) / 3.0 + n * asProvided * (d - x).pow(2) // transformed section

        // Cracking moment
        val fr = when(code) {
            DesignCode.ECP -> 0.6 * sqrt(fcu) // ECP modulus of rupture
            else -> 0.62 * sqrt(fcu * 0.8) // ACI
        }
        val yt = h / 2.0
        val Mcr = fr * Ig / yt / 1e6 // kN.m

        // Effective moment of inertia (Branson equation)
        val Ma = mu // applied moment
        val Ie = if (Ma <= Mcr) {
            Ig
        } else {
            (Mcr / Ma).pow(3) * Ig + (1.0 - (Mcr / Ma).pow(3)) * Icr
        }

        // Immediate deflection (simply supported UDL: 5wL⁴/384EI)
        val wu = mu * 8.0 / (span * span)
        val immediate = (5.0 * wu * (span * 1000.0).pow(4)) / (384.0 * Ec * Ie)

        // Long-term multiplier (creep + shrinkage)
        val longTermFactor = when(code) {
            DesignCode.ECP -> 2.0  // ECP simplified
            else -> 2.0    // ACI: λ = ξ/(1+50ρ')
        }
        val longTerm = immediate * longTermFactor

        // Allowable deflection
        val allowable = span * 1000.0 / 250.0 // L/250
        val basicRatio = when(code) {
            DesignCode.ECP -> 20.0 // SS basic L/d
            else -> 16.0  // ACI
        }
        val modifiedRatio = basicRatio * (Ie / Ig).pow(1.0/3.0)

        val ratioDesc = "L/${String.format("%.0f", span*1000.0/max(longTerm, 0.1))}"

        steps.add(CalculationStep(
            1, "Deflection Check (Enhanced Ie Method)",
            codeReference = when(code) { DesignCode.ECP -> "ECP 203 §4-2-3" else -> "ACI 318 §24.2" },
            formula = "Ig = b×h³/12, Icr (transformed section), Ie = (Mcr/Ma)³×Ig + [1-(Mcr/Ma)³]×Icr\nΔ = 5×wu×L⁴/(384×Ec×Ie)",
            formulaWithValues = "Ig = ${String.format("%.0e", Ig)} mm⁴\nIcr = ${String.format("%.0e", Icr)} mm⁴\nIe = ${String.format("%.0e", Ie)} mm⁴\nMcr = ${String.format("%.2f", Mcr)} kN.m\nΔ(immediate) = ${String.format("%.2f", immediate)} mm\nΔ(long-term) = ${String.format("%.2f", longTerm)} mm\nΔ(allowable) = L/250 = ${String.format("%.2f", allowable)} mm",
            result = "${if (longTerm <= allowable) "PASS" else "FAIL"}: Δ = ${String.format("%.2f", longTerm)} mm ≤ ${String.format("%.2f", allowable)} mm",
            unit = "mm",
            isPass = longTerm <= allowable,
            status = if (longTerm <= allowable) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
        ))

        return DeflectionResult(
            Ig = Ig, Icr = Icr, Ie = Ie, Ec = Ec, Mcr = Mcr,
            immediate = immediate, longTerm = longTerm, allowable = allowable,
            basicRatio = basicRatio, modifiedRatio = modifiedRatio,
            ratioDesc = ratioDesc, steps = steps
        )
    }


    /**
     * ECP 203-2025 beam deflection. Ec = 4400 sqrt(fcu) (2-3-3-1, p.2-23), Es = 200000 (Fig 4-1), Mcr = fctr Ig / yt (4-71),
     * Ie Eq 4-70 with Icr <= Ig, long-term alpha = xi / (1 + 50 rho') Eq 4-73 (xi = 2.0, five years or more),
     * total limit L/250 (4-74), live-load limit L/360 (4-76). The modular ratio n = Es/Ec for Icr is a choice, not a
     * printed clause (page not found). Computed for simply supported beams only; other supports use Table 4-10
     * (p.4-65). rho' = 0 (compression steel is not an input here), sustained part = dead share of service load.
     */
    private fun checkDeflectionEcp2025(
        b: Double, h: Double, d: Double, span: Double, mu: Double, fcu: Double, fy: Double,
        asProvided: Double, nADepth: Double, serviceRatio: Double, sustainedFraction: Double,
        supportType: String, flangeWidth: Double
    ): DeflectionResult {
        InputGuard.positive("b", b); InputGuard.positive("h", h); InputGuard.positive("d", d)
        InputGuard.positive("span", span); InputGuard.positive("fcu", fcu); InputGuard.positive("fy", fy)
        InputGuard.nonNegative("mu", mu); InputGuard.nonNegative("asProvided", asProvided)
        val D = com.civileg.app.domain.calculations.ecp.ECP2025Deflection
        val steps = mutableListOf<CalculationStep>()
        val spanMm = span * 1000.0
        val ec = 4400.0 * sqrt(fcu)
        val ig = b * h.pow(3) / 12.0
        val n = 200000.0 / ec
        val x = nADepth
        val icr = b * x.pow(3) / 3.0 + n * asProvided * (d - x).pow(2)
        val fctr = D.fctr(fcu, fy)
        val mcr = D.crackingMoment(fctr, ig, h / 2.0) / 1e6
        val ma = mu * serviceRatio
        val simple = supportType == "SS" || supportType == "SIMPLY_SUPPORTED"
        val ie = if (asProvided > 0.0 && mcr > 0.0 && icr > 0.0) D.effectiveInertia(mcr, ma, ig, icr) else ig
        val allowable = spanMm / 250.0
        var immediate = 0.0; var longTerm = 0.0
        if (simple) {
            val ws = ma * 8.0 / (span * span)
            immediate = 5.0 * ws * spanMm.pow(4) / (384.0 * ec * ie)
            val alpha = D.longTermMultiplier(D.timeFactorXi(60)!!, 0.0)
            longTerm = D.totalDeflection(immediate, immediate * sustainedFraction, alpha)
        }
        // Table 4-10 (p.4-65), beams are "dropped beams"; T-beam factor from Fig 4-32
        val sup = when (supportType) {
            "SS", "SIMPLY_SUPPORTED" -> D.Support.SIMPLE
            "CANTILEVER" -> D.Support.CANTILEVER
            "FH", "FIXED_HINGED" -> D.Support.ONE_END_CONTINUOUS
            else -> D.Support.BOTH_ENDS_CONTINUOUS
        }
        val applicable = (sup == D.Support.CANTILEVER && span < 2.0) || (sup != D.Support.CANTILEVER && span <= 6.0)
        val baseTable = if (applicable) D.spanToDepthRatio(D.Element.RIGID_BEAM, sup, fy) else null
        val delta = if (flangeWidth > b) D.tBeamFactor(b, flangeWidth) else 1.0
        val tableAllow = baseTable?.let { it * delta }
        val actual = spanMm / h
        val notAssessed = !simple && tableAllow == null
        val computed = simple
        steps.add(CalculationStep(
            1, "Deflection Check (ECP 203-2025)",
            codeReference = "ECP 203-2025 Eq 4-70, 4-71, 4-73, 4-74 (pp.4-60 to 4-62); Ec 2-3-3-1 p.2-23; Table 4-10 p.4-65",
            formula = "Ie = (Mcr/Ma)^3 Ig + [1-(Mcr/Ma)^3] Icr (Icr <= Ig); alpha = xi/(1+50 rho'); total = d_i + alpha d_sustained",
            formulaWithValues = if (simple) "Ec = ${String.format("%.0f", ec)} MPa, Mcr = ${String.format("%.2f", mcr)} kN.m, Ma (service) = ${String.format("%.2f", ma)} kN.m\nIe = ${String.format("%.3e", ie)} mm4, immediate = ${String.format("%.2f", immediate)} mm, alpha = 2.00 (xi 2.0, rho' = 0)\nn = Es/Ec is an implementation choice (page not found); rho' = 0 (not an input)"
                else "Deflection is not computed for this support type; Table 4-10 is used" ,
            result = when {
                simple -> "${if (longTerm <= allowable) "PASS" else "FAIL"}: total = ${String.format("%.2f", longTerm)} mm vs L/250 = ${String.format("%.2f", allowable)} mm"
                tableAllow != null -> "${if (actual <= tableAllow) "PASS" else "FAIL"}: L/h = ${String.format("%.1f", actual)} vs Table 4-10 limit ${String.format("%.1f", tableAllow)} (T-beam factor ${String.format("%.2f", delta)})"
                else -> "NOT ASSESSED: Table 4-10 does not apply (span > 6 m, cantilever >= 2 m or fy > 500) and deflection is not computed for this support type"
            },
            unit = "mm",
            isPass = if (simple) longTerm <= allowable else (tableAllow != null && actual <= tableAllow),
            status = when {
                notAssessed -> StepStatus.INFO
                (if (simple) longTerm <= allowable else actual <= (tableAllow ?: 0.0)) -> StepStatus.CHECK_PASS
                else -> StepStatus.CHECK_FAIL
            }
        ))
        return DeflectionResult(
            Ig = ig, Icr = icr, Ie = ie, Ec = ec, Mcr = mcr,
            immediate = immediate, longTerm = longTerm, allowable = allowable,
            basicRatio = baseTable ?: 0.0, modifiedRatio = tableAllow ?: 0.0,
            ratioDesc = if (simple) "L/${String.format("%.0f", spanMm / max(longTerm, 0.1))}" else "L/h = ${String.format("%.1f", actual)}",
            steps = steps, computed = computed, tableAllowableRatio = tableAllow, tableActualRatio = actual, notAssessed = notAssessed
        )
    }

    // ==================== CRACK WIDTH CHECK ====================

    data class CrackWidthResult(
        val calculated: Double, val allowable: Double,
        val avgStrain: Double, val surfaceStrain: Double,
        val step: CalculationStep
    )

    fun checkCrackWidth(
        b: Double, d: Double, span: Double, mu: Double,
        fcu: Double, fy: Double, code: DesignCode,
        asProvided: Double, nADepth: Double,
        es: Double, ec: Double
    ): CrackWidthResult {
        // ── InputGuard (ADR-010) ──
        InputGuard.positive("b", b)
        InputGuard.positive("d", d)
        InputGuard.positive("span", span)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("es", es)
        InputGuard.positive("ec", ec)

        val allowable = 0.3 // mm (typical for ECP/ACI)
        if (asProvided <= 0) return CrackWidthResult(0.0, allowable, 0.0, 0.0, CalculationStep(0, "", "", "", "", ""))

        // ECP 203: wk = 3.3 × εm × acr
        val fsService = mu * 1e6 / (asProvided * d) * 0.6 // approximate service steel stress
        val surfaceStrain = fsService / es
        val alphaE = es / ec
        val xRatio = if (d > 0) nADepth / d else 0.3
        val avgStrain = surfaceStrain * (1.0 - xRatio) / 3.0
        val acr = sqrt(((b - asProvided.toDouble() / 2.0).pow(2) + (d / 2.0).pow(2))) // distance to nearest bar
        val wk = 3.3 * avgStrain * acr

        val step = CalculationStep(
            1, "Crack Width Check",
            codeReference = when(code) { DesignCode.ECP -> "ECP 203 §4-2-4" else -> "ACI 318 §24.5" },
            formula = "wk = 3.3 × εm × acr",
            formulaWithValues = "εs ≈ ${String.format("%.6f", surfaceStrain)}, εm ≈ ${String.format("%.6f", avgStrain)}\nacr ≈ ${String.format("%.0f", acr)} mm\nwk = 3.3 × ${String.format("%.6f", avgStrain)} × ${String.format("%.0f", acr)} = ${String.format("%.3f", wk)} mm",
            result = "wk = ${String.format("%.3f", wk)} mm ≤ ${allowable} mm → ${if (wk <= allowable) "OK" else "FAIL"}",
            unit = "mm",
            isPass = wk <= allowable,
            status = if (wk <= allowable) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
        )

        return CrackWidthResult(wk, allowable, avgStrain, surfaceStrain, step)
    }

    // ==================== DEVELOPMENT LENGTH ====================

    data class DevLengthResult(
        val required: Double, val provided: Double,
        val fbd: Double, val lapLength: Double,
        val step: CalculationStep
    )

    fun checkDevelopmentLength(
        fcu: Double, fy: Double, dia: Int,
        code: DesignCode, span: Double, d: Double
    ): DevLengthResult {
        // ── InputGuard (ADR-010) ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("dia", dia)
        InputGuard.positive("span", span)
        InputGuard.positive("d", d)

        // ECP only: lengths from RcDevelopmentLength (ECP 203-2020 Eq. 4-66/4-67). Bottom bar (eta = 1.0), straight end,
        // uncoated, normalweight, not bundled; these are stated in the step. ACI/SBC need cylinder strength fc' and the
        // app input is not confirmed to be fc', so they are reported as not computed (required = 0.0).
        val ecp = code == DesignCode.ECP
        val r = if (ecp) RcDevelopmentLength.ecpTension(fcu, fy, dia.toDouble()) else null
        val fbd = if (ecp) 0.30 * sqrt(fcu / RcDevelopmentLength.ECP_GAMMA_C) else 0.0
        val LdMin = r?.length ?: 0.0
        // Available anchorage is not computed. A span/3 stand-in was used before and produced a fake pass/fail.
        val provided = 0.0 // 0.0 = not computed
        val lapLength = if (ecp) RcDevelopmentLength.ecpTensionLap(LdMin, null, null) else 0.0

        val f0 = { v: Double -> String.format("%.0f", v) }
        val step = if (r != null) CalculationStep(
            1, "Development & Lap Length (tension, straight bottom bar; available anchorage not evaluated)",
            codeReference = "ECP 203-2020 section 4-2-5-1 Eq. 4-66/4-67, Tables 4-7/4-8; lap 4-2-5-4-1",
            formula = r.basis,
            formulaWithValues = "Ld,formula = ${f0(r.formulaLength)} mm; minimum ${f0(r.minimum)} mm; Ld = ${f0(LdMin)} mm\n" +
                "Lap = ${f0(lapLength)} mm (1.3 Ld case: area ratio and lapped fraction not known)",
            result = "Ld = ${f0(LdMin)} mm, Lap = ${f0(lapLength)} mm (bottom bar, eta 1.0, no end hook, uncoated, normalweight, not bundled)",
            unit = "mm",
            status = com.civileg.app.domain.entities.StepStatus.WARNING
        ) else CalculationStep(
            1, "Development & Lap Length (not computed; available anchorage not evaluated)",
            codeReference = "${code.displayName}: not computed, cylinder strength fc' basis not resolved",
            formula = "Not computed",
            formulaWithValues = "Not computed",
            result = "Development and lap lengths not computed for this code",
            unit = "mm",
            status = com.civileg.app.domain.entities.StepStatus.WARNING
        )

        return DevLengthResult(LdMin, provided, fbd, lapLength, step)
    }
}