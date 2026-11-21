package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.entities.BoltConnectionType
import com.civileg.app.domain.entities.BoltGrade
import com.civileg.app.domain.entities.BoltPattern
import com.civileg.app.domain.entities.ConnectionType
import com.civileg.app.domain.entities.ElectrodeType
import com.civileg.app.domain.entities.SteelGrade
import com.civileg.app.domain.entities.SteelSectionType
import com.civileg.app.domain.entities.WeldType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden vectors for [SBCSteelDesignEngine]. Sources and status in
 * docs/CLAUSE_LOG.md (batch S-01..S-12, accessed 2026-11-21).
 *
 * Verified vectors assert values hand-computed independently from the
 * AISC 360-05 / SBC 306 LRFD equations that the engine adopts;
 * characterization vectors pin engine-specific simplifications (coastal
 * factor, bolted-connection geometry, base-plate sizing, bracing An = 0.85Ag)
 * so future changes are loud.
 */
class SBCSteelDesignEngineTest {

    private val engine = SBCSteelDesignEngine()
    private val tolPct = 0.5

    private fun assertClose(expected: Double, actual: Double, what: String) {
        assertTrue(
            "$what: expected $expected but was $actual",
            kotlin.math.abs(actual - expected) <= kotlin.math.abs(expected) * tolPct / 100.0 + 1e-6
        )
    }

    // I 300x150x10x7 (h x bf, tf=10, tw=7): Ag = 4960 mm2,
    // Ix = 7.5905e7 mm4, Sx = 506,035.6 mm3, Zx = 572,200 mm3 (exact, CLAUSE_LOG A-08),
    // rx = 123.71 mm, ry = 33.70 mm
    private val iS355 = SteelSectionType.ISection(
        h = 300.0, bf = 150.0, tf = 10.0, tw = 7.0, grade = SteelGrade.S355
    )

    // ---------------------------------------------------------------
    // 1. VERIFIED: beam, compact section, full bracing (no LTB)
    //    Mn = Fy*Zx, Vn = 0.6*Fy*(d-2tf)*tw, delta = 5wL^4/384EI
    // ---------------------------------------------------------------

    @Test
    fun `verified beam compact fully braced flexure shear deflection`() {
        // Hand computation (S355: Fy=355, Fu=510):
        //   classification: flange (150-7)/(2*10)=7.15 <= 0.38*sqrt(200000/355)=9.02,
        //     web 280/7=40 <= 3.76*sqrt(E/Fy)=89.2 -> COMPACT
        //   Mn = 355*572200/1e6 = 203.13 kN.m -> phiMn = 0.9*203.13 = 182.82
        //   Aw = 280*7 = 1960 mm2, Vn = 0.6*355*1960/1000 = 417.48 kN
        //     -> phiVn = 375.73 kN
        //   delta = 5*10*6^4/(384*200000e3*7.5905e-5) = 11.12 mm
        //   delta_allow = 6000/360 = 16.667 mm
        val r = engine.designBeam(
            section = iS355, grade = SteelGrade.S355,
            Mu = 150.0, Vu = 100.0, w_LL = 10.0, span = 6000.0,
            Lb = 0.0, Cb = 1.0, isCoastal = false, isSeismic = false
        )
        assertEquals(SBCSteelDesignEngine.SectionClassification.COMPACT, r.sectionClassification)
        assertClose(203.13, r.Mn_nominal, "Mn nominal")
        assertClose(182.82, r.phiMn, "phiMn")
        assertClose(375.73, r.phiVn, "phiVn")
        assertClose(11.12, r.deflection, "deflection")
        assertClose(16.667, r.deflectionAllowable, "deflection allowable")
        assertClose(150.0 / 182.82, r.momentRatio, "moment ratio")
        assertTrue(r.isSafe)
    }

    // ---------------------------------------------------------------
    // 2. CHARACTERIZED: SBC coastal factor divides capacities by 1.1
    //    (UNVERIFIED engine constant, CLAUSE_LOG S-11)
    // ---------------------------------------------------------------

    @Test
    fun `characterized coastal factor divides capacities by 1p1`() {
        val r = engine.designBeam(
            section = iS355, grade = SteelGrade.S355,
            Mu = 150.0, Vu = 100.0, w_LL = 10.0, span = 6000.0,
            Lb = 0.0, Cb = 1.0, isCoastal = true, isSeismic = false
        )
        assertClose(182.82 / 1.1, r.phiMn, "phiMn coastal")
        assertClose(375.73 / 1.1, r.phiVn, "phiVn coastal")
        assertTrue(r.warnings.any { it.contains("التآكل الساحلي") })
    }

    // ---------------------------------------------------------------
    // 3. VERIFIED: column, inelastic buckling branch (E3-2)
    // ---------------------------------------------------------------

