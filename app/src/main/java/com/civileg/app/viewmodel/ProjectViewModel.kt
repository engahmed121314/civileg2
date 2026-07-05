package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.civileg.app.data.local.dao.ProjectDao as ArchiveProjectDao
import com.civileg.app.db.*
import com.civileg.app.data.local.entities.ProjectEntity
import com.civileg.app.domain.entities.Project as DomainProject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val archiveProjectDao: ArchiveProjectDao,
    private val projectDao: ProjectDao,
    private val designDao: DesignDao,
    private val materialDao: MaterialDao
) : ViewModel() {

    // Archive Projects (Individual designs)
    private val allArchiveProjectsFlow: Flow<List<DomainProject>> = archiveProjectDao.getAllProjects().map { entities ->
        entities.map { it.toProject() }
    }
    val allArchiveProjects = allArchiveProjectsFlow.asLiveData()

    // Main Project Containers
    val allProjects: LiveData<List<Project>> = projectDao.getAllProjects()
    val activeProjectCount: LiveData<Int> = projectDao.getActiveProjectCount()
    
    // Designs
    val allDesigns: LiveData<List<Design>> = designDao.getAllDesigns()

    // --- Archive Methods ---
    suspend fun saveArchiveProject(project: DomainProject): Long {
        return archiveProjectDao.insertProject(ProjectEntity.fromProject(project))
    }

    fun deleteArchiveProject(project: DomainProject) {
        viewModelScope.launch {
            archiveProjectDao.deleteProjectById(project.id)
        }
    }

    // --- Main Project Methods ---
    fun insert(project: Project) {
        viewModelScope.launch {
            projectDao.insertProject(project)
        }
    }

    fun delete(project: Project) {
        viewModelScope.launch {
            projectDao.deleteProject(project)
        }
    }

    // --- Design Methods ---
    fun saveDesign(design: Design) {
        viewModelScope.launch {
            designDao.insertDesign(design)
        }
    }
    
    fun insertDesign(design: Design) = saveDesign(design)

    fun getDesignsForProject(projectId: Long): LiveData<List<Design>> {
        return designDao.getDesignsForProject(projectId)
    }

    // --- Material Methods ---
    fun saveMaterial(material: MaterialItem) {
        viewModelScope.launch {
            materialDao.insertMaterial(material)
        }
    }

    fun getMaterialsForProject(projectId: Long): LiveData<List<MaterialItem>> {
        return materialDao.getMaterialsForProject(projectId)
    }
}
