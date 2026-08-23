package com.civileg.core.calculations.base

import com.civileg.core.calculations.entities.*

/**
 * واجهة موحدة لتصميم البلاطات حسب أي كود إنشائي
 * تغطي: البلاطات ذات الاتجاه الواحد والاتجاهين، والتحقق من السمك
 */
interface SlabDesign {
    
    /**
     * تصميم البلاطة ذات الاتجاه الواحد
     */
    fun designOneWaySlab(
        fcu: Double,
        fy: Double,
        slabThickness: Double,      // mm
        clearSpan: Double,          // mm
        designMoment: Double,       // kN.m/m
        designShear: Double,        // kN/m
        loadCombination: LoadCombination
    ): ReinforcementResult // Simplified for now to generic ReinforcementResult

    /**
     * تصميم البلاطة ذات الاتجاهين (باستخدام معاملات العزم)
     */
    fun designTwoWaySlab(
        fcu: Double,
        fy: Double,
        slabThickness: Double,
        shortSpan: Double,          // mm
        longSpan: Double,           // mm
        totalLoad: Double,          // kN/m²
        loadCombination: LoadCombination
    ): TwoWaySlabResult

    /**
     * التحقق من سمك البلاطة للانحراف والقص
     */
    fun checkSlabThickness(
        span: Double,               // mm
        supportCondition: SupportCondition,
        fy: Double,
        isTwoWay: Boolean
    ): ThicknessCheckResult

    // حدود الكود
    fun getMinSlabThickness(span: Double, supportCondition: SupportCondition): Double
    fun getMinReinforcementRatio(): Double
    fun getMaxBarSpacing(): Double
    fun getMinCover(): Double
}

// نتيجة البلاطة ذات الاتجاهين
data class TwoWaySlabResult(
    val shortDirection: ReinforcementResult,
    val longDirection: ReinforcementResult,
    val momentCoefficients: MomentCoefficients,
    val isSafe: Boolean
)

data class MomentCoefficients(
    val negativeShort: Double,   // عزم سالب في الاتجاه القصير
    val positiveShort: Double,   // عزم موجب في الاتجاه القصير
    val negativeLong: Double,
    val positiveLong: Double
)

data class ThicknessCheckResult(
    val requiredThickness: Double,
    val providedThickness: Double,
    val isSafe: Boolean,
    val deflectionRatio: Double,
    val recommendation: String
)
