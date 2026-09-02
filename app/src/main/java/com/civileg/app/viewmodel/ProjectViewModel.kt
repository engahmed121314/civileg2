package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.*
import com.civileg.app.domain.*
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.BbsGenerator
import com.civileg.core.calculations.entities.ProjectSummary
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

    private val gson = Gson()

    /**
     * W12-FIX: Master BBS feed — derives bar-bending-schedule entries for every
     * stored RC design of the project (beam/column/footing) from its persisted
     * engine result JSON, instead of the previous hardwired emptyList().
     */
    fun getProjectBbs(projectId: Long): LiveData<List<com.civileg.app.utils.BbsEntry>> {
        // androidx.lifecycle.Transformations no longer exists in the current
        // lifecycle version; MediatorLiveData is the version-proof equivalent.
        val result = androidx.lifecycle.MediatorLiveData<List<com.civileg.app.utils.BbsEntry>>()
        result.addSource(designDao.getDesignsForProject(projectId)) { designs ->
            result.value = designs.flatMap { design -> bbsEntriesFor(design) }
        }
        return result
    }

    private fun bbsEntriesFor(design: Design): List<com.civileg.app.utils.BbsEntry> = try {
        val list = when (design.type) {
            DesignType.BEAM -> gson.fromJson(design.results, CalculatorEngine.BeamResult::class.java)
                ?.let { BbsGenerator.generateBeamBbs(design.name, it) }
            DesignType.COLUMN -> gson.fromJson(design.results, CalculatorEngine.ColumnResult::class.java)
                ?.let { BbsGenerator.generateColumnBbs(design.name, it) }
            DesignType.FOOTING -> gson.fromJson(design.results, CalculatorEngine.FootingResult::class.java)
                ?.let { BbsGenerator.generateFootingBbs(design.name, it) }
            DesignType.FLAT_SLAB -> gson.fromJson(design.results, FlatSlabResult::class.java)
                ?.let { BbsGenerator.generateFlatSlabBbs(design.name, it) }
            DesignType.WATER_TANK -> gson.fromJson(design.results, CalculatorEngine.TankResult::class.java)
                ?.let { BbsGenerator.generateTankBbs(design.name, it) }
            DesignType.RETAINING_WALL -> gson.fromJson(design.results, CalculatorEngine.RetainingWallResult::class.java)
                ?.let { BbsGenerator.generateRetainingWallBbs(design.name, it) }
            DesignType.PILE_FOUNDATION -> gson.fromJson(design.results, PileDesignResult::class.java)
                ?.let { BbsGenerator.generatePileFoundationBbs(design.name, it) }
            DesignType.SHEAR_WALL -> gson.fromJson(design.results, ShearWallResult::class.java)
                ?.let { BbsGenerator.generateShearWallBbs(design.name, it, 3000.0) }
            DesignType.SLAB, DesignType.FLAT_SLAB -> gson.fromJson(design.results, CalculatorEngine.SlabResult::class.java)
                ?.let { BbsGenerator.generateSlabBbs(design.name, it, 5.0, 5.0) }
            DesignType.STAIRCASE -> gson.fromJson(design.results, CalculatorEngine.StairResult::class.java)
                ?.let { BbsGenerator.generateStairBbs(design.name, it) }
            else -> emptyList()
        }
        list ?: emptyList()
    } catch (_: Exception) {
        emptyList()
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
                put("reductionFactor", result.responseReduction)
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