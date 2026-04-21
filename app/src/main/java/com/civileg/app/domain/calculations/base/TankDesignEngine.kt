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
        type: TankType, code: String,
        wallT: Double = 250.0,
        baseT: Double = 400.0,
        waterTableDepth: Double = 0.0 // mm from ground level
    ): TankResult {
        
        // 1. Structural Analysis
        val analysisInput = TankAnalysis.AnalysisInput(
            type = type,
            length = length,
            width = width,
            height = height,
            waterDepth = waterDepth,
            wallThickness = wallT,
            baseThickness = baseT,
            isUnderground = type == TankType.RECTANGULAR_UNDERGROUND || type == TankType.CIRCULAR_UNDERGROUND
        )
        val analysis = TankAnalysis.analyze(analysisInput)

        // 2. Reinforcement Calculation
        val asWallV = calculateSteel(analysis.wallMaxMomentVertical, wallT, fy)
        val asWallH = calculateSteel(analysis.wallMaxMomentHorizontal, wallT, fy)
        val asBase = calculateSteel(analysis.baseMaxMoment, baseT, fy)

        val wallReinforcement = ReinforcementResult(
            astRequired = asWallV,
            astProvided = max(asWallV, 450.0), // Min steel
            barDiameter = 12.0,
            numberOfBars = 0,
            spacing = 150.0,
            tiesDiameter = 0.0,
            tiesSpacing = 150.0,
            isSafe = true,
            utilizationRatio = asWallV / ( (1000.0/150.0) * 113.0)
        )

        val baseReinforcement = ReinforcementResult(
            astRequired = asBase,
            astProvided = max(asBase, 600.0),
            barDiameter = 14.0,
            numberOfBars = 0,
            spacing = 150.0,
            tiesDiameter = 0.0,
            tiesSpacing = 150.0,
            isSafe = true,
            utilizationRatio = asBase / ( (1000.0/150.0) * 154.0)
        )

        // 3. Physical Properties
        val capacity = (length / 1000.0) * (width / 1000.0) * (waterDepth / 1000.0)
        val tankWeight = (((length * width * baseT) + (2 * (length + width) * height * wallT)) / 1e9) * 25.0 // kN
        val waterWeight = capacity * 10.0 // kN
        
        val concVol = ((length * width * baseT) + (2 * (length + width) * height * wallT)) / 1e9
        val steelWeight = concVol * 145.0 // kg/m3

        // 4. Safety Checks
        val checks = mutableListOf<TankSafetyCheck>()
        checks.add(TankSafetyCheck("Wall Vertical Moment", analysis.wallMaxMomentVertical, 120.0, "kN.m/m", analysis.wallMaxMomentVertical < 120.0))
        checks.add(TankSafetyCheck("Wall Shear", analysis.wallMaxShear, 200.0, "kN/m", analysis.wallMaxShear < 200.0))
        
        // Uplift Check for Underground
        var upliftFS = 0.0
        if (analysisInput.isUnderground && waterTableDepth < height) {
            val displacedWaterVol = (length * width * (height - waterTableDepth)) / 1e9
            val upliftForce = displacedWaterVol * 10.0
            upliftFS = tankWeight / upliftForce
            checks.add(TankSafetyCheck("Uplift Safety Factor", upliftFS, 1.2, "", upliftFS >= 1.2, "Stability against buoyancy"))
        }

        // Stability Check for Elevated
        if (type == TankType.RECTANGULAR_ELEVATED || type == TankType.CIRCULAR_ELEVATED) {
            val totalVertical = tankWeight + waterWeight
            val overturningMoment = 50.0 * (height / 2000.0) // Mock wind load
            val stabilityFS = (totalVertical * (min(length, width) / 2000.0)) / overturningMoment
            checks.add(TankSafetyCheck("Overturning Stability", stabilityFS, 1.5, "", stabilityFS >= 1.5))
        }

        val isSafe = checks.all { it.isSafe }

        return TankResult(
            wallThickness = wallT,
            baseThickness = baseT,
            wallReinforcement = wallReinforcement,
            baseReinforcement = baseReinforcement,
            capacityM3 = capacity,
            concreteVolume = concVol,
            steelWeight = steelWeight,
            cost = concVol * 6500.0 + (steelWeight / 1000.0 * 55000.0),
            isSafe = isSafe,
            pressure = 10.0 * (waterDepth / 1000.0),
            maxMomentWall = analysis.wallMaxMomentVertical,
            maxMomentBase = analysis.baseMaxMoment,
            maxShearWall = analysis.wallMaxShear,
            factorOfSafetyUplift = upliftFS,
            structuralSystem = when(type) {
                TankType.RECTANGULAR_GROUND -> "Ground Rectangular - PCA Coefficients"
                TankType.CIRCULAR_GROUND -> "Ground Circular - Hoop Tension"
                TankType.RECTANGULAR_ELEVATED -> "Elevated Rectangular on RC Columns"
                TankType.RECTANGULAR_UNDERGROUND -> "Underground Rectangular - Soil + Water"
                else -> "Custom System"
            },
            recommendations = listOf(
                "Use SBR or Water-stop at all construction joints",
                "Minimum concrete cover: 40mm (Water side), 25mm (Outer side)",
                "Curing with clean water for at least 7 days",
                "Testing for leakage before backfilling (for underground)"
            ),
            safetyChecks = checks
        )
    }


    private fun calculateSteel(mu: Double, t: Double, fy: Double): Double {
        val d = t - 50.0
        return (mu * 1e6) / (0.87 * fy * 0.8 * d) // Simplified
    }
}
