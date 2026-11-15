package com.civileg.core.engine.steel.aisc

import com.civileg.core.engine.steel.SectionClass
import com.civileg.core.engine.steel.ExcessiveUnbracedLengthException
import com.civileg.core.engine.steel.InsufficientBoltCapacityException
import com.civileg.core.engine.steel.InvalidSectionException
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * محرك التصميم للمنشآت المعدنية وفق الكود الأمريكي AISC 360-16.
 * Pure Kotlin — Zero Android / Zero UI dependencies.
 *
 * المراجع المعتمدة:
 * - AISC 360-16 Chapter B: Design Requirements (Section Classification, Table B4.1)
 * - AISC 360-16 Chapter F: Design of Members for Flexure (Nominal Moment, LTB)
 * - AISC 360-16 Chapter D: Design of Tension and Compression Members (φ factors)
 * - AISC 360-16 Chapter J: Design of Connections (Bolts, Welds)
 * - AISC 360-16 Appendix D: Structural Steel Properties
 *
 * // الوحدات المستخدمة: مم (mm)، نيوتن (N)، ميغا باسكال (MPa = N/مم²).
 */
object AISC360Engine {

    // =========================================================================
    // ثوابت الكود (AISC 360-16)
    // =========================================================================

    /** معامل مرونة الفولاذ E = 200,000 MPa (AISC 360-16 Appendix D1) */
    const val E_MPA = 200_000.0

    /** معامل تخفيض المقاومة للانحناء φb = 0.90 (AISC 360-16 Table D1) */
    const val PHI_B_FLEXURE = 0.90

    /** معامل تخفيض المقاومة للقص φv = 0.90 (AISC 360-16 Table D1) */
    const val PHI_V_SHEAR = 0.90

    /** معامل تخفيض المقاومة للمسامير φ = 0.75 (AISC 360-16 Table J3.1, bearing/shear) */
    const val PHI_BOLT_SHEAR_BEARING = 0.75

    /** معامل تخفيض المقاومة للوصلات الانزلاقية φ = 1.00 (AISC 360-16 Table J3.1) */
    const val PHI_SLIP_CRITICAL = 1.00

    // =========================================================================
    // 1. تصنيف القطاعات (Section Classification) — AISC 360-16 Table B4.1
    // =========================================================================

    /**
     * تصنيف حافة القطاع (flange) بناءً على نسبة العرض إلى السمك λ.
     *
     * // AISC 360-16 Table B4.1, Case 1: Flanges of rolled I-shaped sections (except M and S shapes)
     * λp = 0.38 * sqrt(E / Fy)
     * λr = 1.0 * sqrt(E / Fy)
     *
     * إذا λ ≤ λp -> Compact
     * إذا λp < λ ≤ λr -> Non-compact
     * إذا λ > λr -> Slender
     */
    fun classifyFlange(
        lambda: Double,
        E: Double = E_MPA,
        Fy: Double
    ): SectionClass {
        require(Fy > 0.0) { "Yield strength Fy must be > 0" }
        require(lambda >= 0.0) { "Lambda must be >= 0" }

        val lambdaP = 0.38 * sqrt(E / Fy)
        val lambdaR = 1.0 * sqrt(E / Fy)

        return when {
            lambda <= lambdaP -> SectionClass.COMPACT
            lambda <= lambdaR -> SectionClass.NON_COMPACT
            else -> SectionClass.SLENDER
        }
    }

    /**
     * تصنيف الجذع (web) بناءً على نسبة الارتفاع إلى السمك λ.
     *
     * // AISC 360-16 Table B4.1, Case 5: Webs of rolled I-shaped sections
     * λp = 3.76 * sqrt(E / Fy)
     * λr = 5.70 * sqrt(E / Fy)
     */
    fun classifyWeb(
        lambda: Double,
        E: Double = E_MPA,
        Fy: Double
    ): SectionClass {
        require(Fy > 0.0) { "Yield strength Fy must be > 0" }
        require(lambda >= 0.0) { "Lambda must be >= 0" }

        val lambdaP = 3.76 * sqrt(E / Fy)
        val lambdaR = 5.70 * sqrt(E / Fy)

        return when {
            lambda <= lambdaP -> SectionClass.COMPACT
            lambda <= lambdaR -> SectionClass.NON_COMPACT
            else -> SectionClass.SLENDER
        }
    }

    // =========================================================================
    // 2. العزم الانحناء الاسمي (Nominal Flexural Strength) — AISC 360-16 Chapter F
    // =========================================================================

    /**
     * العزم الاسمي للقطاع المضغوط (Compact): Mn = Mp = Fy * Zx.
     *
     * // AISC 360-16 Cl. F2.1, Eq. F2-1: Mn = Mp = Fy * Zx (لأطوال غير مدعمة قصيرة Lb ≤ Lp)
     */
    fun calculateNominalMomentCompact(Fy: Double, Zx: Double): Double {
        require(Fy > 0.0) { "Yield strength Fy must be > 0" }
        require(Zx > 0.0) { "Plastic section modulus Zx must be > 0" }
        return Fy * Zx
    }

