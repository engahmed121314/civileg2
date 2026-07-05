package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ReinforcementResult

interface ColumnDesign {
    
    /**
     * حساب قدرة تحمل العمود للحمل المحوري
     * @return القدرة بالكيلو نيوتن (kN)
     */
    fun calculateAxialCapacity(
        fcu: Double,                    // مقاومة الخرسانة المميزة (MPa)
        fy: Double,                     // إجهاد خضوع الحديد (MPa)
        width: Double,                  // عرض المقطع (mm)
        depth: Double,                  // عمق المقطع (mm)
        reinforcementArea: Double,      // مساحة التسليح الكلية (mm²)
        loadCombination: LoadCombination
    ): Double

    /**
     * حساب التسليح المطلوب للعمود تحت تأثير حمل محوري وعزوم
     */
    fun calculateReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        axialLoad: Double,              // kN
        momentX: Double,                // kN.m
        momentY: Double,                // kN.m
        loadCombination: LoadCombination
    ): ReinforcementResult

    /**
     * نسبة التسليح الدنيا حسب الكود
     */
    fun getMinReinforcementRatio(): Double

    /**
     * نسبة التسليح القصوى حسب الكود
     */
    fun getMaxReinforcementRatio(): Double

    /**
     * أقل تباعد مسموح للكانات (مم)
     */
    fun getMinSpacing(): Double

    /**
     * أكبر تباعد مسموح للكانات (مم)
     */
    fun getMaxSpacing(): Double

    /**
     * أقل غطاء خرساني (مم)
     */
    fun getMinCover(): Double = 40.0
}
