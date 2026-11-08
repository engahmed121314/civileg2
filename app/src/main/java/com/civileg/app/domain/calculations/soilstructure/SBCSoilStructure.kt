package com.civileg.app.domain.calculations.soilstructure

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.SoilStructureInteraction
import com.civileg.app.domain.entities.CodeReference
import kotlin.math.sqrt

/**
 * تفاعل المنشأ مع التربة حسب الكود السعودي SBC 304-2018.
 *
 * يحسب توزيع الضغوط، الهبوط، والاستقرار مع مراعاة
 * ظروف التربة في المملكة العربية السعودية.
 */
class SBCSoilStructure : SoilStructureInteraction {

    companion object {
        private const val ALLOWABLE_SETTLEMENT_MM = 25.0
        private const val FS_OVERTAURNING_MIN = 2.0
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

        steps.add("مساحة الأساس = ${"%.2f".format(L)} × ${"%.2f".format(B)} = ${"%.2f".format(A)} م²")
        steps.add("متوسط الضغط = ${"%.1f".format(pAvg)} ك ب")
        steps.add("الشذوذ e = ${"%.2f".format(e)} م")
        steps.add("أقصى ضغط = ${"%.1f".format(pMax)} ك ب ، أقل ضغط = ${"%.1f".format(pMin)} ك ب")

        val bearingOk = pMax <= qAllow * 1.05 && pMin >= 0
        if (!bearingOk) warnings.add("أقصى ضغط تحت الأساس يتجاوز السعة المسموحة حسب SBC.")
        if (pMin < 0) warnings.add("ضغط سالب تحت جزء من الأساس — خطر انقلاب.")

        // Settlement (SBC)
        val es = 10.0
        val settlementMm = pAvg * B / es
        val settlementOk = settlementMm <= ALLOWABLE_SETTLEMENT_MM
        if (!settlementOk) warnings.add("الهبوط يتجاوز الحد المسموح (25 مم) حسب SBC.")

        steps.add("الهبوط التقديري = ${"%.1f".format(settlementMm)} مم (الحد المسموح 25 مم)")

        // Stability
        val resistingMoment = P * (B / 2.0)
        val otFS = if (M > 0) resistingMoment / M else Double.POSITIVE_INFINITY
        val slidingFS = if (M > 0) (P * 0.5) / M else Double.POSITIVE_INFINITY
        val bearingFS = qAllow / pMax

        val stabilityOk = otFS >= FS_OVERTAURNING_MIN && slidingFS >= FS_SLIDING_MIN && bearingFS >= FS_BEARING_MIN
        if (!stabilityOk) warnings.add("معاملات الأمان ضد الاستقرار أقل من الحد المسموح حسب SBC.")

        codeNotes.add("SBC 304-2018: تفاعل المنشأ مع التربة — توزيع ضغوط تحت الأساس")
        codeNotes.add("CodeReference.SBC.SOIL_BEARING")
        codeNotes.add("معامل الأمان الأدنى: الانقلاب 2.0، الانزلاق 1.5، التحمل 3.0")

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