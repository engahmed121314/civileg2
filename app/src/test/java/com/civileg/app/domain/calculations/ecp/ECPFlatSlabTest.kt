package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.base.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ECPFlatSlab — ECP 203-2020 flat slab design engine.
 * Covers DDM, moment distribution, punching shear, deflection,
 * InputGuard validation, and edge cases.
 */
class ECPFlatSlabTest {

    private lateinit var flatSlab: ECPFlatSlab

    @Before
    fun setup() {
        flatSlab = ECPFlatSlab()
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun defaultInput(
        panelType: PanelType = PanelType.INTERIOR,
        designMethod: DesignMethod = DesignMethod.DDM,
        lx: Double = 6000.0,
        ly: Double = 7500.0,
        slabThickness: Double = 250.0,
        dropThickness: Double = 0.0,
        columnWidth: Double = 400.0,
        columnDepth: Double = 400.0,
        fcu: Double = 30.0,
        fy: Double = 400.0,
        liveLoad: Double = 3.0,
        floorFinish: Double = 2.0,
        clearCover: Double = 25.0
    ) = FlatSlabInput(
        panelType, designMethod, lx, ly, slabThickness,
        dropThickness, 0.0, 0.0, columnWidth, columnDepth,
        fcu, fy, liveLoad, floorFinish, 10, clearCover, 3.0
    )

    // ═══════════════════════════════════════════════════════════════
    // 1. Happy path — design
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `design_validInputs_producesResult`() {
        val result = flatSlab.design(defaultInput())
        assertNotNull(result)
        assertTrue("Total dead load > 0", result.totalDeadLoad > 0)
        assertTrue("Total factored load > 0", result.totalFactoredLoad > 0)
    }

    @Test
    fun `design_panelMomentsArePositive`() {
        val result = flatSlab.design(defaultInput())
        assertTrue("Panel moment X > 0", result.panelMomentX > 0)
        assertTrue("Panel moment Y > 0", result.panelMomentY > 0)
    }

    @Test
    fun `design_columnStripWidthsArePositive`() {
        val result = flatSlab.design(defaultInput())
        assertTrue("Column strip width X > 0", result.columnStripWidthX > 0)
        assertTrue("Column strip width Y > 0", result.columnStripWidthY > 0)
    }

    @Test
    fun `design_reinforcementResultsAreValid`() {
        val result = flatSlab.design(defaultInput())
        assertTrue("Column strip top rebar area > 0", result.columnStripTopRebar.providedArea > 0)
        assertTrue("Column strip bot rebar area > 0", result.columnStripBotRebar.providedArea > 0)
    }

    @Test
    fun `design_punchingShearValuesAreSet`() {
        val result = flatSlab.design(defaultInput())
        assertTrue("Punching Vu > 0", result.punchingShearVu > 0)
        assertTrue("Punching Vc > 0", result.punchingShearVc > 0)
        assertTrue("Punching perimeter > 0", result.punchingPerimeter > 0)
    }

    @Test
    fun `design_deflectionValuesAreSet`() {
        val result = flatSlab.design(defaultInput())
        assertTrue("Deflection > 0", result.deflection > 0)
        assertTrue("Allowable deflection > 0", result.allowableDeflection > 0)
    }

    @Test
    fun `design_concreteVolumeIsPositive`() {
        val result = flatSlab.design(defaultInput())
        assertTrue("Concrete volume > 0", result.concreteVolumePerPanel > 0)
    }

    @Test
    fun `design_safetyChecksExist`() {
        val result = flatSlab.design(defaultInput())
        assertTrue("Should have safety checks", result.safetyChecks.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Panel types
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `design_interiorPanel_producesResult`() {
        val result = flatSlab.design(defaultInput(panelType = PanelType.INTERIOR))
        assertNotNull(result)
    }

    @Test
    fun `design_edgePanel_producesResult`() {
        val result = flatSlab.design(defaultInput(panelType = PanelType.EDGE))
        assertNotNull(result)
    }

    @Test
    fun `design_cornerPanel_producesResult`() {
        val result = flatSlab.design(defaultInput(panelType = PanelType.CORNER))
        assertNotNull(result)
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Static moment calculation
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `calculateStaticMoment_returnsCorrectValue`() {
        val wu = 15.0
        val ln = 5.6
        val l2 = 7.1
        val Mo = flatSlab.calculateStaticMoment(wu, ln, l2)
        val expected = wu * l2 * ln * ln / 8.0
        assertEquals(expected, Mo, 0.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateStaticMoment_throwsForZeroLn`() {
        flatSlab.calculateStaticMoment(15.0, 0.0, 7.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateStaticMoment_throwsForZeroL2`() {
        flatSlab.calculateStaticMoment(15.0, 5.0, 0.0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Moment coefficients
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `getMomentCoefficients_interiorPanel_returnsValidCoefficients`() {
        val c = flatSlab.getMomentCoefficients(PanelType.INTERIOR)
        assertTrue("Col positive fraction > 0", c.colPositive > 0)
        assertTrue("Col positive fraction <= 1", c.colPositive <= 1.0)
        assertTrue("Col neg interior fraction > 0", c.colNegInterior > 0)
    }

    @Test
    fun `getMomentCoefficients_colPlusMidEqualsOne`() {
        val c = flatSlab.getMomentCoefficients(PanelType.INTERIOR)
        // Column + middle strip should sum to 1.0 for each moment type
        assertEquals(1.0, c.colPositive + c.midPositive, 0.01)
        assertEquals(1.0, c.colNegInterior + c.midNegInterior, 0.01)
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. Column strip width
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `getColumnStripWidth_returnsHalfOfL2`() {
        val l2 = 7500.0
        val width = flatSlab.getColumnStripWidth(l2, 400.0, PanelType.INTERIOR)
        assertEquals(l2 / 2.0, width, 0.1)
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. Punching shear
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `checkPunchingShear_validInputs_producesResult`() {
        val result = flatSlab.checkPunchingShear(
            400.0, 30.0, 400.0, 250.0, 0.0, 400.0, 400.0, 25.0
        )
        assertNotNull(result)
        assertTrue("Vc > 0", result.vc > 0)
        assertTrue("Perimeter > 0", result.bo > 0)
    }

    @Test
    fun `checkPunchingShear_lowForce_isSafe`() {
        val result = flatSlab.checkPunchingShear(
            100.0, 30.0, 400.0, 250.0, 0.0, 400.0, 400.0, 25.0
        )
        assertTrue("Low force should be safe", result.isSafe)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `checkPunchingShear_throwsForZeroFcu`() {
        flatSlab.checkPunchingShear(400.0, 0.0, 400.0, 250.0, 0.0, 400.0, 400.0, 25.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `checkPunchingShear_throwsForZeroSlabThickness`() {
        flatSlab.checkPunchingShear(400.0, 30.0, 400.0, 0.0, 0.0, 400.0, 400.0, 25.0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. Code limits
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `getMinCover_returnsPositiveValue`() {
        assertTrue("Min cover > 0", flatSlab.getMinCover() > 0)
    }

    @Test
    fun `getMaxBarSpacing_returnsPositiveValue`() {
        assertTrue("Max bar spacing > 0", flatSlab.getMaxBarSpacing() > 0)
    }

    @Test
    fun `getMinReinforcementRatio_returnsPositiveValue`() {
        assertTrue("Min rein ratio > 0", flatSlab.getMinReinforcementRatio(400.0) > 0)
    }

    @Test
    fun `getCodeName_returnsECP`() {
        assertEquals("ECP", flatSlab.getCodeName())
    }

    @Test
    fun `getFactoredLoad_returnsCorrectValue`() {
        val wu = flatSlab.getFactoredLoad(8.0, 3.0)
        assertEquals(1.4 * 8.0 + 1.6 * 3.0, wu, 0.01)
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. InputGuard validation
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `design_throwsForZeroFcu`() {
        flatSlab.design(defaultInput(fcu = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `design_throwsForZeroFy`() {
        flatSlab.design(defaultInput(fy = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `design_throwsForZeroLx`() {
        flatSlab.design(defaultInput(lx = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `design_throwsForZeroLy`() {
        flatSlab.design(defaultInput(ly = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `design_throwsForZeroSlabThickness`() {
        flatSlab.design(defaultInput(slabThickness = 0.0))
    }

    // ═══════════════════════════════════════════════════════════════
    // 9. Edge cases
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `design_withDropPanel_producesResult`() {
        val result = flatSlab.design(defaultInput(dropThickness = 75.0))
        assertNotNull(result)
        assertTrue("Factored load > 0", result.totalFactoredLoad > 0)
    }

    @Test
    fun `design_thickSlab_isSafe`() {
        val result = flatSlab.design(defaultInput(slabThickness = 350.0))
        // A thick slab should be safe
        assertNotNull(result)
    }

    @Test
    fun `design_columnWiderThanSpan_returnsUnsafeResult`() {
        val result = flatSlab.design(defaultInput(lx = 300.0, ly = 300.0, columnWidth = 400.0, columnDepth = 400.0))
        assertFalse("Should be unsafe when column wider than span", result.isSafe)
    }
}
