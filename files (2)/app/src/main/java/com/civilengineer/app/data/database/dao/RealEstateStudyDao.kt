package com.civilengineer.app.data.database.dao

import androidx.room.*
import com.civilengineer.app.data.models.RealEstateStudy
import kotlinx.coroutines.flow.Flow

@Dao
interface RealEstateStudyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(study: RealEstateStudy): Long

    @Update
    suspend fun update(study: RealEstateStudy)

    @Delete
    suspend fun delete(study: RealEstateStudy)

    @Query("SELECT * FROM real_estate_studies WHERE id = :id")
    suspend fun getStudyById(id: Int): RealEstateStudy?

    @Query("SELECT * FROM real_estate_studies WHERE id = :id")
    fun getStudyByIdFlow(id: Int): Flow<RealEstateStudy?>

    @Query("SELECT * FROM real_estate_studies ORDER BY study_date DESC")
    fun getAllStudies(): Flow<List<RealEstateStudy>>

    @Query("SELECT * FROM real_estate_studies WHERE study_name LIKE '%' || :search || '%' ORDER BY study_date DESC")
    fun searchStudies(search: String): Flow<List<RealEstateStudy>>

    @Query("SELECT * FROM real_estate_studies WHERE project_type = :projectType ORDER BY study_date DESC")
    fun getStudiesByProjectType(projectType: String): Flow<List<RealEstateStudy>>

    @Query("DELETE FROM real_estate_studies WHERE id = :id")
    suspend fun deleteStudyById(id: Int)

    @Query("SELECT COUNT(*) FROM real_estate_studies")
    fun getStudiesCount(): Flow<Int>

    @Query("SELECT * FROM real_estate_studies ORDER BY study_date DESC LIMIT :limit")
    fun getRecentStudies(limit: Int): Flow<List<RealEstateStudy>>
}