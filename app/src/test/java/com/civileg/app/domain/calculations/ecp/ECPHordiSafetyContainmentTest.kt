package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.*
import org.junit.Test

/** Legacy-model consistency regressions, NOT ECP method-validation vectors. */
class ECPHordiSafetyContainmentTest {
    private fun design(moment: Double = 10.0, shear: Double = 1.0, depth: Double = 300.0) =
        ECPHordiSlabDesign().designHordiSlab(
            fcu = 25.0, fy = 360.0, ribWidth = 150.0, ribSpacing = 600.0,
            totalThickness = depth, toppingThickness = 60.0, span = 5000.0,
            designMoment = moment, designShear = shear,
            loadCombination = LoadCombination.DEAD_LIVE
        )

    @Test fun `low shear cannot hide insufficient flexural steel`() {
        val result = design(moment = 40.0)
        assertTrue(result.shearCapacity > 1.0)
        assertTrue(result.requiredReinforcement > result.providedReinforcement)
        assertFalse(result.isSafe)
        assertTrue(result.utilizationRatio > 1.0)
        assertTrue(result.warnings.any { it.contains("required flexural reinforcement") })
    }

    @Test fun `negative discriminant cannot be made safe by clamping the square root`() {
        val result = design(moment = 1000.0)
        assertFalse(result.isSafe)
        assertTrue(result.utilizationRatio > 1.0)
        assertTrue(result.warnings.any { it.contains("model range") })
    }

    @Test fun `low demand remains method blocked even when legacy ratios pass`() {
        val result = design()
        assertTrue(result.providedReinforcement >= result.requiredReinforcement)
        assertTrue(result.utilizationRatio <= 1.0)
        assertTrue(result.safetyAssessmentBlocked)
        assertFalse(result.isSafe)
        assertTrue(result.warnings.any { it.contains("verification is incomplete") })
    }

    @Test fun `wrapper preserves the blocked safety assessment`() {
        val result = ECPHordiSlabWrapper().designHordiSlab(
            25.0, 360.0, 150.0, 600.0, 300.0, 60.0, 5000.0,
            10.0, 1.0, LoadCombination.DEAD_LIVE
        )
        assertTrue(result.safetyAssessmentBlocked)
        assertFalse(result.isSafe)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `engine enforces existing nonnegative moment magnitude contract`() { design(moment = -1.0) }

    @Test(expected = IllegalArgumentException::class)
    fun `engine enforces existing nonnegative shear magnitude contract`() { design(shear = -1.0) }

    @Test(expected = IllegalArgumentException::class)
    fun `engine rejects nonfinite demand`() { design(moment = Double.NaN) }

    @Test(expected = IllegalArgumentException::class)
    fun `engine rejects nonpositive effective depth`() { design(depth = 30.0) }
}
