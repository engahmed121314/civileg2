package com.civilengineer.app.data.database.dao

import androidx.room.*
import com.civilengineer.app.data.models.SlabDesign
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object لتصميم البلاطات
 */
@Dao
interface SlabDesignDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(slab: SlabDesign): Long

    @Update
    suspend fun update(slab: SlabDesign)

    @Delete
    suspend fun delete(slab: SlabDesign)

    @Query("SELECT * FROM slabs WHERE id = :id")
    suspend fun getSlabById(id: Int): SlabDesign?

    @Query("SELECT * FROM slabs WHERE id = :id")
    fun getSlabByIdFlow(id: Int): Flow<SlabDesign?>

    @Query("SELECT * FROM slabs ORDER BY created_date DESC")
    fun getAllSlabs(): Flow<List<SlabDesign>>

    @Query("SELECT * FROM slabs WHERE slab_name LIKE '%' || :search || '%' ORDER BY created_date DESC")
    fun searchSlabs(search: String): Flow<List<SlabDesign>>

    @Query("SELECT * FROM slabs WHERE code_type = :codeType ORDER BY created_date DESC")
    fun getSlabsByCodeType(codeType: String): Flow<List<SlabDesign>>

    @Query("SELECT * FROM slabs WHERE slab_type = :slabType ORDER BY created_date DESC")
    fun getSlabsByType(slabType: String): Flow<List<SlabDesign>>

    @Query("DELETE FROM slabs WHERE id = :id")
    suspend fun deleteSlabById(id: Int)

    @Query("SELECT COUNT(*) FROM slabs")
    fun getSlabsCount(): Flow<Int>

    @Query("SELECT * FROM slabs ORDER BY created_date DESC LIMIT :limit")
    fun getRecentSlabs(limit: Int): Flow<List<SlabDesign>>
}