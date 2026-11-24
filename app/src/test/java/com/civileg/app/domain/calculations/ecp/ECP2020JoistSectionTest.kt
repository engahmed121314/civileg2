package com.civileg.app.domain.calculations.ecp

import org.junit.Assert.*
import org.junit.Test

class ECP2020JoistSectionTest {
    private val p = ECP2020JoistSection.Input(30.0, 400.0, 150.0, 600.0, 50.0, 300.0, 25.0, 8.0, 16, 2, 20.0)

    @Test fun `effective depth follows actual bar and link geometry`() {
        assertEquals(259.0, ECP2020JoistSection.check(p).effectiveDepthMm, 0.0)
        assertEquals(257.0, ECP2020JoistSection.check(p.copy(mainBarDiameterMm = 20)).effectiveDepthMm, 0.0)
    }
    @Test fun `flange block capacity agrees with independent force equilibrium`() {
        val r = ECP2020JoistSection.check(p)
        val force = (2 * Math.PI * 16 * 16 / 4) * 400 / 1.15
        val a = force / (0.67 * 30 / 1.5 * 600)
        assertEquals(a, r.blockDepthMm, 1e-8)
        assertTrue(a < 50)
        assertEquals(force * minOf(0.95 * 259, 259 - a / 2) / 1e6, r.momentResistanceKnM, 1e-8)
    }
    @Test fun `block beyond flange includes web contribution and first moment`() {
        val q = p.copy(effectiveFlangeWidthMm = 200.0, toppingMm = 20.0, barsPerRib = 2)
        val r = ECP2020JoistSection.check(q)
        val stress = 0.67 * 30 / 1.5
        val force = 2 * Math.PI * 16 * 16 / 4 * 400 / 1.15
        val webDepth = (force - stress * 200 * 20) / (stress * 150)
        assertEquals(20 + webDepth, r.blockDepthMm, 1e-8)
        val firstMoment = stress * 200 * 20 * 10 + stress * 150 * webDepth * (20 + webDepth / 2)
        assertEquals((force * 259 - firstMoment) / 1e6, r.momentResistanceKnM, 1e-8)
    }
    @Test fun `high demand has no fabricated required area solution`() {
        val r = ECP2020JoistSection.check(p.copy(ultimatePositiveMomentKnM = 1000.0))
        assertNull(r.requiredFlexuralAreaMm2)
        assertNull(r.requiredAreaWithMinimumMm2)
        assertFalse(r.withinSinglyReinforcedLimit)
        assertFalse(r.sectionPasses)
    }
    @Test fun `excess steel cannot pass a ductility limit`() {
        val r = ECP2020JoistSection.check(p.copy(barsPerRib = 20))
        assertFalse(r.withinSinglyReinforcedLimit)
        assertFalse(r.sectionPasses)
        assertEquals(r.singlyReinforcedLimitKnM, r.momentResistanceKnM, 0.0)
    }
    @Test fun `insufficient steel cannot pass on moment alone`() {
        val r = ECP2020JoistSection.check(p.copy(mainBarDiameterMm = 8, barsPerRib = 1, ultimatePositiveMomentKnM = 10.0))
        assertFalse(r.areaSufficient)
        assertFalse(r.sectionPasses)
    }
    @Test(expected = IllegalArgumentException::class) fun `negative moment is not relabeled positive`() {
        ECP2020JoistSection.check(p.copy(ultimatePositiveMomentKnM = -10.0))
    }
    @Test(expected = IllegalArgumentException::class) fun `unknown flange width is not inferred from pitch`() {
        ECP2020JoistSection.check(p.copy(effectiveFlangeWidthMm = Double.NaN))
    }
    @Test fun `rectangular rib demand inversion agrees with closed form across material grades`() {
        // Independent quadratic solution for a rectangular compression block,
        // including the separately imposed 0.95d lever-arm cap.
        for (fcu in listOf(20.0, 30.0, 60.0)) for (fy in listOf(350.0, 400.0, 420.0, 500.0)) {
            if (fy == 500.0 && fcu < 30.0) continue
            for (m in listOf(0.1, 1.0, 3.0, 5.0)) {
                val q = p.copy(fcuMpa = fcu, fyMpa = fy, effectiveFlangeWidthMm = 150.0,
                    ultimatePositiveMomentKnM = m)
                val r = ECP2020JoistSection.check(q)
                val stress = 0.67 * fcu / 1.5
                val d = 259.0
                val aUncapped = d - kotlin.math.sqrt(d * d - 2 * m * 1e6 / (stress * 150))
                val a = if (aUncapped < 0.1 * d) m * 1e6 / (stress * 150 * 0.95 * d) else aUncapped
                val expected = stress * 150 * a / (fy / 1.15)
                assertEquals("fcu=$fcu fy=$fy M=$m", expected, r.requiredFlexuralAreaMm2!!, 1e-7)
            }
        }
    }
    @Test fun `compression block transition at flange interface is continuous`() {
        val f = 0.67 * 30 / 1.5
        val area = 2 * Math.PI * 16 * 16 / 4
        val exact = area * (400 / 1.15) / (f * 600)
        val below = ECP2020JoistSection.check(p.copy(toppingMm = exact - 1e-7))
        val above = ECP2020JoistSection.check(p.copy(toppingMm = exact + 1e-7))
        assertEquals(below.momentResistanceKnM, above.momentResistanceKnM, 1e-5)
        assertEquals(below.blockDepthMm, above.blockDepthMm, 1e-5)
    }
    @Test fun `zero moment still retains the independent gross web minimum`() {
        val r = ECP2020JoistSection.check(p.copy(ultimatePositiveMomentKnM = 0.0))
        assertEquals(0.0, r.requiredFlexuralAreaMm2!!, 1e-8)
        assertEquals(81.0, r.requiredAreaWithMinimumMm2!!, 1e-8)
    }
    @Test fun `increasing demand cannot decrease required steel within the singly reinforced range`() {
        var last = 0.0
        for (step in 0..100) {
            val r = ECP2020JoistSection.check(p.copy(ultimatePositiveMomentKnM = step * 0.25))
            val area = r.requiredFlexuralAreaMm2 ?: continue
            assertTrue(area >= last)
            last = area
        }
    }
}
