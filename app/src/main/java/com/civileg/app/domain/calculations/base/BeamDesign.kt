package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.entities.*

/**
 * واجهة موحدة لتصميم الكمرات حسب أي كود إنشائي
 * تغطي: الانحناء، القص، الالتواء، والانحراف
 */
interface BeamDesign {
    
    /**
     * حساب التسليح المطلوب للانحناء
     * @return نتيجة التسليح مع حالة الأمان
     */
    fun calculateFlexureReinforcement(
        fcu: Double,              // MPa - مقاومة الخرسانة
        fy: Double,               // MPa - إجهاد خضوع الحديد
        width: Double,            // mm - عرض الكمرة
        effectiveDepth: Double,   // mm - العمق الفعال (d)
        totalDepth: Double,       // mm - العمق الكلي (h)
        designMoment: Double,     // kN.m - عزم التصميم
        loadCombination: LoadCombination
    ): ReinforcementResult

    /**
     * حساب تسليح القص (الكانات)
     */
    fun calculateShearReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        effectiveDepth: Double,
        designShear: Double,      // kN - قوة القص للتصميم
        axialLoad: Double,        // kN - الحمل المحوري إن وجد
        loadCombination: LoadCombination
    ): ShearReinforcementResult

    /**
     * التحقق من حد الانحراف المسموح
     * @return نسبة الانحراف الفعلي إلى المسموح
     */
    fun checkDeflection(
        span: Double,             // m - بحر الكمرة
        totalDepth: Double,       // mm - عمق الكمرة
        reinforcementRatio: Double, // نسبة التسليح
        supportCondition: SupportCondition
    ): DeflectionCheckResult

    /**
     * حساب طول التثبيت (Development Length)
     */
    fun calculateDevelopmentLength(
        barDiameter: Double,      // mm
        fy: Double,               // MPa
        fcu: Double,              // MPa
        barLocation: BarLocation, // موقع السيخ (علوي/سفلي)
        coating: CoatingType      // نوع طلاء الحديد
    ): Double

    // حدود الكود
    fun getMinReinforcementRatio(): Double
    fun getMaxReinforcementRatio(): Double
    fun getMinShearReinforcementRatio(): Double
    fun getMaxShearSpacing(): Double
    fun getMinCover(): Double
    fun getDeflectionLimit(span: Double): Double  // mm
}
