package com.civileg.core.engineering

import com.civileg.core.math.SafeMath
import kotlin.math.min

/**
 * Unified shear check (STEP 5 extension, spec §20). Same consolidation pattern
 * as [UnifiedBeamFlexure]: ONE skeleton, family rules injected via
 * [ConcreteCodeParams]. Golden-gated against legacy benchmarks S1 (ECP) and A2 (ACI).
 */
class UnifiedBeamShear(
    private val params: ConcreteCodeParams,
    private val concrete: ConcreteMaterial,
    private val steel: SteelMaterial
) {

    fun design(b: Double, d: Double, vuKn: Double): Outcome {
        SafeMath.requireNonNegative(vuKn, "Vu")
        val trace = CalculationTrace()
        val ref = params.reference

        val vc = params.concreteShearCapacityKn(b, d, concrete)
        val vcCap = params.maxShearCapacityKn(b, d, concrete)
        val vuOverVc = vuKn > vc
        val vuOverHalfVc = vuKn > vc / 2.0
        val utilMax = SafeMath.div(vuKn, vcCap)

        trace.add(
            title = "Concrete shear capacity",
            formula = if (params.family == CodeFamily.ECP_203)
                "qcu = 0.24√(fcu/γc) ; Vc = qcu·b·d"
            else
                "φVc = φ·0.17λ√f'c·b·d  (φ=0.75)",
            substitution = "Vc = ${f(vc)} kN",
            limit = f(vcCap),
            result = "${f(vuKn)} kN applied",
            status = when {
                utilMax > 1.0 -> CheckStatus.FAIL       // beyond absolute cap
                vuOverVc -> CheckStatus.WARNING          // needs shear steel
                else -> CheckStatus.PASS
            },
            utilization = utilMax,
            codeReference = ref
        )

        // Required Vs and As/s (family formulas)
        val vsKn = when (params.family) {
            CodeFamily.ECP_203 -> if (vuOverVc) vuKn - vc else 0.0
            CodeFamily.ACI_318, CodeFamily.SBC_304 -> {
                val phiVc = params.concreteShearCapacityKn(b, d, concrete) // already φVc
                if (vuKn > phiVc) (vuKn - phiVc) / 0.75 else 0.0
            }
        }
        val requiredAsS = if (vsKn > 0.0) SafeMath.div(vsKn * 1000.0, steel.yieldMpa * d) * 1000.0 else 0.0
        val minAsS = params.minShearReinforcementMm2PerM(b, concrete, steel)
        val governing = maxOf(requiredAsS, minAsS)
        val minGoverns = minAsS > requiredAsS

        // Semantic rule: WARNING only when calculated shear steel is needed.
        // Prescriptive minimum stirrups below Vc are the code default, not a defect.
        trace.add(
            title = "Shear reinforcement",
            formula = "As/s = Vs/(fy·d) ; governed by code minimum when lower",
            substitution = "Vs = ${f(vsKn)} kN → ${f(requiredAsS)} mm²/m ; min ${f(minAsS)}",
            result = "${f(governing)} mm²/m" + if (minGoverns && !vuOverVc) " (prescriptive minimum)" else "",
            limit = "≥ ${f(minAsS)} mm²/m",
            status = if (vuOverVc) CheckStatus.WARNING else CheckStatus.PASS,
            utilization = if (vc > 0) vuKn / vc else null,
            codeReference = ref
        )

        // Stirrup geometry (family policy)
        val dia = params.stirrupDiameterMm(b, vuOverHalfVc)
        val twoLegArea = 2.0 * RebarTable.area(dia)
        var spacing = if (governing > 0) twoLegArea * 1000.0 / governing else params.maxStirrupSpacingMm(d, false, false)
        val vsAboveLimit = when (params.family) {
            CodeFamily.ECP_203 -> false
            CodeFamily.ACI_318, CodeFamily.SBC_304 -> vsKn * 1000.0 >
                0.33 * kotlin.math.sqrt(concrete.cylinderStrengthMpa) * b * d
        }
        spacing = min(spacing, params.maxStirrupSpacingMm(d, vuOverHalfVc, vsAboveLimit)).coerceAtLeast(50.0)

        trace.add(
            title = "Stirrup arrangement",
            formula = "s = Av·1000/As,s ≤ family cap",
            substitution = "Ø${dia.toInt()} (${f(twoLegArea)} mm², 2 legs)",
            result = "Ø${dia.toInt()} @ ${f(spacing)} mm",
            limit = "≤ ${f(params.maxStirrupSpacingMm(d, vuOverHalfVc, vsAboveLimit))} mm",
            status = CheckStatus.PASS,
            codeReference = ref
        )

        return Outcome(
            concreteCapacityKn = vc,
            maxCapacityKn = vcCap,
            stirrupDiaMm = dia,
            spacingMm = spacing,
            asPerMeterMm2 = governing,
            minGoverns = minGoverns,
            isSafe = utilMax <= 1.0,
            utilization = utilMax,
            trace = trace
        )
    }

    data class Outcome(
        val concreteCapacityKn: Double,
        val maxCapacityKn: Double,
        val stirrupDiaMm: Double,
        val spacingMm: Double,
        val asPerMeterMm2: Double,
        val minGoverns: Boolean,
        val isSafe: Boolean,
        val utilization: Double,
        val trace: CalculationTrace
    )

    private fun f(v: Double) = String.format("%.3f", v).trimEnd('0').trimEnd('.')
}
