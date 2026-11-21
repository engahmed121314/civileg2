package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.PileCapInput
import com.civileg.app.domain.PileGroupInput
import com.civileg.app.domain.PileInput
import com.civileg.app.domain.PileType
import com.civileg.app.domain.SoilType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Golden vectors for [ACIPileFoundation] (was 2.5% instruction-covered).
 *
 * Verification status per docs/CLAUSE_LOG.md (2026-11-21 batch):
 *  - P-01..P-03 (Nc=9, alpha shaft formula, API alpha curve): VERIFIED against
 *    public sources. Tests named `verified ...` assert independent hand-computed
 *    values from those clauses, not the engine's own arithmetic.
 *  - P-04 (punching vc three-case minimum): VERIFIED (ACI Table 22.6.5.2).
 *  - Everything else is CHARACTERIZATION: the test pins current behavior so any
 *    future change is loud, without claiming code-clause correctness.
 */
class ACIPileFoundationTest {

    private val engine = ACIPileFoundation()
    private val tol = 0.5 // percent tolerance for floating-point comparison

    private fun assertClose(expected: Double, actual: Double, what: String) {
        assertTrue(
            "$what: expected $expected but was $actual",
            kotlin.math.abs(actual - expected) <= kotlin.math.abs(expected) * tol / 100.0 + 1e-6
        )
    }

    // ---------------------------------------------------------------
    // 1. VERIFIED: single pile in clay, alpha method (P-01, P-02)
    // ---------------------------------------------------------------

    @Test
    fun `verified clay driven pile alpha band 1 shaft and Nc9 end bearing`() {
        // cu = 20 kPa -> alpha = 1.0 (soft-clay band, consistent with the API
        // cap alpha <= 1.0). D = 0.45 m, L = 12 m, water table at pile toe
        // (no correction). Hand computation:
        //   Qs = 1.0 * 20 * pi * 0.45 * 12 = 339.292 kN
        //   Qb = 9 * 20 * pi * 0.45^2 / 4 = 28.628 kN  (Skempton Nc = 9, P-01)
        //   Qu = 367.920 kN; Qa = Qu / 2.5 = 147.168 kN
        val input = PileInput(
            pileType = PileType.DRIVEN, pileDiameter = 450.0, pileLength = 12.0,
            soilType = SoilType.CLAY, cu = 20.0, waterTableDepth = 12.0,
            safetyFactor = 2.5, axialLoad = 100.0
        )
        val r = engine.calculatePileCapacity(input)
        assertClose(339.292, r.shaftResistance, "Qs")
        assertClose(28.628, r.endBearingResistance, "Qb")
        assertClose(367.920, r.ultimateCapacity, "Qu")
        assertClose(147.168, r.allowableCapacity, "Qa")
        assertClose(100.0 / 147.168, r.utilizationRatio, "utilization")
    }

    @Test
    fun `verified clay alpha high-strength band matches API 0p5 sqrt form`() {
        // P-02: for cu > 100 kPa the engine uses alpha = 0.5*sqrt(100/cu),
        // the API RP 2A-WSD 6.4.2 form 0.5*psi^-0.5 with p'0 = 100 kPa.
        // cu = 150 -> alpha = 0.5*sqrt(100/150) = 0.408248
        val input = PileInput(
            pileType = PileType.DRIVEN, pileDiameter = 450.0, pileLength = 12.0,
            soilType = SoilType.CLAY, cu = 150.0, waterTableDepth = 12.0,
            safetyFactor = 2.5, axialLoad = 0.0
        )
        val r = engine.calculatePileCapacity(input)
        val alpha = 0.5 * sqrt(100.0 / 150.0)
        val expectedQs = alpha * 150.0 * PI * 0.45 * 12.0
        assertClose(expectedQs, r.shaftResistance, "Qs high band")
        assertClose(0.408248, r.shaftResistance / (150.0 * PI * 0.45 * 12.0), "alpha")
    }

    @Test
    fun `verified bored pile alpha is 0p7 of driven`() {
        // Pile-type installation factor, engine constant (characterized factor,
        // verified dispatch): BORED applies 0.7 x the DRIVEN alpha.
        val base = PileInput(
            pileDiameter = 450.0, pileLength = 12.0, soilType = SoilType.CLAY,
            cu = 20.0, waterTableDepth = 12.0, safetyFactor = 2.5, axialLoad = 0.0
        )
        val driven = engine.calculatePileCapacity(base.copy(pileType = PileType.DRIVEN))
        val bored = engine.calculatePileCapacity(base.copy(pileType = PileType.BORED))
        assertClose(driven.shaftResistance * 0.7, bored.shaftResistance, "bored Qs")
        assertClose(driven.endBearingResistance, bored.endBearingResistance, "Qb unchanged")
    }

    // ---------------------------------------------------------------
    // 2. CHARACTERIZATION: alpha middle band + the cu = 50 kPa jump
    // ---------------------------------------------------------------

