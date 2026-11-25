package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.StaircaseInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ECP 203-2020 Table 4-10 (p. 4-64): solid slab, simply supported, Ln/t <= 25 x (0.40 + fy/700). */
class ECPStairTable410Test {

    private fun run(span: Double, t: Double, fy: Double = 420.0) =
        ECPStaircase().designStaircase(
            StaircaseInput(span = span, totalRise = 1.8, stairWidth = 1.2, waistThickness = t, fcu = 25.0, fy = fy)
        ).safetyChecks.first { it.name.startsWith("Deflection") }

    @Test
    fun `limit is 25 for fy 420 and scaled by Eq 4-81 otherwise`() {
        assertEquals(25.0, run(3.0, 200.0, 420.0).limit, 1e-9)
        assertEquals(25.0 * (0.40 + 360.0 / 700.0), run(3.0, 200.0, 360.0).limit, 1e-9)
    }

    @Test
    fun `thin waist fails and is not passed by an ungrounded modification factor`() {
        assertFalse(run(3.0, 100.0).isSafe)
        assertTrue(run(3.0, 200.0).isSafe)
    }

    @Test
    fun `span above 6 m is outside the table`() {
        assertFalse(run(6.5, 400.0).isSafe)
    }

    @Test
    fun `not computed deflection is flagged and labelled not assessed`() {
        val r = ECPStaircase().designStaircase(
            StaircaseInput(span = 6.5, totalRise = 1.8, stairWidth = 1.2, waistThickness = 400.0, fcu = 25.0, fy = 420.0)
        )
        assertFalse(r.deflectionComputed)
        assertTrue(r.safetyChecks.first { it.name.startsWith("Deflection") }.name.contains("NOT ASSESSED"))
    }
}
