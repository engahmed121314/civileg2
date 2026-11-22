package com.civileg.core.engine.rc.ecp

import com.civileg.core.engine.rc.ecp.Ecp203RectangularFlexure.Section
import com.civileg.core.engine.rc.ecp.Ecp203RectangularFlexure.SteelSurface
import org.junit.Assert.*
import org.junit.Test

class Ecp203RectangularFlexureTest {
    private val engine = Ecp203RectangularFlexure
    private val section = Section(30.0, 400.0, 150.0, 250.0, 300.0, SteelSurface.HIGH_YIELD_DEFORMED)

    @Test fun `force-equilibrium vector uses cube strength and both material factors`() {
        // Independent hand vector: a=50mm, C=(0.67*30/1.5)*150*50=100500N.
        // z=250-25=225mm, M=22.6125kNm, As=C/(400/1.15)=288.9375mm2.
        val result = engine.assess(section, 22.6125)
        assertEquals(225.0, result.leverArmMm!!, 1e-9)
        assertEquals(288.9375, result.demandSteelMm2!!, 1e-8)
        assertTrue(result.withinSinglyReinforcedDomain)
        val provided = engine.checkProvided(section, 22.6125, 288.9375)
        assertEquals(50.0, provided.compressionBlockDepthMm, 1e-9)
        assertEquals(62.5, provided.neutralAxisDepthMm, 1e-9)
        assertEquals(22.6125, provided.momentFromEquilibriumKnM, 1e-9)
        assertTrue(provided.flexureChecksPass)
    }

    @Test fun `low moment never uses lever arm above point95d`() {
        val r = engine.assess(section, 0.1)
        assertEquals(237.5, r.leverArmMm!!, 1e-10)
        assertEquals(0.1e6 / ((400.0 / 1.15) * 237.5), r.demandSteelMm2!!, 1e-10)
        assertTrue(r.requiredSteelMm2!! >= r.minimumSteelMm2)
    }

    @Test fun `minimum ratio includes square root term and one point one over fy`() {
        assertEquals(115.53522697374598, engine.minimumSteelMm2(section), 1e-9)
        val weak = section.copy(fcuMpa = 20.0)
        assertEquals(103.125, engine.minimumSteelMm2(weak), 1e-9)
    }

    @Test fun `absolute floor uses gross depth and concrete strength boundary`() {
        val thinEffectiveSection = section.copy(effectiveDepthMm = 100.0, totalDepthMm = 1000.0)
        assertEquals(225.0, engine.minimumSteelMm2(thinEffectiveSection.copy(fcuMpa = 29.99)), 1e-9)
        assertEquals(270.0, engine.minimumSteelMm2(thinEffectiveSection.copy(fcuMpa = 30.0)), 1e-9)
        assertEquals(375.0, engine.minimumSteelMm2(thinEffectiveSection.copy(fyMpa = 240.0,
            steelSurface = SteelSurface.SMOOTH_MILD)), 1e-9)
    }

    @Test fun `Rmax is not normalized K without gammaC`() {
        val s = section.copy(fyMpa = 420.0, effectiveDepthMm = 270.0)
        val moment = 0.15 * 30.0 * 150.0 * 270.0 * 270.0 / 1e6
        val r = engine.assess(s, moment)
        assertEquals(0.15, r.normalizedMoment, 1e-12)
        assertTrue(r.normalizedMoment < r.tableLimits.rMax)
        assertTrue(r.normalizedMoment > r.tableLimits.rMax / engine.GAMMA_C)
        assertFalse(r.withinSinglyReinforcedDomain)
    }

