package com.civileg.app.engineering

import com.civileg.app.domain.calculations.BeamDesignEngine
import com.civileg.app.domain.calculations.ecp.ECPAdvancedBeam
import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.LoadCombination
import com.civileg.core.engineering.Aci318Params
import com.civileg.core.engineering.CodeRuleEngine
import com.civileg.core.engineering.ConcreteMaterial
import com.civileg.core.engineering.Ecp203Params
import com.civileg.core.engineering.SteelMaterial
import com.civileg.core.engineering.UnifiedBeamTorsion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 00 STEP 3 — Torsion cross-gate.
 *
 * Pins the engine that [com.civileg.app.domain.calculations.BeamDesignEngine]
 * now routes through (`CodeRuleEngine.designBeamTorsion`, the ECP 203 §4-3-4 /
 * ACI 318 §22.7 space-truss model) to code-text golden numbers derived by hand,
 * and documents the known divergence of the legacy `BeamDesignEnginePart2`
 * torsion routine (which uses a simplified b²·d threshold form instead of the
 * code-text Acp²/Pcp space-truss form — see BeamDesignEngine_Part2.designTorsion).
 */
class TorsionCrossGateTest {

    private val concreteEcp = ConcreteMaterial(fcuMpa = 30.0)
    private val steelEcp = SteelMaterial(yieldMpa = 360.0, ultimateMpa = 520.0)
    private val concreteAci = ConcreteMaterial(fcuMpa = 37.5)   // f'c = 30 MPa
    private val steelAci = SteelMaterial(yieldMpa = 420.0, ultimateMpa = 600.0)

    // ── ECP 203 §4-3-4 — minimum-only torsion (Tu between Tth and Tcr) ──

    @Test
    fun ecpTorsion_newEngine_minimumOnly_matchesCodeText() {
        val out = CodeRuleEngine.forEcp(concreteEcp, steelEcp).designBeamTorsion(
            b = 300.0, h = 600.0, coverMm = 40.0, tuKnm = 2.0
        )
        // Tth = 0.083√(fcu/γc)·(Acp²/Pcp) = 0.083·√20·(180000²/1800)/1e6 ≈ 0.555 kN·m
        assertEquals(0.555, out.tuThKnm, 5e-3)
        assertEquals(UnifiedBeamTorsion.TorsionState.MINIMUM_ONLY, out.torsionState)
        assertTrue(out.sectionAdequate)
        assertTrue(out.isSafe)
        // ρv,min = 0.25 % → At/s = 0.0025 · b = 0.75 mm²/mm, halved for closed = 0.375 mm²/mm
        assertEquals(0.375, out.atOverSReq, 1e-6)
        assertEquals(10.0, out.stirrupDiaMm, 1e-9)
        assertEquals(185.0, out.stirrupSpacingMm, 1e-9)   // min(Ph/8=185, 200)
        assertEquals(25.0, out.longitudinalDiaMm, 1e-9)
        assertEquals(11, out.longitudinalBars)
    }

    // ── ACI 318 §22.7 — full design with combined shear + torsion ──

    @Test
    fun aciTorsion_newEngine_fullDesign_matchesCodeText() {
        val out = CodeRuleEngine.forAci(concreteAci, steelAci).designBeamTorsion(
            b = 300.0, h = 600.0, coverMm = 40.0, tuKnm = 5.0, vuKn = 50.0, dMm = 540.0
        )
        assertEquals(UnifiedBeamTorsion.TorsionState.FULL_DESIGN, out.torsionState)
        assertTrue(out.sectionAdequate)
        assertTrue(out.isSafe)
        // Tth ≈ 0.680, Tcr ≈ 2.719, Tmax ≈ 13.92 kN·m (verified vs psi form in core suite)
        assertEquals(0.680, out.tuThKnm, 5e-3)
        assertEquals(2.719, out.tuCrKnm, 5e-3)
        assertEquals(13.92, out.tuMaxKnm, 5e-2)
        assertEquals(0.125, out.atOverSReq, 1e-6)         // ACI min transverse governs
        assertEquals(8.0, out.stirrupDiaMm, 1e-9)
        assertEquals(185.0, out.stirrupSpacingMm, 1e-9)
        assertEquals(19.0, out.longitudinalDiaMm, 1e-9)
        assertEquals(12, out.longitudinalBars)
        // §22.7.7.1a combined stress utilisation well under 1 (√f'c limit)
        assertTrue(out.combinedStressUtil!! < 0.5)
    }

    // ── Legacy divergence guard ──

    @Test
    fun sbcBeamDesign_routesTorsionThroughUnifiedEngine() {
        // SBC 304 is ACI-based → now routed through the unified ACI §22.7 engine (retired legacy).
        val r = BeamDesignEngine.designBeam(
            b = 300.0, h = 600.0, span = 6.0,
            deadLoad = 10.0, liveLoad = 10.0,
            fcu = 30.0, fy = 420.0, preferredDia = 20,
            code = DesignCode.SBC, supportType = "SS",
            cover = 40.0, torsionalMoment = 5.0
        )
        assertTrue(r.needsTorsionDesign)
        // fcu=30 → f'c=24 → ACI threshold ≈ 0.680·√(24/30) = 0.608 kN·m
        assertEquals(0.608, r.torsionalThreshold, 5e-3)
        assertEquals("12Ø19", r.torsionalLongitudinalBars)
        assertTrue(r.torsionalReinforcement.contains("Ø8"))
        assertEquals(185.0, r.torsionalStirrupSpacing, 1e-9)
        assertTrue(r.torsionIsSafe)
    }

    // ── ECPAdvancedBeam.designTorsion now delegates to the unified engine ──

    @Test
    fun ecpAdvancedBeamTorsion_delegatesToUnifiedEngine() {
        // ECPAdvancedBeam uses COVER = 50 mm internally; replicate the same input set.
        val cover = 50.0
        val ref = CodeRuleEngine.forEcp(
            ConcreteMaterial(fcuMpa = 30.0), SteelMaterial(yieldMpa = 360.0, ultimateMpa = 520.0)
        ).designBeamTorsion(
            b = 300.0, h = 600.0, coverMm = cover, tuKnm = 2.0, vuKn = 0.0, dMm = 600.0 - cover
        )
        val r = ECPAdvancedBeam().designTorsion(
            designTorque = 2.0, fcu = 30.0, fy = 360.0,
            width = 300.0, depth = 600.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        // Routed through the unified Acp²/Pcp model → code-text threshold, not the legacy b²·d form.
        assertEquals(ref.tuThKnm, r.thresholdTorque, 1e-9)
        assertEquals(ref.torsionState != UnifiedBeamTorsion.TorsionState.NONE, r.torsionRequired)
        assertEquals(ref.stirrupSpacingMm, r.stirrupSpacing, 1e-9)
        assertEquals(ref.longitudinalBars, r.longitudinalBars)
        assertEquals(ref.longitudinalDiaMm, r.longitudinalDiameter, 1e-9)
        assertEquals(ref.isSafe, r.isSafe)
    }
}
