package com.civileg.app.domain.calculations.base

import kotlin.math.*

/**
 * Structural Analysis Engine for Water Tanks
 * Supports various boundary conditions and loading cases.
 */
object TankAnalysis {

    data class AnalysisInput(
        val type: TankType,
        val length: Double, // mm
        val width: Double,  // mm
        val height: Double, // mm
        val waterDepth: Double, // mm
        val wallThickness: Double, // mm
        val baseThickness: Double, // mm
        val soilPressure: Double = 0.0, // kN/m² (for underground)
        val isUnderground: Boolean = false
    )

    data class AnalysisOutput(
        val wallMaxMomentVertical: Double, // kN.m/m
        val wallMaxMomentHorizontal: Double, // kN.m/m
        val wallMaxShear: Double, // kN/m
        val baseMaxMoment: Double, // kN.m/m
        val baseMaxShear: Double, // kN/m
        val axialForceWall: Double, // kN/m
        val upliftFS: Double = 0.0
    )

    fun analyze(input: AnalysisInput): AnalysisOutput {
        val H = input.height / 1000.0
        val L = input.length / 1000.0
        val W = input.width / 1000.0
        val hw = input.waterDepth / 1000.0
        val gammaW = 10.0 // kN/m³

        // Hydrostatic pressure at base
        val Ph = gammaW * hw

        return when (input.type) {
            TankType.CIRCULAR, TankType.CIRCULAR_GROUND, TankType.CIRCULAR_ELEVATED -> {
                analyzeCircular(input, H, L/2.0, hw, Ph)
            }
            else -> {
                analyzeRectangular(input, H, L, W, hw, Ph)
            }
        }
    }

    private fun analyzeRectangular(input: AnalysisInput, H: Double, L: Double, W: Double, hw: Double, Ph: Double): AnalysisOutput {
        // Simplified PCA Tables logic for rectangular tanks
        // Max Moment in walls (Approximate Coefficients)
        val ratio = L / H
        val coeffM = if (ratio < 2.0) 0.05 else 0.125
        
        val maxMV = coeffM * Ph * hw.pow(2)
        val maxMH = 0.5 * maxMV // Simplified
        val maxShear = 0.5 * Ph * hw
        
        // Base analysis (Plate on elastic foundation or simple span)
        val maxMB = 0.125 * (Ph + (input.baseThickness/1000.0 * 25.0)) * L.pow(2)
        
        return AnalysisOutput(
            wallMaxMomentVertical = maxMV,
            wallMaxMomentHorizontal = maxMH,
            wallMaxShear = maxShear,
            baseMaxMoment = maxMB,
            baseMaxShear = maxShear * 1.2,
            axialForceWall = 0.0
        )
    }

    private fun analyzeCircular(input: AnalysisInput, H: Double, R: Double, hw: Double, Ph: Double): AnalysisOutput {
        // Tension in circular wall (Hoop Tension)
        // T = p * R
        val maxTension = Ph * R
        
        // Bending at base due to fixity (Simplified)
        val maxMV = 0.02 * Ph * hw.pow(2)
        
        return AnalysisOutput(
            wallMaxMomentVertical = maxMV,
            wallMaxMomentHorizontal = 0.0,
            wallMaxShear = 0.3 * Ph * hw,
            baseMaxMoment = 0.1 * Ph * R.pow(2),
            baseMaxShear = 0.5 * Ph * hw,
            axialForceWall = maxTension
        )
    }
}
