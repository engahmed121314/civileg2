package com.civileg.app.data.local.dao

import androidx.room.*
import com.civileg.app.data.local.entities.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    
    @Query("SELECT * FROM project_entities ORDER BY date DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>
    
    @Query("SELECT * FROM project_entities WHERE id = :id")
    suspend fun getProjectById(id: Int): ProjectEntity?
    
    @Query("SELECT * FROM project_entities WHERE elementType = :elementType ORDER BY date DESC")
    fun getProjectsByType(elementType: String): Flow<List<ProjectEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long
    
    @Update
    suspend fun updateProject(project: ProjectEntity)
    
    @Delete
    suspend fun deleteProject(project: ProjectEntity)
    
    @Query("DELETE FROM project_entities WHERE id = :id")
    suspend fun deleteProjectById(id: Int)
}
