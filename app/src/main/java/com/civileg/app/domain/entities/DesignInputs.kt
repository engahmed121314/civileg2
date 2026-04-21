package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ColumnInputs(
    val fcu: Double,
    val fy: Double,
    val axialLoad: Double,
    val momentX: Double,
    val momentY: Double,
    val loadCombination: LoadCombination,
    val unsupportedLength: Double = 3.0,
    val columnType: ColumnType = ColumnType.Rectangular(300.0, 300.0),
    val connectedSlabType: ConnectedSlabType = ConnectedSlabType.SOLID,
    val hasColumnCap: Boolean = false
) : Parcelable

@Parcelize
data class BeamInputs(
    val fcu: Double,
    val fy: Double,
    val width: Double,
    val totalDepth: Double,
    val effectiveDepth: Double,
    val designMoment: Double,
    val designShear: Double,
    val span: Double = 5.0,
    val deadLoad: Double = 0.0,
    val liveLoad: Double = 0.0,
    val loadCombination: LoadCombination = LoadCombination.DEAD_LIVE
) : Parcelable

@Parcelize
data class SlabInputs(
    val fcu: Double,
    val fy: Double,
    val thickness: Double,
    val deadLoad: Double,
    val liveLoad: Double,
    val shortSpan: Double = 4.0,
    val longSpan: Double = 5.0,
    val loadCombination: LoadCombination = LoadCombination.DEAD_LIVE
) : Parcelable

@Parcelize
data class FootingInputs(
    val axialLoad: Double,
    val soilCapacity: Double,
    val fcu: Double,
    val fy: Double
) : Parcelable

@Parcelize
data class SteelInputs(
    val axialLoad: Double,      // Pu (kN)
    val moment: Double,         // Mu (kN.m)
    val shear: Double,          // Vu (kN)
    val unbracedLength: Double, // Lb (mm)
    val length: Double = 0.0    // Total member length (mm)
) : Parcelable

@Parcelize
data class ColumnAlternative(
    val barDiameter: Double,
    val numberOfBars: Int,
    val totalArea: Double,
    val utilizationRatio: Double,
    val isSafe: Boolean
) : Parcelable

@Parcelize
data class MomentShearDiagrams(
    val momentPoints: List<Pair<Double, Double>>,
    val shearPoints: List<Pair<Double, Double>>,
    val normalPoints: List<Pair<Double, Double>> = emptyList()
) : Parcelable
