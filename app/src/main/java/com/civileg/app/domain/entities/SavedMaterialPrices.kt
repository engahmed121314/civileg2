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
    /** Lines left unpriced only because the saved price has no recorded currency (value preserved, not used). */
    val unknownCurrencyCount: Int
) {
    val isComplete: Boolean get() = unpricedCount == 0 && items.isNotEmpty()
}

object BoqPricing {
    /**
     * Only concrete, reinforcement and formwork have a user price. Any other category (excavation,
     * waterproofing, ...) has none and stays unpriced. A price recorded in a different currency than
     * [activeCurrency], or saved with no recorded currency, is never used: its currency is unknown, so
     * it cannot enter a total. Unpriced lines keep their quantity.
     */
    fun apply(items: List<BoqItem>, saved: SavedMaterialPrices, activeCurrency: String): PricedBoq {
        var unknownCcy = 0
        val out = items.map { item ->
            val p = when (item.category) {
                BoqCategory.CONCRETE -> saved.concrete
                BoqCategory.REINFORCEMENT -> saved.steel
                BoqCategory.FORMWORK -> saved.formwork
                else -> null
            }
            val usable = p != null && p.value > 0.0 && p.currency == activeCurrency
            if (usable) {
                item.copy(unitPrice = p.value, total = item.quantity * p.value, isPriced = true)
            } else {
                if (p != null && p.value > 0.0 && p.currency == null) unknownCcy++
                item.copy(unitPrice = 0.0, total = 0.0, isPriced = false)
            }
        }
        return PricedBoq(out, out.filter { it.isPriced }.sumOf { it.total }, out.count { !it.isPriced }, unknownCcy)
    }
}
