package com.civileg.core.engine.boq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * اختبارات تدقيق محرك كميات الخرسانة (Concrete Quantity Takeoff).
 * نسبة التفاوت المستهدفة: ≤ ±0.1% مقارنة بالحسابات التحليلية اليدوية المعتمدة.
 *
 * // المرجع: ECP 203 / ACI 318 - أبعاد القطاعات الإنشائية وحساب الحجوم.
 * // الوحدات: الطول بالملم، الحجم بملم³ (يُحوّل إلى م³ عبر القسمة على 1e9).
 */
class ConcreteQuantityTest {

    private val tolerance = 0.001 // 0.1% tolerance per Audit Gate

    // =========================================================================
    // 1. حجم الكمرة المستطيلة (Rectangular Beam)
    // =========================================================================

    @Test
    fun testRectangularBeamVolume() {
        // البعد: b = 300 مم، h = 500 مم، L = 6 متر = 6000 مم
        // V = b × h × L = 300 × 500 × 6000 = 900,000,000 مم³ = 0.9 م³
        val b = 300.0
        val h = 500.0
        val lengthMm = 6000.0

        val expectedVolumeMm3 = b * h * lengthMm // 900_000_000
        val expectedVolumeM3 = expectedVolumeMm3 / 1e9 // 0.9

        val result = ConcreteQuantityEngine.rectangularBeamVolumeMm3(b, h, lengthMm)
        assertEquals(expectedVolumeMm3, result, expectedVolumeMm3 * tolerance)

        val resultM3 = ConcreteQuantityEngine.rectangularBeamVolumeM3(b, h, lengthMm)
        assertEquals(expectedVolumeM3, resultM3, expectedVolumeM3 * tolerance)
    }

    @Test
    fun testRectangularBeamVolume_MultipleBeams() {
        // 4 كميرات بأبعاد متطابقة: الإجمالي = 4 × 0.9 = 3.6 م³
        val b = 300.0
        val h = 500.0
        val lengthMm = 6000.0
        val count = 4

        val expected = 4 * (b * h * lengthMm / 1e9) // 3.6 م³
        val actual = ConcreteQuantityEngine.totalBeamVolumeM3(b, h, lengthMm, count)
        assertEquals(expected, actual, expected * tolerance)
    }

    // =========================================================================
    // 2. حجم العمود المستطيل (Rectangular Column)
    // =========================================================================

    @Test
    fun testRectangularColumnVolume() {
        // البعد: 400 × 600 مم، الارتفاع = 3 متر = 3000 مم
        // V = 400 × 600 × 3000 = 720,000,000 مم³ = 0.72 م³
        val b = 400.0
        val h = 600.0
        val heightMm = 3000.0

        val expectedVolumeMm3 = b * h * heightMm // 720_000_000
        val expectedVolumeM3 = expectedVolumeMm3 / 1e9 // 0.72

        val result = ConcreteQuantityEngine.rectangularColumnVolumeMm3(b, h, heightMm)
        assertEquals(expectedVolumeMm3, result, expectedVolumeMm3 * tolerance)

        val resultM3 = ConcreteQuantityEngine.rectangularColumnVolumeM3(b, h, heightMm)
        assertEquals(expectedVolumeM3, resultM3, expectedVolumeM3 * tolerance)
    }

    // =========================================================================
    // 3. حجم البلاطة المصمتة (Solid Slab)
    // =========================================================================

    @Test
    fun testSolidSlabVolume() {
        // الأبعاد: L = 5 متر، W = 4 متر، السماكة t = 150 مم
        // V = 5000 × 4000 × 150 = 3,000,000,000 مم³ = 3.0 م³
        val lengthMm = 5000.0
        val widthMm = 4000.0
        val thicknessMm = 150.0

        val expectedVolumeMm3 = lengthMm * widthMm * thicknessMm // 3_000_000_000
        val expectedVolumeM3 = expectedVolumeMm3 / 1e9 // 3.0

        val result = ConcreteQuantityEngine.solidSlabVolumeMm3(lengthMm, widthMm, thicknessMm)
        assertEquals(expectedVolumeMm3, result, expectedVolumeMm3 * tolerance)

        val resultM3 = ConcreteQuantityEngine.solidSlabVolumeM3(lengthMm, widthMm, thicknessMm)
        assertEquals(expectedVolumeM3, resultM3, expectedVolumeM3 * tolerance)
    }

    // =========================================================================
    // 4. مساحة الشدة الخشبية - جوانب الكمرة (Formwork Sides)
    // =========================================================================

