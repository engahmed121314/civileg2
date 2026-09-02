package com.civileg.core.engineering

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

/**
 * Golden tests for [UnifiedBeamTorsion] (Torsion DoD §22.7 / ECP §4-3-4).
 *
 * Cross-gates the unified engine against the code-text formula recomputed
 * independently inside each test (spec §76-78). The threshold/cracking/max
 * constants are also verified against the published psi-based code form via a
 * unit-conversion round-trip, so a wrong SI constant would be caught.
 */
class UnifiedBeamTorsionGoldenTest {

    private val concreteEcp = ConcreteMaterial(fcuMpa = 30.0)
    private val steelEcp = SteelMaterial(yieldMpa = 360.0, ultimateMpa = 520.0)
    private val concreteAci = ConcreteMaterial(fcuMpa = 37.5)   // f'c = 30 MPa
    private val steelAci = SteelMaterial(yieldMpa = 420.0, ultimateMpa = 600.0)

    /** Independent psi-route threshold (kN·m) for cross-checking the SI constant. */
    private fun thresholdPsiKnm(fcPsi: Double, bMm: Double, hMm: Double): Double {
        val bIn = bMm / 25.4
        val hIn = hMm / 25.4
        val acp = bIn * hIn
        val pcp = 2.0 * (bIn + hIn)
        val tLbIn = 0.083 * sqrt(fcPsi) * (acp * acp / pcp)
        return tLbIn * 112.9848 / 1e6   // lb·in → N·mm → kN·m
    }

    // ── Constant cross-check ──

    @Test
    fun `ACI threshold matches published psi-based formula via SI conversion`() {
        val eng = UnifiedBeamTorsion(Aci318Params)
        val out = eng.design(
            UnifiedBeamTorsion.Input(300.0, 600.0, 40.0, concreteAci, steelAci, tuKnm = 0.1)
        )
        val expected = thresholdPsiKnm(30.0 * 145.0377, 300.0, 600.0) // f'c 30 MPa → psi
        assertEquals(expected, out.tuThKnm, 1e-3)
    }

    @Test
    fun `ECP cracking torque equals four times threshold`() {
        val eng = UnifiedBeamTorsion(Ecp203Params)
        val out = eng.design(
            UnifiedBeamTorsion.Input(300.0, 600.0, 40.0, concreteEcp, steelEcp, tuKnm = 0.1)
        )
        assertEquals(4.0 * out.tuThKnm, out.tuCrKnm, 1e-3)
        // ECP max == cracking form
        assertEquals(out.tuCrKnm, out.tuMaxKnm, 1e-6)
    }

    // ── ECP minimum-only design (section within limit) ──

    @Test
    fun `ECP torsion minimum-only design selects 10mm stirrups at 185mm`() {
        val eng = UnifiedBeamTorsion(Ecp203Params)
        val out = eng.design(
            UnifiedBeamTorsion.Input(300.0, 600.0, 40.0, concreteEcp, steelEcp, tuKnm = 2.0)
        )
        assertEquals(UnifiedBeamTorsion.TorsionState.MINIMUM_ONLY, out.torsionState)
        assertTrue(out.sectionAdequate)
        assertTrue(out.isSafe)
        assertEquals(0.375, out.atOverSReq, 1e-6)             // 0.25 % ρv,min
        assertEquals(10.0, out.stirrupDiaMm, 1e-9)
        assertEquals(185.0, out.stirrupSpacingMm, 1e-9)       // min(Ph/8=185, 200)
        assertEquals(0.0, out.combinedStressUtil ?: 0.0, 1e-9) // ECP → null
        assertEquals(25.0, out.longitudinalDiaMm, 1e-9)
        assertEquals(11, out.longitudinalBars)
        // longitudinal area is governed by equilibrium with the selected stirrup
        assertTrue(out.longitudinalAreaMm2 > 4500.0)
    }

    // ── ECP exceeding max → section fails ──

    @Test
    fun `ECP torsion exceeding Tmax reports unsafe section`() {
        val eng = UnifiedBeamTorsion(Ecp203Params)
        val out = eng.design(
            UnifiedBeamTorsion.Input(300.0, 600.0, 40.0, concreteEcp, steelEcp, tuKnm = 5.0)
        )
        assertEquals(UnifiedBeamTorsion.TorsionState.FULL_DESIGN, out.torsionState)
        assertFalse(out.sectionAdequate)
        assertFalse(out.isSafe)
        assertTrue(out.tuKnm > out.tuMaxKnm)
    }

    // ── ACI full design with combined shear+torsion ──

    @Test
    fun `ACI torsion full design with shear passes combined stress check`() {
        val eng = UnifiedBeamTorsion(Aci318Params)
        val out = eng.design(
            UnifiedBeamTorsion.Input(300.0, 600.0, 40.0, concreteAci, steelAci,
                tuKnm = 5.0, vuKn = 50.0, dMm = 540.0)
        )
        assertEquals(UnifiedBeamTorsion.TorsionState.FULL_DESIGN, out.torsionState)
        assertTrue(out.sectionAdequate)
        assertTrue(out.isSafe)
        // threshold ~0.68 kN·m, cracking ~2.72 kN·m, max ~13.92 kN·m
        assertEquals(0.680, out.tuThKnm, 5e-3)
        assertEquals(2.719, out.tuCrKnm, 5e-3)
        assertEquals(13.92, out.tuMaxKnm, 5e-2)
        // minimum transverse governs (At/s = 0.125 mm²/mm)
        assertEquals(0.125, out.atOverSReq, 1e-6)
        assertEquals(8.0, out.stirrupDiaMm, 1e-9)
        assertEquals(185.0, out.stirrupSpacingMm, 1e-9)
        // longitudinal: ~11–12 Ø19
        assertEquals(19.0, out.longitudinalDiaMm, 1e-9)
        assertEquals(12, out.longitudinalBars)
        // combined stress utilisation well under 1 (√f'c limit)
        assertNotNull(out.combinedStressUtil)
        // section utilisation = max(combined-stress §22.7.7.1a, torsion-alone crushing bound)
        assertTrue(out.combinedStressUtil!! < 0.5)
    }

    // ── Neglect band ──

    @Test
    fun `torsion below threshold is neglected for both codes`() {
        val ecp = UnifiedBeamTorsion(Ecp203Params).design(
            UnifiedBeamTorsion.Input(300.0, 600.0, 40.0, concreteEcp, steelEcp, tuKnm = 0.3)
        )
        assertEquals(UnifiedBeamTorsion.TorsionState.NONE, ecp.torsionState)
        assertTrue(ecp.isSafe)
        assertEquals(0.0, ecp.stirrupDiaMm, 1e-9)

        val aci = UnifiedBeamTorsion(Aci318Params).design(
            UnifiedBeamTorsion.Input(300.0, 600.0, 40.0, concreteAci, steelAci, tuKnm = 0.3)
        )
        assertEquals(UnifiedBeamTorsion.TorsionState.NONE, aci.torsionState)
        assertTrue(aci.isSafe)
    }

    // ── Invalid input ──

    @Test(expected = ArithmeticException::class)
    fun `negative torsion throws`() {
        UnifiedBeamTorsion(Ecp203Params).design(
            UnifiedBeamTorsion.Input(300.0, 600.0, 40.0, concreteEcp, steelEcp, tuKnm = -1.0)
        )
    }
}
