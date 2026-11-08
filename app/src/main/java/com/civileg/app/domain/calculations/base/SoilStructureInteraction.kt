package com.civileg.app.domain.calculations.base

/**
 * واجهة تفاعل المنشأ مع التربة (Soil-Structure Interaction) عبر الأكواد المختلفة (ECP/ACI/SBC).
 *
 * تُستخدم لتحليل توزيع الضغوط تحت الأساسات، حساب الهبوط، وتقييم التفاعل بين
 * البنية والتربة تحتها.
 *
 * الوحدات: mm للأبعاد، kN/kN.m للأحمال، kPa لضغط التربة، MPa للإجهادات.
 */
interface SoilStructureInteraction {
    data class Inputs(
        val foundationLengthMm: Double,  // طول الأساس (mm)
        val foundationWidthMm: Double,   // عرض الأساس (mm)
        val foundationThicknessMm: Double, // سماكة الأساس (mm)
        val columnLoadKN: Double,        // حمل العمود المركزي (kN)
        val momentKNm: Double,           // عزم الانقلاب (kN.m)
        val soilBearingCapacityKpa: Double, // سعة التربة المسموحة (kPa)
        val soilUnitWeightKnm3: Double,  // poids النوعي للتربة (kN/m3)
        val frictionAngleDeg: Double,    // زاوية الاحتكاك الداخلية للتربة (degrees)
        val fcu: Double,                 // مقاومة الخرسانة (MPa)
        val fy: Double,                  // إجهاد دخوب الحديد (MPa)
        val embedmentDepthMm: Double     // عمق دفن الأساس (mm)
    )

    data class BearingResult(
        val maxPressureKpa: Double,      // أقصى ضغط تحت الأساس (kPa)
        val minPressureKpa: Double,      // أقل ضغط تحت الأساس (kPa)
        val averagePressureKpa: Double,  // متوسط الضغط (kPa)
        val allowablePressureKpa: Double, // الضغط المسموح (kPa)
        val isSafe: Boolean
    )

    data class SettlementResult(
        val totalSettlementMm: Double,   // الهبوط الكلي (mm)
        val allowableSettlementMm: Double, // الهبوط المسموح (mm)
        val isSafe: Boolean
    )

    data class StabilityResult(
        val factorOfSafetyOverturning: Double,  // معامل الأمان ضد الانقلاب
        val factorOfSafetySliding: Double,      // معامل الأمان ضد الانزلاق
        val factorOfSafetyBearing: Double,      // معامل الأمان ضد تحمل التربة
        val isSafe: Boolean
    )

    data class Result(
        val bearing: BearingResult,
        val settlement: SettlementResult,
        val stability: StabilityResult,
        val maxSoilPressureKpa: Double,
        val minSoilPressureKpa: Double,
        val isSafe: Boolean,
        val warnings: List<String> = emptyList(),
        val codeNotes: List<String> = emptyList(),
        val calculationSteps: List<String> = emptyList()
    )

    fun design(inputs: Inputs): Result
}