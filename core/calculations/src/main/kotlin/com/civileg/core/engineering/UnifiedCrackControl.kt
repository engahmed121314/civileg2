package com.civileg.core.engineering

import com.civileg.core.math.SafeMath

/**
 * Unified crack-control check (STEP 5 extension, spec §20/§62).
 *
 * Deemed-to-satisfy approach: compares actual tension-bar centre-to-centre
 * spacing against the family limit from [ConcreteCodeParams.maxTensionBarSpacingMm].
 *
 * When the family returns null (no rule authored) the check is **NOT_CHECKED**
 * — never silently PASS (spec §62).
 */
class UnifiedCrackControl(
    private val params: ConcreteCodeParams
) {

    /**
     * @param tensionBarSpacingMm actual centre-to-centre spacing of the outermost tension bars (mm)
     * @param clearCoverMm clear cover to the tension bars (mm)
     * @param fyMpa yield strength of the tension bars (MPa)
     */
    fun check(
        tensionBarSpacingMm: Double,
        clearCoverMm: Double,
        fyMpa: Double
    ): Outcome {
        SafeMath.requirePositive(tensionBarSpacingMm, "bar spacing")
        SafeMath.requirePositive(clearCoverMm, "clear cover")
        SafeMath.requirePositive(fyMpa, "fy")
        val trace = CalculationTrace()
        val ref = params.reference

        val limitMm = params.maxTensionBarSpacingMm(clearCoverMm, fyMpa)

        if (limitMm == null) {
            // No rule authored → NOT_CHECKED, NOT PASS.
            trace.add(
                title = "Crack control — bar spacing",
                formula = "s ≤ s_max (family rule)",
                substitution = "no deemed-to-satisfy rule authored for ${params.family}",
                result = "—",
                limit = "NOT_CHECKED",
                status = CheckStatus.NOT_CHECKED,
                utilization = null,
                codeReference = ref
            )
            return Outcome(
                maxAllowedMm = null,
                actualMm = tensionBarSpacingMm,
                isCompliant = false,
                overall = CheckStatus.NOT_CHECKED,
                trace = trace
            )
        }

        val util = SafeMath.div(tensionBarSpacingMm, limitMm)
        val status = if (tensionBarSpacingMm <= limitMm + 1e-6) CheckStatus.PASS else CheckStatus.FAIL

        trace.add(
            title = "Crack control — bar spacing",
            formula = "s ≤ s_max(clearCover, fy)",
            substitution = "s = ${f(tensionBarSpacingMm)} mm ; s_max = ${f(limitMm)} mm",
            result = f(tensionBarSpacingMm),
            limit = "≤ ${f(limitMm)} mm",
            status = status,
            utilization = util,
            codeReference = ref
        )

        return Outcome(
            maxAllowedMm = limitMm,
            actualMm = tensionBarSpacingMm,
            isCompliant = status == CheckStatus.PASS,
            overall = status,
            trace = trace
        )
    }

    data class Outcome(
        val maxAllowedMm: Double?,
        val actualMm: Double,
        val isCompliant: Boolean,
        val overall: CheckStatus,
        val trace: CalculationTrace
    )

    private fun f(v: Double) = String.format("%.3f", v).trimEnd('0').trimEnd('.')
}
