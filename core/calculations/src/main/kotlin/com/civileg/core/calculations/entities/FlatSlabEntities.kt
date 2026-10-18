package com.civileg.core.calculations.entities

/**
 * كيانات خاصة بتقسيم البلاطات المسطحة إلى شرائط
 */
data class FlatSlabStripInfo(
    val columnStripWidth: Double,   // mm
    val middleStripWidth: Double,   // mm
    val shortDirection: StripReinforcement,
    val longDirection: StripReinforcement
)

data class StripReinforcement(
    val columnStripTop: ReinforcementResult,
    val columnStripBottom: ReinforcementResult,
    val middleStripTop: ReinforcementResult,
    val middleStripBottom: ReinforcementResult
)

data class FlatSlabInputs(
    val shortSpan: Double,  // mm
    val longSpan: Double,   // mm
    val totalLoad: Double,  // kN/m²
    val fcu: Double,
    val fy: Double,
    val thickness: Double,
    val columnWidth: Double,
    val columnDepth: Double,
    val designCode: DesignCode = DesignCode.ECP
)
