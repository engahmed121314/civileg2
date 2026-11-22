package com.civileg.core.engine.rc.ecp

import org.junit.Assert.*
import org.junit.Test

class Ecp203LayeredFlexureTest {
    private val surface = Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED
    private fun assess(fcu: Double = 30.0, width: Double = 200.0, phi: Double = 8.0,
        count: Int = 7, demand: Double = 21.5, layers: List<Ecp203BarLayout.Layer>? = null
    ): Ecp203LayeredFlexure.Assessment {
        val actual = layers ?: List(count) { Ecp203BarLayout.Layer(1, phi) }
        val bars = Ecp203BarLayout.assess(200.0, 350.0, 25.0, 8.0, 20.0, actual)
        val section = Ecp203RectangularFlexure.Section(fcu, 400.0, 200.0,
            requireNotNull(bars.effectiveDepthMm), 350.0, surface)
        return Ecp203LayeredFlexure.assess(section, width, 60.0, actual,
            bars.layerCentersFromTensionFaceMm, demand)
    }
    @Test fun rectangularSevenLayerIndependentEquilibrium() {
        val a = assess()
        assertEquals(56.15412210042254, a.neutralAxisDepthMm, 1e-8)
        assertEquals(21.481219193360015, a.momentResistanceKnM, 1e-8)
        assertEquals(308.2147150087178, a.layerStressMpa.last(), 1e-7)
        assertFalse(a.allLayersAtDesignYield)
        assertFalse(a.flexureChecksPass)
    }
    @Test fun centroidYieldApproximationWouldIncorrectlyPass() {
        val bars = Ecp203BarLayout.assess(200.0, 350.0, 25.0, 8.0, 20.0,
            List(7) { Ecp203BarLayout.Layer(1, 8.0) })
        val s = Ecp203RectangularFlexure.Section(30.0, 400.0, 200.0,
            bars.effectiveDepthMm!!, 350.0, surface)
        assertTrue(Ecp203RectangularFlexure.checkProvided(s, 21.5, bars.providedSteelMm2).flexureChecksPass)
        assertFalse(assess().flexureChecksPass)
    }
    @Test fun tSectionIndependentEquilibriumIncludesElasticInnerLayer() {
        val a = assess(fcu = 20.0, width = 600.0, phi = 12.0, demand = 43.6)
        assertEquals(55.91673396584889, a.neutralAxisDepthMm, 1e-8)
        assertEquals(43.479898502792096, a.equilibriumMomentKnM, 1e-8)
        assertEquals(42.139745384407185, a.momentResistanceKnM, 1e-8)
        assertEquals(33.08418588289736, a.layerStressMpa.last(), 1e-7)
        assertFalse(a.flexureChecksPass)
    }
    @Test fun smallerDemandPassesCompatibleLayeredSection() {
        assertTrue(assess(demand = 21.4).flexureChecksPass)
    }
    @Test fun requiredSteelReflectsLayeredCompatibility() {
        val fail = assess()
        val pass = assess(demand = 21.4)
        val area = 7.0 * Math.PI * 8.0 * 8.0 / 4.0
        assertTrue(fail.requiredSteelMm2!! > area)
        assertTrue(pass.requiredSteelMm2!! < area)
    }
    @Test fun singleLayerKeepsExistingGoldenResult() {
        val a = assess(width = 600.0, demand = 62.0,
            layers = listOf(Ecp203BarLayout.Layer(2, 20.0)))
        assertEquals(63.73881721178874, a.momentResistanceKnM, 1e-8)
        assertEquals(611.1777815875193, a.requiredSteelMm2!!, 1e-7)
        assertTrue(a.allLayersAtDesignYield)
        assertTrue(a.flexureChecksPass)
    }
    @Test fun stressesUseSourceStrainAndSteelModulus() {
        val a = assess()
        val depths = listOf(313.0, 275.0, 237.0, 199.0, 161.0, 123.0, 85.0)
        depths.indices.forEach { i ->
            val expected = minOf(400.0 / 1.15, 200000.0 * 0.003 * (depths[i] - a.neutralAxisDepthMm) / a.neutralAxisDepthMm)
            assertEquals(expected, a.layerStressMpa[i], 1e-9)
        }
    }
    @Test fun zeroDemandStillEnforcesSourceMinimumSteel() {
        val a = assess(demand = 0.0)
        assertTrue(a.requiredSteelMm2!! > 0.0)
        assertTrue(a.flexureChecksPass)
    }
    @Test fun demandAboveSupportedEnvelopeHasNoFabricatedRequiredArea() {
        assertNull(assess(demand = 10000.0).requiredSteelMm2)
    }
    @Test(expected = IllegalArgumentException::class)
    fun layersCrossingNeutralAxisNeedMixedReinforcementMethod() {
        assess(count = 8)
    }
    @Test(expected = IllegalArgumentException::class)
    fun declaredDepthMustMatchActualLayerCentroid() {
        Ecp203LayeredFlexure.assess(Ecp203RectangularFlexure.Section(30.0, 400.0,
            200.0, 300.0, 350.0, surface), 600.0, 60.0,
            listOf(Ecp203BarLayout.Layer(2, 20.0)), listOf(43.0), 10.0)
    }
}
