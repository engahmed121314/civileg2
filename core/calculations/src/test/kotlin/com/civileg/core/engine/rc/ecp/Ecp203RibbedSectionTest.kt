package com.civileg.core.engine.rc.ecp

import org.junit.Assert.*
import org.junit.Test

class Ecp203RibbedSectionTest {
    private fun context() = Ecp203RibbedSection.Context(
        25.0, 20.0, listOf(Ecp203BarLayout.Layer(2, 20.0)),
        Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED,
        Ecp203RibbedSection.Region.FLANGE_IN_COMPRESSION, 5000.0, 3.0,
        Ecp203WebShear.VerticalLinks(240.0, Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD,
            8.0, 2, 200.0))
    private fun assess(c: Ecp203RibbedSection.Context = context(), moment: Double = 62.0,
        shear: Double = 10.0, span: Double = 5000.0, width: Double = 200.0) =
        Ecp203RibbedSection.assess(30.0, 400.0, width, 600.0, 350.0, 60.0,
            span, moment, shear, c)

    @Test fun `independent coupled equilibrium uses actual bars cover and factored force once`() {
        // d=350-(25+8+20/2)=307. As=2*pi*20^2/4=628.31853.
        // fyd=400/1.15; B=min(16*60+200,5000/5+200,600)=600.
        // a=As*fyd/(.67*30/1.5*600)=27.1828 < .1d.
        // z=.95*307=291.65. MRd=As*fyd*z/1e6; Asreq=62e6/(fyd*z).
        val a = assess()
        assertEquals(307.0, a.layout.effectiveDepthMm!!, 1e-9)
        assertEquals(628.3185307179587, a.layout.providedSteelMm2, 1e-9)
        assertEquals(600.0, a.effectiveFlangeWidthMm, 1e-9)
        assertEquals(63.73881721178874, a.momentResistanceKnM, 1e-8)
        assertEquals(611.1777815875193, a.requiredSteelMm2!!, 1e-8)
        assertTrue(a.sectionChecksPass)
    }

    @Test fun `negative region uses web only not compression flange capacity`() {
        val positive = assess()
        val negative = assess(context().copy(region = Ecp203RibbedSection.Region.FLANGE_IN_TENSION,
            contraflexureDistanceMm = null))
        assertEquals(200.0, negative.effectiveFlangeWidthMm, 0.0)
        assertTrue(negative.momentResistanceKnM < positive.momentResistanceKnM)
        assertTrue(positive.flexureChecksPass)
        assertFalse(negative.flexureChecksPass)
    }
    @Test fun `cover changes actual centroid and decision`() {
        val moreCover = assess(context().copy(coverToOutsideLinksMm = 45.0))
        assertEquals(287.0, moreCover.layout.effectiveDepthMm!!, 1e-9)
        assertFalse(moreCover.flexureChecksPass)
    }
    @Test fun `same area in two real layers changes centroid and capacity`() {
        val two = assess(context().copy(tensionLayers = listOf(
            Ecp203BarLayout.Layer(1,20.0), Ecp203BarLayout.Layer(1,20.0))))
        assertEquals(assess().layout.providedSteelMm2, two.layout.providedSteelMm2, 1e-9)
        assertEquals(282.0, two.layout.effectiveDepthMm!!, 1e-9)
        assertFalse(two.flexureChecksPass)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `impossible bars do not get a fabricated effective depth`() {
        assess(context().copy(tensionLayers = listOf(Ecp203BarLayout.Layer(8,20.0))))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `compression flange requires actual contraflexure distance`() {
        assess(context().copy(contraflexureDistanceMm = null))
    }
    @Test fun `cross rib requirement uses characteristic live load independently of forces`() {
        assertEquals(Ecp203RibbedGeometry.CrossRibs.NONE, assess().requiredCrossRibs)
        assertEquals(Ecp203RibbedGeometry.CrossRibs.ONE_AT_MIDSPAN,
            assess(context().copy(characteristicLiveLoadKnM2 = 4.0)).requiredCrossRibs)
    }
    @Test fun `geometry failure cannot be hidden by passing strength`() {
        val a = assess(context().copy(coverToOutsideLinksMm = 0.0), width = 110.0, moment = 1.0)
        assertFalse(a.geometry.geometryChecksPass)
        assertFalse(a.sectionChecksPass)
    }
    @Test fun `shear failure cannot be hidden by passing moment`() {
        val a = assess(shear = 1000.0)
        assertTrue(a.flexureChecksPass)
        assertFalse(a.shear.shearChecksPass)
        assertFalse(a.sectionChecksPass)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `nonfinite factored demand is rejected`() { assess(moment = Double.NaN) }
    @Test fun `out of domain demand has no fabricated required steel`() {
        assertNull(assess(moment = 10000.0).requiredSteelMm2)
    }
}
