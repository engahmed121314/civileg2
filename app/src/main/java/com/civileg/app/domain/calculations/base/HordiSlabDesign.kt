package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.entities.*
import com.civileg.app.domain.entities.BarBondClass

/**
 * Interface for Hordi/Ribbed/Joist Slab design.
 */
interface HordiSlabDesign {
    companion object {
        const val DEFAULT_COVER_MM = 25.0
        const val DEFAULT_LINK_DIA_MM = 8.0
        const val DEFAULT_MAIN_BAR_DIA_MM = 12.0
    }

    fun designHordiSlab(
        fcu: Double,
        fy: Double,
        ribWidth: Double,           // mm
        ribSpacing: Double,         // mm (Center to center)
        totalThickness: Double,     // mm
        toppingThickness: Double,   // mm
        span: Double,               // mm
        designMoment: Double,       // kN.m/rib
        designShear: Double,        // kN/rib
        loadCombination: LoadCombination,
        coverMm: Double = DEFAULT_COVER_MM,     // concrete cover to link outside, mm
        linkDiaMm: Double = DEFAULT_LINK_DIA_MM,    // link/stirrup diameter, mm
        mainBarDiaMm: Double = DEFAULT_MAIN_BAR_DIA_MM, // main bar diameter, mm
        barBondClass: BarBondClass = BarBondClass.UNKNOWN
    ): SlabDesignResult
}
