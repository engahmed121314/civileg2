package com.civileg.app.data.repository

import androidx.lifecycle.asFlow
import com.civileg.app.data.local.PreferencesManager
import com.civileg.app.db.ProjectDao
import com.civileg.app.db.Project
import com.civileg.app.domain.entities.Project as DomainProject
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.ElementType
import com.civileg.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao,
    private val preferencesManager: PreferencesManager
) : ProjectRepository {

    private fun Project.toDomain(): DomainProject = DomainProject(
        id = id.toInt(),
        name = name,
        date = createdAt.time,
        designCode = DesignCode.entries.find { it.name == code } ?: DesignCode.ECP,
        elementType = ElementType.BEAM,
        inputs = emptyMap(),
        results = emptyMap(),
        notes = "$location - $description"
    )

    override fun getAllProjects(): Flow<List<DomainProject>> =
        projectDao.getAllProjects().asFlow().map { list -> list.map { it.toDomain() } }

    override fun getProjectsByType(elementType: String): Flow<List<DomainProject>> =
        projectDao.getAllProjects().asFlow().map { list ->
            list.filter { it.name.contains(elementType, ignoreCase = true) }.map { it.toDomain() }
        }

    override suspend fun getProject(id: Int): DomainProject? =
        projectDao.getProjectById(id.toLong())?.toDomain()

    override suspend fun saveProject(project: DomainProject): Long {
        val entity = Project(
            id = project.id.toLong(), name = project.name,
            description = project.notes
        )
        return if (project.id == 0) projectDao.insertProject(entity)
        else { projectDao.updateProject(entity); project.id.toLong() }
    }

    override suspend fun deleteProject(project: DomainProject) {
        val entity = Project(id = project.id.toLong(), name = project.name)
        projectDao.deleteProject(entity)
    }

    override suspend fun deleteProjectById(id: Int) {
        val entity = projectDao.getProjectById(id.toLong()) ?: return
        projectDao.deleteProject(entity)
    }

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

    override suspend fun setCurrency(currency: String) = preferencesManager.setCurrency(currency)
    override suspend fun setDefaultDesignCode(code: String) = preferencesManager.setDefaultDesignCode(code)
    override suspend fun setUnitSystem(system: String) = preferencesManager.setUnitSystem(system)
    override suspend fun setThemeMode(mode: String) = preferencesManager.setThemeMode(mode)
    override suspend fun setReportLanguage(lang: String) = preferencesManager.setReportLanguage(lang)
}