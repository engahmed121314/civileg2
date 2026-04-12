package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.TankDesign
import com.civileg.app.domain.calculations.base.TankResult
import com.civileg.app.domain.calculations.base.TankType
import com.civileg.app.domain.entities.ReinforcementResult
import kotlin.math.*

class ECPTank : TankDesign {
    override fun calculateTank(
        length: Double,
        width: Double,
        height: Double,
        waterDepth: Double,
        fcu: Double,
        fy: Double,
        type: TankType
    ): TankResult {
        // Concrete Volume
        val wallThickness = 250.0 // mm
        val baseThickness = 400.0 // mm
        
        val capacity = (length / 1000.0) * (width / 1000.0) * (waterDepth / 1000.0)
        
        // Simplified ECP Tank calc for walls
        val gammaW = 9.81 // kN/m3
        val hydrostaticPressure = gammaW * (waterDepth / 1000.0)
        
        // Base reinforcement placeholder
        val wallReinforcement = ReinforcementResult(
            astRequired = 800.0,
            astProvided = 942.0,
            barDiameter = 12.0,
            numberOfBars = 8, // per meter
            tiesDiameter = 0.0,
            tiesSpacing = 150.0,
            isSafe = true,
            utilizationRatio = 0.6
        )
        
        return TankResult(
            wallThickness = wallThickness,
            baseThickness = baseThickness,
            wallReinforcement = wallReinforcement,
            baseReinforcement = wallReinforcement,
            capacityM3 = capacity,
            concreteVolume = 15.0,
            steelWeight = 1200.0,
            cost = 25000.0,
            isSafe = true,
            pressure = hydrostaticPressure
        )
    }
}
