package com.civileg.app.domain.calculations.sbc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SbcSeismic301AdapterTest {
    private val input = SbcElfInput(0.75, 0.3, 8.0, SbcSiteClass.D, SbcRiskCategory.II, SbcSeismicSystems.byCode("C4"), 15.0,
        List(5) { 2000.0 }, listOf(3.0, 6.0, 9.0, 12.0, 15.0))

    @Test fun `adapter keeps the engine numbers and gives level overturning`() {
        val r = SbcSeismic301.compute(input)
        val f = SbcSeismic301Adapter.floorForces(input, r)
        assertEquals(5, f.size)
        assertEquals(r.v, f[0].storyShear, 1e-9)
        assertEquals(0.0, f[4].overturningMoment, 1e-9)
        // moment at level 1 = sum F_j (h_j - 3)
        val expect = (1..4).sumOf { r.levelForces[it] * (input.storyHeights[it] - 3.0) }
        assertEquals(expect, f[0].overturningMoment, 1e-6)
        val bs = SbcSeismic301Adapter.baseShear(input, r)
        assertEquals(r.v, bs.baseShear, 0.0); assertEquals(0.75, bs.zoneFactor, 0.0); assertEquals(1.2, bs.soilFactor, 1e-9)
        val sp = SbcSeismic301Adapter.spectrum(input, r)
        assertEquals(50, sp.size)
        assertTrue(sp.all { it.spectralAcceleration > 0 })
        assertEquals(0.6, SbcSeismic301Adapter.spectralAccelAtT(input, r), 1e-9) // T 0.533 s is inside Ts = 0.6 s
    }
}