    @Test
    fun `characterization alpha middle band and the 50 kPa discontinuity`() {
        // KNOWN DEFECT (CLAUSE_LOG U-P1): the middle band alpha = 1-(cu-25)/100
        // reaches 0.75 at cu = 50-, while the 50..100 band pins alpha = 0.5.
        // That is a 33% capacity drop for a 0.2 kPa change in cu. This test
        // pins the current behavior; the discontinuity needs an engineering
        // decision (smooth the curve), NOT a silent fix.
        fun alphaAt(cu: Double): Double {
            val r = engine.calculatePileCapacity(
                PileInput(
                    pileType = PileType.DRIVEN, pileDiameter = 450.0, pileLength = 12.0,
                    soilType = SoilType.CLAY, cu = cu, waterTableDepth = 12.0,
                    safetyFactor = 2.5, axialLoad = 0.0
                )
            )
            return r.shaftResistance / (cu * PI * 0.45 * 12.0)
        }
        assertClose(0.751, alphaAt(49.9), "alpha(49.9)")
        assertClose(0.5, alphaAt(50.1), "alpha(50.1)")
        assertClose(0.85, alphaAt(40.0), "alpha(40)")
    }

    // ---------------------------------------------------------------
    // 3. CHARACTERIZATION: sand beta method with Berezantzev Nq
    // ---------------------------------------------------------------

    @Test
    fun `characterization sand driven pile beta method`() {
        // K = 1.0 (driven), beta = tan(32 deg), avg effective stress = gamma*L/2
        // Nq = exp(pi*tan(phi)) * tan(45 + phi/2)^2 (Berezantzev)
        val phi = 32.0
        val input = PileInput(
            pileType = PileType.DRIVEN, pileDiameter = 400.0, pileLength = 10.0,
            soilType = SoilType.SAND, phi = phi, gammaSoil = 18.0,
            waterTableDepth = 10.0, safetyFactor = 3.0, axialLoad = 0.0
        )
        val r = engine.calculatePileCapacity(input)
        val beta = tan(phi * PI / 180.0)
        val expectedQs = beta * (18.0 * 10.0 / 2.0) * PI * 0.4 * 10.0
        val nq = exp(PI * tan(phi * PI / 180.0)) * tan(PI / 4 + phi * PI / 360.0).pow(2)
        val expectedQb = nq * 18.0 * 10.0 * PI * 0.4 * 0.4 / 4.0
        assertClose(expectedQs, r.shaftResistance, "Qs sand")
        assertClose(expectedQb, r.endBearingResistance, "Qb sand")
        assertClose((expectedQs + expectedQb) / 3.0, r.allowableCapacity, "Qa sand")
    }

    // ---------------------------------------------------------------
    // 4. CHARACTERIZATION: rock socket + water table correction
    // ---------------------------------------------------------------

    @Test
    fun `characterization rock socket capacity`() {
        // Qs = 0.2*cu*pi*D*embedment; Qb = 5*cu*pi*D^2/4 (cu used as rock strength)
        val input = PileInput(
            pileType = PileType.BORED, pileDiameter = 600.0, pileLength = 8.0,
            soilType = SoilType.ROCK, cu = 2000.0, embedmentDepth = 1.5,
            waterTableDepth = 8.0, safetyFactor = 3.0, axialLoad = 0.0
        )
        val r = engine.calculatePileCapacity(input)
        assertClose(0.2 * 2000.0 * PI * 0.6 * 1.5, r.shaftResistance, "Qs rock")
        assertClose(2000.0 * 5.0 * PI * 0.36 / 4.0, r.endBearingResistance, "Qb rock")
    }

    @Test
    fun `characterization water table correction factors`() {
        // submerged ratio 0.5 -> Qs x (1-0.5*0.5)=0.75, Qb x (1-0.5)=0.5
        val dry = engine.calculatePileCapacity(
            PileInput(
                pileType = PileType.DRIVEN, pileDiameter = 450.0, pileLength = 12.0,
                soilType = SoilType.CLAY, cu = 20.0, waterTableDepth = 12.0,
                safetyFactor = 2.5, axialLoad = 0.0
            )
        )
        val wet = engine.calculatePileCapacity(
            PileInput(
                pileType = PileType.DRIVEN, pileDiameter = 450.0, pileLength = 12.0,
                soilType = SoilType.CLAY, cu = 20.0, waterTableDepth = 6.0,
                safetyFactor = 2.5, axialLoad = 0.0
            )
        )
        assertClose(dry.shaftResistance * 0.75, wet.shaftResistance, "Qs wet")
        assertClose(dry.endBearingResistance * 0.5, wet.endBearingResistance, "Qb wet")
    }

    // ---------------------------------------------------------------
    // 5. VERIFIED: pile cap structural design (P-04 + ACI 22.5.5 + Rn-rho)
    // ---------------------------------------------------------------

