package com.civileg.app.db

import com.civileg.app.utils.CalculatorEngine
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DesignRepository: المستودع المركزي لإدارة كافة التصميمات الإنشائية.
 * تم ابتكار حلول احترافية لدمج الحصر الشامل والتكلفة مع المحرك المطور.
 */
@Singleton
class DesignRepository @Inject constructor(
    private val designDao: DesignDao,
    private val footingDao: FootingDao,
    private val columnDao: ColumnDao,
    private val slabDao: SlabDao,
    private val beamDao: BeamDao,
    private val stairDao: StairDao,
    private val retainingWallDao: RetainingWallDao,
    private val tankDao: TankDao
) {
    private val gson = Gson()

    suspend fun saveFootingDesign(
        projectId: Long,
        name: String,
        result: CalculatorEngine.FootingResult,
        codeUsed: String = "ECP"
    ) {
        saveGeneralDesign(projectId, DesignType.FOOTING, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, codeUsed)
    }

    suspend fun saveBeamDesign(
        projectId: Long,
        name: String,
        result: CalculatorEngine.BeamResult,
        codeUsed: String = "ECP"
    ) {
        saveGeneralDesign(projectId, DesignType.BEAM, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, codeUsed)
    }

    suspend fun saveColumnDesign(
        projectId: Long,
        name: String,
        result: CalculatorEngine.ColumnResult,
        codeUsed: String = "ECP"
    ) {
        saveGeneralDesign(projectId, DesignType.COLUMN, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, codeUsed)
    }

    suspend fun saveSlabDesign(
        projectId: Long,
        name: String,
        result: CalculatorEngine.SlabResult,
        codeUsed: String = "ECP"
    ) {
        saveGeneralDesign(projectId, DesignType.SLAB, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, codeUsed)
    }

    suspend fun saveStairDesign(projectId: Long, name: String, result: CalculatorEngine.StairResult) {
        saveGeneralDesign(projectId, DesignType.STAIRCASE, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName)
    }

    suspend fun saveTankDesign(projectId: Long, name: String, result: CalculatorEngine.TankResult) {
        saveGeneralDesign(projectId, DesignType.WATER_TANK, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName)
    }

    suspend fun saveRetainingWallDesign(projectId: Long, name: String, result: CalculatorEngine.RetainingWallResult) {
        saveGeneralDesign(projectId, DesignType.RETAINING_WALL, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName)
    }

    suspend fun saveSeismicDesign(projectId: Long, name: String, result: CalculatorEngine.SeismicResult) {
        saveGeneralDesign(projectId, DesignType.SEISMIC, name, result.isSafe, 0.0, 0.0, 0.0, result, "ASCE/SBC")
    }

    private suspend fun saveGeneralDesign(
        projectId: Long,
        type: DesignType,
        name: String,
        isSafe: Boolean,
        concreteVolume: Double,
        steelWeight: Double,
        totalCost: Double,
        result: Any,
        codeUsed: String
    ) {
        val design = Design(
            projectId = projectId,
            type = type,
            name = name,
            inputData = "{}",
            results = gson.toJson(result),
            isSafe = isSafe,
            codeUsed = codeUsed,
            concreteVolume = concreteVolume,
            steelWeight = steelWeight,
            totalCost = totalCost
        )
        designDao.insertDesign(design)
    }

    fun getDesignsForProject(projectId: Long) = designDao.getDesignsForProject(projectId)
    suspend fun deleteDesign(design: Design) = designDao.deleteDesign(design)
    suspend fun getTotalCost(projectId: Long): Double = designDao.getTotalCostForProject(projectId) ?: 0.0
}
