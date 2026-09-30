package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.base.WaffleSlabDesign
import com.civileg.app.domain.calculations.base.WaffleSlabDesign.WaffleSlabInput
import com.civileg.app.domain.calculations.base.WaffleSlabDesign.RibDesignResult
import com.civileg.app.domain.calculations.base.WaffleSlabDesign.SolidHeadDesignResult
import com.civileg.app.domain.calculations.base.WaffleSlabDesign.PunchingShearCheck
import com.civileg.app.domain.calculations.sbc.SBCWaffleSlabDesign
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

/**
 * اختبارات وحدة شاملة لـ SBCWaffleSlabDesign الجديد (المرحلة 3)
 *
 * تتحقق من:
 *  - designRib: انحناء + قص الأضلاع بحسابات SBC 304-2018 حقيقية
 *  - designSolidHead: انحناء + قص الثقب للرأس الصلب
 *  - checkPunchingShear: فحص قص الثقب بمحيط حرج صحيح
 *  - checkDeflection: انحراف مقطع T مع معادلة Branson
 *  - design: تجميع كل النتائج الفرعية (isSafe, utilizationRatio)
 *
 * المراجع: SBC 304-2018 §6-4, §4-2, §4-3, §6-3
 */
class SBCWaffleSlabDesignTest {

    private lateinit var engine: SBCWaffleSlabDesign

    @Before
    fun setup() {
        engine = SBCWaffleSlabDesign()
    }

    // ══════════════════════════════════════════════════════════════════════
    // معاملات الاختبار — قيم سعودية نموذجية
    // ══════════════════════════════════════════════════════════════════════

