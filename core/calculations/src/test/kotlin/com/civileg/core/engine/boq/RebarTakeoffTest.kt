package com.civileg.core.engine.boq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * اختبارات تدقيق محرك تفريد حديد التسليح (Rebar Takeoff / BBS).
 * نسبة التفاوت المستهدفة: ≤ ±0.1% مقارنة بجداول الأوزان القياسية للمعايير الهندسية.
 *
 * // المرجع: جدول أوزان أسياخ الصلب القياسية (kg/m) = d² × 0.006165 حيث d بالملم.
 * // الأقطار القياسية: φ8 → φ32 حسب ECP 203 / مواصفات التفريد المعتمدة.
 */
class RebarTakeoffTest {

    private val tolerance = 0.001 // 0.1% tolerance per Audit Gate

    // =========================================================================
    // 1. وزن المتر الطولي للسيخ (Unit Weight per Meter)
    // =========================================================================

    @Test
    fun testUnitWeight_Phi8() {
        // W = d² × 0.006165 = 8² × 0.006165 = 64 × 0.006165 = 0.39456 كغ/م
        val d = 8.0
        val expected = d * d * 0.006165 // 0.39456
        val actual = RebarTakeoffEngine.unitWeightKgPerMeter(d)
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testUnitWeight_Phi16() {
        // W = 16² × 0.006165 = 256 × 0.006165 = 1.57824 كغ/م
        val d = 16.0
        val expected = d * d * 0.006165 // 1.57824
        val actual = RebarTakeoffEngine.unitWeightKgPerMeter(d)
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testUnitWeight_Phi32() {
        // W = 32² × 0.006165 = 1024 × 0.006165 = 6.31296 كغ/م
        val d = 32.0
        val expected = d * d * 0.006165 // 6.31296
        val actual = RebarTakeoffEngine.unitWeightKgPerMeter(d)
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testUnitWeight_StandardDiameters() {
        // التحقق من تزايد الأوزان مع قطر السيخ
        val weights = mutableListOf<Double>()
        for (d in listOf(8.0, 10.0, 12.0, 16.0, 20.0, 25.0, 32.0)) {
            weights.add(RebarTakeoffEngine.unitWeightKgPerMeter(d))
        }
        for (i in 1 until weights.size) {
            assertTrue("Weight must increase with diameter", weights.get(i) > weights.get(i - 1))
        }
    }

    // =========================================================================
    // 2. وزن السيخ कुल (Total Bar Weight)
    // =========================================================================

    @Test
    fun testTotalBarWeight_SingleBar() {
        // سيخ φ16، الطول = 12 متر
        // W = 1.57824 كغ/م × 12 م = 18.93888 كغ
        val diameterMm = 16.0
        val lengthM = 12.0
        val unitWeight = 16.0 * 16.0 * 0.006165 // 1.57824

        val expected = unitWeight * lengthM // 18.93888
        val actual = RebarTakeoffEngine.totalBarWeightKg(diameterMm, lengthM)
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testTotalBarWeight_MultipleBars() {
        // 8 أسياخ φ20، كل سيخ 6 أمتار
        // وحدة φ20 = 20² × 0.006165 = 2.466 كغ/م
        // الإجمالي = 8 × 2.466 × 6 = 118.368 كغ
        val diameterMm = 20.0
        val lengthM = 6.0
        val count = 8
        val unitWeight = 20.0 * 20.0 * 0.006165 // 2.466

        val expected = count * unitWeight * lengthM // 118.368
        val actual = RebarTakeoffEngine.totalRebarWeightKg(diameterMm, lengthM, count)
        assertEquals(expected, actual, expected * tolerance)
    }

    // =========================================================================
    // 3. تفريد الحديد حسب القطر (Rebar Weight by Diameter)
    // =========================================================================

    @Test
    fun testRebarByDiameter_Grouping() {
        // مجموعة من الأسياخ بأقطار مختلفة:
        // 4×φ16 × 6م، 6×φ12 × 4م
        val entries = listOf(
            RebarEntry(diameterMm = 16.0, lengthM = 6.0, count = 4),
            RebarEntry(diameterMm = 12.0, lengthM = 4.0, count = 6)
        )

        val result = RebarTakeoffEngine.groupByDiameter(entries)

        // φ16: 4 × (16² × 0.006165) × 6 = 4 × 1.57824 × 6 = 37.87776 كغ
        val phi16Weight = 4.0 * (16.0 * 16.0 * 0.006165) * 6.0
        // φ12: 6 × (12² × 0.006165) × 4 = 6 × 0.88776 × 4 = 21.30624 كغ
        val phi12Weight = 6.0 * (12.0 * 12.0 * 0.006165) * 4.0

        val phi16Result = result.first { it.diameterMm == 16.0 }
        val phi12Result = result.first { it.diameterMm == 12.0 }

        assertEquals(phi16Weight, phi16Result.totalWeightKg, phi16Weight * tolerance)
        assertEquals(4, phi16Result.totalCount)
        assertEquals(24.0, phi16Result.totalLengthM, 0.001)

        assertEquals(phi12Weight, phi12Result.totalWeightKg, phi12Weight * tolerance)
        assertEquals(6, phi12Result.totalCount)
        assertEquals(24.0, phi12Result.totalLengthM, 0.001)
    }

    // =========================================================================
    // 4. جدول التفريد BBS - Bar Bending Schedule
    // =========================================================================

    @Test
    fun testBBSEntity_TotalLength_WithBends() {
        // سيخ مستقيم مع ثنيتين بزاوية 90° عند الأطراف
        // الطول الكلي = الطول الصافي +essor الزاويتان
        // افتراض: كل ثني 90° يضيف 6d (حسب المواصفات الهندسية الشائعة)
        // φ16، طول صافي = 6000 مم، ثنيتان 90°
        // إضافة كل ثني = 6 × 16 = 96 مم → total = 6000 + 2 × 96 = 6192 مم = 6.192 م
        val diameterMm = 16.0
        val clearLengthMm = 6000.0
        val numBends90 = 2

        val bendAllowanceMm = 6.0 * diameterMm * numBends90 // 192
        val expectedTotalLengthMm = clearLengthMm + bendAllowanceMm // 6192

        val result = RebarTakeoffEngine.barLengthWithBendsMm(clearLengthMm, diameterMm, numBends90)
        assertEquals(expectedTotalLengthMm, result, expectedTotalLengthMm * tolerance)
    }

    @Test
    fun testBBS_SummaryByDiameter() {
        // ملخص BBS لعدة أقطار
        val entries = listOf(
            RebarEntry(diameterMm = 16.0, lengthM = 6.0, count = 10),
            RebarEntry(diameterMm = 12.0, lengthM = 4.0, count = 15),
            RebarEntry(diameterMm = 8.0,  lengthM = 3.0, count = 20)
        )

        val summary = RebarTakeoffEngine.bbsSummary(entries)

        // التحقق من وجود الأقطار الثلاثة في الملخص
        assertTrue("BBS must contain φ16", summary.any { it.diameterMm == 16.0 })
        assertTrue("BBS must contain φ12", summary.any { it.diameterMm == 12.0 })
        assertTrue("BBS must contain φ8",  summary.any { it.diameterMm == 8.0 })

        // الوزن الإجمالي يجب أن يساوي مجموع الأوزان الفردية
        val totalExpected = entries.sumOf { e ->
            e.count * e.lengthM * (e.diameterMm * e.diameterMm * 0.006165)
        }
        val totalActual = summary.sumOf { it.totalWeightKg }
        assertEquals(totalExpected, totalActual, totalExpected * tolerance)
    }

    // =========================================================================
    // 5. Safety: أقطار غير صالحة تُرفض
    // =========================================================================

    @Test(expected = IllegalArgumentException::class)
    fun testUnitWeight_NegativeDiameterRejected() {
        RebarTakeoffEngine.unitWeightKgPerMeter(-16.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testUnitWeight_ZeroDiameterRejected() {
        RebarTakeoffEngine.unitWeightKgPerMeter(0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testTotalBarWeight_NegativeLengthRejected() {
        RebarTakeoffEngine.totalBarWeightKg(16.0, -5.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBendAllowance_NegativeDiameterRejected() {
        RebarTakeoffEngine.barLengthWithBendsMm(6000.0, -16.0, 2)
    }
}
