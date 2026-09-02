package com.civileg.app.utils

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 (P044) — Stirrup / transverse-steel suite through the live god-engine.
 *
 * Pins the new engine behavior end-to-end:
 *  1) leg count follows the beam width (2 / 4 / 6 below 400 / 600 / beyond),
 *  2) Ø8 upgrades to Ø10 when the required support spacing falls below 100 mm,
 *  3) confinement zones are denser at the supports and relaxed at mid-span,
 *  4) steel weight stays positive with the multi-ring perimeter model.
 */
class CalculatorEngineStirrupTest {

    private fun engine() = CalculatorEngine(mockk(relaxed = true))

    private fun design(
        width: Double,
        customShear: Double? = null,
        code: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN
    ) = engine().designBeam(
        width = width, height = 500.0, span = 5.0,
        fcu = 25.0, fy = 400.0, deadLoad = 10.0, liveLoad = 5.0,
        preferredDiameter = 16, code = code,
        customShear = customShear
    )

    @Test
    fun `leg count follows beam width`() {
        val legsByWidth = mapOf(300.0 to 2, 500.0 to 4, 700.0 to 6)
        val shapeLabel = mapOf(2 to "2-Leg", 4 to "4-Leg", 6 to "6-Leg")
        for ((width, legs) in legsByWidth) {
            val r = design(width)
            assertEquals("width $width", legs, r.stirrups.numLegs)
            val firstZoneDesc = r.stirrups.zones.first().description
            assertTrue("width $width -> $firstZoneDesc", firstZoneDesc.contains(shapeLabel.getValue(legs)))
        }
    }

    @Test
    fun `zones dense at support relaxed at midspan`() {
        val r = design(300.0)
        val zones = r.stirrups.zones

        assertEquals(3, zones.size)
        assertEquals("Support Zone (Left)", zones[0].name)
        assertEquals("Mid-Span Zone", zones[1].name)
        assertEquals("Support Zone (Right)", zones[2].name)
        assertEquals(0.0, zones[0].startLocation, 1e-9)
        assertEquals(5000.0, zones[2].endLocation, 1e-9)
        // min(2*h = 1000, span/4 = 1250).
        assertEquals(1000.0, r.stirrups.condensationZoneLength, 1e-9)
        assertEquals(zones[0].spacing, zones[2].spacing, 1e-9)
        assertTrue(zones[1].spacing >= zones[0].spacing)

        // Moderate ECP load keeps shear below vc -> code max everywhere.
        assertEquals(200.0, zones[0].spacing, 1e-9)
        assertEquals(200.0, zones[1].spacing, 1e-9)
        assertEquals(200.0, r.stirrups.spacingAtSupport, 1e-9)
        assertEquals(200.0, r.stirrups.spacingAtMidspan, 1e-9)
        assertEquals(200.0, r.stirrups.spacing, 1e-9)
        assertTrue(zones[0].description.contains("Ø8 @ 200 mm c/c · 2-Leg"))
    }

    @Test
    fun `high shear upgrades to 10mm at support`() {
        // vu = 420 kN -> v_stress = 3.11 MPa > vc and the Ø8 support spacing
        // drops below the 100 mm practical minimum -> engine upgrades to Ø10.
        val r = design(300.0, customShear = 420.0)

        assertEquals(10, r.stirrups.diameter)
        assertEquals(90.0, r.stirrups.spacingAtSupport, 1e-9)
        assertEquals(200.0, r.stirrups.spacingAtMidspan, 1e-9)
        assertEquals(10, r.stirrups.zones[0].diameter)
        assertEquals(90.0, r.stirrups.zones[0].spacing, 1e-9)
        assertEquals(200.0, r.stirrups.zones[1].spacing, 1e-9)
        assertTrue(r.warnings.any { it.contains("High shear") })
        assertTrue(r.isSafe)

        // vs > 1.5*vc lands in the critical band -> sMax halved to d/4 / 100 mm.
        val spacingCheck = r.safetyChecks.first { it.name == "Max Stirrup Spacing" }
        assertEquals(100.0, spacingCheck.limit, 1e-9)
        assertTrue(spacingCheck.isSafe)
    }

    @Test
    fun `shear above code limit fails`() {
        // vu = 500 kN -> v_stress = 3.70 MPa > qmax = 0.7*sqrt(25) = 3.5 MPa.
        val r = design(300.0, customShear = 500.0)

        val shearCheck = r.safetyChecks.first { it.name == "Shear Stress" }
        assertFalse(shearCheck.isSafe)
        assertFalse(r.isSafe)
    }

    @Test
    fun `steel weight stays positive with multi-ring perimeter`() {
        val r = design(500.0)
        assertTrue(r.steelWeight > 0.0)

        // 4-leg at width 500 -> outer ring + (4-2)/2 = 1 inner ring.
        val innerRings = (4 - 2) / 2
        val perimeter = (2 * (500.0 + 500.0) - 8 * 25.0) * (1 + innerRings) / 1000.0
        val ringWeight = perimeter * (8.0 * 8.0 / 162.0)
        val nSupportZone = (1000.0 / 200.0).toInt() + 1
        val nMidZone = (3000.0 / 200.0).toInt() + 1
        val expectedStirrupKg = (2 * nSupportZone + nMidZone) * ringWeight

        // Whole-beam steel weight (includes flexure bars + 10% laps) must at
        // least cover the stirrup portion.
        assertTrue(
            "steelWeight ${r.steelWeight} must cover stirrups $expectedStirrupKg",
            r.steelWeight >= expectedStirrupKg
        )
    }
}