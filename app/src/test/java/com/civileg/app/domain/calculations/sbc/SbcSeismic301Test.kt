package com.civileg.app.domain.calculations.sbc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.pow

/** Hand values from SBC 301-CR-2018: Tables 11-1, 11-2 (p.66), 11-3, 11-4 (p.66), 12-6, 12-7 (p.104), Eq 12-12 to 12-24 (p.80-81), Eq 2-1 (p.13), Eq 12-5 (p.76). */
class SbcSeismic301Test {

    private fun frameInput(ss: Double = 0.75, s1: Double = 0.3, site: SbcSiteClass = SbcSiteClass.D, risk: SbcRiskCategory = SbcRiskCategory.II,
                           sys: String = "C4", analysisT: Double? = null) = SbcElfInput(
        ss = ss, s1 = s1, tl = 8.0, siteClass = site, riskCategory = risk, system = SbcSeismicSystems.byCode(sys), hn = 15.0,
        storyWeights = List(5) { 2000.0 }, storyHeights = listOf(3.0, 6.0, 9.0, 12.0, 15.0), analysisPeriod = analysisT
    )

    @Test fun `Fa and Fv straight-line interpolation`() {
        assertEquals(1.2, SbcSeismic301.fa(SbcSiteClass.D, 0.75), 1e-9)
        assertEquals(1.5, SbcSeismic301.fa(SbcSiteClass.D, 0.375), 1e-9)
        assertEquals(1.6, SbcSeismic301.fa(SbcSiteClass.D, 0.1), 1e-9)
        assertEquals(1.0, SbcSeismic301.fa(SbcSiteClass.D, 2.0), 1e-9)
        assertEquals(1.8, SbcSeismic301.fv(SbcSiteClass.D, 0.3), 1e-9)
        assertEquals(2.2, SbcSeismic301.fv(SbcSiteClass.D, 0.15), 1e-9)
        assertEquals(1.3, SbcSeismic301.fv(SbcSiteClass.C, 0.9), 1e-9)
    }

