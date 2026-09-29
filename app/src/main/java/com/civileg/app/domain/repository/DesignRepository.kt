package com.civileg.app.domain.repository

import com.civileg.app.db.Design
import com.civileg.app.db.InventoryItem
import com.civileg.app.db.InventoryType
import com.civileg.app.domain.entities.SteelMemberResult
import com.civileg.app.domain.entities.SteelWarehouseAnalysisResult
import kotlinx.coroutines.flow.Flow

/**
 * Interface for managing structural design persistence.
 * Implemented by [com.civileg.app.data.repository.DesignRepositoryImpl].
 */
interface DesignRepository {
    // --- Inventory ---
    fun getAllInventoryItems(): Flow<List<InventoryItem>>
    fun getInventoryItemsByType(type: InventoryType): Flow<List<InventoryItem>>
    suspend fun saveInventoryItem(item: InventoryItem)
    suspend fun updateInventoryItem(item: InventoryItem)
    suspend fun deleteInventoryItem(item: InventoryItem)
    fun getLowStockItems(): Flow<List<InventoryItem>>

    // --- General Designs ---
    fun getDesignsForProject(projectId: Long): Flow<List<Design>>
    fun searchDesigns(query: String): Flow<List<Design>>
    suspend fun deleteDesign(design: Design)
    suspend fun getTotalCost(projectId: Long): Double

    // --- Concrete Element Designs (accepts Any result, serialized internally) ---
    suspend fun saveFootingDesign(projectId: Long, name: String, result: Any, codeUsed: String)
    suspend fun saveColumnDesign(projectId: Long, name: String, result: Any, codeUsed: String)
    suspend fun saveBeamDesign(projectId: Long, name: String, result: Any, codeUsed: String)
    suspend fun saveSlabDesign(projectId: Long, name: String, result: Any, codeUsed: String)
    suspend fun saveStairDesign(projectId: Long, name: String, result: Any, codeUsed: String)
    suspend fun saveTankDesign(projectId: Long, name: String, result: Any, codeUsed: String)
    suspend fun saveRetainingWallDesign(projectId: Long, name: String, result: Any, codeUsed: String)
    suspend fun saveSteelMemberDesign(projectId: Long, name: String, result: SteelMemberResult)
    suspend fun saveSteelWarehouseDesign(projectId: Long, name: String, result: SteelWarehouseAnalysisResult)
    suspend fun saveSeismicDesign(projectId: Long, name: String, result: Any, codeUsed: String)
}
