package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.BoundaryConstraints
import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Isolated footing: biaxial bearing, net punching force, overburden guard (review/foundations-20261126). */
class ECPFootingFoundationFixesTest {
    private val footing = ECPFooting()

    private fun design(axial: Double = 1500.0, mx: Double = 0.0, my: Double = 0.0, sbc: Double = 200.0, depth: Double = 500.0) =
        footing.designIsolatedFooting(
            25.0, 360.0, 400.0, 400.0, axial, mx, my, sbc, depth,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )

    @Test
    fun biaxialMomentAddsBothEccentricities() {
        val r = design(mx = 150.0, my = 150.0)
        val factor = LoadCombination.DEAD_LIVE.getFactorForCode(com.civileg.app.domain.entities.DesignCode.ECP)
        val p = 1500.0 / factor
        val e = 150.0 / factor / p
        val b = r.requiredWidth / 1000.0
        val l = r.requiredLength / 1000.0
        val qAvg = p / (b * l)
        val expected = qAvg * (1 + 6 * e / b + 6 * e / l)
        assertEquals(expected, r.maxSoilPressure, 0.5)
    }

    @Test
    fun footingGrowsUntilEdgePressureWithinAllowable() {
        val r = design(mx = 200.0, my = 200.0)
        assertTrue("edge pressure ${r.maxSoilPressure} must not exceed 200 kPa", r.maxSoilPressure <= 200.0 + 1e-6)
    }

    @Test
    fun punchingUsesNetForceBelowColumnLoad() {
        val r = design()
        val c = r.punchingShearCheck
        val d = 2.0 * c.criticalSection
        val forceKn = c.appliedShear * c.criticalPerimeter * d / 1000.0
        assertTrue("net punching force $forceKn must be below the 1500 kN column load", forceKn < 1500.0)
        assertTrue("and still positive", forceKn > 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun overburdenAboveAllowableBearingIsRejected() {
        design(sbc = 10.0, depth = 500.0)
    }
}
