package com.civileg.core.engineering

import com.civileg.core.calculations.entities.SupportCondition
import org.junit.Assert.*
import org.junit.Test

/**
 * Golden tests for [BeamDesignFacade] — full beam design integration.
 *
 * Verifies that flexure + shear + deflection + crack-control run together,
 * the aggregated trace has the correct number of entries, and the overall
 * status reflects the worst individual result.
 *
 * Regression gate: spec §76-78.
 */
class BeamDesignFacadeGoldenTest {

    // ── ECP 203 happy path: all four checks PASS ──

    @Test
    fun `ECP - full design all PASS`() {
        val facade = BeamDesignFacade(Ecp203Params, CONCRETE_ECP, STEEL_ECP)
        val result = facade.design(
            b = 300.0, d = 450.0, h = 500.0,
            muKnm = 120.0, vuKn = 80.0,
            spanM = 6.0, support = SupportCondition.SIMPLY_SUPPORTED,
            tensionRatioPercent = 1.0,
            clearCoverMm = 30.0, tensionBarSpacingMm = 150.0,
            computedDeflectionMm = 10.0 // L/250 = 24 mm → 10 < 24 PASS
        )
        assertEquals(CheckStatus.PASS, result.overallStatus)
        assertTrue(result.isSafe)
        // flexure PASS, shear PASS, deflection PASS, crack PASS → trace overall = PASS
        assertEquals(CheckStatus.PASS, result.trace.overall)
        // trace should have entries from all four sub-engines (≥ 6 entries)
        assertTrue("trace should have ≥6 entries, got ${result.trace.all.size}", result.trace.all.size >= 6)
    }

    @Test
    fun `ECP - shear WARNING degrades overall to WARNING`() {
        // Vu > Vc → shear needs stirrups → WARNING
        val facade = BeamDesignFacade(Ecp203Params, CONCRETE_ECP, STEEL_ECP)
        val result = facade.design(
            b = 300.0, d = 450.0, h = 500.0,
            muKnm = 120.0, vuKn = 150.0,
            spanM = 6.0, support = SupportCondition.SIMPLY_SUPPORTED,
            tensionRatioPercent = 1.0,
            clearCoverMm = 30.0, tensionBarSpacingMm = 150.0,
            computedDeflectionMm = 10.0
        )
        assertEquals(CheckStatus.WARNING, result.overallStatus)
        assertTrue(result.isSafe)
    }

    @Test
    fun `ECP - crack FAIL degrades overall to FAIL`() {
        // Crack spacing way above 300 mm limit
        val facade = BeamDesignFacade(Ecp203Params, CONCRETE_ECP, STEEL_ECP)
        val result = facade.design(
            b = 300.0, d = 450.0, h = 500.0,
            muKnm = 120.0, vuKn = 80.0,
            spanM = 6.0, support = SupportCondition.SIMPLY_SUPPORTED,
            tensionRatioPercent = 1.0,
            clearCoverMm = 30.0, tensionBarSpacingMm = 400.0,
            computedDeflectionMm = 10.0
        )
        assertEquals(CheckStatus.FAIL, result.overallStatus)
        assertFalse(result.isSafe)
    }

    @Test
    fun `ECP - deflection NOT_CHECKED degrades overall`() {
        // No computed deflection supplied → NOT_CHECKED on Layer 2
        val facade = BeamDesignFacade(Ecp203Params, CONCRETE_ECP, STEEL_ECP)
        val result = facade.design(
            b = 300.0, d = 450.0, h = 500.0,
            muKnm = 120.0, vuKn = 80.0,
            spanM = 6.0, support = SupportCondition.SIMPLY_SUPPORTED,
            tensionRatioPercent = 1.0,
            clearCoverMm = 30.0, tensionBarSpacingMm = 150.0,
            computedDeflectionMm = null
        )
        // span/depth may PASS, but computed deflection = NOT_CHECKED → overall = NOT_CHECKED
        assertEquals(CheckStatus.NOT_CHECKED, result.overallStatus)
        assertFalse(result.isSafe) // NOT_CHECKED degrades isSafe to false (only PASS/WARNING are safe)
    }

