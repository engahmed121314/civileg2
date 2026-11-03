package com.civileg.app.domain.calculations.settlement

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * مدخلات تحليل الترخيم.
 *
 * الوحدات:
 * - footingWidth / footingLength / foundationDepth / layerThickness / drainagePath: m
 * - appliedPressure / initialEffectiveStress / preconsolidationPressure / stressIncrease: kPa
 * - elasticModulus: MPa
 * - coefficientOfConsolidation: m²/year
 * - timeYears: year
 * - allowableSettlement: mm
 *
 * appliedPressure هو الضغط الصافي عند منسوب التأسيس (net pressure).
 */
data class SettlementInput(
    val footingWidth: Double,
    val footingLength: Double,
    val appliedPressure: Double,
    val elasticModulus: Double,
    val foundationDepth: Double = 0.0,
    val poissonRatio: Double = 0.30,
    val influenceFactor: Double = 1.0,
    val layerThickness: Double = 0.0,
    val compressionIndex: Double = 0.0,
    val recompressionIndex: Double = 0.0,
    val initialVoidRatio: Double = 0.0,
    val initialEffectiveStress: Double = 0.0,
    val preconsolidationPressure: Double = 0.0,
    val stressIncrease: Double = 0.0,
    val coefficientOfConsolidation: Double = 0.0,
    val drainagePath: Double = 0.0,
    val timeYears: Double = 1.0,
    val allowableSettlement: Double = 25.0
)

data class SettlementCheck(
    val name: String,
    val calculated: Double,
    val limit: Double,
    val unit: String,
    val passed: Boolean
)

data class SettlementResult(
    val immediateSettlement: Double,
    val consolidationSettlement: Double,
    val totalSettlement: Double,
    val stressIncrease: Double,
    val degreeOfConsolidation: Double,
    val settlementAtTime: Double,
    val timeFor90Consolidation: Double,
    val allowableSettlement: Double,
    val isConsolidationApplicable: Boolean,
    val isSafe: Boolean,
    val checks: List<SettlementCheck>,
    val warnings: List<String>,
    val notes: List<String>
)

/**
 * تحليل الترخيم:
 * - ترخيم مرن فوري (Elastic / Schleicher).
 * - ترخيم الانضغاط أحادي البعد (Terzaghi) للطين المشبع.
 * - معدل الزمن بنظرية Consolidation (Tv, U, t90).
 * - مقارنة الترخيم الكلي بالحد المسموح.
 */
class SettlementAnalysis {

    companion object {
        /** درجة التصلب المتوسطة U من عامل الزمن Tv. */
        fun degreeOfConsolidation(tv: Double): Double {
            if (tv <= 0.0) return 0.0
            return if (tv <= 0.2) {
                sqrt(4.0 * tv / PI).coerceIn(0.0, 1.0)
            } else {
                (1.0 - (8.0 / (PI * PI)) * exp(-PI * PI * tv / 4.0)).coerceIn(0.0, 1.0)
            }
        }

        /** عامل الزمن Tv = cv·t/Hd². */
        fun timeFactor(coefficientOfConsolidation: Double, timeYears: Double, drainagePath: Double): Double {
            if (coefficientOfConsolidation <= 0.0 || drainagePath <= 0.0 || timeYears < 0.0) return 0.0
            return coefficientOfConsolidation * timeYears / (drainagePath * drainagePath)
        }
    }

