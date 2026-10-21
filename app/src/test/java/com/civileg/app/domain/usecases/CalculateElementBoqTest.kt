package com.civileg.app.domain.usecases

import com.civileg.app.domain.entities.BoqCategory
import com.civileg.app.domain.entities.MaterialPrices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateElementBoqTest {

    private val useCase = CalculateElementBoq()

    @Test
    fun `tank project boq includes base plus wall waterproofing`() {
        val items = useCase.calculateTankBoq(
            tankLength = 4.0, tankWidth = 3.0, tankHeight = 2.5,
            wallThickness = 250.0, baseThickness = 300.0,
            wallRebarDia = 12.0, wallRebarSpacingH = 200.0, wallRebarSpacingV = 200.0,
            baseRebarDia = 12.0, baseRebarSpacing = 200.0,
            prices = MaterialPrices()
        )

        val waterproofing = items.single { it.itemId == "TNK_WP_001" }
        assertEquals(BoqCategory.MISCELLANEOUS, waterproofing.category)
        assertEquals("m2", waterproofing.unit)
        assertEquals(4.0 * 3.0 + 2.0 * (4.0 + 3.0) * 2.5, waterproofing.quantity, 1e-9)
        assertEquals(80.0, waterproofing.unitPrice, 1e-9)
        assertTrue(waterproofing.total > 0.0)
    }

    @Test
    fun `retaining wall project boq includes earth face waterproofing`() {
        val items = useCase.calculateRetainingWallBoq(
            wallLength = 10.0, totalHeight = 4.0, baseWidth = 2.5,
            baseThickness = 400.0, stemTopThickness = 200.0, stemBottomThickness = 300.0,
            mainRebarDia = 16.0,
            verticalRebarSpacing = 150.0,
            prices = MaterialPrices()
        )

        val waterproofing = items.single { it.itemId == "RW_WP_001" }
        assertEquals(40.0, waterproofing.quantity, 1e-9)
        assertEquals(80.0, waterproofing.unitPrice, 1e-9)
    }
}
