package com.civileg.app.db

import com.civileg.app.domain.entities.SteelMemberResult
import com.civileg.app.domain.entities.SteelWarehouseAnalysisResult
import com.civileg.app.utils.CalculatorEngine
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
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
    private val tankDao: TankDao,
    private val inventoryDao: InventoryDao
) {
    private val gson = Gson()

    // --- Inventory Operations ---
    fun getAllInventoryItems(): Flow<List<InventoryItem>> = inventoryDao.getAllItems()
    fun getInventoryItemsByType(type: InventoryType): Flow<List<InventoryItem>> = inventoryDao.getItemsByType(type)
    suspend fun saveInventoryItem(item: InventoryItem) = inventoryDao.insertItem(item)
    suspend fun updateInventoryItem(item: InventoryItem) = inventoryDao.updateItem(item)
    suspend fun deleteInventoryItem(item: InventoryItem) = inventoryDao.deleteItem(item)
    fun getLowStockItems(): Flow<List<InventoryItem>> = inventoryDao.getLowStockItems()

    suspend fun saveFootingDesign(
        projectId: Long, name: String, result: CalculatorEngine.FootingResult,
        fcu: Double = 25.0, fy: Double = 360.0
    ) {
        val inputData = JSONObject().apply {
            put("fcu", fcu)
            put("fy", fy)
            put("load", result.soilPressure)
            put("soilPressure", result.soilPressure)
            put("allowablePressure", result.allowablePressure)
            put("colWidth", result.column1Size.first)
            put("colDepth", result.column1Size.second)
        }.toString()
        saveGeneralDesign(projectId, DesignType.FOOTING, name, result.isSafe, result.utilizationRatio, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName, inputData)
        
        val footing = Footing(
            projectId = projectId, type = "Isolated", load = result.soilPressure, soilPressure = result.allowablePressure,
            fcu = fcu, fy = fy, colWidth = result.column1Size.first, colDepth = result.column1Size.second,
            width = result.width, length = result.length, thickness = result.thickness,
            reinforcementBottom = result.reinforcementBottom.barString, reinforcementTop = null,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost,
            utilizationRatio = result.utilizationRatio
        )
        footingDao.insertFooting(footing)
    }

    suspend fun saveColumnDesign(
        projectId: Long, name: String, result: CalculatorEngine.ColumnResult,
        fcu: Double = 25.0, fy: Double = 360.0
    ) {
        val inputData = JSONObject().apply {
            put("fcu", fcu)
            put("fy", fy)
            put("load", result.pu)
            put("width", result.width)
            put("depth", result.depth)
        }.toString()
        saveGeneralDesign(projectId, DesignType.COLUMN, name, result.isSafe, result.utilizationRatio, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName, inputData)
        
        val column = ColumnEntity(
            projectId = projectId, load = result.pu, fcu = fcu, fy = fy,
            width = result.width, depth = result.depth,
            reinforcement = result.reinforcement.barString, ties = result.stirrups.description,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost,
            utilizationRatio = result.utilizationRatio
        )
        columnDao.insertColumn(column)
    }

    suspend fun saveBeamDesign(
        projectId: Long, name: String, result: CalculatorEngine.BeamResult,
        span: Double = 5.0, fcu: Double = 25.0, fy: Double = 360.0
    ) {
        val inputData = JSONObject().apply {
            put("span", span)
            put("fcu", fcu)
            put("fy", fy)
            put("width", result.width)
            put("depth", result.depth)
            put("load", result.appliedMoment)
            put("supportType", result.supportType.name)
        }.toString()
        saveGeneralDesign(projectId, DesignType.BEAM, name, result.isSafe, result.utilizationRatio, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName, inputData)
        
        val beam = Beam(
            projectId = projectId, span = span, load = result.appliedMoment, fcu = fcu, fy = fy,
            width = result.width, depth = result.depth,
            reinforcement = result.reinforcementBottom.barString, stirrups = result.stirrups.description,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost,
            utilizationRatio = result.utilizationRatio
        )
        beamDao.insertBeam(beam)
    }

    suspend fun saveSlabDesign(
        projectId: Long, name: String, result: CalculatorEngine.SlabResult,
        spanX: Double = 4.0, spanY: Double = 5.0, fcu: Double = 25.0, fy: Double = 360.0
    ) {
        val inputData = JSONObject().apply {
            put("spanX", spanX)
            put("spanY", spanY)
            put("fcu", fcu)
            put("fy", fy)
            put("thickness", result.thickness)
            put("load", result.totalLoad)
            put("type", result.type.name)
        }.toString()
        saveGeneralDesign(projectId, DesignType.SLAB, name, result.isSafe, result.utilizationRatio, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName, inputData)
        
        val slab = Slab(
            projectId = projectId, type = result.type.name, spanX = spanX, spanY = spanY,
            thickness = result.thickness, load = result.totalLoad, fcu = fcu, fy = fy,
            reinforcement = result.reinforcementMain.barString,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost,
            utilizationRatio = result.utilizationRatio
        )
        slabDao.insertSlab(slab)
    }

    suspend fun saveStairDesign(projectId: Long, name: String, result: CalculatorEngine.StairResult) {
        // StairResult already contains fcu, fy, span, riser, tread directly
        val inputData = JSONObject().apply {
            put("fcu", result.fcu)
            put("fy", result.fy)
            put("span", result.span)
            put("riser", result.riser)
            put("tread", result.tread)
            put("thickness", result.thickness)
            put("wu", result.wu)
            put("type", result.type.name)
        }.toString()
        saveGeneralDesign(projectId, DesignType.STAIRCASE, name, result.isSafe, result.utilizationRatio, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName, inputData)
        
        val stair = Stair(
            projectId = projectId, thickness = result.thickness, load = result.wu, fcu = result.fcu, fy = result.fy,
            reinforcement = result.reinforcement.barString,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost,
            utilizationRatio = result.utilizationRatio
        )
        stairDao.insertStair(stair)
    }

    suspend fun saveTankDesign(projectId: Long, name: String, result: CalculatorEngine.TankResult) {
        // TankResult already contains fcu, fy directly
        val inputData = JSONObject().apply {
            put("fcu", result.fcu)
            put("fy", result.fy)
            put("length", result.length)
            put("width", result.width)
            put("height", result.height)
            put("wallThickness", result.wallThickness)
            put("baseThickness", result.baseThickness)
        }.toString()
        saveGeneralDesign(projectId, DesignType.WATER_TANK, name, result.isSafe, result.utilizationRatio, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName, inputData)
        
        val tank = Tank(
            projectId = projectId, length = result.length, width = result.width, height = result.height,
            wallThickness = result.wallThickness, baseThickness = result.baseThickness,
            reinforcement = result.wallReinforcement.barString,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost,
            utilizationRatio = result.utilizationRatio
        )
        tankDao.insertTank(tank)
    }

    suspend fun saveRetainingWallDesign(projectId: Long, name: String, result: CalculatorEngine.RetainingWallResult) {
        // RetainingWallResult already contains fcu, fy, soilDensity, backfillAngle directly
        val inputData = JSONObject().apply {
            put("fcu", result.fcu)
            put("fy", result.fy)
            put("height", result.height)
            put("stemThickness", result.stemThickness)
            put("baseWidth", result.baseWidth)
            put("baseThickness", 500.0)
            put("soilDensity", result.soilDensity)
            put("backfillAngle", result.backfillAngle)
        }.toString()
        saveGeneralDesign(projectId, DesignType.RETAINING_WALL, name, result.isSafe, result.utilizationRatio, result.concreteVolume, result.steelWeight, result.cost, result, result.code.displayName, inputData)
        
        val wall = RetainingWall(
            projectId = projectId, height = result.height, stemThickness = result.stemThickness,
            baseWidth = result.baseWidth, baseThickness = 500.0,
            reinforcement = result.stemReinforcement.barString,
            concreteVolume = result.concreteVolume, steelWeight = result.steelWeight, cost = result.cost,
            utilizationRatio = result.utilizationRatio
        )
        retainingWallDao.insertRetainingWall(wall)
    }

    suspend fun saveSteelMemberDesign(projectId: Long, name: String, result: SteelMemberResult) {
        val inputData = JSONObject().apply {
            put("sectionType", result.sectionType.sectionName)
            put("memberType", result.memberType.name)
            put("weight", result.weight)
        }.toString()
        saveGeneralDesign(
            projectId = projectId,
            type = DesignType.STEEL_MEMBER,
            name = name,
            isSafe = result.isSafe,
            utilizationRatio = result.utilizationRatio,
            concreteVolume = 0.0,
            steelWeight = result.weight,
            totalCost = result.cost,
            result = result,
            codeUsed = "Steel Code",
            inputData = inputData
        )
    }

    suspend fun saveSteelWarehouseDesign(projectId: Long, name: String, result: SteelWarehouseAnalysisResult) {
        val inputData = JSONObject().apply {
            put("totalWeight", result.totalWeight)
            put("totalCladdingArea", result.totalCladdingArea)
            put("safetyStatus", result.safetyStatus)
        }.toString()
        saveGeneralDesign(
            projectId = projectId,
            type = DesignType.STEEL_WAREHOUSE,
            name = name,
            isSafe = result.safetyStatus,
            utilizationRatio = 0.0, // Warehouse usually has multiple ratios, using 0.0 for general
            concreteVolume = 0.0,
            steelWeight = result.totalWeight * 1000.0, // Convert Tons to Kg
            totalCost = result.totalWeight * 50000.0, // Estimated cost per ton
            result = result,
            codeUsed = "Steel Code",
            inputData = inputData
        )
    }

    suspend fun saveSeismicDesign(projectId: Long, name: String, result: CalculatorEngine.SeismicResult) {
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
        saveGeneralDesign(projectId, DesignType.SEISMIC, name, result.isSafe, 0.0, 0.0, 0.0, 0.0, result, result.code.displayName, inputData)
    }

    private suspend fun saveGeneralDesign(
        projectId: Long, type: DesignType, name: String, isSafe: Boolean, utilizationRatio: Double,
        concreteVolume: Double, steelWeight: Double, totalCost: Double, result: Any, codeUsed: String,
        inputData: String = "{}"
    ) {
        val design = Design(
            projectId = projectId, type = type, name = name, inputData = inputData,
            results = gson.toJson(result), isSafe = isSafe, utilizationRatio = utilizationRatio, codeUsed = codeUsed,
            concreteVolume = concreteVolume, steelWeight = steelWeight, totalCost = totalCost
        )
        designDao.insertDesign(design)
    }

    fun getDesignsForProject(projectId: Long) = designDao.getDesignsForProject(projectId)
    fun searchDesigns(query: String): Flow<List<Design>> = designDao.searchDesigns("%$query%")
    suspend fun deleteDesign(design: Design) = designDao.deleteDesign(design)
    suspend fun getTotalCost(projectId: Long): Double = designDao.getTotalCostForProject(projectId) ?: 0.0

    suspend fun saveFrameAnalysisDesign(
        projectId: Long, name: String,
        nodes: List<Any>, members: List<Any>,
        nodalLoads: List<Any>, memberLoads: List<Any>,
        result: Any,
        designCode: String
    ) {
        val inputData = JSONObject().apply {
            put("nodes", gson.toJson(nodes))
            put("members", gson.toJson(members))
            put("nodalLoads", gson.toJson(nodalLoads))
            put("memberLoads", gson.toJson(memberLoads))
        }.toString()
        saveGeneralDesign(
            projectId = projectId,
            type = DesignType.FRAME_ANALYSIS,
            name = name,
            isSafe = true, // Frame analysis itself doesn't have a single safe/unsafe flag
            utilizationRatio = 0.0,
            concreteVolume = 0.0,
            steelWeight = 0.0,
            totalCost = 0.0,
            result = result,
            codeUsed = designCode,
            inputData = inputData
        )
    }
}
