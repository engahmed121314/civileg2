package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.ecp.ECP2025Deflection as D
import org.junit.Assert.*
import org.junit.Test

/** Hand-checked against ECP 203-2025 printed pp. 4-60..4-66 (see docs/evidence/ecp2025-deflection.md). */
class ECP2025DeflectionTest {
    private val eps = 1e-6

    @Test fun fctr_eq471bc() {
        assertEquals(0.6 * Math.sqrt(30.0), D.fctr(30.0, 400.0), eps)
        assertEquals(3.286335, D.fctr(30.0, 400.0), 1e-5)
        assertEquals(0.37 * Math.sqrt(30.0), D.fctr(30.0, 600.0), eps)
    }

    @Test fun mcr_eq471a_slab_strip() {
        val ig = 1000.0 * 250.0 * 250.0 * 250.0 / 12.0
        assertEquals(3.42326e7, D.crackingMoment(D.fctr(30.0, 400.0), ig, 125.0), 2e3)
    }

    @Test fun ie_eq470() {
        // Mcr/Ma = 0.5: 0.125*100 + 0.875*40 = 47.5
        assertEquals(47.5, D.effectiveInertia(50.0, 100.0, 100.0, 40.0), eps)
        assertEquals(100.0, D.effectiveInertia(120.0, 100.0, 100.0, 40.0), eps) // uncracked guard
        assertEquals(D.effectiveInertia(50.0, 100.0, 100.0, 100.0), D.effectiveInertia(50.0, 100.0, 100.0, 250.0), eps) // Icr capped at Ig
    }

    @Test fun ie_continuous_eq472() {
        assertEquals(50.0, D.continuousInertiaBothEnds(50.0, 40.0, 60.0), eps)
        assertEquals(48.5, D.continuousInertiaOneEnd(50.0, 40.0), eps)
    }

    @Test fun longTerm_eq473() {
        assertEquals(1.0, D.timeFactorXi(3)!!, eps)
        assertEquals(1.2, D.timeFactorXi(6)!!, eps)
        assertEquals(1.4, D.timeFactorXi(12)!!, eps)
        assertEquals(2.0, D.timeFactorXi(60)!!, eps)
        assertEquals(2.0, D.timeFactorXi(120)!!, eps)
        assertNull("Fig 4-31 intermediate values are not tabulated", D.timeFactorXi(7))
        assertEquals(2.0 / 1.5, D.longTermMultiplier(2.0, 0.01), eps)
        assertEquals(2.0, D.longTermMultiplier(2.0, 0.0), eps)
        assertEquals(10.0 + 2.0 * 6.0, D.totalDeflection(10.0, 6.0, 2.0), eps)
    }

    @Test fun limits_eq474to479() {
        assertEquals(24.0, D.allowable(D.Limit.TOTAL_ONE_OR_TWO_WAY, 6000.0), eps)
        assertEquals(6000.0 / 450.0, D.allowable(D.Limit.CANTILEVER_TOTAL, 6000.0), eps)
        assertEquals(6000.0 / 360.0, D.allowable(D.Limit.LIVE_IMMEDIATE, 6000.0), eps)
        assertEquals(12.5, D.allowable(D.Limit.INCREMENTAL_SENSITIVE, 6000.0), eps)
        assertEquals(25.0, D.allowable(D.Limit.INCREMENTAL_NOT_SENSITIVE, 6000.0), eps)
        assertTrue(D.check(D.Limit.TOTAL_ONE_OR_TWO_WAY, 24.0, 6000.0).ok)
        assertFalse(D.check(D.Limit.TOTAL_ONE_OR_TWO_WAY, 24.01, 6000.0).ok)
    }

    @Test fun eq477_and_eq480() {
        assertEquals(4.0, D.liveImmediate(10.0, 6.0), eps)
        // (5+1)*(2.0-0.5) + (4-1) = 12
        assertEquals(12.0, D.incrementalDeflection(5.0, 1.0, 2.0, 0.5, 4.0), eps)
    }

    @Test fun table410_eq481() {
        assertEquals(31.0, D.spanToDepthRatio(D.Element.SOLID_SLAB, D.Support.BOTH_ENDS_CONTINUOUS, 420.0)!!, eps)
        assertEquals(10.0, D.spanToDepthRatio(D.Element.SOLID_SLAB, D.Support.CANTILEVER, 420.0)!!, eps)
        assertEquals(23.0, D.spanToDepthRatio(D.Element.RIBBED_SLAB_OR_BURIED_BEAM, D.Support.ONE_END_CONTINUOUS, 420.0)!!, eps)
        assertEquals(12.0, D.spanToDepthRatio(D.Element.RIGID_BEAM, D.Support.SIMPLE, 420.0)!!, eps)
        assertEquals(31.0 / (0.4 + 500.0 / 700.0), D.spanToDepthRatio(D.Element.SOLID_SLAB, D.Support.BOTH_ENDS_CONTINUOUS, 500.0)!!, eps)
        assertNull(D.spanToDepthRatio(D.Element.SOLID_SLAB, D.Support.SIMPLE, 600.0))
    }

    @Test fun eq482_slabOnBeams() {
        // a=4000 b=5000 fy=420 betaP=0.5: 4000*(0.85+0.2625)/(15+20+5) = 111.25
        assertEquals(111.25, D.twoWaySlabOnBeamsMinThickness(4000.0, 5000.0, 420.0, 0.5, 3.0, 5000.0, false)!!, eps)
        assertEquals(100.0, D.twoWaySlabOnBeamsMinThickness(1500.0, 2000.0, 420.0, 0.5, 3.0, 2000.0, false)!!, eps)
        assertNull(D.twoWaySlabOnBeamsMinThickness(5500.0, 6500.0, 420.0, 0.5, 3.0, 6500.0, false))
        assertNull(D.twoWaySlabOnBeamsMinThickness(4000.0, 5000.0, 420.0, 0.5, 6.0, 5000.0, false))
    }

    @Test fun tBeamFactor_Fig4_32() {
        assertEquals(0.80, D.tBeamFactor(100.0, 500.0), 1e-9)   // b/B = 0.20
        assertEquals(0.80, D.tBeamFactor(150.0, 500.0), 1e-9)   // b/B = 0.30
        assertEquals(1.00, D.tBeamFactor(500.0, 500.0), 1e-9)
        assertEquals(0.80 + 0.20 * (0.65 - 0.30) / 0.70, D.tBeamFactor(650.0, 1000.0), 1e-9)
    }

    @Test fun spanToDepth_otherSteelDividedByEq481() {
        assertEquals(12.0 / (0.4 + 360.0 / 700.0), D.spanToDepthRatio(D.Element.RIGID_BEAM, D.Support.SIMPLE, 360.0)!!, 1e-9)
        assertEquals(12.0 / (0.4 + 450.0 / 700.0), D.spanToDepthRatio(D.Element.RIGID_BEAM, D.Support.SIMPLE, 450.0)!!, 1e-9)
    }
}