    @Test
    fun testBeamFormworkSidesArea() {
        // جوانب الكمرة الجانبية (دون الأسفل والسطح): 2 × (b + h) × L
        // b = 300، h = 500، L = 6000 مم
        // Area = 2 × (300 + 500) × 6000 = 2 × 800 × 6000 = 9,600,000 مم² = 9.6 م²
        val b = 300.0
        val h = 500.0
        val lengthMm = 6000.0

        val expectedAreaMm2 = 2.0 * (b + h) * lengthMm // 9_600_000
        val expectedAreaM2 = expectedAreaMm2 / 1e6 // 9.6

        val result = ConcreteQuantityEngine.beamFormworkSidesAreaMm2(b, h, lengthMm)
        assertEquals(expectedAreaMm2, result, expectedAreaMm2 * tolerance)

        val resultM2 = ConcreteQuantityEngine.beamFormworkSidesAreaM2(b, h, lengthMm)
        assertEquals(expectedAreaM2, resultM2, expectedAreaM2 * tolerance)
    }

    @Test
    fun testBeamFormworkSoffitArea() {
        // سطح الكمرة السفلي (Soffit): b × L
        // b = 300، L = 6000 مم → 1,800,000 مم² = 1.8 م²
        val b = 300.0
        val lengthMm = 6000.0

        val expectedAreaMm2 = b * lengthMm // 1_800_000
        val expectedAreaM2 = expectedAreaMm2 / 1e6 // 1.8

        val result = ConcreteQuantityEngine.beamSoffitAreaMm2(b, lengthMm)
        assertEquals(expectedAreaMm2, result, expectedAreaMm2 * tolerance)

        val resultM2 = ConcreteQuantityEngine.beamSoffitAreaM2(b, lengthMm)
        assertEquals(expectedAreaM2, resultM2, expectedAreaM2 * tolerance)
    }

    // =========================================================================
    // 5. مساحة الشدة الخشبية - جوانب العمود (Formwork Sides)
    // =========================================================================

    @Test
    fun testColumnFormworkSidesArea() {
        // جوانب العمود (دون الرأس والقاعدة): 2 × (b + h) × height
        // b = 400، h = 600، الارتفاع = 3000 مم
        // Area = 2 × (400 + 600) × 3000 = 6,000,000 مم² = 6.0 م²
        val b = 400.0
        val h = 600.0
        val heightMm = 3000.0

        val expectedAreaMm2 = 2.0 * (b + h) * heightMm // 6_000_000
        val expectedAreaM2 = expectedAreaMm2 / 1e6 // 6.0

        val result = ConcreteQuantityEngine.columnFormworkSidesAreaMm2(b, h, heightMm)
        assertEquals(expectedAreaMm2, result, expectedAreaMm2 * tolerance)

        val resultM2 = ConcreteQuantityEngine.columnFormworkSidesAreaM2(b, h, heightMm)
        assertEquals(expectedAreaM2, resultM2, expectedAreaM2 * tolerance)
    }

    // ==================================================================///////
    // 6. مساحة الشدة الخشبية - سطح البلاطة (Slab Soffit)
    // =========================================================================

    @Test
    fun testSlabSoffitArea() {
        // سطح البلاطة السفلي: L × W
        // L = 5000، W = 4000 مم → 20,000,000 مم² = 20.0 م²
        val lengthMm = 5000.0
        val widthMm = 4000.0

        val expectedAreaMm2 = lengthMm * widthMm // 20_000_000
        val expectedAreaM2 = expectedAreaMm2 / 1e6 // 20.0

        val result = ConcreteQuantityEngine.slabSoffitAreaMm2(lengthMm, widthMm)
        assertEquals(expectedAreaMm2, result, expectedAreaMm2 * tolerance)

        val resultM2 = ConcreteQuantityEngine.slabSoffitAreaM2(lengthMm, widthMm)
        assertEquals(expectedAreaM2, resultM2, expectedAreaM2 * tolerance)
    }

    // =========================================================================
    // 7.BOQ ملخص - 통합 الكميات
    // =========================================================================

    @Test
    fun testBOQSummary_TotalConcrete() {
        // كميات خرسانية من عناصر متعددة:
        // كميرة: 0.9 م³، عمود: 0.72 م³، بلاطة: 3.0 م³
        // الإجمالي = 0.9 + 0.72 + 3.0 = 4.62 م³
        val beamVol = 0.9
        val columnVol = 0.72
        val slabVol = 3.0

        val expectedTotal = beamVol + columnVol + slabVol // 4.62
        val actualTotal = ConcreteQuantityEngine.totalConcreteVolumeM3(listOf(beamVol, columnVol, slabVol))
        assertEquals(expectedTotal, actualTotal, expectedTotal * tolerance)
    }

    // =========================================================================
    // 8. Safety: قيم سالبة تُرفض
    // =========================================================================

    @Test(expected = IllegalArgumentException::class)
    fun testBeamVolume_NegativeWidthRejected() {
        ConcreteQuantityEngine.rectangularBeamVolumeMm3(-300.0, 500.0, 6000.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSlabVolume_NegativeThicknessRejected() {
        ConcreteQuantityEngine.solidSlabVolumeMm3(5000.0, 4000.0, -150.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testColumnFormwork_NegativeHeightRejected() {
        ConcreteQuantityEngine.columnFormworkSidesAreaMm2(400.0, 600.0, -3000.0)
    }
}
