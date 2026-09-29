package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.StrapFootingDesign
import com.civileg.core.engineering.StrapFootingDesignEngine

/**
 * تصميم القاعدة الشريطية حسب ACI 318-19
 * معاملات تحميل ACI §9.2: γD = 1.2, γL = 1.6
 */
class ACIStrapFooting : StrapFootingDesign {

    companion object {
        // ACI 318-19 §9.2: معاملات التحميل
        private const val LF_DEAD = 1.2
        private const val LF_LIVE = 1.6
    }

    override fun design(inputs: StrapFootingDesignEngine.Inputs): StrapFootingDesignEngine.Result {
        // ── InputGuard: loud failures, no silent zeros (ADR-010) ──
        InputGuard.notNull("inputs", inputs)
        InputGuard.positive("fcu", inputs.fcu)
        InputGuard.positive("fy", inputs.fy)
        InputGuard.positive("column1Load", inputs.column1Load)
        InputGuard.positive("column2Load", inputs.column2Load)
        InputGuard.positive("soilBearingCapacity", inputs.soilBearingCapacity)
        InputGuard.positive("distanceBetweenColumns", inputs.distanceBetweenColumns)
        InputGuard.nonNegative("column1Width", inputs.column1Width)
        InputGuard.nonNegative("column2Width", inputs.column2Width)

        // ── حقن معاملات تحميل ACI §9.2 ──
        val codeInputs = inputs.copy(
            deadLoadFactor = LF_DEAD,
            liveLoadFactor = LF_LIVE
        )

        val result = StrapFootingDesignEngine.design(codeInputs)

        // ── إضافة ملاحظات الكود ──
        val notes = result.codeNotes.toMutableList()
        notes.add("ACI 318-19 §9.2: γD=${LF_DEAD}, γL=${LF_LIVE}")
        notes.add("ACI 318-19 §13: Strap Footing Design")

        return result.copy(codeNotes = notes)
    }
}
