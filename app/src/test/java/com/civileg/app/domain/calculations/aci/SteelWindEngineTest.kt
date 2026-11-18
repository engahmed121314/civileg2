package com.civileg.app.domain.calculations.aci

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SteelWindEngine (ASCE 7-16 wind load pressure calculation).
 * Tests wind pressure calculations across exposure categories, heights, windward vs leeward.
 */
class SteelWindEngineTest {

    @Test
    fun calculateWindPressure_windward_returnsPositivePressure() {
        val p = SteelWindEngine.calculateWindPressure(
            velocityKmh = 140.0,
            heightM = 10.0,
            isWindward = true,
            exposureCategory = "C"
        )
        assertTrue("Windward wind pressure > 0", p > 0)
    }

    @Test
    fun calculateWindPressure_leeward_returnsSuction() {
        val p = SteelWindEngine.calculateWindPressure(
            velocityKmh = 140.0,
            heightM = 10.0,
            isWindward = false,
            exposureCategory = "C"
        )
        assertTrue("Leeward wind pressure is suction (< 0)", p < 0)
    }

    @Test
    fun calculateWindPressure_higherWindSpeed_increasesPressure() {
        val p100 = SteelWindEngine.calculateWindPressure(100.0, 10.0, true, "C")
        val p160 = SteelWindEngine.calculateWindPressure(160.0, 10.0, true, "C")
        assertTrue("Higher wind velocity should increase pressure", p160 > p100)
    }

    @Test
    fun calculateWindPressure_greaterHeight_increasesPressure() {
        val pLow = SteelWindEngine.calculateWindPressure(140.0, 5.0, true, "C")
        val pHigh = SteelWindEngine.calculateWindPressure(140.0, 30.0, true, "C")
        assertTrue("Greater height should increase velocity pressure qz", pHigh > pLow)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateWindPressure_throwsForZeroVelocity() {
        SteelWindEngine.calculateWindPressure(0.0, 10.0, true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateWindPressure_throwsForZeroHeight() {
        SteelWindEngine.calculateWindPressure(140.0, 0.0, true)
    }
}