    fun analyze(input: SettlementInput): SettlementResult {
        validate(input)

        // ── Immediate elastic settlement ────────────────────────────────
        // Se [mm] = q [kPa] · B [m] · (1 − ν²) · Is / Es [MPa]
        val immediateSettlement = input.appliedPressure * input.footingWidth *
            (1.0 - input.poissonRatio * input.poissonRatio) *
            input.influenceFactor / input.elasticModulus

        // ── Stress increase at the mid-height of the compressible layer ──
        // layerThickness is measured below the foundation base, so the 2:1
        // spread is evaluated at z = H/2 below the base.
        val midDepth = input.layerThickness / 2.0
        val stressIncrease = when {
            input.stressIncrease > 0.0 -> input.stressIncrease
            input.footingWidth > 0.0 && input.footingLength > 0.0 -> {
                // 2:1 stress distribution method.
                val top = input.footingWidth * input.footingLength
                val bottom = (input.footingWidth + midDepth) * (input.footingLength + midDepth)
                if (bottom > 0.0) input.appliedPressure * top / bottom else 0.0
            }
            else -> 0.0
        }

        val e0 = input.initialVoidRatio
        val sigma0 = input.initialEffectiveStress
        val sigmaF = sigma0 + stressIncrease
        val consolidationApplicable = input.layerThickness > 0.0 &&
            input.compressionIndex > 0.0 &&
            e0 > 0.0 &&
            sigma0 > 0.0 &&
            sigmaF > sigma0

        val consolidationSettlement = if (!consolidationApplicable) {
            0.0
        } else {
            val hMm = input.layerThickness * 1000.0
            val cc = input.compressionIndex
            val cr = if (input.recompressionIndex > 0.0) {
                input.recompressionIndex
            } else {
                0.10 * cc
            }
            val sigmaP = input.preconsolidationPressure
            val overConsolidated = sigmaP > sigma0

            if (overConsolidated && sigmaF <= sigmaP) {
                // Entire loading remains within the recompression range.
                (cr / (1.0 + e0)) * hMm * log10(sigmaF / sigma0)
            } else if (overConsolidated) {
                // Recompression up to σ'p, then virgin compression beyond it.
                val recompression = (cr / (1.0 + e0)) * hMm * log10(sigmaP / sigma0)
                val virgin = (cc / (1.0 + e0)) * hMm * log10(sigmaF / sigmaP)
                recompression + virgin
            } else {
                // Normally consolidated clay.
                (cc / (1.0 + e0)) * hMm * log10(sigmaF / sigma0)
            }
        }

        val totalSettlement = immediateSettlement + consolidationSettlement

        // ── Time rate ──────────────────────────────────────────────────
        val tv = timeFactor(
            coefficientOfConsolidation = input.coefficientOfConsolidation,
            timeYears = input.timeYears,
            drainagePath = input.drainagePath
        )
        val degreeU = degreeOfConsolidation(tv)
        val settlementAtTime = immediateSettlement + consolidationSettlement * degreeU
        val timeFor90 = if (input.coefficientOfConsolidation > 0.0 && input.drainagePath > 0.0) {
            0.848 * input.drainagePath * input.drainagePath / input.coefficientOfConsolidation
        } else {
            0.0
        }

        val checks = listOf(
            SettlementCheck(
                "Total settlement",
                totalSettlement,
                input.allowableSettlement,
                "mm",
                totalSettlement <= input.allowableSettlement
            ),
            SettlementCheck(
                "Immediate settlement",
                immediateSettlement,
                input.allowableSettlement,
                "mm",
                immediateSettlement <= input.allowableSettlement
            )
        )

        val warnings = mutableListOf<String>()
        if (totalSettlement > input.allowableSettlement) {
            warnings.add("Total settlement exceeds the allowable limit")
        }
        if (input.layerThickness > 0.0 && !consolidationApplicable) {
            warnings.add("Consolidation data are incomplete — only immediate settlement is computed")
        }
        if (input.coefficientOfConsolidation > 0.0 && input.drainagePath <= 0.0) {
            warnings.add("Drainage path is required for the time-rate calculation")
        }

        val notes = mutableListOf<String>()
        notes.add("Immediate settlement Se = ${"%.2f".format(immediateSettlement)} mm")
        notes.add("Stress increase at mid-layer Δσ = ${"%.2f".format(stressIncrease)} kPa")
        notes.add("Consolidation settlement Sc = ${"%.2f".format(consolidationSettlement)} mm")
        notes.add("Total settlement = ${"%.2f".format(totalSettlement)} mm")
        notes.add("Time factor Tv = ${"%.4f".format(tv)}, U = ${"%.3f".format(degreeU)}")
        notes.add("Time for 90% consolidation = ${"%.2f".format(timeFor90)} years")

        return SettlementResult(
            immediateSettlement = immediateSettlement,
            consolidationSettlement = consolidationSettlement,
            totalSettlement = totalSettlement,
            stressIncrease = stressIncrease,
            degreeOfConsolidation = degreeU,
            settlementAtTime = settlementAtTime,
            timeFor90Consolidation = timeFor90,
            allowableSettlement = input.allowableSettlement,
            isConsolidationApplicable = consolidationApplicable,
            isSafe = totalSettlement <= input.allowableSettlement,
            checks = checks,
            warnings = warnings,
            notes = notes
        )
    }

    private fun validate(input: SettlementInput) {
        require(input.footingWidth > 0.0) { "footingWidth must be positive / عرض القاعدة موجب" }
        require(input.footingLength > 0.0) { "footingLength must be positive / طول القاعدة موجب" }
        require(input.appliedPressure >= 0.0) { "appliedPressure must be non-negative / الضغط غير سالب" }
        require(input.elasticModulus > 0.0) { "elasticModulus must be positive / معامل المرونة موجب" }
        require(input.foundationDepth >= 0.0) { "foundationDepth must be non-negative / منسوب التأسيس غير سالب" }
        require(input.poissonRatio in 0.0..0.5) { "poissonRatio must be between 0 and 0.5 / نسبة بواسون غير صالحة" }
        require(input.influenceFactor > 0.0) { "influenceFactor must be positive / معامل التأثير موجب" }
        require(input.layerThickness >= 0.0) { "layerThickness must be non-negative / سماكة الطبقة غير سالبة" }
        require(input.compressionIndex >= 0.0 && input.recompressionIndex >= 0.0) {
            "compression indices must be non-negative / معاملات الانضغاط غير سالبة"
        }
        require(input.initialVoidRatio >= 0.0) { "initialVoidRatio must be non-negative / نسبة الفراغات غير سالبة" }
        require(input.initialEffectiveStress >= 0.0) { "initialEffectiveStress must be non-negative / الإجهاد الفعال غير سالب" }
        require(input.preconsolidationPressure >= 0.0) { "preconsolidationPressure must be non-negative / ضغط ما قبل التصلب غير سالب" }
        require(input.stressIncrease >= 0.0) { "stressIncrease must be non-negative / زيادة الإجهاد غير سالبة" }
        require(input.coefficientOfConsolidation >= 0.0) { "coefficientOfConsolidation must be non-negative / معامل التصلب غير سالب" }
        require(input.drainagePath >= 0.0) { "drainagePath must be non-negative / مسار الصرف غير سالب" }
        require(input.timeYears >= 0.0) { "timeYears must be non-negative / الزمن غير سالب" }
        require(input.allowableSettlement > 0.0) { "allowableSettlement must be positive / الترخيم المسموح موجب" }
        listOf(
            input.footingWidth, input.footingLength, input.appliedPressure,
            input.elasticModulus, input.foundationDepth, input.poissonRatio,
            input.influenceFactor, input.layerThickness, input.compressionIndex,
            input.recompressionIndex, input.initialVoidRatio,
            input.initialEffectiveStress, input.preconsolidationPressure,
            input.stressIncrease, input.coefficientOfConsolidation,
            input.drainagePath, input.timeYears, input.allowableSettlement
        ).forEach {
            require(it.isFinite()) { "inputs must be finite / المدخلات يجب أن تكون منتهية" }
        }
    }
}
