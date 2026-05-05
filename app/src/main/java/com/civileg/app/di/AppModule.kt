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
    
    // Database from com.civileg.app.data.local.database
    @Provides
    @Singleton
    fun provideLocalDatabase(@ApplicationContext context: Context): com.civileg.app.data.local.database.AppDatabase {
        return Room.databaseBuilder(
            context,
            com.civileg.app.data.local.database.AppDatabase::class.java,
            "civil_engineer_db"
        ).build()
    }
    
    @Provides
    fun provideLocalProjectDao(database: com.civileg.app.data.local.database.AppDatabase): com.civileg.app.data.local.dao.ProjectDao {
        return database.projectDao()
    }

    // Database from com.civileg.app.db (Main database for designs)
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "civil_eg_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    @Provides
    fun provideProjectDao(database: AppDatabase): ProjectDao {
        return database.projectDao()
    }

    @Provides
    fun provideDesignDao(database: AppDatabase): DesignDao {
        return database.designDao()
    }

    @Provides
    fun provideFootingDao(database: AppDatabase): FootingDao {
        return database.footingDao()
    }

    @Provides
    fun provideColumnDao(database: AppDatabase): ColumnDao {
        return database.columnDao()
    }

    @Provides
    fun provideSlabDao(database: AppDatabase): SlabDao {
        return database.slabDao()
    }

    @Provides
    fun provideBeamDao(database: AppDatabase): BeamDao {
        return database.beamDao()
    }

    @Provides
    fun provideStairDao(database: AppDatabase): StairDao {
        return database.stairDao()
    }

    @Provides
    fun provideRetainingWallDao(database: AppDatabase): RetainingWallDao {
        return database.retainingWallDao()
    }

    @Provides
    fun provideTankDao(database: AppDatabase): TankDao {
        return database.tankDao()
    }

    @Provides
    fun provideMaterialDao(database: AppDatabase): MaterialDao {
        return database.materialDao()
    }

    @Provides
    fun provideInventoryDao(database: AppDatabase): InventoryDao {
        return database.inventoryDao()
    }
    
    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }
    
    @Provides
    @Singleton
    fun provideProjectRepository(
        projectDao: com.civileg.app.data.local.dao.ProjectDao,
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
