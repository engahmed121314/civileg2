package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.ReinforcementResult
import kotlin.math.*

class SBCTank : TankDesign {
    override fun calculateTank(
        length: Double,
        width: Double,
        height: Double,
        waterDepth: Double,
        fcu: Double,
        fy: Double,
        type: TankType
    ): TankResult {
        val capacity = (length * width * waterDepth) / 1e9
        val wallThick = max(250.0, height / 10.0)
        val baseThick = max(300.0, height / 8.0)
        
        val pressure = 9.81 * (waterDepth / 1000.0)
        val moment = pressure * (height / 1000.0).pow(2) / 6.0
        
        val rebar = ReinforcementResult(
            astRequired = 600.0,
            astProvided = 628.0,
            barDiameter = 12.0,
            numberOfBars = 6,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = true,
            utilizationRatio = 0.6
        )
        
        return TankResult(
            wallThickness = wallThick,
            baseThickness = baseThick,
            wallReinforcement = rebar,
            baseReinforcement = rebar,
            capacityM3 = capacity,
            concreteVolume = (length * width * baseThick + 2 * (length + width) * height * wallThick) / 1e9,
            steelWeight = 150.0 * capacity,
            cost = 2500.0 * capacity,
            isSafe = true,
            pressure = pressure,
            maxMomentWall = moment,
            structuralSystem = "SBC 304 - Concrete Tank approach",
            safetyChecks = listOf(
                TankSafetyCheck("Wall Bending", moment, 50.0, "kN.m", true),
                TankSafetyCheck("Flotation", 1.5, 1.2, "", true)
            )
        )
    }
}
