package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.FlatSlabInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ECPFlatSlabDropEdgeTest {
    @Test
    fun `drop panel adds a punching check at the drop edge and no longer says not assessed`() {
        val r = ECPFlatSlab().design(FlatSlabInput(dropThickness = 100.0, dropSizeX = 2000.0, dropSizeY = 2500.0))
        assertTrue(r.safetyChecks.any { it.name == "Punching at drop edge" })
        assertFalse(r.warnings.any { it.contains("Second punching section") && it.contains("NOT ASSESSED") })
    }

    @Test
    fun `failing drop edge fails the slab`() {
        val r = ECPFlatSlab().design(
            FlatSlabInput(slabThickness = 200.0, dropThickness = 100.0, dropSizeX = 2000.0, dropSizeY = 2500.0, numberOfFloors = 40, liveLoad = 5.0)
        )
        val edge = r.safetyChecks.first { it.name == "Punching at drop edge" }
        if (!edge.passed) {
            assertFalse(r.isSafe)
            assertTrue(r.warnings.any { it.contains("drop panel edge") })
        }
    }

    @Test
    fun `failed punching without a drop panel gets a suggestion or a plain no-solution note`() {
        val r = ECPFlatSlab().design(FlatSlabInput(slabThickness = 200.0, numberOfFloors = 40, liveLoad = 5.0))
        if (!r.punchingShearOk) {
            assertTrue(r.warnings.any { it.startsWith("Suggested drop panel") || it.startsWith("No drop panel") })
            assertFalse(r.isSafe)
        }
    }
}
