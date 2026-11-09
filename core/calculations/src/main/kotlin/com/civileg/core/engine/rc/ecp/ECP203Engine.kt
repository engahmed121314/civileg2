package com.civileg.core.engine.rc.ecp

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
 * محرك الحسابات الخرسانية للكود المصري لتصميم وتنفيذ المنشآت الخرسانية ECP 203.
 * Pure Kotlin — Zero Android / Zero UI dependencies.
 *
 * المراجع المعتمدة:
 * - ECP 203-2020 الباب الرابع (تصميم القطاعات الخرسانية بطريقة حالات الحدود Ultimate Limit State)
 * - ECP 203-2020 الباب السادس (الأعمدة النحيفة Slender Columns والترخيم)
 */
object ECP203Engine {

    // ECP 203 Cl. 3-2-1: معاملات تخفيض المقاومة لحالات الحدود القصوى
    const val GAMMA_C = 1.50   // معامل أمان الخرسانة في الانحناء والضغط
    const val GAMMA_S = 1.15   // معامل أمان صلب التسليح

    // =========================================================================
    // 1. الكمرات والانحناء (Beams Flexure) — ECP 203 Cl. 4-2-1
    // =========================================================================

    data class BeamFlexureInput(
        val muKnM: Double,         // عزم الانحناء التصميمي (kN.m)
        val fcuMpa: Double,        // المقاومة المميزة للخرسانة مكعبات (MPa)
        val fyMpa: Double,         // إجهاد خضوع صلب التسليح (MPa)
        val widthMm: Double,       // عرض الكمرة b (mm)
        val depthMm: Double,       // العمق الفعال d (mm)
        val coverMm: Double = 50.0,// الغطاء الخرساني c (mm)
        val barDiameterMm: Double = 16.0, // قطر السيخ المقترح (mm)
        val stirrupDiameterMm: Double = 8.0 // قطر الكانة (mm)
    )

    data class BeamFlexureResult(
        val ru: Double,            // معامل العزم التصميمي Ru
        val muRatio: Double,       // نسبة التسليح المحسوبة μ
        val asRequiredMm2: Double, // مساحة حديد الشد المطلوبة (mm²)
        val asMinMm2: Double,      // الحد الأدنى لحديد التسليح (mm²)
        val asProvidedMm2: Double, // مساحة حديد التسليح الفعلي المنفذ (mm²)
        val numberOfBars: Int,     // عدد الأسياخ
        val barDiameterMm: Double, // قطر الأسياخ
        val clearSpacingMm: Double,// التباعد الصافي بين الأسياخ (mm)
        val leverArmMm: Double,    // ذراع العزم الداخلي z (mm)
        val isSafe: Boolean,
        val codeReference: String = "ECP 203 Cl. 4-2-1"
    )

