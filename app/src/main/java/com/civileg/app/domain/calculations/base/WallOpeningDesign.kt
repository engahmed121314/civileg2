package com.civileg.app.domain.calculations.base

/**
 * واجهة تصميم فتحات الحوائط (Wall Opening Design) عبر الأكواد المختلفة (ECP/ACI/SBC).
 *
 * تُستخدم عندما يُفتح فتحة في حائط خرساني مسلح (حامل أو غير حامل) وتُصمم
 * التسليح حول الفتحة لتعويض الخرسانة المفقودة وتوزيع الأحمال بأمان.
 *
 * الوحدات: mm للأبعاد، kN/kN.m للأحمال، MPa للإجهادات.
 */
interface WallOpeningDesign {
    data class Inputs(
        val wallHeightMm: Double,        // ارتفاع الحائط (mm)
        val wallThicknessMm: Double,     // سماكة الحائط (mm)
        val openingWidthMm: Double,      // عرض الفتحة (mm)
        val openingHeightMm: Double,     // ارتفاع الفتحة (mm)
        val openingFromBaseMm: Double,   // مسافة нижية الفتحة من قاعدة الحائط (mm)
        val wallLengthMm: Double,        // طول الحائط بين الدعامات (mm)
        val fcu: Double,                 // مقاومة الخرسانة (MPa)
        val fy: Double,                  // إجهاد دخوب الحديد (MPa)
        val loadAboveOpeningKNm: Double  // الحمل المحوري فوق الفتحة (kN/م run)
    )

    data class ReinforcementResult(
        val barDiameterMm: Double,
        val numberOfBars: Int,
        val spacingMm: Double,
        val areaProvidedMm2: Double,
        val areaRequiredMm2: Double,
        val isSafe: Boolean
    )

    data class Result(
        val openingReinforcement: ReinforcementResult,    // تسليح حول الفتحة
        val headerBeamReinforcement: ReinforcementResult, // تسليح الكمر العلوي فوق الفتحة
        val sillReinforcement: ReinforcementResult,       // تسليح العتبة السفلية
        val verticalReinforcementEachSide: ReinforcementResult, // تسليح رأسي على كل جانب
        val maxStressMpa: Double,                         // أقصى إجهاد في الخرسانة حول الفتحة
        val isSafe: Boolean,
        val warnings: List<String> = emptyList(),
        val codeNotes: List<String> = emptyList(),
        val calculationSteps: List<String> = emptyList()
    )

    fun design(inputs: Inputs): Result
}