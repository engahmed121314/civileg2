package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم البلاطات حسب الكود السعودي SBC 304-2018
 * يعتمد على ACI 318 مع تعديلات سعودية محددة
 *
 * المراجع:
 * - SBC 304-2018 البند 8 (البلاطات)
 * - SBC 304-2018 البند 8.4 (الحد الأدنى للتسليح)
 */
class SBCSlab : SlabDesign {

    companion object {
        private const val PHI_FLEXURE = 0.90
        private const val PHI_SHEAR = 0.75
        private const val MIN_REIN_RATIO = 0.0018  // SBC 304-8.4
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

        // SBC 304 / ACI 318: f'c = 0.8 × fcu (تحويل مكعب لأسطوانة)
        val fc_prime = 0.8 * fcu
        val effectiveDepth = slabThickness - getMinCover() - 6.0
        val b = 1000.0

        // 1. طريقة Rn-ρ
        val Mu = designMoment * 1e6
        val Rn = Mu / (PHI_FLEXURE * b * effectiveDepth * effectiveDepth)

        val discriminant = 1.0 - 2.0 * Rn / (0.85 * fc_prime)
        val rho = if (discriminant > 0) {
            (0.85 * fc_prime / fy) * (1.0 - sqrt(discriminant))
        } else {
            warnings.add("SBC: Compression failure - increase depth")
            0.025
        }

        // الحد الأدنى (SBC 304-8.4 = ACI 7.6.1.1)
        val rhoMin = max(MIN_REIN_RATIO, 0.25 * sqrt(fc_prime) / fy)
        val rhoFinal = rho.coerceIn(0.0, 0.025)
        val asRequired = max(rhoFinal, rhoMin) * b * effectiveDepth

        // 2. اختيار القضبان
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0)
        val barDiameter = availableBars.firstOrNull { dia ->
            val area = PI * dia * dia / 4.0
            val spacing = area * 1000.0 / asRequired
            spacing in 100.0..450.0
        } ?: 12.0

        val barArea = PI * barDiameter * barDiameter / 4.0
        val nominalSpacing = barArea * 1000.0 / asRequired
        val maxSpacing = min(3.0 * slabThickness, 450.0, getMaxBarSpacing())
        val finalSpacing = nominalSpacing.coerceIn(100.0, maxSpacing)
        val asProvided = barArea * 1000.0 / finalSpacing

        // 3. فحص القص (SBC 304 = ACI 22.5.5.1)
        val Vc = 0.17 * sqrt(fc_prime) * b * effectiveDepth / 1000.0
        val isShearSafe = designShear <= PHI_SHEAR * Vc

        if (!isShearSafe) {
            warnings.add("SBC: Shear exceeds capacity - increase thickness")
        }

        val utilizationRatio = rhoFinal / rhoMin.coerceAtLeast(0.001)

        codeNotes.add("SBC 304-2018: One-Way Slab")
        codeNotes.add(String.format("fc'=%.0f MPa, d=%.0f mm", fc_prime, effectiveDepth))

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
        // SBC 304 يتبع ACI 318 في البلاطات ذات الاتجاهين
        val fc_prime = 0.8 * fcu
        val ly = longSpan / 1000.0
        val lx = shortSpan / 1000.0
        val ratio = ly / lx

        val shortMomentCoeff = when {
            ratio <= 1.0 -> 0.08
            ratio <= 1.5 -> 0.070
            ratio <= 2.0 -> 0.050
            else -> 0.045
        }
        val longMomentCoeff = shortMomentCoeff * (1.0 / ratio.pow(2)).coerceAtMost(1.0)

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

        val q = totalLoad
        val Mu_short = max(q * lx * lx * shortMomentCoeff, q * lx * lx * cs)
        val Mu_long = max(q * lx * lx * longMomentCoeff, q * lx * lx * cm)

        val shortRes = designOneWaySlab(fcu, fy, slabThickness, shortSpan, Mu_short, q * lx / 2.0, loadCombination)
        val longRes = designOneWaySlab(fcu, fy, slabThickness, longSpan, Mu_long, q * lx / 3.0, loadCombination)

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
        val fyFactor = min(1.0, 420.0 / fy.coerceAtLeast(200.0))
        val adjustedMin = if (fy != 420.0) minT * fyFactor else minT

        return ThicknessCheckResult(
            requiredThickness = adjustedMin,
            providedThickness = adjustedMin,
            isSafe = true,
            deflectionRatio = 0.8,
            recommendation = String.format("SBC 304 minimum thickness (adjusted for fy=%.0f)", fy)
        )
    }

    override fun getMinSlabThickness(span: Double, supportCondition: SupportCondition): Double {
        return when (supportCondition) {
            SupportCondition.SIMPLY_SUPPORTED -> span / 20.0
            SupportCondition.CONTINUOUS -> span / 28.0
            SupportCondition.CANTILEVER -> span / 8.0
        }
    }

    override fun getMinReinforcementRatio(): Double = MIN_REIN_RATIO
    override fun getMaxBarSpacing(): Double = 450.0
    override fun getMinCover(): Double = 20.0
}