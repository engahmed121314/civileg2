package com.civileg.app.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials WHERE projectId = :projectId")
    fun getMaterialsForProject(projectId: Long): LiveData<List<MaterialItem>>
    
    @Insert
    suspend fun insertMaterial(material: MaterialItem)
    
    @Update
    suspend fun updateMaterial(material: MaterialItem)
    
    @Delete
    suspend fun deleteMaterial(material: MaterialItem)
    
    @Query("SELECT SUM(totalPrice) FROM materials WHERE projectId = :projectId")
    suspend fun getTotalCost(projectId: Long): Double?
}