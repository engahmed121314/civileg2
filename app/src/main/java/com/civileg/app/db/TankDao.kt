package com.civileg.app.db

import androidx.room.*

@Dao
interface TankDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTank(tank: Tank): Long

    @Query("SELECT * FROM tanks WHERE projectId = :projectId")
    suspend fun getTanksForProject(projectId: Long): List<Tank>

    @Delete
    suspend fun deleteTank(tank: Tank)
}
