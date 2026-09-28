package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.RebarResult
import com.civileg.app.domain.SafetyCheckItem
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.ShearReinforcementResult

/**
 * Interface for Waffle Slab design per any design code.
 *
 * Waffle slabs are two-way ribbed slabs with ribs in two directions.
 * Design includes:
 *  - Rib design (flexure, shear)
 *  - Solid head design (column heads)
 *  - Topping/slab design
 *  - Punching shear at column heads
 *  - Deflection check
 */
interface WaffleSlabDesign {

    /**
     * Full waffle slab design — main entry point.
     */
    fun design(input: WaffleSlabInput): WaffleSlabResult

    /**
     * Design rib reinforcement (flexure + shear)
     */
    fun designRib(
        fcu: Double,
        fy: Double,
        ribWidth: Double,
        ribHeight: Double,
        ribSpacing: Double,
        clearSpan: Double,
        totalLoad: Double,
        loadCombination: LoadCombination
    ): RibDesignResult

    /**
     * Design solid head (column head)
     */
    fun designSolidHead(
        fcu: Double,
        fy: Double,
        solidHeadSize: Double,
        ribWidth: Double,
        columnSize: Double,
        axialLoad: Double,
        moment: Double
    ): SolidHeadDesignResult

    /**
     * Punching shear check at solid head
     */
    fun checkPunchingShear(
        fcu: Double,
        fy: Double,
        solidHeadSize: Double,
        columnSize: Double,
        axialLoad: Double,
        shear: Double
    ): PunchingShearCheck

    /**
     * Deflection check for waffle slab
     */
    fun checkDeflection(
        span: Double,
        totalDepth: Double,
        ribWidth: Double,
        ribHeight: Double,
        fcu: Double,
        fy: Double,
        providedAs: Double,
        loadCombination: LoadCombination
    ): FlatSlabDesign.DeflectionResult

    // ═══════════════════════════════════════════════════════════════════════════════════
    // RESULT DATA CLASSES
    // ════════════════════════════════════════════════════════════════════════════════════

    data class WaffleSlabResult(
        val isSafe: Boolean,
        val utilizationRatio: Double,
        val warnings: List<String> = emptyList(),
        val codeNotes: List<String> = emptyList(),
        val safetyChecks: List<SafetyCheckItem> = emptyList(),
        val ribDesign: RibDesignResult? = null,
        val solidHeadDesign: SolidHeadDesignResult? = null,
        val punchingShearCheck: PunchingShearCheck? = null,
        val deflectionCheck: FlatSlabDesign.DeflectionResult? = null,
        val concreteVolume: Double = 0.0,
        val steelWeight: Double = 0.0,
        val cost: Double = 0.0
    )

    data class RibDesignResult(
        val flexureReinforcement: RebarResult,
        val shearReinforcement: ShearReinforcementResult,
        val isSafe: Boolean,
        val utilizationRatio: Double
    )

    data class SolidHeadDesignResult(
        val flexureReinforcement: RebarResult,
        val punchingShear: PunchingShearCheck,
        val isSafe: Boolean
    )

    data class PunchingShearCheck(
        val vu: Double,
        val vc: Double,
        val isSafe: Boolean,
        val utilizationRatio: Double
    )

    // ══════════════════════════════════════════════════════════════════════════════════════════════════════════════════
    // INPUT DATA CLASS
    // ══════════════════════════════════════════════════════════════════════════════════════════════════════════════════

    data class WaffleSlabInput(
        val lx: Double = 6000.0,           // mm (shorter span)
        val ly: Double = 7500.0,           // mm (longer span)
        val ribSpacing: Double = 600.0,    // mm (center-to-center)
        val ribWidth: Double = 150.0,      // mm
        val ribHeight: Double = 300.0,     // mm
        val toppingThickness: Double = 50.0,  // mm
        val solidHeadSize: Double = 1000.0,   // mm
        val columnWidth: Double = 400.0,     // mm
        val columnDepth: Double = 400.0,     // mm
        val fcu: Double = 30.0,            // MPa
        val fy: Double = 400.0,            // MPa
        val liveLoad: Double = 3.0,        // kN/m2
        val deadLoad: Double = 3.0,        // kN/m2
        val clearCover: Double = 25.0,     // mm
        val designCode: DesignCode = DesignCode.ECP
    )
}