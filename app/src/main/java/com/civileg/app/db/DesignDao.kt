package com.civileg.app.db

import androidx.room.*
import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow

@Dao
interface DesignDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDesign(design: Design): Long

    @Query("SELECT * FROM designs WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getDesignsForProject(projectId: Long): LiveData<List<Design>>

    @Query("SELECT * FROM designs ORDER BY createdAt DESC")
    fun getAllDesigns(): LiveData<List<Design>>

    @Query("SELECT * FROM designs WHERE name LIKE :query ORDER BY createdAt DESC")
    fun searchDesigns(query: String): Flow<List<Design>>

    @Delete
    suspend fun deleteDesign(design: Design)

    @Query("SELECT * FROM designs WHERE id = :designId")
    suspend fun getDesignById(designId: Long): Design?

    @Query("SELECT SUM(totalCost) FROM designs WHERE projectId = :projectId")
    suspend fun getTotalCostForProject(projectId: Long): Double?
}
