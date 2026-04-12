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
    val codeNotes: List<String> = emptyList() // ملاحظات خاصة بالكود
) : Parcelable {
    val safetyStatus: String
        get() = when {
            isSafe && utilizationRatio < 0.7 -> "Safe (Economical)"
            isSafe -> "Safe"
            else -> "Unsafe - Redesign Required"
        }
}
