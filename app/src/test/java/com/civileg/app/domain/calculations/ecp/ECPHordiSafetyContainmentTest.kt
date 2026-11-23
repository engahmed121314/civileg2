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

    private fun advancedHordi() = ECPAdvancedSlab().designSlab(
        slabType = com.civileg.app.domain.entities.SlabType.Hordi(
            totalThickness = 300.0, ribWidth = 150.0, ribSpacing = 600.0,
            blockWidth = 450.0, blockHeight = 240.0, span = 5.0,
            supportConditions = com.civileg.app.domain.entities.SlabSupportConditions(
                edgeA = com.civileg.app.domain.entities.EdgeCondition.SIMPLY_SUPPORTED,
                edgeB = com.civileg.app.domain.entities.EdgeCondition.SIMPLY_SUPPORTED,
                edgeC = com.civileg.app.domain.entities.EdgeCondition.SIMPLY_SUPPORTED,
                edgeD = com.civileg.app.domain.entities.EdgeCondition.SIMPLY_SUPPORTED
            )
        ),
        fcu = 25.0, fy = 400.0, deadLoad = 0.1, liveLoad = 0.1,
        inventory = null, loadCombination = LoadCombination.DEAD_LIVE
    )

    @Test fun `alternate advanced Hordi cannot bypass the method hold at low load`() {
        val result = advancedHordi()
        assertTrue(result.flexureResult.utilizationRatio < 1.0)
        assertTrue(result.flexureResult.safetyAssessmentBlocked)
        assertFalse(result.flexureResult.isSafe)
        assertFalse(result.shearCheck.isSafe)
        assertFalse(result.deflectionCheck.isSafe)
        assertTrue(result.warnings.any { it.contains("verification is incomplete") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blocked advanced Hordi cannot generate an execution drawing package`() {
        com.civileg.app.domain.calculations.slab.SlabDrawingPackageBuilder.buildPackage(advancedHordi())
    }

    @Test fun `shared hollow block result retains the hold warning and cannot be safe`() {
        val result = sharedResult()
        assertTrue(result.safetyAssessmentBlocked)
        assertFalse(result.isSafe)
        assertTrue(result.suggestions.any { it.contains("verification is incomplete") })
    }
    private fun sharedResult() = com.civileg.app.domain.calculations.utils.CalculatorEngine(io.mockk.mockk(relaxed = true)).designSlab(
            lx = 4.0, ly = 5.0, deadLoad = 0.1, liveLoad = 0.1,
            fcu = 25.0, fy = 360.0, ts = 300.0, preferredDiameter = 12,
            code = com.civileg.app.domain.entities.DesignCode.ECP,
            type = com.civileg.app.domain.calculations.utils.CalculatorEngine.SlabType.HOLLOW_BLOCK,
            ribWidth = 150.0, ribSpacing = 600.0
        )

    @Test fun `shared method hold survives JSON storage and drawing summary`() {
        val gson = com.google.gson.Gson()
        val result = sharedResult()
        val restored = gson.fromJson(gson.toJson(result), result.javaClass)
        assertTrue(restored.safetyAssessmentBlocked)
        assertEquals("NOT VERIFIED", com.civileg.app.ui.compose.components.drawings.slabDrawingSummary(restored).safetyLabel)
        assertTrue(com.civileg.app.domain.calculations.utils.BoqEstimator.slabRows(restored, 4.0, 5.0).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `shared method hold rejects central bar schedule generator`() {
        com.civileg.app.domain.calculations.utils.BbsGenerator.generateSlabBbs("S1", sharedResult(), 4000.0, 5000.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `dedicated method hold rejects central bar schedule generator`() {
        com.civileg.app.domain.calculations.utils.BbsGenerator.generateHordiSlabBbs("H1", design(), 5000.0, 600.0)
    }

}
