package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import org.junit.Assert.assertEquals
import org.junit.Test

class AdvancedResultAcceptanceTest {
    @Test fun `failure wins over missing and empty never passes`() {
        assertEquals(DisplayedCheckStatus.INCOMPLETE, displayedCheckStatus())
        assertEquals(DisplayedCheckStatus.INCOMPLETE, displayedCheckStatus(true, null))
        assertEquals(DisplayedCheckStatus.FAILED, displayedCheckStatus(false, null))
        assertEquals(DisplayedCheckStatus.PASSED, displayedCheckStatus(true, true))
    }

    private fun column() = AdvancedColumnResult(
        ColumnType.Rectangular(300.0, 600.0), 2000.0, 200.0, 200.0, 10.0,
        false, 3000.0, ReinforcementResult(1000.0, 1200.0, 20.0, 4, 8.0, 150.0,
            isSafe = true, utilizationRatio = 0.8), null, null, null,
        emptyList(), emptyList()
    )

    @Test fun `passing reinforcement cannot hide biaxial or punching failure`() {
        val base = column()
        val biaxial = BiaxialCheckResult(0.5, 0.5, 1.2, false, "fixture")
        val punching = PunchingCheckResult(100.0, 90.0, false, false, 1000.0)
        assertEquals(DisplayedCheckStatus.FAILED,
            base.copy(biaxialCheck = biaxial).displayedCheckStatus(true, false))
        assertEquals(DisplayedCheckStatus.FAILED,
            base.copy(biaxialCheck = null, punchingCheck = punching).displayedCheckStatus(false, true))
    }

    @Test fun `required absent column checks are incomplete but not applicable is allowed`() {
        val base = column().copy(biaxialCheck = null, punchingCheck = null)
        assertEquals(DisplayedCheckStatus.INCOMPLETE, base.displayedCheckStatus(true, false))
        assertEquals(DisplayedCheckStatus.INCOMPLETE, base.displayedCheckStatus(false, true))
        assertEquals(DisplayedCheckStatus.PASSED, base.displayedCheckStatus(false, false))
    }

    private fun slab() = AdvancedSlabResult(
        SlabType.OneWay(180.0, 4.0, 1.0),
        SlabDesignResult(500.0, 600.0, 12.0, 150.0, 180.0, 100.0, true, 0.8),
        ShearCheckResult(isSafe = true), DeflectionCheckResult(isSafe = true), null,
        ReinforcementLayout(BarLayout(12.0, 150.0, BarDirection.SHORT, 4.0, 7),
            BarLayout(12.0, 150.0, BarDirection.SHORT, 4.0, 7), null, emptyList()),
        0.72, 4.0, null, null, emptyList(), emptyList()
    )

    @Test fun `passing flexure cannot hide shear or deflection failure`() {
        val base = slab()
        assertEquals(DisplayedCheckStatus.FAILED,
            base.copy(shearCheck = base.shearCheck.copy(isSafe = false)).displayedCheckStatus())
        assertEquals(DisplayedCheckStatus.FAILED,
            base.copy(deflectionCheck = base.deflectionCheck.copy(isSafe = false)).displayedCheckStatus())
        assertEquals(DisplayedCheckStatus.PASSED, base.displayedCheckStatus())
    }

    @Test fun `flat slab requires punching result and failure wins`() {
        val base = slab().copy(slabType = SlabType.FlatPlate(200.0, 6.0, 6.0, 400.0))
        assertEquals(DisplayedCheckStatus.INCOMPLETE,
            base.copy(punchingShearCheck = null).displayedCheckStatus())
        assertEquals(DisplayedCheckStatus.FAILED,
            base.copy(punchingShearCheck = PunchingShearCheckResult(isSafe = false)).displayedCheckStatus())
        assertEquals(DisplayedCheckStatus.PASSED,
            base.copy(punchingShearCheck = PunchingShearCheckResult(isSafe = true)).displayedCheckStatus())
    }

    @Test fun `PT requires stress result and propagates failure`() {
        val support = SlabSupportConditions(EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED,
            EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED)
        val base = slab().copy(slabType = SlabType.PostTensioned(200.0, 6.0, 6.0,
            TendonType.BONDED, 1000.0, 40.0, support))
        assertEquals(DisplayedCheckStatus.INCOMPLETE,
            base.copy(postTensionCalculations = null).displayedCheckStatus())
        assertEquals(DisplayedCheckStatus.FAILED,
            base.copy(postTensionCalculations = PostTensionCalculations(isSafe = false)).displayedCheckStatus())
    }
}
