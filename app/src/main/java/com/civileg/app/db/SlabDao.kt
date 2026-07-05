package com.civileg.app.db

import androidx.room.*

@Dao
interface SlabDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlab(slab: Slab): Long

    @Query("SELECT * FROM slabs WHERE projectId = :projectId")
    suspend fun getSlabsForProject(projectId: Long): List<Slab>

    @Delete
    suspend fun deleteSlab(slab: Slab)
}
