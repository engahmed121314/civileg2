package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.CodeReference
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*

/**
 * تنفيذ التصميم الزلزالي حسب الكود السعودي SBC 301-2020 — مستقل بالكامل عن ACI
 *
 * فروقات SBC 301 عن ASCE 7-16:
 * - خريطة المخاطر الزلزالية السعودية مختلفة تماماً (مناطق تبوك/جيزان/خليج العقبة)
 * - معاملات المناطق الزلزالية حسب SBC 301 (أقل من ASCE 7 لمعظم المناطق)
 * - معاملات الموقع Fa/Fv حسب SBC 301 Table 4-1 (مختلفة عن ASCE 7)
 * - شكل طيف الاستجابة SBC 301 §4.3 (مختلف في النقاط المميزة T0, Ts)
 * - أقطار تسارع الذروة الصخرية SPS حسب المنطقة السعودية
 * - الدور الذاتي T ≈ 0.1N للمنشآت الخرسانية (تقريب سعودي)
 *
 * المراجع:
 * - SBC 301-2020 البند 4 (التصميم الزلزالي)
 * - SBC 301-2020 البند 4.3 (طيف الاستجابة)
 * - SBC 301-2020 الجدول 4-1 (معاملات الموقع)
 * - SBC 301-2020 خريطة المخاطر الزلزالية للمملكة
 */
class SBCSeismic : SeismicDesign {

    companion object {
        // ── SBC 301-2020 خريطة المخاطر الزلزالية السعودية ──
        // مناطق زلزالية مختلفة عن ASCE 7 — أقل خطر في معظم المناطق
        private val SAUDI_ZONE_FACTORS = mapOf(
            SeismicZone.ZONE_1 to 0.05,   // تبوك/جيزان — منخفضة جداً
            SeismicZone.ZONE_2 to 0.10,   // الرياض/الدمام — منخفضة
            SeismicZone.ZONE_3 to 0.15,   // جدة/مكة — متوسطة
            SeismicZone.ZONE_4 to 0.25,   // خليج العقبة — عالية
            SeismicZone.ZONE_5 to 0.35    // المناطق الزلزالية النشطة — عالية جداً
        )

        // ── SBC 301-2020 الجدول 4-1: معاملات الموقع Fa (فترة قصيرة) ──
        // مختلفة عن ASCE 7 — السعودية لها ظروف جيولوجية فريدة
        private val SBC_SOIL_FACTORS_FA = mapOf(
            SoilType.A to 0.8,   // صخرة صلبة
            SoilType.B to 1.0,   // صخرة
            SoilType.C to 1.2,   // تربة كثيفة
            SoilType.D to 1.6,   // تربة متوسطة
            SoilType.E to 2.5    // تربة لينة
        )

        // ── SBC 301-2020 الجدول 4-2: معاملات الموقع Fv (فترة 1 ثانية) ──
        private val SBC_SOIL_FACTORS_FV = mapOf(
            SoilType.A to 0.8,
            SoilType.B to 1.0,
            SoilType.C to 1.5,
            SoilType.D to 2.4,
            SoilType.E to 3.5
        )

        // ── SBC 301-2020 حدود طيف الاستجابة ──
        private const val DEFAULT_SPS = 0.10   // g — تسارع الذروة الصخرية الافتراضي
        private const val SBC_TL = 4.0         // ثانية — فترة انتقال طويلة

        // ── SBC 301-2020 معامل تخميد مرجعي ──
        private const val REFERENCE_DAMPING = 0.05  // 5%
    }

