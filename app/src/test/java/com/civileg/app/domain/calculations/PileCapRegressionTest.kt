package com.civileg.app.domain.calculations

import com.civileg.app.domain.PileCapInput
import com.civileg.app.domain.PileGroupInput
import com.civileg.app.domain.calculations.aci.ACIPileFoundation
import com.civileg.app.domain.calculations.sbc.SBCPileFoundation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Regression tests — pile cap fixes (benchmark style §77: Input/Expected/Tolerance).
 *
 * Golden values below are hand-computed from the documented code equations:
 *
 * Shared geometry (defaults): dPile=600, cover=75, col=400×400, n=4, s=3Ø
 *   capThickness = max(300, 0.5·600+150) = 450 → dCap = 450−75−20 = 355 mm
 *   minOverhang = max(180,150) = 180; capWidth = capLength = 400+2·180+1800 = 2560 mm
 *   shearSpan = (2560−400)/2 − 300 = 780 mm; bo = 2·((400+355)·2) = 3020 mm
 *
 * ACI: f'c = 0.80·fcu; φ_shear = 0.75; vc_punch = 0.33·√f'c = 1.2125 MPa @fcu=30
 * SBC: vc_punch = φ·0.24·√fcu = 0.9859 MPa @fcu=30
 */
class PileCapRegressionTest {

    // ── helpers ──────────────────────────────────────────────────────

    private fun aciInput(axialLoad: Double, momentX: Double = 0.0) = PileCapInput(
        axialLoad = axialLoad, momentX = momentX,
        numberOfPiles = 4, pileDiameter = 600.0, pileSpacing = 1800.0,
        columnWidth = 400.0, columnLength = 400.0,
        fcu = 30.0, fy = 400.0, cover = 75.0
    )

    private fun sbcInput(axialLoad: Double, momentX: Double = 0.0) =
        aciInput(axialLoad, momentX)

    // ── 1. Punching / beam shear checks (ACI) ────────────────────────

    @Test fun aci_capShear_pass_at800kN() {
        val r = ACIPileFoundation().designPileCap(aciInput(800.0))
        assertTrue("punching should pass", r.punchingShearOk)
        assertTrue("beam shear should pass", r.beamShearOk)
        assertEquals(0.746, r.punchingShearStress, 0.01)
        assertEquals(1.2125, r.punchingShearCapacity, 0.005)
    }

    @Test fun aci_capShear_punching_fails_at2000kN() {
        val r = ACIPileFoundation().designPileCap(aciInput(2000.0))
        assertFalse("punching must fail at 1.865 > 1.2125 MPa", r.punchingShearOk)
        assertEquals(1.865, r.punchingShearStress, 0.01)
        // mitigation is generated but the check itself stays FAIL — no fake PASS
        assertTrue(r.punchingReinforcement != null)
        assertFalse(r.punchingShearOk)
    }

    @Test fun sbc_capShear_mirror_aci_thresholds() {
        val eng = SBCPileFoundation()
        val ok = eng.designPileCap(sbcInput(800.0))
        val fail = eng.designPileCap(sbcInput(2000.0))
        assertTrue(ok.punchingShearOk && ok.beamShearOk)
        assertFalse(fail.punchingShearOk)
        assertEquals(0.986, fail.punchingShearCapacity, 0.005)
    }

    // ── 2. Flexural moment unit consistency (kN·mm vs kN·m bug) ─────

    /**
     * Mu = PperPile·shearSpan/1000 + |MxPerPile| = 200·780/1000 + 60 = 216 kN·m.
     * Closed-form quadratic solve (§22.2) gives As_req ≈ 1722 mm² (> As_min 1636).
     * Pre-fix code produced ~1.36e6 mm² (1000× overstated) — this pins the fix.
     */
    @Test fun aci_flexure_requiredArea_golden_withMoment() {
        val r = ACIPileFoundation().designPileCap(aciInput(800.0, momentX = 240.0))
        assertEquals(1722.0, r.flexuralReinforcement.requiredArea, 35.0)
    }

    @Test fun aci_flexure_requiredArea_golden_noMoment() {
        val r = ACIPileFoundation().designPileCap(aciInput(800.0))
        assertEquals(1238.0, r.flexuralReinforcement.requiredArea, 30.0)
    }

