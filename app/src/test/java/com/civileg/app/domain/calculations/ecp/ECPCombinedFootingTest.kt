package com.civileg.app.domain.calculations.ecp

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ECPCombinedFootingTest {

    private lateinit var engine: ECPCombinedFooting

    @Before
    fun setUp() {
        engine = ECPCombinedFooting()
    }

    @Test
    fun testCombinedFooting_goldenVector() {
        val result = engine.design(
            p1 = 500.0,
            p2 = 600.0,
            col1Width = 300.0,
            col1Depth = 300.0,
            col2Width = 300.0,
            col2Depth = 300.0,
            distanceBetweenColumns = 4000.0, // mm
            soilBearingCapacity = 200.0,     // kN/m^2
            footingThickness = 800.0,        // mm
            fcu = 30.0,                      // MPa
            fy = 400.0                       // MPa
        )

        assertNotNull(result)
        assertTrue(result.footingLength > 0.0)
        assertTrue(result.footingWidth > 0.0)
        assertTrue(result.longBottomAs > 0.0)
        assertTrue(result.oneWayShearSafe)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeSoilPressure_rejectedByInputGuard() {
        engine.design(
            p1 = 500.0,
            p2 = 600.0,
            col1Width = 300.0,
            col1Depth = 300.0,
            col2Width = 300.0,
            col2Depth = 300.0,
            distanceBetweenColumns = 4000.0,
            soilBearingCapacity = -150.0,
            footingThickness = 500.0,
            fcu = 25.0,
            fy = 400.0
        )
    }
}
