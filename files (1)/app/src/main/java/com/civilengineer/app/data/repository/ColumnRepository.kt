package com.civilengineer.app.data.repository

import com.civilengineer.app.data.database.dao.ColumnDesignDao
import com.civilengineer.app.data.models.ColumnDesign
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مستودع الأعمدة - يدير جميع العمليات المتعلقة بالأعمدة
 */
@Singleton
class ColumnRepository @Inject constructor(
    private val columnDao: ColumnDesignDao
) {

    suspend fun insertColumn(column: ColumnDesign): Long {
        return columnDao.insert(column)
    }

    suspend fun updateColumn(column: ColumnDesign) {
        columnDao.update(column)
    }

    suspend fun deleteColumn(column: ColumnDesign) {
        columnDao.delete(column)
    }

    suspend fun deleteColumnById(id: Int) {
        columnDao.deleteColumnById(id)
    }

    suspend fun getColumnById(id: Int): ColumnDesign? {
        return columnDao.getColumnById(id)
    }

    fun getColumnByIdFlow(id: Int): Flow<ColumnDesign?> {
        return columnDao.getColumnByIdFlow(id)
    }

    fun getAllColumns(): Flow<List<ColumnDesign>> {
        return columnDao.getAllColumns()
    }

    fun searchColumns(searchQuery: String): Flow<List<ColumnDesign>> {
        return columnDao.searchColumns(searchQuery)
    }

    fun getColumnsByCodeType(codeType: String): Flow<List<ColumnDesign>> {
        return columnDao.getColumnsByCodeType(codeType)
    }

    fun getColumnsBySafety(isSafe: Boolean): Flow<List<ColumnDesign>> {
        return columnDao.getColumnsBySafety(isSafe)
    }

    fun getColumnsCount(): Flow<Int> {
        return columnDao.getColumnsCount()
    }

    suspend fun getAverageSafetyFactor(): Double {
        return columnDao.getAverageSafetyFactor()
    }

    fun getRecentColumns(limit: Int = 10): Flow<List<ColumnDesign>> {
        return columnDao.getRecentColumns(limit)
    }
}