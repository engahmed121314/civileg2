package com.civileg.app.viewmodel

import android.content.Context
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

import com.civileg.app.data.ProjectSyncManager
import androidx.lifecycle.MutableLiveData
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.civileg.app.domain.calculations.InputGuard

@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    private val designDao: DesignDao,
    private val materialDao: MaterialDao,
    private val syncManager: ProjectSyncManager
) : ViewModel() {

    private val _syncStatus = MutableLiveData<Boolean?>(null)
    val syncStatus: LiveData<Boolean?> = _syncStatus

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun syncData() {
        viewModelScope.launch {
            try {
                val success = syncManager.syncAllProjects()
                _syncStatus.value = success
            } catch (e: Exception) {
                _errorMessage.value = "Sync failed: ${e.message ?: "Unknown error"} / فشل المزامنة"
                _syncStatus.value = false
            }
        }
    }

    fun resetSyncStatus() {
        _syncStatus.value = null
    }

    // Archive Projects — reuse main projects list sorted by date
    val allArchiveProjects: LiveData<List<Project>> = projectDao.getAllProjects()

    // Main Project Containers
    val allProjects: LiveData<List<Project>> = projectDao.getAllProjects()
    val activeProjectCount: LiveData<Int> = projectDao.getActiveProjectCount()

    // Designs
    val allDesigns: LiveData<List<Design>> = designDao.getAllDesigns()

    // --- Main Project Methods ---
    fun insert(project: Project) {
        InputGuard.notNull("project", project)

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
                costEfficiencyIndex = 1.0,
                costBreakdown = breakdown
            )
        }
    }

    fun exportProjectSummaryToPdf(context: Context, projectId: Long, projectName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val designsList = designDao.getDesignsForProjectList(projectId)
                if (designsList.isEmpty()) return@launch
                
                val totalConcrete = designsList.sumOf { it.concreteVolume }
                val totalSteel = designsList.sumOf { it.steelWeight }
                val totalCost = designsList.sumOf { it.totalCost }
                val breakdown = designsList.groupBy { it.type.name }
                    .mapValues { entry -> entry.value.sumOf { d -> d.totalCost } }
                
                val summary = ProjectSummary(totalConcrete, totalSteel, totalCost, designsList.size, 1.0, breakdown)
                
                val fileName = "ProjectSummary_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)
                
                val exported = ComprehensivePdfExporter(context)
                    .exportProjectBatchReport(projectName, designsList, summary, file.absolutePath)

                withContext(Dispatchers.Main) {
                    exported?.let { ExportUtils.openPdf(context, it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "PDF export failed: ${e.message ?: "Unknown error"} / فشل تصدير PDF"
                }
            }
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