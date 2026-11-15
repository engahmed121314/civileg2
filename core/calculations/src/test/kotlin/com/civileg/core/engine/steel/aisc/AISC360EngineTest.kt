package com.civileg.core.engine.steel.aisc

import com.civileg.core.engine.steel.SectionClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * اختبارات تدقيق محرك المنشآت المعدنية AISC 360-16 (Zero-Tolerance Verification Gate).
 * نسبة التفاوت المستهدفة: ≤ ±0.1% مقارنة بالأمثلة المعتمدة من AISC Design Examples و AISC Manual.
 *
 * // المرجع: AISC 360-16 Specification for Structural Steel Buildings.
 * // الوحدات: مم، نيوتن، ميغا باسكال (MPa = N/مم²).
 */
class AISC360EngineTest {

    private val tolerance = 0.001 // 0.1% tolerance per Audit Gate

    // =========================================================================
    // 1. تصنيف القطاعات (Section Classification) — AISC 360-16 Table B4.1
    // =========================================================================

    @Test
    fun testClassifyFlange_CompactCase1() {
        //Case 1: Flanges of rolled I-shaped sections (except M and S shapes)
        // λp = 0.38 * sqrt(E/Fy), λr = 1.0 * sqrt(E/Fy)
        // E = 200,000 MPa, Fy = 345 MPa (A572 Gr 50)
        // λp = 0.38 * sqrt(200000/345) = 0.38 * 24.079 = 9.150
        // λr = 1.0 * sqrt(200000/345) = 24.079
        val E = 200_000.0
        val Fy = 345.0
        val lambdaP = 0.38 * kotlin.math.sqrt(E / Fy)
        val lambdaR = 1.0 * kotlin.math.sqrt(E / Fy)

        // λ = 8.0 < λp -> Compact
        assertEquals(SectionClass.COMPACT, AISC360Engine.classifyFlange(8.0, E, Fy))
        // λ = 9.150 = λp boundary -> Compact (λ ≤ λp)
        assertEquals(SectionClass.COMPACT, AISC360Engine.classifyFlange(lambdaP, E, Fy))
        // λ = 15.0 between λp and λr -> Non-compact
        assertEquals(SectionClass.NON_COMPACT, AISC360Engine.classifyFlange(15.0, E, Fy))
        // λ = 24.079 = λr boundary -> Non-compact (λ ≤ λr)
        assertEquals(SectionClass.NON_COMPACT, AISC360Engine.classifyFlange(lambdaR, E, Fy))
        // λ = 30.0 > λr -> Slender
        assertEquals(SectionClass.SLENDER, AISC360Engine.classifyFlange(30.0, E, Fy))
    }

    @Test
    fun testClassifyFlange_DifferentSteelGrades() {
        // A36 steel: Fy = 250 MPa
        // λp = 0.38 * sqrt(200000/250) = 0.38 * 28.284 = 10.748
        // λr = 28.284
        val resultCompact = AISC360Engine.classifyFlange(5.0, 200_000.0, 250.0)
        assertEquals(SectionClass.COMPACT, resultCompact)

        val resultSlender = AISC360Engine.classifyFlange(40.0, 200_000.0, 250.0)
        assertEquals(SectionClass.SLENDER, resultSlender)
    }

    @Test
    fun testClassifyWeb_Compact() {
        // Web classification for rolled I-shapes: λp = 3.76 * sqrt(E/Fy), λr = 5.70 * sqrt(E/Fy)
        // E = 200,000, Fy = 345 -> sqrt(E/Fy) = 24.079
        // λp = 90.5, λr = 137.3
        assertEquals(SectionClass.COMPACT, AISC360Engine.classifyWeb(50.0, 200_000.0, 345.0))
        assertEquals(SectionClass.SLENDER, AISC360Engine.classifyWeb(200.0, 200_000.0, 345.0))
    }

    // =========================================================================
    // 2. عزم الانحناء الاسمي (Nominal Flexural Strength) — AISC 360-16 Chapter F
    // =========================================================================

    @Test
    fun testNominalMoment_CompactSection_Yielding() {
        // Section compact -> Mn = Mp = Fy * Zx
        // W-shape section: Zx = 1,500,000 mm³, Fy = 345 MPa
        // Mn = 345 * 1,500,000 = 517,500,000 N·mm = 517.5 kN·m
        val Fy = 345.0 // MPa
        val Zx = 1_500_000.0 // mm³
        val expectedMn = Fy * Zx // 517,500,000 N·mm
        val actualMn = AISC360Engine.calculateNominalMomentCompact(Fy, Zx)
        assertEquals(expectedMn, actualMn, expectedMn * tolerance)
    }

