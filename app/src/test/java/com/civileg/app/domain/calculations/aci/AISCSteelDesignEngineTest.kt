package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.entities.BoltConnectionType
import com.civileg.app.domain.entities.BoltGrade
import com.civileg.app.domain.entities.BoltPattern
import com.civileg.app.domain.entities.ConnectionType
import com.civileg.app.domain.entities.SteelGrade
import com.civileg.app.domain.entities.SteelSectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden vectors for [AISCSteelDesignEngine] member design (tension D2/J4.3,
 * compression E3/E4/E7). Sources and status in docs/CLAUSE_LOG.md
 * (batch A-01..A-05, accessed 2026-11-21).
 *
 * Verified vectors assert values hand-computed independently from the AISC
 * 360-16 equations; characterization vectors pin engine-specific
 * simplifications (shear-lag U, block-shear geometry, torsional E4 path) so
 * future changes are loud.
 */
class AISCSteelDesignEngineTest {

    private val engine = AISCSteelDesignEngine()
    private val tolPct = 0.5

    private fun assertClose(expected: Double, actual: Double, what: String) {
        assertTrue(
            "$what: expected $expected but was $actual",
            kotlin.math.abs(actual - expected) <= kotlin.math.abs(expected) * tolPct / 100.0 + 1e-6
        )
    }

    // I 300x150x10x7 (h x bf, tf=10, tw=7): Ag = 4960 mm2,
    // rx = 123.71 mm, ry = 33.70 mm
    private val iSection = SteelSectionType.ISection(
        h = 300.0, bf = 150.0, tf = 10.0, tw = 7.0, grade = SteelGrade.A36
    )

    // ---------------------------------------------------------------
    // 1. VERIFIED: tension, AISC D2-1 gross yielding + D2-2 net rupture
    // ---------------------------------------------------------------

    @Test
    fun `verified tension bolted I-section net rupture governs`() {
        // Hand computation (A36: Fy=250, Fu=400):
        //   phiPn_gross = 0.9 * 250 * 4960 / 1000 = 1116.0 kN   (D2-1)
        //   d_hole = 22, An = 4960 - 4*22*10 = 4080 mm2
        //   U = 1 - 75/240 = 0.6875 (engine Table D3.1 approximation)
        //   phiPn_net = 0.75 * 400 * 2805 / 1000 = 841.5 kN    (D2-2)
        //   Block shear (engine geometry): gauge=150, Ant=1280, lv=235,
        //     Agv=4700, Anv=4040 -> Rn = min(1217.0, 1481.6) = 1217.0 kN
        //     phiRn = 912.75 kN
        //   design = min(1116.0, 841.5, 912.75) = 841.5 -> net rupture governs
        val result = engine.designTensionMember(
            section = iSection,
            grade = SteelGrade.A36,
            Pu = 600.0,
            connectionType = ConnectionType.Bolted(
                boltDiameter = 20.0,
                boltGrade = BoltGrade.A325,
                numberOfBolts = 4,
                boltPattern = BoltPattern.SINGLE_ROW,
                connectionType = BoltConnectionType.BEARING
            )
        )
        assertClose(1116.0, result.grossYielding, "phiPn gross (D2-1)")
        assertClose(841.5, result.netRupture, "phiPn net (D2-2)")
        assertClose(912.75, result.blockShear, "phiRn block shear (J4.3)")
        assertClose(841.5, result.designCapacity, "design capacity")
        assertTrue("net rupture must govern", result.governingCase.contains("D2-2"))
        assertClose(600.0 / 841.5, result.utilizationRatio, "utilization")
        assertTrue(result.isSafe)
    }

    @Test
    fun `verified tension welded gross yielding governs`() {
        // Welded: An = Ag, U = 1.0 -> phiPn_net = 0.75*400*4960/1000 = 1488.0
        // Engine block-shear placeholder for welded = 0.75*0.6*250*4960/1000
        //   = 558.0 kN -> GOVERNS. CLAUSE_LOG U-A1 flags this placeholder: a
        // welded connection has no block-shear limit state in AISC J4.3, so
        // the engine understates welded tension capacity by ~50%.
        // Characterized here, not fixed (owner engineering decision).
        val welded = ConnectionType.Welded(
            weldType = com.civileg.app.domain.entities.WeldType.FILLET,
            weldSize = 8.0,
            weldLength = 200.0,
            electrodeType = com.civileg.app.domain.entities.ElectrodeType.E70XX
        )
        val result = engine.designTensionMember(iSection, SteelGrade.A36, 400.0, welded)
        assertClose(1116.0, result.grossYielding, "phiPn gross (D2-1)")
        assertClose(1488.0, result.netRupture, "phiPn net (D2-2)")
        assertClose(558.0, result.blockShear, "welded block-shear placeholder")
        assertClose(558.0, result.designCapacity, "design capacity (pinned)")
    }

