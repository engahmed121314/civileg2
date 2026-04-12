package com.civileg.app.db

import androidx.room.*

@Dao
interface StairDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStair(stair: Stair): Long

    @Query("SELECT * FROM stairs WHERE projectId = :projectId")
    suspend fun getStairsForProject(projectId: Long): List<Stair>

    @Delete
    suspend fun deleteStair(stair: Stair)
}
