package com.civilengineer.app.data.database.dao

import androidx.room.*
import com.civilengineer.app.data.models.FootingDesign
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object لتصميم القواعد
 */
@Dao
interface FootingDesignDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(footing: FootingDesign): Long

    @Update
    suspend fun update(footing: FootingDesign)

    @Delete
    suspend fun delete(footing: FootingDesign)

    @Query("SELECT * FROM footings WHERE id = :id")
    suspend fun getFootingById(id: Int): FootingDesign?

    @Query("SELECT * FROM footings WHERE id = :id")
    fun getFootingByIdFlow(id: Int): Flow<FootingDesign?>

    @Query("SELECT * FROM footings ORDER BY created_date DESC")
    fun getAllFootings(): Flow<List<FootingDesign>>

    @Query("SELECT * FROM footings WHERE footing_name LIKE '%' || :search || '%' ORDER BY created_date DESC")
    fun searchFootings(search: String): Flow<List<FootingDesign>>

    @Query("SELECT * FROM footings WHERE code_type = :codeType ORDER BY created_date DESC")
    fun getFootingsByCodeType(codeType: String): Flow<List<FootingDesign>>

    @Query("SELECT * FROM footings WHERE footing_type = :footingType ORDER BY created_date DESC")
    fun getFootingsByType(footingType: String): Flow<List<FootingDesign>>

    @Query("DELETE FROM footings WHERE id = :id")
    suspend fun deleteFootingById(id: Int)

    @Query("SELECT COUNT(*) FROM footings")
    fun getFootingsCount(): Flow<Int>

    @Query("SELECT * FROM footings ORDER BY created_date DESC LIMIT :limit")
    fun getRecentFootings(limit: Int): Flow<List<FootingDesign>>
}