    @Test
    fun `verified column inelastic buckling I-section`() {
        // KL/r governing = 3000/33.70 = 89.0 <= 4.71*sqrt(200000/355) = 111.8
        // Fe = pi^2*200000/89.0^2 = 249.1 MPa
        // Fcr = 0.658^(355/249.1)*355 = 195.5 MPa
        // phiPn = 0.9*195.5*4960/1000 = 872.7 kN
        val r = engine.designColumn(
            section = iS355, grade = SteelGrade.S355,
            Pu = 800.0, Mux = 0.0, Muy = 0.0,
            Kx = 1.0, Ky = 1.0, Lx = 3000.0, Ly = 3000.0,
            isCoastal = false, isSeismic = false
        )
        assertClose(89.0, r.slendernessRatio, "slenderness")
        assertClose(195.5, r.Fcr, "Fcr inelastic")
        assertClose(872.7, r.phiPn, "phiPn")
        assertClose(800.0 / 872.7, r.axialRatio, "axial ratio")
        assertTrue(r.isSafe)
    }

    // ---------------------------------------------------------------
    // 4. VERIFIED: column, elastic buckling branch (E3-3), RHS section
    // ---------------------------------------------------------------

    @Test
    fun `verified column elastic buckling RHS`() {
        // RHS 200x200x8: Ag = 2*(200+200-16)*8 = 6144 mm2,
        //   Ix = (200*200^3 - 184*184^3)/12 = 3.7814e7 mm4, r = 78.45 mm
        // lambda = 9000/78.45 = 114.7 > 111.8 -> elastic
        // Fe = pi^2*200000/114.7^2 = 150.0 MPa, Fcr = 0.877*150.0 = 131.5
        // phiPn = 0.9*131.5*6144/1000 = 727.3 kN
        val rhs = SteelSectionType.RHS(
            width = 200.0, height = 200.0, thickness = 8.0, grade = SteelGrade.S355
        )
        val r = engine.designColumn(
            section = rhs, grade = SteelGrade.S355,
            Pu = 700.0, Mux = 0.0, Muy = 0.0,
            Kx = 1.0, Ky = 1.0, Lx = 9000.0, Ly = 9000.0,
            isCoastal = false, isSeismic = false
        )
        assertClose(114.7, r.slendernessRatio, "slenderness")
        assertClose(131.5, r.Fcr, "Fcr elastic")
        assertClose(727.3, r.phiPn, "phiPn")
        assertTrue(r.isSafe)
    }

    // ---------------------------------------------------------------
    // 5. VERIFIED: combined loading, H1-1a passing case
    // ---------------------------------------------------------------

    @Test
    fun `verified combined loading H1-1a passing`() {
        // Same column as test 3: phiPn = 872.7, phiMnx = 182.82 (exact Zx),
        // phiMny = 0.9*355*Zy/1e6 with Zy = 1.12*Sy, Sy = 5633034/75 = 75107.1
        //   -> Zy = 84120.0 mm3, phiMny = 26.87 kN.m
        // pr = 400/872.7 = 0.458 >= 0.2 -> H1-1a
        // IR = 0.458 + (8/9)*(40/182.82 + 10/26.87) = 0.984
        val r = engine.checkCombinedLoading(
            Pu = 400.0, Mux = 40.0, Muy = 10.0,
            section = iS355, grade = SteelGrade.S355,
            Kx = 1.0, Ky = 1.0, Lx = 3000.0, Ly = 3000.0,
            isCoastal = false
        )
        assertEquals("H1-1a", r.equation)
        assertClose(0.458, r.pr, "pr")
        assertClose(0.984, r.interactionRatio, "interaction ratio")
        assertTrue(r.isSafe)
    }

    // ---------------------------------------------------------------
    // 6. VERIFIED: fillet weld connection (J2.4)
    // ---------------------------------------------------------------

    @Test
    fun `verified fillet weld capacity governs over base metal`() {
        // w=8, L=200, E70XX (Fexx=482): throat = 8/sqrt(2) = 5.657 mm,
        //   area = 1131.4 mm2
        // phiRn_weld = 0.75*0.6*482*1131.4/1000 = 245.4 kN
        // phiRn_base = 0.9*0.6*510*10*200/1000 = 550.8 kN -> weld governs
        val welded = ConnectionType.Welded(
            weldType = WeldType.FILLET,
            weldSize = 8.0, weldLength = 200.0,
            electrodeType = ElectrodeType.E70XX
        )
        val r = engine.designConnection(
            connectionType = welded, appliedForce = 200.0,
            plateThickness = 10.0, grade = SteelGrade.S355, isSeismic = false
        )
        assertClose(245.4, r.capacity, "weld capacity")
        assertClose(200.0 / 245.4, r.utilizationRatio, "utilization")
        assertTrue(r.isSafe)
    }

    // ---------------------------------------------------------------
    // 7. CHARACTERIZED: bolted connection, engine block-shear geometry
    //    (lc = 3*db) governs the capacity stack
    // ---------------------------------------------------------------

