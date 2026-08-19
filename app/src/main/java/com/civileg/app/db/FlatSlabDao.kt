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
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var projectId: Long = 0,
    var panelSpanX: Double = 6.0,
    var panelSpanY: Double = 6.0,
    var slabThickness: Double = 250.0,
    var dropPanelThickness: Double = 0.0,
    var dropPanelSizeX: Double = 0.0,
    var dropPanelSizeY: Double = 0.0,
    var columnSizeX: Double = 400.0,
    var columnSizeY: Double = 400.0,
    var hasDropPanel: Boolean = false,
    var hasShearReinforcement: Boolean = false,
    var deadLoad: Double = 5.0,
    var liveLoad: Double = 3.0,
    var fcu: Double = 30.0,
    var fy: Double = 400.0,
    var columnStripWidthX: Double = 0.0,
    var columnStripWidthY: Double = 0.0,
    var middleStripWidthX: Double = 0.0,
    var middleStripWidthY: Double = 0.0,
    var negMomentColStripX: Double = 0.0,
    var posMomentColStripX: Double = 0.0,
    var negMomentMidStripX: Double = 0.0,
    var posMomentMidStripX: Double = 0.0,
    var negMomentColStripY: Double = 0.0,
    var posMomentColStripY: Double = 0.0,
    var negMomentMidStripY: Double = 0.0,
    var posMomentMidStripY: Double = 0.0,
    var topReinColStripX: String = "{}",
    var botReinColStripX: String = "{}",
    var topReinMidStripX: String = "{}",
    var botReinMidStripX: String = "{}",
    var topReinColStripY: String = "{}",
    var botReinColStripY: String = "{}",
    var topReinMidStripY: String = "{}",
    var botReinMidStripY: String = "{}",
    var punchingShearStress: Double = 0.0,
    var punchingShearCapacity: Double = 0.0,
    var isPunchingSafe: Boolean = false,
    var deflection: Double = 0.0,
    var allowableDeflection: Double = 0.0,
    var isDeflectionSafe: Boolean = false,
    var isSafe: Boolean = false,
    var utilizationRatio: Double = 0.0,
    var concreteVolume: Double = 0.0,
    var steelWeight: Double = 0.0,
    var totalCost: Double = 0.0,
    var codeUsed: String = "ECP",
    var inputDataJson: String = "{}",
    var resultsJson: String = "{}",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)