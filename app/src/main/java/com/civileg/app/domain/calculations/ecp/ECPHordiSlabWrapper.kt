package com.civileg.app.domain.calculations.ecp

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
        // Delegate to the actual ECPHordiSlabDesign engine
        // Previously returned zeros with isSafe=true — a silent wrong-answer bug
        return engine.designHordiSlab(
            fcu, fy, ribWidth, ribSpacing, totalThickness,
            toppingThickness, span, designMoment, designShear, loadCombination
        )
    }
}
