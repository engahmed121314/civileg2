package com.civileg.app.db

import com.civileg.app.domain.entities.BoqCategory
import com.civileg.app.domain.entities.BoqItem
import com.civileg.app.domain.entities.MaterialPrices
import com.civileg.app.domain.usecases.CalculateElementBoq
import com.civileg.app.viewmodel.BOQViewModel
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoqFallbackSteelUnitTest {
    @Suppress("UNCHECKED_CAST")
    @Test fun `stored kg steel is priced as tonnes in the aggregate fallback`() {
        val design = Design(
            id = 1L, projectId = 1L, type = DesignType.BEAM, name = "Beam",
            inputData = "{}", results = "{}", isSafe = true, codeUsed = "ECP",
            concreteVolume = 1.0, steelWeight = 500.0 // kg
        )
        val prices = MaterialPrices(steelPerTon = 18000.0)
        val vm = BOQViewModel(mockk(relaxed = true), mockk<CalculateElementBoq>(relaxed = true), mockk(relaxed = true))
        val method = BOQViewModel::class.java.getDeclaredMethod(
            "generateBoqFromDesign", Design::class.java, MaterialPrices::class.java
        ).apply { isAccessible = true }
        val items = method.invoke(vm, design, prices) as List<BoqItem>
        val steel = items.firstOrNull { it.category == BoqCategory.REINFORCEMENT }
        assertNotNull(steel)
        assertEquals("ton", steel!!.unit)
        assertEquals(0.5, steel.quantity, 1e-9)
        assertEquals(9000.0, steel.total, 1e-6)
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun `one thousand kg is exactly one tonne`() {
        val design = Design(
            id = 2L, projectId = 1L, type = DesignType.BEAM, name = "Beam",
            inputData = "{}", results = "{}", isSafe = true, codeUsed = "ECP",
            concreteVolume = 0.0, steelWeight = 1000.0
        )
        val vm = BOQViewModel(mockk(relaxed = true), mockk<CalculateElementBoq>(relaxed = true), mockk(relaxed = true))
        val method = BOQViewModel::class.java.getDeclaredMethod(
            "generateBoqFromDesign", Design::class.java, MaterialPrices::class.java
        ).apply { isAccessible = true }
        val items = method.invoke(vm, design, MaterialPrices(steelPerTon = 20000.0)) as List<BoqItem>
        val steel = items.single { it.category == BoqCategory.REINFORCEMENT }
        assertEquals(1.0, steel.quantity, 1e-9)
        assertEquals(20000.0, steel.total, 1e-6)
    }
}
