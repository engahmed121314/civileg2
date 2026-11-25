package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.entities.*

/**
 * Interface for Hordi/Ribbed/Joist Slab design.
 */
interface HordiSlabDesign {
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
        coverMm: Double = 25.0,     // concrete cover to link outside, mm
        linkDiaMm: Double = 8.0,    // link/stirrup diameter, mm
        mainBarDiaMm: Double = 12.0 // main bar diameter, mm
    ): SlabDesignResult
}