    @Test fun `site class F is not assessed`() {
        try { SbcSeismic301.fa(SbcSiteClass.F, 0.5); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("NOT ASSESSED")) }
    }

    @Test fun `ELF five-story concrete moment frame`() {
        val r = SbcSeismic301.compute(frameInput())
        assertEquals(0.6, r.sds, 1e-9); assertEquals(0.36, r.sd1, 1e-9)
        val ta = 0.0466 * 15.0.pow(0.9)
        assertEquals(ta, r.ta, 1e-9); assertEquals(0.5331, r.t, 1e-3)
        assertEquals(0.6 / 6.5, r.csCalculated, 1e-9)
        assertEquals(0.36 / (ta * 6.5), r.csUpper, 1e-9)
        assertEquals(0.6 / 6.5, r.cs, 1e-9)
        assertEquals("Eq 12-13", r.csGoverning)
        assertEquals(923.08, r.v, 0.01)
        assertEquals("D", r.sdc)
        assertEquals(1.0 + (ta - 0.5) / 2.0, r.k, 1e-9)
        assertEquals(r.v, r.levelForces.sum(), 1e-9)
        assertEquals(r.v, r.storyShears[0], 1e-9)
        assertEquals(r.levelForces[4], r.storyShears[4], 1e-9)
        assertTrue(r.levelForces[4] > r.levelForces[0])
        assertEquals(true, r.heightLimitOk)
    }

    @Test fun `analysis period is limited to Cu Ta`() {
        val r = SbcSeismic301.compute(frameInput(analysisT = 3.0))
        assertEquals(1.4, r.cu, 1e-9)
        assertEquals(1.4 * r.ta, r.t, 1e-9)
        assertTrue(r.periodNote.contains("12.8.2.2"))
    }

    @Test fun `upper limit Eq 12-14 governs for long period`() {
        // SDS 0.6, SD1 0.36, R 3 (steel OMF row C3), T = Ta = 0.0724*15^0.8 = 0.6307: Cs calc 0.2, upper 0.36/(0.6307*3) = 0.1903
        val r = SbcSeismic301.compute(frameInput(sys = "C3"))
        assertEquals(0.0724 * 15.0.pow(0.8), r.ta, 1e-9)
        assertEquals(0.2, r.csCalculated, 1e-9)
        assertTrue(r.csUpper < r.csCalculated)
        assertEquals(r.csUpper, r.cs, 1e-12)
        assertEquals("Eq 12-14 (upper limit)", r.csGoverning)
    }

    @Test fun `lower limit Eq 12-17 for S1 at least 0_6`() {
        // Ss 1.5, S1 0.7, site B: SDS = 1.0, SD1 = 0.4667, R 6.5 (C4). Ta 0.533 s. Calc 0.1538, upper 0.4667/(0.533*6.5)=0.1347 -> upper governs, 12-17: 0.5*0.7/6.5 = 0.0538
        val r = SbcSeismic301.compute(frameInput(ss = 1.5, s1 = 0.7, site = SbcSiteClass.B))
        assertEquals(1.0, r.sds, 1e-9)
        assertEquals(0.5 * 0.7 / 6.5, r.csLowerEq1217!!, 1e-9)
        assertEquals(r.csUpper, r.cs, 1e-12)
        assertEquals("D", r.sdc)   // S1 = 0.7 < 0.75, so Tables 11-3/11-4 apply
    }

    @Test fun `lower limit Eq 12-16 governs at low seismicity`() {
        // Ss 0.2, S1 0.08, site B: SDS 0.1333, SD1 0.0533, R 6.5, T 0.533: calc 0.0205, upper 0.0154, lower 0.044*0.1333=0.00587 -> 0.01 minimum? max(0.00587, 0.01) = 0.01
        val r = SbcSeismic301.compute(frameInput(ss = 0.2, s1 = 0.08, site = SbcSiteClass.B))
        assertEquals(0.01, r.csLowerEq1216, 1e-12)
        assertEquals(r.csUpper, r.cs, 1e-12)   // 0.0154 > 0.01
    }

    @Test fun `seismic design category`() {
        assertEquals("E", SbcSeismic301.seismicDesignCategory(SbcRiskCategory.II, 0.5, 0.3, 0.75).first)
        assertEquals("F", SbcSeismic301.seismicDesignCategory(SbcRiskCategory.IV, 0.5, 0.3, 0.8).first)
        assertEquals("C", SbcSeismic301.seismicDesignCategory(SbcRiskCategory.IV, 0.2, 0.1, 0.15).first)
        assertEquals("B", SbcSeismic301.seismicDesignCategory(SbcRiskCategory.II, 0.2, 0.1, 0.15).first)
        assertEquals("C", SbcSeismic301.seismicDesignCategory(SbcRiskCategory.II, 0.2, 0.15, 0.2).first)   // SD1 0.15 -> C governs over B
        assertEquals("A", SbcSeismic301.seismicDesignCategory(SbcRiskCategory.II, 0.1, 0.05, 0.07).first)
    }

    @Test fun `importance factors Table 1-3`() {
        assertEquals(1.0, SbcRiskCategory.II.ie, 0.0); assertEquals(1.25, SbcRiskCategory.III.ie, 0.0); assertEquals(1.5, SbcRiskCategory.IV.ie, 0.0)
        val r = SbcSeismic301.compute(frameInput(risk = SbcRiskCategory.III))
        assertEquals(0.6 / (6.5 / 1.25), r.csCalculated, 1e-9)
    }

    @Test fun `design spectrum Eq 11-5 to 11-8`() {
        val sds = 0.6; val sd1 = 0.36   // T0 = 0.12, Ts = 0.6
        assertEquals(0.6 * 0.4, SbcSeismic301.designSpectrum(0.0, sds, sd1, 8.0), 1e-9)
        assertEquals(0.6, SbcSeismic301.designSpectrum(0.3, sds, sd1, 8.0), 1e-9)
        assertEquals(0.36 / 1.0, SbcSeismic301.designSpectrum(1.0, sds, sd1, 8.0), 1e-9)
        assertEquals(0.36 * 8.0 / 100.0, SbcSeismic301.designSpectrum(10.0, sds, sd1, 8.0), 1e-9)
    }

    @Test fun `k exponent`() {
        assertEquals(1.0, SbcSeismic301.kExponent(0.4), 0.0); assertEquals(1.5, SbcSeismic301.kExponent(1.5), 1e-12); assertEquals(2.0, SbcSeismic301.kExponent(3.0), 0.0)
    }

    @Test fun `system not permitted in SDC D is reported`() {
        val r = SbcSeismic301.compute(frameInput(sys = "C6"))
        assertEquals(false, r.heightLimitOk)
        assertTrue(r.warnings.any { it.contains("NOT PERMITTED") })
    }

    @Test fun `height limit exceeded is reported`() {
        val i = frameInput(sys = "B2").copy(hn = 60.0, storyHeights = listOf(12.0, 24.0, 36.0, 48.0, 60.0))
        val r = SbcSeismic301.compute(i)
        assertEquals(50.0, r.heightLimit!!, 0.0)
        assertEquals(false, r.heightLimitOk)
    }

    @Test fun `invalid input is rejected with the field name`() {
        for (bad in listOf(frameInput(ss = 0.0), frameInput(s1 = -1.0), frameInput().copy(tl = 0.0), frameInput().copy(hn = 0.0),
            frameInput().copy(storyHeights = listOf(3.0, 3.0, 9.0, 12.0, 15.0)), frameInput().copy(storyWeights = listOf(1.0)))) {
            try { SbcSeismic301.compute(bad); fail("accepted bad input") } catch (e: IllegalArgumentException) { }
        }
    }

    @Test fun `drift and stability formulas`() {
        assertEquals(5.5 * 10.0 / 1.0, SbcSeismic301.amplifiedDeflection(10.0, 5.5, 1.0), 1e-12)
        assertEquals(0.5 / (1.0 * 5.5), SbcSeismic301.thetaMax(1.0, 5.5), 1e-12)
        assertEquals(0.25, SbcSeismic301.thetaMax(1.0, 1.5), 0.0)
        assertEquals(0.015, SbcSeismic301.allowableDriftRatioAllOthers(SbcRiskCategory.III), 0.0)
    }

    @Test fun `basic combinations Eq 2-1`() {
        val c = SbcLoadCombinations301.basic(SbcLoadEffects(d = 100.0, l = 50.0))
        assertEquals(140.0, c.first { it.id == "1" }.value, 1e-9)
        assertEquals(1.4 * 100 + 1.7 * 50, c.first { it.id.startsWith("2*") }.value, 1e-9)
        assertEquals(225.0, SbcLoadCombinations301.governingMax(c).value, 1e-9)
        val si = SbcLoadCombinations301.basic(SbcLoadEffects(d = 100.0, l = 50.0), specialInspection = true)
        assertEquals(200.0, si.first { it.id.startsWith("2 ") }.value, 1e-9)
    }

    @Test fun `wind combinations keep their own factors and both directions`() {
        val c = SbcLoadCombinations301.basic(SbcLoadEffects(d = 100.0, l = 50.0, w = 30.0))
        assertEquals(1.2 * 100 + 30 + 50, c.first { it.id.startsWith("4 (Lr, +W)") }.value, 1e-9)
        assertEquals(0.9 * 100 - 30, c.first { it.id == "6 (-W)" }.value, 1e-9)
        assertEquals(60.0, SbcLoadCombinations301.governingMin(c).value, 1e-9)    // 0.9D - W = 60
    }

    @Test fun `seismic combinations Eq 12-5 with vertical effect`() {
        val e = SbcLoadEffects(d = 100.0, l = 50.0, qe = 40.0)
        val c = SbcLoadCombinations301.seismic(e, sds = 0.6, rho = 1.0)
        assertEquals((1.2 + 0.12) * 100 + 40 + 50, c.first { it.id == "5 (+E)" }.value, 1e-9)
        assertEquals((0.9 - 0.12) * 100 - 40, c.first { it.id == "7 (-E)" }.value, 1e-9)
        val low = SbcLoadCombinations301.seismic(e, sds = 0.1, rho = 1.0)       // SDS <= 0.125, Ev = 0
        assertEquals(1.2 * 100 + 40 + 50, low.first { it.id == "5 (+E)" }.value, 1e-9)
        val om = SbcLoadCombinations301.seismic(e, sds = 0.6, rho = 1.0, omega0 = 2.5)
        assertEquals((1.2 + 0.12) * 100 + 2.5 * 40 + 50, om.first { it.id == "5 (+E)" }.value, 1e-9)
    }

    @Test fun `seismic combinations need rho`() {
        try { SbcLoadCombinations301.seismic(SbcLoadEffects(d = 1.0), 0.5, 0.0); fail() } catch (e: IllegalArgumentException) { }
    }
}
