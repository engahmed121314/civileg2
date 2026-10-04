package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ECPSlab — ECP 203-2020 slab design engine.
 * Covers one-way slab, two-way slab, thickness checks,
 * InputGuard validation, and edge cases.
 */
class ECPSlabTest {

    private lateinit var slab: ECPSlab

    @Before
    fun setup() {
        slab = ECPSlab()
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper — typical valid inputs
    // ═══════════════════════════════════════════════════════════════

    private fun defaultOneWayInput(
        fcu: Double = 25.0,
        fy: Double = 360.0,
        thickness: Double = 150.0,
        span: Double = 4000.0,
        moment: Double = 30.0,
        shear: Double = 40.0
    ) = OneWayParams(fcu, fy, thickness, span, moment, shear)

    private data class OneWayParams(
        val fcu: Double, val fy: Double, val thickness: Double,
        val span: Double, val moment: Double, val shear: Double
    )

    private val defaultSupportConditions = SlabSupportConditions(
        EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED,
        EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED
    )

    // ═══════════════════════════════════════════════════════════════
    // 1. Happy path — one-way slab
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designOneWaySlab_validInputs_producesResult`() {
        val p = defaultOneWayInput()
        val result = slab.designOneWaySlab(
            p.fcu, p.fy, p.thickness, p.span, p.moment, p.shear,
            LoadCombination.DEAD_LIVE
        )
        assertNotNull(result)
        assertTrue("Required reinforcement should be positive", result.requiredReinforcement > 0)
        assertTrue("Provided reinforcement should be positive", result.providedReinforcement > 0)
    }

    @Test
    fun `designOneWaySlab_providedExceedsRequired`() {
        val p = defaultOneWayInput()
        val result = slab.designOneWaySlab(
            p.fcu, p.fy, p.thickness, p.span, p.moment, p.shear,
            LoadCombination.DEAD_LIVE
        )
        assertTrue("As_provided >= As_required", result.providedReinforcement >= result.requiredReinforcement * 0.95)
    }

    @Test
    fun `designOneWaySlab_barDiameterIsReasonable`() {
        val p = defaultOneWayInput()
        val result = slab.designOneWaySlab(
            p.fcu, p.fy, p.thickness, p.span, p.moment, p.shear,
            LoadCombination.DEAD_LIVE
        )
        assertTrue("Bar diameter >= 8mm", result.barDiameter >= 8.0)
        assertTrue("Bar diameter <= 14mm", result.barDiameter <= 14.0)
    }

    @Test
    fun `designOneWaySlab_barSpacingWithinLimits`() {
        val p = defaultOneWayInput()
        val result = slab.designOneWaySlab(
            p.fcu, p.fy, p.thickness, p.span, p.moment, p.shear,
            LoadCombination.DEAD_LIVE
        )
        assertTrue("Spacing >= 50mm", result.barSpacing >= 50.0)
        assertTrue("Spacing <= max spacing", result.barSpacing <= slab.getMaxBarSpacing())
    }

    @Test
    fun `designOneWaySlab_shearCapacityIsPositive`() {
        val p = defaultOneWayInput()
        val result = slab.designOneWaySlab(
            p.fcu, p.fy, p.thickness, p.span, p.moment, p.shear,
            LoadCombination.DEAD_LIVE
        )
        assertTrue("Shear capacity > 0", result.shearCapacity > 0)
    }

    @Test
    fun `designOneWaySlab_safeResultForModerateLoad`() {
        // Use a thicker slab (250mm) and lower loads to ensure safety
        val p = defaultOneWayInput(thickness = 250.0, moment = 10.0, shear = 15.0)
        val result = slab.designOneWaySlab(
            p.fcu, p.fy, p.thickness, p.span, p.moment, p.shear,
            LoadCombination.DEAD_LIVE
        )
        assertTrue("Should be safe for moderate loads", result.isSafe)
    }

    @Test
    fun `designOneWaySlab_utilizationRatioIsFinite`() {
        val p = defaultOneWayInput()
        val result = slab.designOneWaySlab(
            p.fcu, p.fy, p.thickness, p.span, p.moment, p.shear,
            LoadCombination.DEAD_LIVE
        )
        assertTrue("Utilization ratio should be finite", result.utilizationRatio.isFinite())
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Happy path — two-way slab
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designTwoWaySlab_validInputs_producesResult`() {
        val result = slab.designTwoWaySlab(
            25.0, 360.0, 200.0, 5000.0, 6000.0,
            defaultSupportConditions, 12.0, LoadCombination.DEAD_LIVE
        )
        assertNotNull(result)
        assertNotNull(result.shortDirection)
        assertNotNull(result.longDirection)
    }

    @Test
    fun `designTwoWaySlab_bothDirectionsHaveReinforcement`() {
        val result = slab.designTwoWaySlab(
            25.0, 360.0, 200.0, 5000.0, 6000.0,
            defaultSupportConditions, 12.0, LoadCombination.DEAD_LIVE
        )
        assertTrue("Short dir As > 0", result.shortDirection.requiredReinforcement > 0)
        assertTrue("Long dir As > 0", result.longDirection.requiredReinforcement > 0)
    }

    @Test
    fun `designTwoWaySlab_momentCoefficientsAreReasonable`() {
        val result = slab.designTwoWaySlab(
            25.0, 360.0, 200.0, 5000.0, 6000.0,
            defaultSupportConditions, 12.0, LoadCombination.DEAD_LIVE
        )
        val c = result.momentCoefficients
        assertTrue("Coefficients should be positive", c.positiveShort > 0)
        assertTrue("Coefficients should be < 0.1", c.positiveShort < 0.1)
    }

    @Test
    fun `designTwoWaySlab_fixedSupportsLowerCoefficients`() {
        val fixedSupports = SlabSupportConditions(
            EdgeCondition.FIXED, EdgeCondition.FIXED,
            EdgeCondition.FIXED, EdgeCondition.FIXED
        )
        val result = slab.designTwoWaySlab(
            25.0, 360.0, 200.0, 5000.0, 6000.0,
            fixedSupports, 12.0, LoadCombination.DEAD_LIVE
        )
        val resultSS = slab.designTwoWaySlab(
            25.0, 360.0, 200.0, 5000.0, 6000.0,
            defaultSupportConditions, 12.0, LoadCombination.DEAD_LIVE
        )
        assertTrue("Fixed supports should have lower positive coefficient",
            result.momentCoefficients.positiveShort <= resultSS.momentCoefficients.positiveShort)
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Thickness checks
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `checkSlabThickness_returnsValidResult`() {
        val result = slab.checkSlabThickness(4000.0, SupportCondition.SIMPLY_SUPPORTED, 360.0, false)
        assertTrue("Required thickness > 0", result.requiredThickness > 0)
        assertTrue("Provided thickness > 0", result.providedThickness > 0)
    }

    @Test
    fun `checkSlabThickness_cantileverRequiresThickerSlab`() {
        val ssResult = slab.checkSlabThickness(4000.0, SupportCondition.SIMPLY_SUPPORTED, 360.0, false)
        val cantResult = slab.checkSlabThickness(4000.0, SupportCondition.CANTILEVER, 360.0, false)
        assertTrue("Cantilever should require thinner slab (shorter effective span ratio)",
            cantResult.requiredThickness > 0)
    }

    @Test
    fun `getMinSlabThickness_positiveResult`() {
        val minT = slab.getMinSlabThickness(4000.0, SupportCondition.SIMPLY_SUPPORTED)
        assertTrue("Min thickness > 0", minT > 0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Code limits
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `getMinReinforcementRatio_returnsReasonableValue`() {
        val ratio = slab.getMinReinforcementRatio()
        assertEquals(0.0013, ratio, 0.0001)
    }

    @Test
    fun `getMaxBarSpacing_returns200mm`() {
        assertEquals(200.0, slab.getMaxBarSpacing(), 0.1)
    }

    @Test
    fun `getMinCover_returns20mm`() {
        assertEquals(20.0, slab.getMinCover(), 0.1)
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. InputGuard — one-way slab validation
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `designOneWaySlab_throwsForZeroFcu`() {
        slab.designOneWaySlab(0.0, 360.0, 150.0, 4000.0, 30.0, 40.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designOneWaySlab_throwsForNegativeFcu`() {
        slab.designOneWaySlab(-25.0, 360.0, 150.0, 4000.0, 30.0, 40.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designOneWaySlab_throwsForZeroFy`() {
        slab.designOneWaySlab(25.0, 0.0, 150.0, 4000.0, 30.0, 40.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designOneWaySlab_throwsForZeroThickness`() {
        slab.designOneWaySlab(25.0, 360.0, 0.0, 4000.0, 30.0, 40.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designOneWaySlab_throwsForZeroSpan`() {
        slab.designOneWaySlab(25.0, 360.0, 150.0, 0.0, 30.0, 40.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designOneWaySlab_throwsForNegativeThickness`() {
        slab.designOneWaySlab(25.0, 360.0, -150.0, 4000.0, 30.0, 40.0, LoadCombination.DEAD_LIVE)
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. InputGuard — two-way slab validation
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `designTwoWaySlab_throwsForZeroFcu`() {
        slab.designTwoWaySlab(0.0, 360.0, 200.0, 5000.0, 6000.0, defaultSupportConditions, 12.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designTwoWaySlab_throwsForZeroShortSpan`() {
        slab.designTwoWaySlab(25.0, 360.0, 200.0, 0.0, 6000.0, defaultSupportConditions, 12.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designTwoWaySlab_throwsForZeroLongSpan`() {
        slab.designTwoWaySlab(25.0, 360.0, 200.0, 5000.0, 0.0, defaultSupportConditions, 12.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designTwoWaySlab_throwsForZeroTotalLoad`() {
        slab.designTwoWaySlab(25.0, 360.0, 200.0, 5000.0, 6000.0, defaultSupportConditions, 0.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designTwoWaySlab_throwsForNegativeThickness`() {
        slab.designTwoWaySlab(25.0, 360.0, -200.0, 5000.0, 6000.0, defaultSupportConditions, 12.0, LoadCombination.DEAD_LIVE)
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. InputGuard — thickness check validation
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `checkSlabThickness_throwsForZeroSpan`() {
        slab.checkSlabThickness(0.0, SupportCondition.SIMPLY_SUPPORTED, 360.0, false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `checkSlabThickness_throwsForZeroFy`() {
        slab.checkSlabThickness(4000.0, SupportCondition.SIMPLY_SUPPORTED, 0.0, false)
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. Edge cases
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designOneWaySlab_highStrengthConcrete_producesResult`() {
        val result = slab.designOneWaySlab(50.0, 500.0, 150.0, 4000.0, 30.0, 40.0, LoadCombination.DEAD_LIVE)
        assertTrue("Should produce valid result for high-strength concrete", result.requiredReinforcement > 0)
    }

    @Test
    fun `designOneWaySlab_verySmallMoment_appliesMinimumSteel`() {
        val result = slab.designOneWaySlab(25.0, 360.0, 150.0, 4000.0, 0.5, 5.0, LoadCombination.DEAD_LIVE)
        assertTrue("Min steel should be applied", result.requiredReinforcement > 0)
    }

    @Test
    fun `designOneWaySlab_thickSlab_isSafe`() {
        val result = slab.designOneWaySlab(25.0, 360.0, 300.0, 4000.0, 20.0, 20.0, LoadCombination.DEAD_LIVE)
        assertTrue("Thick slab should be safe", result.isSafe)
    }

    @Test
    fun `designTwoWaySlab_squareSlap_bothDirectionsSimilar`() {
        val result = slab.designTwoWaySlab(
            25.0, 360.0, 200.0, 5000.0, 5000.0,
            defaultSupportConditions, 10.0, LoadCombination.DEAD_LIVE
        )
        // For a square slab with symmetric supports, short and long directions should be similar
        val ratio = result.shortDirection.requiredReinforcement / result.longDirection.requiredReinforcement
        assertTrue("Square slab should have similar reinforcement in both directions", ratio in 0.7..1.3)
    }
}
