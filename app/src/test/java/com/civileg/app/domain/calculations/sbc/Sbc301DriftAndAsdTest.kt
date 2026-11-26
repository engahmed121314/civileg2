package com.civileg.app.domain.calculations.sbc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Hand values from SBC 301-CR-2018: Eq 12-26 (p.82), Eq 12-27 and 12-28 (p.83), Table 12-8 (p.104),
 * 12.12.1.1 (p.86), Eq 2-3 (p.14-15), Eq 12-6 (p.77). Printed page = PDF page - 18.
 */
class Sbc301DriftAndAsdTest {

    private fun stories(deltaXe: List<Double>, hsx: Double = 3000.0, vx: Double = 500.0, px: Double = 2000.0) =
        deltaXe.map { SbcDriftStory(vx = vx, px = px, hsxMm = hsx, deltaXeMm = it) }

    @Test fun `amplified drift Eq 12-26 and Table 12-8 all other structures row`() {
        // Cd 5.5, Ie 1.0, delta_xe = 2,5,9,14,20 mm -> delta_x = 11,27.5,49.5,77,110 mm; drift = 11,16.5,22,27.5,33 mm
        val r = SbcDrift301.check(
            stories(listOf(2.0, 5.0, 9.0, 14.0, 20.0)), cd = 5.5, ie = 1.0,
            structureType = SbcDriftStructureType.ALL_OTHER, risk = SbcRiskCategory.II, momentFramesOnlyInSdcDef = false
        )
        assertEquals(11.0, r.rows[0].deltaXMm, 1e-9)
        assertEquals(33.0, r.rows[4].deltaXMm - r.rows[3].deltaXMm, 1e-9)
        assertEquals(33.0, r.rows[4].driftMm, 1e-9)
        assertEquals(33.0 / 3000.0, r.rows[4].driftRatio, 1e-9)
        assertEquals(0.020, r.rows[4].allowableRatio!!, 1e-12)
        assertEquals(true, r.driftOk)
        // theta story 1 = 2000*11*1/(500*3000*5.5) = 0.00267 <= 0.10 -> P-delta not required
        assertTrue(r.rows[0].notes.any { it.contains("12.8.7.1") })
        assertTrue(r.pdeltaOk)
    }

    @Test fun `drift failure is reported against Table 12-8`() {
        val r = SbcDrift301.check(
            stories(listOf(10.0, 25.0, 45.0, 70.0, 100.0)), cd = 5.5, ie = 1.0,
            structureType = SbcDriftStructureType.ALL_OTHER, risk = SbcRiskCategory.IV, momentFramesOnlyInSdcDef = false
        )
        assertEquals(0.010, r.rows[4].allowableRatio!!, 1e-12)   // risk IV column
        assertFalse(r.driftOk!!)
    }

    @Test fun `Table 12-8 rows by structure type`() {
        assertEquals(0.025, SbcDriftStructureType.NON_MASONRY_4_STORY_ACCOMMODATING.allowableRatio(SbcRiskCategory.I), 0.0)
        assertEquals(0.020, SbcDriftStructureType.NON_MASONRY_4_STORY_ACCOMMODATING.allowableRatio(SbcRiskCategory.III), 0.0)
        assertEquals(0.015, SbcDriftStructureType.NON_MASONRY_4_STORY_ACCOMMODATING.allowableRatio(SbcRiskCategory.IV), 0.0)
        assertEquals(0.010, SbcDriftStructureType.MASONRY_CANTILEVER_SHEAR_WALL.allowableRatio(SbcRiskCategory.IV), 0.0)
        assertEquals(0.007, SbcDriftStructureType.MASONRY_OTHER_SHEAR_WALL.allowableRatio(SbcRiskCategory.II), 0.0)
    }

    @Test fun `single story accommodating walls has no drift limit Table 12-8 footnote c`() {
        val r = SbcDrift301.check(
            stories(listOf(50.0)), cd = 5.5, ie = 1.0,
            structureType = SbcDriftStructureType.NON_MASONRY_4_STORY_ACCOMMODATING, risk = SbcRiskCategory.II,
            momentFramesOnlyInSdcDef = false
        )
        assertNull(r.rows[0].allowableRatio)
        assertNull(r.rows[0].driftOk)
        assertTrue(r.rows[0].notes.any { it.contains("footnote c") })
    }