    @Test
    fun `verified pile cap geometry punching beam shear and flexure`() {
        // Hand computation (spreadsheet-checked):
        // minOverhang = max(600*0.3, 150) = 180; n > 2 ->
        //   capWidth = capLength = 400 + 2*180 + 1800 = 2560 mm
        //   capThickness = max(300, 300+150) = 450; dCap = 450-75-20 = 355
        //   fc = 30*0.8 = 24 MPa; sqrt(fc) = 4.89898
        // Punching (Table 22.6.5.2, beta = 1, alpha_s = 40):
        //   bo = 4*755 = 3020; vc = min(1.61666, 2.49847, 2.72527) = 1.61666 MPa
        //   phi*vc = 1.21250 MPa; Vc = 1.21250*3020*355/1000 = 1299.92 kN
        //   Vu = 2000 kN > Vc -> punching FAILS (studs are added)
        //   vu = 2000*1000/(3020*355) = 1.86550 MPa
        // Beam shear: span = (2560-400)/2 - 300 = 780; Vu = 1000 kN
        //   phi*0.17*sqrt(fc) = 0.62462 MPa; Vc = 0.62462*2560*355/1000 = 567.65 kN -> FAILS
        //   vu = 1000*1000/(2560*355) = 1.10035 MPa
        // Flexure: Mu = 500*0.78 = 390 kN.m; Rn = 1.34315; m = 19.6078
        //   rhoReq = 0.003476 < rhoMin = 0.0035 -> As = 0.0035*2560*355 = 3180.8 mm2
        //   Selection (after the provided>=required fix): T14 @ 121 mm,
        //   bars = 21, AsProv = 3232.7 mm2 (the pre-fix code silently provided
        //   25 T12 = 2827 mm2, 11% BELOW required)
        val r = engine.designPileCap(
            PileCapInput(
                axialLoad = 2000.0, numberOfPiles = 4, pileDiameter = 600.0,
                pileSpacing = 1800.0, columnWidth = 400.0, columnLength = 400.0,
                fcu = 30.0, fy = 400.0, cover = 75.0
            )
        )
        assertClose(2560.0, r.capWidth, "capWidth")
        assertClose(2560.0, r.capLength, "capLength")
        assertClose(450.0, r.capThickness, "capThickness")

        assertFalse("punching must fail for this vector", r.punchingShearOk)
        assertClose(1.86550, r.punchingShearStress, "punching stress")
        assertClose(1.21250, r.punchingShearCapacity, "punching capacity")
        assertNotNull("punching studs must be added when punching fails", r.punchingReinforcement)

        assertFalse("beam shear must fail for this vector", r.beamShearOk)
        assertClose(1.10035, r.beamShearStress, "beam shear stress")
        assertClose(0.62462, r.beamShearCapacity, "beam shear capacity")

        assertClose(3180.8, r.flexuralReinforcement.requiredArea, "As required")
        assertEquals(14, r.flexuralReinforcement.diameter)
        assertEquals(121, r.flexuralReinforcement.spacing)
        assertEquals(21, r.flexuralReinforcement.bars)
        assertClose(3232.7, r.flexuralReinforcement.area, "As provided")
        assertTrue(
            "provided steel must never fall below required",
            r.flexuralReinforcement.area >= r.flexuralReinforcement.requiredArea
        )

        assertClose(2.94912, r.concreteVolume, "concrete volume")
    }

    @Test
    fun `regression provided reinforcement always meets required`() {
        // Sweep: the pre-fix loop could provide less steel than required.
        listOf(1500.0, 2000.0, 3000.0, 4500.0).forEach { load ->
            val r = engine.designPileCap(
                PileCapInput(
                    axialLoad = load, numberOfPiles = 4, pileDiameter = 600.0,
                    pileSpacing = 1800.0, columnWidth = 400.0, columnLength = 400.0,
                    fcu = 30.0, fy = 400.0, cover = 75.0
                )
            )
            assertTrue(
                "load=$load: provided ${r.flexuralReinforcement.area} < required ${r.flexuralReinforcement.requiredArea}",
                r.flexuralReinforcement.area >= r.flexuralReinforcement.requiredArea - 1e-6
            )
        }
    }

    // ---------------------------------------------------------------
    // 6. CHARACTERIZATION: group efficiency (Converse-Labarre variant)
    // ---------------------------------------------------------------

    @Test
    fun `characterization group efficiency pins current variant`() {
        // KNOWN GAP (CLAUSE_LOG U-P2): this is a Converse-Labarre VARIANT:
        // it uses atan(s/d) instead of the standard atan(d/s) and drops the
        // (n-1)m term. For 3x3 at s = 3d the standard formula gives ~0.73,
        // this variant gives ~0.65. Pins current behavior; flagged for an
        // engineering decision rather than silently changed.
        val r = engine.checkGroupEfficiency(
            PileGroupInput(numberOfPiles = 9, pileDiameter = 600.0, spacing = 3.0)
        )
        assertEquals(0.65, r.efficiencyFactor, 0.02)
        assertTrue(r.pattern.startsWith("3×3"))
        assertEquals(9, r.numberOfPiles)
    }

    // ---------------------------------------------------------------
    // 7. Guards
    // ---------------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `negative diameter is rejected loudly`() {
        engine.calculatePileCapacity(PileInput(pileDiameter = -1.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero pile length is rejected loudly`() {
        engine.calculatePileCapacity(PileInput(pileLength = 0.0))
    }
}
