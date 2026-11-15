package com.civileg.core.engine.boq

/**
 * نتيجة حساب حجم الخرسانة لعنصر إنشائي واحد.
 *
 * // المرجع: مواصفات حساب الكميات الإنشائية (BOQ) - ECP 203 / مواصفات التنفيذ.
 */
data class ConcreteQuantityResult(
    val elementType: String,          // نوع العنصر: Beam, Column, Slab, Footing
    val volumeMm3: Double,            // الحجم بالملم³
    val volumeM3: Double,             // الحجم بالأمتار المكعبة
    val formworkAreaMm2: Double = 0.0, // مساحة الشدة الخشبية بالملم²
    val formworkAreaM2: Double = 0.0,  // مساحة الشدة الخشبية بالأمتار المربعة
    val codeReference: String = "ECP 203 / BOQ Takeoff"
)

/**
 * نتيجة تفريد سيخ حديد واحد أو مجموعة أسياخ بنفس القطر.
 */
data class RebarTakeoffResult(
    val diameterMm: Double,           // قطر السيخ بالملم
    val unitWeightKgPerMeter: Double, // وزن المتر الطولي بالكغ
    val totalLengthM: Double,         // الطول الإجمالي بالأمتار
    val totalCount: Int,              // عدد الأسياخ
    val totalWeightKg: Double,        // الوزن الإجمالي بالكغ
    val codeReference: String = "ECP 203 / Rebar Takeoff"
)

/**
 * سطر في جدول التفريد BBS (Bar Bending Schedule).
 */
data class BBSEntity(
    val mark: String,                 // رقم التحديد (Mark No.)
    val diameterMm: Double,           // قطر السيخ بالملم
    val shape: String,                // شكل التلويح (Straight, Bent, U-shape, etc.)
    val lengthMm: Double,             // طول السيخ بالملم
    val count: Int,                   // عدد الأسياخ بهذا الشكل
    val totalLengthMm: Double,        // الطول الإجمالي بالملم
    val totalWeightKg: Double,        // الوزن الإجمالي بالكغ
    val codeReference: String = "ECP 203 / BBS"
)

/**
 * ملخص BOQ كامل للمشروع.
 */
data class BOQSummary(
    val totalConcreteM3: Double,      // إجمالي حجم الخرسانة بالأمتار المكعبة
    val totalFormworkM2: Double,      // إجمالي مساحة الشدة الخشبية بالأمتار المربعة
    val totalRebarWeightKg: Double,   // إجمالي وزن حديد التسليح بالكغ
    val rebarByDiameter: List<RebarTakeoffResult>, // تفريد الحديد مجمّع حسب القطر
    val bbsEntities: List<BBSEntity>, // جدول التفريد BBS
    val codeReference: String = "ECP 203 / BOQ Summary"
)

/**
 * إدخال تفريد حديد التسليح لعملية BOQ.
 */
data class RebarEntry(
    val diameterMm: Double,   // قطر السيخ بالملم
    val lengthM: Double,      // طول السيخ بالأمتار
    val count: Int            // عدد الأسياخ
)