    @Test
    fun `ECP - deflection computed PASS when supplied`() {
        val facade = BeamDesignFacade(Ecp203Params, CONCRETE_ECP, STEEL_ECP)
        val result = facade.design(
            b = 300.0, d = 450.0, h = 500.0,
            muKnm = 120.0, vuKn = 80.0,
            spanM = 6.0, support = SupportCondition.SIMPLY_SUPPORTED,
            tensionRatioPercent = 1.0,
            clearCoverMm = 30.0, tensionBarSpacingMm = 150.0,
            computedDeflectionMm = 10.0 // L/250 = 24 mm → 10 < 24 PASS
        )
        assertEquals(CheckStatus.PASS, result.overallStatus)
    }

    // ── ACI 318 happy path ──

    @Test
    fun `ACI - full design all PASS`() {
        val facade = BeamDesignFacade(Aci318Params, CONCRETE_ACI, STEEL_ACI)
        // s_max = 380*(280/420) - 2.5*40 = 253.33 - 100 = 153.33 → spacing must be ≤ 153
        val result = facade.design(
            b = 300.0, d = 450.0, h = 500.0,
            muKnm = 100.0, vuKn = 60.0,
            spanM = 5.0, support = SupportCondition.CONTINUOUS,
            tensionRatioPercent = 0.8,
            clearCoverMm = 40.0, tensionBarSpacingMm = 140.0,
            computedDeflectionMm = 8.0 // L/250 = 20 mm → 8 < 20 PASS
        )
        assertEquals(CheckStatus.PASS, result.overallStatus)
        assertTrue(result.isSafe)
    }

    @Test
    fun `ACI - crack control uses ACI formula`() {
        // s_max = 380*(280/420) - 2.5*40 = 253.33 - 100 = 153.33
        // s_actual = 153.0 → PASS (just under)
        val facade = BeamDesignFacade(Aci318Params, CONCRETE_ACI, STEEL_ACI)
        val result = facade.design(
            b = 300.0, d = 450.0, h = 500.0,
            muKnm = 100.0, vuKn = 60.0,
            spanM = 5.0, support = SupportCondition.CONTINUOUS,
            tensionRatioPercent = 0.8,
            clearCoverMm = 40.0, tensionBarSpacingMm = 153.0,
            computedDeflectionMm = 8.0
        )
        assertEquals(CheckStatus.PASS, result.overallStatus)
    }

    @Test
    fun `ACI - crack control FAIL when spacing exceeds limit`() {
        // s_max ≈ 153.33; s_actual = 200 → FAIL
        val facade = BeamDesignFacade(Aci318Params, CONCRETE_ACI, STEEL_ACI)
        val result = facade.design(
            b = 300.0, d = 450.0, h = 500.0,
            muKnm = 100.0, vuKn = 60.0,
            spanM = 5.0, support = SupportCondition.CONTINUOUS,
            tensionRatioPercent = 0.8,
            clearCoverMm = 40.0, tensionBarSpacingMm = 200.0,
            computedDeflectionMm = 8.0
        )
        assertEquals(CheckStatus.FAIL, result.overallStatus)
        assertFalse(result.isSafe)
    }