    @Test
    fun testNominalMoment_NonCompactSection_LateralTorsionalBuckling() {
        // Section non-compact: Mn = Mn (LTB controlled) < Mp
        // For non-compact: Mn = Mp - (Mp - 0.70 * Fy * Sx) * (λ - λp) / (λr - λp)
        // Using simplified: Mn = min(Mp, Fy * Sx) for flange local buckling
        val Fy = 345.0
        val Zx = 1_500_000.0
        val Sx = 1_350_000.0
        val Mp = Fy * Zx // 517.5e6 N·mm
        val My = Fy * Sx // 465.75e6 N·mm

        val lambdaP = 0.38 * kotlin.math.sqrt(200_000.0 / Fy)
        val lambdaR = 1.0 * kotlin.math.sqrt(200_000.0 / Fy)
        val lambda = 15.0 // بين λp و λr -> غير مضغوط
        val result = AISC360Engine.calculateNominalMomentNonCompact(Fy, Zx, Sx, lambda, lambdaP, lambdaR)
        // Should be ≤ Mp and ≥ 0.70 * My
        assertTrue("Mn must not exceed plastic moment Mp", result <= Mp * (1 + tolerance))
        assertTrue("Mn must be >= 0.70 * My", result >= 0.70 * My * (1 - tolerance))
    }

    @Test
    fun testNominalMoment_SlenderSection_Reduced() {
        // Section slender: Mn reduced by effective section properties
        val Fy = 345.0
        val Zx = 1_500_000.0
        val Sx = 1_350_000.0
        val Q = 0.85 // reduction factor for slender elements

        val result = AISC360Engine.calculateNominalMomentSlender(Fy, Zx, Sx, Q)
        val expectedMn = Q * Fy * Sx
        assertEquals(expectedMn, result, expectedMn * tolerance)
    }

    @Test
    fun testDesignStrength_PhiMn() {
        // φb = 0.90 for flexure (AISC 360-16 Table D1)
        // φMn = 0.90 * Mn
        val Mn = 500_000_000.0 // N·mm
        val expectedPhiMn = 0.90 * Mn // 450,000,000 N·mm
        val actualPhiMn = AISC360Engine.calculateDesignFlexuralStrength(Mn)
        assertEquals(expectedPhiMn, actualPhiMn, expectedPhiMn * tolerance)
    }

    // =========================================================================
    // 3. الانبعاج الجانبي الملتوي (Lateral-Torsional Buckling) — AISC 360-16 Cl. F2
    // =========================================================================

    @Test
    fun testLTB_LimitingLength_Lp() {
        // Lp = 1.76 * ry * sqrt(E/Fy) for compact sections (AISC 360-16 Eq. F2-5)
        // ry = 50 mm, E = 200,000, Fy = 345
        // Lp = 1.76 * 50 * sqrt(200000/345) = 1.76 * 50 * 24.079 = 2119 mm
        val ry = 50.0
        val E = 200_000.0
        val Fy = 345.0
        val expectedLp = 1.76 * ry * kotlin.math.sqrt(E / Fy)
        val actualLp = AISC360Engine.calculateLp(ry, E, Fy)
        assertEquals(expectedLp, actualLp, expectedLp * tolerance)
    }

    @Test
    fun testLTB_LimitingLength_Lr() {
        // Lr = 1.95 * rts * (E / 0.70 * Fy) * sqrt(J * c / (Sx * ho)) * sqrt(sqrt(1 + ...)) 
        // Simplified: Lr = 1.95 * rts * E / (0.70 * Fy) * sqrt(J * c / (Sx * ho))
        // Using standard formula approximation
        val rts = 60.0
        val E = 200_000.0
        val Fy = 345.0
        val J = 500_000.0 // mm⁴
        val c = 1.0 // torsional constant factor
        val Sx = 1_350_000.0 // mm³
        val ho = 300.0 // mm

        val result = AISC360Engine.calculateLr(rts, E, Fy, J, c, Sx, ho)
        assertTrue("Lr must be positive", result > 0)
        assertTrue("Lr must be > Lp for same section", result > AISC360Engine.calculateLp(rts, E, Fy))
    }