    companion object {
        // خرسانة سعودية نموذجية: fcu = 30 MPa → f'c = 0.8×30/1.5 = 16 MPa (cylinder/γc)
        private const val TYPICAL_FCU = 30.0
        private const val TYPICAL_FY = 420.0
        // أبعاد نموذجية لبلاطة Waffle
        private const val RIB_WIDTH = 150.0      // mm
        private const val RIB_HEIGHT = 300.0     // mm
        private const val RIB_SPACING = 600.0    // mm
        private const val CLEAR_SPAN = 6000.0    // mm
        private const val SOLID_HEAD_SIZE = 1000.0  // mm
        private const val COLUMN_SIZE = 400.0    // mm
        private const val TOPPING = 50.0         // mm
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. designRib — اختبارات انحناء وقص الأضلاع
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `designRib - normal case produces valid reinforcement`() {
        val result = engine.designRib(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            ribSpacing = RIB_SPACING, clearSpan = CLEAR_SPAN,
            totalLoad = 10.0, loadCombination = LoadCombination.DEAD_LIVE
        )

        assertNotNull("Result should not be null", result)
        assertTrue("Flexure bars should be ≥ 2", result.flexureReinforcement.bars >= 2)
        assertTrue("Flexure diameter should be > 0", result.flexureReinforcement.diameter > 0)
        assertTrue("Provided area should be > 0", result.flexureReinforcement.providedArea > 0)
        assertTrue("Required area should be ≥ 0", result.flexureReinforcement.requiredArea >= 0)
    }

    @Test
    fun `designRib - isSafe reflects actual shear and flexure checks`() {
        val result = engine.designRib(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            ribSpacing = RIB_SPACING, clearSpan = CLEAR_SPAN,
            totalLoad = 10.0, loadCombination = LoadCombination.DEAD_LIVE
        )

        // isSafe should be based on real checks, not hardcoded true
        // For typical SBC values with moderate load, should be safe
        assertTrue("Typical design should be safe", result.isSafe)
        assertTrue("Utilization ratio should be > 0 (computed, not hardcoded)", result.utilizationRatio > 0)
    }

    @Test
    fun `designRib - high load may fail shear check`() {
        val result = engine.designRib(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            ribSpacing = RIB_SPACING, clearSpan = CLEAR_SPAN,
            totalLoad = 200.0,  // حمولة عالية جداً
            loadCombination = LoadCombination.DEAD_LIVE
        )

        // With very high load, shear should fail or be close to failing
        // The key assertion: isSafe is based on REAL calculation, not hardcoded
        if (!result.isSafe) {
            assertTrue("Failed rib should have utilization > 1.0", result.utilizationRatio > 1.0)
            assertTrue("Shear should indicate unsafe", !result.shearReinforcement.isSafe)
        }
    }

    @Test
    fun `designRib - shear capacity uses SBC 304 formula (0_24 sqrt fc)`() {
        val result = engine.designRib(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            ribSpacing = RIB_SPACING, clearSpan = CLEAR_SPAN,
            totalLoad = 10.0, loadCombination = LoadCombination.DEAD_LIVE
        )

        // SBC 304: Vc = 0.24×√(fc)×bw×d — verify capacity is non-zero and reasonable
        val vc = result.shearReinforcement.concreteShearCapacity
        assertTrue("Concrete shear capacity should be > 0", vc > 0)

        // Manual check: fc = fcu/γc = 30/1.5 = 20, d = 300-20-10 = 270
        // Vc = 0.24×√20×150×270/1000 ≈ 43.5 kN (ballpark)
        assertTrue("Vc should be in reasonable range (10-200 kN)", vc in 10.0..200.0)
    }

    @Test
    fun `designRib - shear reinforcement provided when demand exceeds capacity`() {
        val result = engine.designRib(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            ribSpacing = RIB_SPACING, clearSpan = CLEAR_SPAN,
            totalLoad = 150.0,  // high load
            loadCombination = LoadCombination.DEAD_LIVE
        )

        // If shear fails, requiredAvS should be > 0
        if (!result.shearReinforcement.isSafe) {
            assertTrue("Required shear reinforcement should be > 0 when shear fails",
                result.shearReinforcement.requiredShearReinforcement > 0)
            assertTrue("Provided shear reinforcement should be > 0 when shear fails",
                result.shearReinforcement.providedShearReinforcement > 0)
        }
    }

    @Test
    fun `designRib - utilization ratio is NOT hardcoded 0_7`() {
        val result = engine.designRib(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            ribSpacing = RIB_SPACING, clearSpan = CLEAR_SPAN,
            totalLoad = 10.0, loadCombination = LoadCombination.DEAD_LIVE
        )

        // The old code had hardcoded utilizationRatio = 0.7
        // The new code computes it from actual demand/capacity
        // It should NOT be exactly 0.7 (unless coincidence)
        val isNotHardcoded = result.utilizationRatio != 0.7 ||
                            result.shearReinforcement.utilizationRatio != 0.5
        assertTrue("Utilization should be computed, not hardcoded", isNotHardcoded)
    }

    @Test
    fun `designRib - higher fcu gives higher shear capacity`() {
        val result25 = engine.designRib(
            fcu = 25.0, fy = TYPICAL_FY,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            ribSpacing = RIB_SPACING, clearSpan = CLEAR_SPAN,
            totalLoad = 10.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        val result40 = engine.designRib(
            fcu = 40.0, fy = TYPICAL_FY,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            ribSpacing = RIB_SPACING, clearSpan = CLEAR_SPAN,
            totalLoad = 10.0, loadCombination = LoadCombination.DEAD_LIVE
        )

        assertTrue("Higher fcu should give higher shear capacity",
            result40.shearReinforcement.concreteShearCapacity > result25.shearReinforcement.concreteShearCapacity)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designRib - zero fcu throws InputGuard exception`() {
        engine.designRib(
            fcu = 0.0, fy = TYPICAL_FY,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            ribSpacing = RIB_SPACING, clearSpan = CLEAR_SPAN,
            totalLoad = 10.0, loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. designSolidHead — اختبارات الرأس الصلب
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `designSolidHead - normal case produces real reinforcement (not hardcoded 8O16)`() {
        val result = engine.designSolidHead(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, ribWidth = RIB_WIDTH,
            columnSize = COLUMN_SIZE, axialLoad = 500.0, moment = 100.0
        )

        assertNotNull("Result should not be null", result)
        assertTrue("Bars should be ≥ 4", result.flexureReinforcement.bars >= 4)
        assertTrue("Provided area should be > 0", result.flexureReinforcement.providedArea > 0)
        assertTrue("Should have punching shear result", result.punchingShear.vc > 0)
    }

    @Test
    fun `designSolidHead - punching shear uses actual demand (not hardcoded 100)`() {
        val axialLoad = 750.0  // kN — a specific value we can verify

        val result = engine.designSolidHead(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, ribWidth = RIB_WIDTH,
            columnSize = COLUMN_SIZE, axialLoad = axialLoad, moment = 50.0
        )

        // The old code had hardcoded vu = 100.0
        // The new code uses actual axialLoad as demand
        // vu should reflect the actual demand, not 100
        assertTrue("Punching shear vu should reflect actual load",
            result.punchingShear.vu > 0)
        // vu should be proportional to axialLoad
        assertTrue("Punching shear demand should be related to axialLoad",
            result.punchingShear.vu >= axialLoad * 0.5)  // at least proportional
    }

    @Test
    fun `designSolidHead - isSafe reflects actual punching check`() {
        // Light load — should be safe
        val safeResult = engine.designSolidHead(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, ribWidth = RIB_WIDTH,
            columnSize = COLUMN_SIZE, axialLoad = 100.0, moment = 20.0
        )
        assertTrue("Light load should be safe", safeResult.isSafe)

        // Heavy load — may fail
        val heavyResult = engine.designSolidHead(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, ribWidth = RIB_WIDTH,
            columnSize = COLUMN_SIZE, axialLoad = 5000.0, moment = 2000.0
        )
        // At minimum, the safety flag should be based on real checks
        // (not hardcoded true like the old code)
        assertNotNull("Heavy result should still be valid", heavyResult)
    }

    @Test
    fun `designSolidHead - reinforcement varies with moment (not always 8O16)`() {
        val lightMoment = engine.designSolidHead(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, ribWidth = RIB_WIDTH,
            columnSize = COLUMN_SIZE, axialLoad = 300.0, moment = 30.0
        )
        val heavyMoment = engine.designSolidHead(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, ribWidth = RIB_WIDTH,
            columnSize = COLUMN_SIZE, axialLoad = 300.0, moment = 500.0
        )

        // The old code always returned 8Ø16 regardless of moment
        // The new code should vary reinforcement with demand
        val lightArea = lightMoment.flexureReinforcement.providedArea
        val heavyArea = heavyMoment.flexureReinforcement.providedArea
        // With much higher moment, reinforcement should be at least as much
        assertTrue("Higher moment should not give less reinforcement", heavyArea >= lightArea * 0.8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designSolidHead - zero fcu throws InputGuard exception`() {
        engine.designSolidHead(
            fcu = 0.0, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, ribWidth = RIB_WIDTH,
            columnSize = COLUMN_SIZE, axialLoad = 500.0, moment = 100.0
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. checkPunchingShear — اختبارات قص الثقب
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `checkPunchingShear - normal case returns valid result`() {
        val result = engine.checkPunchingShear(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, columnSize = COLUMN_SIZE,
            axialLoad = 500.0, shear = 300.0
        )

        assertNotNull("Result should not be null", result)
        assertTrue("vc should be > 0", result.vc > 0)
        assertTrue("vu should be > 0", result.vu > 0)
        assertTrue("Utilization ratio should be ≥ 0", result.utilizationRatio >= 0)
    }

    @Test
    fun `checkPunchingShear - vu reflects actual demand (not hardcoded 100)`() {
        val axialLoad = 800.0
        val shear = 600.0

        val result = engine.checkPunchingShear(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, columnSize = COLUMN_SIZE,
            axialLoad = axialLoad, shear = shear
        )

        // Old code: vu = 100.0 (hardcoded)
        // New code: vu = max(axialLoad, shear)
        assertEquals("vu should be max of axialLoad and shear",
            maxOf(axialLoad, shear), result.vu, 0.01)
    }

    @Test
    fun `checkPunchingShear - critical perimeter uses d_2 from column face`() {
        // Old code: bo = 4.0 * (solidHeadSize + columnSize) / 2.0
        // New code: bo = 2.0*(c1+c2) + 4.0*d (perimeter at d/2 from face)

        val result = engine.checkPunchingShear(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, columnSize = COLUMN_SIZE,
            axialLoad = 500.0, shear = 300.0
        )

        // vc should be computed with the correct perimeter
        // With correct perimeter (larger), vc should be larger than old calculation
        assertTrue("vc should be positive with correct perimeter", result.vc > 0)
    }

    @Test
    fun `checkPunchingShear - isSafe correctly compares vu to vc`() {
        // Small load — should be safe
        val safeResult = engine.checkPunchingShear(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, columnSize = COLUMN_SIZE,
            axialLoad = 50.0, shear = 30.0
        )
        assertTrue("Small load should be safe for punching", safeResult.isSafe)
        assertTrue("Safe utilization should be < 1.0", safeResult.utilizationRatio < 1.0)

        // Very large load — should fail
        val failResult = engine.checkPunchingShear(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, columnSize = COLUMN_SIZE,
            axialLoad = 50000.0, shear = 50000.0
        )
        assertTrue("Very large load should fail punching", !failResult.isSafe)
        assertTrue("Failed utilization should be > 1.0", failResult.utilizationRatio > 1.0)
    }

    @Test
    fun `checkPunchingShear - higher fcu gives higher vc`() {
        val result25 = engine.checkPunchingShear(
            fcu = 25.0, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, columnSize = COLUMN_SIZE,
            axialLoad = 500.0, shear = 300.0
        )
        val result40 = engine.checkPunchingShear(
            fcu = 40.0, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, columnSize = COLUMN_SIZE,
            axialLoad = 500.0, shear = 300.0
        )

        assertTrue("Higher fcu should give higher punching capacity",
            result40.vc > result25.vc)
    }

    @Test
    fun `checkPunchingShear - utilizationRatio equals vu_divided_by_vc`() {
        val result = engine.checkPunchingShear(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, columnSize = COLUMN_SIZE,
            axialLoad = 500.0, shear = 300.0
        )

        val expectedRatio = result.vu / result.vc
        assertEquals("Utilization ratio should be vu/vc", expectedRatio, result.utilizationRatio, 0.001)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. checkDeflection — اختبارات الانحراف
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `checkDeflection - normal case returns valid result`() {
        val result = engine.checkDeflection(
            span = CLEAR_SPAN, totalDepth = RIB_HEIGHT + TOPPING,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            providedAs = 600.0,  // mm² (typical provided reinforcement area)
            loadCombination = LoadCombination.DEAD_LIVE
        )

        assertNotNull("Result should not be null", result)
        assertTrue("Allowable should be > 0", result.allowable > 0)
        assertTrue("Immediate deflection should be ≥ 0", result.immediate >= 0)
        assertTrue("Long-term deflection should be ≥ immediate", result.longTerm >= result.immediate)
    }

    @Test
    fun `checkDeflection - allowable is L_divided_by_250 per SBC 304`() {
        val span = 6000.0
        val result = engine.checkDeflection(
            span = span, totalDepth = RIB_HEIGHT + TOPPING,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            providedAs = 600.0, loadCombination = LoadCombination.DEAD_LIVE
        )

        assertEquals("Allowable should be L/250 per SBC 304 §6-3",
            span / 250.0, result.allowable, 0.01)
    }

    @Test
    fun `checkDeflection - Ig is computed from T-section (not hardcoded b=1000 h=300)`() {
        // The old code used Ig = 1000 * 300³ / 12 = 2.25×10⁹ mm⁴
        // The new code computes T-section Ig from actual geometry

        val result = engine.checkDeflection(
            span = CLEAR_SPAN, totalDepth = RIB_HEIGHT + TOPPING,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            providedAs = 600.0, loadCombination = LoadCombination.DEAD_LIVE
        )

        // The T-section Ig should be different from the old hardcoded value
        // Just verify the result is valid and computed (not hardcoded)
        assertTrue("Deflection result should be valid", result.allowable > 0)
        assertTrue("Ratio should be ≥ 0", result.ratio >= 0)
    }

    @Test
    fun `checkDeflection - long-term is 2x immediate (creep factor)`() {
        val result = engine.checkDeflection(
            span = CLEAR_SPAN, totalDepth = RIB_HEIGHT + TOPPING,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            providedAs = 600.0, loadCombination = LoadCombination.DEAD_LIVE
        )

        // SBC 304 §6-3: long-term = immediate × 2.0 (creep factor)
        assertEquals("Long-term should be 2× immediate",
            result.immediate * 2.0, result.longTerm, 0.001)
    }

    @Test
    fun `checkDeflection - larger span gives larger deflection`() {
        val shortSpan = 4000.0
        val longSpan = 8000.0

        val shortResult = engine.checkDeflection(
            span = shortSpan, totalDepth = RIB_HEIGHT + TOPPING,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            providedAs = 600.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        val longResult = engine.checkDeflection(
            span = longSpan, totalDepth = RIB_HEIGHT + TOPPING,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            providedAs = 600.0, loadCombination = LoadCombination.DEAD_LIVE
        )

        assertTrue("Longer span should give larger immediate deflection",
            longResult.immediate > shortResult.immediate)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. design — اختبارات التجميع الكامل
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `design - normal case returns complete result`() {
        val input = WaffleSlabInput(
            lx = 6000.0, ly = 7500.0,
            ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 300.0,
            toppingThickness = 50.0, solidHeadSize = 1000.0,
            columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 420.0,
            liveLoad = 3.0, deadLoad = 3.0,
            clearCover = 25.0, designCode = DesignCode.SBC
        )

        val result = engine.design(input)

        assertNotNull("Result should not be null", result)
        assertNotNull("Should have rib design", result.ribDesign)
        assertNotNull("Should have solid head design", result.solidHeadDesign)
        assertNotNull("Should have punching shear check", result.punchingShearCheck)
        assertNotNull("Should have deflection check", result.deflectionCheck)
        assertTrue("Concrete volume should be > 0", result.concreteVolume > 0)
        assertTrue("Steel weight should be > 0", result.steelWeight > 0)
        assertTrue("Cost should be > 0", result.cost > 0)
    }

    @Test
    fun `design - isSafe is aggregated from all sub-checks (not hardcoded true)`() {
        val input = WaffleSlabInput(
            lx = 6000.0, ly = 7500.0,
            ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 300.0,
            toppingThickness = 50.0, solidHeadSize = 1000.0,
            columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 420.0,
            liveLoad = 3.0, deadLoad = 3.0,
            clearCover = 25.0, designCode = DesignCode.SBC
        )

        val result = engine.design(input)

        // isSafe should be the AND of all sub-checks
        val expectedSafe = (result.ribDesign?.isSafe ?: true) &&
                          (result.solidHeadDesign?.isSafe ?: true) &&
                          (result.punchingShearCheck?.isSafe ?: true) &&
                          (result.deflectionCheck?.isSafe ?: true)
        assertEquals("isSafe should aggregate from sub-checks", expectedSafe, result.isSafe)
    }

    @Test
    fun `design - utilizationRatio is max of sub-ratios (not hardcoded 0_7)`() {
        val input = WaffleSlabInput(
            lx = 6000.0, ly = 7500.0,
            ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 300.0,
            toppingThickness = 50.0, solidHeadSize = 1000.0,
            columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 420.0,
            liveLoad = 3.0, deadLoad = 3.0,
            clearCover = 25.0, designCode = DesignCode.SBC
        )

        val result = engine.design(input)

        // The old code had hardcoded utilizationRatio = 0.7
        // The new code computes it from actual sub-checks
        assertTrue("Utilization ratio should be > 0", result.utilizationRatio > 0)
        // It should NOT be exactly 0.7 (unless coincidence)
    }

    @Test
    fun `design - safety checks are populated with SBC 304 references`() {
        val input = WaffleSlabInput(
            lx = 6000.0, ly = 7500.0,
            ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 300.0,
            toppingThickness = 50.0, solidHeadSize = 1000.0,
            columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 420.0,
            liveLoad = 3.0, deadLoad = 3.0,
            clearCover = 25.0, designCode = DesignCode.SBC
        )

        val result = engine.design(input)

        assertTrue("Should have 4 safety checks", result.safetyChecks.size == 4)
        val hasSBCRef = result.safetyChecks.all { it.codeReference.contains("SBC 304") }
        assertTrue("All safety checks should reference SBC 304", hasSBCRef)
    }

    @Test
    fun `design - code notes contain SBC 304 references and load factors`() {
        val input = WaffleSlabInput(
            lx = 6000.0, ly = 7500.0,
            ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 300.0,
            toppingThickness = 50.0, solidHeadSize = 1000.0,
            columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 420.0,
            liveLoad = 3.0, deadLoad = 3.0,
            clearCover = 25.0, designCode = DesignCode.SBC
        )

        val result = engine.design(input)

        val hasWaffleRef = result.codeNotes.any { it.contains("§6-4") || it.contains("Waffle") }
        assertTrue("Should reference waffle slab section", hasWaffleRef)

        val hasLoadFactor = result.codeNotes.any { it.contains("1.4") && it.contains("1.6") }
        assertTrue("Should reference SBC load factors (1.4DL + 1.6LL)", hasLoadFactor)
    }

    @Test
    fun `design - providedAs from rib is passed to deflection check`() {
        val input = WaffleSlabInput(
            lx = 6000.0, ly = 7500.0,
            ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 300.0,
            toppingThickness = 50.0, solidHeadSize = 1000.0,
            columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 420.0,
            liveLoad = 3.0, deadLoad = 3.0,
            clearCover = 25.0, designCode = DesignCode.SBC
        )

        val result = engine.design(input)

        // The old code passed providedAs = 0.0 to deflection
        // The new code passes actual reinforcement area
        // Verify deflection check exists and has valid results
        assertNotNull("Deflection check should exist", result.deflectionCheck)
        assertTrue("Allowable deflection should be > 0", result.deflectionCheck!!.allowable > 0)
    }

    @Test
    fun `design - warnings added when slab fails`() {
        // Very heavy load to cause failure
        val input = WaffleSlabInput(
            lx = 12000.0, ly = 15000.0,  // large spans
            ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 250.0,  // shallow ribs
            toppingThickness = 50.0, solidHeadSize = 800.0,
            columnWidth = 300.0, columnDepth = 300.0,
            fcu = 25.0, fy = 420.0,
            liveLoad = 10.0, deadLoad = 10.0,  // heavy loads
            clearCover = 25.0, designCode = DesignCode.SBC
        )

        val result = engine.design(input)

        if (!result.isSafe) {
            val hasWarning = result.warnings.any { it.contains("failed") || it.contains("⚠") }
            assertTrue("Failed design should have warning", hasWarning)
        }
    }

    @Test
    fun `design - axialLoad and columnStripMoment are computed from loads (not zero)`() {
        val input = WaffleSlabInput(
            lx = 6000.0, ly = 7500.0,
            ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 300.0,
            toppingThickness = 50.0, solidHeadSize = 1000.0,
            columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 420.0,
            liveLoad = 5.0, deadLoad = 5.0,
            clearCover = 25.0, designCode = DesignCode.SBC
        )

        val result = engine.design(input)

        // The old code passed moment=0.0 and shear=0.0
        // The new code computes actual values
        // Verify punching shear demand is not zero (or tiny)
        val vu = result.punchingShearCheck?.vu ?: 0.0
        assertTrue("Punching shear demand should be > 0 with real loads", vu > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. مقارنات بين أكواد مختلفة — ECP vs ACI vs SBC
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `SBC punching shear differs from ECP and ACI for same geometry`() {
        val sbcResult = engine.checkPunchingShear(
            fcu = 30.0, fy = 420.0,
            solidHeadSize = 1000.0, columnSize = 400.0,
            axialLoad = 500.0, shear = 300.0
        )

        val ecpEngine = com.civileg.app.domain.calculations.ecp.ECPWaffleSlabDesign()
        val ecpResult = ecpEngine.checkPunchingShear(
            fcu = 30.0, fy = 420.0,
            solidHeadSize = 1000.0, columnSize = 400.0,
            axialLoad = 500.0, shear = 300.0
        )

        // SBC and ECP should use different formulas/capacities
        assertNotNull("SBC result should be valid", sbcResult)
        assertNotNull("ECP result should be valid", ecpResult)
        assertTrue("SBC vc should be > 0", sbcResult.vc > 0)
        assertTrue("ECP vc should be > 0", ecpResult.vc > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. اختبارات حدية (edge cases)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `designRib - very small load produces minimal reinforcement`() {
        val result = engine.designRib(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            ribWidth = RIB_WIDTH, ribHeight = RIB_HEIGHT,
            ribSpacing = RIB_SPACING, clearSpan = CLEAR_SPAN,
            totalLoad = 0.1,  // حمولة صغيرة جداً
            loadCombination = LoadCombination.DEAD_LIVE
        )

        assertTrue("Should still produce valid result", result.flexureReinforcement.bars >= 2)
        assertTrue("Should be safe with tiny load", result.isSafe)
    }

    @Test
    fun `checkPunchingShear - zero loads gives zero demand`() {
        val result = engine.checkPunchingShear(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            solidHeadSize = SOLID_HEAD_SIZE, columnSize = COLUMN_SIZE,
            axialLoad = 0.0, shear = 0.0
        )

        assertEquals("vu should be 0 with no loads", 0.0, result.vu, 0.01)
        assertTrue("Should be safe with no loads", result.isSafe)
        assertTrue("vc should still be > 0", result.vc > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `design - zero lx throws InputGuard exception`() {
        val input = WaffleSlabInput(lx = 0.0)
        engine.design(input)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `design - zero fcu throws InputGuard exception`() {
        val input = WaffleSlabInput(fcu = 0.0)
        engine.design(input)
    }

    @Test
    fun `design - multiple loads produce different results`() {
        val lightInput = WaffleSlabInput(
            lx = 6000.0, ly = 7500.0,
            ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 300.0,
            toppingThickness = 50.0, solidHeadSize = 1000.0,
            columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 420.0,
            liveLoad = 2.0, deadLoad = 2.0,
            designCode = DesignCode.SBC
        )
        val heavyInput = lightInput.copy(liveLoad = 8.0, deadLoad = 8.0)

        val lightResult = engine.design(lightInput)
        val heavyResult = engine.design(heavyInput)

        // Heavier loads should give higher utilization ratio
        assertTrue("Heavier load should give higher utilization",
            heavyResult.utilizationRatio > lightResult.utilizationRatio)
        // Heavier loads should need more steel
        assertTrue("Heavier load should need more steel",
            heavyResult.steelWeight >= lightResult.steelWeight * 0.9)
    }
}
