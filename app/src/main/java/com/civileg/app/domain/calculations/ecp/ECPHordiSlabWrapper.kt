package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.HordiSlabDesign
import com.civileg.app.domain.entities.*

class ECPHordiSlabWrapper : HordiSlabDesign {
    private val engine = ECPHordiSlabDesign()

    override fun designHordiSlab(
        fcu: Double,
        fy: Double,
        ribWidth: Double,
        ribSpacing: Double,
        totalThickness: Double,
        toppingThickness: Double,
        span: Double,
        designMoment: Double,
        designShear: Double,
        loadCombination: LoadCombination
    ): SlabDesignResult {
        // ── InputGuard (ADR-010) — ECP 203 ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("ribWidth", ribWidth)
        InputGuard.positive("ribSpacing", ribSpacing)
        InputGuard.positive("totalThickness", totalThickness)
        InputGuard.positive("span", span)
        InputGuard.notNull("loadCombination", loadCombination)
        // Delegate to the actual ECPHordiSlabDesign engine
        // Previously returned zeros with isSafe=true — a silent wrong-answer bug
        return engine.designHordiSlab(
            fcu, fy, ribWidth, ribSpacing, totalThickness,
            toppingThickness, span, designMoment, designShear, loadCombination
        )
    }
}
