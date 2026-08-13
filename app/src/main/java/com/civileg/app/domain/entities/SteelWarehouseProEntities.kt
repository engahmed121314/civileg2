package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SteelWarehouseProResult(
    val codeName: String,
    val tributaryAreaM2: Double,
    val serviceLoadKnM2: Double,
    val frameReactionKn: Double,
    val baseShearKn: Double,
    val maxMomentKnM: Double,
    val maxAxialKn: Double,
    val maxShearKn: Double,
    val driftMm: Double,
    val utilization: Double,
    val compressionZone: String,
    val tensionZone: String,
    val notes: List<String>
) : Parcelable