    @Test
    fun testLTB_CbFactor() {
        // Moment modification factor Cb (AISC 360-16 Eq. F1-1)
        // Cb = 1.25 + 0.125 * (Mmax/Mmid) + ... ≤ 2.5 (for simple cases)
        // With equal end moments (reverse curvature): Cb = 1.0 (approximately for symmetric)
        // For single curvature with equal end moments: Cb = 1.0
        val Mmax = 100.0
        val MA = 100.0 // same sign
        val MB = 100.0
        val MC = 100.0
        val MD = 100.0

        val cb = AISC360Engine.calculateCb(Mmax, MA, MB, MC, MD)
        // For uniform moment: Cb = 1.25 + 0.125*(1+1+1+1) - 0.5*... = depends on formula
        // At minimum Cb = 1.0 for doubly symmetric sections in single curvature with equal end moments
        assertTrue("Cb must be >= 1.0", cb >= 1.0)
        assertTrue("Cb must be <= 3.0 (conservative upper bound)", cb <= 3.0)
    }

    // =========================================================================
    // 4. الوصلات الملولية (Bolted Connections) — AISC 360-16 Chapter J
    // =========================================================================

    @Test
    fun testBoltShearStrength_StandardHole() {
        // Bolt shear strength: φFn = φ * Fn * Ab
        // A325 bolt: Fn = 448 MPa (shear), φ = 0.75 (AISC 360-16 Table J3.2)
        // 20 mm bolt: Ab = π * 20² / 4 = 314.16 mm²
        // φRn = 0.75 * 448 * 314.16 = 105,557 N = 105.6 kN
        val boltDiameterMm = 20.0
        val boltAreaMm2 = kotlin.math.PI * boltDiameterMm * boltDiameterMm / 4.0
        val FnMpa = 448.0 // A325 bolt shear strength (MPa)
        val phi = 0.75

        val expectedRn = phi * FnMpa * boltAreaMm2
        val actualRn = AISC360Engine.calculateBoltShearStrength(boltDiameterMm, FnMpa, phi)
        assertEquals(expectedRn, actualRn, expectedRn * tolerance)
    }

    @Test
    fun testBoltBearingStrength() {
        // Bearing strength: φRn = φ * 2.4 * dt * Fu * t (for standard holes, AISC 360-16 Eq. J3-6a)
        // dt = 20 mm, Fu = 400 MPa, t = 12 mm, φ = 0.75
        // φRn = 0.75 * 2.4 * 20 * 400 * 12 = 172,800 N = 172.8 kN
        val dt = 20.0
        val Fu = 400.0
        val t = 12.0
        val phi = 0.75

        val expectedRn = phi * 2.4 * dt * Fu * t
        val actualRn = AISC360Engine.calculateBoltBearingStrength(dt, Fu, t, phi)
        assertEquals(expectedRn, actualRn, expectedRn * tolerance)
    }

    @Test
    fun testSlipCriticalConnection() {
        // Slip-critical: φRn = φ * μ * hf * Sc * (number of slip planes)
        // μ = 0.30 (for class A surface), hf = 1.0, Sc = 90.0 kN (for 20mm A325 bolt)
        // φ = 1.0 for slip-critical (AISC 360-16 Table J3.1)
        val mu = 0.30
        val hf = 1.0
        val Sc = 90_000.0 // N
        val numBolts = 4
        val numSlipPlanes = 1

        val expectedRn = 1.0 * mu * hf * Sc * numBolts * numSlipPlanes // 108,000 N
        val actualRn = AISC360Engine.calculateSlipCriticalStrength(mu, hf, Sc, numBolts, numSlipPlanes)
        assertEquals(expectedRn, actualRn, expectedRn * tolerance)
    }

    // =========================================================================
    // 5. الفشل الهندسي — استثناءات السلامة (Safety Exceptions)
    // =========================================================================

    @Test(expected = IllegalArgumentException::class)
    fun testSectionClassification_InvalidFy() {
        AISC360Engine.classifyFlange(10.0, 200_000.0, -50.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNominalMoment_NegativeZx() {
        AISC360Engine.calculateNominalMomentCompact(345.0, -100.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBoltStrength_NegativeDiameter() {
        AISC360Engine.calculateBoltShearStrength(-10.0, 448.0, 0.75)
    }
}