    @Test
    fun `characterized bolted connection block shear governs`() {
        // db=20, A325 (Fu=830), n=4, plate 10, S355:
        //   Ab = 314.16 mm2
        //   shear  = 0.75*0.45*830*314.16/1000 * 4 = 352.0 kN
        //   bearing= 0.75*2.4*510*20*10/1000 * 4    = 734.4 kN
        //   tension= 0.75*0.75*830*314.16/1000 * 4  = 586.7 kN
        //   block shear (lc=60): Agv=600, Ant=500, Anv=400
        //     yield   = 0.6*355*600 + 510*500 = 382,800 N
        //     rupture = 0.6*510*400 + 510*500 = 377,400 N
        //     phiRn = 0.75*377400/1000 = 283.1 kN  <- governs
        val bolted = ConnectionType.Bolted(
            boltDiameter = 20.0, boltGrade = BoltGrade.A325,
            numberOfBolts = 4, boltPattern = BoltPattern.SINGLE_ROW,
            connectionType = BoltConnectionType.BEARING
        )
        val r = engine.designConnection(
            connectionType = bolted, appliedForce = 250.0,
            plateThickness = 10.0, grade = SteelGrade.S355, isSeismic = false
        )
        assertClose(283.1, r.capacity, "bolted capacity (block shear)")
        assertTrue(r.isSafe)
    }

    // ---------------------------------------------------------------
    // 8. CHARACTERIZED: base plate sizing (engine rules, CLAUSE_LOG S-09)
    // ---------------------------------------------------------------

    @Test
    fun `characterized base plate sizing and thickness`() {
        // Pu=1000 kN, no moments, I 300x150, fcu=30, grouted:
        //   phiPp/area = 0.65*0.85*30 = 16.575 MPa
        //   requiredArea = 1e6/(0.5*16.575) = 120,664 mm2 -> N_initial = 348
        //   N = ceil(max(348, 300+150)/10)*10 = 450, B = 450
        //   A1 = 202,500, sqrt(A2/A1) = 2 -> phiPp = 6712.9 kN >= 1000 OK
        //   m = 75, n = 150 -> X = 150; fP = 4.938 MPa
        //   tp_req = 150*sqrt(2*4.938/(0.9*355)) = 26.4 -> tp = 28 mm
        //   anchors: 4 x Ø24, length max(12*24, 250) = 288 mm
        val r = engine.designBasePlate(
            Pu = 1000.0, Mux = 0.0, Muy = 0.0,
            columnSection = iS355, grade = SteelGrade.S355,
            fcu = 30.0, isGrouted = true
        )
        assertClose(450.0, r.plateWidth, "plate width")
        assertClose(450.0, r.plateLength, "plate length")
        assertClose(28.0, r.plateThickness, "plate thickness")
        assertEquals(4, r.anchorBolts)
        assertClose(24.0, r.anchorDiameter, "anchor diameter")
        assertClose(288.0, r.anchorLength, "anchor length")
        assertTrue(r.isSafe)
    }

    // ---------------------------------------------------------------
    // 9. CHARACTERIZED: bracing tension with An = 0.85*Ag (CLAUSE_LOG S-10)
    // ---------------------------------------------------------------

    @Test
    fun `characterized bracing tension net approximation`() {
        // I 300x150x10x7, S355, axial = -600 kN (tension):
        //   phiPn_yield   = 0.9*355*4960/1000      = 1584.7 kN  <- governs
        //   phiPn_rupture = 0.75*510*0.85*4960/1000 = 1612.6 kN
        val welded = ConnectionType.Welded(
            weldType = WeldType.FILLET,
            weldSize = 8.0, weldLength = 300.0,
            electrodeType = ElectrodeType.E70XX
        )
        val r = engine.designBracing(
            section = iS355, grade = SteelGrade.S355,
            axialLoad = -600.0, unbracedLength = 3000.0,
            connectionType = welded
        )
        assertTrue(r.isTension)
        assertClose(1584.7, r.phiPn, "bracing tension capacity")
        assertTrue(r.isSafe)
    }

    // ---------------------------------------------------------------
    // 10. VERIFIED: bracing compression reuses the E3 column curve
    // ---------------------------------------------------------------

    @Test
    fun `verified bracing compression matches column curve`() {
        // Same I-section, compression 800 kN, unbraced 3000 mm:
        //   r = rx = 123.71 (ISection branch), lambda = 3000/123.71 = 24.25
        //   Fe = pi^2*200000/24.25^2 = 3357.4 MPa
        //   Fcr = 0.658^(355/3357.4)*355 = 339.6 MPa
        //   phiPn = 0.9*339.6*4960/1000 = 1516.1 kN
        val welded = ConnectionType.Welded(
            weldType = WeldType.FILLET,
            weldSize = 8.0, weldLength = 300.0,
            electrodeType = ElectrodeType.E70XX
        )
        val r = engine.designBracing(
            section = iS355, grade = SteelGrade.S355,
            axialLoad = 800.0, unbracedLength = 3000.0,
            connectionType = welded
        )
        assertTrue(!r.isTension)
        assertClose(24.25, r.slendernessRatio, "slenderness")
        assertClose(1516.1, r.phiPn, "bracing compression capacity")
        assertTrue(r.isSafe)
    }
}
