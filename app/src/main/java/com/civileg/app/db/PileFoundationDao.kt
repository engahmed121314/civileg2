package com.civileg.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PileFoundationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(design: PileFoundationDesignEntity)

    @Update
    suspend fun update(design: PileFoundationDesignEntity)

    @Delete
    suspend fun delete(design: PileFoundationDesignEntity)

    @Query("SELECT * FROM pile_foundations WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getByProject(projectId: Long): Flow<List<PileFoundationDesignEntity>>

    @Query("SELECT * FROM pile_foundations ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PileFoundationDesignEntity>>

    @Query("DELETE FROM pile_foundations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM pile_foundations")
    suspend fun count(): Int
}

@Entity(tableName = "pile_foundations", indices = [Index("projectId")])
data class PileFoundationDesignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long = 0,
    val pileType: String = "BORED",
    val pileDiameter: Double = 600.0,
    val pileLength: Double = 15.0,
    val numberOfPiles: Int = 4,
    val axialLoad: Double = 2000.0,
    val lateralLoad: Double = 0.0,
    val momentLoad: Double = 0.0,
    val fcu: Double = 30.0,
    val fy: Double = 400.0,
    val soilType: String = "CLAY",
    val cu: Double = 50.0,
    val phi: Double = 30.0,
    val ultimateCapacity: Double = 0.0,
    val allowableCapacity: Double = 0.0,
    val shaftResistance: Double = 0.0,
    val endBearingResistance: Double = 0.0,
    val settlement: Double = 0.0,
    val isSafe: Boolean = false,
    val utilizationRatio: Double = 0.0,
    val concreteVolume: Double = 0.0,
    val steelWeight: Double = 0.0,
    val totalCost: Double = 0.0,
    val codeUsed: String = "ECP",
    val inputDataJson: String = "{}",
    val resultsJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)