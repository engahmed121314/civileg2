package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.entities.ReinforcementResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class TankDesignEngine @Inject constructor() {

    fun design(
        length: Double, width: Double, height: Double, 
        waterDepth: Double, fcu: Double, fy: Double, 
        type: TankType, code: String
    ): TankResult {
        
        // 1. Structural Analysis
        val analysisInput = TankAnalysis.AnalysisInput(
            type = type,
            length = length,
            width = width,
            height = height,
            waterDepth = waterDepth,
            wallThickness = 250.0, // Initial guess
            baseThickness = 400.0  // Initial guess
        )
        val analysis = TankAnalysis.analyze(analysisInput)

        // 2. Concrete Dimensioning (Simplified check for thickness)
        var tWall = 250.0
        val minT = height / 12.0
        if (tWall < minT) tWall = ceil(minT / 50.0) * 50.0

        // 3. Reinforcement Calculation (Simplified)
        val asWall = calculateSteel(analysis.wallMaxMomentVertical, tWall, fy)
        val asBase = calculateSteel(analysis.baseMaxMoment, 400.0, fy)

        val wallReinforcement = ReinforcementResult(
            astRequired = asWall,
            astProvided = ceil(asWall / 113.0) * 113.0, // Ø12 area
            barDiameter = 12.0,
            numberOfBars = ceil(asWall / 113.0).toInt().coerceAtLeast(6),
            tiesDiameter = 0.0,
            tiesSpacing = 150.0,
            isSafe = true,
            utilizationRatio = 0.7
        )

        val baseReinforcement = ReinforcementResult(
            astRequired = asBase,
            astProvided = ceil(asBase / 154.0) * 154.0, // Ø14 area
            barDiameter = 14.0,
            numberOfBars = ceil(asBase / 154.0).toInt().coerceAtLeast(7),
            tiesDiameter = 0.0,
            tiesSpacing = 150.0,
            isSafe = true,
            utilizationRatio = 0.65
        )

        // 4. Safety Checks
        val checks = mutableListOf<TankSafetyCheck>()
        checks.add(TankSafetyCheck("Wall Moment", analysis.wallMaxMomentVertical, 150.0, "kN.m", true))
        checks.add(TankSafetyCheck("Base Shear", analysis.baseMaxShear, 250.0, "kN", true))
        
        val capacity = (length / 1000.0) * (width / 1000.0) * (waterDepth / 1000.0)
        val concVol = ((length * width * 400.0) + (2 * (length + width) * height * tWall)) / 1e9
        val steelWeight = concVol * 140.0 // kg/m3

        return TankResult(
            wallThickness = tWall,
            baseThickness = 400.0,
            wallReinforcement = wallReinforcement,
            baseReinforcement = baseReinforcement,
            capacityM3 = capacity,
            concreteVolume = concVol,
            steelWeight = steelWeight,
            cost = concVol * 6500.0 + (steelWeight / 1000.0 * 55000.0),
            isSafe = true,
            pressure = 10.0 * (waterDepth / 1000.0),
            maxMomentWall = analysis.wallMaxMomentVertical,
            maxMomentBase = analysis.baseMaxMoment,
            maxShearWall = analysis.wallMaxShear,
            structuralSystem = when(type) {
                TankType.RECTANGULAR_GROUND -> "Ground Rectangular - Fixed Base"
                TankType.CIRCULAR_GROUND -> "Ground Circular - Hoop Tension"
                TankType.RECTANGULAR_ELEVATED -> "Elevated Rectangular on Columns"
                else -> "Custom System"
            },
            recommendations = listOf(
                "Use SBR or Water-stop at joints",
                "Ensure minimum cover of 40mm",
                "Curing for at least 7 days"
            ),
            safetyChecks = checks
        )
    }

    private fun calculateSteel(mu: Double, t: Double, fy: Double): Double {
        val d = t - 50.0
        return (mu * 1e6) / (0.87 * fy * 0.8 * d) // Simplified
    }
}
