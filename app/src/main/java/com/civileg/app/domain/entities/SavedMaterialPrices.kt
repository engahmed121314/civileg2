package com.civileg.app.domain.entities

/** A price the user typed in Settings. [currency] is null for values saved before the currency was recorded. */
data class SavedPrice(val value: Double, val currency: String?)

/** The user's saved material prices; null means the user never entered one. No invented default. */
data class SavedMaterialPrices(
    val concrete: SavedPrice? = null,
    val steel: SavedPrice? = null,
    val formwork: SavedPrice? = null
)

/** BOQ lines after pricing with the user's saved prices only. */
data class PricedBoq(
    val items: List<BoqItem>,
    /** Sum of priced lines only. Not a full estimate when [unpricedCount] > 0. */
    val pricedTotal: Double,
    val unpricedCount: Int,
    /** True when a price used was saved before its currency was recorded. */
    val usesUnrecordedCurrency: Boolean
) {
    val isComplete: Boolean get() = unpricedCount == 0 && items.isNotEmpty()
}

object BoqPricing {
    /**
     * Only concrete, reinforcement and formwork have a user price. Any other category (excavation,
     * waterproofing, ...) has none and stays unpriced. A price recorded in a different currency than
     * [activeCurrency] is not used. Unpriced lines keep their quantity.
     */
    fun apply(items: List<BoqItem>, saved: SavedMaterialPrices, activeCurrency: String): PricedBoq {
        var unrecorded = false
        val out = items.map { item ->
            val p = when (item.category) {
                BoqCategory.CONCRETE -> saved.concrete
                BoqCategory.REINFORCEMENT -> saved.steel
                BoqCategory.FORMWORK -> saved.formwork
                else -> null
            }
            val usable = p != null && p.value > 0.0 && (p.currency == null || p.currency == activeCurrency)
            if (usable) {
                if (p!!.currency == null) unrecorded = true
                item.copy(unitPrice = p.value, total = item.quantity * p.value, isPriced = true)
            } else {
                item.copy(unitPrice = 0.0, total = 0.0, isPriced = false)
            }
        }
        return PricedBoq(out, out.filter { it.isPriced }.sumOf { it.total }, out.count { !it.isPriced }, unrecorded)
    }
}
