package com.civileg.app.domain.entities

import com.civileg.app.domain.calculations.ecp.ECPHordiSlabWrapper
import com.civileg.core.engine.rc.ecp.*
import org.junit.Assert.*
import org.junit.Test

class HordiCalculatedSectionSnapshotTest {
    private fun inputs(topTension: Boolean = false) = HordiSlabInputs(30.0, 400.0,
        200.0, 600.0, 350.0, 60.0, 5000.0, if (topTension) 20.0 else 62.0, 10.0,
        LoadCombination.DEAD_LIVE, "ECP", Ecp203RibbedSection.Context(25.0, 20.0,
            listOf(Ecp203BarLayout.Layer(2, 20.0)), Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED,
            if (topTension) Ecp203RibbedSection.Region.FLANGE_IN_TENSION else Ecp203RibbedSection.Region.FLANGE_IN_COMPRESSION,
            if (topTension) null else 5000.0, 8.0,
            Ecp203WebShear.VerticalLinks(240.0, Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD, 8.0, 2, 200.0)))

    private fun calculate(i: HordiSlabInputs) = with(i) {
        ECPHordiSlabWrapper().designHordiSlab(fcu, fy, ribWidth, ribSpacing, totalThickness,
            toppingThickness, span, designMoment, designShear, loadCombination, sectionContext)
    }

    @Test fun `actual adopted bars and numbers share the completed record identity`() {
        val input = inputs()
        val result = calculate(input)
        val frozen = HordiSlabDesignSnapshot.capture(input, result)
        val output = requireNotNull(HordiCalculatedSectionSnapshot.from(frozen))
        assertEquals(frozen.revisionSha256, output.revisionId)
        assertEquals(611.1777815875193, output.requiredSteelMm2, 1e-8)
        assertEquals(result.providedReinforcement, output.providedSteelMm2, 0.0)
        assertEquals(listOf(43.0, 157.0), output.section.bars!!.map { it.xFromWebLeftMm })
        assertEquals(listOf(43.0, 43.0), output.section.bars!!.map { it.yFromBottomMm })
        assertEquals(600.0, output.section.enteredRibPitchMm, 0.0)
        assertEquals("BOTTOM", output.section.tensionFace)
        assertTrue(output.section.placementBasis.startsWith("ADOPTED_"))
        assertTrue(output.incompleteChecks.any { it.contains("anchorage") })
        assertTrue(result.safetyAssessmentBlocked)
        assertFalse(result.isSafe)
    }

    @Test fun `negative region maps the same measured layers from the top tension face`() {
        val input = inputs(true)
        val output = requireNotNull(HordiCalculatedSectionSnapshot.from(
            HordiSlabDesignSnapshot.capture(input, calculate(input))))
        assertEquals("TOP", output.section.tensionFace)
        assertEquals(listOf(307.0, 307.0), output.section.bars!!.map { it.yFromBottomMm })
        assertEquals(listOf(43.0, 157.0), output.section.bars!!.map { it.xFromWebLeftMm })
    }

    @Test fun `older result with no section geometry is not rebuilt from its frozen input`() {
        val input = inputs()
        val result = calculate(input).copy(calculatedSection = null)
        val frozen = HordiSlabDesignSnapshot.capture(input, result)
        assertTrue(frozen.hasValidFingerprint())
        assertNull(HordiCalculatedSectionSnapshot.from(frozen))
        assertEquals(result.requiredReinforcement, frozen.result().requiredReinforcement, 0.0)
        assertNull(frozen.result().calculatedSection)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched frozen geometry is rejected despite a valid pair hash`() {
        val input = inputs()
        val oldResult = calculate(input)
        HordiCalculatedSectionSnapshot.from(HordiSlabDesignSnapshot.capture(input.copy(ribWidth = 250.0), oldResult))
    }

    @Test fun `output consumers cannot mutate frozen bars and a changed coordinate changes identity`() {
        val input = inputs()
        val result = calculate(input)
        val frozen = HordiSlabDesignSnapshot.capture(input, result)
        val output = requireNotNull(HordiCalculatedSectionSnapshot.from(frozen))
        try { (output.section.bars as MutableList).clear(); fail("Output bars must be immutable") }
        catch (_: UnsupportedOperationException) { }
        val section = requireNotNull(result.calculatedSection)
        val changed = result.copy(calculatedSection = section.copy(bars = section.bars!!.mapIndexed { index, bar ->
            if (index == 0) bar.copy(xFromWebLeftMm = bar.xFromWebLeftMm + 1.0) else bar
        }))
        assertNotEquals(frozen.revisionSha256, HordiSlabDesignSnapshot.capture(input, changed).revisionSha256)
        assertEquals(listOf(43.0, 157.0), frozen.result().calculatedSection!!.bars!!.map { it.xFromWebLeftMm })
    }

    @Test fun `unconverted legacy caller has no adopted geometry output`() {
        val input = inputs().copy(sectionContext = null)
        val result = calculate(input)
        assertNull(result.calculatedSection)
        assertNull(HordiCalculatedSectionSnapshot.from(HordiSlabDesignSnapshot.capture(input, result)))
    }
}