    override fun calculateBaseShear(
        totalWeight: Double,
        seismicZone: SeismicZone,
        soilType: SoilType,
        importanceFactor: Double,
        responseModificationFactor: Double,
        buildingHeight: Double
    ): SeismicBaseShearResult {
        // ── InputGuard: تحقق صارم (ADR-010) — SBC 301 §4 ──
        InputGuard.positive("totalWeight", totalWeight)
        InputGuard.notNull("seismicZone", seismicZone)
        InputGuard.notNull("soilType", soilType)
        InputGuard.positive("importanceFactor", importanceFactor)
        InputGuard.positive("responseModificationFactor", responseModificationFactor)
        InputGuard.positive("buildingHeight", buildingHeight)

        val warnings = mutableListOf<String>()

        // ── SBC 301 §4.1: معامل المنطقة الزلزالية ──
        val zoneFactor = SAUDI_ZONE_FACTORS[seismicZone] ?: 0.10

        // ── SBC 301 §4.2: معاملات الموقع ──
        val Fa = SBC_SOIL_FACTORS_FA[soilType] ?: 1.0
        val Fv = SBC_SOIL_FACTORS_FV[soilType] ?: 1.0

        // ── SBC 301 §4.3: معاملات الطيف التصميمي ──
        // SPS = تسارع الذروة الصخرية = zoneFactor
        // SDS = Fa × SPS (فترة قصيرة)
        // SD1 = Fv × SPS (فترة 1 ثانية)
        val SPS = zoneFactor
        val SDS = Fa * SPS
        val SD1 = Fv * SPS * 0.5  // SBC 301: SD1 = Fv × S1 حيث S1 ≈ 0.5 × SPS

        // ── SBC 301 §4.4: حساب Cs ──
        // Cs = SDS / (R / Ie)
        var cs = SDS / (responseModificationFactor / importanceFactor)

        // ── SBC 301 §4.4.1: حدود Cs ──
        // Cs,max = SD1 / (T × (R / Ie)) لـ T ≤ TL
        val T = estimatePeriod(buildingHeight)
        val csMax = if (T > 0) SD1 / (T * (responseModificationFactor / importanceFactor)) else cs * 2

        // Cs,min = max(0.044 × SDS × Ie, 0.01)
        val csMin = max(0.044 * SDS * importanceFactor, 0.01)

        // لـ SDS ≥ 0.6: Cs,min = 0.5 × S1 / (R / Ie)
        val csMinHighSeismic = if (SDS >= 0.6) {
            0.5 * (SD1) / (responseModificationFactor / importanceFactor)
        } else csMin

        val effectiveCsMin = max(csMin, csMinHighSeismic)
        // SBC 301 §4.4.1: When csMax < csMin (e.g. low seismic zones with long periods),
        // the code minimum governs — use effectiveCsMin rather than throwing.
        val finalCs = if (csMax >= effectiveCsMin) {
            cs.coerceIn(effectiveCsMin, csMax)
        } else {
            effectiveCsMin
        }

        if (cs < effectiveCsMin) warnings.add("SBC 301 §4.4.1: Cs زِيد للحد الأدنى")
        if (cs > csMax) warnings.add("SBC 301 §4.4.1: Cs خُفِّض للحد الأقصى")

        val baseShear = finalCs * totalWeight

        return SeismicBaseShearResult(
            baseShear = baseShear,
            zoneFactor = SPS,
            soilFactor = Fa,
            importanceFactor = importanceFactor,
            responseModification = responseModificationFactor,
            calculationFormula = "SBC 301: V = Cs × W where Cs = SDS / (R/Ie)",
            codeReference = CodeReference.SBC.SEISMIC_BASE_SHEAR,
            warnings = warnings
        )
    }

    override fun getResponseSpectrum(
        period: Double,
        dampingRatio: Double,
        soilType: SoilType,
        peakGroundAcceleration: Double,
        importanceFactor: Double
    ): SpectrumValue {
        // ── InputGuard ──
        InputGuard.positive("period", period)
        InputGuard.positive("dampingRatio", dampingRatio)
        InputGuard.notNull("soilType", soilType)
        InputGuard.positive("peakGroundAcceleration", peakGroundAcceleration)
        InputGuard.positive("importanceFactor", importanceFactor)

        // ── SBC 301 §4.3: طيف الاستجابة التصميمي ──
        val Fa = SBC_SOIL_FACTORS_FA[soilType] ?: 1.0
        val Fv = SBC_SOIL_FACTORS_FV[soilType] ?: 1.0

        // حساب SDS و SD1
        val SPS = if (peakGroundAcceleration > 0) peakGroundAcceleration else DEFAULT_SPS
        val SDS = Fa * SPS
        val SD1 = Fv * SPS * 0.5

        // ── SBC 301 §4.3.1: نقاط الطيف المميزة ──
        // T0 = 0.2 × SD1 / SDS
        // Ts = SD1 / SDS
        val T0 = 0.2 * SD1 / SDS.coerceAtLeast(0.001)
        val Ts = SD1 / SDS.coerceAtLeast(0.001)

        // ── SBC 301 §4.3.2: حساب Sa ──
        // للفترات المختلفة:
        // T < T0: Sa = SDS × (0.4 + 0.6 × T/T0)
        // T0 ≤ T ≤ Ts: Sa = SDS
        // Ts < T ≤ TL: Sa = SD1 / T
        // T > TL: Sa = SD1 × TL / T²
        val sa = when {
            period < T0 -> SDS * (0.4 + 0.6 * period / T0.coerceAtLeast(0.001))
            period <= Ts -> SDS
            period <= SBC_TL -> SD1 / period
            else -> SD1 * SBC_TL / (period * period)
        }

        // ── معامل التخميد — SBC 301 §4.3.4 ──
        // للمنشآت ذات تخميد ≠ 5%: ضرب في عامل تعديل
        val dampingFactor = if (dampingRatio != REFERENCE_DAMPING && dampingRatio > 0) {
            sqrt(2.0 / (1.0 + 3.0 * dampingRatio / REFERENCE_DAMPING))
        } else 1.0

        val saAdjusted = sa * dampingFactor

        val desc = if (peakGroundAcceleration > 0) {
            "SBC 301 طيف الاستجابة [SPS=%.3fg, Fa=%.1f, Fv=%.1f, %s]".format(
                SPS, Fa, Fv, soilType.displayName)
        } else {
            "SBC 301 طيف الاستجابة [SPS=%.3fg (افتراضي), %s]".format(SPS, soilType.displayName)
        }

        return SpectrumValue(
            spectralAcceleration = saAdjusted,
            period = period,
            dampingRatio = dampingRatio,
            description = desc
        )
    }

