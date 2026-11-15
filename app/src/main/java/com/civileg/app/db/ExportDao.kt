package com.civileg.app.db

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * بوابة الوصول لبيانات تصدير المخرجات (PDF/DXF) في قاعدة بيانات Room.
 */
@Dao
interface ExportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExport(export: ExportEntity): Long

    @Query("SELECT * FROM exports WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getExportsForProject(projectId: Long): LiveData<List<ExportEntity>>

    @Query("SELECT * FROM exports ORDER BY createdAt DESC")
    fun getAllExports(): LiveData<List<ExportEntity>>

    // One-shot (suspend) variants of the two queries above. LiveData can only be read
    // through an observer on the main thread, which deadlocks in unit tests; these
    // return the same rows synchronously for tests and for off-main-thread callers.
    @Query("SELECT * FROM exports WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun getExportsForProjectOnce(projectId: Long): List<ExportEntity>

    @Query("SELECT * FROM exports ORDER BY createdAt DESC")
    suspend fun getAllExportsOnce(): List<ExportEntity>

    @Query("DELETE FROM exports WHERE id = :exportId")
    suspend fun deleteExport(exportId: Long)

    @Query("DELETE FROM exports")
    suspend fun deleteAllExports()
}
