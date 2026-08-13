package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.entities.DesignCode
import kotlin.math.PI
import kotlin.math.pow

data class RetainingWallInput(
    val wallHeight: Double,
    val stemBaseThickness: Double,
    val stemTopThickness: Double,
    val baseWidth: Double,
    val baseThickness: Double,
    val toeLength: Double,
    val heelLength: Double,
    val soilDensity: Double,
    val frictionAngle: Double,
    val surchargeLoad: Double,
    val waterTableDepth: Double,
    val fcu: Double,
    val fy: Double,
    val baseFrictionCoeff: Double = 0.5,
    val soilBearingCapacity: Double = 200.0
)

data class RetainingWallResult(
    val isSafe: Boolean,
    val designCode: DesignCode,
    val designCodeName: String = "",
    val stemThickness: Double = 0.0, // [NEW]
    val baseWidth: Double = 0.0,
    val overturningFS: Double,
    val slidingFS: Double,
    val bearingFS: Double,
    val maxBearingPressure: Double,
    val minBearingPressure: Double,
    val stemMoment: Double,
    val stemShear: Double,
    val stemMainRebar: String,
    val stemMainRebarArea: Double,
    val stemDistributionRebar: String,
    val toeMoment: Double,
    val toeShear: Double,
    val toeRebar: String,
    val heelMoment: Double,
    val heelShear: Double,
    val heelRebar: String,
    val safetyChecks: List<WallSafetyCheck>,
    val formulas: List<String> = emptyList(), // [NEW] Actual formulas used
    val codeNotes: List<String>
)

data class WallSafetyCheck(
    val name: String,
    val isSafe: Boolean,
    val value: Double,
    val limit: Double,
    val unit: String = "",       // [NEW] e.g. "kN.m"
    val formula: String = "",    // [NEW] e.g. "M = qL²/2"
    val description: String
)

interface RetainingWallDesign {
    fun designRetainingWall(input: RetainingWallInput): RetainingWallResult

    companion object {
        fun selectBars(requiredArea: Double, maxBars: Int = 12): Pair<Int, Int> {
            val diameters = intArrayOf(10, 12, 13, 16, 18, 20, 22, 25, 28, 32)
            for (n in 1..maxBars) {
                for (d in diameters) {
                    val area = n * Math.PI * (d / 2.0).pow(2)
                    if (area >= requiredArea) return Pair(n, d)
                }
            }
            return Pair(maxBars, diameters.last())
        }

        fun formatRebar(count: Int, dia: Int): String = "${count}\u03A6${dia}"
    }
}