package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.ShearCheckResult

/**
 * واجهة موحدة لتصميم القواعد حسب أي كود إنشائي
 * تغطي: القواعد المنفصلة، التحقق من قص الاختراق، وحساب التسليح
 */
interface FootingDesign {
    
    /**
     * تصميم قاعدة منفصلة تحت عمود
     */
    fun designIsolatedFooting(
        fcu: Double,
        fy: Double,
        columnWidth: Double,      // mm
        columnDepth: Double,      // mm
        axialLoad: Double,        // kN
        momentX: Double,          // kN.m
        momentY: Double,          // kN.m
        soilBearingCapacity: Double, // kPa
        footingDepth: Double,     // mm - عمق التأسيس
        loadCombination: LoadCombination
    ): FootingDesignResult

    /**
     * التحقق من قص الاختراق (Punching Shear)
     */
    fun checkPunchingShear(
        fcu: Double,
        columnWidth: Double,
        columnDepth: Double,
        effectiveDepth: Double,
        punchingShearForce: Double,  // kN
        loadCombination: LoadCombination
    ): ShearCheckResult

    /**
     * حساب تسليح القاعدة
     */
    fun calculateFootingReinforcement(
        fcu: Double,
        fy: Double,
        footingWidth: Double,
        footingLength: Double,
        effectiveDepth: Double,
        designMoment: Double,  // kN.m/m
        direction: FootingDirection
    ): ReinforcementResult

    // حدود الكود
    fun getMinFootingThickness(): Double
    fun getMinCover(): Double
    fun getPunchingShearCapacity(fcu: Double, perimeter: Double, effectiveDepth: Double): Double
}

data class FootingDesignResult(
    val requiredWidth: Double,          // mm
    val requiredLength: Double,         // mm
    val requiredThickness: Double,      // mm
    val soilPressure: Double,           // kPa
    val maxSoilPressure: Double,        // kPa (مع العزم)
    val reinforcement: ReinforcementResult,
    val punchingShearCheck: ShearCheckResult,
    val isSafe: Boolean,
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList()
)

enum class FootingDirection { SHORT, LONG }
