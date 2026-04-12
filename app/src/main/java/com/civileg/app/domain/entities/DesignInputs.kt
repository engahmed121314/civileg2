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
    val loadCombination: LoadCombination
) : Parcelable

@Parcelize
data class BeamInputs(
    val fcu: Double,
    val fy: Double,
    val width: Double,
    val totalDepth: Double,
    val effectiveDepth: Double,
    val designMoment: Double,
    val designShear: Double
) : Parcelable

@Parcelize
data class SlabInputs(
    val fcu: Double,
    val fy: Double,
    val thickness: Double,
    val deadLoad: Double,
    val liveLoad: Double
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
    val axialLoad: Double,
    val moment: Double,
    val shear: Double,
    val unbracedLength: Double
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
    val shearPoints: List<Pair<Double, Double>>
) : Parcelable
