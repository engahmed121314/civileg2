package com.civileg.app.db

import androidx.room.*

@Dao
interface RetainingWallDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRetainingWall(retainingWall: RetainingWall): Long

    @Query("SELECT * FROM retaining_walls WHERE projectId = :projectId")
    suspend fun getRetainingWallsForProject(projectId: Long): List<RetainingWall>

    @Delete
    suspend fun deleteRetainingWall(retainingWall: RetainingWall)
}
