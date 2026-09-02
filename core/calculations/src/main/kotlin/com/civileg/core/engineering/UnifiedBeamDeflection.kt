package com.civileg.core.engineering

import com.civileg.core.calculations.entities.SupportCondition
import com.civileg.core.math.SafeMath

/**
 * Unified deflection screening (STEP 5 extension, spec §20/§62).
 *
 * Layer 1 — span/depth screening with family table + modification factor.
 * Layer 2 — computed deflection: only when the caller supplies a value from a
 * service-load analysis; otherwise reported **NOT_CHECKED**, which is distinct
 * from PASS (spec rule). The overall status therefore degrades honestly.
 */
class UnifiedBeamDeflection(
    private val params: ConcreteCodeParams,
    private val steel: SteelMaterial
) {

    /**
     * @param spanM span in metres
     * @param totalDepthMm overall section depth h (mm)
     * @param tensionRatioPercent midspan tension steel ratio, PERCENT (e.g. 1.26)
     * @param computedDeflectionMm optional result of a real serviceability analysis
     */
    fun screen(
        spanM: Double,
        totalDepthMm: Double,
        tensionRatioPercent: Double,
        support: SupportCondition,
        computedDeflectionMm: Double? = null
    ): Outcome {
        SafeMath.requirePositive(spanM, "span")
        SafeMath.requirePositive(totalDepthMm, "h")
        val trace = CalculationTrace()
        val ref = params.reference

        val basic = params.basicSpanDepthRatio(support)
        val mf = params.spanDepthModificationFactor(steel.yieldMpa, tensionRatioPercent)
        val allowableRatio = basic * mf
        val actualRatio = spanM * 1000.0 / totalDepthMm
        val util = SafeMath.div(actualRatio, allowableRatio)

        trace.add(
            title = "Span/depth screening",
            formula = "allowable = basic(${basic}) × MF(${f(mf)}) ; actual = L/h",
            substitution = "L/h = ${f(spanM * 1000)}/${f(totalDepthMm)} = ${f(actualRatio)}",
            result = f(actualRatio),
            limit = "≤ ${f(allowableRatio)}",
            status = if (util <= 1.0) CheckStatus.PASS else CheckStatus.FAIL,
            utilization = util,
            codeReference = ref
        )

        // Computed-deflection layer: NOT_CHECKED when no analysis was supplied.
        val limitMm = params.deflectionLimitMm(spanM)
        val computedStatus: CheckStatus
        if (computedDeflectionMm == null) {
            computedStatus = CheckStatus.NOT_CHECKED
            trace.add(
                title = "Computed deflection",
                formula = "δ ≤ L/250 (= ${f(limitMm)} mm)",
                substitution = "no service-load analysis supplied",
                result = "—",
                limit = f(limitMm),
                status = CheckStatus.NOT_CHECKED,
                utilization = null,
                codeReference = ref
            )
        } else {
            computedStatus =
                if (computedDeflectionMm <= limitMm) CheckStatus.PASS else CheckStatus.FAIL
            trace.add(
                title = "Computed deflection",
                formula = "δ(computed) ≤ L/250",
                substitution = "δ = ${f(computedDeflectionMm)} mm",
                result = f(computedDeflectionMm),
                limit = f(limitMm),
                status = computedStatus,
                utilization = SafeMath.div(computedDeflectionMm, limitMm),
                codeReference = ref
            )
        }

        return Outcome(
            actualRatio = actualRatio,
            allowableRatio = allowableRatio,
            utilization = util,
            isSafe = util <= 1.0 && computedStatus != CheckStatus.FAIL,
            overall = trace.overall,
            trace = trace
        )
    }

    data class Outcome(
        val actualRatio: Double,
        val allowableRatio: Double,
        val utilization: Double,
        val isSafe: Boolean,
        val overall: CheckStatus,
        val trace: CalculationTrace
    )

    private fun f(v: Double) = String.format("%.3f", v).trimEnd('0').trimEnd('.')
}
