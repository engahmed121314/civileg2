package com.civileg.app.utils

import kotlin.math.pow
import kotlin.math.sqrt

object AiCheckerEngine {

    fun checkBeam(result: CalculatorEngine.BeamResult): AiChecker.AiReport {
        val findings = mutableListOf<AiChecker.Finding>()

        AiChecker.utilFinding(result.utilizationRatio, "Beam")?.let { findings += it }

        if (result.supportType == CalculatorEngine.SupportType.CANTILEVER &&
            result.reinforcementBottom.diameter > result.reinforcementTop.diameter
        ) {
            findings += AiChecker.warn(
                "CANT_DIA",
                "قضبان السقف أصغر من قضبان القاعدة",
                "Cantilever top bars undersized",
                "Top Ø${result.reinforcementTop.diameter} < bottom Ø${result.reinforcementBottom.diameter}. " +
                    "Cantilever tension is at top — increase top bar diameter."
            )
        }

        return AiChecker.AiReport("BEAM", findings)
    }

    fun checkColumn(result: CalculatorEngine.ColumnResult): AiChecker.AiReport {
        val findings = mutableListOf<AiChecker.Finding>()
        val rho = result.reinforcementRatio

        when {
            rho < 1.0 -> findings += AiChecker.crit(
                "RHO_MIN",
                "نسبة التسليح أقل من الحد الأدنى",
                "Below minimum reinforcement ratio",
                "ρ = ${"%.2f".format(rho)}% — code minimum is 1%."
            )
            rho > 6.0 -> findings += AiChecker.crit(
                "RHO_MAX",
                "نسبة التسليح تتجاوز الحد الأقصى",
                "Above maximum reinforcement ratio",
                "ρ = ${"%.2f".format(rho)}% — code maximum is 6%."
            )
            rho in 1.0..2.0 -> findings += AiChecker.opt(
                "RHO_ECON",
                "النسبة الاقتصادية للتسليح",
                "Economical reinforcement ratio",
                "ρ = ${"%.2f".format(rho)}% — within the economic band (1–2%)."
            )
        }

        return AiChecker.AiReport("COLUMN", findings)
    }

    fun checkSlab(result: CalculatorEngine.SlabResult): AiChecker.AiReport {
        return AiChecker.AiReport("SLAB", emptyList())
    }

    fun checkTank(result: CalculatorEngine.TankResult): AiChecker.AiReport {
        return AiChecker.AiReport("TANK", emptyList())
    }

    fun checkRetainingWall(result: CalculatorEngine.RetainingWallResult): AiChecker.AiReport {
        return AiChecker.AiReport("RETAINING_WALL", emptyList())
    }

    fun checkFooting(result: CalculatorEngine.FootingResult): AiChecker.AiReport {
        return AiChecker.AiReport("FOOTING", emptyList())
    }

    fun checkStair(result: CalculatorEngine.StairResult): AiChecker.AiReport {
        return AiChecker.AiReport("STAIR", emptyList())
    }
}
