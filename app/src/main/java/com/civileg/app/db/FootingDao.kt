package com.civileg.app.db

import androidx.room.*

@Dao
interface FootingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFooting(footing: Footing): Long

    @Query("SELECT * FROM footings WHERE projectId = :projectId")
    suspend fun getFootingsForProject(projectId: Long): List<Footing>
    
    @Delete
    suspend fun deleteFooting(footing: Footing)
}
