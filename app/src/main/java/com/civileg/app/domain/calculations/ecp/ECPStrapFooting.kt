package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.StrapFootingDesign
import com.civileg.core.engineering.StrapFootingDesignEngine

/**
 * تصميم القاعدة الشريطية حسب ECP 203-2020
 * معاملات تحميل ECP §2.3: γD = 1.4, γL = 1.6
 */
class ECPStrapFooting : StrapFootingDesign {

    companion object {
        // ECP 203-2020 §2.3: معاملات التحميل
        private const val LF_DEAD = 1.4
        private const val LF_LIVE = 1.6
    }

    override fun design(inputs: StrapFootingDesignEngine.Inputs): StrapFootingDesignEngine.Result {
        // ── InputGuard (ADR-010) — ECP 203 §2.3 ──
        InputGuard.notNull("inputs", inputs)
        InputGuard.positive("fcu", inputs.fcu)
        InputGuard.positive("fy", inputs.fy)
        InputGuard.positive("column1Load", inputs.column1Load)
        InputGuard.positive("column2Load", inputs.column2Load)
        InputGuard.positive("soilBearingCapacity", inputs.soilBearingCapacity)
        InputGuard.positive("distanceBetweenColumns", inputs.distanceBetweenColumns)
        InputGuard.nonNegative("column1Width", inputs.column1Width)
        InputGuard.nonNegative("column2Width", inputs.column2Width)

        // ── حقن معاملات تحميل ECP §2.3 ──
        val codeInputs = inputs.copy(
            deadLoadFactor = LF_DEAD,
            liveLoadFactor = LF_LIVE
        )

        val result = StrapFootingDesignEngine.design(codeInputs)

        // ── إضافة ملاحظات الكود ──
        val notes = result.codeNotes.toMutableList()
        notes.add("ECP 203-2020 §2.3: γD=${LF_DEAD}, γL=${LF_LIVE}")
        notes.add("ECP 203-2020 §7: Strap Footing Design")

        return result.copy(codeNotes = notes)
    }
}
