package com.civileg.app.domain.usecases

import com.civileg.app.domain.entities.MaterialPrices
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P030 regression support — combined vs isolated footing BOQ item families.
 *
 * The ViewModel dispatch bug (duplicate `when` FOOTING branch making the
 * CFTG_* path unreachable) was fixed by merging branches. The dispatch itself
 * parses org.json and therefore needs an instrumented test to verify end-to-end;
 * THIS test pins the contract each branch must produce: distinct, non-empty,
 * correctly-prefixed line-item families with sane quantities.
 */
class CombinedFootingBoqTest {

    private val useCase = CalculateElementBoq()
    private val prices = MaterialPrices()

    @Test fun combinedFooting_emitsCftgFamily() {
        val items = useCase.calculateCombinedFootingBoq(
            length = 6000.0, width = 2000.0, thickness = 700.0,
            concreteGrade = 30.0, astBottomX = 1500.0, astBottomY = 1200.0,
            rebarDia = 16.0, rebarSpacingX = 180.0, rebarSpacingY = 200.0,
            prices = prices, excavationDepth = 1.5
        )
        assertTrue(items.isNotEmpty())
        assertTrue("all ids must be CFTG_*", items.all { it.itemId.startsWith("CFTG_") })
        // Must contain the combined-specific PC blinding + R.C. + both rebar directions
        listOf("_PC_001", "_CONC_001", "_REINF_X_001", "_REINF_Y_001", "_FORM_001").forEach { suffix ->
            assertTrue("missing $suffix in ${items.map { it.itemId }}",
                items.any { it.itemId.endsWith(suffix) })
        }
        // Excavation present when depth > 0 (with working-space offset)
        assertTrue(items.any { it.itemId.endsWith("_EXCAV_001") })
        assertTrue(items.all { it.quantity > 0.0 && it.total >= 0.0 })
    }

    @Test fun isolatedFooting_emitsFtgFamily_distinctGeometry() {
        val isolated = useCase.calculateFootingBoq(
            length = 3000.0, width = 3000.0, thickness = 600.0,
            concreteGrade = 30.0, astBottomX = 1000.0, astBottomY = 1000.0,
            rebarDia = 14.0, rebarSpacingX = 200.0, rebarSpacingY = 200.0,
            prices = prices, excavationDepth = 1.2
        )
        val combined = useCase.calculateCombinedFootingBoq(
            length = 3000.0, width = 3000.0, thickness = 600.0,
            concreteGrade = 30.0, astBottomX = 1000.0, astBottomY = 1000.0,
            rebarDia = 14.0, rebarSpacingX = 200.0, rebarSpacingY = 200.0,
            prices = prices, excavationDepth = 1.2
        )
        assertTrue(isolated.any { it.itemId.startsWith("FTG_") })
        assertTrue(combined.any { it.itemId.startsWith("CFTG_") })
        // Same geometry → identical concrete volume across both paths (one source of truth)
        val isoConc = isolated.first { it.itemId.endsWith("_CONC_001") }.quantity
        val cmbConc = combined.first { it.itemId.endsWith("_CONC_001") }.quantity
        assertTrue("concrete volume must match for equal geometry ($isoConc vs $cmbConc)",
            Math.abs(isoConc - cmbConc) < 1e-9)
    }

    @Test fun zeroExcavation_omitsExcavationItem() {
        val items = useCase.calculateCombinedFootingBoq(
            length = 5000.0, width = 2000.0, thickness = 600.0,
            concreteGrade = 25.0, astBottomX = 800.0, astBottomY = 800.0,
            rebarDia = 12.0, rebarSpacingX = 200.0, rebarSpacingY = 250.0,
            prices = prices, excavationDepth = 0.0
        )
        assertTrue(items.none { it.itemId.endsWith("_EXCAV_001") })
    }
}
