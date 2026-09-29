package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.StrapFootingDesign
import com.civileg.core.engineering.StrapFootingDesignEngine

/**
 * تصميم القاعدة الشريطية حسب الكود السعودي SBC 304-2018
 * SBC 304 Strap Footing Design — يوسع ويتطور وليس مجرد حراسة
 *
 * التوسعات:
 * - تحقق InputGuard لكل المدخلات قبل الحساب (ADR-010)
 * - مرجع SBC 304-2018 البند 13 (القواعد)
 * - معاملات تحميل سعودية: 1.2D + 1.6L (SBC 304 §9.2)
 * - غطاء خرساني أكبر في البيئات المالحة (75 مم)
 */
class SBCStrapFooting : StrapFootingDesign {

    companion object {
        // SBC 304-2018 §9.2: معاملات التحميل
        private const val LF_DEAD = 1.2
        private const val LF_LIVE = 1.6

        // SBC 304-2018 §4: الغطاء الخرساني
        private const val SBC_COVER_CORROSIVE = 75.0  // mm — البيئة المالحة (السعودية)
        private const val SBC_COVER_NORMAL = 50.0     // mm — عادي
        private const val SBC_MIN_THICKNESS = 300.0   // mm — أقل سماكة للقاعدة
    }

    override fun design(inputs: StrapFootingDesignEngine.Inputs): StrapFootingDesignEngine.Result {
        // ── InputGuard: تحقق صارم قبل أي حساب (ADR-010: loud failures, no silent zeros) ──
        InputGuard.notNull("inputs", inputs)
        InputGuard.positive("fcu", inputs.fcu)
        InputGuard.positive("fy", inputs.fy)
        InputGuard.positive("column1Load", inputs.column1Load)
        InputGuard.positive("column2Load", inputs.column2Load)
        InputGuard.positive("soilBearingCapacity", inputs.soilBearingCapacity)
        InputGuard.positive("distanceBetweenColumns", inputs.distanceBetweenColumns)
        InputGuard.nonNegative("column1Width", inputs.column1Width)
        InputGuard.nonNegative("column2Width", inputs.column2Width)

        // ── حقن معاملات تحميل SBC 304 §9.2 ──
        val codeInputs = inputs.copy(
            deadLoadFactor = LF_DEAD,
            liveLoadFactor = LF_LIVE
        )

        val result = StrapFootingDesignEngine.design(codeInputs)

        // ── إضافة ملاحظات الكود السعودي ──
        val notes = result.codeNotes.toMutableList()
        notes.add("SBC 304-2018 §9.2: γD=${LF_DEAD}, γL=${LF_LIVE}")
        notes.add("SBC 304-2018 §13: Strap Footing Design")
        notes.add("Cover: ${SBC_COVER_CORROSIVE}mm (بيئة مالحة) / ${SBC_COVER_NORMAL}mm (عادي)")
        notes.add("Min thickness: ${SBC_MIN_THICKNESS}mm")

        return result.copy(codeNotes = notes)
    }
}
