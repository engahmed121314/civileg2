package com.civilengineer.app.data.repository

import com.civilengineer.app.data.database.dao.RetainingWallDao
import com.civilengineer.app.data.models.RetainingWall
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مستودع حوائط السند - يدير جميع العمليات المتعلقة بحوائط السند
 */
@Singleton
class RetainingWallRepository @Inject constructor(
    private val wallDao: RetainingWallDao
) {

    suspend fun insertWall(wall: RetainingWall): Long {
        return wallDao.insert(wall)
    }

    suspend fun updateWall(wall: RetainingWall) {
        wallDao.update(wall)
    }

    suspend fun deleteWall(wall: RetainingWall) {
        wallDao.delete(wall)
    }

    suspend fun deleteWallById(id: Int) {
        wallDao.deleteWallById(id)
    }

    suspend fun getWallById(id: Int): RetainingWall? {
        return wallDao.getWallById(id)
    }

    fun getWallByIdFlow(id: Int): Flow<RetainingWall?> {
        return wallDao.getWallByIdFlow(id)
    }

    fun getAllWalls(): Flow<List<RetainingWall>> {
        return wallDao.getAllWalls()
    }

    fun searchWalls(searchQuery: String): Flow<List<RetainingWall>> {
        return wallDao.searchWalls(searchQuery)
    }

    fun getWallsByCodeType(codeType: String): Flow<List<RetainingWall>> {
        return wallDao.getWallsByCodeType(codeType)
    }

    fun getWallsByType(wallType: String): Flow<List<RetainingWall>> {
        return wallDao.getWallsByType(wallType)
    }

    fun getWallsCount(): Flow<Int> {
        return wallDao.getWallsCount()
    }

    fun getRecentWalls(limit: Int = 10): Flow<List<RetainingWall>> {
        return wallDao.getRecentWalls(limit)
    }
}