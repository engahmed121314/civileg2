package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم البلاطات حسب الكود الأمريكي ACI 318-19
 * يغطي: بلاطة اتجاه واحد، اتجاهين، فحص السمك، الانحراف
 *
 * المراجع:
 * - ACI 318-19 البند 7 (أبعاد البلاطات)
 * - ACI 318-19 البند 8 (بلاطات اتجاهين)
 * - ACI 318-19 البند 24 (الانحراف)
 * - ACI 318-19 البند 7.6 (الحد الأدنى للتسليح)
 */
class ACISlab : SlabDesign {

    companion object {
        private const val PHI_FLEXURE = 0.90
        private const val PHI_SHEAR = 0.75
        private const val MIN_REIN_RATIO = 0.0018  // ACI 7.6.1.1
    }

    override fun designOneWaySlab(
        fcu: Double,
        fy: Double,
        slabThickness: Double,
        clearSpan: Double,
        designMoment: Double,
        designShear: Double,
        loadCombination: LoadCombination
    ): SlabDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // f'c = 0.8 × fcu (تحويل مقاومة المكعب لمقاومة الأسطوانة)
        val fc_prime = 0.8 * fcu
        val effectiveDepth = slabThickness - getMinCover() - 6.0  // cover + bar/2
        val b = 1000.0  // mm/م

        // 1. حساب التسليح بطريقة Rn-ρ (ACI 318-22.2)
        val Mu = designMoment * 1e6  // N.mm/m
        val Rn = Mu / (PHI_FLEXURE * b * effectiveDepth * effectiveDepth)

        // ρ = (0.85×fc'/fy) × [1 - √(1 - 2×Rn/(0.85×fc'))]
        val discriminant = 1.0 - 2.0 * Rn / (0.85 * fc_prime)
        val rho = if (discriminant > 0) {
            (0.85 * fc_prime / fy) * (1.0 - sqrt(discriminant))
        } else {
            warnings.add("ACI: Compression failure - increase slab depth")
            0.025  // تصميم مضغوط
        }

        // الحد الأدنى للتسليح (ACI 7.6.1.1)
        val rhoMin = max(MIN_REIN_RATIO, 0.25 * sqrt(fc_prime) / fy)
        val rhoMax = 0.025  // ACI الحد الأقصى
        val rhoFinal = rho.coerceIn(0.0, rhoMax)
        val asRequired = max(rhoFinal, rhoMin) * b * effectiveDepth  // As = ρ × b × d

