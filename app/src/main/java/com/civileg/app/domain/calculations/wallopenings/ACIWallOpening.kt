package com.civileg.app.domain.calculations.wallopenings

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.WallOpeningDesign
import com.civileg.app.domain.entities.CodeReference
import kotlin.math.ceil

/**
 * تصميم فتحات الحوائط حسب الكود الأمريكي ACI 318-19.
 *
 * يعتمد على مبدأ التعويض عن التقوية المفقودة وتوزيع الأحمال
 * حول الفتحة باستخدام معاملات ACI للجدران الحاملة.
 */
class ACIWallOpening : WallOpeningDesign {

    companion object {
        private const val ALLOWABLE_STRESS_FACTOR = 0.40 // نسبة من fcu
        private const val MIN_REBAR_RATIO = 0.0025      // نسبة التسليح الدنيا (أكثر صرامة من ECP)
    }

    override fun design(inputs: WallOpeningDesign.Inputs): WallOpeningDesign.Result {
        InputGuard.notNull("inputs", inputs)
        InputGuard.positive("wallHeightMm", inputs.wallHeightMm)
        InputGuard.positive("wallThicknessMm", inputs.wallThicknessMm)
        InputGuard.positive("openingWidthMm", inputs.openingWidthMm)
        InputGuard.positive("openingHeightMm", inputs.openingHeightMm)
        InputGuard.nonNegative("openingFromBaseMm", inputs.openingFromBaseMm)
        InputGuard.positive("wallLengthMm", inputs.wallLengthMm)
        InputGuard.positive("fcu", inputs.fcu)
        InputGuard.positive("fy", inputs.fy)
        InputGuard.positive("loadAboveOpeningKNm", inputs.loadAboveOpeningKNm)
        InputGuard.require(
            inputs.openingWidthMm < inputs.wallLengthMm,
            "Opening width must be less than wall length / يجب أن يكون عرض الفتحة أقل من طول الحائط"
        )
        InputGuard.require(
            inputs.openingFromBaseMm + inputs.openingHeightMm < inputs.wallHeightMm,
            "Opening must fit within wall height / يجب أن تقع الفتحة بالكامل داخل ارتفاع الحائط"
        )

        val warnings = mutableListOf<String>()
        val steps = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val t = inputs.wallThicknessMm
        val wo = inputs.openingWidthMm
        val ho = inputs.openingHeightMm
        val fcu = inputs.fcu
        val fy = inputs.fy

        // 1. مساحة الخرسانة المفقودة
        val lostArea = wo * ho
        steps.add("مساحة الخرسانة المفقودة = ${wo.toInt()} × ${ho.toInt()} = ${lostArea.toInt()} مم²")

        // 2. تصميم الكمر العلوي حسب ACI (معاملات شد أعلى)
        val loadOnHeader = inputs.loadAboveOpeningKNm * (wo / 1000.0)
        val headerSpan = wo / 1000.0
        val headerMu = loadOnHeader * headerSpan / 8.0
        val headerEffectiveDepth = (t - 60.0).coerceAtLeast(1.0)
        val headerAsReq = headerMu * 1e6 / (0.9 * fy * 0.9 * headerEffectiveDepth)

        val headerReinf = chooseBars(headerAsReq.coerceAtLeast(0.0), t, 12.0, 200.0)

        steps.add("عزم الكمر العلوي (ACI) ≈ ${"%.1f".format(headerMu)} ك ن.م → تسليح مطلوب ${"%.1f".format(headerAsReq)} مم²")

        // 3. العتبة السفلية
        val sillAsReq = headerAsReq * 0.6
        val sillReinf = chooseBars(sillAsReq, t, 10.0, 250.0)

        // 4. التسليح الرأسي على جانبي الفتحة
        val verticalAsReq = lostArea * MIN_REBAR_RATIO / 2.0
        val verticalReinf = chooseBars(verticalAsReq, t, 10.0, 200.0)

        // 5. التسليح حول الفتحة
        val openingAsReq = lostArea * MIN_REBAR_RATIO
        val openingReinf = chooseBars(openingAsReq, t, 12.0, 200.0)

        // 6. فحص الإجهاد
        val grossArea = inputs.wallLengthMm * inputs.wallHeightMm
        val netArea = grossArea - lostArea
        InputGuard.positive("netWallAreaMm2", netArea)
        val avgStress = (loadOnHeader * 1000.0) / netArea
        val maxStress = avgStress * 1.6 // عامل تركيز أعلى في ACI
        val allowableStress = ALLOWABLE_STRESS_FACTOR * fcu
        steps.add("Maximum stress around opening = ${"%.3f".format(maxStress)} MPa; allowable = ${"%.3f".format(allowableStress)} MPa")

        val isSafe = headerReinf.isSafe && sillReinf.isSafe &&
            verticalReinf.isSafe && openingReinf.isSafe &&
            maxStress <= allowableStress

        if (maxStress > allowableStress) {
            warnings.add("Maximum stress around opening exceeds allowable — increase wall thickness or reinforcement.")
        }

        codeNotes.add("ACI 318-19: Wall opening design — reinforcement around opening")
        codeNotes.add("CodeReference.ACI.WALL_OPENING")
        codeNotes.add("Minimum reinforcement ratio: 0.25% of lost concrete area")

        return WallOpeningDesign.Result(
            openingReinforcement = openingReinf,
            headerBeamReinforcement = headerReinf,
            sillReinforcement = sillReinf,
            verticalReinforcementEachSide = verticalReinf,
            maxStressMpa = maxStress,
            isSafe = isSafe,
            warnings = warnings,
            codeNotes = codeNotes,
            calculationSteps = steps
        )
    }

    private fun chooseBars(asReqMm2: Double, widthMm: Double, diaMm: Double, maxSpacingMm: Double): WallOpeningDesign.ReinforcementResult {
        val areaPerBar = Math.PI * diaMm * diaMm / 4.0
        var n = ceil(asReqMm2 / areaPerBar).toInt().coerceAtLeast(2)
        var effectiveSpacing = widthMm / n
        if (effectiveSpacing > maxSpacingMm) {
            effectiveSpacing = maxSpacingMm
            n = ceil(widthMm / effectiveSpacing).toInt().coerceAtLeast(2)
        }
        val provided = n * areaPerBar
        return WallOpeningDesign.ReinforcementResult(
            barDiameterMm = diaMm,
            numberOfBars = n,
            spacingMm = widthMm / n,
            areaProvidedMm2 = provided,
            areaRequiredMm2 = asReqMm2,
            isSafe = provided >= asReqMm2
        )
    }
}