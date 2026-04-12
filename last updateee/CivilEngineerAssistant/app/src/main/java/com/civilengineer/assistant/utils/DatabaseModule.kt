package com.civilengineer.assistant.utils

import android.content.Context
import androidx.room.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

// ═══════════════════════════════════════════════════════════
// Entity - جدول المشاريع المحفوظة
// ═══════════════════════════════════════════════════════════

@Entity(tableName = "saved_projects")
data class SavedProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectName: String,
    val elementType: String,         // column, slab, foundation, beam, etc.
    val designCode: String,          // ECP, SBC, ACI
    val inputData: String,           // JSON string of inputs
    val resultData: String,          // JSON string of results
    val quantityData: String = "",   // JSON string of quantity survey
    val costData: String = "",       // JSON string of cost
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ═══════════════════════════════════════════════════════════
// Entity - جدول سجل الحسابات
// ═══════════════════════════════════════════════════════════

@Entity(tableName = "calculation_history")
data class CalculationHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val elementType: String,
    val summary: String,             // ملخص مختصر
    val isSafe: Boolean,
    val designCode: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ═══════════════════════════════════════════════════════════
// Entity - جدول الأسعار المخصصة
// ═══════════════════════════════════════════════════════════

@Entity(tableName = "custom_prices")
data class CustomPrice(
    @PrimaryKey val currencyCode: String,
    val concretePerM3: Double,
    val steelPerTon: Double,
    val formworkPerM2: Double,
    val laborPerM3: Double,
    val excavationPerM3: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)

// ═══════════════════════════════════════════════════════════
// DAO - واجهة الوصول لقاعدة البيانات
// ═══════════════════════════════════════════════════════════

@Dao
interface ProjectDao {

    // المشاريع
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: SavedProject): Long

    @Update
    suspend fun updateProject(project: SavedProject)

    @Delete
    suspend fun deleteProject(project: SavedProject)

    @Query("SELECT * FROM saved_projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<SavedProject>>

    @Query("SELECT * FROM saved_projects WHERE elementType = :type ORDER BY updatedAt DESC")
    fun getProjectsByType(type: String): Flow<List<SavedProject>>

    @Query("SELECT * FROM saved_projects WHERE id = :id")
    suspend fun getProjectById(id: Long): SavedProject?

    @Query("SELECT * FROM saved_projects WHERE projectName LIKE '%' || :query || '%'")
    fun searchProjects(query: String): Flow<List<SavedProject>>

    // سجل الحسابات
    @Insert
    suspend fun insertHistory(history: CalculationHistory)

    @Query("SELECT * FROM calculation_history ORDER BY timestamp DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<CalculationHistory>>

    @Query("DELETE FROM calculation_history WHERE timestamp < :before")
    suspend fun cleanOldHistory(before: Long)

    // الأسعار
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrice(price: CustomPrice)

    @Query("SELECT * FROM custom_prices WHERE currencyCode = :code")
    suspend fun getPrice(code: String): CustomPrice?

    @Query("SELECT * FROM custom_prices")
    fun getAllPrices(): Flow<List<CustomPrice>>
}

// ═══════════════════════════════════════════════════════════
// Database
// ═══════════════════════════════════════════════════════════

@Database(
    entities = [SavedProject::class, CalculationHistory::class, CustomPrice::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}

// ═══════════════════════════════════════════════════════════
// Hilt Module
// ═══════════════════════════════════════════════════════════

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "civil_engineer_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProjectDao(database: AppDatabase): ProjectDao {
        return database.projectDao()
    }
}
