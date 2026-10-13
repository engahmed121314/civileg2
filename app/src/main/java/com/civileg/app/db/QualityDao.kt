package com.civileg.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Daily trend data point for pass/fail rates over time.
 */
data class DailyTrend(
    val date: String, // YYYY-MM-DD
    val total: Int,
    val passed: Int,
    val failed: Int,
    val passRate: Double
)

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

    @Query(
        "SELECT date(createdAt/1000, 'unixepoch', 'localtime') AS date, " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN passed = 1 THEN 1 ELSE 0 END) AS passed, " +
            "SUM(CASE WHEN passed = 0 THEN 1 ELSE 0 END) AS failed, " +
            "CASE WHEN COUNT(*) > 0 THEN SUM(CASE WHEN passed = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*) ELSE 0 END AS passRate " +
            "FROM quality_records " +
            "GROUP BY date(createdAt/1000, 'unixepoch', 'localtime') " +
            "ORDER BY date DESC " +
            "LIMIT 30"
    )
    fun getDailyTrend(): Flow<List<DailyTrend>>

    // ==================================================================
    // Inspection & Test Plans (ITP)
    // ==================================================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItp(item: InspectionTestPlan): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItpList(items: List<InspectionTestPlan>)

    @Query("SELECT * FROM inspection_test_plans ORDER BY discipline, activity")
    fun getAllItp(): Flow<List<InspectionTestPlan>>

    @Query("SELECT * FROM inspection_test_plans WHERE discipline = :discipline ORDER BY activity")
    fun getItpByDiscipline(discipline: String): Flow<List<InspectionTestPlan>>

    @Query(
        "UPDATE inspection_test_plans SET status = :status, resultValue = :resultValue, " +
            "checkedBy = :checkedBy, remarks = :remarks WHERE id = :id"
    )
    suspend fun updateItpStatus(
        id: Long,
        status: String,
        resultValue: String,
        checkedBy: String,
        remarks: String
    )

    @Query("DELETE FROM inspection_test_plans WHERE id = :id")
    suspend fun deleteItp(id: Long)

    @Query("DELETE FROM inspection_test_plans")
    suspend fun deleteAllItp()

    // ==================================================================
    // Non-Conformity Reports (NCR)
    // ==================================================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNcr(ncr: NonConformity): Long

    @Query("SELECT * FROM non_conformities ORDER BY createdAt DESC")
    fun getAllNcr(): Flow<List<NonConformity>>

    @Query(
        "UPDATE non_conformities SET status = :status, correctiveAction = :correctiveAction, " +
            "closedBy = :closedBy WHERE id = :id"
    )
    suspend fun updateNcrStatus(
        id: Long,
        status: String,
        correctiveAction: String,
        closedBy: String
    )

    @Query("DELETE FROM non_conformities WHERE id = :id")
    suspend fun deleteNcr(id: Long)

    @Query("SELECT COUNT(*) FROM non_conformities WHERE status != 'closed'")
    fun countOpenNcr(): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM non_conformities " +
            "WHERE status != 'closed' AND discipline = :discipline AND elementId = :elementId"
    )
    suspend fun countOpenNcrFor(
        discipline: String,
        elementId: String
    ): Int
}
