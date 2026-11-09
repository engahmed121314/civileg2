package com.civileg.core.engine.rc.aci

import com.civileg.core.engine.exceptions.OverReinforcedException
import com.civileg.core.engine.exceptions.RebarCongestionException
import com.civileg.core.engine.exceptions.SectionUnsafeShearException
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * محرك الحسابات الخرسانية للكود الأمريكي ACI 318M-19.
 * Pure Kotlin — Zero Android / Zero UI dependencies.
 *
 * المراجع المعتمدة:
 * - ACI 318M-19 Chapter 22 (Sectional Strength: Flexure & Shear)
 * - ACI 318M-19 Chapter 21 (Strength Reduction Factors φ)
 */
object ACI318Engine {

    const val ES_MPA = 200_000.0 // معامل مرونة صلب التسليح Es (MPa)
    const val EPSILON_CU = 0.003 // أقصى انفعال ضغط للخرسانة عند الانهيار εcu

    // =========================================================================
    // 1. معاملات كتلة إجهادات ويتني وتخفيض المقاومة (Whitney Block & Phi)
    // =========================================================================

    /**
     * حساب معامل عمق كتلة ويتني β1 حسب ACI 318M-19 Table 22.2.2.4.3:
     * β1 = 0.85 للخرسانة حتى 28 MPa
     * ينخفض بمقدار 0.05 لكل 7 MPa فوق 28 MPa حتى حد أدنى 0.65
     */
    fun calculateBeta1(fcPrimeMpa: Double): Double {
        return when {
            fcPrimeMpa <= 28.0 -> 0.85
            fcPrimeMpa >= 55.0 -> 0.65
            else -> max(0.65, 0.85 - 0.05 * (fcPrimeMpa - 28.0) / 7.0)
        }
    }

    /**
     * حساب معامل تخفيض المقاومة φ حسب ACI 318M-19 Table 21.2.2 بناء على انفعال الشد εt:
     * إذا εt ≤ εy -> φ = 0.65 (Compression-controlled)
     * إذا εt ≥ 0.005 -> φ = 0.90 (Tension-controlled)
     * في منطقة التدرج (Transition Zone) -> استيفاء خطي
     */
    fun calculatePhiFlexure(epsilonT: Double, fyMpa: Double): Double {
        val epsilonY = fyMpa / ES_MPA
        return when {
            epsilonT <= epsilonY -> 0.65
            epsilonT >= 0.005 -> 0.90
            else -> 0.65 + 0.25 * ((epsilonT - epsilonY) / (0.005 - epsilonY))
        }
    }

    // =========================================================================
    // 2. تصميم الكمرات والانحناء (Beams Flexure) — ACI 318M-19
    // =========================================================================

    data class ACIBeamFlexureInput(
        val muKnM: Double,         // عزم الانحناء التصميمي المعامل Mu (kN.m)
        val fcPrimeMpa: Double,    // المقاومة الانضغاطية للأسطوانة الخرسانية f'c (MPa)
        val fyMpa: Double,         // إجهاد خضوع صلب التسليح fy (MPa)
        val widthMm: Double,       // عرض الكمرة bw (mm)
        val depthMm: Double,       // العمق الفعال d (mm)
        val coverMm: Double = 50.0,// الغطاء الخرساني c (mm)
        val barDiameterMm: Double = 16.0,
        val stirrupDiameterMm: Double = 10.0
    )

    data class ACIBeamFlexureResult(
        val asRequiredMm2: Double, // مساحة التسليح المطلوبة As (mm²)
        val asMinMm2: Double,      // مساحة التسليح الدنيا As,min (mm²)
        val asProvidedMm2: Double, // مساحة التسليح الفعلي المنفذ (mm²)
        val numberOfBars: Int,     // عدد الأسياخ
        val barDiameterMm: Double, // قطر الأسياخ
        val beta1: Double,         // معامل ويتني β1
        val neutralAxisDepthMm: Double, // عمق محور الخمول c (mm)
        val tensionStrainEpsilonT: Double, // انفعال صلب الشد εt
        val phiFactor: Double,     // معامل تخفيض المقاومة المحسوب φ
        val nominalMomentMnKnM: Double, // العزم الاسمي Mn (kN.m)
        val designMomentPhiMnKnM: Double, // العزم التصميمي φMn (kN.m)
        val isTensionControlled: Boolean, // هل الانهيار بالشد؟
        val clearSpacingMm: Double, // التباعد الصافي بين الأسياخ
        val isSafe: Boolean,
        val codeReference: String = "ACI 318M-19 Cl. 22.2"
    )

