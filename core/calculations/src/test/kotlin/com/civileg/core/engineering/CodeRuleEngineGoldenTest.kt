package com.civileg.core.engineering

import com.civileg.core.calculations.entities.SupportCondition
import org.junit.Assert.*
import org.junit.Test

/**
 * Golden tests for [CodeRuleEngine] (PHASE 09 single entry point).
 *
 * Verifies both families instantiate all bundled engines and that the
 * convenience delegates route to the same unified results.
 * Regression gate: spec §76-78.
 */
class CodeRuleEngineGoldenTest {

    private val concreteEcp = ConcreteMaterial(fcuMpa = 25.0)
    private val steelEcp = SteelMaterial(yieldMpa = 360.0, ultimateMpa = 520.0)
    private val concreteAci = ConcreteMaterial(fcuMpa = 25.0)
    private val steelAci = SteelMaterial(yieldMpa = 420.0, ultimateMpa = 600.0)

    @Test
    fun `ECP engine bundles all sub-engines`() {
        val engine = CodeRuleEngine.forEcp(concreteEcp, steelEcp)
        assertNotNull(engine.beamFlexure)
        assertNotNull(engine.beamShear)
        assertNotNull(engine.beamDeflection)
        assertNotNull(engine.crackControl)
        assertNotNull(engine.beamTorsion)
        assertNotNull(engine.column)
        assertNotNull(engine.slab)
    }

    @Test
    fun `ACI engine bundles all sub-engines`() {
        val engine = CodeRuleEngine.forAci(concreteAci, steelAci)
        assertNotNull(engine.beamFlexure)
        assertNotNull(engine.beamTorsion)
        assertNotNull(engine.column)
        assertNotNull(engine.slab)
    }

    @Test
    fun `designBeam delegate produces aggregated report`() {
        val engine = CodeRuleEngine.forEcp(concreteEcp, steelEcp)
        val out = engine.designBeam(
            b = 300.0, d = 450.0, h = 500.0, muKnm = 120.0, vuKn = 80.0,
            spanM = 6.0, support = SupportCondition.SIMPLY_SUPPORTED,
            tensionRatioPercent = 1.0, clearCoverMm = 30.0,
            tensionBarSpacingMm = 150.0, computedDeflectionMm = 10.0
        )
        assertEquals(CheckStatus.PASS, out.overallStatus)
        assertTrue(out.trace.all.isNotEmpty())
    }

    @Test
    fun `designColumn delegate produces aggregated report`() {
        val engine = CodeRuleEngine.forEcp(concreteEcp, steelEcp)
        val out = engine.designColumn(b = 400.0, h = 400.0, puKnm = 2000.0, vuKn = 50.0)
        assertEquals(CheckStatus.PASS, out.overallStatus)
    }

    @Test
    fun `designSlabTwoWay delegate produces aggregated report`() {
        val engine = CodeRuleEngine.forEcp(concreteEcp, steelEcp)
        val out = engine.designSlabTwoWay(shortSpanM = 5.0, longSpanM = 5.0, h = 200.0,
            totalLoadKnm2 = 10.0, allEdgesFixed = true)
        assertTrue(out.isSafe)
        assertTrue(out.trace.all.any { it.title.contains("coefficients") })
    }

    @Test
    fun `Same inputs give different but both-valid results per family`() {
        val ecp = CodeRuleEngine.forEcp(concreteEcp, steelEcp)
        val aci = CodeRuleEngine.forAci(concreteAci, steelAci)
        val ecpOut = ecp.designBeam(300.0, 450.0, 500.0, 120.0, 80.0, 6.0,
            SupportCondition.SIMPLY_SUPPORTED, 1.0, 30.0, 150.0, computedDeflectionMm = 10.0)
        val aciOut = aci.designBeam(300.0, 450.0, 500.0, 100.0, 60.0, 5.0,
            SupportCondition.CONTINUOUS, 0.8, 40.0, 140.0, computedDeflectionMm = 8.0)
        assertEquals(CheckStatus.PASS, ecpOut.overallStatus)
        assertEquals(CheckStatus.PASS, aciOut.overallStatus)
    }

    @Test
    fun `designBeamTorsion routes to unified torsion engine`() {
        val engine = CodeRuleEngine.forEcp(concreteEcp, steelEcp)
        val out = engine.designBeamTorsion(b = 300.0, h = 600.0, coverMm = 40.0, tuKnm = 2.0)
        assertEquals(UnifiedBeamTorsion.TorsionState.MINIMUM_ONLY, out.torsionState)
        assertTrue(out.isSafe)
    }

    @Test
    fun `designBeam with tuKnm includes torsion in aggregated report`() {
        val engine = CodeRuleEngine.forEcp(concreteEcp, steelEcp)
        val out = engine.designBeam(
            b = 300.0, d = 560.0, h = 600.0, muKnm = 120.0, vuKn = 0.0,
            spanM = 6.0, support = SupportCondition.SIMPLY_SUPPORTED,
            tensionRatioPercent = 1.0, clearCoverMm = 40.0,
            tensionBarSpacingMm = 150.0, tuKnm = 2.0
        )
        assertNotNull(out.torsion)
        assertEquals(UnifiedBeamTorsion.TorsionState.MINIMUM_ONLY, out.torsion!!.torsionState)
        assertTrue(out.trace.all.any { it.title.contains("Torsion") })
    }
}
