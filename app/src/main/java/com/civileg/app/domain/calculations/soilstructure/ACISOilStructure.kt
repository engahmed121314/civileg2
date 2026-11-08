package com.civileg.app.domain.calculations.soilstructure

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.SoilStructureInteraction

/**
 * تفاعل المنشأ مع التربة حسب الكود الأمريكي ACI 318-19 / ASCE 7-16.
 *
 * يحسب توزيع الضغوط، الهبوط، والاستقرار باستخدام معاملات ACI.
 */
class ACISOilStructure : SoilStructureInteraction {

    companion object {
        private const val ALLOWABLE_SETTLEMENT_MM = 19.0 // ACI/ASCE stricter settlement limit
        private const val FS_OVERTAURNING_MIN = 1.5
        private const val FS_SLIDING_MIN = 1.5
        private const val FS_BEARING_MIN = 3.0
    }

    override fun design(inputs: SoilStructureInteraction.Inputs): SoilStructureInteraction.Result {
        InputGuard.notNull("inputs", inputs)
        InputGuard.positive("foundationLengthMm", inputs.foundationLengthMm)
        InputGuard.positive("foundationWidthMm", inputs.foundationWidthMm)
        InputGuard.positive("foundationThicknessMm", inputs.foundationThicknessMm)
        InputGuard.positive("columnLoadKN", inputs.columnLoadKN)
        InputGuard.positive("soilBearingCapacityKpa", inputs.soilBearingCapacityKpa)
        InputGuard.positive("fcu", inputs.fcu)
        InputGuard.positive("fy", inputs.fy)
        InputGuard.positive("embedmentDepthMm", inputs.embedmentDepthMm)
        InputGuard.finite("momentKNm", inputs.momentKNm)

        val warnings = mutableListOf<String>()
        val steps = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val L = inputs.foundationLengthMm / 1000.0
        val B = inputs.foundationWidthMm / 1000.0
        val A = L * B
        val P = inputs.columnLoadKN
        val M = kotlin.math.abs(inputs.momentKNm)
        val qAllow = inputs.soilBearingCapacityKpa

        val e = if (P > 0) M / P else 0.0
        val pAvg = P / A
        val sectionModulus = (B * L * L) / 6.0
        val deltaP = if (sectionModulus > 0) M / sectionModulus else 0.0

        val pMax = pAvg + deltaP
        val pMin = pAvg - deltaP

        steps.add("Foundation area = ${"%.2f".format(L)} × ${"%.2f".format(B)} = ${"%.2f".format(A)} ft²-equivalent m²")
        steps.add("Average pressure = ${"%.1f".format(pAvg)} kPa")
        steps.add("Eccentricity e = ${"%.2f".format(e)} m")
        steps.add("Maximum pressure = ${"%.1f".format(pMax)} kPa, Minimum = ${"%.1f".format(pMin)} kPa")

        val bearingOk = pMax <= qAllow * 1.0 && pMin >= 0
        if (!bearingOk) warnings.add("Maximum soil pressure exceeds ACI allowable bearing.")

        // Settlement (ACI stricter)
        val es = 12.0
        val settlementMm = pAvg * B / es
        val settlementOk = settlementMm <= ALLOWABLE_SETTLEMENT_MM
        if (!settlementOk) warnings.add("Estimated settlement exceeds ACI limit (19 mm).")

        steps.add("Estimated settlement = ${"%.1f".format(settlementMm)} mm (ACI limit 19 mm)")

        // Stability
        val resistingMoment = P * (B / 2.0)
        val otFS = if (M > 0) resistingMoment / M else Double.POSITIVE_INFINITY
        val slidingFS = if (M > 0) (P * 0.4) / M else Double.POSITIVE_INFINITY
        val bearingFS = qAllow / pMax

        val stabilityOk = otFS >= FS_OVERTAURNING_MIN && slidingFS >= FS_SLIDING_MIN && bearingFS >= FS_BEARING_MIN
        if (!stabilityOk) warnings.add("ACI stability factors below minimum.")

        codeNotes.add("ACI 318-19 / ASCE 7-16: Soil-structure interaction")
        codeNotes.add("CodeReference.ACI.SOIL_BEARING")
        codeNotes.add("Allowable settlement: 19 mm; FS overturning ≥ 1.5, sliding ≥ 1.5, bearing ≥ 3.0")

        return SoilStructureInteraction.Result(
            bearing = SoilStructureInteraction.BearingResult(
                maxPressureKpa = pMax,
                minPressureKpa = pMin,
                averagePressureKpa = pAvg,
                allowablePressureKpa = qAllow,
                isSafe = bearingOk
            ),
            settlement = SoilStructureInteraction.SettlementResult(
                totalSettlementMm = settlementMm,
                allowableSettlementMm = ALLOWABLE_SETTLEMENT_MM,
                isSafe = settlementOk
            ),
            stability = SoilStructureInteraction.StabilityResult(
                factorOfSafetyOverturning = otFS,
                factorOfSafetySliding = slidingFS,
                factorOfSafetyBearing = bearingFS,
                isSafe = stabilityOk
            ),
            maxSoilPressureKpa = pMax,
            minSoilPressureKpa = pMin,
            isSafe = bearingOk && settlementOk && stabilityOk,
            warnings = warnings,
            codeNotes = codeNotes,
            calculationSteps = steps
        )
    }
}