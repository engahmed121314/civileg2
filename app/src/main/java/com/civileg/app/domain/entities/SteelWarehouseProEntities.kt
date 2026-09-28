package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BOMItem(
    val description: String,
    val category: String,
    val quantity: Double,
    val unit: String,
    val weightKg: Double,
    val totalCost: Double
) : Parcelable

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
    val notes: List<String>,
    val billOfMaterials: List<BOMItem> = emptyList(),
    val totalCost: Double = 0.0,
    val durationWeeks: Int = 0,
    val safetyScore: Double = 0.0
) : Parcelable
