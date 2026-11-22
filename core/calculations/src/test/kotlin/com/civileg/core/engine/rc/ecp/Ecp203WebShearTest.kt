package com.civileg.core.engine.rc.ecp

import kotlin.math.PI
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class Ecp203WebShearTest {
    private val e = Ecp203WebShear
    private val s = Ecp203WebShear.Section(30.0, 150.0, 250.0, 300.0, 50.0)
    private val links = Ecp203WebShear.VerticalLinks(400.0,
        Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED, 8.0, 2, 200.0)

    @Test fun `concrete-only capacity uses point16 not bent-bar point24 or ACI phi`() {
        val thin = s.copy(totalDepthMm = 250.0, effectiveDepthMm = 210.0)
        val r = e.assess(thin, 20.0)
        assertTrue(r.concreteOnlyRequired)
        assertEquals(0.16 * sqrt(20.0), r.concreteUncrackedStressMpa, 1e-12)
        assertEquals(22.53956521319788, r.shearResistanceKn, 1e-9)
        assertTrue(r.shearChecksPass)
    }

    @Test fun `links cannot rescue concrete-only sizing above its resistance`() {
        val thin = s.copy(totalDepthMm = 250.0, effectiveDepthMm = 210.0)
        val r = e.assess(thin, 25.0, links.copy(diameterMm = 20.0, spacingMm = 50.0))
        assertFalse(r.carriesShear)
        assertFalse(r.shearChecksPass)
    }

    @Test fun `depth rule takes maximum of250 two-point-five-tf and half-web`() {
        assertTrue(e.assess(s.copy(totalDepthMm = 250.0, effectiveDepthMm = 210.0), 1.0).concreteOnlyRequired)
        assertFalse(e.assess(s.copy(totalDepthMm = 250.1, effectiveDepthMm = 210.0), 1.0).concreteOnlyRequired)
        assertTrue(e.assess(s.copy(totalDepthMm = 300.0, flangeThicknessMm = 120.0), 1.0).concreteOnlyRequired)
        assertTrue(e.assess(s.copy(totalDepthMm = 400.0, webWidthMm = 900.0), 1.0).concreteOnlyRequired)
    }

    @Test fun `ordinary web needs minimum links even at zero shear`() {
        val r = e.assess(s, 0.0)
        assertFalse(r.concreteOnlyRequired)
        assertFalse(r.linksDetailingPass)
        assertFalse(r.shearChecksPass)
        assertNull(r.requiredLinksRatio)
    }

    @Test fun `ordinary cracked shear uses actual links and gamma-s`() {
        val r = e.assess(s, 30.0, links)
        assertEquals(0.8, r.designShearStressMpa, 1e-12)
        assertEquals(0.12 * sqrt(20.0), r.concreteCrackedStressMpa, 1e-12)
        assertEquals(2.0 * PI * 8.0 * 8.0 / 4.0 / (150.0 * 200.0), r.providedLinksRatio, 1e-12)
        assertEquals(0.001, r.minimumLinksRatio!!, 1e-12)
        assertTrue(r.linksDetailingPass)
        assertTrue(r.shearChecksPass)
        assertFalse(e.assess(s, 65.0, links).carriesShear)
    }

    @Test fun `required link ratio includes cracked concrete subtraction above uncracked threshold`() {
        val r = e.assess(s, 80.0, links)
        assertEquals((80e3 / (150.0 * 250.0) - 0.12 * sqrt(20.0)) / (400.0 / 1.15), r.requiredLinksRatio!!, 1e-12)
        assertTrue(r.requiredLinksRatio!! > r.providedLinksRatio)
    }

    @Test fun `design value of links yield is capped420 rather than rejecting500 material`() {
        val r = e.assess(s, 30.0, links.copy(fyMpa = 500.0))
        val reference = e.assess(s, 30.0, links.copy(fyMpa = 420.0))
        assertEquals(420.0, r.designLinksYieldMpa!!, 0.0)
        assertEquals(reference.shearResistanceKn, r.shearResistanceKn, 0.0)
    }

    @Test fun `shear compression limit cannot be overcome by adding links`() {
        val r = e.assess(s, 200.0, links.copy(diameterMm = 30.0, spacingMm = 30.0))
        assertFalse(r.withinConcreteMaximum)
        assertFalse(r.shearChecksPass)
        assertTrue(r.shearResistanceKn < 200.0)
    }

    @Test fun `maximum stress also has absolute four-point-four cap`() {
        assertEquals(4.4, e.assess(s.copy(fcuMpa = 100.0), 1.0, links).maximumShearStressMpa, 0.0)
    }

    @Test fun `minimum link diameter and200 spacing checked separately from strength`() {
        assertFalse(e.assess(s, 1.0, links.copy(diameterMm = 6.0)).linksDetailingPass)
        assertFalse(e.assess(s, 1.0, links.copy(spacingMm = 250.0)).linksDetailingPass)
        assertFalse(e.assess(s, 1.0, links.copy(legs = 1)).linksDetailingPass)
    }

    @Test fun `wide web needs at least four legs and a checked transverse layout`() {
        val wide = s.copy(webWidthMm = 400.0, effectiveDepthMm = 350.0, totalDepthMm = 450.0)
        assertFalse(e.assess(wide, 1.0, links).linksDetailingPass)
        assertFalse(e.assess(wide, 1.0, links.copy(legs = 4)).linksDetailingPass)
        assertFalse(e.assess(wide, 1.0, links.copy(legs = 4, maximumTransverseLegSpacingMm = 251.0)).linksDetailingPass)
        assertTrue(e.assess(wide, 1.0, links.copy(legs = 4, maximumTransverseLegSpacingMm = 120.0)).linksDetailingPass)
    }

    @Test fun `smooth240 links use explicit mild steel minimum`() {
        val r = e.assess(s, 1.0, links.copy(fyMpa = 240.0,
            surface = Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD))
        assertEquals(0.4 / 240.0, r.minimumLinksRatio!!, 1e-12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative shear is not silently accepted as magnitude`() { e.assess(s, -1.0, links) }

    @Test(expected = IllegalArgumentException::class)
    fun `nonfinite geometry rejected`() { e.assess(s.copy(webWidthMm = Double.NaN), 1.0, links) }

    @Test(expected = IllegalArgumentException::class)
    fun `zero effective depth rejected`() { e.assess(s.copy(effectiveDepthMm = 0.0), 1.0, links) }
}
