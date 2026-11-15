package com.civileg.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * اختبارات تدقيق وحدات القياس والتحويل (Units & UnitConverter).
 *
 * نسبة التفاوت المستهدفة: ≤ ±0.1% مقارنة بالقيم المحسوبة تحليلياً والمعتمدة.
 * جميع عوامل التحويل مستمدة من SI Brochure (BIPM) و ASTM E380 — دقيقة بالتعريف.
 *
 * // المرجع: SI Brochure (BIPM); ASTM E380 / IEEE-ASTM SI 10.
 */
class UnitsTest {

    private val tolerance = 0.001 // 0.1% tolerance per Audit Gate

    // =========================================================================
    // 1. Length conversions (base unit: mm)
    // =========================================================================

    @Test
    fun testLength_InchToMm_ExactDefinition() {
        // 1 بوصة = 25.4 مم exactement (بالتعريف، ASTM E380)
        val expected = 25.4
        val actual = Length.inch(1.0).mm
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testLength_FootToMm_ExactDefinition() {
        // 1 قدم = 12 × 25.4 = 304.8 مم
        val expected = 304.8
        val actual = Length.foot(1.0).mm
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testLength_YardToMm_ExactDefinition() {
        // 1 ياردة = 3 × 304.8 = 914.4 مم
        val expected = 914.4
        val actual = Length.yard(1.0).mm
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testLength_MileToMm_ExactDefinition() {
        // 1 ميل = 1760 × 914.4 = 1,609,344 مم
        val expected = 1_609_344.0
        val actual = Length.mile(1.0).mm
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testLength_MeterToMm_ExactDefinition() {
        // 1 متر = 1000 مم
        val expected = 1000.0
        val actual = Length.m(1.0).mm
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testLength_CentimeterToMm_ExactDefinition() {
        // 1 سم = 10 مم
        val expected = 10.0
        val actual = Length.cm(1.0).mm
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun lengthMmToMeter() {
        val expected = 1.0
        val actual = Length.mm(1000.0).m
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun lengthMmToFoot() {
        // 1000 مم / 304.8 = 3.280839895... قدم
        val expected = 1000.0 / 304.8
        val actual = Length.mm(1000.0).foot
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun lengthPlusDifferentUnits() {
        // 1 متر + 1 قدم = 1000 + 304.8 = 1304.8 مم
        val result = Length.m(1.0) + Length.foot(1.0)
        assertEquals(1304.8, result.mm, 0.01)
    }

    @Test
    fun lengthMinusDifferentUnits() {
        // 1 متر - 1 قدم = 1000 - 304.8 = 695.2 مم
        val result = Length.m(1.0) - Length.foot(1.0)
        assertEquals(695.2, result.mm, 0.01)
    }

    @Test
    fun lengthTimesScalar() {
        // 5 متر × 2 = 10 متر
        val result = Length.m(5.0) * 2.0
        assertEquals(10.0, result.m, 0.001)
    }

    @Test
    fun lengthDivScalar() {
        // 10 متر / 2 = 5 متر
        val result = Length.m(10.0) / 2.0
        assertEquals(5.0, result.m, 0.001)
    }

    // =========================================================================
    // 2. Force conversions (base unit: N)
    // =========================================================================

    @Test
    fun testForce_LbfToN_ExactDefinition() {
        // 1 lbf = 4.4482216152605 N (بالتعريف، الجاذبية القياسية)
        val expected = 4.4482216152605
        val actual = Force.lbf(1.0).n
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testForce_KipToN_ExactDefinition() {
        // 1 kip = 1000 lbf = 4448.2216152605 N
        val expected = 4448.2216152605
        val actual = Force.kip(1.0).n
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testForce_KgfToN_StandardGravity() {
        // 1 kgf = 9.80665 N (الجاذبية القياسية)
        val expected = 9.80665
        val actual = Force.kgf(1.0).n
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testForce_TfToN_MetricTonForce() {
        // 1 طن-قوة مترى = 1000 kgf = 9806.65 N
        val expected = 9806.65
        val actual = Force.tf(1.0).n
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testForce_KnToN() {
        // 1 kN = 1000 N
        val expected = 1000.0
        val actual = Force.kn(1.0).n
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testForce_NToLbf() {
        // 1 N = 1 / 4.4482216152605 lbf = 0.224808943...
        val expected = 1.0 / 4.4482216152605
        val actual = Force.n(1.0).lbf
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun forcePlusDifferentUnits() {
        // 10 kN + 1000 lbf = 10000 + 4448.2216152605 = 14448.2216... N
        val result = Force.kn(10.0) + Force.lbf(1000.0)
        assertEquals(14448.2216152605, result.n, 0.01)
    }

    @Test
    fun forceTimesScalar() {
        // 5 kN × 3 = 15 kN
        val result = Force.kn(5.0) * 3.0
        assertEquals(15.0, result.kn, 0.001)
    }

    // =========================================================================
    // 3. Stress conversions (base unit: MPa = N/mm²)
    // =========================================================================

    @Test
    fun testStress_PsiToMpa_ExactDefinition() {
        // 1 psi = 1 lbf/in² = 4.4482216152605 N / (25.4² mm²) = 0.006894757293168 MPa
        val expected = 4.4482216152605 / (25.4 * 25.4)
        val actual = Stress.psi(1.0).mpa
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testStress_KsiToMpa_ExactDefinition() {
        // 1 ksi = 1000 psi = 6.894757293168 MPa
        val expected = 6.894757293168
        val actual = Stress.ksi(1.0).mpa
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testStress_MpaToPa() {
        // 1 MPa = 1,000,000 Pa
        val expected = 1_000_000.0
        val actual = Stress.mpa(1.0).pa
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testStress_KgfCm2ToMpa() {
        // 1 kgf/cm² = 9.80665 N / (100 mm²) = 0.0980665 MPa
        val expected = 9.80665 / 100.0
        val actual = Stress.kgfCm2(1.0).mpa
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testStress_Nmm2EqualsMpa() {
        // 1 N/mm² = 1 MPa بالضبط (بالتعريف)
        val result = Stress.mpa(30.0)
        assertEquals(30.0, result.mpa, 0.001)
    }

    // =========================================================================
    // 4. Moment conversions (base unit: N·mm)
    // =========================================================================

    @Test
    fun testMoment_NmToNmm() {
        // 1 N·m = 1000 N·mm
        val expected = 1000.0
        val actual = Moment.nm(1.0).nmm
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testMoment_KNmToNmm() {
        // 1 kN·m = 1000 kN·mm = 1,000,000 N·mm
        val expected = 1_000_000.0
        val actual = Moment.knm(1.0).nmm
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testMoment_LbfFtToNmm_ExactDefinition() {
        // 1 lbf·ft = 4.4482216152605 N × 304.8 mm = 1355.8179483314 N·mm
        val expected = 4.4482216152605 * 304.8
        val actual = Moment.lbfFt(1.0).nmm
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testMoment_KipFtToNmm_ExactDefinition() {
        // 1 kip·ft = 4448.2216152605 N × 304.8 mm = 1355817.9483314 N·mm
        val expected = 4448.2216152605 * 304.8
        val actual = Moment.kipFt(1.0).nmm
        assertEquals(expected, actual, expected * tolerance)
    }

    @Test
    fun testMoment_KNmToLbfFt() {
        // 1 kN·m = 1,000,000 N·mm / 1355.8179483314 = 737.562... lbf·ft
        val expected = 1_000_000.0 / 1355.8179483314
        val actual = Moment.knm(1.0).lbfFt
        assertEquals(expected, actual, expected * tolerance)
    }

    // =========================================================================
    // 5. Dimensional consistency (physics checks)
    // =========================================================================

    @Test
    fun testStressEqualsForceDividedByArea() {
        // الإجهاد = القوة / المساحة
        // 1000 N / (100 مم × 100 مم) = 1000 / 10000 = 0.1 N/mm² = 0.1 MPa
        val force = Force.n(1000.0)
        val width = Length.mm(100.0)
        val height = Length.mm(100.0)
        val areaMm2 = width.mm * height.mm // 10,000 mm²
        val stressMpa = force.n / areaMm2 // N/mm² = MPa

        val expected = 0.1
        assertEquals(expected, stressMpa, expected * tolerance)

        // التحقق باستخدام 클래스 القياسات مباشرة
        val stressFromClasses = Stress(force.n / areaMm2, StressUnit.MPa)
        assertEquals(0.1, stressFromClasses.mpa, 0.001)
    }

    @Test
    fun testMomentEqualsForceTimesLength() {
        // العزوم = القوة × الطول
        // 10 kN × 5 متر = 50 kN·m = 50,000,000 N·mm
        val force = Force.kn(10.0)
        val length = Length.m(5.0)
        val momentNmm = force.n * length.mm // N × mm = N·mm

        val expected = 50_000_000.0
        assertEquals(expected, momentNmm, expected * tolerance)

        // التحقق باستخدام 클래س القياسات مباشرة
        val momentFromClasses = Moment(force.kn * length.m, MomentUnit.kNm)
        assertEquals(50.0, momentFromClasses.knm, 0.001)
    }

    // =========================================================================
    // 6. UnitConverter object tests
    // =========================================================================

    @Test
    fun testUnitConverter_ConvertLength() {
        // ASTM E380 — 1 بوصة = 25.4 مم
        val result = UnitConverter.convertLength(1.0, LengthUnit.IN, LengthUnit.MM)
        assertEquals(25.4, result, 0.001)
    }

    @Test
    fun testUnitConverter_ConvertForce() {
        // 1 kip = 4448.2216152605 N
        val result = UnitConverter.convertForce(1.0, ForceUnit.kip, ForceUnit.N)
        assertEquals(4448.2216152605, result, 0.01)
    }

    @Test
    fun testUnitConverter_ConvertStress() {
        // 1 ksi = 6.894757293168 MPa
        val result = UnitConverter.convertStress(1.0, StressUnit.ksi, StressUnit.MPa)
        assertEquals(6.894757293168, result, 0.001)
    }

    @Test
    fun testUnitConverter_ConvertMoment() {
        // 1 kN·m = 1,000,000 N·mm
        val result = UnitConverter.convertMoment(1.0, MomentUnit.kNm, MomentUnit.Nmm)
        assertEquals(1_000_000.0, result, 1.0)
    }

    @Test
    fun testUnitConverter_ConvertLengthObject() {
        // تحويل كائن Length من متر إلى قدم
        val result = UnitConverter.convertLength(Length.m(1.0), LengthUnit.FT)
        val expected = 1000.0 / 304.8
        assertEquals(expected, result.foot, expected * tolerance)
    }

    @Test
    fun testUnitConverter_ConvertForceObject() {
        // تحويل كائن Force من كيلو نيوتن إلى رطل-قوة
        val result = UnitConverter.convertForce(Force.kn(1.0), ForceUnit.lbf)
        val expected = 1000.0 / 4.4482216152605
        assertEquals(expected, result.lbf, expected * tolerance)
    }

    // =========================================================================
    // 7. Safety: zero values rejected
    // =========================================================================

    @Test(expected = IllegalArgumentException::class)
    fun testLength_ZeroRejected() {
        Length.mm(0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testForce_NegativeRejected() {
        Force.n(-100.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testStress_NegativeRejected() {
        Stress.mpa(-10.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMoment_ZeroRejected() {
        Moment.nmm(0.0)
    }
}
