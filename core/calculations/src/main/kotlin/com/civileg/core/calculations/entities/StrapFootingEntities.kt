package com.civileg.core.calculations.entities

/**
 * مدخلات تصميم القواعد الشريطية (Strap Footing)
 */
data class StrapFootingInputs(
    val column1Load: Double,        // kN (External/Edge column)
    val column2Load: Double,        // kN (Internal column)
    val distanceBetweenColumns: Double, // mm (Center to center)
    val column1Width: Double,       // mm
    val column1Depth: Double,       // mm
    val column2Width: Double,       // mm
    val column2Depth: Double,       // mm
    val soilBearingCapacity: Double, // kPa
    val fcu: Double,
    val fy: Double,
    val footing1Width: Double? = null, // Optional fixed width
    val strapBeamWidth: Double = 400.0,
    val designCode: DesignCode = DesignCode.ECP
)

/**
 * نتائج تصميم القواعد الشريطية
 */
data class StrapFootingResult(
    val footing1: FootingDimension,
    val footing2: FootingDimension,
    val strapBeam: StrapBeamResult,
    val reactions: Pair<Double, Double>, // R1, R2
    val isSafe: Boolean,
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList()
)

data class FootingDimension(
    val width: Double,
    val length: Double,
    val thickness: Double,
    val reinforcement: ReinforcementResult
)

data class StrapBeamResult(
    val width: Double,
    val depth: Double,
    val topReinforcement: ReinforcementResult,
    val bottomReinforcement: ReinforcementResult,
    val shearReinforcement: ShearReinforcementResult,
    val maxMoment: Double, // kN.m
    val maxShear: Double   // kN
)
