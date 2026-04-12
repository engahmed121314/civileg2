package com.civilengineer.app.data.repository

import com.civilengineer.app.data.database.dao.FootingDesignDao
import com.civilengineer.app.data.models.FootingDesign
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مستودع القواعد - يدير جميع العمليات المتعلقة بالقواعد
 */
@Singleton
class FootingRepository @Inject constructor(
    private val footingDao: FootingDesignDao
) {

    suspend fun insertFooting(footing: FootingDesign): Long {
        return footingDao.insert(footing)
    }

    suspend fun updateFooting(footing: FootingDesign) {
        footingDao.update(footing)
    }

    suspend fun deleteFooting(footing: FootingDesign) {
        footingDao.delete(footing)
    }

    suspend fun deleteFootingById(id: Int) {
        footingDao.deleteFootingById(id)
    }

    suspend fun getFootingById(id: Int): FootingDesign? {
        return footingDao.getFootingById(id)
    }

    fun getFootingByIdFlow(id: Int): Flow<FootingDesign?> {
        return footingDao.getFootingByIdFlow(id)
    }

    fun getAllFootings(): Flow<List<FootingDesign>> {
        return footingDao.getAllFootings()
    }

    fun searchFootings(searchQuery: String): Flow<List<FootingDesign>> {
        return footingDao.searchFootings(searchQuery)
    }

    fun getFootingsByCodeType(codeType: String): Flow<List<FootingDesign>> {
        return footingDao.getFootingsByCodeType(codeType)
    }

    fun getFootingsByType(footingType: String): Flow<List<FootingDesign>> {
        return footingDao.getFootingsByType(footingType)
    }

    fun getFootingsCount(): Flow<Int> {
        return footingDao.getFootingsCount()
    }

    fun getRecentFootings(limit: Int = 10): Flow<List<FootingDesign>> {
        return footingDao.getRecentFootings(limit)
    }
}