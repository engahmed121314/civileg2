package com.civilengineer.app.data.repository

import com.civilengineer.app.data.database.dao.SlabDesignDao
import com.civilengineer.app.data.models.SlabDesign
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مستودع البلاطات - يدير جميع العمليات المتعلقة بالبلاطات
 */
@Singleton
class SlabRepository @Inject constructor(
    private val slabDao: SlabDesignDao
) {

    suspend fun insertSlab(slab: SlabDesign): Long {
        return slabDao.insert(slab)
    }

    suspend fun updateSlab(slab: SlabDesign) {
        slabDao.update(slab)
    }

    suspend fun deleteSlab(slab: SlabDesign) {
        slabDao.delete(slab)
    }

    suspend fun deleteSlabById(id: Int) {
        slabDao.deleteSlabById(id)
    }

    suspend fun getSlabById(id: Int): SlabDesign? {
        return slabDao.getSlabById(id)
    }

    fun getSlabByIdFlow(id: Int): Flow<SlabDesign?> {
        return slabDao.getSlabByIdFlow(id)
    }

    fun getAllSlabs(): Flow<List<SlabDesign>> {
        return slabDao.getAllSlabs()
    }

    fun searchSlabs(searchQuery: String): Flow<List<SlabDesign>> {
        return slabDao.searchSlabs(searchQuery)
    }

    fun getSlabsByCodeType(codeType: String): Flow<List<SlabDesign>> {
        return slabDao.getSlabsByCodeType(codeType)
    }

    fun getSlabsByType(slabType: String): Flow<List<SlabDesign>> {
        return slabDao.getSlabsByType(slabType)
    }

    fun getSlabsCount(): Flow<Int> {
        return slabDao.getSlabsCount()
    }

    fun getRecentSlabs(limit: Int = 10): Flow<List<SlabDesign>> {
        return slabDao.getRecentSlabs(limit)
    }
}