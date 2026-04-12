package com.civilengineer.app.data.database.dao

import androidx.room.*
import com.civilengineer.app.data.models.ColumnDesign
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object لتصميم الأعمدة
 */
@Dao
interface ColumnDesignDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(column: ColumnDesign): Long

    @Update
    suspend fun update(column: ColumnDesign)

    @Delete
    suspend fun delete(column: ColumnDesign)

    @Query("SELECT * FROM columns WHERE id = :id")
    suspend fun getColumnById(id: Int): ColumnDesign?

    @Query("SELECT * FROM columns WHERE id = :id")
    fun getColumnByIdFlow(id: Int): Flow<ColumnDesign?>

    @Query("SELECT * FROM columns ORDER BY created_date DESC")
    fun getAllColumns(): Flow<List<ColumnDesign>>

    @Query("SELECT * FROM columns WHERE column_name LIKE '%' || :search || '%' ORDER BY created_date DESC")
    fun searchColumns(search: String): Flow<List<ColumnDesign>>

    @Query("SELECT * FROM columns WHERE code_type = :codeType ORDER BY created_date DESC")
    fun getColumnsByCodeType(codeType: String): Flow<List<ColumnDesign>>

    @Query("SELECT * FROM columns WHERE is_safe = :isSafe ORDER BY created_date DESC")
    fun getColumnsBySafety(isSafe: Boolean): Flow<List<ColumnDesign>>

    @Query("DELETE FROM columns WHERE id = :id")
    suspend fun deleteColumnById(id: Int)

    @Query("SELECT COUNT(*) FROM columns")
    fun getColumnsCount(): Flow<Int>

    @Query("SELECT AVG(safety_factor) FROM columns")
    suspend fun getAverageSafetyFactor(): Double

    @Query("SELECT * FROM columns ORDER BY created_date DESC LIMIT :limit")
    fun getRecentColumns(limit: Int): Flow<List<ColumnDesign>>
}