package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.base.FlatSlabDesign
import com.civileg.app.domain.calculations.base.FlatSlabDesign.MomentCoefficients
import com.civileg.app.domain.calculations.base.FlatSlabDesign.PunchingShearResult
import com.civileg.app.domain.calculations.base.FlatSlabDesign.ReinforcementDesign
import com.civileg.app.domain.calculations.base.FlatSlabDesign.DeflectionResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for SBCFlatSlab — SBC 304-2018 flat slab design engine.
 *
 * Covers:
 *  - design: full flat slab design happy path, validation
 *  - calculateStaticMoment: DDM Mo calculation
 *  - getMomentCoefficients: column/middle strip distribution
 *  - getColumnStripWidth: strip width calculation
 *  - checkPunchingShear: punching shear check
 *  - designReinforcement: strip reinforcement design
 *  - checkDeflection: deflection check
 *  - getMinimumThickness: minimum thickness per SBC
 *  - Code limits: cover, spacing, reinforcement ratio
 *  - InputGuard validation
 */
class SBCFlatSlabTest {

    private lateinit var engine: SBCFlatSlab

    @Before
    fun setup() {
        engine = SBCFlatSlab()
    }

    companion object {
        private fun typicalInput() = FlatSlabInput(
            panelType = PanelType.INTERIOR,
            designMethod = DesignMethod.DDM,
            lx = 6000.0, ly = 7500.0,
            slabThickness = 250.0, dropThickness = 0.0,
            columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 400.0,
            liveLoad = 3.0, floorFinish = 2.0,
            numberOfFloors = 10, clearCover = 25.0, storyHeight = 3.0
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Full Design — Happy Path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `design - typical inputs produce valid result`() {
        val result = engine.design(typicalInput())
        assertNotNull(result)
        assertTrue("Total factored load should be positive", result.totalFactoredLoad > 0)
        assertTrue("Panel moment X should be positive", result.panelMomentX > 0)
        assertTrue("Panel moment Y should be positive", result.panelMomentY > 0)
    }

    @Test
    fun `design - typical result has strip moments`() {
        val result = engine.design(typicalInput())
        assertTrue("Column strip positive moment should be positive",
            result.columnStripMomentPos > 0)
        assertTrue("Column strip negative moment should be positive",
            result.columnStripMomentNeg > 0)
    }

    @Test
    fun `design - typical result has code notes`() {
        val result = engine.design(typicalInput())
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    @Test
    fun `design - interior panel produces valid widths`() {
        val result = engine.design(typicalInput())
        assertTrue("Column strip width X should be positive", result.columnStripWidthX > 0)
        assertTrue("Column strip width Y should be positive", result.columnStripWidthY > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Edge/Corner Panels
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `design - edge panel produces valid result`() {
        val input = FlatSlabInput(
            panelType = PanelType.EDGE,
            lx = 6000.0, ly = 7500.0, slabThickness = 250.0,
            fcu = 30.0, fy = 400.0, liveLoad = 3.0
        )
        val result = engine.design(input)
        assertTrue("Should have positive factored load", result.totalFactoredLoad > 0)
    }

    @Test
    fun `design - corner panel produces valid result`() {
        val input = FlatSlabInput(
            panelType = PanelType.CORNER,
            lx = 6000.0, ly = 7500.0, slabThickness = 250.0,
            fcu = 30.0, fy = 400.0, liveLoad = 3.0
        )
        val result = engine.design(input)
        assertTrue("Should have positive factored load", result.totalFactoredLoad > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Static Moment Calculation
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `static moment - typical values produce positive result`() {
        val mo = engine.calculateStaticMoment(wu = 15.0, ln = 5.6, l2 = 7.5)
        assertTrue("Mo should be positive", mo > 0)
    }

    @Test
    fun `static moment - increases with load`() {
        val mo1 = engine.calculateStaticMoment(wu = 10.0, ln = 5.6, l2 = 7.5)
        val mo2 = engine.calculateStaticMoment(wu = 20.0, ln = 5.6, l2 = 7.5)
        assertTrue("Higher load should give higher Mo", mo2 > mo1)
    }

    @Test
    fun `static moment - increases with span squared`() {
        val mo1 = engine.calculateStaticMoment(wu = 15.0, ln = 4.0, l2 = 7.5)
        val mo2 = engine.calculateStaticMoment(wu = 15.0, ln = 6.0, l2 = 7.5)
        assertTrue("Longer span should give higher Mo", mo2 > mo1)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Moment Coefficients
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `moment coefficients - interior panel has valid fractions`() {
        val coeffs = engine.getMomentCoefficients(PanelType.INTERIOR)
        assertTrue("Column neg interior should be between 0 and 1",
            coeffs.colNegInterior in 0.0..1.0)
        assertTrue("Column positive should be between 0 and 1",
            coeffs.colPositive in 0.0..1.0)
    }

    @Test
    fun `moment coefficients - edge panel has valid fractions`() {
        val coeffs = engine.getMomentCoefficients(PanelType.EDGE)
        assertTrue("Column neg exterior should be between 0 and 1",
            coeffs.colNegExterior in 0.0..1.0)
        assertTrue("Column positive should be between 0 and 1",
            coeffs.colPositive in 0.0..1.0)
    }

    @Test
    fun `moment coefficients - corner panel has valid fractions`() {
        val coeffs = engine.getMomentCoefficients(PanelType.CORNER)
        assertNotNull(coeffs)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Punching Shear
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `punching shear - typical inputs produce valid result`() {
        val result = engine.checkPunchingShear(
            vu = 300.0, fcu = 30.0, fy = 400.0,
            slabThickness = 250.0, dropThickness = 0.0,
            columnWidth = 400.0, columnDepth = 400.0
        )
        assertTrue("Vc should be positive", result.vc > 0)
        assertTrue("bo should be positive", result.bo > 0)
        assertTrue("d should be positive", result.d > 0)
    }

    @Test
    fun `punching shear - low Vu is safe`() {
        val result = engine.checkPunchingShear(
            vu = 100.0, fcu = 30.0, fy = 400.0,
            slabThickness = 250.0, dropThickness = 0.0,
            columnWidth = 400.0, columnDepth = 400.0
        )
        assertTrue("Should be safe for low Vu", result.isSafe)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. Reinforcement Design
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `reinforcement design - typical inputs produce valid result`() {
        val result = engine.designReinforcement(
            moment = 100.0, fcu = 30.0, fy = 400.0,
            effectiveDepth = 210.0, stripWidth = 3000.0
        )
        assertTrue("As required should be positive", result.asRequired > 0)
        assertTrue("As provided should be positive", result.asProvided > 0)
        assertTrue("Bar diameter should be positive", result.barDia > 0)
        assertTrue("Bar spacing should be positive", result.barSpacing > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. Column Strip Width
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `column strip width - typical inputs produce positive result`() {
        val width = engine.getColumnStripWidth(
            l2 = 7500.0, columnSize = 400.0, panelType = PanelType.INTERIOR
        )
        assertTrue("Column strip width should be positive", width > 0)
        assertTrue("Column strip width should be less than l2", width < 7500.0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 8. Minimum Thickness
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `minimum thickness - typical inputs produce positive result`() {
        val minT = engine.getMinimumThickness(
            clearSpan = 6000.0, transverseSpan = 7500.0,
            hasDropPanel = false, fcu = 30.0, fy = 400.0
        )
        assertTrue("Min thickness should be positive", minT > 0)
    }

    @Test
    fun `minimum thickness - with drop panel allows thinner slab`() {
        val minTNoDrop = engine.getMinimumThickness(
            clearSpan = 6000.0, transverseSpan = 7500.0,
            hasDropPanel = false, fcu = 30.0, fy = 400.0
        )
        val minTWithDrop = engine.getMinimumThickness(
            clearSpan = 6000.0, transverseSpan = 7500.0,
            hasDropPanel = true, fcu = 30.0, fy = 400.0
        )
        assertTrue("Drop panel should allow thinner slab",
            minTWithDrop <= minTNoDrop)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 9. Code Limits
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `code name is SBC`() {
        assertEquals("Code name should be SBC", "SBC", engine.getCodeName())
    }

    @Test
    fun `min cover is positive`() {
        assertTrue("Min cover should be positive", engine.getMinCover() > 0)
    }

    @Test
    fun `max bar spacing is positive`() {
        assertTrue("Max bar spacing should be positive", engine.getMaxBarSpacing() > 0)
    }

    @Test
    fun `min reinforcement ratio is positive`() {
        assertTrue("Min reinforcement ratio should be positive",
            engine.getMinReinforcementRatio(420.0) > 0)
    }

    @Test
    fun `factored load follows SBC 304`() {
        val wu = engine.getFactoredLoad(deadLoad = 8.0, liveLoad = 3.0)
        // SBC 304: Wu = 1.4×DL + 1.6×LL
        val expected = 1.4 * 8.0 + 1.6 * 3.0
        assertEquals("Factored load should follow SBC 304", expected, wu, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 10. Deflection Check
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `deflection check - typical inputs produce valid result`() {
        val result = engine.checkDeflection(
            span = 6000.0, slabThickness = 250.0,
            fcu = 30.0, fy = 400.0,
            serviceMoment = 80.0, effectiveDepth = 210.0,
            providedAs = 1200.0
        )
        assertTrue("Allowable deflection should be positive", result.allowable > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 11. InputGuard Validation
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `design - zero fcu throws`() {
        try {
            val input = FlatSlabInput(
                lx = 6000.0, ly = 7500.0, slabThickness = 250.0,
                fcu = 0.0, fy = 400.0
            )
            engine.design(input)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `design - zero slabThickness throws`() {
        try {
            val input = FlatSlabInput(
                lx = 6000.0, ly = 7500.0, slabThickness = 0.0,
                fcu = 30.0, fy = 400.0
            )
            engine.design(input)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `design - invalid span returns unsafe result`() {
        val input = FlatSlabInput(
            lx = 300.0, ly = 7500.0, slabThickness = 250.0,
            columnWidth = 400.0, fcu = 30.0, fy = 400.0
        )
        val result = engine.design(input)
        assertFalse("Should be unsafe when column wider than span", result.isSafe)
    }
}
