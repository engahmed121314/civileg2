package com.civileg.app.domain.calculations

import com.civileg.app.domain.RebarResult
import com.civileg.app.domain.entities.CalculationStep
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.StepStatus

/**
 * Shared flat-slab trace so ECP, ACI and SBC publish the same auditable step list
 * (values still differ per code — only the presentation is shared).
 */
fun flatSlabCalculationSteps(
    code: DesignCode,
    wu: Double,
    moX: Double,
    moY: Double,
    columnStripMomentNeg: Double,
    columnStripMomentPos: Double,
    columnStripTopRebar: RebarResult,
    columnStripBotRebar: RebarResult,
    punchingVu: Double,
    punchingVc: Double,
    punchingOk: Boolean,
    deflection: Double,
    allowableDeflection: Double,
    deflectionOk: Boolean
): List<CalculationStep> {
    val ref = when (code) {
        DesignCode.ECP -> "ECP 203-2020"
        DesignCode.ACI -> "ACI 318-19"
        DesignCode.SBC -> "SBC 304-2018"
    }
    return listOf(
        CalculationStep(
            stepNumber = 1, title = "Factored Load",
            formula = "wu = γD·DL + γL·LL",
            formulaWithValues = "wu = ${"%.2f".format(wu)} kN/m²",
            result = "${"%.2f".format(wu)} kN/m²", unit = "kN/m²",
            codeReference = ref
        ),
        CalculationStep(
            stepNumber = 2, title = "Static Moment",
            formula = "Mo = wu·l2·ln²/8",
            formulaWithValues = "Mo,x = ${"%.1f".format(moX)} kN·m, Mo,y = ${"%.1f".format(moY)} kN·m",
            result = "Mo = ${"%.1f".format(moX)} kN·m", unit = "kN·m",
            codeReference = ref
        ),
        CalculationStep(
            stepNumber = 3, title = "Column Strip Moment",
            formula = "M = α·Mo (DDM distribution)",
            formulaWithValues = "M− = ${"%.1f".format(columnStripMomentNeg)} kN·m, M+ = ${"%.1f".format(columnStripMomentPos)} kN·m",
            result = "M− = ${"%.1f".format(columnStripMomentNeg)} kN·m", unit = "kN·m",
            codeReference = ref
        ),
        CalculationStep(
            stepNumber = 4, title = "Column Strip Steel",
            formula = "As = M / (0.87fy·j·d)",
            formulaWithValues = "As,req,top = ${"%.0f".format(columnStripTopRebar.requiredArea)} mm², As,prov = ${"%.0f".format(columnStripTopRebar.providedArea)} mm²",
            result = "${columnStripTopRebar.bars}Ø${columnStripTopRebar.diameter} @ ${columnStripTopRebar.spacing} mm", unit = "mm²",
            codeReference = ref,
            isPass = columnStripTopRebar.ratio >= 0.999,
            status = if (columnStripTopRebar.ratio >= 0.999) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
        ),
        CalculationStep(
            stepNumber = 5, title = "Punching Shear",
            formula = "Vu ≤ Vc (perimeter at d/2)",
            formulaWithValues = "Vu = ${"%.1f".format(punchingVu)} kN vs Vc = ${"%.1f".format(punchingVc)} kN",
            result = if (punchingOk) "OK" else "Shear reinforcement / drop required", unit = "kN",
            codeReference = ref, isPass = punchingOk,
            status = if (punchingOk) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
        ),
        CalculationStep(
            stepNumber = 6, title = "Deflection",
            formula = "Δ ≤ L/250",
            formulaWithValues = "Δ = ${"%.2f".format(deflection)} mm vs ${"%.2f".format(allowableDeflection)} mm",
            result = if (deflectionOk) "OK" else "Exceeds limit", unit = "mm",
            codeReference = ref, isPass = deflectionOk,
            status = if (deflectionOk) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
        )
    )
}
