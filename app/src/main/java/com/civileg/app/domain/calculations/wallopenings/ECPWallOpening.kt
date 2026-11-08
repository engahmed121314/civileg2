package com.civileg.app.domain.calculations.wallopenings

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.WallOpeningDesign
import com.civileg.app.domain.entities.CodeReference
import kotlin.math.ceil

/**
 * تصميم فتحات الحوائط حسب الكود المصري ECP 203-2020.
 *
 * المعتمدة على توزيع الإجهادات حول الفتحة وتصميم تسليح الكمر العلوي
 * والعتبة السفلية والتسليح الرأسي على جانبي الفتحة.
 */
class ECPWallOpening : WallOpeningDesign {

    companion object {
        private const val ALLOWABLE_STRESS_FACTOR = 0.45 // نسبة من fcu للخرسانة حول الفتحة
        private const val MIN_REBAR_RATIO = 0.002       // نسبة التسليح الدنيا
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

        // 1. مساحة الخرسانة المفقودة due to opening
        val lostArea = wo * ho // mm2
        steps.add("مساحة الخرسانة المفقودة بسبب الفتحة = ${wo.toInt()} × ${ho.toInt()} = ${lostArea.toInt()} مم²")

        // 2. تصميم تسليح الكمر العلوي (Header beam) فوق الفتحة
        // العزم تقريبي: الحمل فوق الفتحة ينتقل عبر الكمر العلوي
        val loadOnHeader = inputs.loadAboveOpeningKNm * (wo / 1000.0) // kN
        val headerSpan = wo / 1000.0 // m
        val headerMu = loadOnHeader * headerSpan / 8.0 // kN.m (تبسيط: كمر simplemente مدعوم)
        val headerEffectiveDepth = (t - 60.0).coerceAtLeast(1.0)
        val headerAsReq = headerMu * 1e6 / ((fy / 1.15) * 0.9 * headerEffectiveDepth)

        val headerReinf = chooseBars(headerAsReq.coerceAtLeast(0.0), t, 12.0, 200.0)

        steps.add("عزم الكمر العلوي فوق الفتحة ≈ ${"%.1f".format(headerMu)} ك ن.م → تسليح مطلوب ${"%.1f".format(headerAsReq)} مم²")

        // 3. تصميم العتبة السفلية (Sill)
        val sillAsReq = headerAsReq * 0.6 // تبسيط: 60% من تسليح الكمر العلوي
        val sillReinf = chooseBars(sillAsReq, t, 10.0, 250.0)

        // 4. التسليح الرأسي على جانبي الفتحة
        val verticalAsReq = lostArea * MIN_REBAR_RATIO / 2.0 // على كل جانب
        val verticalReinf = chooseBars(verticalAsReq, t, 10.0, 200.0)

        // 5. التسليح العام حول الفتحة
        val openingAsReq = lostArea * MIN_REBAR_RATIO
        val openingReinf = chooseBars(openingAsReq, t, 12.0, 200.0)

        // 6. فحص الإجهاد الأقصى
        val grossArea = inputs.wallLengthMm * inputs.wallHeightMm
        val netArea = grossArea - lostArea
        InputGuard.positive("netWallAreaMm2", netArea)
        val avgStress = (loadOnHeader * 1000.0) / netArea // N/mm2 = MPa
        val maxStress = avgStress * 1.5 // عامل تركيز الإجهاد حول الفتحة
        val allowableStress = ALLOWABLE_STRESS_FACTOR * fcu
        steps.add("الإجهاد الأقصى حول الفتحة = ${"%.3f".format(maxStress)} MPa (Maximum stress; المسموح ${"%.3f".format(allowableStress)} MPa)")

        val isSafe = headerReinf.isSafe && sillReinf.isSafe &&
            verticalReinf.isSafe && openingReinf.isSafe &&
            maxStress <= allowableStress

        if (maxStress > allowableStress) {
            warnings.add("الإجهاد الأقصى حول الفتحة يتجاوز المسموح — زيد سماكة الحائط أو حجم التسليح.")
        }

        codeNotes.add("ECP 203-2020: تصميم فتحة حائط خرساني مسلح")
        codeNotes.add("CodeReference.ECP.WALL_OPENING")
        codeNotes.add("تسليح حول الفتحة: حد أدنى 0.2% من مساحة الخرسانة المفقودة")

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
        val spacing = widthMm / n
        val effectiveSpacing = if (spacing > maxSpacingMm) maxSpacingMm else spacing
        n = ceil(widthMm / effectiveSpacing).toInt().coerceAtLeast(2)
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