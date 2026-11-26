package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.StrapFootingDesign
import com.civileg.app.domain.entities.CodeReference
import com.civileg.core.engineering.StrapFootingDesignEngine
import kotlin.math.max
import kotlin.math.sqrt

/**
 * تصميم القاعدة الشريطية حسب ECP 203
 * معاملات تحميل ECP §2.3: γD = 1.4, γL = 1.6
 */
class ECPStrapFooting : StrapFootingDesign {

    companion object {
        // ECP 203 §2.3: معاملات التحميل
        private const val LF_DEAD = 1.4
        private const val LF_LIVE = 1.6
        private const val GAMMA_S = 1.15
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
            liveLoadFactor = LF_LIVE,
            // ECP 203-2025 Eq 4-46 punching (edge column alpha 3, interior 4), K-method flexure and Eq 4-30 concrete
            // shear share (cracked, with stirrups) instead of the ACI-style 0.33*sqrt(fcu), phi/Rn and 0.17*sqrt(fcu).
            punchingQcup = { fcu, d, b0, colB, colT, edge ->
                ECP2025ConcreteShear.punchingQcup(
                    fcu, d, b0, colB, colT,
                    if (edge) ECP2025ConcreteShear.ColumnPosition.EDGE else ECP2025ConcreteShear.ColumnPosition.INTERIOR
                )
            },
            flexureAs = { mu, b, d, fcu, fy ->
                val k = mu * 1e6 / (fcu * b * d * d)
                if (k > ECP2025Flexure.kLimit(fy)) Double.NaN  // Eq 4-4 / Table 4-1
                else mu * 1e6 / (fy / GAMMA_S * ECP2025Flexure.leverArm(k, d))
            },
            beamQcu = 0.12 * sqrt(inputs.fcu / ECP2025ConcreteShear.GAMMA_C)
        )

        val result = StrapFootingDesignEngine.design(codeInputs)

        // ── إضافة ملاحظات الكود ──
        val notes = result.codeNotes.toMutableList()
        notes.add("ECP 203 §2.3: γD=${LF_DEAD}, γL=${LF_LIVE}")
        notes.add("ECP 203 §7: Strap Footing Design")
        notes.add(CodeReference.ECP.FOOTING_BEARING)
        notes.add(CodeReference.ECP.FOOTING_REINFORCEMENT)

        return result.copy(codeNotes = notes)
    }
}
