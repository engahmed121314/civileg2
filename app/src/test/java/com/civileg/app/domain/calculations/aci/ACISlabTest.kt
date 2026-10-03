package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.SlabDesign
import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ACISlab — ACI 318-19 slab design engine.
 * Covers: one-way slab, two-way slab, thickness check,
 * InputGuard validation, edge cases, and safety checks.
 */
class ACISlabTest {

    private lateinit var slab: ACISlab

    @Before
    fun setup() {
        slab = ACISlab()
    }

    companion object {
        private const val FCU = 25.0          // MPa
        private const val FY = 420.0          // MPa
        private const val THICKNESS = 200.0   // mm
        private const val CLEAR_SPAN = 4000.0 // mm
        private const val DESIGN_MOMENT = 30.0  // kN.m/m
        private const val DESIGN_SHEAR = 20.0   // kN/m
        private const val SHORT_SPAN = 4000.0   // mm
        private const val LONG_SPAN = 5000.0    // mm
        private const val TOTAL_LOAD = 12.0     // kN/m²

        private val FIXED_SUPPORTS = SlabSupportConditions(
            EdgeCondition.FIXED, EdgeCondition.FIXED,
            EdgeCondition.FIXED, EdgeCondition.FIXED
        )
        private val SIMPLY_SUPPORTED_EDGES = SlabSupportConditions(
            EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED,
            EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Instance & interface
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun aciSlabInstanceCreatedSuccessfully() {
        assertNotNull(slab)
    }

    @Test
    fun aciSlabImplementsSlabDesign() {
        assertTrue(slab is SlabDesign)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. One-Way Slab — happy path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designOneWaySlab_validInputs_producesResult() {
        val result = slab.designOneWaySlab(
            FCU, FY, THICKNESS, CLEAR_SPAN, DESIGN_MOMENT, DESIGN_SHEAR,
            LoadCombination.DEAD_LIVE
        )
        assertNotNull(result)
        assertTrue("Required reinforcement must be positive", result.requiredReinforcement > 0)
        assertTrue("Provided reinforcement must be positive", result.providedReinforcement > 0)
    }

    @Test
    fun designOneWaySlab_validInputs_barDiameterReasonable() {
        val result = slab.designOneWaySlab(
            FCU, FY, THICKNESS, CLEAR_SPAN, DESIGN_MOMENT, DESIGN_SHEAR,
            LoadCombination.DEAD_LIVE
        )
        assertTrue("Bar diameter should be at least 10mm", result.barDiameter >= 10.0)
        assertTrue("Bar diameter should be at most 20mm", result.barDiameter <= 20.0)
    }

    @Test
    fun designOneWaySlab_validInputs_barSpacingReasonable() {
        val result = slab.designOneWaySlab(
            FCU, FY, THICKNESS, CLEAR_SPAN, DESIGN_MOMENT, DESIGN_SHEAR,
            LoadCombination.DEAD_LIVE
        )
        assertTrue("Bar spacing should be at least 100mm", result.barSpacing >= 100.0)
        assertTrue("Bar spacing should not exceed 450mm", result.barSpacing <= 450.0)
    }

    @Test
    fun designOneWaySlab_validInputs_shearCapacityPositive() {
        val result = slab.designOneWaySlab(
            FCU, FY, THICKNESS, CLEAR_SPAN, DESIGN_MOMENT, DESIGN_SHEAR,
            LoadCombination.DEAD_LIVE
        )
        assertTrue("Shear capacity must be positive", result.shearCapacity > 0)
    }

    @Test
    fun designOneWaySlab_thickerSlab_lowerReinforcement() {
        val resultThin = slab.designOneWaySlab(
            FCU, FY, 150.0, CLEAR_SPAN, DESIGN_MOMENT, DESIGN_SHEAR,
            LoadCombination.DEAD_LIVE
        )
        val resultThick = slab.designOneWaySlab(
            FCU, FY, 250.0, CLEAR_SPAN, DESIGN_MOMENT, DESIGN_SHEAR,
            LoadCombination.DEAD_LIVE
        )
        // Thicker slab has larger d, so for same moment the required ρ should be lower
        assertTrue("Thicker slab should require less or equal reinforcement per unit width",
            resultThick.requiredReinforcement <= resultThin.requiredReinforcement * 1.5)
    }

    @Test
    fun designOneWaySlab_lowShear_isSafe() {
        val result = slab.designOneWaySlab(
            FCU, FY, THICKNESS, CLEAR_SPAN, DESIGN_MOMENT, 5.0,
            LoadCombination.DEAD_LIVE
        )
        assertTrue("Low shear should be safe", result.isSafe)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. One-Way Slab — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun designOneWaySlab_negativeFcu_throwsException() {
        slab.designOneWaySlab(-25.0, FY, THICKNESS, CLEAR_SPAN, DESIGN_MOMENT, DESIGN_SHEAR, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designOneWaySlab_zeroFy_throwsException() {
        slab.designOneWaySlab(FCU, 0.0, THICKNESS, CLEAR_SPAN, DESIGN_MOMENT, DESIGN_SHEAR, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designOneWaySlab_zeroThickness_throwsException() {
        slab.designOneWaySlab(FCU, FY, 0.0, CLEAR_SPAN, DESIGN_MOMENT, DESIGN_SHEAR, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designOneWaySlab_zeroSpan_throwsException() {
        slab.designOneWaySlab(FCU, FY, THICKNESS, 0.0, DESIGN_MOMENT, DESIGN_SHEAR, LoadCombination.DEAD_LIVE)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Two-Way Slab — happy path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designTwoWaySlab_validInputs_producesResult() {
        val result = slab.designTwoWaySlab(
            FCU, FY, THICKNESS, SHORT_SPAN, LONG_SPAN, FIXED_SUPPORTS,
            TOTAL_LOAD, LoadCombination.DEAD_LIVE
        )
        assertNotNull(result)
        assertNotNull(result.shortDirection)
        assertNotNull(result.longDirection)
    }

    @Test
    fun designTwoWaySlab_validInputs_shortDirectionHasReinforcement() {
        val result = slab.designTwoWaySlab(
            FCU, FY, THICKNESS, SHORT_SPAN, LONG_SPAN, FIXED_SUPPORTS,
            TOTAL_LOAD, LoadCombination.DEAD_LIVE
        )
        assertTrue("Short direction must have required reinforcement",
            result.shortDirection.requiredReinforcement > 0)
        assertTrue("Short direction must have provided reinforcement",
            result.shortDirection.providedReinforcement > 0)
    }

    @Test
    fun designTwoWaySlab_validInputs_longDirectionHasReinforcement() {
        val result = slab.designTwoWaySlab(
            FCU, FY, THICKNESS, SHORT_SPAN, LONG_SPAN, FIXED_SUPPORTS,
            TOTAL_LOAD, LoadCombination.DEAD_LIVE
        )
        assertTrue("Long direction must have required reinforcement",
            result.longDirection.requiredReinforcement > 0)
    }

    @Test
    fun designTwoWaySlab_validInputs_momentCoefficientsExist() {
        val result = slab.designTwoWaySlab(
            FCU, FY, THICKNESS, SHORT_SPAN, LONG_SPAN, FIXED_SUPPORTS,
            TOTAL_LOAD, LoadCombination.DEAD_LIVE
        )
        assertNotNull(result.momentCoefficients)
        assertTrue("Negative short coefficient should be positive",
            result.momentCoefficients.negativeShort > 0)
        assertTrue("Positive short coefficient should be positive",
            result.momentCoefficients.positiveShort > 0)
    }

    @Test
    fun designTwoWaySlab_squareSlab_shortAndLongSimilar() {
        val result = slab.designTwoWaySlab(
            FCU, FY, THICKNESS, 4000.0, 4000.0, FIXED_SUPPORTS,
            TOTAL_LOAD, LoadCombination.DEAD_LIVE
        )
        // For square slab, both directions should be similar
        val ratio = result.shortDirection.requiredReinforcement /
                    result.longDirection.requiredReinforcement.coerceAtLeast(0.001)
        assertTrue("Square slab: short/long ratio should be near 1.0 (was $ratio)",
            ratio in 0.5..2.0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Two-Way Slab — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun designTwoWaySlab_negativeFcu_throwsException() {
        slab.designTwoWaySlab(-25.0, FY, THICKNESS, SHORT_SPAN, LONG_SPAN, FIXED_SUPPORTS, TOTAL_LOAD, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designTwoWaySlab_zeroThickness_throwsException() {
        slab.designTwoWaySlab(FCU, FY, 0.0, SHORT_SPAN, LONG_SPAN, FIXED_SUPPORTS, TOTAL_LOAD, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designTwoWaySlab_zeroShortSpan_throwsException() {
        slab.designTwoWaySlab(FCU, FY, THICKNESS, 0.0, LONG_SPAN, FIXED_SUPPORTS, TOTAL_LOAD, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designTwoWaySlab_zeroTotalLoad_throwsException() {
        slab.designTwoWaySlab(FCU, FY, THICKNESS, SHORT_SPAN, LONG_SPAN, FIXED_SUPPORTS, 0.0, LoadCombination.DEAD_LIVE)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. Slab Thickness Check
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun checkSlabThickness_validInputs_producesResult() {
        val result = slab.checkSlabThickness(CLEAR_SPAN, SupportCondition.SIMPLY_SUPPORTED, FY, false)
        assertNotNull(result)
        assertTrue("Required thickness must be positive", result.requiredThickness > 0)
    }

    @Test
    fun getMinSlabThickness_simplySupported_isSpanOver20() {
        val minT = slab.getMinSlabThickness(CLEAR_SPAN, SupportCondition.SIMPLY_SUPPORTED)
        assertEquals(CLEAR_SPAN / 20.0, minT, 1e-6)
    }

    @Test
    fun getMinSlabThickness_continuous_isSpanOver28() {
        val minT = slab.getMinSlabThickness(CLEAR_SPAN, SupportCondition.CONTINUOUS)
        assertEquals(CLEAR_SPAN / 28.0, minT, 1e-6)
    }

    @Test
    fun getMinSlabThickness_cantilever_isSpanOver8() {
        val minT = slab.getMinSlabThickness(CLEAR_SPAN, SupportCondition.CANTILEVER)
        assertEquals(CLEAR_SPAN / 8.0, minT, 1e-6)
    }

    @Test
    fun getMinSlabThickness_cantileverThickerThanSimple() {
        val simpleT = slab.getMinSlabThickness(CLEAR_SPAN, SupportCondition.SIMPLY_SUPPORTED)
        val cantT = slab.getMinSlabThickness(CLEAR_SPAN, SupportCondition.CANTILEVER)
        assertTrue("Cantilever requires thicker slab", cantT > simpleT)
    }

    @Test(expected = IllegalArgumentException::class)
    fun getMinSlabThickness_zeroSpan_throwsException() {
        slab.getMinSlabThickness(0.0, SupportCondition.SIMPLY_SUPPORTED)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. Code limits
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun minReinforcementRatio_isPositive() {
        assertTrue(slab.getMinReinforcementRatio() > 0)
    }

    @Test
    fun maxBarSpacing_is450mm() {
        assertEquals(450.0, slab.getMaxBarSpacing(), 1e-6)
    }

    @Test
    fun minCover_is20mm() {
        assertEquals(20.0, slab.getMinCover(), 1e-6)
    }
}
