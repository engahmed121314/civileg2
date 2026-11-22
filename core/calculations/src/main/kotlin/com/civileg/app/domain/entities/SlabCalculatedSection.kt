package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Actual calculated/adopted section coordinates, never a longitudinal bar path. */
@Parcelize
data class SlabPlacedSectionBar(
    val layerIndex: Int,
    val indexInLayer: Int,
    val xFromWebLeftMm: Double,
    val yFromBottomMm: Double,
    val diameterMm: Double
) : Parcelable

/** One rib-pitch cross-section only. Pitch is not a supplied full-panel width. */
@Parcelize
data class SlabCalculatedSection(
    val geometryVersion: Int,
    val ribWidthMm: Double,
    val enteredRibPitchMm: Double,
    val totalDepthMm: Double,
    val toppingThicknessMm: Double,
    /** Analytical effective flange width, not a newly inferred physical edge. */
    val analyticalFlangeWidthMm: Double,
    val tensionFace: String,
    val coverToOutsideLinksMm: Double,
    val linkDiameterMm: Double,
    val linkLegs: Int,
    val linkSpacingMm: Double,
    val placementBasis: String,
    /** Null if coordinate export is outside its explicit budget. Never fabricated bars. */
    val bars: List<SlabPlacedSectionBar>?,
    val incompleteGeometry: List<String>
) : Parcelable
