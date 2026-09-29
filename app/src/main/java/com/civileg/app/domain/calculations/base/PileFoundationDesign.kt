package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.PileCapInput
import com.civileg.app.domain.PileDesignResult
import com.civileg.app.domain.PileGroupInput
import com.civileg.app.domain.PileInput

/**
 * Unified interface for pile foundation design per any structural code.
 * Covers: single pile capacity, pile cap design, settlement,
 * group efficiency, lateral load, and negative skin friction.
 */
interface PileFoundationDesign {

    /**
     * Complete pile foundation design — runs all sub-calculations
     * and returns an aggregated [PileDesignResult].
     */
    fun designPile(input: PileInput): PileDesignResult

    /**
     * Single pile geotechnical capacity:
     * shaft friction + end bearing (ultimate & allowable).
     */
    fun calculatePileCapacity(input: PileInput): PileCapacityResult

    /**
     * Pile cap structural design:
     * punching shear, beam shear, flexural reinforcement.
     */
    fun designPileCap(input: PileCapInput): PileCapResult

    /**
     * Pile settlement estimation
     * (immediate + consolidation, per Meyerhof's method).
     */
    fun calculateSettlement(input: PileInput): PileSettlementResult

    /**
     * Pile group efficiency analysis (Converse-Labarre formula)
     * and group capacity.
     */
    fun checkGroupEfficiency(input: PileGroupInput): PileGroupResult

    /**
     * Lateral load capacity using Broms method.
     * @return ultimate lateral capacity in kN
     */
    fun calculateLateralCapacity(input: PileInput): LateralLoadResult

    /**
     * Negative skin friction calculation for compressible soil layers.
     * @return negative skin friction force in kN
     */
    fun calculateNegativeSkinFriction(input: PileInput): Double

    /**
     * Structural design of the pile itself (reinforcement for axial + lateral).
     */
    fun designPileReinforcement(input: PileInput, axialLoad: Double, moment: Double): com.civileg.app.domain.PileReinforcementResult
}

// ══════════════════════════════════════════════════════════════
// RESULT DATA CLASSES
// ══════════════════════════════════════════════════════════════

data class PileCapacityResult(
    val ultimateCapacity: Double,       // kN
    val allowableCapacity: Double,       // kN
    val shaftResistance: Double,         // kN
    val endBearingResistance: Double,    // kN
    val fs: Double,                      // factor of safety
    val utilizationRatio: Double
)

data class PileCapResult(
    val capWidth: Double,                // mm
    val capLength: Double,               // mm
    val capThickness: Double,            // mm
    val punchingShearOk: Boolean,
    val punchingShearStress: Double,     // MPa
    val punchingShearCapacity: Double,   // MPa
    val beamShearOk: Boolean,
    val beamShearStress: Double,         // MPa
    val beamShearCapacity: Double,       // MPa
    val flexuralReinforcement: RebarDetail,
    val punchingReinforcement: RebarDetail?,
    val concreteVolume: Double,          // m3
    val steelWeight: Double              // kg
)

data class RebarDetail(
    val bars: Int,
    val diameter: Int,                   // mm
    val spacing: Int,                    // mm
    val area: Double,                    // mm2
    val requiredArea: Double,            // mm2
    val ratio: Double
) {
    val barString: String
        get() = if (bars > 0) "${bars}Ø$diameter @$spacing" else "N/A"
}

data class PileSettlementResult(
    val immediateSettlement: Double,     // mm
    val consolidationSettlement: Double, // mm
    val totalSettlement: Double,         // mm
    val allowableSettlement: Double,     // mm
    val isOk: Boolean
)

data class PileGroupResult(
    val efficiencyFactor: Double,
    val groupCapacity: Double,           // kN
    val individualCapacity: Double,      // kN
    val spacing: Double,                 // mm
    val numberOfPiles: Int,
    val pattern: String                  // "2x2", "3x3", etc.
)

/**
 * Result for lateral load analysis via Broms method.
 */
data class LateralLoadResult(
    val ultimateLateralCapacity: Double,  // kN
    val allowableLateralCapacity: Double, // kN
    val maxBendingMoment: Double,         // kN.m
    val depthToFixity: Double,            // m
    val deflectionAtHead: Double          // mm
)
