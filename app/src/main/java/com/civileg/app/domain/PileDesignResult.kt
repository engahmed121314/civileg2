package com.civileg.app.domain

import com.civileg.app.domain.calculations.base.PileCapacityResult
import com.civileg.app.domain.calculations.base.PileCapResult
import com.civileg.app.domain.calculations.base.PileSettlementResult
import com.civileg.app.domain.calculations.base.PileGroupResult

/**
 * Comprehensive result for the complete pile foundation design.
 * Aggregates capacity, settlement, group efficiency, pile cap,
 * lateral capacity, and structural pile design.
 */
data class PileDesignResult(
    // ── Input summary ──
    val pileType: String,
    val soilType: String,
    val pileDiameterMm: Double,
    val pileLengthM: Double,
    val numberOfPiles: Int,
    val fcu: Double,
    val fy: Double,

    // ── Single pile capacity ──
    val capacityResult: PileCapacityResult,

    // ── Pile group efficiency ──
    val groupResult: PileGroupResult,

    // ── Settlement ──
    val settlementResult: PileSettlementResult,

    // ── Pile cap design ──
    val capResult: PileCapResult,

    // ── Lateral load capacity (Broms) ──
    val lateralCapacity: Double,          // kN
    val lateralUtilizationRatio: Double,

    // ── Negative skin friction ──
    val negativeSkinFriction: Double,     // kN

    // ── Pile structural design ──
    val pileReinforcement: PileReinforcementResult,

    // ── Overall safety ──
    val isSafe: Boolean,
    val utilizationRatio: Double,
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList()
)

/**
 * Reinforcement design result for a single pile.
 */
data class PileReinforcementResult(
    val longitudinalBars: Int,
    val longitudinalDiameter: Int,        // mm
    val longitudinalArea: Double,         // mm2
    val requiredLongitudinalArea: Double, // mm2
    val tiesDiameter: Int,                // mm
    val tiesSpacing: Int,                 // mm
    val isSafe: Boolean,
    val ratio: Double                     // As/Ac ratio
) {
    val barString: String
        get() = if (longitudinalBars > 0) {
            "$longitudinalBarsØ$longitudinalDiameter ties Ø$tiesDiameter @$tiesSpacing"
        } else "N/A"
}

/**
 * Lateral load capacity result (Broms method).
 */
data class LateralLoadResult(
    val ultimateLateralCapacity: Double,  // kN
    val allowableLateralCapacity: Double, // kN
    val maxBendingMoment: Double,         // kN.m
    val depthToFixity: Double,            // m
    val deflectionAtHead: Double          // mm
)