    /**
     * حساب عزم الانحناء وتسليح الشد للكمرات المصمتة المفردة:
     * Ru = Mu / (fcu * b * d²)
     * μ = (1 / γs) * (1 - √(1 - 2*Ru*γs / γc))
     * As = μ * b * d
     * As,min = max(1.1/fy * b * d, 1.3 * As,req)
     */
    fun designBeamFlexure(input: BeamFlexureInput): BeamFlexureResult {
        require(input.fcuMpa > 0) { "fcu must be > 0" }
        require(input.fyMpa > 0) { "fy must be > 0" }
        require(input.widthMm > 0) { "width must be > 0" }
        require(input.depthMm > 0) { "depth must be > 0" }
        require(input.muKnM >= 0) { "Mu cannot be negative" }

        val b = input.widthMm
        val d = input.depthMm
        val fcu = input.fcuMpa
        val fy = input.fyMpa
        val muNmm = input.muKnM * 1e6

        // 1. حساب معامل العزم Ru
        val ru = if (fcu * b * d * d > 0) muNmm / (fcu * b * d * d) else 0.0

        // 2. التحقق من حد القصوى للعزم ومقاومة القطاع
        // ECP 203 Cl. 4-2-1-2: R_max بناء على نوع الصلب
        val ruMax = when {
            fy >= 420 -> 0.194
            fy >= 360 -> 0.214
            else -> 0.240
        }

        val discriminant = 1.0 - (2.0 * ru * GAMMA_S / GAMMA_C)
        if (discriminant < 0.0 || ru > ruMax) {
            val muActual = if (b * d > 0) muNmm / (fy * 0.8 * d) / (b * d) else 0.0
            throw OverReinforcedException(
                muActual = muActual,
                muMax = ruMax,
                codeReference = "ECP 203 Cl. 4-2-1-2",
                message = String.format(
                    Locale.US,
                    "Beam section is over-reinforced (Ru=%.4f > Ru_max=%.4f). Enlarge cross-section (%dx%d mm) or design as doubly-reinforced section.",
                    ru, ruMax, b.toInt(), d.toInt()
                )
            )
        }

        // 3. نسبة التسليح المحسوبة μ
        val muRatio = (1.0 / GAMMA_S) * (1.0 - sqrt(discriminant))
        val asCalculated = muRatio * (fcu / fy) * b * d

        // 4. الحد الأدنى للتسليح As,min حسب ECP 203 Cl. 4-2-1-2
        val asMin1 = (1.1 / fy) * b * d
        val asMin2 = 1.3 * asCalculated
        val asMin3 = (0.15 / 100.0) * b * d
        val asMin = max(asMin1, min(asMin2, asMin3))

        val asRequired = max(asCalculated, asMin)

        // 5. تفريد الأسياخ والتحقق من التباعد الصافي (Rebar Congestion)
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
                codeReference = "ECP 203 Cl. 4-2-5-1",
                message = String.format(
                    Locale.US,
                    "Rebar spacing too small (%.1f mm < %.1f mm required) for %dØ%.0f bars in width %.0f mm. Arrange in multiple layers.",
                    clearSpacing, minAllowedSpacing, numberOfBars, input.barDiameterMm, b
                )
            )
        }

        val leverArm = d * (0.5 + 0.5 * sqrt(discriminant))

        return BeamFlexureResult(
            ru = ru,
            muRatio = muRatio,
            asRequiredMm2 = asRequired,
            asMinMm2 = asMin,
            asProvidedMm2 = asProvided,
            numberOfBars = numberOfBars,
            barDiameterMm = input.barDiameterMm,
            clearSpacingMm = clearSpacing,
            leverArmMm = leverArm,
            isSafe = true
        )
    }

    // =========================================================================
    // 2. قص الكمرات (Beams Shear) — ECP 203 Cl. 4-2-3
    // =========================================================================

    data class BeamShearInput(
        val quKn: Double,           // قوى القص التصميمية القصوى (kN)
        val fcuMpa: Double,         // المقاومة المميزة للخرسانة (MPa)
        val widthMm: Double,        // عرض الجذع b (mm)
        val depthMm: Double         // العمق الفعال d (mm)
    )

    data class BeamShearResult(
        val quMpa: Double,          // إجهاد القص الأقصى المسلط (MPa)
        val qcuMpa: Double,         // إجهاد القص الآمن للخرسانة غير المشرخة (MPa)
        val quMaxMpa: Double,       // الحد الأقصى المسموح به لإجهاد القص (MPa)
        val isConcreteSafeAlone: Boolean, // هل الخرسانة تكفي بمفردها؟
        val isSafe: Boolean,
        val codeReference: String = "ECP 203 Cl. 4-2-3"
    )

    /**
     * فحص إجهاد القص الأقصى:
     * qu = Qu / (b * d)
     * qu_max = 0.70 * √(fcu / γc) ≤ 4.0 MPa
     * إذا qu > qu_max -> SectionUnsafeShearException
     */
    fun checkBeamShear(input: BeamShearInput): BeamShearResult {
        val b = input.widthMm
        val d = input.depthMm
        val fcu = input.fcuMpa
        val quN = input.quKn * 1000.0

        val quMpa = quN / (b * d)
        val qcuMpa = 0.24 * sqrt(fcu / GAMMA_C) // ECP 203 Cl. 4-2-3-1
        val quMaxMpa = min(4.0, 0.70 * sqrt(fcu / GAMMA_C)) // ECP 203 Cl. 4-2-3-2

        if (quMpa > quMaxMpa) {
            throw SectionUnsafeShearException(
                quActualMpa = quMpa,
                quMaxMpa = quMaxMpa,
                codeReference = "ECP 203 Cl. 4-2-3-2",
                message = String.format(
                    Locale.US,
                    "Nominal shear stress qu = %.2f MPa exceeds maximum limit qu_max = %.2f MPa for section %dx%d mm (fcu=%.1f MPa). Web crushing hazard, increase cross-section.",
                    quMpa, quMaxMpa, b.toInt(), d.toInt(), fcu
                )
            )
        }

        return BeamShearResult(
            quMpa = quMpa,
            qcuMpa = qcuMpa,
            quMaxMpa = quMaxMpa,
            isConcreteSafeAlone = quMpa <= qcuMpa,
            isSafe = true
        )
    }

    // =========================================================================
    // 3. الأعمدة القصيرة (Short Columns) — ECP 203 Cl. 4-2-4
    // =========================================================================

    data class ShortColumnInput(
        val acMm2: Double,          // المساحة الكلية لقطاع العمود Ac (mm²)
        val astMm2: Double,         // مساحة حديد التسليح الطولي Ast (mm²)
        val fcuMpa: Double,         // المقاومة المميزة للخرسانة (MPa)
        val fyMpa: Double           // إجهاد خضوع حديد التسليح (MPa)
    )

    /**
     * حساب الحمل المحوري الأقصى للأعمدة القصيرة المقيدة بالكانات (Tied Columns):
     * Pu,max = 0.35 * fcu * (Ac - Ast) + 0.67 * fy * Ast (ECP 203 Cl. 4-2-4-1)
     */
    fun calculateShortColumnCapacityKn(input: ShortColumnInput): Double {
        require(input.acMm2 > input.astMm2) { "Ac must be > Ast" }
        require(input.fcuMpa > 0) { "fcu must be > 0" }
        require(input.fyMpa > 0) { "fy must be > 0" }

        val puN = 0.35 * input.fcuMpa * (input.acMm2 - input.astMm2) + 0.67 * input.fyMpa * input.astMm2
        return puN / 1000.0 // تحويل إلى kN
    }

    // =========================================================================
    // 4. الأعمدة النحيفة (Slender Columns) — ECP 203 Cl. 6-4
    // =========================================================================

    data class SlenderColumnInput(
        val puKn: Double,           // الحمل المحوري المطبق Pu (kN)
        val muKnM: Double,          // العزم المطبق الأولي Mu (kN.m)
        val bMm: Double,            // بُعد العمود في اتجاه الانبعاج (mm)
        val clearHeightMm: Double,  // الارتفاع الصافي الحر للعمود Lin (mm)
        val effectiveLengthFactorK: Double = 1.0, // معامل الطول الفعال k
        val isBraced: Boolean = true // مقيد ضد الإزاحة الجانبية أم لا
    )

    data class SlenderColumnResult(
        val slendernessRatioLambda: Double, // معامل النحافة λ
        val isSlender: Boolean,             // هل العمود نحيف؟
        val additionalMomentKnM: Double,    // العزم الإضافي الناتج عن الانبعاج Madd (kN.m)
        val totalDesignMomentKnM: Double,   // العزم النهائي للتصميم Mtotal (kN.m)
        val deflectionMm: Double,           // قيمة الإزاحة الإضافية δ (mm)
        val codeReference: String = "ECP 203 Cl. 6-4"
    )

    /**
     * حساب العزوم الإضافية للأعمدة النحيفة:
     * λ = (k * Lin) / b
     * إذا λ > 15 في المقيد أو > 10 في غير المقيد:
     * δ = (λ² * b) / 2000 (mm)
     * Madd = Pu * δ (kN.m)
     */
    fun analyzeSlenderColumn(input: SlenderColumnInput): SlenderColumnResult {
        require(input.bMm > 0) { "Column dimension b must be > 0" }
        require(input.clearHeightMm > 0) { "Clear height must be > 0" }

        val lin = input.clearHeightMm
        val b = input.bMm
        val lambda = (input.effectiveLengthFactorK * lin) / b

        val lambdaLimit = if (input.isBraced) 15.0 else 10.0
        val isSlender = lambda > lambdaLimit

        val deltaMm = if (isSlender) (lambda * lambda * b) / 2000.0 else 0.0
        // Madd = Pu (kN) * delta (m) = Pu * deltaMm / 1000
        val maddKnM = if (isSlender) input.puKn * (deltaMm / 1000.0) else 0.0
        val totalMoment = input.muKnM + maddKnM

        return SlenderColumnResult(
            slendernessRatioLambda = lambda,
            isSlender = isSlender,
            additionalMomentKnM = maddKnM,
            totalDesignMomentKnM = totalMoment,
            deflectionMm = deltaMm
        )
    }
}