    /**
     * العزم الاسمي للقطاع غير المضغوط (Non-compact) مع الت khúcان بين Mp و Mr.
     *
     * // AISC 360-16 Cl. F2.2, Eq. F2-3: Mn = Mp - (Mp - 0.70 * Fy * Sx) * (λ - λp) / (λr - λp)
     * حيث Mp = Fy * Zx و Mr = 0.70 * Fy * Sx
     */
    fun calculateNominalMomentNonCompact(
        Fy: Double,
        Zx: Double,
        Sx: Double,
        lambda: Double,
        lambdaP: Double,
        lambdaR: Double
    ): Double {
        require(Fy > 0.0) { "Yield strength Fy must be > 0" }
        require(Zx > 0.0) { "Plastic section modulus Zx must be > 0" }
        require(Sx > 0.0) { "Elastic section modulus Sx must be > 0" }
        require(lambdaR > lambdaP) { "lambdaR must be > lambdaP" }

        val Mp = Fy * Zx
        val Mr = 0.70 * Fy * Sx

        return when {
            lambda <= lambdaP -> Mp
            lambda >= lambdaR -> Mr
            else -> Mp - (Mp - Mr) * (lambda - lambdaP) / (lambdaR - lambdaP)
        }
    }

    /**
     * العزم الاسمي للقطاع النحيف (Slender) باستخدام معامل التخفيض Q.
     *
     * // AISC 360-16 Cl. F2.4, Eq. F2-6: Mn = Q * Fy * Sx (بالتقريب المبسط)
     */
    fun calculateNominalMomentSlender(
        Fy: Double,
        Zx: Double,
        Sx: Double,
        Q: Double
    ): Double {
        require(Fy > 0.0) { "Yield strength Fy must be > 0" }
        require(Sx > 0.0) { "Elastic section modulus Sx must be > 0" }
        require(Q in 0.0..1.0) { "Reduction factor Q must be between 0 and 1" }
        return Q * Fy * Sx
    }

    /**
     * العزم التصميمي φMn.
     *
     * // AISC 360-16 Chapter D, Table D1: φb = 0.90 للانحناء
     */
    fun calculateDesignFlexuralStrength(Mn: Double): Double {
        require(Mn >= 0.0) { "Nominal moment Mn must be >= 0" }
        return PHI_B_FLEXURE * Mn
    }

    // =========================================================================
    // 3. الانبعاج الجانبي الملتوي (Lateral-Torsional Buckling) — AISC 360-16 Cl. F2
    // =========================================================================

    /**
     * الطول غير المدعوم المرن Lp حيث يمكن للقطاع الوصول إلى Mn = Mp.
     *
     * // AISC 360-16 Cl. F2.2, Eq. F2-5: Lp = 1.76 * ry * sqrt(E / Fy)
     */
    fun calculateLp(
        ry: Double,
        E: Double = E_MPA,
        Fy: Double
    ): Double {
        require(ry > 0.0) { "Radius of gyration ry must be > 0" }
        require(Fy > 0.0) { "Yield strength Fy must be > 0" }
        return 1.76 * ry * sqrt(E / Fy)
    }

    /**
     * الطول غير المدعوم الحاسم Lr فوقه يتحكم الانبعاج الجانبي الملتوي.
     *
     * // AISC 360-16 Cl. F2.2, Eq. F2-6 (الشكل المبسط):
     * Lr = 1.95 * rts * (E / (0.70 * Fy)) * sqrt(J * c / (Sx * ho))
     */
    fun calculateLr(
        rts: Double,
        E: Double = E_MPA,
        Fy: Double,
        J: Double,
        c: Double = 1.0,
        Sx: Double,
        ho: Double
    ): Double {
        require(rts > 0.0) { "rts must be > 0" }
        require(Fy > 0.0) { "Yield strength Fy must be > 0" }
        require(J > 0.0) { "Torsional constant J must be > 0" }
        require(Sx > 0.0) { "Elastic section modulus Sx must be > 0" }
        require(ho > 0.0) { "Distance between flange centroids ho must be > 0" }

        return 1.95 * rts * (E / (0.70 * Fy)) * sqrt(J * c / (Sx * ho))
    }

    /**
     * عامل تعديل العزم Cb للحالات الحمولية غير المتجانسة.
     *
     * // AISC 360-16 Cl. F1.1, Eq. F1-1:
     * Cb = 12.5 * Mmax / (2.5 * Mmax + 3 * MA + 4 * MB + 3 * MC)
     *限: 1.0 ≤ Cb ≤ 2.5 (للقطع ذات التماثل المزدوج، الانحناء الأحادي الاتجاه)
     */
    fun calculateCb(
        Mmax: Double,
        MA: Double,
        MB: Double,
        MC: Double,
        MD: Double
    ): Double {
        require(Mmax > 0.0) { "Maximum moment Mmax must be > 0" }

        val numerator = 12.5 * Mmax
        val denominator = 2.5 * Mmax + 3.0 * MA + 4.0 * MB + 3.0 * MC

        val cb = if (denominator > 0.0) numerator / denominator else 1.0

        return cb.coerceIn(1.0, 2.5)
    }

