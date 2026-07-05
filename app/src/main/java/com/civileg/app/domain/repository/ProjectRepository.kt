package com.civileg.app.domain.repository

import com.civileg.app.domain.entities.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun getAllProjects(): Flow<List<Project>>
    fun getProjectsByType(elementType: String): Flow<List<Project>>
    suspend fun getProject(id: Int): Project?
    suspend fun saveProject(project: Project): Long
    suspend fun deleteProject(project: Project)
    suspend fun deleteProjectById(id: Int)
    
    // Material Prices & Settings
    fun getConcretePrice(): Flow<Double>
    fun getSteelPrice(): Flow<Double>
    fun getFormworkPrice(): Flow<Double>
    fun getCurrency(): Flow<String>
    fun getDefaultDesignCode(): Flow<String>
    fun getUnitSystem(): Flow<String>
    fun getThemeMode(): Flow<String>
    fun getReportLanguage(): Flow<String>
    
    suspend fun updatePrices(concrete: Double?, steel: Double?, formwork: Double?)
    suspend fun setCurrency(currency: String)
    suspend fun setDefaultDesignCode(code: String)
    suspend fun setUnitSystem(system: String)
    suspend fun setThemeMode(mode: String)
    suspend fun setReportLanguage(lang: String)
}
