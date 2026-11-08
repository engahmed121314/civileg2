package com.civileg.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "building_layouts")
data class BuildingLayoutEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val structureType: String,
    val plotWidth: Double,
    val plotLength: Double,
    val gridSpacing: Double,
    val jsonModelData: String,
    val updatedAt: Long
)
