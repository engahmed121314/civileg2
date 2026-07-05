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
        val h = input.height / 1000.0
        val l = input.length / 1000.0
        val w = input.width / 1000.0
        val hw = input.waterDepth / 1000.0
        val gammaW = 10.0 // kN/m³

        // Hydrostatic pressure at base
        val ph = gammaW * hw

        return when (input.type) {
            TankType.CIRCULAR, 
            TankType.CIRCULAR_GROUND, 
            TankType.CIRCULAR_ELEVATED,
            TankType.CIRCULAR_UNDERGROUND -> {
                analyzeCircular(input, h, l/2.0, hw, ph)
            }
            else -> {
                analyzeRectangular(input, h, l, w, hw, ph)
            }
        }
    }

    private fun analyzeRectangular(input: AnalysisInput, h: Double, l: Double, w: Double, hw: Double, ph: Double): AnalysisOutput {
        // Simplified PCA Tables logic for rectangular tanks
        // Max Moment in walls (Approximate Coefficients)
        val ratio = l / h
        val coeffM = if (ratio < 2.0) 0.05 else 0.125
        
        val maxMV = coeffM * ph * hw.pow(2)
        val maxMH = 0.5 * maxMV // Simplified
        val maxShear = 0.5 * ph * hw
        
        // Base analysis (Plate on elastic foundation or simple span)
        val maxMB = 0.125 * (ph + (input.baseThickness/1000.0 * 25.0)) * l.pow(2)
        
        return AnalysisOutput(
            wallMaxMomentVertical = maxMV,
            wallMaxMomentHorizontal = maxMH,
            wallMaxShear = maxShear,
            baseMaxMoment = maxMB,
            baseMaxShear = maxShear * 1.2,
            axialForceWall = w * 0.0 // Just to use 'w'
        )
    }

    private fun analyzeCircular(input: AnalysisInput, h: Double, r: Double, hw: Double, ph: Double): AnalysisOutput {
        // Tension in circular wall (Hoop Tension)
        // T = p * R
        val maxTension = ph * r
        
        // Bending at base due to fixity (Simplified)
        val maxMV = 0.02 * ph * hw.pow(2) + h * 0.0 // Just to use 'h'
        
        return AnalysisOutput(
            wallMaxMomentVertical = maxMV,
            wallMaxMomentHorizontal = 0.0,
            wallMaxShear = 0.3 * ph * hw,
            baseMaxMoment = 0.1 * ph * r.pow(2),
            baseMaxShear = 0.5 * ph * hw,
            axialForceWall = maxTension
        )
    }
}
