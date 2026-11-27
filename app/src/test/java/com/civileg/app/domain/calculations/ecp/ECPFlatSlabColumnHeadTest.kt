package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.FlatSlabInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ECPFlatSlabColumnHeadTest {
    @Test
    fun `no column head given is stated as not assessed`() {
        val r = ECPFlatSlab().design(FlatSlabInput())
        assertFalse(r.safetyChecks.any { it.name.startsWith("Column head geometry") })
        assertTrue(r.codeNotes.any { it.contains("no column head given") && it.contains("NOT ASSESSED") })
    }

    @Test
    fun `head that is too wide or too steep fails the slab`() {
        val r = ECPFlatSlab().design(FlatSlabInput(headEffectiveDiameter = 2000.0, headSlopeFromVerticalDeg = 50.0))
        val c = r.safetyChecks.first { it.name.startsWith("Column head geometry") }
        assertFalse(c.passed)
        assertFalse(r.isSafe)
    }

    @Test
    fun `head within the limits adds a passing check`() {
        val r = ECPFlatSlab().design(FlatSlabInput(headEffectiveDiameter = 1000.0, headSlopeFromVerticalDeg = 40.0))
        assertTrue(r.safetyChecks.first { it.name.startsWith("Column head geometry") }.passed)
    }
}