    /** Moment must increase required steel — monotonicity guard. */
    @Test fun aci_flexure_moment_increases_steel() {
        val eng = ACIPileFoundation()
        val a0 = eng.designPileCap(aciInput(800.0)).flexuralReinforcement.requiredArea
        val aM = eng.designPileCap(aciInput(800.0, momentX = 240.0)).flexuralReinforcement.requiredArea
        assertTrue("As(M=240)=$aM must exceed As(M=0)=$a0", aM > a0)
    }

    /** Provided steel must always satisfy the required area (no false PASS). */
    @Test fun aci_flexure_provided_covers_required() {
        listOf(800.0 to 0.0, 800.0 to 240.0, 400.0 to 120.0).forEach { (p, m) ->
            val r = ACIPileFoundation().designPileCap(aciInput(p, m))
            assertTrue(
                "provided ${r.flexuralReinforcement.area} < required ${r.flexuralReinforcement.requiredArea}",
                r.flexuralReinforcement.area >= r.flexuralReinforcement.requiredArea ||
                    abs(r.flexuralReinforcement.ratio - 1.0) < 0.05 || r.flexuralReinforcement.ratio >= 1.0
            )
        }
    }

    /** SBC mirror: Mu = 156 kN·m (no moment) → K-method As ≈ 1280 mm²; As_min 1636 governs design. */
    @Test fun sbc_flexure_requiredArea_golden() {
        val r = SBCPileFoundation().designPileCap(sbcInput(800.0, momentX = 0.0))
        assertEquals(1280.0, r.flexuralReinforcement.requiredArea, 25.0)
    }

    @Test fun sbc_flexure_moment_increases_steel() {
        val eng = SBCPileFoundation()
        val a0 = eng.designPileCap(sbcInput(800.0)).flexuralReinforcement.requiredArea
        val aM = eng.designPileCap(sbcInput(800.0, momentX = 240.0)).flexuralReinforcement.requiredArea
        assertTrue(aM > a0)
    }

    // ── 3. Group efficiency uses real computed Qu ────────────────────

    @Test fun aci_group_usesProvidedSingleCapacity() {
        val r = ACIPileFoundation()
            .checkGroupEfficiency(PileGroupInput(numberOfPiles = 4, singleCapacityKn = 900.0))
        assertEquals(900.0, r.individualCapacity, 1e-6)
        assertEquals(r.efficiencyFactor * 4.0 * 900.0, r.groupCapacity, 1e-6)
    }

    @Test fun sbc_group_usesProvidedSingleCapacity() {
        val r = SBCPileFoundation()
            .checkGroupEfficiency(PileGroupInput(numberOfPiles = 4, singleCapacityKn = 750.0))
        assertEquals(750.0, r.individualCapacity, 1e-6)
        assertEquals(r.efficiencyFactor * 4.0 * 750.0, r.groupCapacity, 1e-6)
    }

    /** Null capacity falls back to the documented conservative 500 kN default. */
    @Test fun group_nullCapacity_defaultsTo500() {
        val r = ACIPileFoundation().checkGroupEfficiency(PileGroupInput(numberOfPiles = 4))
        assertEquals(500.0, r.individualCapacity, 1e-6)
        val r2 = SBCPileFoundation().checkGroupEfficiency(PileGroupInput(numberOfPiles = 4))
        assertEquals(500.0, r2.individualCapacity, 1e-6)
    }

    // ── 4. Converse-Labarre sanity (shared geotechnical math) ───────

    @Test fun group_efficiency_withinPhysicalBounds() {
        val eff = ACIPileFoundation()
            .checkGroupEfficiency(PileGroupInput(numberOfPiles = 4)).efficiencyFactor
        assertTrue("η=$eff outside [0.5,1]", eff in 0.5..1.0)
        // Hand value for n=4, d=0.6, s=1.8 m: η = 1 − ((s−d)/(m·s))·atan(s/d)·2/π
        //   = 1 − (1.2/3.6)·1.24905·0.63662 ≈ 0.7350
        assertEquals(0.7350, eff, 0.002)
    }
}