    @Test
    fun `Facade trace is aggregated from all sub-engines`() {
        val facade = BeamDesignFacade(Ecp203Params, CONCRETE_ECP, STEEL_ECP)
        val result = facade.design(
            b = 300.0, d = 450.0, h = 500.0,
            muKnm = 120.0, vuKn = 80.0,
            spanM = 6.0, support = SupportCondition.SIMPLY_SUPPORTED,
            tensionRatioPercent = 1.0,
            clearCoverMm = 30.0, tensionBarSpacingMm = 150.0,
            computedDeflectionMm = 15.0
        )
        // Expected: flexure(5: K, steel, min-moment gate, min-steel gate, bar) + shear(3) + deflection(2) + crack(1) = 11 entries
        assertEquals("aggregated trace should have 11 entries", 11, result.trace.all.size)
        // Verify all sub-engine titles present
        val titles = result.trace.all.map { it.title }.toSet()
        assertTrue("missing flexure trace", titles.any { it.contains("Flexural") || it.contains("tension") })
        assertTrue("missing shear trace", titles.any { it.contains("Concrete shear") || it.contains("Shear reinforcement") })
        assertTrue("missing deflection trace", titles.any { it.contains("Span/depth") })
        assertTrue("missing crack trace", titles.any { it.contains("Crack") })
    }

    @Test
    fun `Facade with load combo labels trace`() {
        val combo = FactoredCombination("1.2G+1.6Q", mapOf(LoadType.DEAD to 1.2, LoadType.LIVE to 1.6), "ECP 203 §2-3-1-1(b)")
        val facade = BeamDesignFacade(Ecp203Params, CONCRETE_ECP, STEEL_ECP)
        val result = facade.design(
            b = 300.0, d = 450.0, h = 500.0,
            muKnm = 120.0, vuKn = 80.0,
            spanM = 6.0, support = SupportCondition.SIMPLY_SUPPORTED,
            tensionRatioPercent = 1.0,
            clearCoverMm = 30.0, tensionBarSpacingMm = 150.0,
            computedDeflectionMm = 10.0,
            loadCombo = combo
        )
        val flexEntry = result.trace.all.first { it.title.contains("Flexural") || it.title.contains("tension") }
        assertTrue("combo reference should appear", flexEntry.codeReference!!.contains("1.2G+1.6Q"))
    }

    // ── Torsion integration (§22.7 / ECP §4-3-4) ──

    @Test
    fun `ECP - torsion integrated into facade report (minimum-only)`() {
        val facade = BeamDesignFacade(Ecp203Params, CONCRETE_ECP, STEEL_ECP)
        val result = facade.design(
            b = 300.0, d = 560.0, h = 600.0,
            muKnm = 120.0, vuKn = 0.0,
            spanM = 6.0, support = SupportCondition.SIMPLY_SUPPORTED,
            tensionRatioPercent = 1.0,
            clearCoverMm = 40.0, tensionBarSpacingMm = 150.0,
            tuKnm = 2.0
        )
        assertNotNull(result.torsion)
        assertEquals(UnifiedBeamTorsion.TorsionState.MINIMUM_ONLY, result.torsion!!.torsionState)
        assertTrue(result.torsion!!.isSafe)
        // trace must contain torsion entries
        assertTrue(result.trace.all.any { it.title.contains("Torsion") })
    }

    @Test
    fun `ECP - torsion omitted when tuKnm not supplied`() {
        val facade = BeamDesignFacade(Ecp203Params, CONCRETE_ECP, STEEL_ECP)
        val result = facade.design(
            b = 300.0, d = 560.0, h = 600.0,
            muKnm = 120.0, vuKn = 80.0,
            spanM = 6.0, support = SupportCondition.SIMPLY_SUPPORTED,
            tensionRatioPercent = 1.0,
            clearCoverMm = 40.0, tensionBarSpacingMm = 150.0
        )
        assertNull(result.torsion)
    }

    // ── Test materials ──

    companion object {
        private val CONCRETE_ECP = ConcreteMaterial(fcuMpa = 25.0)
        private val STEEL_ECP = SteelMaterial(yieldMpa = 360.0, ultimateMpa = 520.0)
        private val CONCRETE_ACI = ConcreteMaterial(fcuMpa = 25.0)  // f'c = 20 MPa
        private val STEEL_ACI = SteelMaterial(yieldMpa = 420.0, ultimateMpa = 600.0)
    }
}
