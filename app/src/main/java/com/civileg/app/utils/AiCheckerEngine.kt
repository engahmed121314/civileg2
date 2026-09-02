package com.civileg.app.utils

import kotlin.math.pow
import kotlin.math.sqrt

object AiCheckerEngine {

    fun checkBeam(result: CalculatorEngine.BeamResult): AiChecker.AiReport {
        return AiChecker.AiReport("BEAM", emptyList())
    }

    fun checkColumn(result: CalculatorEngine.ColumnResult): AiChecker.AiReport {
        return AiChecker.AiReport("COLUMN", emptyList())
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
