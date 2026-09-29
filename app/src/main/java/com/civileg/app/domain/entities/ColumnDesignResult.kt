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
    val deflectionIn: Double,         // mm - lateral deflection in-plane
    val deflectionOut: Double,        // mm - lateral deflection out-of-plane
    val MaddIn: Double,               // kN.m - additional moment in-plane
    val MaddOut: Double,              // kN.m - additional moment out-of-plane
    val MdesIn: Double,               // kN.m - design moment in-plane
    val MdesOut: Double,              // kN.m - design moment out-of-plane
    val eccentricityIn: Double,       // mm
    val eccentricityOut: Double,      // mm

    // ===== AXIAL CAPACITY =====
    val Phi: Double,                  // Strength reduction factor
    val alphaFactor: Double,         // Alpha factor
    val Pu0: Double,                  // kN - pure axial capacity (no moment)
    val axialCapacity: Double,       // kN - axial capacity with moment
    val utilizationRatio: Double,     // Pu / axialCapacity

    // ===== REINFORCEMENT =====
    val AsRequired: Double,           // mm²
    val AsMin: Double,                // mm² (code minimum)
    val AsMax: Double,                // mm² (code maximum)
    val AsProvided: Double,           // mm²
    val rhoActual: Double,            // %
    val rhoMin: Double,
    val rhoMax: Double,
    val finalBars: String,           // e.g. "8Ø22"
    val finalBarCount: Int,
    val finalBarDia: Int,
    val rebarAlternatives: List<RebarAlternative> = emptyList(),

    // ===== TIES / STIRRUPS =====
    val tieDia: Int = 8,
    val tieSpacingMax: Double,         // mm
    val tieSpacingDense: Double,      // mm (at condensation zone)
    val tieSpacingNormal: Double,    // mm (at mid-height)
    val condensationZoneLength: Double, // mm
    val tieDescription: String = "",
    val tieWeightKg: Double = 0.0,

    // ===== SHEAR =====
    val shearForce: Double = 0.0,
    val concreteShearCapacity: Double = 0.0,
    val shearIsSafe: Boolean = true,
    val shearVsRequired: Double = 0.0,
    val shearAvProvided: Double = 0.0,
    val shearDescription: String = "",

    // ===== SAFETY =====
    val isSafe: Boolean,
    val safetyChecks: List<SafetyCheckItem> = emptyList(),
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList(),

    // ===== QUANTITIES =====
    val concreteVolume: Double = 0.0,
    val steelWeight: Double = 0.0,
    val cost: Double = 0.0,

    // ===== STEP-BY-STEP =====
    val calculationSteps: List<CalculationStep> = emptyList()
) : Parcelable

@Parcelize
data class RebarAlternative(
    val count: Int,
    val diameter: Int,
    val area: Double,
    val description: String
) : Parcelable
