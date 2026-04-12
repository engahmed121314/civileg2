package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * عنصر في جدول الكميات والأسعار (Bill of Quantities)
 */
@Parcelize
data class BoqItem(
    val itemId: String,           // كود العنصر (مثلاً: CONC_001)
    val description: String,      // وصف العنصر
    val category: BoqCategory,    // التصنيف
    val unit: String,             // وحدة القياس
    val quantity: Double,         // الكمية
    val unitPrice: Double,        // سعر الوحدة
    val total: Double = quantity * unitPrice,
    val codeReference: String? = null  // مرجع الكود إن وجد
) : Parcelable

enum class BoqCategory(val displayName: String) {
    CONCRETE("Concrete Works"),
    REINFORCEMENT("Reinforcement Steel"),
    FORMWORK("Formwork & Shuttering"),
    EXCAVATION("Excavation & Earthwork"),
    FINISHES("Finishes"),
    MISCELLANEOUS("Miscellaneous")
}

/**
 * جدول الكميات الكامل للمشروع
 */
@Parcelize
data class BillOfQuantities(
    val projectName: String,
    val designCode: DesignCode,
    val items: List<BoqItem>,
    val currency: String = "EGP",
    val lastUpdated: Long = System.currentTimeMillis()
) : Parcelable {
    
    // حسابات مجمعة
    fun getTotalByCategory(category: BoqCategory): Double =
        items.filter { it.category == category }.sumOf { it.total }
    
    fun getGrandTotal(): Double = items.sumOf { it.total }
    
    fun getConcreteVolume(): Double =
        items.filter { it.category == BoqCategory.CONCRETE }.sumOf { it.quantity }
    
    fun getSteelWeight(): Double =
        items.filter { it.category == BoqCategory.REINFORCEMENT }.sumOf { it.quantity }
}
