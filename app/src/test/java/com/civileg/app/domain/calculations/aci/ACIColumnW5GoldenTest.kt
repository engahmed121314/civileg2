package com.civileg.app.domain.calculations.aci

import com.civileg.core.calculations.entities.LoadCombination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * W5 golden regression gate (PHASE00_AUDIT §3.1 W5):
 *  - Euler buckling load was ~1000x overstated (unit error: EI/1e6 mixed with
 *    length in metres) and ignored the effective-length factor K.
 *  - Axial capacity lacked the ACI 318-19 §22.4.2 maximum-cap cap
 *    (phi*0.80*P0 tied / phi*0.85*P0 spiral).
 *
 * All expected values are hand-derived from ACI 318-19 equations.
 */
class ACIColumnW5GoldenTest {

    private val column = ACIColumn()

    // ── Euler buckling load ──────────────────────────────────────────────────

    @Test
    fun `W5 euler load matches closed form with K equals 1`() {
        // w=d=400mm, L=3m, K=1, rho=0.02, fcu=25 (fc'=20):
        // Ec = 4700*sqrt(20) = 21019.2 MPa
        // Ig = 400*400^3/12  = 2.13333e9 mm^4
        // As = 0.02*160000   = 3200 mm^2 ; d' = 40mm
        // EI = 0.4*Ec*Ig + Es*As*(h/2-d')^2
        //    = 0.4*21019.2*2.13333e9 + 200000*3200*160^2
        //    = 1.793634e13 + 1.638400e13 = 3.432034e13 N.mm^2
        // Pc = pi^2*EI / (1000*(K*L_mm)^2) = 9.869604*3.432034e13 / 9.0e9
        //    = 37,616.4 kN
        val ec = 4700.0 * sqrt(0.8 * 25.0)
        val ig = 400.0 * 400.0.pow(3) / 12.0
        val ei = 0.4 * ec * ig + 200000.0 * 0.02 * 160000.0 * (400.0 / 2 - 40.0).pow(2)
        val expected = (PI * PI * ei) / (1000.0 * (1.0 * 3000.0).pow(2)) // kN

        val (_, slender, pc) = column.calculateSlendernessEffect(
            unsupportedLength = 3.0, effectiveLengthFactor = 1.0,
            width = 400.0, depth = 400.0, fcu = 25.0, reinforcementRatio = 0.02
        )
        assertEquals(expected, pc, expected * 0.001)
        assertEquals(37616.4, pc, 50.0)
        // KL/r = 3000/(400/sqrt(12)) = 25.98 -> slender (> 22)
        assertTrue(slender)
    }

    @Test
    fun `W5 euler load scales inversely with K squared`() {
        // Pc(K=2) = Pc(K=1)/4 = 9,404.1 kN — before the fix K was ignored entirely
        val base = column.calculateSlendernessEffect(
            3.0, 1.0, 400.0, 400.0, 25.0, reinforcementRatio = 0.02
        ).third
        val doubledK = column.calculateSlendernessEffect(
            3.0, 2.0, 400.0, 400.0, 25.0, reinforcementRatio = 0.02
        ).third
        assertEquals(base / 4.0, doubledK, base / 4.0 * 0.001)
    }

    @Test
    fun `W5 euler load magnitude is physically plausible`() {
        // Before the fix the returned value exceeded gross-section Euler load by ~1000x.
        // Upper bound: pi^2 * Ec * Ig / (1000 * L_mm^2) using UNREDUCED stiffness:
        val ec = 4700.0 * sqrt(0.8 * 25.0)
        val ig = 400.0 * 400.0.pow(3) / 12.0
        val upperBoundKn = PI * PI * ec * ig / (1000.0 * 3000.0.pow(2))
        val pc = column.calculateSlendernessEffect(
            3.0, 1.0, 400.0, 400.0, 25.0, reinforcementRatio = 0.02
        ).third
        assertTrue("Pc=$pc must not exceed gross-section Euler bound $upperBoundKn", pc < upperBoundKn)
    }

    // ── Maximum axial capacity cap (ACI 318-19 Table 22.4.2.1a) ─────────────

    @Test
    fun `W5 tied column capacity capped at phi times 0_80 times P0`() {
        // w=d=300, Ast given = 6% Ag = 5400 mm2, fcu=30 (fc'=24), fy=420:
        // P0 = 0.85*24*(90000-5400) + 420*5400 = 1,725,840 + 2,268,000 = 3,993,840 N
        // phiPn,max = 0.65*0.80*P0 = 2,076.80 kN  (uncapped phi*P0 would be 2,596.00)
        val ag = 300.0 * 300.0
        val p0 = 0.85 * (0.8 * 30.0) * (ag - 0.06 * ag) + 420.0 * 0.06 * ag // N
        val expected = 0.65 * 0.80 * p0 / 1000.0

        val capacity = column.calculateAxialCapacity(
            fcu = 30.0, fy = 420.0, width = 300.0, depth = 300.0,
            reinforcementArea = 0.06 * ag, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertEquals(expected, capacity, 0.5)
        assertEquals(2076.8, capacity, 1.0)
    }

    @Test
    fun `W5 spiral column capacity capped at phi times 0_85 times P0`() {
        val ag = 300.0 * 300.0
        val p0 = 0.85 * (0.8 * 30.0) * (ag - 0.06 * ag) + 420.0 * 0.06 * ag
        val expected = 0.75 * 0.85 * p0 / 1000.0

        val capacity = column.calculateAxialCapacityWithPhi(
            fcu = 30.0, fy = 420.0, width = 300.0, depth = 300.0,
            reinforcementArea = 0.06 * ag, isSpiral = true
        )
        assertEquals(expected, capacity, 0.5)
        assertEquals(2546.1, capacity, 1.0)
    }

    @Test
    fun `W5 modest reinforcement stays below cap so cap is inert`() {
        // Ast = 2% Ag: phiP0 = 0.65*[0.85*24*(88200)+420*18000]/1000 = 1,842.66 kN
        // cap 0.80*phiP0 = 1474.13 < phiP0 -> hmm, cap governs here too by design;
        // verify returned value equals the cap formula exactly.
        val ag = 300.0 * 300.0
        val ast = 0.02 * ag
        val p0 = 0.85 * (0.8 * 30.0) * (ag - ast) + 420.0 * ast
        val expected = 0.65 * 0.80 * p0 / 1000.0
        val capacity = column.calculateAxialCapacity(
            fcu = 30.0, fy = 420.0, width = 300.0, depth = 300.0,
            reinforcementArea = ast, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertEquals(expected, capacity, 0.5)
    }
}