    /**
     * تصميم الكمرة للانحناء وفق ACI 318M-19:
     * Mu ≤ φ * Mn
     */
    fun designBeamFlexure(input: ACIBeamFlexureInput): ACIBeamFlexureResult {
        require(input.fcPrimeMpa > 0) { "f'c must be > 0" }
        require(input.fyMpa > 0) { "fy must be > 0" }
        require(input.widthMm > 0) { "width must be > 0" }
        require(input.depthMm > 0) { "depth must be > 0" }
        require(input.muKnM >= 0) { "Mu cannot be negative" }

        val b = input.widthMm
        val d = input.depthMm
        val fc = input.fcPrimeMpa
        val fy = input.fyMpa
        val muNmm = input.muKnM * 1e6

        val beta1 = calculateBeta1(fc)

        // 1. تقدير مبدئي للـ As باستخدام φ = 0.90
        val phiInitial = 0.90
        val rn = if (phiInitial * b * d * d > 0) muNmm / (phiInitial * b * d * d) else 0.0
        val m = fy / (0.85 * fc)
        val discriminant = 1.0 - (2.0 * m * rn / fy)

        if (discriminant < 0.0) {
            throw OverReinforcedException(
                muActual = rn / fy,
                muMax = 0.85 * fc / fy,
                codeReference = "ACI 318M-19 Cl. 22.2.2",
                message = String.format(
                    Locale.US,
                    "Section is grossly over-reinforced for Mu = %.1f kN.m in section %dx%d mm. Compression steel or enlarged section required.",
                    input.muKnM, b.toInt(), d.toInt()
                )
            )
        }

        val rhoReq = (1.0 / m) * (1.0 - sqrt(discriminant))
        val asCalculated = rhoReq * b * d

        // 2. الحد الأدنى للتسليح As,min حسب ACI 318M-19 Cl. 9.6.1.2
        val asMin1 = (0.25 * sqrt(fc) / fy) * b * d
        val asMin2 = (1.4 / fy) * b * d
        val asMin = max(asMin1, asMin2)
        val asRequired = max(asCalculated, asMin)

        // 3. التحقق الدقيق من الانفعال εt ومعامل φ
        val a = (asRequired * fy) / (0.85 * fc * b)
        val c = a / beta1
        val epsilonT = if (c > 0) EPSILON_CU * (d - c) / c else 0.010
        val epsilonY = fy / ES_MPA

        // التحقق من حد الليونة القصوى ACI 318M-19 Cl. 9.3.3.1 (εt ≥ 0.004 للأعضاء المعرضة للعزم)
        if (epsilonT < epsilonY) {
            throw OverReinforcedException(
                muActual = rhoReq,
                muMax = 0.005,
                codeReference = "ACI 318M-19 Cl. 9.3.3.1",
                message = String.format(
                    Locale.US,
                    "Section violates ACI ductility requirements: tension strain εt = %.5f < yield strain εy = %.5f. Section is compression-controlled and over-reinforced.",
                    epsilonT, epsilonY
                )
            )
        }

        val phi = calculatePhiFlexure(epsilonT, fy)

        // 4. تفريد الأسياخ والتحقق من التباعد الصافي
        val barArea = PI * input.barDiameterMm * input.barDiameterMm / 4.0
        val numberOfBars = max(2, ceil(asRequired / barArea).toInt())
        val asProvided = numberOfBars * barArea

        val totalCover = 2 * input.coverMm + 2 * input.stirrupDiameterMm
        val clearSpacing = if (numberOfBars > 1) {
            (b - totalCover - numberOfBars * input.barDiameterMm) / (numberOfBars - 1)
        } else {
            b - totalCover - input.barDiameterMm
        }

        val minAllowedSpacing = max(25.0, input.barDiameterMm)
        if (numberOfBars > 1 && clearSpacing < minAllowedSpacing) {
            throw RebarCongestionException(
                clearSpacingMm = clearSpacing,
                minAllowedSpacingMm = minAllowedSpacing,
                codeReference = "ACI 318M-19 Cl. 25.2.1",
                message = String.format(
                    Locale.US,
                    "Rebar clear spacing (%.1f mm) is less than ACI minimum (%.1f mm) for %dØ%.0f bars.",
                    clearSpacing, minAllowedSpacing, numberOfBars, input.barDiameterMm
                )
            )
        }

        // العزم الاسمي الفعلي بناء على AsProvided
        val aProvided = (asProvided * fy) / (0.85 * fc * b)
        val mnNmm = asProvided * fy * (d - aProvided / 2.0)
        val phiMnKnM = (phi * mnNmm) / 1e6

        return ACIBeamFlexureResult(
            asRequiredMm2 = asRequired,
            asMinMm2 = asMin,
            asProvidedMm2 = asProvided,
            numberOfBars = numberOfBars,
            barDiameterMm = input.barDiameterMm,
            beta1 = beta1,
            neutralAxisDepthMm = c,
            tensionStrainEpsilonT = epsilonT,
            phiFactor = phi,
            nominalMomentMnKnM = mnNmm / 1e6,
            designMomentPhiMnKnM = phiMnKnM,
            isTensionControlled = epsilonT >= 0.005,
            clearSpacingMm = clearSpacing,
            isSafe = phiMnKnM >= input.muKnM
        )
    }

