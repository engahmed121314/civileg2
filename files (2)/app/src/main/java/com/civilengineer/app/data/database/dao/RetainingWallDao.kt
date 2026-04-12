package com.civilengineer.app.data.database.dao

import androidx.room.*
import com.civilengineer.app.data.models.RetainingWall
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object لتصميم حوائط السند
 */
@Dao
interface RetainingWallDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wall: RetainingWall): Long

    @Update
    suspend fun update(wall: RetainingWall)

    @Delete
    suspend fun delete(wall: RetainingWall)

    @Query("SELECT * FROM retaining_walls WHERE id = :id")
    suspend fun getWallById(id: Int): RetainingWall?

    @Query("SELECT * FROM retaining_walls WHERE id = :id")
    fun getWallByIdFlow(id: Int): Flow<RetainingWall?>

    @Query("SELECT * FROM retaining_walls ORDER BY created_date DESC")
    fun getAllWalls(): Flow<List<RetainingWall>>

    @Query("SELECT * FROM retaining_walls WHERE wall_name LIKE '%' || :search || '%' ORDER BY created_date DESC")
    fun searchWalls(search: String): Flow<List<RetainingWall>>

    @Query("SELECT * FROM retaining_walls WHERE code_type = :codeType ORDER BY created_date DESC")
    fun getWallsByCodeType(codeType: String): Flow<List<RetainingWall>>

    @Query("SELECT * FROM retaining_walls WHERE wall_type = :wallType ORDER BY created_date DESC")
    fun getWallsByType(wallType: String): Flow<List<RetainingWall>>

    @Query("DELETE FROM retaining_walls WHERE id = :id")
    suspend fun deleteWallById(id: Int)

    @Query("SELECT COUNT(*) FROM retaining_walls")
    fun getWallsCount(): Flow<Int>

    @Query("SELECT * FROM retaining_walls ORDER BY created_date DESC LIMIT :limit")
    fun getRecentWalls(limit: Int): Flow<List<RetainingWall>>
}