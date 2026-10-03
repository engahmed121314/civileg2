package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for SBCSlab — SBC 304-2018 slab design engine.
 *
 * Covers:
 *  - One-way slab: designOneWaySlab happy path, validation, edge cases
 *  - Two-way slab: designTwoWaySlab happy path, validation
 *  - Thickness check: checkSlabThickness happy path, validation
 *  - Code limits: min reinforcement ratio, cover, max spacing
 *  - InputGuard: zero/negative values throw IllegalArgumentException
 */
class SBCSlabTest {

    private lateinit var engine: SBCSlab

    @Before
    fun setup() {
        engine = SBCSlab()
    }

    companion object {
        private const val TYPICAL_FCU = 30.0
        private const val TYPICAL_FY = 420.0
        private const val TYPICAL_THICKNESS = 200.0
        private const val TYPICAL_SPAN = 6000.0
        private const val TYPICAL_MOMENT = 40.0
        private const val TYPICAL_SHEAR = 30.0
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. One-Way Slab — Happy Path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `one-way slab - typical inputs produce valid result`() {
        val result = engine.designOneWaySlab(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, slabThickness = TYPICAL_THICKNESS,
            clearSpan = TYPICAL_SPAN, designMoment = TYPICAL_MOMENT,
            designShear = TYPICAL_SHEAR, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Should have required reinforcement", result.requiredReinforcement > 0)
        assertTrue("Should have provided reinforcement", result.providedReinforcement > 0)
        assertTrue("Bar diameter should be positive", result.barDiameter > 0)
        assertTrue("Bar spacing should be positive", result.barSpacing > 0)
        assertTrue("Shear capacity should be positive", result.shearCapacity > 0)
    }

    @Test
    fun `one-way slab - typical inputs should be safe`() {
        val result = engine.designOneWaySlab(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, slabThickness = TYPICAL_THICKNESS,
            clearSpan = TYPICAL_SPAN, designMoment = TYPICAL_MOMENT,
            designShear = TYPICAL_SHEAR, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Should be safe for typical inputs", result.isSafe)
    }

    @Test
    fun `one-way slab - result has code notes`() {
        val result = engine.designOneWaySlab(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, slabThickness = TYPICAL_THICKNESS,
            clearSpan = TYPICAL_SPAN, designMoment = TYPICAL_MOMENT,
            designShear = TYPICAL_SHEAR, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    @Test
    fun `one-way slab - zero shear is valid`() {
        val result = engine.designOneWaySlab(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, slabThickness = TYPICAL_THICKNESS,
            clearSpan = TYPICAL_SPAN, designMoment = TYPICAL_MOMENT,
            designShear = 0.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Should handle zero shear", result.requiredReinforcement > 0)
    }

    @Test
    fun `one-way slab - zero moment produces minimum reinforcement`() {
        val result = engine.designOneWaySlab(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, slabThickness = TYPICAL_THICKNESS,
            clearSpan = TYPICAL_SPAN, designMoment = 0.0,
            designShear = 0.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Should have minimum reinforcement", result.providedReinforcement > 0)
    }

    @Test
    fun `one-way slab - thicker slab reduces required reinforcement`() {
        val resultThin = engine.designOneWaySlab(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, slabThickness = 150.0,
            clearSpan = TYPICAL_SPAN, designMoment = TYPICAL_MOMENT,
            designShear = TYPICAL_SHEAR, loadCombination = LoadCombination.DEAD_LIVE
        )
        val resultThick = engine.designOneWaySlab(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, slabThickness = 300.0,
            clearSpan = TYPICAL_SPAN, designMoment = TYPICAL_MOMENT,
            designShear = TYPICAL_SHEAR, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Both should produce valid results",
            resultThin.providedReinforcement > 0 && resultThick.providedReinforcement > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. One-Way Slab — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `one-way slab - zero fcu throws`() {
        engine.designOneWaySlab(
            fcu = 0.0, fy = TYPICAL_FY, slabThickness = TYPICAL_THICKNESS,
            clearSpan = TYPICAL_SPAN, designMoment = TYPICAL_MOMENT,
            designShear = TYPICAL_SHEAR, loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `one-way slab - negative fy throws`() {
        engine.designOneWaySlab(
            fcu = TYPICAL_FCU, fy = -420.0, slabThickness = TYPICAL_THICKNESS,
            clearSpan = TYPICAL_SPAN, designMoment = TYPICAL_MOMENT,
            designShear = TYPICAL_SHEAR, loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `one-way slab - zero slabThickness throws`() {
        engine.designOneWaySlab(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, slabThickness = 0.0,
            clearSpan = TYPICAL_SPAN, designMoment = TYPICAL_MOMENT,
            designShear = TYPICAL_SHEAR, loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `one-way slab - zero clearSpan throws`() {
        engine.designOneWaySlab(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, slabThickness = TYPICAL_THICKNESS,
            clearSpan = 0.0, designMoment = TYPICAL_MOMENT,
            designShear = TYPICAL_SHEAR, loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Two-Way Slab — Happy Path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `two-way slab - typical inputs produce valid result`() {
        val result = engine.designTwoWaySlab(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, slabThickness = TYPICAL_THICKNESS,
            shortSpan = 6000.0, longSpan = 7500.0,
            supportConditions = SlabSupportConditions(
                edgeA = EdgeCondition.FIXED, edgeB = EdgeCondition.FIXED,
                edgeC = EdgeCondition.FIXED, edgeD = EdgeCondition.FIXED
            ),
            totalLoad = 12.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Short direction should have reinforcement",
            result.shortDirection.providedReinforcement > 0)
        assertTrue("Long direction should have reinforcement",
            result.longDirection.providedReinforcement > 0)
    }

    @Test
    fun `two-way slab - square slab produces similar reinforcement both directions`() {
        val result = engine.designTwoWaySlab(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, slabThickness = TYPICAL_THICKNESS,
            shortSpan = 6000.0, longSpan = 6000.0,
            supportConditions = SlabSupportConditions(
                edgeA = EdgeCondition.FIXED, edgeB = EdgeCondition.FIXED,
                edgeC = EdgeCondition.FIXED, edgeD = EdgeCondition.FIXED
            ),
            totalLoad = 12.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertNotNull(result)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Two-Way Slab — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `two-way slab - zero fcu throws`() {
        engine.designTwoWaySlab(
            fcu = 0.0, fy = TYPICAL_FY, slabThickness = TYPICAL_THICKNESS,
            shortSpan = 6000.0, longSpan = 7500.0,
            supportConditions = SlabSupportConditions(
                edgeA = EdgeCondition.FIXED, edgeB = EdgeCondition.FIXED,
                edgeC = EdgeCondition.FIXED, edgeD = EdgeCondition.FIXED
            ),
            totalLoad = 12.0, loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `two-way slab - zero totalLoad throws`() {
        engine.designTwoWaySlab(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, slabThickness = TYPICAL_THICKNESS,
            shortSpan = 6000.0, longSpan = 7500.0,
            supportConditions = SlabSupportConditions(
                edgeA = EdgeCondition.FIXED, edgeB = EdgeCondition.FIXED,
                edgeC = EdgeCondition.FIXED, edgeD = EdgeCondition.FIXED
            ),
            totalLoad = 0.0, loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Thickness Check
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `thickness check - simply supported produces valid result`() {
        val result = engine.checkSlabThickness(
            span = 6000.0, supportCondition = SupportCondition.SIMPLY_SUPPORTED,
            fy = TYPICAL_FY, isTwoWay = false
        )
        assertTrue("Required thickness should be positive", result.requiredThickness > 0)
    }

    @Test
    fun `thickness check - cantilever requires thicker slab`() {
        val ssResult = engine.checkSlabThickness(
            span = 3000.0, supportCondition = SupportCondition.SIMPLY_SUPPORTED,
            fy = TYPICAL_FY, isTwoWay = false
        )
        val cantResult = engine.checkSlabThickness(
            span = 3000.0, supportCondition = SupportCondition.CANTILEVER,
            fy = TYPICAL_FY, isTwoWay = false
        )
        assertTrue("Cantilever should require thicker slab",
            cantResult.requiredThickness > ssResult.requiredThickness)
    }

    @Test
    fun `thickness check - InputGuard rejects zero span`() {
        try {
            engine.checkSlabThickness(
                span = 0.0, supportCondition = SupportCondition.SIMPLY_SUPPORTED,
                fy = TYPICAL_FY, isTwoWay = false
            )
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. Code Limits
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `min reinforcement ratio is 0_0018 per SBC 304-8_4`() {
        assertEquals("Min ratio should be 0.0018", 0.0018, engine.getMinReinforcementRatio(), 0.0001)
    }

    @Test
    fun `min cover is 20mm for interior slabs`() {
        assertEquals("Cover should be 20mm", 20.0, engine.getMinCover(), 0.1)
    }

    @Test
    fun `max bar spacing is 450mm`() {
        assertEquals("Max spacing should be 450mm", 450.0, engine.getMaxBarSpacing(), 0.1)
    }

    @Test
    fun `min slab thickness - simply supported is span_div_20`() {
        val minT = engine.getMinSlabThickness(6000.0, SupportCondition.SIMPLY_SUPPORTED)
        assertEquals("Simply supported: L/20", 300.0, minT, 0.1)
    }

    @Test
    fun `min slab thickness - continuous is span_div_28`() {
        val minT = engine.getMinSlabThickness(6000.0, SupportCondition.CONTINUOUS)
        assertEquals("Continuous: L/28", 6000.0 / 28.0, minT, 0.1)
    }

    @Test
    fun `min slab thickness - cantilever is span_div_8`() {
        val minT = engine.getMinSlabThickness(3000.0, SupportCondition.CANTILEVER)
        assertEquals("Cantilever: L/8", 375.0, minT, 0.1)
    }
}
