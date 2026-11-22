package com.civileg.app.db

import com.civileg.app.domain.entities.BoqCategory
import com.civileg.app.domain.entities.BoqItem
import com.civileg.app.domain.entities.BoqPricing
import com.civileg.app.domain.entities.SavedMaterialPrices
import com.civileg.app.domain.entities.SavedPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoqPricingTest {
    private fun item(c: BoqCategory, q: Double, unit: String) =
        BoqItem(itemId = c.name, description = c.name, category = c, unit = unit, quantity = q, unitPrice = 1.0, total = q)

    private val items = listOf(
        item(BoqCategory.CONCRETE, 2.0, "m3"),
        item(BoqCategory.REINFORCEMENT, 1.0, "ton"),
        item(BoqCategory.FORMWORK, 10.0, "m2"),
        item(BoqCategory.EXCAVATION, 5.0, "m3")
    )

    @Test fun `nothing saved leaves every line unpriced with quantity kept`() {
        val r = BoqPricing.apply(items, SavedMaterialPrices(), "EGP")
        assertEquals(4, r.unpricedCount)
        assertEquals(0.0, r.pricedTotal, 0.0)
        assertFalse(r.isComplete)
        assertEquals(2.0, r.items[0].quantity, 0.0)
        assertTrue(r.items.none { it.isPriced })
    }

    @Test fun `saved steel price of 38000 for one tonne gives 38000 and total is partial`() {
        val r = BoqPricing.apply(items, SavedMaterialPrices(steel = SavedPrice(38000.0, "EGP")), "EGP")
        assertEquals(38000.0, r.pricedTotal, 1e-9)
        assertEquals(3, r.unpricedCount)
        assertFalse(r.isComplete)
    }

    @Test fun `price saved in another currency is not used`() {
        val r = BoqPricing.apply(items, SavedMaterialPrices(steel = SavedPrice(38000.0, "EGP")), "SAR")
        assertEquals(0.0, r.pricedTotal, 0.0)
        assertEquals(4, r.unpricedCount)
    }

    @Test fun `legacy price without currency is kept out of totals and flagged`() {
        val r = BoqPricing.apply(items, SavedMaterialPrices(concrete = SavedPrice(1000.0, null)), "EGP")
        assertEquals(0.0, r.pricedTotal, 0.0)
        assertEquals(1, r.unknownCurrencyCount)
        assertFalse(r.items[0].isPriced)
        assertEquals(2.0, r.items[0].quantity, 0.0)
    }

    @Test fun `excavation never gets a price from the three saved keys`() {
        val all = SavedMaterialPrices(SavedPrice(1.0, "EGP"), SavedPrice(1.0, "EGP"), SavedPrice(1.0, "EGP"))
        val r = BoqPricing.apply(items, all, "EGP")
        assertEquals(1, r.unpricedCount)
        assertFalse(r.items.last().isPriced)
    }

    @Test fun `report steel is passed in kg so one tonne prints as 1 ton, not 0.001`() {
        val kg = BoqPricing.steelKgForReport(items)
        assertEquals(1000.0, kg, 1e-9)
        assertEquals(1.0, kg / 1000.0, 1e-9) // what the PDF prints as Tons
    }
}
