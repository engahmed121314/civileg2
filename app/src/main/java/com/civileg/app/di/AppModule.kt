package com.civileg.app.di

import android.content.Context
import androidx.room.Room
import com.civileg.app.data.local.PreferencesManager
import com.civileg.app.db.*
import com.civileg.app.data.repository.ProjectRepositoryImpl
import com.civileg.app.domain.repository.ProjectRepository
import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // قاعدة البيانات الرئيسية (الوحيدة)
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "civil_eg_database"
        )
        // Tier-1 data safety: explicit migration chain instead of the blanket
        // destructive fallback that wiped user data on every schema bump.
        .addMigrations(
            Migrations.MIGRATION_6_7,
            Migrations.MIGRATION_7_8
        )
        // Ancient pre-migration-era installs (v<=5) have no upgrade path;
        // destruction is bounded to those versions only.
        .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
        .build()
    }

    @Provides
    fun provideProjectDao(database: AppDatabase): ProjectDao = database.projectDao()

    @Provides
    fun provideDesignDao(database: AppDatabase): DesignDao = database.designDao()

    @Provides
    fun provideFootingDao(database: AppDatabase): FootingDao = database.footingDao()

    @Provides
    fun provideColumnDao(database: AppDatabase): ColumnDao = database.columnDao()

    @Provides
    fun provideSlabDao(database: AppDatabase): SlabDao = database.slabDao()

    @Provides
    fun provideBeamDao(database: AppDatabase): BeamDao = database.beamDao()

    @Provides
    fun provideStairDao(database: AppDatabase): StairDao = database.stairDao()

    @Provides
    fun provideRetainingWallDao(database: AppDatabase): RetainingWallDao = database.retainingWallDao()

    @Provides
    fun provideTankDao(database: AppDatabase): TankDao = database.tankDao()

    @Provides
    fun provideMaterialDao(database: AppDatabase): MaterialDao = database.materialDao()

    @Provides
    fun provideInventoryDao(database: AppDatabase): InventoryDao = database.inventoryDao()

    @Provides
    fun provideConstructionDao(database: AppDatabase): ConstructionDao = database.constructionDao()

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideProjectRepository(
        projectDao: ProjectDao,
        preferencesManager: PreferencesManager
    ): ProjectRepository {
        return ProjectRepositoryImpl(projectDao, preferencesManager)
    }

    @Provides
    @Singleton
    fun provideComprehensivePdfExporter(@ApplicationContext context: Context): ComprehensivePdfExporter {
        return ComprehensivePdfExporter(context)
    }
}
