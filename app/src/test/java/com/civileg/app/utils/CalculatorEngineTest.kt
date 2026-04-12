package com.civileg.app.utils

import org.junit.Assert.*
import org.junit.Test

class CalculatorEngineTest {

    @Test
    fun testBeamDesignSafety() {
        // Test a typical beam case
        val mu = 150.0 // kNm
        val result = CalculatorEngine.calculateBeam(
            CalculatorEngine.BeamInput(
                b = 250.0, h = 600.0, L = 5.0,
                fc = 25.0, fy = 360.0, Mu = mu, Vu = 0.0,
                code = CalculatorEngine.DesignCode.EGYPTIAN
            )
        )
        
        assertTrue("Beam should be safe for given moment", result.isSafe)
        assertTrue("Concrete volume should be positive", result.concreteVol > 0)
        assertTrue("Steel area should be calculated", result.requiredSteelArea > 0)
    }

    @Test
    fun testColumnAxialCapacity() {
        val pu = 2000.0 // kN
        val result = CalculatorEngine.calculateColumn(
            CalculatorEngine.ColumnInput(
                b = 300.0, h = 600.0, L = 3.0,
                fc = 30.0, fy = 360.0, Pu = pu,
                code = CalculatorEngine.DesignCode.EGYPTIAN,
                isCircular = false
            )
        )
        
        // Pn = 0.35 * fcu * (Ag - As) + 0.67 * fy * As
        // For 300x600, Ag = 180,000 mm2.
        // Approx Pn = 0.35 * 30 * 180000 / 1000 = 1890 kN (approx)
        // With min steel, it should be around 2000+ kN
        assertTrue("Column capacity should be near or above applied load", result.capacity >= 1800.0)
    }

    @Test
    fun testFootingPunchingShear() {
        val fc = 25.0
        val d = 530.0 // 600 - 70 cover
        val colB = 300.0
        val colH = 600.0
        val p = 1500.0 // kN
        
        val isSafe = CalculatorEngine.checkPunchingShear(
            p, colB, colH, d, fc, CalculatorEngine.DesignCode.EGYPTIAN
        )
        
        // Perimeter bo = 2 * (300+530 + 600+530) = 2 * (830 + 1130) = 3920 mm
        // vu = 1500 * 1000 / (3920 * 530) = 0.72 MPa
        // vc = 0.316 * sqrt(25) = 1.58 MPa
        // Should be safe
        assertTrue("Punching shear should be safe for these dimensions", isSafe)
    }

    @Test
    fun testTwoWaySlabMoments() {
        val lx = 4.0
        val ly = 5.0
        val load = 10.0 // kN/m2
        val result = CalculatorEngine.calculateTwoWaySlab(lx, ly, load, 150.0, 25.0, 360.0)
        
        // Ratio r = 5/4 = 1.25
        // Alpha = 1.25^4 / (1 + 1.25^4) = 2.44 / 3.44 = 0.71
        // Mx = 0.71 * 10 * 4^2 / 8 = 14.2 kNm
        assertEquals(14.2, result.momentX, 1.0)
        assertTrue("Slab should be safe for thickness", result.isSafe)
    }

    @Test
    fun testEconomicReinforcementSelection() {
        val reqArea = 1200.0 // mm2
        val b = 250.0
        val result = CalculatorEngine.getEconomicReinforcement(reqArea, b, 25.0, 1.0)
        
        // For 1200 mm2:
        // 4T20 = 1256 mm2
        // 6T16 = 1206 mm2
        // 6T16 should be selected if spacing allows
        assertTrue("Should select at least the required area", result.count * 3.14 * result.diameter * result.diameter / 4 >= reqArea)
        assertEquals(16, result.diameter)
    }
}
