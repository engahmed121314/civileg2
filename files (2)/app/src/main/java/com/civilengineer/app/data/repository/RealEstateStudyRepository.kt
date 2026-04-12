package com.civilengineer.app.data.repository

import com.civilengineer.app.data.database.dao.RealEstateStudyDao
import com.civilengineer.app.data.models.RealEstateStudy
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealEstateStudyRepository @Inject constructor(
    private val dao: RealEstateStudyDao
) {

    suspend fun insertStudy(study: RealEstateStudy): Long {
        return dao.insert(study)
    }

    suspend fun updateStudy(study: RealEstateStudy) {
        dao.update(study)
    }

    suspend fun deleteStudy(study: RealEstateStudy) {
        dao.delete(study)
    }

    suspend fun deleteStudyById(id: Int) {
        dao.deleteStudyById(id)
    }

    suspend fun getStudyById(id: Int): RealEstateStudy? {
        return dao.getStudyById(id)
    }

    fun getStudyByIdFlow(id: Int): Flow<RealEstateStudy?> {
        return dao.getStudyByIdFlow(id)
    }

    fun getAllStudies(): Flow<List<RealEstateStudy>> {
        return dao.getAllStudies()
    }

    fun searchStudies(searchQuery: String): Flow<List<RealEstateStudy>> {
        return dao.searchStudies(searchQuery)
    }

    fun getStudiesByProjectType(projectType: String): Flow<List<RealEstateStudy>> {
        return dao.getStudiesByProjectType(projectType)
    }

    fun getStudiesCount(): Flow<Int> {
        return dao.getStudiesCount()
    }

    fun getRecentStudies(limit: Int = 10): Flow<List<RealEstateStudy>> {
        return dao.getRecentStudies(limit)
    }
}