package com.civileg.app.domain.calculations.ecp

import com.civileg.app.db.*
import com.civileg.app.domain.calculations.base.WaffleSlabDesign
import com.civileg.app.domain.calculations.utils.*
import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Test

/** Approval-containment regressions, not source-method golden vectors. */
class ECPWaffleSafetyContainmentTest {
    private val engine = ECPWaffleSlabDesign()
    private fun dedicated() = engine.design(WaffleSlabDesign.WaffleSlabInput(deadLoad = 0.1, liveLoad = 0.1))

    @Test fun `low load cannot approve unverified dedicated waffle method`() {
        val r = dedicated()
        assertTrue(r.safetyAssessmentBlocked)
        assertFalse(r.isSafe)
        assertFalse(r.ribDesign!!.isSafe)
        assertTrue(r.ribDesign!!.safetyAssessmentBlocked)
        assertFalse(r.solidHeadDesign!!.isSafe)
        assertTrue(r.solidHeadDesign!!.safetyAssessmentBlocked)
        assertFalse(r.punchingShearCheck!!.isSafe)
        assertFalse(r.deflectionCheck!!.isSafe)
        assertTrue(r.warnings.any { it.contains("verification is incomplete") })
    }

    @Test fun `public rib preview cannot return constant approval at extreme demand`() {
        val r = engine.designRib(30.0, 420.0, 150.0, 300.0, 600.0, 6000.0,
            10000.0, LoadCombination.DEAD_LIVE)
        assertFalse(r.isSafe)
        assertTrue(r.safetyAssessmentBlocked)
        assertFalse(r.shearReinforcement.isSafe)
    }

    @Test fun `public solid head placeholder cannot approve its fixed values`() {
        val r = engine.designSolidHead(30.0, 420.0, 1000.0, 150.0, 400.0, 10000.0, 1000.0)
        assertTrue(r.safetyAssessmentBlocked)
        assertFalse(r.isSafe)
        assertFalse(r.punchingShear.isSafe)
    }

    private fun advanced() = ECPAdvancedSlab().designSlab(
        SlabType.Waffle(300.0, 150.0, 250.0, 600.0, 5.0, 6.0,
            SlabSupportConditions(EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED,
                EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED)),
        30.0, 420.0, 20.0, 2.0, null, LoadCombination.DEAD_LIVE
    )

    @Test fun `advanced path cannot use legacy K ratio as safety approval`() {
        val r = advanced()
        assertTrue(r.flexureResult.utilizationRatio < 1.0)
        assertTrue(r.flexureResult.safetyAssessmentBlocked)
        assertFalse(r.flexureResult.isSafe)
        assertFalse(r.shearCheck.isSafe)
        assertFalse(r.deflectionCheck.isSafe)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `advanced held waffle cannot build an execution drawing package`() {
        com.civileg.app.domain.calculations.slab.SlabDrawingPackageBuilder.buildPackage(advanced())
    }

    @Test fun `shared waffle result retains method hold`() {
        val r = CalculatorEngine(io.mockk.mockk(relaxed = true)).designSlab(
            4.0, 5.0, 0.1, 0.1, 30.0, 420.0, 350.0, 12, DesignCode.ECP,
            type = CalculatorEngine.SlabType.WAFFLE
        )
        assertTrue(r.executionAssessmentBlocked)
        assertTrue(r.safetyAssessmentBlocked)
        assertFalse(r.isSafe)
        assertTrue(r.suggestions.any { it.contains("verification is incomplete") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `central waffle BBS refuses held method`() {
        BbsGenerator.generateWaffleSlabBbs("W1", dedicated(), 6000.0, 7500.0, 600.0, 1000.0)
    }

    @Test fun `held waffle has no execution BOQ rows`() {
        assertTrue(BoqEstimator.waffleSlabRows(dedicated(), 6.0, 7.5).isEmpty())
    }

    @Test fun `pre-flag waffle records are classified by actual code without blanket other-code hold`() {
        for (code in DesignCode.entries) {
            val d = Design(projectId = 2, type = DesignType.SLAB, name = "Old waffle", inputData = "{}",
                results = """{"type":"WAFFLE","code":"${code.name}","isSafe":true}""",
                isSafe = true, codeUsed = code.displayName)
            assertEquals(code == DesignCode.ECP, StoredDesignSafety.isBlocked(d))
            assertEquals(code != DesignCode.ECP, StoredDesignSafety.forDisplay(d).isSafe)
        }
    }

    @Test fun `legacy dedicated mixed-code waffle stays unresolved and preserves its JSON`() {
        val old = Design(projectId = 2, type = DesignType.WAFFLE_SLAB, name = "Old waffle",
            inputData = "{}", results = """{"isSafe":true}""", isSafe = true, codeUsed = "ECP/ACI")
        assertEquals(StoredDesignSafety.Status.WAFFLE_CODE_UNRESOLVED, StoredDesignSafety.status(old))
        assertFalse(StoredDesignSafety.forDisplay(old).isSafe)
        assertEquals(old.results, StoredDesignSafety.forDisplay(old).results)
    }
}
