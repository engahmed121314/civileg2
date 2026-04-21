package com.civileg.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_designs")
data class DesignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val designType: String, // "Beam", "Column", etc.
    val resultSummary: String,
    val timestamp: Long = System.currentTimeMillis()
)