    override fun distributeSeismicForces(
        baseShear: Double,
        floorWeights: List<Double>,
        floorHeights: List<Double>
    ): List<SeismicForceDistribution> {
        // ── InputGuard ──
        InputGuard.positive("baseShear", baseShear)
        InputGuard.notEmpty("floorWeights", floorWeights)
        InputGuard.notEmpty("floorHeights", floorHeights)

        val n = floorWeights.size
        if (n == 0) return emptyList()

        // ── SBC 301 §4.5: توزيع القوى الزلزالية ──
        // Fx = Cvx × V
        // Cvx = (Wx × hx^k) / Σ(Wi × hi^k)
        // k = 1 لـ T ≤ 0.5s, k = 2 لـ T > 0.5s
        val k = 1.0  // تقريب للمنشآت القصيرة

        val whSum = floorWeights.zip(floorHeights).sumOf { (w, h) -> w * h.pow(k) }

        val results = mutableListOf<SeismicForceDistribution>()
        var storyShear = baseShear
        var accumulatedOTM = 0.0

        for (i in n - 1 downTo 0) {
            val force = if (whSum > 0) {
                (floorWeights[i] * floorHeights[i].pow(k) / whSum) * baseShear
            } else 0.0

            val storyHeight = if (i > 0) floorHeights[i] - floorHeights[i - 1] else floorHeights[i]
            accumulatedOTM += storyShear * storyHeight

            results.add(
                SeismicForceDistribution(
                    floorIndex = i,
                    floorWeight = floorWeights[i],
                    floorHeight = floorHeights[i],
                    lateralForce = force,
                    storyShear = storyShear,
                    overturningMoment = accumulatedOTM
                )
            )
            storyShear -= force
        }

        return results.reversed()
    }

    // ══════════════════════════════════════════════════════════════════════
    // دوال مساعدة
    // ══════════════════════════════════════════════════════════════════════

    /**
     * تقدير الدور الذاتي — SBC 301 §4.4.2
     * T ≈ 0.1 × N (عدد الأدوار) للمنشآت الخرسانية
     * T ≈ 0.08 × H^0.75 للمباني العامة
     */
    private fun estimatePeriod(buildingHeight: Double): Double {
        if (buildingHeight <= 0) {
            // SBC 301: لا يمكن تقدير الدور لارتفاع غير صالح — نُبلغ بالخطأ بدل قيمة افتراضية خاطئة
            throw IllegalArgumentException("buildingHeight must be positive for period estimation (SBC 301 §4.4.2), got: $buildingHeight")
        }
        // SBC 301 تقريب: T = 0.08 × H^0.75 (H بالأمتار)
        return 0.08 * buildingHeight.pow(0.75)
    }

    override fun getCodeName(): DesignCode = DesignCode.SBC
    override fun getSeismicZones(): List<SeismicZone> = SeismicZone.entries.toList()
    override fun getZoneFactors(): Map<SeismicZone, Double> = SAUDI_ZONE_FACTORS
    override fun getSoilFactors(): Map<SoilType, Double> = SBC_SOIL_FACTORS_FA
}
