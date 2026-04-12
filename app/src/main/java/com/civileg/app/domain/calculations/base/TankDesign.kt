package com.civileg.app.domain.calculations.base

import android.os.Parcelable
import com.civileg.app.domain.entities.ReinforcementResult
import kotlinx.parcelize.Parcelize

interface TankDesign {
    fun calculateTank(
        length: Double,     // mm
        width: Double,      // mm
        height: Double,     // mm
        waterDepth: Double, // mm
        fcu: Double,
        fy: Double,
        type: TankType = TankType.RECTANGULAR
    ): TankResult
}

enum class TankType {
    RECTANGULAR, CIRCULAR, ELEVATED, UNDERGROUND
}

@Parcelize
data class TankResult(
    val wallThickness: Double,
    val baseThickness: Double,
    val wallReinforcement: ReinforcementResult,
    val baseReinforcement: ReinforcementResult,
    val capacityM3: Double,
    val concreteVolume: Double,
    val steelWeight: Double,
    val cost: Double,
    val isSafe: Boolean,
    val pressure: Double, // kN/m²
    val warnings: List<String> = emptyList()
) : Parcelable
