package com.civileg.core.output

import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.ElementType
import com.civileg.core.engine.rc.ecp.ECP203Engine
import kotlin.math.max

/**
 * Adapters to wrap concrete engine results into unified ResultEnvelope containers (§2.2).
 */
object ResultEnvelopeAdapters {

    fun fromBeamFlexure(
        input: ECP203Engine.BeamFlexureInput,
        result: ECP203Engine.BeamFlexureResult
    ): ResultEnvelope<ECP203Engine.BeamFlexureResult> {
        val checks = listOf(
            DesignCheck(
                name = "Flexural Capacity",
                demand = input.muKnM,
                capacity = input.muKnM * (result.asProvidedMm2 / max(1.0, result.asRequiredMm2)),
                unit = "kN.m",
                utilization = result.asRequiredMm2 / max(1.0, result.asProvidedMm2),
                passed = result.isSafe,
                clause = result.codeReference
            )
        )

        val trace = listOf(
            CalcStep(
                symbol = "Ru",
                formula = "Mu / (fcu * b * d^2)",
                substitution = "${input.muKnM} * 10^6 / (${input.fcuMpa} * ${input.widthMm} * ${input.depthMm}^2)",
                value = result.ru,
                unit = "MPa",
                clause = "ECP 203 §4-2-1"
            ),
            CalcStep(
                symbol = "As",
                formula = "mu * b * d",
                substitution = "${result.muRatio} * ${input.widthMm} * ${input.depthMm}",
                value = result.asRequiredMm2,
                unit = "mm²",
                clause = "ECP 203 §4-2-1"
            )
        )

        val inputsMap = mapOf(
            "Width (b)" to "${input.widthMm} mm",
            "Effective Depth (d)" to "${input.depthMm} mm",
            "Concrete Strength (fcu)" to "${input.fcuMpa} MPa",
            "Steel Yield (fy)" to "${input.fyMpa} MPa",
            "Applied Moment (Mu)" to "${input.muKnM} kN.m"
        )

        return ResultEnvelope(
            element = ElementType.BEAM,
            code = DesignCode.ECP,
            inputs = inputsMap,
            result = result,
            checks = checks,
            assumptions = listOf("Normal weight concrete", "Ultimate Limit State (ULS) design"),
            warnings = emptyList(),
            trace = trace
        )
    }
}
