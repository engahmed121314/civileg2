package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Comprehensive Column Design Result
 * Based on ECP 203 (Egyptian Code) - Eng. Yasser El-Leathy's Notes
 * Covers: Slenderness, K-factors, Additional Moments, Flexure, Shear, Ties
 */
@Parcelize
data class ColumnDesignResult(
    // ===== INPUT =====
    val columnWidth: Double,          // mm - b (out of plane)
    val columnDepth: Double,          // mm - t (in plane)
    val totalHeight: Double,         // mm - H
    val clearHeightIn: Double,       // mm - Ho_in
    val clearHeightOut: Double,      // mm - Ho_out
    val fcu: Double,
    val fy: Double,
    val Pu: Double,                  // kN - ultimate axial load
    val MextIn: Double,            // kN.m - external moment in-plane
    val MextOut: Double,           // kN.m - external moment out-of-plane
    val isBraced: Boolean,
    val topCond: Int,                // 1=Fixed, 2=PartiallyFixed, 3=Hinged, 4=Free
    val botCond: Int,
    val beamDepthIn: Double,        // mm
    val beamDepthOut: Double,       // mm
    val cover: Double,               // mm
    val designCode: DesignCode,

    // ===== SLENDERNESS (PDF 16: Column Classification) =====
    val KfactorIn: Double,          // K effective length factor in-plane
    val KfactorOut: Double,         // K effective length factor out-of-plane
    val lambdaIn: Double,            // Slenderness ratio in-plane
    val lambdaOut: Double,           // Slenderness ratio out-of-plane
    val lambdaMax: Double,           // max of both
    val lambdaLimitShort: Double,    // short column limit
    val lambdaLimitLong: Double,     // long column limit
    val columnClassification: String, // "Short", "Long", "Unsafe_Slender"

    // ===== ADDITIONAL MOMENT (PDF 16: Long Columns) =====
    deflectionIn: Double,         // mm - lateral deflection in-plane
    deflectionOut: Double,        // mm - lateral deflection out-of-plane
    MaddIn: Double,               // kN.m - additional moment in-plane
    MaddOut: Double,              // kN.m - additional moment out-of-plane
    MdesIn: Double,               // kN.m - design moment in-plane
    MdesOut: Double,              // kN.m - design moment out-of-plane
    eccentricityIn: Double,       // mm
    eccentricityOut: Double,      // mm

    // ===== AXIAL CAPACITY =====
    Phi: Double,                  // Strength reduction factor
    alphaFactor: Double,         // Alpha factor
    Pu0: Double,                  // kN - pure axial capacity (no moment)
    axialCapacity: Double,       // kN - axial capacity with moment
    utilizationRatio: Double,     // Pu / axialCapacity

    // ===== REINFORCEMENT =====
    AsRequired: Double,           // mm²
    AsMin: Double,                // mm² (code minimum)
    AsMax: Double,                // mm² (code maximum)
    AsProvided: Double,           // mm²
    rhoActual: Double,            // %
    rhoMin: Double,
    rhoMax: Double,
    finalBars: String,           // e.g. "8Ø22"
    finalBarCount: Int,
    finalBarDia: Int,
    rebarAlternatives: List<RebarAlternative> = emptyList(),

    // ===== TIES / STIRRUPS =====
    tieDia: Int = 8,
    tieSpacingMax: Double,         // mm
    tieSpacingDense: Double,      // mm (at condensation zone)
    tieSpacingNormal: Double,    // mm (at mid-height)
    condensationZoneLength: Double, // mm
    tieDescription: String = "",
    tieWeightKg: Double = 0.0,

    // ===== SHEAR =====
    shearForce: Double = 0.0,
    concreteShearCapacity: Double = 0.0,
    shearIsSafe: Boolean = true,
    shearVsRequired: Double = 0.0,
    shearAvProvided: Double = 0.0,
    shearDescription: String = "",

    // ===== SAFETY =====
    isSafe: Boolean,
    safetyChecks: List<SafetyCheckItem> = emptyList(),
    warnings: List<String> = emptyList(),
    codeNotes: List<String> = emptyList(),

    // ===== QUANTITIES =====
    concreteVolume: Double = 0.0,
    steelWeight: Double = 0.0,
    cost: Double = 0.0,

    // ===== STEP-BY-STEP =====
    calculationSteps: List<CalculationStep> = emptyList()
) : Parcelable

@Parcelize
data class RebarAlternative(
    val count: Int,
    val diameter: Int,
    val area: Double,
    val description: String
) : Parcelable
