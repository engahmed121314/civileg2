package com.civileg.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FlatSlabDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(design: FlatSlabDesignEntity)

    @Update
    suspend fun update(design: FlatSlabDesignEntity)

    @Delete
    suspend fun delete(design: FlatSlabDesignEntity)

    @Query("SELECT * FROM flat_slabs WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getByProject(projectId: Long): Flow<List<FlatSlabDesignEntity>>

    @Query("SELECT * FROM flat_slabs ORDER BY createdAt DESC")
    fun getAll(): Flow<List<FlatSlabDesignEntity>>

    @Query("DELETE FROM flat_slabs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM flat_slabs")
    suspend fun count(): Int
}

@Entity(tableName = "flat_slabs", indices = [Index("projectId")])
data class FlatSlabDesignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long = 0,
    val panelSpanX: Double = 6.0,
    val panelSpanY: Double = 6.0,
    val slabThickness: Double = 250.0,
    val dropPanelThickness: Double = 0.0,
    val dropPanelSizeX: Double = 0.0,
    val dropPanelSizeY: Double = 0.0,
    val columnSizeX: Double = 400.0,
    val columnSizeY: Double = 400.0,
    val hasDropPanel: Boolean = false,
    val hasShearReinforcement: Boolean = false,
    val deadLoad: Double = 5.0,
    val liveLoad: Double = 3.0,
    val fcu: Double = 30.0,
    val fy: Double = 400.0,
    val columnStripWidthX: Double = 0.0,
    val columnStripWidthY: Double = 0.0,
    val middleStripWidthX: Double = 0.0,
    val middleStripWidthY: Double = 0.0,
    val negMomentColStripX: Double = 0.0,
    val posMomentColStripX: Double = 0.0,
    val negMomentMidStripX: Double = 0.0,
    val posMomentMidStripX: Double = 0.0,
    val negMomentColStripY: Double = 0.0,
    val posMomentColStripY: Double = 0.0,
    val negMomentMidStripY: Double = 0.0,
    val posMomentMidStripY: Double = 0.0,
    val topReinColStripX: String = "{}",
    val botReinColStripX: String = "{}",
    val topReinMidStripX: String = "{}",
    val botReinMidStripX: String = "{}",
    val topReinColStripY: String = "{}",
    val botReinColStripY: String = "{}",
    val topReinMidStripY: String = "{}",
    val botReinMidStripY: String = "{}",
    val punchingShearStress: Double = 0.0,
    val punchingShearCapacity: Double = 0.0,
    val isPunchingSafe: Boolean = false,
    deflection: Double = 0.0,
    val allowableDeflection: Double = 0.0,
    val isDeflectionSafe: Boolean = false,
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