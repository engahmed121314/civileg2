package com.civileg.core.engine.rc.ecp

import org.junit.Assert.*
import org.junit.Test

class Ecp203TSectionFlexureTest {
    private val rectangle = Ecp203RectangularFlexure.Section(30.0, 400.0, 150.0, 300.0, 350.0,
        Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED)
    private val t = Ecp203TSectionFlexure.Section(rectangle, 600.0, 50.0)
    private val engine = Ecp203TSectionFlexure

    @Test fun `compression in flange and web matches independent force and first moment sums`() {
        // a80: C=13.4*(600*50+150*30)=462300N.
        // M=13.4*(600*50*275+150*30*235)=124720500Nmm.
        val r = engine.assess(t, 124.7205)
        assertTrue(r.withinSinglyReinforcedDomain)
        assertEquals(1329.1125, r.requiredSteelMm2!!, 1e-7)
        assertEquals(80.0, r.compressionBlockDepthMm!!, 1e-8)
        assertEquals(100.0, r.neutralAxisDepthMm!!, 1e-8)
        val q = engine.checkProvided(t, 124.7205, 1329.1125)
        assertTrue(q.flexureChecksPass)
        assertEquals(124.7205, q.momentResistanceKnM, 1e-8)
    }

    @Test fun `block entirely in flange matches wide rectangular force equilibrium`() {
        // a30: C=13.4*600*30=241200N, z285, M68.742kNm.
        val r = engine.assess(t, 68.742)
        assertEquals(693.45, r.requiredSteelMm2!!, 1e-8)
        assertEquals(30.0, r.compressionBlockDepthMm!!, 1e-8)
    }

    @Test fun `zero flange overhang reduces to web rectangular section`() {
        val moment = 20.0
        val a = engine.assess(t.copy(effectiveFlangeWidthMm = 150.0), moment)
        val b = Ecp203RectangularFlexure.assess(rectangle, moment)
        assertEquals(b.requiredSteelMm2!!, a.requiredSteelMm2!!, 1e-8)
    }

    @Test fun `minimum reinforcement uses web area rather than flange area`() {
        assertEquals(Ecp203RectangularFlexure.minimumSteelMm2(rectangle), engine.assess(t, 0.0).minimumSteelMm2, 1e-10)
        assertTrue(engine.assess(t, 0.0).minimumSteelMm2 < Ecp203RectangularFlexure.minimumSteelMm2(rectangle.copy(widthMm = 600.0)))
    }

    @Test fun `low demand still obeys lever arm ceiling`() {
        val r = engine.assess(t, 1.0)
        val minimum = r.minimumSteelMm2
        val q = engine.checkProvided(t, 1.0, minimum)
        assertTrue(q.flexureChecksPass)
        val lowProvided = 1e6 / ((400.0 / 1.15) * (0.95 * 300.0))
        assertEquals(1.0, engine.checkProvided(t, 1.0, lowProvided).momentResistanceKnM, 1e-9)
        assertFalse(engine.checkProvided(t, 1.0, lowProvided).meetsMinimumSteel)
    }

    @Test fun `moment above ductility limit produces no proposed required reinforcement`() {
        val limit = engine.assess(t, 0.0).maximumMomentKnM
        val r = engine.assess(t, limit * 1.01)
        assertFalse(r.withinSinglyReinforcedDomain)
        assertNull(r.requiredSteelMm2)
        assertFalse(engine.checkProvided(t, limit * 1.01, 4000.0).flexureChecksPass)
    }

    @Test fun `provided area remains a separate check from area required`() {
        assertFalse(engine.checkProvided(t, 124.7205, 1200.0).carriesMoment)
        assertFalse(engine.checkProvided(t, 124.7205, 4000.0).withinNeutralAxisLimit)
    }

    @Test fun `effective width separately obeys topping contraflexure and available-width bounds`() {
        assertEquals(470.0, engine.effectiveFlangeWidthMm(150.0, 20.0, 6000.0, 1000.0), 0.0)
        assertEquals(390.0, engine.effectiveFlangeWidthMm(150.0, 50.0, 1200.0, 1000.0), 0.0)
        assertEquals(600.0, engine.effectiveFlangeWidthMm(150.0, 50.0, 6000.0, 600.0), 0.0)
    }

    @Test fun `response is continuous as compression passes flange boundary`() {
        val boundaryMoment = 13.4 * 600.0 * 50.0 * 275.0 / 1e6
        val lower = engine.assess(t, boundaryMoment - 1e-6)
        val upper = engine.assess(t, boundaryMoment + 1e-6)
        assertTrue(lower.compressionBlockDepthMm!! < 50.0)
        assertTrue(upper.compressionBlockDepthMm!! > 50.0)
        assertEquals(lower.requiredSteelMm2!!, upper.requiredSteelMm2!!, 0.001)
    }

    @Test fun `required area is monotonic throughout supported domain`() {
        var previous = 0.0
        val maxMoment = engine.assess(t, 0.0).maximumMomentKnM
        for (step in 0..30) {
            val r = engine.assess(t, maxMoment * step / 30.0)
            assertTrue(r.withinSinglyReinforcedDomain)
            assertTrue(r.requiredSteelMm2!! + 1e-8 >= previous)
            previous = r.requiredSteelMm2!!
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `flange narrower than web is not a T section`() { engine.assess(t.copy(effectiveFlangeWidthMm = 100.0), 1.0) }

    @Test(expected = IllegalArgumentException::class)
    fun `zero topping is not accepted as a monolithic flange`() { engine.assess(t.copy(flangeThicknessMm = 0.0), 1.0) }

    @Test(expected = IllegalArgumentException::class)
    fun `negative demand is not silently interpreted as positive T bending`() { engine.assess(t, -1.0) }
}
