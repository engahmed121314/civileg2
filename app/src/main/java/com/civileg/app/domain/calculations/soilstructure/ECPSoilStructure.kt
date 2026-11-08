package com.civileg.app.domain.calculations.soilstructure

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.SoilStructureInteraction
import com.civileg.app.domain.entities.CodeReference
import kotlin.math.sqrt

/**
 * تفاعل المنشأ مع التربة حسب الكود المصري ECP 203-2020.
 *
 * يحسب توزيع الضغوط تحت الأساس، الهبوط، ومعاملات الأمان ضد
 * الانقلاب والانزلاق وحمل التربة.
 */
class ECPSoilStructure : SoilStructureInteraction {

    companion object {
        private const val ALLOWABLE_SETTLEMENT_MM = 25.0 // ECP الحد المسموح للهبوط
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

        val L = inputs.foundationLengthMm / 1000.0 // m
        val B = inputs.foundationWidthMm / 1000.0 // m
        val A = L * B // m2
        val P = inputs.columnLoadKN // kN
        val M = kotlin.math.abs(inputs.momentKNm) // kN.m
        val qAllow = inputs.soilBearingCapacityKpa // kPa

        // 1. توزيع الضغوط تحت الأساس
        val e = if (P > 0) M / P else 0.0 // excentricity (m)
        val pAvg = P / A // kPa (متوسط الضغط)
        val sectionModulus = (B * L * L) / 6.0 // m3 (modulus around width axis)
        val deltaP = if (sectionModulus > 0) M / sectionModulus else 0.0 // kPa

        val pMax = pAvg + deltaP
        val pMin = pAvg - deltaP

        steps.add("المساحة الكلية = ${"%.2f".format(L)} × ${"%.2f".format(B)} = ${"%.2f".format(A)} م²")
        steps.add("المتوسط الضغط = $P / $A = ${"%.1f".format(pAvg)} ك ب")
        steps.add("الشذوذ e = $M / $P = ${"%.2f".format(e)} م")
        steps.add("الأقصى ضغط = ${"%.1f".format(pMax)} ك ب ، الأدنى = ${"%.1f".format(pMin)} ك ب")

        val bearingOk = pMax <= qAllow * 1.05 && pMin >= 0
        if (!bearingOk) {
            warnings.add("أقصى ضغط تحت الأساس يتجاوز السعة المسموحة — زيد أبعاد الأساس.")
        }
        if (pMin < 0) {
            warnings.add("ضغط سالب تحت جزء من الأساس — خطر انقلاب، راجع موقع الحمل.")
        }

        // 2. حساب الهبوط (تقريبي حسب ECP)
        val es = 10.0 // معامل تشوه التربة تقريبي (MPa) — يجب إدخاله من المستخدم في الإصدار الكامل
        val settlementMm = pAvg * B / es // mm; kPa / MPa × footing width
        val settlementOk = settlementMm <= ALLOWABLE_SETTLEMENT_MM
        if (!settlementOk) {
            warnings.add("الهبوط المتوقع يتجاوز الحد المسموح (25 مم) — improved soil or larger footing.")
        }

        steps.add("الهبوط التقديري = ${"%.1f".format(settlementMm)} مم (الحد المسموح 25 مم)")

        // 3. استقرار الانقلاب والانزلاق
        val resistingMoment = P * (B / 2.0) // kN.m (تبسيط)
        val overturningFS = if (M > 0) resistingMoment / M else Double.POSITIVE_INFINITY
        val slidingFS = if (M > 0) (P * 0.5) / M else Double.POSITIVE_INFINITY // تبسيط: الاحتكاك = 0.5P
        val bearingFS = qAllow / pMax

        val stabilityOk = overturningFS >= FS_OVERTAURNING_MIN &&
            slidingFS >= FS_SLIDING_MIN &&
            bearingFS >= FS_BEARING_MIN

        if (!stabilityOk) {
            warnings.add("معامل الأمان ضد الاستقرار أقل من الحد المسموح حسب ECP.")
        }

        codeNotes.add("ECP 203-2020: تفاعل المنشأ مع التربة — توزيع ضغوط under foundation")
        codeNotes.add("CodeReference.ECP.SOIL_BEARING")
        codeNotes.add("معامل الأمان الأدنى ضد الانقلاب: 2.0، ضد الانزلاق: 1.5، ضد التحمل: 3.0")

        val isSafe = bearingOk && settlementOk && stabilityOk

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
                factorOfSafetyOverturning = overturningFS,
                factorOfSafetySliding = slidingFS,
                factorOfSafetyBearing = bearingFS,
                isSafe = stabilityOk
            ),
            maxSoilPressureKpa = pMax,
            minSoilPressureKpa = pMin,
            isSafe = isSafe,
            warnings = warnings,
            codeNotes = codeNotes,
            calculationSteps = steps
        )
    }
}