    // =========================================================================
    // 3. قص الكمرات مع معامل حجم العنصر (Shear with Size Effect Factor λs)
    // =========================================================================

    data class ACIBeamShearInput(
        val vuKn: Double,           // قوة القص المعاملة Vu (kN)
        val fcPrimeMpa: Double,     // مقاومة الخرسانة f'c (MPa)
        val widthMm: Double,        // عرض الجذع bw (mm)
        val depthMm: Double,        // العمق الفعال d (mm)
        val lightweightLambda: Double = 1.0 // معامل الخرسانة خفيفة الوزن λ
    )

    data class ACIBeamShearResult(
        val sizeEffectFactorLambdaS: Double, // معامل حجم العنصر λs
        val concreteShearVcKn: Double,       // مقاومة الخرسانة للقص Vc (kN)
        val maxNominalShearVnMaxKn: Double,  // أقصى قوة قص يتحملها القطاع Vn,max (kN)
        val phiFactor: Double = 0.75,        // معامل تخفيض القص φ = 0.75
        val isSafe: Boolean,
        val codeReference: String = "ACI 318M-19 Table 22.5.5.1"
    )

    /**
     * حساب مقاومة القص وفق ACI 318M-19:
     * λs = √(2 / (1 + 0.004 * d)) ≤ 1.0
     * Vc = 0.17 * λ * λs * √f'c * bw * d (kN)
     * أقصى مقاومة قص: Vn,max = Vc + 0.66 * √f'c * bw * d
     * إذا Vu > φ * Vn,max -> SectionUnsafeShearException
     */
    fun checkBeamShear(input: ACIBeamShearInput): ACIBeamShearResult {
        val bw = input.widthMm
        val d = input.depthMm
        val fc = input.fcPrimeMpa
        val lambda = input.lightweightLambda
        val vuN = input.vuKn * 1000.0

        // 1. معامل حجم العنصر λs حسب ACI 318M-19 Cl. 22.5.5.1.3
        val lambdaS = min(1.0, sqrt(2.0 / (1.0 + 0.004 * d)))

        // 2. مقاومة الخرسانة للقص Vc (N)
        val vcN = 0.17 * lambda * lambdaS * sqrt(fc) * bw * d
        val vcKn = vcN / 1000.0

        // 3. أقصى مقاومة قص مسموح بها للقطاع لمنع انهيار الضغط المائل في الجذع ACI 318M-19 Cl. 22.5.1.2
        val maxVsN = 0.66 * sqrt(fc) * bw * d
        val vnMaxN = vcN + maxVsN
        val phi = 0.75 // ACI 318M-19 Table 21.2.1
        val phiVnMaxKn = (phi * vnMaxN) / 1000.0

        if (input.vuKn > phiVnMaxKn) {
            throw SectionUnsafeShearException(
                quActualMpa = vuN / (bw * d),
                quMaxMpa = (phi * vnMaxN) / (bw * d),
                codeReference = "ACI 318M-19 Cl. 22.5.1.2",
                message = String.format(
                    Locale.US,
                    "Factored shear Vu = %.1f kN exceeds maximum permissible shear capacity φVn,max = %.1f kN for section %dx%d mm. Section enlargement required.",
                    input.vuKn, phiVnMaxKn, bw.toInt(), d.toInt()
                )
            )
        }

        return ACIBeamShearResult(
            sizeEffectFactorLambdaS = lambdaS,
            concreteShearVcKn = vcKn,
            maxNominalShearVnMaxKn = vnMaxN / 1000.0,
            phiFactor = phi,
            isSafe = true
        )
    }
}
