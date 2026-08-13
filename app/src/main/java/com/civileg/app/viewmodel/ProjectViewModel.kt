package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.*
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.domain.entities.ProjectSummary
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    private val designDao: DesignDao,
    private val materialDao: MaterialDao
) : ViewModel() {

    // Archive Projects — reuse main projects list sorted by date
    val allArchiveProjects: LiveData<List<Project>> = projectDao.getAllProjects()

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

    // --- Archive Methods (delegate to main project dao) ---
    fun deleteArchiveProject(project: Project) {
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

    fun getProjectSummary(projectId: Long): Flow<ProjectSummary> {
        return designDao.getDesignsForProjectFlow(projectId).map { designs ->
            val totalConcrete = designs.sumOf { it.concreteVolume }
            val totalSteel = designs.sumOf { it.steelWeight }
            val totalCost = designs.sumOf { it.totalCost }
            val breakdown = designs.groupBy { it.type.name }
                .mapValues { entry -> entry.value.sumOf { it.totalCost } }
            
            ProjectSummary(
                totalConcrete = totalConcrete,
                totalSteel = totalSteel,
                totalCost = totalCost,
                designCount = designs.size,
                costEfficiencyIndex = 1.0, // Placeholder
                costBreakdown = breakdown
            )
        }
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
            val inputData = JSONObject().apply {
                put("zone", result.zone)
                put("importance", result.importance)
                put("reductionFactor", result.reductionFactor)
                put("totalWeight", result.totalWeight)
                put("height", result.height)
                put("baseShear", result.baseShear)
                put("storyDrift", result.storyDrift)
                put("timePeriod", result.timePeriod)
                put("spectralAcceleration", result.spectralAcceleration)
            }.toString()
            val design = Design(
                projectId = projectId,
                type = DesignType.SEISMIC,
                name = name,
                inputData = inputData,
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