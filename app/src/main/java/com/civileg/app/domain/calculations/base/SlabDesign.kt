package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.entities.LoadCombination

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
    ): SlabDesignResult

    /**
     * تصميم البلاطة ذات الاتجاهين (باستخدام معاملات العزم)
     */
    fun designTwoWaySlab(
        fcu: Double,
        fy: Double,
        slabThickness: Double,
        shortSpan: Double,          // mm
        longSpan: Double,           // mm
        supportConditions: SlabSupportConditions,
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

// نتيجة تصميم البلاطة
data class SlabDesignResult(
    val requiredReinforcement: Double,    // mm²/m
    val providedReinforcement: Double,    // mm²/m
    val barDiameter: Double,              // mm
    val barSpacing: Double,               // mm
    val minThickness: Double,             // mm
    val shearCapacity: Double,            // kN/m
    val isSafe: Boolean,
    val utilizationRatio: Double,
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList()
)

// نتيجة البلاطة ذات الاتجاهين
data class TwoWaySlabResult(
    val shortDirection: SlabDesignResult,
    val longDirection: SlabDesignResult,
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

data class SlabSupportConditions(
    val edgeA: EdgeCondition,
    val edgeB: EdgeCondition,
    val edgeC: EdgeCondition,
    val edgeD: EdgeCondition
)

enum class EdgeCondition { FIXED, SIMPLY_SUPPORTED, FREE, CONTINUOUS }
