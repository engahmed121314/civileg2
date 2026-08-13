package com.civileg.app.data.repository

import com.civileg.app.db.*
import com.civileg.app.domain.entities.SteelMemberResult
import com.civileg.app.domain.entities.SteelWarehouseAnalysisResult
import com.civileg.app.domain.repository.DesignRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow

/**
 * Decoupled implementation of [DesignRepository].
 * Accepts `Any` result types and extracts common fields via Gson serialization,
 * eliminating the dependency on CalculatorEngine.*Result types.
 */
class DesignRepositoryImpl(
    private val designDao: DesignDao,
    private val footingDao: FootingDao,
    private val columnDao: ColumnDao,
    private val slabDao: SlabDao,
    private val beamDao: BeamDao,
    private val stairDao: StairDao,
    private val retainingWallDao: RetainingWallDao,
    private val tankDao: TankDao,
    private val inventoryDao: InventoryDao
) : DesignRepository {

    private val gson = Gson()

    // ── Field extraction helper ──────────────────────────────────────────

    private data class ResultFields(
        val isSafe: Boolean = false,
        val utilizationRatio: Double = 0.0,
        val concreteVolume: Double = 0.0,
        val steelWeight: Double = 0.0,
        val cost: Double = 0.0,
        val width: Double = 0.0,
        val depth: Double = 0.0,
        val thickness: Double = 0.0,
        val length: Double = 0.0,
        val height: Double = 0.0,
        val wallThickness: Double = 0.0,
        val baseThickness: Double = 0.0,
        val baseWidth: Double = 0.0,
        val stemThickness: Double = 0.0,
        val reinforcement: String = "",
        val reinforcementBottom: String = "",
        val reinforcementMain: String = "",
        val wallReinforcement: String = "",
        val stemReinforcement: String = "",
        val stirrups: String = "",
        val ties: String = "",
        val type: String = "",
        val soilPressure: Double = 0.0,
        val allowablePressure: Double = 0.0,
        val load: Double = 0.0,
        val pu: Double = 0.0,
        val span: Double = 0.0,
        val totalLoad: Double = 0.0,
        val appliedMoment: Double = 0.0
    )

    private fun extractFields(result: Any): ResultFields {
        val json = gson.toJson(result)
        return gson.fromJson(json, ResultFields::class.java)
    }

    // ── Inventory ────────────────────────────────────────────────────────

    override fun getAllInventoryItems(): Flow<List<InventoryItem>> = inventoryDao.getAllItems()
    override fun getInventoryItemsByType(type: InventoryType): Flow<List<InventoryItem>> = inventoryDao.getItemsByType(type)
    override suspend fun saveInventoryItem(item: InventoryItem) = inventoryDao.insertItem(item)
    override suspend fun updateInventoryItem(item: InventoryItem) = inventoryDao.updateItem(item)
    override suspend fun deleteInventoryItem(item: InventoryItem) = inventoryDao.deleteItem(item)
    override fun getLowStockItems(): Flow<List<InventoryItem>> = inventoryDao.getLowStockItems()

    // ── General Designs ──────────────────────────────────────────────────

    override fun getDesignsForProject(projectId: Long) = designDao.getDesignsForProject(projectId)
    override fun searchDesigns(query: String): Flow<List<Design>> = designDao.searchDesigns("%$query%")
    override suspend fun deleteDesign(design: Design) = designDao.deleteDesign(design)
    override suspend fun getTotalCost(projectId: Long): Double = designDao.getTotalCostForProject(projectId) ?: 0.0

    // ── Concrete Element Designs ─────────────────────────────────────────

    override suspend fun saveFootingDesign(projectId: Long, name: String, result: Any, codeUsed: String) {
        val f = extractFields(result)
        saveGeneralDesign(projectId, DesignType.FOOTING, name, f.isSafe, f.utilizationRatio,
            f.concreteVolume, f.steelWeight, f.cost, result, codeUsed)
        val footing = Footing(
            projectId = projectId, type = "Isolated", load = f.soilPressure, soilPressure = f.allowablePressure,
            fcu = 25.0, fy = 360.0, colWidth = 300.0, colDepth = 600.0,
            width = f.width, length = f.length, thickness = f.thickness,
            reinforcementBottom = f.reinforcementBottom, reinforcementTop = null,
            concreteVolume = f.concreteVolume, steelWeight = f.steelWeight, cost = f.cost,
            utilizationRatio = f.utilizationRatio
        )
        footingDao.insertFooting(footing)
    }

    override suspend fun saveColumnDesign(projectId: Long, name: String, result: Any, codeUsed: String) {
        val f = extractFields(result)
        saveGeneralDesign(projectId, DesignType.COLUMN, name, f.isSafe, f.utilizationRatio,
            f.concreteVolume, f.steelWeight, f.cost, result, codeUsed)
        val column = ColumnEntity(
            projectId = projectId, load = f.pu, fcu = 25.0, fy = 360.0,
            width = f.width, depth = f.depth,
            reinforcement = f.reinforcement, ties = f.ties,
            concreteVolume = f.concreteVolume, steelWeight = f.steelWeight, cost = f.cost,
            utilizationRatio = f.utilizationRatio
        )
        columnDao.insertColumn(column)
    }

    override suspend fun saveBeamDesign(projectId: Long, name: String, result: Any, codeUsed: String) {
        val f = extractFields(result)
        saveGeneralDesign(projectId, DesignType.BEAM, name, f.isSafe, f.utilizationRatio,
            f.concreteVolume, f.steelWeight, f.cost, result, codeUsed)
        val beam = Beam(
            projectId = projectId, span = 5.0, load = f.appliedMoment, fcu = 25.0, fy = 360.0,
            width = f.width, depth = f.depth,
            reinforcement = f.reinforcementBottom, stirrups = f.stirrups,
            concreteVolume = f.concreteVolume, steelWeight = f.steelWeight, cost = f.cost,
            utilizationRatio = f.utilizationRatio
        )
        beamDao.insertBeam(beam)
    }

    override suspend fun saveSlabDesign(projectId: Long, name: String, result: Any, codeUsed: String) {
        val f = extractFields(result)
        saveGeneralDesign(projectId, DesignType.SLAB, name, f.isSafe, f.utilizationRatio,
            f.concreteVolume, f.steelWeight, f.cost, result, codeUsed)
        val slab = Slab(
            projectId = projectId, type = f.type.ifEmpty { "Solid" }, spanX = 4.0, spanY = 5.0,
            thickness = f.thickness, load = f.totalLoad, fcu = 25.0, fy = 360.0,
            reinforcement = f.reinforcementMain,
            concreteVolume = f.concreteVolume, steelWeight = f.steelWeight, cost = f.cost,
            utilizationRatio = f.utilizationRatio
        )
        slabDao.insertSlab(slab)
    }

    override suspend fun saveStairDesign(projectId: Long, name: String, result: Any, codeUsed: String) {
        val f = extractFields(result)
        saveGeneralDesign(projectId, DesignType.STAIRCASE, name, f.isSafe, f.utilizationRatio,
            f.concreteVolume, f.steelWeight, f.cost, result, codeUsed)
        val stair = Stair(
            projectId = projectId, thickness = f.thickness, load = 5.0, fcu = 25.0, fy = 360.0,
            reinforcement = f.reinforcement,
            concreteVolume = f.concreteVolume, steelWeight = f.steelWeight, cost = f.cost,
            utilizationRatio = f.utilizationRatio
        )
        stairDao.insertStair(stair)
    }

    override suspend fun saveTankDesign(projectId: Long, name: String, result: Any, codeUsed: String) {
        val f = extractFields(result)
        saveGeneralDesign(projectId, DesignType.WATER_TANK, name, f.isSafe, f.utilizationRatio,
            f.concreteVolume, f.steelWeight, f.cost, result, codeUsed)
        val tank = Tank(
            projectId = projectId, length = f.length, width = f.width, height = f.height,
            wallThickness = f.wallThickness, baseThickness = f.baseThickness,
            reinforcement = f.wallReinforcement,
            concreteVolume = f.concreteVolume, steelWeight = f.steelWeight, cost = f.cost,
            utilizationRatio = f.utilizationRatio
        )
        tankDao.insertTank(tank)
    }

    override suspend fun saveRetainingWallDesign(projectId: Long, name: String, result: Any, codeUsed: String) {
        val f = extractFields(result)
        saveGeneralDesign(projectId, DesignType.RETAINING_WALL, name, f.isSafe, f.utilizationRatio,
            f.concreteVolume, f.steelWeight, f.cost, result, codeUsed)
        val wall = RetainingWall(
            projectId = projectId, height = f.height, stemThickness = f.stemThickness,
            baseWidth = f.baseWidth, baseThickness = 500.0,
            reinforcement = f.stemReinforcement,
            concreteVolume = f.concreteVolume, steelWeight = f.steelWeight, cost = f.cost,
            utilizationRatio = f.utilizationRatio
        )
        retainingWallDao.insertRetainingWall(wall)
    }

    override suspend fun saveSeismicDesign(projectId: Long, name: String, result: Any, codeUsed: String) {
        val f = extractFields(result)
        saveGeneralDesign(projectId, DesignType.SEISMIC, name, f.isSafe, 0.0, 0.0, 0.0, 0.0, result, codeUsed)
    }

    // ── Steel Designs ────────────────────────────────────────────────────

    override suspend fun saveSteelMemberDesign(projectId: Long, name: String, result: SteelMemberResult) {
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
            codeUsed = "Steel Code"
        )
    }

    override suspend fun saveSteelWarehouseDesign(projectId: Long, name: String, result: SteelWarehouseAnalysisResult) {
        saveGeneralDesign(
            projectId = projectId,
            type = DesignType.STEEL_WAREHOUSE,
            name = name,
            isSafe = result.safetyStatus,
            utilizationRatio = 0.0,
            concreteVolume = 0.0,
            steelWeight = result.totalWeight * 1000.0,
            totalCost = result.totalWeight * 50000.0,
            result = result,
            codeUsed = "Steel Code"
        )
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private suspend fun saveGeneralDesign(
        projectId: Long, type: DesignType, name: String, isSafe: Boolean, utilizationRatio: Double,
        concreteVolume: Double, steelWeight: Double, totalCost: Double, result: Any, codeUsed: String
    ) {
        val design = Design(
            projectId = projectId, type = type, name = name, inputData = "{}",
            results = gson.toJson(result), isSafe = isSafe, utilizationRatio = utilizationRatio, codeUsed = codeUsed,
            concreteVolume = concreteVolume, steelWeight = steelWeight, totalCost = totalCost
        )
        designDao.insertDesign(design)
    }
}
