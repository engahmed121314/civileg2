package com.civileg.app.db

import com.civileg.app.utils.CalculatorEngine
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DesignRepository: المستودع المركزي لإدارة كافة التصميمات الإنشائية.
 * تم دمج الحفظ في الجداول المتخصصة (للحصر) والجدول العام (للمشروع).
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

    suspend fun saveFootingDesign(projectId: Long, name: String, result: CalculatorEngine.FootingResult) {
        saveGeneralDesign(projectId, DesignType.FOOTING, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName)
        
        val footing = Footing(
            projectId = projectId, type = "Isolated", load = result.soilPressure, soilPressure = result.allowablePressure,
            fcu = 25.0, fy = 360.0, colWidth = 300.0, colDepth = 600.0,
            width = result.width, length = result.length, thickness = result.thickness,
            reinforcementBottom = result.reinforcementBottom.barString, reinforcementTop = null,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost
        )
        footingDao.insertFooting(footing)
    }

    suspend fun saveColumnDesign(projectId: Long, name: String, result: CalculatorEngine.ColumnResult) {
        saveGeneralDesign(projectId, DesignType.COLUMN, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName)
        
        val column = ColumnEntity(
            projectId = projectId, load = result.pu, fcu = 25.0, fy = 360.0,
            width = result.width, depth = result.depth,
            reinforcement = result.reinforcement.barString, ties = result.stirrups.description,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost
        )
        columnDao.insertColumn(column)
    }

    suspend fun saveBeamDesign(projectId: Long, name: String, result: CalculatorEngine.BeamResult) {
        saveGeneralDesign(projectId, DesignType.BEAM, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName)
        
        val beam = Beam(
            projectId = projectId, span = 5.0, load = result.appliedMoment, fcu = 25.0, fy = 360.0,
            width = result.width, depth = result.depth,
            reinforcement = result.reinforcementBottom.barString, stirrups = result.stirrups.description,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost
        )
        beamDao.insertBeam(beam)
    }

    suspend fun saveSlabDesign(projectId: Long, name: String, result: CalculatorEngine.SlabResult) {
        saveGeneralDesign(projectId, DesignType.SLAB, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName)
        
        val slab = Slab(
            projectId = projectId, type = result.type.name, spanX = 4.0, spanY = 5.0,
            thickness = result.thickness, load = result.totalLoad, fcu = 25.0, fy = 360.0,
            reinforcement = result.reinforcementMain.barString,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost
        )
        slabDao.insertSlab(slab)
    }

    suspend fun saveStairDesign(projectId: Long, name: String, result: CalculatorEngine.StairResult) {
        saveGeneralDesign(projectId, DesignType.STAIRCASE, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName)
        
        val stair = Stair(
            projectId = projectId, thickness = result.thickness, load = 5.0, fcu = 25.0, fy = 360.0,
            reinforcement = result.reinforcement.barString,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost
        )
        stairDao.insertStair(stair)
    }

    suspend fun saveTankDesign(projectId: Long, name: String, result: CalculatorEngine.TankResult) {
        saveGeneralDesign(projectId, DesignType.WATER_TANK, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName)
        
        val tank = Tank(
            projectId = projectId, length = result.length, width = result.width, height = result.height,
            wallThickness = result.wallThickness, baseThickness = result.baseThickness,
            reinforcement = result.wallReinforcement.barString,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost
        )
        tankDao.insertTank(tank)
    }

    suspend fun saveRetainingWallDesign(projectId: Long, name: String, result: CalculatorEngine.RetainingWallResult) {
        saveGeneralDesign(projectId, DesignType.RETAINING_WALL, name, result.isSafe, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName)
        
        val wall = RetainingWall(
            projectId = projectId, height = result.height, stemThickness = result.stemThickness,
            baseWidth = result.baseWidth, baseThickness = 500.0,
            reinforcement = result.stemReinforcement.barString,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost
        )
        retainingWallDao.insertRetainingWall(wall)
    }

    suspend fun saveSeismicDesign(projectId: Long, name: String, result: CalculatorEngine.SeismicResult) {
        saveGeneralDesign(projectId, DesignType.SEISMIC, name, result.isSafe, 0.0, 0.0, 0.0, result, "ECP-201")
    }

    private suspend fun saveGeneralDesign(
        projectId: Long, type: DesignType, name: String, isSafe: Boolean,
        concreteVolume: Double, steelWeight: Double, totalCost: Double, result: Any, codeUsed: String
    ) {
        val design = Design(
            projectId = projectId, type = type, name = name, inputData = "{}",
            results = gson.toJson(result), isSafe = isSafe, codeUsed = codeUsed,
            concreteVolume = concreteVolume, steelWeight = steelWeight, totalCost = totalCost
        )
        designDao.insertDesign(design)
    }

    fun getDesignsForProject(projectId: Long) = designDao.getDesignsForProject(projectId)
    fun searchDesigns(query: String): Flow<List<Design>> = designDao.searchDesigns("%$query%")
    suspend fun deleteDesign(design: Design) = designDao.deleteDesign(design)
    suspend fun getTotalCost(projectId: Long): Double = designDao.getTotalCostForProject(projectId) ?: 0.0
}
