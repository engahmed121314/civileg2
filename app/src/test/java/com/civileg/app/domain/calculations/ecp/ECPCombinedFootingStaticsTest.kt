package com.civileg.app.domain.calculations.ecp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Combined footing: footing centred on the resultant (no spurious eccentricity), strip shear/moment analysis. */
class ECPCombinedFootingStaticsTest {
    private val engine = ECPCombinedFooting()

    private fun run(p1: Double, p2: Double, dist: Double = 4000.0, t: Double = 700.0) = engine.design(
        p1 = p1, p2 = p2, col1Width = 400.0, col1Depth = 400.0, col2Width = 400.0, col2Depth = 400.0,
        distanceBetweenColumns = dist, soilBearingCapacity = 200.0, footingThickness = t, fcu = 25.0, fy = 400.0
    )

    @Test
    fun unequalLoadsGiveUniformPressureBecauseFootingIsCentredOnResultant() {
        val r = run(500.0, 900.0)
        assertEquals(r.qMax, r.qMin, 1e-6)
        // resultant 4.0 * 900/1400 m from column 1
        assertEquals(4.0 * 900.0 / 1400.0, r.centroidFromCol1, 1e-9)
    }

    @Test
    fun hoggingMomentBetweenColumnsGivesTopSteel() {
        val r = run(800.0, 800.0)
        assertTrue("top steel required between columns", r.longTopAs > 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun footingWeightAboveAllowableBearingIsRejected() {
        engine.design(
            p1 = 500.0, p2 = 600.0, col1Width = 300.0, col1Depth = 300.0, col2Width = 300.0, col2Depth = 300.0,
            distanceBetweenColumns = 4000.0, soilBearingCapacity = 10.0, footingThickness = 800.0, fcu = 25.0, fy = 400.0
        )
    }
}