    @Test fun `all printed rounded table maxima are respected`() {
        for (fy in listOf(240.0, 350.0, 400.0, 420.0, 500.0)) {
            val s = section.copy(fyMpa = fy)
            val r = engine.assess(s, 0.0)
            val q = engine.checkProvided(s, 0.0, r.maximumSinglyReinforcedSteelMm2)
            assertTrue(q.neutralAxisDepthMm / s.effectiveDepthMm <= r.tableLimits.cOverDMax + 1e-10)
            assertTrue(r.maximumSinglyReinforcedSteelMm2 <= r.tableLimits.muMaxCoefficient * s.fcuMpa * s.widthMm * s.effectiveDepthMm + 1e-8)
            assertTrue(r.maximumSinglyReinforcedMomentKnM * 1e6 / (s.fcuMpa * s.widthMm * s.effectiveDepthMm * s.effectiveDepthMm) <= r.tableLimits.rMax / engine.GAMMA_C + 1e-10)
        }
    }

    @Test fun `bar area underprovision cannot pass with low shear elsewhere`() {
        assertFalse(engine.checkProvided(section, 22.6125, 200.0).flexureChecksPass)
    }

    @Test fun `adding excessive steel does not turn an overreinforced section into a pass`() {
        val r = engine.assess(section, 22.6125)
        val q = engine.checkProvided(section, 22.6125, r.maximumSinglyReinforcedSteelMm2 * 1.5)
        assertFalse(q.withinMaximumSteel)
        assertFalse(q.withinNeutralAxisLimit)
        assertFalse(q.flexureChecksPass)
    }

    @Test fun `impossible flexure returns no fabricated area or fallback lever arm`() {
        val r = engine.assess(section, 1000.0)
        assertNull(r.demandSteelMm2)
        assertNull(r.requiredSteelMm2)
        assertNull(r.leverArmMm)
        assertFalse(r.withinSinglyReinforcedDomain)
    }

    @Test fun `demand increases required steel monotonically inside domain`() {
        var previous = 0.0
        val limit = engine.assess(section, 0.0).maximumSinglyReinforcedMomentKnM
        for (step in 0..20) {
            val r = engine.assess(section, limit * step / 20.0)
            assertTrue(r.requiredSteelMm2!! + 1e-9 >= previous)
            previous = r.requiredSteelMm2!!
        }
    }

    @Test fun `zero moment still requires minimum reinforcement`() {
        val r = engine.assess(section, 0.0)
        assertEquals(r.minimumSteelMm2, r.requiredSteelMm2!!, 0.0)
        assertFalse(engine.checkProvided(section, 0.0, 0.0).flexureChecksPass)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported grade is not silently interpolated`() { engine.assess(section.copy(fyMpa = 415.0), 1.0) }

    @Test fun `fy500 concrete strength preference is not changed into a mandatory ban`() {
        assertTrue(engine.assess(section.copy(fyMpa = 500.0, fcuMpa = 25.0), 1.0).withinSinglyReinforcedDomain)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `smooth mild steel is not inferred from arbitrary high fy`() { engine.assess(section.copy(steelSurface = SteelSurface.SMOOTH_MILD), 1.0) }

    @Test(expected = IllegalArgumentException::class)
    fun `effective depth cannot equal overall depth`() { engine.assess(section.copy(effectiveDepthMm = 300.0), 1.0) }

    @Test(expected = IllegalArgumentException::class)
    fun `negative moment is not silently changed to sagging`() { engine.assess(section, -1.0) }

    @Test(expected = IllegalArgumentException::class)
    fun `nonfinite input rejected`() { engine.assess(section.copy(fcuMpa = Double.NaN), 1.0) }
    @Test fun `reinforced concrete source range includes both endpoints`() {
        assertTrue(engine.assess(section.copy(fcuMpa = 20.0), 1.0).withinSinglyReinforcedDomain)
        assertTrue(engine.assess(section.copy(fcuMpa = 60.0), 1.0).withinSinglyReinforcedDomain)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `reinforced concrete below source minimum is rejected`() {
        engine.assess(section.copy(fcuMpa = 19.999), 1.0)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `reinforced concrete above source maximum is rejected`() {
        engine.assess(section.copy(fcuMpa = 60.001), 1.0)
    }

}
