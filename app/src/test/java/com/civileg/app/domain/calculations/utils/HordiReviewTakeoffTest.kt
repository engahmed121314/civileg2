package com.civileg.app.domain.calculations.utils

import com.civileg.app.domain.entities.SlabDesignResult
import org.junit.Assert.*
import org.junit.Test

class HordiReviewTakeoffTest {
    private fun result(area: Double = 4 * Math.PI * 12 * 12 / 4) = SlabDesignResult(
        requiredReinforcement = 400.0, providedReinforcement = area,
        barDiameter = 12.0, barSpacing = 600.0, minThickness = 300.0,
        shearCapacity = 20.0, isSafe = true, utilizationRatio = 0.5
    )
    @Test fun `physical main bar count is conserved per one actual rib`() {
        val s = HordiReviewTakeoff.schedule(result(), "ECP", 6000.0, 600.0, 25.0)!!
        assertEquals(4, s.groups.single().totalCount)
        assertEquals(6000.0, s.groups.single().cutLengthMm, 0.0)
        assertEquals(1, s.groups.size)
        assertFalse(s.executionReady)
    }
    @Test fun `area demand is not rounded into adopted bars`() {
        assertNull(HordiReviewTakeoff.schedule(result(475.6), "ACI", 6000.0, 600.0, 25.0))
    }
    @Test fun `net concrete excludes hollow blocks`() {
        assertEquals(0.675, HordiReviewTakeoff.concreteM3(6000.0, 300.0, 50.0, 150.0, 600.0), 1e-10)
    }
    @Test fun `edits affect a new takeoff without mutating old schedule`() {
        val a = HordiReviewTakeoff.schedule(result(), "ECP", 6000.0, 600.0, 25.0)!!
        val b = HordiReviewTakeoff.schedule(result(), "SBC", 9000.0, 600.0, 25.0)!!
        assertEquals("ECP", a.code)
        assertEquals("SBC", b.code)
        assertEquals(6000.0, a.groups.single().bodyLengthMm, 0.0)
        assertEquals(9000.0, b.groups.single().bodyLengthMm, 0.0)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `impossible topping rejected instead of clipped quantity`() {
        HordiReviewTakeoff.concreteM3(6000.0, 300.0, 350.0, 150.0, 600.0)
    }
    @Test fun `legacy unsafe flag cannot produce an adopted body schedule`() {
        assertNull(HordiReviewTakeoff.schedule(result().copy(isSafe = false), "ACI", 6000.0, 600.0, 25.0))
    }
    @Test fun `legacy safe flag does not override insufficient adopted steel`() {
        assertNull(HordiReviewTakeoff.schedule(result().copy(requiredReinforcement = 1721.25), "SBC", 6000.0, 600.0, 25.0))
    }
    @Test fun `noncatalog diameter returns withheld instead of schedule constructor error`() {
        val r = result(4 * Math.PI * 13 * 13 / 4).copy(barDiameter = 13.0)
        assertNull(HordiReviewTakeoff.schedule(r, "ACI", 6000.0, 600.0, 25.0))
    }
    @Test fun `unknown required steel cannot produce body quantities`() {
        assertNull(HordiReviewTakeoff.schedule(result().copy(requiredReinforcement = Double.NaN), "SBC", 6000.0, 600.0, 25.0))
    }
}
