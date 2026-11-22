package com.civileg.app.db

import com.civileg.app.domain.entities.BoqCategory
import com.civileg.app.domain.entities.BoqItem
import com.civileg.app.domain.entities.BoqPricing
import com.civileg.app.domain.entities.PriceValidation
import com.civileg.app.domain.entities.SavedMaterialPrices
import com.civileg.app.domain.entities.SavedPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoqPriceValidationTest {
    private fun concrete(q: Double) =
        BoqItem(itemId = "c", description = "c", category = BoqCategory.CONCRETE, unit = "m3", quantity = q, unitPrice = 1.0, total = q)

    @Test fun `only finite positive values are valid prices`() {
        assertTrue(PriceValidation.isValid(1200.0))
        assertFalse(PriceValidation.isValid(0.0))
        assertFalse(PriceValidation.isValid(-5.0))
        assertFalse(PriceValidation.isValid(Double.NaN))
        assertFalse(PriceValidation.isValid(Double.POSITIVE_INFINITY))
        assertFalse(PriceValidation.isValid(Double.NEGATIVE_INFINITY))
    }

    @Test fun `non finite or negative saved prices leave the line unpriced and out of the total`() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -100.0, 0.0)) {
            val r = BoqPricing.apply(listOf(concrete(2.0)), SavedMaterialPrices(concrete = SavedPrice(bad, "EGP")), "EGP")
            assertEquals(1, r.unpricedCount)
            assertEquals(0.0, r.pricedTotal, 0.0)
            assertTrue(r.pricedTotal.isFinite())
            assertEquals(2.0, r.items[0].quantity, 0.0)
        }
    }

    @Test fun `a non finite quantity is never multiplied into a priced total`() {
        val r = BoqPricing.apply(listOf(concrete(Double.NaN)), SavedMaterialPrices(concrete = SavedPrice(1000.0, "EGP")), "EGP")
        assertEquals(1, r.unpricedCount)
        assertTrue(r.pricedTotal.isFinite())
    }

    @Test fun `finite inputs that overflow a line total leave the line unpriced`() {
        val r = BoqPricing.apply(listOf(concrete(1e200)), SavedMaterialPrices(concrete = SavedPrice(1e200, "EGP")), "EGP")
        assertEquals(1, r.unpricedCount)
        assertEquals(0.0, r.pricedTotal, 0.0)
        assertFalse(r.isComplete)
    }

    @Test fun `an aggregate that would overflow keeps earlier lines and leaves the overflowing line unpriced`() {
        val big = Double.MAX_VALUE / 1.5
        val items = listOf(
            BoqItem(itemId = "c", description = "c", category = BoqCategory.CONCRETE, unit = "m3", quantity = 1.0, unitPrice = 1.0, total = 1.0),
            BoqItem(itemId = "s", description = "s", category = BoqCategory.REINFORCEMENT, unit = "ton", quantity = 1.0, unitPrice = 1.0, total = 1.0),
            BoqItem(itemId = "f", description = "f", category = BoqCategory.FORMWORK, unit = "m2", quantity = 1.0, unitPrice = 1.0, total = 1.0)
        )
        val saved = SavedMaterialPrices(concrete = SavedPrice(big, "EGP"), steel = SavedPrice(big, "EGP"), formwork = SavedPrice(5.0, "EGP"))
        val r = BoqPricing.apply(items, saved, "EGP")
        assertTrue(r.pricedTotal.isFinite())
        assertTrue(r.items[0].isPriced)
        assertFalse(r.items[1].isPriced)
        assertEquals(1, r.items.count { !it.isPriced && it.category == BoqCategory.REINFORCEMENT })
        assertFalse(r.isComplete)
    }

    @Test fun `a negative quantity is never priced`() {
        val r = BoqPricing.apply(listOf(concrete(-3.0)), SavedMaterialPrices(concrete = SavedPrice(1000.0, "EGP")), "EGP")
        assertEquals(1, r.unpricedCount)
        assertEquals(0.0, r.pricedTotal, 0.0)
    }
}