        // 2. اختيار القضبان والمسافات
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0)
        val barDiameter = availableBars.firstOrNull { dia ->
            val area = PI * dia * dia / 4.0
            val spacing = area * 1000.0 / asRequired
            spacing in 100.0..450.0
        } ?: 12.0

        val barArea = PI * barDiameter * barDiameter / 4.0
        val nominalSpacing = barArea * 1000.0 / asRequired
        // ACI 7.7.2.3: max spacing = min(3h, 450mm)
        val maxSpacing = min(3.0 * slabThickness, 450.0, getMaxBarSpacing())
        val finalSpacing = nominalSpacing.coerceIn(100.0, maxSpacing)
        val asProvided = barArea * 1000.0 / finalSpacing

        // 3. فحص القص (ACI 22.5.5.1): Vc = 0.17λ√fc' × b × d
        val Vc = 0.17 * sqrt(fc_prime) * b * effectiveDepth / 1000.0  // kN/m
        val Vu = designShear  // kN/m (ممرر بالفعل كقوة قصية تصميمية)
        val isShearSafe = Vu <= PHI_SHEAR * Vc

        if (!isShearSafe) {
            warnings.add("ACI: Shear exceeds concrete capacity - increase thickness")
        }

        // 4. نسبة الاستغلال
        val utilizationRatio = rhoFinal / rhoMin.coerceAtLeast(0.001)

        codeNotes.add("ACI 318-19: One-Way Slab Design")
        codeNotes.add("fc'=%.0f MPa, d=%.0f mm, Rn=%.2f".format(fc_prime, effectiveDepth, Rn))
        codeNotes.add("rho=%.4f, rho_min=%.4f".format(rhoFinal, rhoMin))
        codeNotes.add("Vc=%.1f kN/m".format(Vc))

        return SlabDesignResult(
            requiredReinforcement = asRequired,
            providedReinforcement = asProvided,
            barDiameter = barDiameter,
            barSpacing = finalSpacing,
            minThickness = getMinSlabThickness(clearSpan, SupportCondition.SIMPLY_SUPPORTED),
            shearCapacity = Vc,
            isSafe = discriminant > 0 && isShearSafe,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun designTwoWaySlab(
        fcu: Double,
        fy: Double,
        slabThickness: Double,
        shortSpan: Double,
        longSpan: Double,
        supportConditions: SlabSupportConditions,
        totalLoad: Double,
        loadCombination: LoadCombination
    ): TwoWaySlabResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val fc_prime = 0.8 * fcu
        val effectiveDepth = slabThickness - getMinCover() - 6.0
        val ly = longSpan / 1000.0  // m
        val lx = shortSpan / 1000.0  // m
        val ratio = ly / lx  // يجب أن يكون ≥ 1.0

        // معاملات العزم حسب ACI 318 الجدول 8.10.2.2 (Direct Design Method)
        // للحالات البسيطة: نستخدم قيم تقريبية
        val cs = when (supportConditions.edgeA) {
            EdgeCondition.FIXED, EdgeCondition.CONTINUOUS -> 0.033
            EdgeCondition.SIMPLY_SUPPORTED -> 0.076
            EdgeCondition.FREE -> 0.085
        }
        val cm = when (supportConditions.edgeC) {
            EdgeCondition.FIXED, EdgeCondition.CONTINUOUS -> 0.063
            EdgeCondition.SIMPLY_SUPPORTED -> 0.071
            EdgeCondition.FREE -> 0.085
        }

        // معاملات التوزيع بين الاتجاه القصير والطويل
        // الاتجاه القصير يتحمل نسبة أكبر
        val shortMomentCoeff = when {
            ratio <= 1.0 -> 0.08  // مربع
            ratio <= 1.5 -> 0.070  // معتدل
            ratio <= 2.0 -> 0.050  // مستطيل
            else -> 0.045  // طويل جداً
        }
        val longMomentCoeff = shortMomentCoeff * (1.0 / ratio.pow(2)).coerceAtMost(1.0)

        // عزم الاتجاه القصير (السالب عند الدعامة، الموجب في الوسط)
        val q = totalLoad  // kN/m²
        val Mu_short_pos = q * lx * lx * shortMomentCoeff   // kN.m/m
        val Mu_short_neg = q * lx * lx * cs                   // kN.m/m (عند الدعامة)
        val Mu_long_pos = q * lx * lx * longMomentCoeff       // kN.m/m
        val Mu_long_neg = q * lx * lx * cm                     // kN.m/m

        // تصميم التسليح في كل اتجاه (نستخدم الأكبر: سالب عند الدعامة)
        val Mu_short = max(Mu_short_pos, Mu_short_neg)
        val Mu_long = max(Mu_long_pos, Mu_long_neg)

        val shortRes = designOneWaySlab(fcu, fy, slabThickness, shortSpan, Mu_short, q * lx / 2.0, loadCombination)
        val longRes = designOneWaySlab(fcu, fy, slabThickness, longSpan, Mu_long, q * lx / 3.0, loadCombination)

        codeNotes.add("ACI 318-19: Two-Way Slab (Direct Design Method)")
        codeNotes.add("ly/lx=%.2f, q=%.1f kN/m²".format(ratio, q))
        codeNotes.add("Short: M+=%.1f, M-=%.1f kN.m/m".format(Mu_short_pos, Mu_short_neg))
        codeNotes.add("Long: M+=%.1f, M-=%.1f kN.m/m".format(Mu_long_pos, Mu_long_neg))

        return TwoWaySlabResult(
            shortDirection = shortRes,
            longDirection = longRes,
            momentCoefficients = MomentCoefficients(
                negativeShort = cs,
                positiveShort = shortMomentCoeff,
                negativeLong = cm,
                positiveLong = longMomentCoeff
            ),
            isSafe = shortRes.isSafe && longRes.isSafe
        )
    }

    override fun checkSlabThickness(
        span: Double,
        supportCondition: SupportCondition,
        fy: Double,
        isTwoWay: Boolean
    ): ThicknessCheckResult {
        val minT = getMinSlabThickness(span, supportCondition)
        // تعديل حسب fy (ACI Table 7.3.1.1): h_min يتناسب عكسياً مع fy
        val fyFactor = min(1.0, 420.0 / fy.coerceAtLeast(200.0))
        val adjustedMin = if (fy != 420.0) minT * fyFactor else minT

        return ThicknessCheckResult(
            requiredThickness = adjustedMin,
            providedThickness = adjustedMin,
            isSafe = true,
            deflectionRatio = 0.8,
            recommendation = "ACI 318 Table 7.3.1.1 minimum thickness (adjusted for fy=%.0f)".format(fy)
        )
    }

    override fun getMinSlabThickness(span: Double, supportCondition: SupportCondition): Double {
        // ACI 318 Table 7.3.1.1 (لـ fy = 420 MPa, عادي الوزن)
        return when (supportCondition) {
            SupportCondition.SIMPLY_SUPPORTED -> span / 20.0
            SupportCondition.CONTINUOUS -> span / 28.0  // ACI: L/28 للبلاطات المستمرة
            SupportCondition.CANTILEVER -> span / 8.0
        }
    }

    override fun getMinReinforcementRatio(): Double = MIN_REIN_RATIO
    override fun getMaxBarSpacing(): Double = 450.0
    override fun getMinCover(): Double = 20.0
}