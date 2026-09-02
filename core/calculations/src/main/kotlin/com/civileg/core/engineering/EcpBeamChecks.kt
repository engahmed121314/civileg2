package com.civileg.core.engineering

import com.civileg.core.math.SafeMath
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Pilot One-Source-of-Truth beam checks (spec §20, §81) — ECP 203 K-method,
 * built on the PHASE 02/03 kernel with full calculation traces.
 *
 * Every numeric result here is cross-gated against the independent
 * `engineering/BeamGoldenBenchmarkTest` values from STEP 1.
 */
class EcpBeamChecks(
    private val concrete: ConcreteMaterial,
    private val steel: SteelMaterial,
    private val gamma: PartialSafetyFactors = PartialSafetyFactors.ECP
) {
    init { require(concrete.fcuMpa > 0 && steel.yieldMpa > 0) }

    // K_bal per ECP 203 §4-2-2-1 (εcu=0.003, β=0.9, design stresses)
    val kBalanced: Double = run {
        val epsY = steel.yieldMpa / (200_000.0 * gamma.gammaS)
        val aOverD = 0.9 * 0.003 / (0.003 + epsY)
        (0.67 / gamma.gammaC) * aOverD * (1.0 - aOverD / 2.0)
    }

    /** Minimum flexural steel ratio — ECP 203 pair (ACI-form): max(0.25√fcu/fy, 0.0013). */
    fun minSteelRatio(): Double =
        maxOf(0.25 * sqrt(concrete.fcuMpa) / steel.yieldMpa, 0.0013)

    /**
     * Singly-reinforced rectangular flexure check.
     * @param b mm · d mm · muKnm factored moment (kN·m)
     */
    fun flexure(b: Double, d: Double, h: Double, muKnm: Double): FlexureResult {
        val trace = CalculationTrace()
        val muNmm = SafeMath.requirePositive(muKnm, "Mu") * 1e6

        val k = SafeMath.div(muNmm, concrete.fcuMpa * b * d * d)
        trace.add(
            title = "Flexural K factor",
            formula = "K = Mu / (fcu · b · d²)",
            substitution = "K = ${fmt(muNmm)} / (${concrete.fcuMpa} × $b × $d²)",
            result = fmt(k),
            limit = "≤ K_bal = ${fmt(kBalanced)}",
            status = if (k <= kBalanced) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = com.civileg.core.math.SafeMath.div(k, kBalanced),
            codeReference = "ECP 203 §4-2-2-1"
        )

        val z = if (0.25 - k / K_DIVISOR > 0) d * (0.5 + sqrt(0.25 - k / K_DIVISOR))
        else error("Flexure: K=$k exceeds lever-arm validity (over-reinforced); use doubly-reinforced path")
        val fsd = steel.yieldMpa / gamma.gammaS
        var asReq = muNmm / (fsd * z)

        val minAs = minSteelRatio() * b * d
        var status = CheckStatus.PASS
        var note: String? = null
        if (asReq < minAs) {
            asReq = minAs
            status = CheckStatus.WARNING
            note = "minimum steel governs"
        }
        trace.add(
            title = "Tension reinforcement",
            formula = "z = d(0.5 + √(0.25 − K/$K_DIVISOR)) ; As = Mu/(fsd·z)",
            substitution = "z = ${fmt(z)} mm ; As = ${fmt(muNmm)}/(${fmt(fsd)}×${fmt(z)})",
            result = "${fmt(asReq)} mm²" + (note?.let { " ($it)" } ?: ""),
            limit = "≥ As,min = ${fmt(minAs)} mm²",
            status = status,
            utilization = asReq / maxOf(minAs, asReq),
            codeReference = "ECP 203 §4-2-2-1 / §4-2-2-3"
        )

        val (n, dia) = RebarTable.select(asReq)
        val asProv = n * RebarTable.area(dia)
        trace.add(
            title = "Bar selection",
            formula = "n·πØ²/4 ≥ As",
            substitution = "${n}Ø${dia.toInt()} → ${fmt(asProv)} mm²",
            result = fmt(asProv),
            limit = "≥ ${fmt(asReq)} mm²",
            status = if (asProv >= asReq - 1e-6) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = asReq / asProv
        )

        return FlexureResult(k, z, asReq, asProvidedMm2 = asProv, bars = "${n}Ø${dia.toInt()}", trace)
    }

    /**
     * Shear check — qcu = 0.24√(fcu/γc), minimum stirrups 0.15% bd.
     * @param vuKnm factored shear (kN) on width b (mm), depth d (mm).
     */
    fun shear(b: Double, d: Double, vuKn: Double): ShearResult {
        val trace = CalculationTrace()
        val qcu = 0.24 * sqrt(concrete.fcuMpa / gamma.gammaC)
        val vcKn = qcu * b * d / 1000.0

        trace.add(
            title = "Concrete shear capacity",
            formula = "qcu = 0.24√(fcu/γc) ; Vc = qcu·b·d",
            substitution = "qcu = ${fmt(qcu)} MPa ; Vc = ${fmt(vcKn)} kN",
            result = fmt(vcKn),
            limit = "≥ Vu = ${fmt(vuKn)} kN",
            status = if (vuKn <= vcKn) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = com.civileg.core.math.SafeMath.div(vuKn, vcKn),
            codeReference = "ECP 203 §4-3-1-2"
        )

        val required = if (vuKn > vcKn) {
            ((vuKn - vcKn) * 1000.0) / (steel.yieldMpa / gamma.gammaS * d) * 1000.0
        } else 0.0
        val minStirrups = 0.0015 * b * 1000.0   // mm²/m over 1 m strip
        val governing = maxOf(required, minStirrups)
        val legArea2 = 2.0 * RebarTable.area(8.0)  // Ø8 closed stirrup, 2 legs
        val spacing = (legArea2 * 1000.0 / governing).coerceIn(50.0, 200.0)

        trace.add(
            title = "Shear reinforcement",
            formula = "As/s = (Vu−Vc)/(fsd·d) ; s = Av·1000/As,s",
            substitution = "required=${fmt(required)} mm²/m ; min=${fmt(minStirrups)} → ${fmt(governing)}",
            result = "Ø8 @ ${fmt(spacing)} mm",
            limit = "s ≤ 200 mm",
            status = if (vuKn <= vcKn) CheckStatus.PASS else CheckStatus.WARNING,
            utilization = if (vcKn > 0) vuKn / vcKn else null,
            codeReference = "ECP 203 §4-3-2"
        )

        return ShearResult(vcKn, governing, 8.0, spacing, trace)
    }

    companion object {
        const val K_DIVISOR = 0.893  // γc / (2 × 0.67)
        private fun fmt(v: Double) = String.format("%.3f", v).trimEnd('0').trimEnd('.')
    }

    data class FlexureResult(
        val k: Double,
        val leverArmZmm: Double,
        val asRequiredMm2: Double,
        val asProvidedMm2: Double,
        val bars: String,
        val trace: CalculationTrace
    )

    data class ShearResult(
        val concreteCapacityKn: Double,
        val stirrupAreaPerMeterMm2: Double,
        val stirrupDiaMm: Double,
        val spacingMm: Double,
        val trace: CalculationTrace
    )
}




