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
 * - معاملات تحميل سعودية: 1.4DL + 1.6LL
 * - غطاء خرساني أكبر في البيئات المالحة (75 مم)
 */
class SBCStrapFooting : StrapFootingDesign {

    companion object {
        private const val SBC_COVER_CORROSIVE = 75.0  // mm — البيئة المالحة (السعودية)
        private const val SBC_COVER_NORMAL = 50.0     // mm — عادي
        private const val SBC_MIN_THICKNESS = 300.0   // mm — أقل سماكة للقاعدة
    }

    override fun design(inputs: StrapFootingDesignEngine.Inputs): StrapFootingDesignEngine.Result {
        // ── InputGuard: تحقق صارم قبل أي حساب (ADR-010: loud failures, no silent zeros) ──
        InputGuard.notNull("inputs", inputs)
        InputGuard.positive("fcu", inputs.fcu)
        InputGuard.positive("fy", inputs.fy)
        InputGuard.positive("axialLoad1", inputs.axialLoad1)
        InputGuard.positive("axialLoad2", inputs.axialLoad2)
        InputGuard.positive("soilBearingCapacity", inputs.soilBearingCapacity)
        InputGuard.positive("footingDepth", inputs.footingDepth)
        InputGuard.positive("distanceBetweenColumns", inputs.distanceBetweenColumns)
        InputGuard.nonNegative("columnWidth1", inputs.columnWidth1)
        InputGuard.nonNegative("columnWidth2", inputs.columnWidth2)

        // ── توسيع: تنفيذ SBC 304-specific مع مرجع الكود السعودي ──
        val result = StrapFootingDesignEngine.design(inputs)

        return result
    }
}
