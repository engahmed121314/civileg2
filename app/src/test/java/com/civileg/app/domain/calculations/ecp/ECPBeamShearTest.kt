package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden vectors for CLAUSE_LOG E-BS-1 (2026-11-21): ECP 203-2020 beam shear.
 * Every expected value below is hand-computed from the printed code:
 *  q_cu(uncracked) = 0.16√(fcu/γc)  Eq.[4-29] printed 4-18
 *  q_cu(cracked)   = 0.12√(fcu/γc)  Eq.[4-30] printed 4-20
 *  q_su = q_u − q_cu(cracked)       Eq.[4-33] printed 4-20
 *  μ_st = q_su/(fy/γs)              Eq.[4-34] printed 4-21
 *  μ_min = 0.4/fy, floors 0.15%/0.10%  Eq.[4-40] + §4-2-2-1-6(أ) printed 4-22
 *  ≥4 legs & ≤250 mm leg gap for b ≥ 400  §4-2-2-1-6(ب) printed 4-22
 *  fy(web) ≤ 420 MPa                §4-2-2-1-6(هـ) printed 4-22
 *  s ≤ 200 mm                       §4-2-2-1-6(و) printed 4-22
 *  q_umax = 0.70√(fcu/γc) ≤ 4.4 MPa Eq.[4-27] printed 4-17
 * γc = 1.5, γs = 1.15.
 */
class ECPBeamShearTest {

    private val beam = ECPBeam()

    private fun shear(fcu: Double, fy: Double, b: Double, d: Double, vuKn: Double) =
        beam.calculateShearReinforcement(
            fcu = fcu, fy = fy, width = b, effectiveDepth = d,
            designShear = vuKn, axialLoad = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )

    /** fcu=30: q_unc = 0.16√20 = 0.715542; q_cr = 0.12√20 = 0.536656 */
    @Test
    fun `stirrup design - fcu30 b250 d550 Vu200 fy400`() {
        val r = shear(30.0, 400.0, 250.0, 550.0, 200.0)
        // concrete-only capacity = 0.715542×250×550/1000 = 98.387 kN < 200 → stirrups
        assertEquals(98.387, r.concreteShearCapacity, 0.01)
        // excess = 200000 − 0.536656×250×550 = 126209.8 N
        // fyd = 400/1.15 = 347.826; req = 126209.8/(347.826×550)×1000 = 659.73 mm²/m
        assertEquals(659.73, r.requiredShearReinforcement, 0.5)
        assertEquals(2, r.numLegs)
        assertEquals(10.0, r.stirrupDiameter, 0.001)
        // 2-leg Ø10 = 157.0796 mm² → s = 157.0796×1000/659.73 = 238.1 → capped 200
        assertEquals(200.0, r.stirrupSpacing, 0.001)
        assertEquals(785.398, r.providedShearReinforcement, 0.5)
        assertTrue(r.isSafe)
    }

    @Test
    fun `below uncracked threshold - minimum reinforcement only`() {
        val r = shear(30.0, 400.0, 250.0, 550.0, 90.0)
        // 90 kN ≤ 98.387 kN → min: μ_min = 0.4/400 = 0.001 → 0.001×250×1000 = 250 mm²/m
        assertEquals(250.0, r.requiredShearReinforcement, 0.01)
        assertEquals(200.0, r.stirrupSpacing, 0.001)
    }

    @Test
    fun `wide beam b450 gets 4 legs - b450 d700 Vu500 fy360`() {
        val r = shear(30.0, 360.0, 450.0, 700.0, 500.0)
        // §4-2-2-1-6(ب): b ≥ 400 → ≥ 4 legs
        assertEquals(4, r.numLegs)
        // excess = 500000 − 0.536656×450×700 = 330953.4 N
        // fyd = 360/1.15 = 313.043; req = 330953.4/(313.043×700)×1000 = 1510.30 mm²/m
        assertEquals(1510.30, r.requiredShearReinforcement, 0.5)
        // 4-leg Ø10 = 314.159 mm² → s = 208.0 → 200
        assertEquals(200.0, r.stirrupSpacing, 0.001)
        assertEquals(1570.796, r.providedShearReinforcement, 0.5)
    }

    @Test
    fun `fy600 capped at 420 for web steel`() {
        val r = shear(30.0, 600.0, 250.0, 550.0, 200.0)
        // fyd = 420/1.15 = 365.217; req = 126209.8/(365.217×550)×1000 = 628.32 mm²/m
        assertEquals(628.32, r.requiredShearReinforcement, 0.5)
    }

    @Test
    fun `mild steel 240 min ratio is 0_4 over fy not flat 0_15pct`() {
        val r = shear(30.0, 240.0, 250.0, 550.0, 90.0)
        // μ_min = max(0.4/240, 0.0015) = 0.0016667 → 416.67 mm²/m
        assertEquals(416.67, r.requiredShearReinforcement, 0.05)
    }

    @Test
    fun `q_umax absolute cap 4_4 MPa active at fcu60`() {
        val r = shear(60.0, 400.0, 250.0, 550.0, 600.0)
        // 0.70√(60/1.5) = 4.427 > 4.4 → max capacity = 4.4×250×550/1000 = 605.0 kN
        // utilization = 600/605 = 0.99174
        assertEquals(0.99174, r.utilizationRatio, 0.0005)
        assertTrue(r.isSafe)
    }
}