    // =========================================================================
    // 4. الوصلات الملولية (Bolted Connections) — AISC 360-16 Chapter J
    // =========================================================================

    /**
     * مقاومة القص التصميمي للمسمار الواحد.
     *
     * // AISC 360-16 Cl. J3.1: φFn = φ * Fn * Ab (للمسامير في القوالب القياسية)
     * حيث Ab = π * d² / 4 هي مساحة المسمار الاسمية
     */
    fun calculateBoltShearStrength(
        boltDiameterMm: Double,
        FnMpa: Double,
        phi: Double = PHI_BOLT_SHEAR_BEARING
    ): Double {
        require(boltDiameterMm > 0.0) { "Bolt diameter must be > 0" }
        require(FnMpa > 0.0) { "Bolt shear strength Fn must be > 0" }
        require(phi in 0.0..1.0) { "Resistance factor phi must be between 0 and 1" }

        val Ab = PI * boltDiameterMm * boltDiameterMm / 4.0 // mm²
        return phi * FnMpa * Ab // N
    }

    /**
     * مقاومة الحاملة التصميمي للمسمار الواحد.
     *
     * // AISC 360-16 Cl. J3.10, Eq. J3-6a: φRn = φ * 2.4 * dt * Fu * t (للقوالب القياسية)
     * حيث dt = قطر الثقب، Fu = مقاومة الشد النهائية للوحة، t = سماكة اللوحة
     */
    fun calculateBoltBearingStrength(
        holeDiameterMm: Double,
        FuMpa: Double,
        plateThicknessMm: Double,
        phi: Double = PHI_BOLT_SHEAR_BEARING
    ): Double {
        require(holeDiameterMm > 0.0) { "Hole diameter must be > 0" }
        require(FuMpa > 0.0) { "Tensile strength Fu must be > 0" }
        require(plateThicknessMm > 0.0) { "Plate thickness must be > 0" }
        require(phi in 0.0..1.0) { "Resistance factor phi must be between 0 and 1" }

        return phi * 2.4 * holeDiameterMm * FuMpa * plateThicknessMm // N
    }

    /**
     * مقاومة الاتصال المقاوم للانزلاق (Slip-critical connection).
     *
     * // AISC 360-16 Cl. J3.8: φRn = φ * μ * hf * Sc * Nb * Ns
     * حيث μ = معامل الاحتكاك، hf = عامل تعديل السماكة، Sc = مقاومة الانزلاق للمسمار الواحد
     * Nb = عدد المسامير، Ns = عدد أسطح الانزلاق
     */
    fun calculateSlipCriticalStrength(
        mu: Double,
        hf: Double,
        ScN: Double,
        numBolts: Int,
        numSlipPlanes: Int = 1,
        phi: Double = PHI_SLIP_CRITICAL
    ): Double {
        require(mu > 0.0) { "Friction coefficient mu must be > 0" }
        require(hf > 0.0) { "Thickness factor hf must be > 0" }
        require(ScN > 0.0) { "Slip-critical strength Sc must be > 0" }
        require(numBolts > 0) { "Number of bolts must be > 0" }
        require(numSlipPlanes > 0) { "Number of slip planes must be > 0" }
        require(phi in 0.0..1.0) { "Resistance factor phi must be between 0 and 1" }

        return phi * mu * hf * ScN * numBolts * numSlipPlanes // N
    }

    /**
     * التحقق من كفاية سعة المسمار للأحمال المطبقة.
     * يطرح InsufficientBoltCapacityException إذا كانت السعة التصميمية غير كافية.
     */
    fun checkBoltCapacity(
        appliedLoadN: Double,
        designCapacityN: Double,
        codeReference: String = "AISC 360-16 Chapter J"
    ) {
        require(appliedLoadN >= 0.0) { "Applied load must be >= 0" }
        require(designCapacityN > 0.0) { "Design capacity must be > 0" }

        if (appliedLoadN > designCapacityN) {
            throw InsufficientBoltCapacityException(
                appliedLoadN = appliedLoadN,
                designCapacityN = designCapacityN,
                codeRef = codeReference
            )
        }
    }

    // =========================================================================
    // 5. عامل الامتلاء (Slenderness) للأعضاء الضاغطة — AISC 360-16 Cl. E3
    // =========================================================================

    /**
     * معامل نحافة العضو الضاغط λc = (KL / r) * π * sqrt(Fy / E) / sqrt(2) ... الشكل المبسط.
     *
     * // AISC 360-16 Cl. E3, Eq. E3-2: λc = (KL / r) * π * sqrt(Fy / E) / 2  (الشكل التقريبي)
     * // الدقيق: λc = KL / (π * r) * sqrt(Fy / E)
     */
    public fun calculateCompressionSlenderness(
        KL: Double,
        r: Double,
        Fy: Double,
        E: Double = E_MPA
    ): Double {
        require(KL > 0.0) { "Effective length KL must be > 0" }
        require(r > 0.0) { "Radius of gyration r must be > 0" }
        require(Fy > 0.0) { "Yield strength Fy must be > 0" }

        return (KL / (PI * r)) * sqrt(Fy / E)
    }
}
