package com.civileg.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Aggregated pass/fail counts per discipline, returned by [QualityDao.getCategorySummary].
 */
data class CategorySummary(
    val category: String,
    val total: Int,
    val passedCount: Int
)

/**
 * DAO for the Quality Engineer module ([QualityRecord]).
 *
 * Backs the "Quality Reports" tab with reactive statistics and provides
 * persistence for every discipline check.
 */
@Dao
interface QualityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: QualityRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<QualityRecord>)

    @Query("SELECT * FROM quality_records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<QualityRecord>>

    @Query("SELECT * FROM quality_records WHERE category = :category ORDER BY createdAt DESC")
    fun getRecordsByCategory(category: String): Flow<List<QualityRecord>>

    @Query("SELECT COUNT(*) FROM quality_records")
    fun countAll(): Flow<Int>

    @Query("SELECT COUNT(*) FROM quality_records WHERE passed = 1")
    fun countPassed(): Flow<Int>

    @Query(
        "SELECT category AS category, COUNT(*) AS total, " +
            "SUM(CASE WHEN passed = 1 THEN 1 ELSE 0 END) AS passedCount " +
            "FROM quality_records GROUP BY category ORDER BY total DESC"
    )
    fun getCategorySummary(): Flow<List<CategorySummary>>

    @Query("DELETE FROM quality_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM quality_records")
    suspend fun deleteAll()
}
