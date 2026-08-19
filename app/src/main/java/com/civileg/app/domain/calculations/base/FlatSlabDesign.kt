package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.FlatSlabInput
import com.civileg.app.domain.FlatSlabResult

/**
 * Interface for flat slab design per any design code.
 *
 * Covers:
 *  - Direct Design Method (DDM): Mo = wu * l2 * ln^2 / 8
 *  - Moment distribution to column strip (60-75%) and middle strip (25-40%)
 *  - Positive/negative moment split
 *  - Punching shear: Vu at d/2, Vc with code-specific formulas
 *  - Shear stud / stirrup design if Vc insufficient
 *  - Deflection check (immediate + long-term)
 *  - Drop panel requirements
 *  - Reinforcement design per strip
 */
interface FlatSlabDesign {

    /**
     * Full flat slab design — main entry point.
     * @param input all design parameters
     * @return complete design result
     */
    fun design(input: FlatSlabInput): FlatSlabResult

    /**
     * Calculate total static moment Mo for one span (DDM).
     * Mo = wu * l2 * ln^2 / 8
     */
    fun calculateStaticMoment(
        wu: Double,   // factored load kN/m2
        ln: Double,   // clear span in design direction (m)
        l2: Double    // transverse span (m)
    ): Double       // kN.m

    /**
     * Get moment distribution coefficients for column/middle strip.
     * Returns: (colStripNegFraction, colStripPosFraction, midStripNegFraction, midStripPosFraction)
     */
    fun getMomentCoefficients(
        panelType: PanelType,
        hasBeams: Boolean = false
    ): MomentCoefficients

    data class MomentCoefficients(
        val colNegExterior: Double,  // fraction of -M_ext to column strip
        val colPositive: Double,     // fraction of +M to column strip
        val colNegInterior: Double,  // fraction of -M_int to column strip
        val midNegExterior: Double,
        val midPositive: Double,
        val midNegInterior: Double
    )

    /**
     * Column strip width per code.
     */
    fun getColumnStripWidth(
        l2: Double,        // transverse span (mm)
        columnSize: Double, // column dimension perpendicular to span (mm)
        panelType: PanelType
    ): Double  // mm

    /**
     * Punching shear check at column-slab connection.
     * Critical section at d/2 from column face.
     */
    fun checkPunchingShear(
        vu: Double,           // factored shear force (kN)
        fcu: Double,
        fy: Double,
        slabThickness: Double, // mm
        dropThickness: Double,// mm (0 if no drop)
        columnWidth: Double,   // mm
        columnDepth: Double,   // mm
        cover: Double = 25.0
    ): PunchingShearResult

    data class PunchingShearResult(
        val vu: Double,         // kN
        val vc: Double,         // kN (concrete capacity)
        val bo: Double,         // mm (critical perimeter)
        val d: Double,          // mm (effective depth)
        val isSafe: Boolean,
        val utilizationRatio: Double,
        val needsReinforcement: Boolean,
        val studDia: Double = 0.0,
        val studSpacing: Double = 0.0,
        val studRows: Int = 0,
        val vsProvided: Double = 0.0
    )

    /**
     * Design reinforcement for a strip given moment.
     * Returns: (As required mm2, As provided mm2, bar dia mm, bar spacing mm)
     */
    fun designReinforcement(
        moment: Double,          // kN.m
        fcu: Double,
        fy: Double,
        effectiveDepth: Double,  // mm
        stripWidth: Double,      // mm
        cover: Double = 25.0
    ): ReinforcementDesign

    data class ReinforcementDesign(
        val asRequired: Double,     // mm2
        val asProvided: Double,     // mm2
        val barDia: Double,         // mm
        val barSpacing: Double,     // mm
        val barsCount: Int,
        val isMinSteel: Boolean
    )

    /**
     * Deflection check.
     */
    fun checkDeflection(
        span: Double,            // mm (clear span)
        slabThickness: Double,   // mm
        fcu: Double,
        fy: Double,
        serviceMoment: Double,   // kN.m
        effectiveDepth: Double,  // mm
        providedAs: Double       // mm2
    ): DeflectionResult

    data class DeflectionResult(
        val immediate: Double,     // mm
        val longTerm: Double,      // mm
        val allowable: Double,     // mm
        val isSafe: Boolean,
        val ratio: Double
    )

    /**
     * Minimum slab thickness per code.
     */
    fun getMinimumThickness(
        clearSpan: Double,     // mm
        transverseSpan: Double, // mm
        hasDropPanel: Boolean,
        fcu: Double,
        fy: Double
    ): Double  // mm

    /**
     * Code-specific limits.
     */
    fun getMinCover(): Double
    fun getMaxBarSpacing(): Double
    fun getMinReinforcementRatio(fy: Double): Double
    fun getCodeName(): String
    fun getFactoredLoad(deadLoad: Double, liveLoad: Double): Double
}