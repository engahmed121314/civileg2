package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.*
import org.junit.Test

class ECPHordiFlexureConsistencyTest {
    private fun design(moment: Double) = ECPHordiSlabDesign().designHordiSlab(
        30.0, 400.0, 150.0, 600.0, 300.0, 50.0, 6000.0,
        moment, 5.0, LoadCombination.DEAD_LIVE
    )
    @Test fun `infeasible high moment cannot pass on shear alone`() {
        val r = design(1000.0)
        assertFalse(r.isSafe)
        assertTrue(r.providedReinforcement < r.requiredReinforcement)
        assertTrue(r.utilizationRatio > 1.0)
        assertTrue(r.warnings.any { it.contains("Flexure") })
    }
    @Test fun `sufficient low moment retains area and shear consistency`() {
        val r = design(10.0)
        assertTrue(r.providedReinforcement >= r.requiredReinforcement)
        assertTrue(r.shearCapacity >= 5.0)
        assertTrue(r.utilizationRatio.isFinite())
    }
    @Test(expected = IllegalArgumentException::class)
    fun `negative moment is not a usable area solution`() { design(-1.0) }
}
