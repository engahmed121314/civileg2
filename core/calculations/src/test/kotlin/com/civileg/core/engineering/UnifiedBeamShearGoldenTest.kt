package com.civileg.core.engineering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP 5 extension gates — unified shear reproduces BOTH legacy benchmarks:
 * ECP S1 (Vu=150 → Vc=122.47, min 375 governs, Ø10@200, util 0.42)
 * ACI A2 (Vu=180 → Vc=134.92 φ-included, Av/s=463.4, Ø10, cap 270, util 0.3643)
 */
class UnifiedBeamShearGoldenTest {

    private val conc25 = ConcreteMaterial(fcuMpa = 25.0)
    private val conc30 = ConcreteMaterial(fcuMpa = 30.0)
    private val steel360 = SteelMaterial(yieldMpa = 360.0, ultimateMpa = 520.0)
    private val steel420 = SteelMaterial(yieldMpa = 420.0, ultimateMpa = 620.0)

    @Test
    fun `ECP S1 - capacity min-governed stirrups and spacing cap`() {
        val r = UnifiedBeamShear(Ecp203Params, conc25, steel360)
            .design(b = 250.0, d = 500.0, vuKn = 150.0)
        assertEquals(122.47, r.concreteCapacityKn, 1.23)         // ±1%
        assertEquals(375.0, r.asPerMeterMm2, 4.0)                // 0.0015·250·1000
        assertEquals(10.0, r.stirrupDiaMm, 1e-9)                 // width ≥ 250 policy
        assertEquals(200.0, r.spacingMm, 1e-6)                   // family cap
        assertTrue(r.isSafe)
        assertEquals(0.42, r.utilization, 0.01)                  // Vu / qmax·bd
        assertEquals(CheckStatus.WARNING, r.trace.overall)       // Vu>Vc -> stirrups needed
        assertTrue(r.minGoverns)
    }

    @Test
    fun `ECP below-capacity case passes without warning`() {
        val r = UnifiedBeamShear(Ecp203Params, conc25, steel360)
            .design(b = 250.0, d = 500.0, vuKn = 100.0)
        assertTrue(r.isSafe)
        assertEquals(CheckStatus.PASS, r.trace.overall)
        assertEquals(200.0, r.spacingMm, 1e-6)                   // default cap spacing
    }

    @Test
    fun `ECP beyond absolute cap fails loudly`() {
        val r = UnifiedBeamShear(Ecp203Params, conc25, steel360)
            .design(b = 250.0, d = 500.0, vuKn = 500.0)          // > qmax capacity 357.2
        assertFalse(r.isSafe)
        assertEquals(CheckStatus.FAIL, r.trace.overall)
        assertEquals(357.22, r.maxCapacityKn, 1.5)
    }

    @Test
    fun `ACI A2 - phiVc required steel and d-over-2 spacing cap`() {
        val r = UnifiedBeamShear(Aci318Params, conc30, steel420)
            .design(b = 300.0, d = 540.0, vuKn = 180.0)
        // φVc = 0.75×0.17×√24×162000/1000 = 101.19 kN ; legacy concreteShearCapacity reported Vc itself
        assertEquals(101.19, r.concreteCapacityKn, 1.0)
        // Vs=(180−101.19)/0.75=105.08 kN -> As/s = 105.08e6/(420·540)·1e-3? hand: 463.4 mm²/m
        assertEquals(463.4, r.asPerMeterMm2, 5.0)
        assertFalse(r.minGoverns)                                 // min is only 250 mm²/m
        assertEquals(10.0, r.stirrupDiaMm, 1e-9)                  // Vu > ½φVc
        assertEquals(270.0, r.spacingMm, 1e-6)                    // min(339, d/2=270)
        // max cap: φ(Vc + 0.66√f'c bd) = 494.04 kN -> util 0.3643
        assertEquals(0.3643, r.utilization, 0.008)
        assertTrue(r.isSafe)
        assertEquals(CheckStatus.WARNING, r.trace.overall)
    }

    @Test
    fun `ACI low shear takes minimum reinforcement path`() {
        val r = UnifiedBeamShear(Aci318Params, conc30, steel420)
            .design(b = 300.0, d = 540.0, vuKn = 80.0)           // φVc/2=50.6 < 80 ≤ φVc=101.19
        assertEquals(250.0, r.asPerMeterMm2, 2.0)                 // max(0.062√24·300/420, 0.35·300/420)e3
        assertTrue(r.minGoverns)
        assertEquals(10.0, r.stirrupDiaMm, 1e-9)                  // Vu > ½φVc -> Ø10 policy
        // below concrete capacity -> PASS; prescriptive minima are defaults, not warnings
        assertEquals(CheckStatus.PASS, r.trace.overall)
        assertTrue(r.isSafe)
    }
}
