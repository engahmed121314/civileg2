package com.civileg.app.db

import com.civileg.app.domain.entities.MaterialPrices
import com.civileg.app.domain.usecases.CalculateElementBoq
import com.civileg.app.viewmodel.BOQViewModel
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.InvocationTargetException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuantityLedgerTest {
    private fun row(basis: QuantityBasis, v: Double, s: Double, c: Double, type: DesignType = DesignType.BEAM) = Design(
        projectId = 1L, type = type, name = "d", inputData = "{}", results = "{}", isSafe = true,
        codeUsed = "ACI", concreteVolume = v, steelWeight = s, totalCost = c, quantityBasis = basis
    )

    @Test fun `all unknown project has no measured total and counts the unknowns`() {
        val s = QuantityLedger.summarize(List(2) { row(QuantityBasis.UNKNOWN, 0.0, 0.0, 0.0, DesignType.HORDI_SLAB) })
        assertFalse(s.hasMeasuredQuantities)
        assertEquals(2, s.unknownQuantityCount)
        assertEquals(0, s.measuredCount)
        assertEquals(0.0, s.nominalConcrete, 0.0)
        assertEquals(0.0, s.legacyConcrete, 0.0)
    }

    @Test fun `bases stay in separate subtotals and are never added together`() {
        val s = QuantityLedger.summarize(
            listOf(
                row(QuantityBasis.MEASURED, 1.0, 100.0, 10.0),
                row(QuantityBasis.NOMINAL_ESTIMATE, 2.0, 200.0, 20.0),
                row(QuantityBasis.LEGACY_UNVERIFIED, 4.0, 400.0, 40.0),
                row(QuantityBasis.UNKNOWN, 0.0, 0.0, 0.0)
            )
        )
        assertTrue(s.hasMeasuredQuantities)
        assertEquals(1.0, s.totalConcrete, 0.0); assertEquals(100.0, s.totalSteel, 0.0); assertEquals(10.0, s.totalCost, 0.0)
        assertEquals(2.0, s.nominalConcrete, 0.0); assertEquals(200.0, s.nominalSteel, 0.0)
        assertEquals(4.0, s.legacyConcrete, 0.0); assertEquals(400.0, s.legacySteel, 0.0)
        assertEquals(4, s.designCount); assertEquals(1, s.unknownQuantityCount)
    }

    @Test fun `unknown quantity design produces no BOQ`() {
        val vm = BOQViewModel(mockk(relaxed = true), mockk<CalculateElementBoq>(relaxed = true), mockk(relaxed = true))
        val method = BOQViewModel::class.java.getDeclaredMethod(
            "generateBoqFromDesign", Design::class.java, MaterialPrices::class.java
        ).apply { isAccessible = true }
        val error = runCatching { method.invoke(vm, row(QuantityBasis.UNKNOWN, 0.0, 0.0, 0.0), MaterialPrices()) }.exceptionOrNull()
        assertTrue(error is InvocationTargetException)
        assertTrue(error!!.cause is IllegalArgumentException)
    }
}
