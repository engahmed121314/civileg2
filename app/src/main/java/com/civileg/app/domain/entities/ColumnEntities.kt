package com.civileg.app.domain.entities

import kotlin.math.PI

/**
 * جميع أنواع الأعمدة المدعومة
 */
sealed class ColumnType(val displayName: String, val codeReference: String) {
    
    /**
     * عمود مستطيل
     */
    data class Rectangular(
        val width: Double,      // mm
        val depth: Double       // mm
    ) : ColumnType("Rectangular Column", "ECP 203-4.2 / ACI 318-10.3")
    
    /**
     * عمود دائري
     */
    data class Circular(
        val diameter: Double    // mm
    ) : ColumnType("Circular Column", "ECP 203-4.2 / ACI 318-10.7")
    
    /**
     * عمود L-shaped (زوايا)
     */
    data class LShaped(
        val legWidth: Double,   // mm
        val legDepth: Double,   // mm
        val thickness: Double   // mm
    ) : ColumnType("L-Shaped Column", "ACI 318-10.3 / Special Section")
    
    /**
     * عمود T-shaped
     */
    data class TShaped(
        val flangeWidth: Double,
        val flangeThickness: Double,
        val webWidth: Double,
        val webDepth: Double
    ) : ColumnType("T-Shaped Column", "ACI 318-10.3 / Special Section")
    
    /**
     * عمود مركب (Steel + Concrete)
     */
    data class Composite(
        val steelArea: Double,      // mm²
        val concreteWidth: Double,  // mm
        val concreteDepth: Double   // mm
    ) : ColumnType("Composite Column", "AISC 360-I1 / ECP 205")
    
    /**
     * عمود أنبوبي (للأبراج)
     */
    data class Tubular(
        val outerDiameter: Double,
        val innerDiameter: Double
    ) : ColumnType("Tubular Column", "AISC 360 / Special Section")
    
    fun getGrossArea(): Double = when (this) {
        is Rectangular -> width * depth
        is Circular -> PI * diameter * diameter / 4
        is LShaped -> (legWidth * thickness) + (legDepth - thickness) * thickness
        is TShaped -> flangeWidth * flangeThickness + webWidth * webDepth
        is Composite -> concreteWidth * concreteDepth + steelArea
        is Tubular -> PI * (outerDiameter * outerDiameter - innerDiameter * innerDiameter) / 4
    }
}

/**
 * ظروف تثبيت الأعمدة
 */
data class ColumnEndConditions(
    val topCondition: EndCondition,
    val bottomCondition: EndCondition
)

enum class EndCondition(val displayName: String, val effectiveLengthFactor: Double) {
    FIXED("Fixed", 0.65),
    PINNED("Pinned/Hinged", 1.0),
    ROLLER("Roller", 1.0),
    FREE("Free", 2.0),
    PARTIAL_FIXED("Partially Fixed", 0.8)
}

/**
 * نتيجة تصميم متقدمة للأعمدة
 */
data class AdvancedColumnResult(
    val columnType: ColumnType,
    val axialCapacity: Double,
    val momentCapacityX: Double,
    val momentCapacityY: Double,
    val slendernessRatio: Double,
    val isSlender: Boolean,
    val effectiveLength: Double,
    val reinforcementResult: ReinforcementResult,
    val inventoryAnalysis: InventoryAnalysisResult?,
    val biaxialCheck: BiaxialCheckResult?,
    val warnings: List<String>,
    val codeNotes: List<String>
)

data class BiaxialCheckResult(
    val mxRatio: Double,
    val myRatio: Double,
    val interactionFactor: Double,
    val isSafe: Boolean,
    val formula: String
)
