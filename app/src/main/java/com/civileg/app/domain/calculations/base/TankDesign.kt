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
    RECTANGULAR_GROUND, CIRCULAR_GROUND,
    RECTANGULAR_ELEVATED, CIRCULAR_ELEVATED,
    RECTANGULAR_UNDERGROUND, CIRCULAR_UNDERGROUND,
    RECTANGULAR, CIRCULAR
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
    val maxMomentWall: Double = 0.0,
    val maxMomentBase: Double = 0.0,
    val maxShearWall: Double = 0.0,
    val factorOfSafetyUplift: Double = 0.0,
    val structuralSystem: String = "",
    val recommendations: List<String> = emptyList(),
    val safetyChecks: List<TankSafetyCheck> = emptyList(),
    val warnings: List<String> = emptyList()
) : Parcelable

@Parcelize
data class TankSafetyCheck(
    val name: String,
    val value: Double,
    val limit: Double,
    val unit: String,
    val isSafe: Boolean,
    val description: String = ""
) : Parcelable
