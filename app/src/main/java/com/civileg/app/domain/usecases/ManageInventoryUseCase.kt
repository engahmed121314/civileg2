package com.civileg.app.domain.usecases

import com.civileg.app.db.InventoryItem
import com.civileg.app.db.InventoryType
import com.civileg.app.domain.repository.DesignRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ManageInventoryUseCase @Inject constructor(
    private val designRepository: DesignRepository
) {
    fun getAllItems(): Flow<List<InventoryItem>> = designRepository.getAllInventoryItems()
    fun getItemsByType(type: InventoryType): Flow<List<InventoryItem>> = designRepository.getInventoryItemsByType(type)
    fun getLowStockItems(): Flow<List<InventoryItem>> = designRepository.getLowStockItems()
    suspend fun saveItem(item: InventoryItem) = designRepository.saveInventoryItem(item)
    suspend fun updateItem(item: InventoryItem) = designRepository.updateInventoryItem(item)
    suspend fun deleteItem(item: InventoryItem) = designRepository.deleteInventoryItem(item)
}