    @Test fun `moment frames only in SDC D to F use allowable over rho 12_12_1_1`() {
        val r = SbcDrift301.check(
            stories(listOf(2.0, 5.0, 9.0, 14.0, 20.0)), cd = 5.5, ie = 1.0,
            structureType = SbcDriftStructureType.ALL_OTHER, risk = SbcRiskCategory.II,
            momentFramesOnlyInSdcDef = true, rho = 1.3
        )
        assertEquals(0.020 / 1.3, r.rows[4].allowableRatio!!, 1e-12)
        assertEquals(true, r.driftOk)   // 0.011 <= 0.01538
        try {
            SbcDrift301.check(stories(listOf(2.0)), 5.5, 1.0, SbcDriftStructureType.ALL_OTHER, SbcRiskCategory.II, true)
            fail("rho missing accepted")
        } catch (e: IllegalArgumentException) { }
    }

    @Test fun `p-delta instability is reported Eq 12-27 12-28`() {
        // story 5: Px 12000, Vx 200, drift 33 mm, hsx 3000, Cd 5.5 -> theta = 12000*33/(200*3000*5.5) = 0.12 > theta_max = 0.5/5.5 = 0.0909
        val r = SbcDrift301.check(
            stories(listOf(2.0, 5.0, 9.0, 14.0, 20.0), vx = 200.0, px = 12000.0), cd = 5.5, ie = 1.0,
            structureType = SbcDriftStructureType.ALL_OTHER, risk = SbcRiskCategory.II, momentFramesOnlyInSdcDef = false
        )
        assertEquals(0.12, r.rows[4].theta, 1e-9)
        assertEquals(0.5 / 5.5, r.rows[4].thetaMax, 1e-12)
        assertFalse(r.pdeltaOk)
        assertTrue(r.rows[4].notes.any { it.contains("12.8.7.5") })
    }

    @Test fun `invalid drift input is rejected`() {
        try {
            SbcDrift301.check(emptyList(), 5.5, 1.0, SbcDriftStructureType.ALL_OTHER, SbcRiskCategory.II, false)
            fail("empty stories accepted")
        } catch (e: IllegalArgumentException) { }
        try {
            SbcDrift301.check(stories(listOf(1.0), hsx = 0.0), 5.5, 1.0, SbcDriftStructureType.ALL_OTHER, SbcRiskCategory.II, false)
            fail("zero story height accepted")
        } catch (e: IllegalArgumentException) { }
    }

    @Test fun `asd basic combinations Eq 2-3`() {
        val c = SbcLoadCombinations301.allowableStress(SbcLoadEffects(d = 100.0, l = 50.0, lr = 20.0))
        assertEquals(100.0, c.first { it.id == "1" }.value, 1e-9)
        assertEquals(150.0, c.first { it.id == "2" }.value, 1e-9)
        assertEquals(120.0, c.first { it.id.startsWith("3") }.value, 1e-9)
        assertEquals(100.0 + 37.5 + 15.0, c.first { it.id.startsWith("4") }.value, 1e-9)
    }

    @Test fun `asd wind combinations Eq 2-3 both directions`() {
        val c = SbcLoadCombinations301.allowableStress(SbcLoadEffects(d = 100.0, l = 50.0, lr = 20.0, w = 30.0))
        assertEquals(100.0 + 0.6 * 30, c.first { it.id == "5 (+0.6W)" }.value, 1e-9)
        assertEquals(0.6 * 100 - 0.6 * 30, c.first { it.id == "7 (-0.6W)" }.value, 1e-9)
        assertEquals(100.0 + 37.5 + 0.75 * 0.6 * 30 + 15.0, c.first { it.id.startsWith("6a (Lr, +0.6W)") }.value, 1e-9)
    }

    @Test fun `asd seismic combinations Eq 12-6`() {
        val e = SbcLoadEffects(d = 100.0, l = 50.0, qe = 40.0)
        val c = SbcLoadCombinations301.seismicAsd(e, sds = 0.6, omega0 = 2.5)
        assertEquals((1.0 + 0.084) * 100 + 0.7 * 2.5 * 40, c.first { it.id == "5 (+E)" }.value, 1e-9)
        assertEquals((1.0 + 0.06) * 100 + 0.525 * 2.5 * 40 + 37.5, c.first { it.id == "6b (+E)" }.value, 1e-9)
        assertEquals((0.6 - 0.084) * 100 - 0.7 * 2.5 * 40, c.first { it.id == "8 (-E)" }.value, 1e-9)
        val low = SbcLoadCombinations301.seismicAsd(e, sds = 0.1, omega0 = 2.5)   // SDS <= 0.125, Ev = 0
        assertEquals(100.0 + 70.0, low.first { it.id == "5 (+E)" }.value, 1e-9)
        try {
            SbcLoadCombinations301.seismicAsd(e, 0.5, 0.0)
            fail("Omega0 < 1 accepted")
        } catch (ex: IllegalArgumentException) { }
    }
}
