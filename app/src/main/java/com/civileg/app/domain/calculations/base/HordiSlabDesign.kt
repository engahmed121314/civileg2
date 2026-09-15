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
        loadCombination: LoadCombination
    ): SlabDesignResult
}
