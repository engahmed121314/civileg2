package com.civileg.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DesignDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDesign(design: DesignEntity)

    @Query("SELECT * FROM saved_designs ORDER BY timestamp DESC")
    fun getAllDesigns(): Flow<List<DesignEntity>>

    @Delete
    suspend fun deleteDesign(design: DesignEntity)
}