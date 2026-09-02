package com.civileg.app.domain.entities

import android.os.Parcelable
import com.civileg.core.calculations.entities.DesignCode
import kotlinx.parcelize.Parcelize

/**
 * عنصر في جدول الكميات والأسعار (Bill of Quantities)
 *
 * R5 cost detail: each item's total decomposes into MATERIALS / LABOR /
 * EQUIPMENT using the canonical per-category split from [BoqCostSplit]
 * (§17 single source — no per-caller invented ratios).
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
) : Parcelable {
    /** Cost decomposition of [total] — materials first component. */
    val materialCost: Double get() = total * BoqCostSplit.material(category)
    /** Cost decomposition of [total] — labor component. */
    val laborCost: Double get() = total * BoqCostSplit.labor(category)
    /** Cost decomposition of [total] — equipment component. */
    val equipmentCost: Double get() = total * BoqCostSplit.equipment(category)
}

/**
 * Canonical MATERIAL/LABOR/EQUIPMENT split per BOQ category (R5).
 * Typical Egyptian-market build rates; each triple sums to 1.0 by contract
 * (verified in tests). Override point if a project uses different crew mixes.
 */
object BoqCostSplit {
    private data class Split(val material: Double, val labor: Double, val equipment: Double)

    private val byCategory = mapOf(
        BoqCategory.CONCRETE      to Split(0.62, 0.28, 0.10),  // ready-mix heavy, carpentry/crew, pumps/vibrators
        BoqCategory.REINFORCEMENT to Split(0.78, 0.17, 0.05),  // steel dominates, bending/fixing crew, cutting gear
        BoqCategory.FORMWORK      to Split(0.45, 0.45, 0.10),  // plywood/timber vs skilled carpenters, cranes
        BoqCategory.EXCAVATION    to Split(0.15, 0.25, 0.60),  // machinery-dominated
        BoqCategory.FINISHES      to Split(0.55, 0.40, 0.05),
        BoqCategory.MISCELLANEOUS to Split(0.50, 0.35, 0.15)
    )

    private fun split(category: BoqCategory) =
        byCategory.getValue(category)

    fun material(category: BoqCategory): Double = split(category).material
    fun labor(category: BoqCategory): Double = split(category).labor
    fun equipment(category: BoqCategory): Double = split(category).equipment

    fun materialRatio(category: BoqCategory): Double = split(category).material
}

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
