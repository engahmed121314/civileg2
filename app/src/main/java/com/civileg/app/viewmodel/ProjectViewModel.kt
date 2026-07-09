package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.*
import com.civileg.app.domain.entities.Project as DomainProject
import com.civileg.app.utils.CalculatorEngine
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    private val designDao: DesignDao,
    private val materialDao: MaterialDao
) : ViewModel() {

    // Main Project Containers
    val allProjects: LiveData<List<Project>> = projectDao.getAllProjects()
    val activeProjectCount: LiveData<Int> = projectDao.getActiveProjectCount()

    // Designs
    val allDesigns: LiveData<List<Design>> = designDao.getAllDesigns()

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

    fun saveSeismic(projectId: Long, name: String, result: CalculatorEngine.SeismicResult) {
        viewModelScope.launch {
            val gson = Gson()
            val design = Design(
                projectId = projectId,
                type = DesignType.SEISMIC,
                name = name,
                inputData = "{}",
                results = gson.toJson(result),
                isSafe = result.isSafe,
                utilizationRatio = 0.0,
                codeUsed = result.code.displayName,
                createdAt = Date()
            )
            designDao.insertDesign(design)
        }
    }
}