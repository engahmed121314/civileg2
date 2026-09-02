package com.civileg.core.engineering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP 5 golden gates — unified flexure engine, both families, hand-derived.
 * ECP numbers mirror engineering/BeamGoldenBenchmarkTest E1;
 * ACI numbers mirror its A1 — one engine now reproduces both benchmark sets.
 */
class UnifiedBeamFlexureGoldenTest {

    private val conc25 = ConcreteMaterial(fcuMpa = 25.0)
    private val conc30 = ConcreteMaterial(fcuMpa = 30.0)
    private val steel360 = SteelMaterial(yieldMpa = 360.0, ultimateMpa = 520.0)
    private val steel420 = SteelMaterial(yieldMpa = 420.0, ultimateMpa = 620.0)

    // ---------------- ECP 203 family ----------------

    @Test
    fun `ECP E1 - K-method matches STEP-1 benchmark`() {
        val r = UnifiedBeamFlexure(Ecp203Params, conc25, steel360)
            .design(b = 250.0, d = 500.0, h = 560.0, muKnm = 200.0)
        assertEquals(1545.83, r.asRequiredMm2, 15.5)   // ±1% of hand value
        assertEquals("5Ø20", r.bars)
        assertEquals(1570.8, r.asProvidedMm2, 1.0)
        assertEquals(CheckStatus.PASS, r.trace.overall)
        assertEquals(null, r.governingNote)
    }

    @Test
    fun `ECP minimum steel uses corrected post-A10 pair and warns`() {
        val r = UnifiedBeamFlexure(Ecp203Params, conc25, steel360)
            .design(b = 250.0, d = 500.0, h = 560.0, muKnm = 5.0)
        assertEquals(434.03, r.asRequiredMm2, 4.3)     // max(0.25√25/360, 0.0013)*125000
        assertEquals(CheckStatus.WARNING, r.trace.overall)
        assertEquals("minimum steel governs", r.governingNote)
    }

    @Test
    fun `ECP over-reinforced demand routes loudly to doubly path`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            UnifiedBeamFlexure(Ecp203Params, conc25, steel360)
                .design(b = 250.0, d = 500.0, h = 560.0, muKnm = 400.0)
        }
        assertTrue(ex.message!!.contains("doubly"))
    }

    @Test
    fun `ECP parameters reproduce documented K_bal at fcu25-fy360`() {
        assertEquals(0.18605, Ecp203Params.kBalanced(steel360), 5e-5)
    }

    // ---------------- ACI 318 family ----------------

    @Test
    fun `ACI A1 - Rn-rho matches STEP-1 benchmark`() {
        val r = UnifiedBeamFlexure(Aci318Params, conc30, steel420)
            .design(b = 300.0, d = 540.0, h = 600.0, muKnm = 300.0)
        assertEquals(1640.7, r.asRequiredMm2, 16.4)    // ±1%
        assertEquals("6Ø19", r.bars)
        assertEquals(1701.2, r.asProvidedMm2, 1.0)
        assertEquals(CheckStatus.PASS, r.trace.overall)
    }

    @Test
    fun `ACI minimum steel governed by 1_4 over fy term`() {
        val r = UnifiedBeamFlexure(Aci318Params, conc30, steel420)
            .design(b = 300.0, d = 540.0, h = 600.0, muKnm = 10.0)
        assertEquals(540.0, r.asRequiredMm2, 0.5)      // max(0.25√24/420, 1.4/420)*162000
        assertEquals(CheckStatus.WARNING, r.trace.overall)
    }

    // ---------------- Pipeline: combinations -> flexure ----------------

    @Test
    fun `envelope feeds the engine end-to-end with citation carried into traces`() {
        val governing = LoadCombinations.envelopeMax(LoadCombinations.ecp203Gravity(), dead = 150.0, live = 60.0)
        // 1.4G=210 ; 1.2G+1.6Q=180+96=276 -> b governs
        assertEquals(276.0, governing.demand, 1e-9)

        val r = UnifiedBeamFlexure(Ecp203Params, conc25, steel360)
            .design(b = 300.0, d = 540.0, h = 600.0, muKnm = governing.demand, loadCombo =
                LoadCombinations.ecp203Gravity().first { it.name == governing.combinationName })
        assertTrue(r.trace.all.first().codeReference!!.contains("1.2G+1.6Q"))
        assertTrue(r.trace.all.first().codeReference!!.contains("ECP 203 §2-3-1-1(b)"))
        // K = 276e6/(25·300·540²) = 0.1264 < K_bal -> clean singly pass
        assertEquals(CheckStatus.PASS, r.trace.overall)
    }

    // ---------------- Minimum-moment (accidental eccentricity) gate ----------------

    @Test
    fun `minimum moment gate inert when Pu is zero`() {
        val r = UnifiedBeamFlexure(Ecp203Params, conc25, steel360)
            .design(b = 250.0, d = 500.0, h = 560.0, muKnm = 200.0)
        val gate = r.trace.all.first { it.title.contains("Minimum-moment") }
        assertTrue(gate.result.contains("200"))
        assertEquals(null, r.governingNote)
    }

    @Test
    fun `ECP minimum moment Mu min governs a small applied moment`() {
        // h=560 -> e_min=15+16.8=31.8 mm ; Pu=2000 kN = 2.0e6 N -> Mu,min = 2.0e6·31.8 / 1e6 = 63.6 kN·m
        val r = UnifiedBeamFlexure(Ecp203Params, conc25, steel360)
            .design(b = 250.0, d = 500.0, h = 560.0, muKnm = 5.0, puN = 2_000_000.0)
        val gate = r.trace.all.first { it.title.contains("Minimum-moment") }
        assertTrue(gate.result.contains("63.6"))
        assertTrue("Mu,min must govern the design moment", r.governingNote!!.contains("minimum moment Mu,min governs"))
    }
}
