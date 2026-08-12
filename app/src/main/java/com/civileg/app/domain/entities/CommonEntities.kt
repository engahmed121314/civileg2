package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * نتائج التحقق من الترخيم (Deflection)
 */
@Parcelize
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
) : Parcelable

/**
 * نتائج التحقق من القص (Shear)
 */
@Parcelize
data class ShearCheckResult(
    val appliedShear: Double = 0.0,
    val shearCapacity: Double = 0.0,
    val isSafe: Boolean = true,
    val utilizationRatio: Double = 0.0,
    val criticalSection: Double = 0.0,
    val criticalPerimeter: Double = 0.0,
    val warnings: List<String> = emptyList()
) : Parcelable

/**
 * نتائج تسليح القص (للكمرات)
 */
@Parcelize
data class ShearReinforcementResult(
    val concreteShearCapacity: Double = 0.0,
    val requiredArea: Double = 0.0,
    val providedArea: Double = 0.0,
    val requiredShearReinforcement: Double = 0.0,
    val providedShearReinforcement: Double = 0.0,
    val stirrupDiameter: Double = 0.0,
    val stirrupSpacing: Double = 0.0,
    val isSafe: Boolean = true,
    val utilizationRatio: Double = 0.0,
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList()
) : Parcelable

/**
 * نتائج التحقق من عرض الشروخ (Crack Width)
 */
@Parcelize
data class CrackWidthCheckResult(
    val calculatedWidth: Double = 0.0,
    val allowableWidth: Double = 0.0,
    val isSafe: Boolean = true,
    val codeReference: String = ""
) : Parcelable

/**
 * نتائج التحقق من طول التماسك (Development Length)
 */
@Parcelize
data class DevelopmentLengthCheckResult(
    val requiredLength: Double = 0.0,
    val availableLength: Double = 0.0,
    val isSafe: Boolean = true,
    val codeReference: String = ""
) : Parcelable

/**
 * نتائج التحقق من الثقب (Punching Shear)
 */
@Parcelize
data class PunchingShearCheckResult(
    val appliedShear: Double = 0.0,
    val shearCapacity: Double = 0.0,
    val utilizationRatio: Double = 0.0,
    val isSafe: Boolean = true,
    val criticalPerimeter: Double = 0.0,
    val shearHeadsRequired: Boolean = false,
    val codeReference: String = "",
    val warnings: List<String> = emptyList()
) : Parcelable

/**
 * حسابات ما بعد الشد (Post Tension)
 */
@Parcelize
data class PostTensionCalculations(
    val prestressForce: Double = 0.0,
    val losses: PrestressLosses = PrestressLosses(),
    val equivalentLoad: Double = 0.0,
    val camber: Double = 0.0,
    val stressAtTransfer: Double = 0.0,
    val stressAtService: Double = 0.0,
    val isSafe: Boolean = true
) : Parcelable

@Parcelize
data class PrestressLosses(
    val elasticShortening: Double = 0.0,
    val creep: Double = 0.0,
    val shrinkage: Double = 0.0,
    val relaxation: Double = 0.0,
    val friction: Double = 0.0,
    val anchorage: Double = 0.0,
    val totalLoss: Double = 0.0,
    val totalLossPercentage: Double = 0.0
) : Parcelable

/**
 * شروط التثبيت (Support Conditions)
 */
@Parcelize
enum class SupportCondition : Parcelable { 
    SIMPLY_SUPPORTED, 
    CONTINUOUS, 
    CANTILEVER 
}

/**
 * موقع سيخ التسليح (Bar Location)
 */
@Parcelize
enum class BarLocation : Parcelable { 
    TOP, 
    BOTTOM, 
    SIDE 
}

/**
 * نوع طلاء الحديد (Coating Type)
 */
@Parcelize
enum class CoatingType : Parcelable { 
    UNCOATED, 
    EPOXY_COATED, 
    GALVANIZED 
}
