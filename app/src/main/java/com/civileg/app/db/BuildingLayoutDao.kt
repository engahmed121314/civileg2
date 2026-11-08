package com.civileg.app.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildingLayoutDao {
    @Query("SELECT * FROM building_layouts ORDER BY updatedAt DESC")
    fun getAllLayouts(): Flow<List<BuildingLayoutEntity>>

    @Query("SELECT * FROM building_layouts WHERE id = :layoutId")
    suspend fun getLayoutById(layoutId: String): BuildingLayoutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayout(layout: BuildingLayoutEntity)

    @Delete
    suspend fun deleteLayout(layout: BuildingLayoutEntity)
}
