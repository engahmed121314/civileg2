package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ReinforcementResult(
    val astRequired: Double,        // mm² - مساحة الحديد المطلوبة
    val astProvided: Double,        // mm² - مساحة الحديد المقدمة
    val barDiameter: Double,        // mm - قطر السيخ الرئيسي
    val numberOfBars: Int,          // عدد الأسياخ
    val tiesDiameter: Double,       // mm - قطر الكانات
    val tiesSpacing: Double,        // mm - تباعد الكانات
    val isSafe: Boolean,            // هل التصميم آمن؟
    val utilizationRatio: Double,   // نسبة الاستغلال
    val warnings: List<String> = emptyList(), // تحذيرات إن وجدت
    val codeNotes: List<String> = emptyList(), // ملاحظات خاصة بالكود
    val spacing: Double = 0.0,
    val description: String = ""
) : Parcelable {
    val barString: String get() = if (numberOfBars > 0) "${numberOfBars}Ø${barDiameter.toInt()}" else if (spacing > 0) "${(1000/spacing).toInt()}Ø${barDiameter.toInt()}/m'" else description

    val safetyStatus: String
        get() = when {
            isSafe && utilizationRatio < 0.7 -> "Safe (Economical)"
            isSafe -> "Safe"
            else -> "Unsafe - Redesign Required"
        }
}
