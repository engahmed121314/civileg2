package com.civileg.app.domain.entities

import com.civileg.app.domain.calculations.ecp.ECPHordiSlabWrapper
import com.civileg.app.domain.calculations.ecp.HordiFourBarCalculation
import com.civileg.core.engine.rc.ecp.*
import org.junit.Assert.*
import org.junit.Test

class HordiFourBarCalculationTest {
    private fun input() = HordiSlabInputs(30.0, 400.0, 200.0, 600.0, 350.0, 60.0, 5000.0,
        62.0, 10.0, LoadCombination.DEAD_LIVE, "ECP", Ecp203RibbedSection.Context(25.0, 20.0,
            listOf(Ecp203BarLayout.Layer(3, 20.0), Ecp203BarLayout.Layer(1, 20.0)),
            Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED,
            Ecp203RibbedSection.Region.FLANGE_IN_COMPRESSION, 5000.0, 8.0,
            Ecp203WebShear.VerticalLinks(240.0, Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD, 8.0, 2, 200.0)))
    private fun calculate(i: HordiSlabInputs = input(), policy: Ecp203FourBarArrangement.Policy =
        Ecp203FourBarArrangement.Policy.STANDARD_PAIRED_UPPER) = HordiFourBarCalculation.calculate(i, ECPHordiSlabWrapper(), policy)

    @Test fun `actual input result coordinates area and identity all adopt five bars`() {
        val snapshot = calculate()
        assertTrue(snapshot.hasValidFingerprint())
        assertEquals(listOf(3, 2), snapshot.input().sectionContext!!.tensionLayers.map { it.count })
        assertEquals(Ecp203FourBarArrangement.Policy.STANDARD_PAIRED_UPPER, snapshot.input().fourBarArrangementPolicy)
        val out = requireNotNull(HordiCalculatedSectionSnapshot.from(snapshot))
        assertEquals(snapshot.revisionSha256, out.revisionId)
        assertEquals(1570.7963267948967, out.providedSteelMm2, 1e-8)
        assertEquals(0.4501453297620758, snapshot.result().utilizationRatio, 1e-10)
        assertEquals(60.910910994282666, snapshot.result().shearCapacity, 1e-9)
        assertEquals(listOf(43.0, 100.0, 157.0, 43.0, 157.0), out.section.bars!!.map { it.xFromWebLeftMm })
        assertEquals(listOf(43.0, 43.0, 43.0, 93.0, 93.0), out.section.bars!!.map { it.yFromBottomMm })
        assertTrue(out.section.placementBasis.contains("STANDARD_PAIRED_UPPER"))
        assertTrue(snapshot.result().boundedSectionChecksPass == true)
        assertTrue(snapshot.result().safetyAssessmentBlocked)
        assertFalse(snapshot.result().isSafe)
        assertTrue(snapshot.result().codeNotes.any { it.contains("centroid effective depth=287.0 mm") })
    }
    @Test fun `economy only explicit and creates distinct four-bar frozen identity`() {
        val normal = calculate()
        val economic = calculate(policy = Ecp203FourBarArrangement.Policy.EXPLICIT_ECONOMY_FOUR_BARS)
        assertEquals(listOf(2, 2), economic.input().sectionContext!!.tensionLayers.map { it.count })
        assertEquals(1256.6370614359173, economic.result().providedReinforcement, 1e-8)
        assertEquals(0.556660055374669, economic.result().utilizationRatio, 1e-10)
        assertEquals(59.84974529751817, economic.result().shearCapacity, 1e-9)
        assertNotEquals(normal.revisionSha256, economic.revisionSha256)
        assertTrue(economic.result().codeNotes.any { it.contains("centroid effective depth=282.0 mm") })
        assertTrue(economic.result().safetyAssessmentBlocked)
    }
    @Test fun `single four-bar row from the actual screen follows the same adopted producer`() {
        val entered = input()
        val single = entered.copy(sectionContext = entered.sectionContext!!.copy(
            tensionLayers = listOf(Ecp203BarLayout.Layer(4, 20.0))))
        assertEquals(calculate().revisionSha256, calculate(single).revisionSha256)
    }
    @Test fun `recalculation preserves a valid already recorded explicit economic choice`() {
        val choice = Ecp203FourBarArrangement.Policy.EXPLICIT_ECONOMY_FOUR_BARS
        val first = calculate(policy = choice)
        assertEquals(first.revisionSha256, calculate(first.input(), choice).revisionSha256)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `a recorded standard policy cannot relabel unchanged rejected three plus one`() {
        calculate(input().copy(fourBarArrangementPolicy = Ecp203FourBarArrangement.Policy.STANDARD_PAIRED_UPPER))
    }
    @Test fun `bottom policy is not silently mirrored into top tension reinforcement`() {
        val entered = input()
        val top = entered.copy(designMoment = 20.0, sectionContext = entered.sectionContext!!.copy(
            region = Ecp203RibbedSection.Region.FLANGE_IN_TENSION, contraflexureDistanceMm = null))
        val frozen = calculate(top)
        assertNull(frozen.input().fourBarArrangementPolicy)
        assertEquals(listOf(3, 1), frozen.input().sectionContext!!.tensionLayers.map { it.count })
    }
    @Test(expected = IllegalArgumentException::class)
    fun `economic branch does not silently extend the selected bottom policy to top steel`() {
        val entered = input()
        calculate(entered.copy(designMoment = 20.0, sectionContext = entered.sectionContext!!.copy(
            region = Ecp203RibbedSection.Region.FLANGE_IN_TENSION, contraflexureDistanceMm = null)),
            Ecp203FourBarArrangement.Policy.EXPLICIT_ECONOMY_FOUR_BARS)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `extra steel cannot override maximum steel or failed bounded checks`() { calculate(input().copy(fcu = 20.0)) }
    @Test(expected = IllegalArgumentException::class)
    fun `economy cannot bypass insufficient moment resistance`() {
        calculate(input().copy(designMoment = 120.0), Ecp203FourBarArrangement.Policy.EXPLICIT_ECONOMY_FOUR_BARS)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `default cannot choose economy silently in a narrower rib`() { calculate(input().copy(ribWidth = 150.0)) }
}
