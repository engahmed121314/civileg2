package com.civileg.core.calculations.entities

/**
 * نتائج التحقق من الترخيم (Deflection)
 */
data class DeflectionCheckResult(
    val immediateDeflection: Double = 0.0,
    val longTermDeflection: Double = 0.0,
    val calculatedDeflection: Double = 0.0,
    val allowableDeflection: Double = 0.0,
    val ratio: Double = 0.0,
    val isSafe: Boolean = true,
    val message: String = "",
    val recommendation: String = "",
    val warnings: List<String> = emptyList()
)

/**
 * نتائج التحقق من القص (Shear)
 */
data class ShearCheckResult(
    val appliedShear: Double = 0.0,
    val shearCapacity: Double = 0.0,
    val isSafe: Boolean = true,
    val utilizationRatio: Double = 0.0,
    val criticalSection: Double = 0.0,
    val criticalPerimeter: Double = 0.0,
    val warnings: List<String> = emptyList()
)

data class StirrupZone(
    val name: String,
    val startLocation: Double, // mm
    val endLocation: Double,   // mm
    val spacing: Double,       // mm
    val numLegs: Int = 2,
    val diameter: Int = 8,
    val description: String = ""
)

/**
 * نتائج تسليح القص (للكمرات)
 */
data class ShearReinforcementResult(
    val concreteShearCapacity: Double = 0.0,
    val requiredArea: Double = 0.0,
    val providedArea: Double = 0.0,
    val requiredShearReinforcement: Double = 0.0,
    val providedShearReinforcement: Double = 0.0,
    val stirrupDiameter: Double = 0.0,
    val stirrupSpacing: Double = 0.0,
    val numLegs: Int = 2,
    val zones: List<StirrupZone> = emptyList(),
    val isSafe: Boolean = true,
    val utilizationRatio: Double = 0.0,
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList()
)

/**
 * نتائج التحقق من عرض الشروخ (Crack Width)
 */
data class CrackWidthCheckResult(
    val calculatedWidth: Double = 0.0,
    val allowableWidth: Double = 0.0,
    val isSafe: Boolean = true,
    val codeReference: String = ""
)

/**
 * نتائج التحقق من طول التماسك (Development Length)
 */
data class DevelopmentLengthCheckResult(
    val requiredLength: Double = 0.0,
    val availableLength: Double = 0.0,
    val isSafe: Boolean = true,
    val codeReference: String = ""
)

/**
 * نتائج التحقق من الثقب (Punching Shear)
 */
data class PunchingShearCheckResult(
    val appliedShear: Double = 0.0,
    val shearCapacity: Double = 0.0,
    val utilizationRatio: Double = 0.0,
    val isSafe: Boolean = true,
    val criticalPerimeter: Double = 0.0,
    val shearHeadsRequired: Boolean = false,
    val codeReference: String = "",
    val warnings: List<String> = emptyList()
)

/**
 * حسابات ما بعد الشد (Post Tension)
 */
data class PostTensionCalculations(
    val prestressForce: Double = 0.0,
    val losses: PrestressLosses = PrestressLosses(),
    val equivalentLoad: Double = 0.0,
    val camber: Double = 0.0,
    val stressAtTransfer: Double = 0.0,
    val stressAtService: Double = 0.0,
    val isSafe: Boolean = true
)

data class PrestressLosses(
    val elasticShortening: Double = 0.0,
    val creep: Double = 0.0,
    val shrinkage: Double = 0.0,
    val relaxation: Double = 0.0,
    val friction: Double = 0.0,
    val anchorage: Double = 0.0,
    val totalLoss: Double = 0.0,
    val totalLossPercentage: Double = 0.0
)

/**
 * شروط التثبيت (Support Conditions)
 */
enum class SupportCondition { 
    SIMPLY_SUPPORTED, 
    CONTINUOUS, 
    CANTILEVER 
}

/**
 * موقع سيخ التسليح (Bar Location)
 */
enum class BarLocation { 
    TOP, 
    BOTTOM, 
    SIDE 
}

/**
 * نوع طلاء الحديد (Coating Type)
 */
enum class CoatingType { 
    UNCOATED, 
    EPOXY_COATED, 
    GALVANIZED 
}

/**
 * ملخص المشروع (Project Summary)
 */
data class ProjectSummary(
    val totalConcrete: Double = 0.0,
    val totalSteel: Double = 0.0,
    val totalCost: Double = 0.0,
    val designCount: Int = 0,
    val costEfficiencyIndex: Double = 1.0,
    val costBreakdown: Map<String, Double> = emptyMap()
)
