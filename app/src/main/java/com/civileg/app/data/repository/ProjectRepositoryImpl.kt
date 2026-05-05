package com.civileg.app.data.repository

import com.civileg.app.data.local.PreferencesManager
import com.civileg.app.data.local.dao.ProjectDao
import com.civileg.app.data.local.entities.ProjectEntity
import com.civileg.app.domain.entities.Project
import com.civileg.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao,
    private val preferencesManager: PreferencesManager
) : ProjectRepository {
    
    override fun getAllProjects(): Flow<List<Project>> = 
        projectDao.getAllProjects().map { entities -> 
            entities.map { it.toProject() } 
        }
    
    override fun getProjectsByType(elementType: String): Flow<List<Project>> =
        projectDao.getProjectsByType(elementType).map { entities ->
            entities.map { it.toProject() }
        }
    
    override suspend fun getProject(id: Int): Project? = 
        projectDao.getProjectById(id)?.toProject()
    
    override suspend fun saveProject(project: Project): Long {
        val entity = ProjectEntity.fromProject(project)
        return if (project.id == 0) {
            projectDao.insertProject(entity)
        } else {
            projectDao.updateProject(entity)
            project.id.toLong()
        }
    }
    
    override suspend fun deleteProject(project: Project) {
        projectDao.deleteProject(ProjectEntity.fromProject(project))
    }
    
    override suspend fun deleteProjectById(id: Int) {
        projectDao.deleteProjectById(id)
    }
    
    // Material Prices & Settings
    override fun getConcretePrice(): Flow<Double> = preferencesManager.concretePrice
    override fun getSteelPrice(): Flow<Double> = preferencesManager.steelPrice
    override fun getFormworkPrice(): Flow<Double> = preferencesManager.formworkPrice
    override fun getCurrency(): Flow<String> = preferencesManager.currency
    override fun getDefaultDesignCode(): Flow<String> = preferencesManager.defaultDesignCode
    override fun getUnitSystem(): Flow<String> = preferencesManager.unitSystem
    override fun getThemeMode(): Flow<String> = preferencesManager.themeMode
    override fun getReportLanguage(): Flow<String> = preferencesManager.reportLanguage
    
    override suspend fun updatePrices(concrete: Double?, steel: Double?, formwork: Double?) {
        concrete?.let { preferencesManager.setConcretePrice(it) }
        steel?.let { preferencesManager.setSteelPrice(it) }
        formwork?.let { preferencesManager.setFormworkPrice(it) }
    }

    override suspend fun setCurrency(currency: String) {
        preferencesManager.setCurrency(currency)
    }

    override suspend fun setDefaultDesignCode(code: String) {
        preferencesManager.setDefaultDesignCode(code)
    }

    override suspend fun setUnitSystem(system: String) {
        preferencesManager.setUnitSystem(system)
    }

    override suspend fun setThemeMode(mode: String) {
        preferencesManager.setThemeMode(mode)
    }

    override suspend fun setReportLanguage(lang: String) {
        preferencesManager.setReportLanguage(lang)
    }
}