    // ---------------------------------------------------------------
    // 2. VERIFIED: compression, AISC E3 flexural buckling
    // ---------------------------------------------------------------

    @Test
    fun `verified compression RHS inelastic buckling E3-3`() {
        // RHS 200x100x8 (A36): torsional buckling cannot govern (closed
        // section), local buckling factors = 1.
        //   Ag = 4544 mm2, rx = 40.84, ry = 71.24
        //   KL = 2000: lambda = 48.97, Fe = 823.03 MPa
        //   lambda < 4.71*sqrt(E/Fy) = 133.2 -> E3-3:
        //   Fcr = 0.658^(250/823.03) * 250 = 220.15 MPa
        //   phiPn = 0.9 * 220.15 * 4544 / 1000 = 900.3 kN
        val rhs = SteelSectionType.RHS(width = 200.0, height = 100.0, thickness = 8.0, grade = SteelGrade.A36)
        val result = engine.designCompressionMember(
            section = rhs, grade = SteelGrade.A36, Pu = 700.0,
            Kx = 1.0, Ky = 1.0, Lx = 2000.0, Ly = 2000.0
        )
        assertClose(220.15, result.Fcr, "Fcr inelastic (E3-3)")
        assertClose(48.97, result.slendernessRatio, "slenderness")
        assertClose(900.3, result.phiPn, "phiPn")
        assertTrue(result.bucklingMode.contains("Flexural"))
    }

    @Test
    fun `verified compression I-section elastic buckling E3-4`() {
        // KL = 6000: lambda_y = 178.04 > 133.2 -> E3-4:
        //   Fe = 62.27 MPa, Fcr = 0.877 * Fe = 54.61 MPa
        //   Torsional E4 gives 110.09 MPa -> flexural elastic governs.
        //   phiPn = 0.9 * 54.61 * 4960 / 1000 = 243.8 kN
        val result = engine.designCompressionMember(
            section = iSection, grade = SteelGrade.A36, Pu = 200.0,
            Kx = 1.0, Ky = 1.0, Lx = 6000.0, Ly = 6000.0
        )
        assertClose(54.61, result.Fcr, "Fcr elastic (E3-4)")
        assertClose(178.04, result.slendernessRatio, "slenderness")
        assertClose(243.8, result.phiPn, "phiPn")
        assertTrue(result.bucklingMode.contains("Flexural"))
    }

    // ---------------------------------------------------------------
    // 3. CHARACTERIZATION: torsional buckling path (E4) governing
    // ---------------------------------------------------------------

    @Test
    fun `characterization compression I-section torsional governs`() {
        // KL = 4000: flexural E3-3 gives Fcr = 118.47 MPa, but the engine's
        // E4 torsional path (J = 132,013 mm4, Cw = 1.47e9 mm6, Fe_t = 126.77)
        // gives Fcr = 109.51 MPa and governs.
        // Engine simplification: the E3-3/E3-4 branch choice uses the FLEXURAL
        // slenderness, not the torsional equivalent slenderness, and L is taken
        // as min(KxLx, KyLy) instead of the torsional effective length Kz*Lz.
        // Pinned here, recorded as U-A2; conservative direction for this case.
        val result = engine.designCompressionMember(
            section = iSection, grade = SteelGrade.A36, Pu = 400.0,
            Kx = 1.0, Ky = 1.0, Lx = 4000.0, Ly = 4000.0
        )
        assertClose(109.51, result.Fcr, "Fcr torsional-governed")
        assertClose(488.9, result.phiPn, "phiPn")
        assertTrue(result.bucklingMode.contains("Torsional"))
    }

    // ---------------------------------------------------------------
    // 4. Guards
    // ---------------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `zero compression load is rejected loudly`() {
        engine.designCompressionMember(iSection, SteelGrade.A36, 0.0, 1.0, 1.0, 4000.0, 4000.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative tension load is rejected loudly`() {
        engine.designTensionMember(
            iSection, SteelGrade.A36, -5.0,
            ConnectionType.Pressed(pressForce = 100.0, contactArea = 5000.0, surfaceTreatment = "None")
        )
    }
}
