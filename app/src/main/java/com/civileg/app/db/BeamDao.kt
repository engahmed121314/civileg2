package com.civileg.app.db

import androidx.room.*

@Dao
interface BeamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeam(beam: Beam): Long

    @Query("SELECT * FROM beams WHERE projectId = :projectId")
    suspend fun getBeamsForProject(projectId: Long): List<Beam>

    @Delete
    suspend fun deleteBeam(beam: Beam)
}
