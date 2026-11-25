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
    val axialLoad: Double = 0.0,
    val lateralLoad: Double = 0.0,
    val columnWidth: Double = 400.0,
    val columnLength: Double = 400.0,

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
    val codeNotes: List<String> = emptyList(),
    val calculationSteps: List<com.civileg.app.domain.entities.CalculationStep> = emptyList(),
    /** Never true yet: capacity/group/settlement/lateral/negative-friction rows are geotechnical estimates and the cap/pile-steel rules are not verified clause by clause. */
    val codeAcceptanceComplete: Boolean = false,
    val qualificationNotes: List<String> = PileQualification.NOTES
)

enum class PileStatus { CHECKS_FAILED, PRELIMINARY, ACCEPTED }

object PileQualification {
    val NOTES: List<String> = listOf(
        "PRELIMINARY: single-pile capacity (alpha/beta, Nc, Nq), group efficiency, settlement, lateral (Broms) and negative skin friction are geotechnical estimates from engine formulas. They are not checks taken from the selected concrete code. A geotechnical report and an independent check are required.",
        "Pile cap and pile reinforcement rules are not verified clause by clause against the selected edition. The result is not a code-accepted pile design."
    )

    /** A failed estimate stays failed. Passing estimates are only ever PRELIMINARY until acceptance is complete. */
    fun status(isSafe: Boolean, codeAcceptanceComplete: Boolean): PileStatus = when {
        !isSafe -> PileStatus.CHECKS_FAILED
        !codeAcceptanceComplete -> PileStatus.PRELIMINARY
        else -> PileStatus.ACCEPTED
    }
}

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
            "${longitudinalBars}Ø$longitudinalDiameter ties Ø$tiesDiameter @$tiesSpacing"
        } else "N/A"
}