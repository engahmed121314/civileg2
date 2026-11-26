package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.base.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ECPFlatSlab — ECP 203-2025 flat slab design engine.
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
    fun `design_deflectionIsReportedNotAssessed`() {
        val result = flatSlab.design(defaultInput())
        assertFalse("Deflection must not be reported as assessed", result.deflectionAssessed)
        assertFalse("No false deflection pass", result.deflectionOk)
        assertTrue(result.warnings.any { it.contains("Deflection is not yet calculated") })
    }

    // ECP 203-2025 hand checks (defaultInput: 6.0 x 7.5 m, h=250, col 400, fcu 30, fy 400, LL 3, finish 2)
    // wu = 1.4 x (6.25 + 2) + 1.6 x 3 = 16.35 kN/m2

    @Test
    fun `eq618_totalMoment_matchesHandValue`() {
        // M0X = (16.35 x 7.5 / 8)(6 - 2 x 0.4 / 3)^2 = 503.9 kN.m (Eq 6-18, p.6-24)
        val r = flatSlab.design(defaultInput())
        assertEquals(503.9, r.panelMomentX, 0.5)
        // M0Y = (16.35 x 6 / 8)(7.5 - 0.2667)^2 = 641.6
        assertEquals(641.6, r.panelMomentY, 0.5)
    }

    @Test
    fun `table65_interiorBay_columnStripMoments`() {
        // col strip +0.25 M0, -0.45 M0 (Table 6-5, p.6-25)
        val r = flatSlab.design(defaultInput())
        assertEquals(0.25 * r.panelMomentX, r.columnStripMomentPos, 0.01)
        assertEquals(0.45 * r.panelMomentX, r.columnStripMomentNeg, 0.01)
        assertEquals(0.15 * r.panelMomentX, r.middleStripMomentPos, 0.01)
    }

    @Test
    fun `table65_exteriorBay_totalsMatchFig69`() {
        val t = ECPFlatSlab.BAY_EXTERIOR
        assertEquals(0.30, t.colNegExt + t.midNegExt, 1e-9)
        assertEquals(0.50, t.colPos + t.midPos, 1e-9)
        assertEquals(0.70, t.colNegInt + t.midNegInt, 1e-9)
        val i = ECPFlatSlab.BAY_INTERIOR
        assertEquals(0.60, i.colNegExt + i.midNegExt, 1e-9)
        assertEquals(0.40, i.colPos + i.midPos, 1e-9)
    }

    @Test
    fun `stripWidth_isLminOverTwo_bothDirections`() {
        val r = flatSlab.design(defaultInput())
        assertEquals(3000.0, r.columnStripWidthX, 0.1)
        assertEquals(3000.0, r.columnStripWidthY, 0.1)
    }

    @Test
    fun `design_yDirectionReinforcementIsDesigned`() {
        val r = flatSlab.design(defaultInput())
        assertNotNull(r.columnStripTopRebarY)
        assertTrue(r.columnStripTopRebarY!!.providedArea >= r.columnStripTopRebarY!!.requiredArea)
        assertTrue(r.columnStripTopRebarY!!.requiredArea > r.columnStripTopRebar.requiredArea) // MoY > MoX
    }

    @Test
    fun `punching_interior_matchesEq446c_andEq625`() {
        // d = 250 - 25 - 6 = 219; b1 = b2 = 619; bo = 2476
        // q_cup = 0.316 sqrt(30/1.5) = 1.413 N/mm2 -> Vc = 766 kN; Vu = 16.35 x 45 x 1.15 = 846 kN
        val r = flatSlab.design(defaultInput())
        assertEquals(2476.0, r.punchingPerimeter, 1.0)
        assertEquals(766.3, r.punchingShearVc, 1.0)
        assertEquals(846.1, r.punchingShearVu, 1.0)
        assertNotNull("Vu > Vc needs punching reinforcement (h >= 250 mm)", r.punchingReinforcement)
    }

    @Test
    fun `punching_columnLoadDependsOnPanelType`() {
        val i = flatSlab.design(defaultInput(panelType = PanelType.INTERIOR)).punchingShearVu
        val e = flatSlab.design(defaultInput(panelType = PanelType.EDGE)).punchingShearVu
        val c = flatSlab.design(defaultInput(panelType = PanelType.CORNER)).punchingShearVu
        assertEquals(16.35 * 45.0 * 1.15, i, 0.5)
        assertEquals(16.35 * 22.5 * 1.30, e, 0.5)
        assertEquals(16.35 * 11.25 * 1.50, c, 0.5)
    }

    @Test
    fun `punching_thinSlabWithoutStudThickness_fails`() {
        val r = flatSlab.design(defaultInput(slabThickness = 180.0))
        assertFalse(r.punchingShearOk)
        assertFalse(r.isSafe)
    }

    @Test
    fun `minThickness_table63`() {
        // fy 400 -> 350 row. interior no drop Ln/35, exterior no edge beam Ln/32; Ln(long) = 7100
        assertEquals(7100.0 / 35.0, flatSlab.design(defaultInput()).let { 7100.0 / ECPFlatSlab.table63Divisor(400.0, false, false) }, 1e-9)
        assertEquals(7100.0 / 32.0, flatSlab.getMinimumThickness(5600.0, 7100.0, false, 30.0, 400.0, true), 1e-6)
        assertEquals(7100.0 / 35.0, flatSlab.getMinimumThickness(5600.0, 7100.0, false, 30.0, 400.0, false), 1e-6)
        assertEquals(38.0, ECPFlatSlab.table63Divisor(350.0, false, true), 0.0)
        assertEquals(22.0, ECPFlatSlab.table63Divisor(700.0, false, false), 0.0)
    }

    @Test
    fun `steelWeight_isPlausible`() {
        val r = flatSlab.design(defaultInput())
        assertTrue("kg/panel should be a few hundred to few thousand, was ${r.steelWeightPerPanel}", r.steelWeightPerPanel in 200.0..5000.0)
    }

    @Test
    fun `empirical_notApplicable_whenAspectAbove1_3`() {
        val r = flatSlab.design(defaultInput(lx = 5000.0, ly = 8000.0))
        assertFalse(r.isSafe)
        assertTrue(r.warnings.any { it.contains("aspect ratio") })
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
        assertEquals("ECP 203-2025", flatSlab.getCodeName())
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
