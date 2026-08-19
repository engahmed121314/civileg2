package com.civileg.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShearWallDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(design: ShearWallDesignEntity)

    @Update
    suspend fun update(design: ShearWallDesignEntity)

    @Delete
    suspend fun delete(design: ShearWallDesignEntity)

    @Query("SELECT * FROM shear_walls WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getByProject(projectId: Long): Flow<List<ShearWallDesignEntity>>

    @Query("SELECT * FROM shear_walls ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ShearWallDesignEntity>>

    @Query("DELETE FROM shear_walls WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM shear_walls")
    suspend fun count(): Int
}

@Entity(tableName = "shear_walls", indices = [Index("projectId")])
data class ShearWallDesignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long = 0,
    val wallLength: Double = 3000.0,
    val wallHeight: Double = 3000.0,
    val wallThickness: Double = 250.0,
    val wallType: String = "COUPLED",
    val hasBoundaryElement: Boolean = true,
    val beLength: Double = 400.0,
    val beThickness: Double = 400.0,
    val axialLoad: Double = 0.0,
    val shearForce: Double = 0.0,
    val bendingMoment: Double = 0.0,
    val fcu: Double = 30.0,
    val fy: Double = 400.0,
    val verticalReinforcement: String = "{}",
    val horizontalReinforcement: String = "{}",
    val boundaryVerticalReinforcement: String = "{}",
    val boundaryTransverseReinforcement: String = "{}",
    val webVerticalReinRatio: Double = 0.0,
    val webHorizontalReinRatio: Double = 0.0,
    val concreteShearCapacity: Double = 0.0,
    val steelShearCapacity: Double = 0.0,
    val totalShearCapacity: Double = 0.0,
    val appliedShearStress: Double = 0.0,
    val isShearSafe: Boolean = false,
    val axialCapacity: Double = 0.0,
    val momentCapacity: Double = 0.0,
    val isFlexureSafe: Boolean = false,
    val driftRatio: Double = 0.0,
    val allowableDriftRatio: Double = 0.0,
    val isDriftSafe: Boolean = false,
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