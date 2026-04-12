package com.civilengineer.app.di

import android.content.Context
import com.civilengineer.app.data.database.AppDatabase
import com.civilengineer.app.data.repository.ColumnRepository
import com.civilengineer.app.data.repository.SlabRepository
import com.civilengineer.app.data.repository.FootingRepository
import com.civilengineer.app.data.repository.RetainingWallRepository
import com.civilengineer.app.domain.calculator.*
import com.civilengineer.app.domain.export.PDFExporter
import com.civilengineer.app.domain.export.ExcelExporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency Injection Module
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideColumnRepository(database: AppDatabase): ColumnRepository {
        return ColumnRepository(database.columnDesignDao())
    }

    @Provides
    @Singleton
    fun provideSlabRepository(database: AppDatabase): SlabRepository {
        return SlabRepository(database.slabDesignDao())
    }

    @Provides
    @Singleton
    fun provideFootingRepository(database: AppDatabase): FootingRepository {
        return FootingRepository(database.footingDesignDao())
    }

    @Provides
    @Singleton
    fun provideRetainingWallRepository(database: AppDatabase): RetainingWallRepository {
        return RetainingWallRepository(database.retainingWallDao())
    }

    @Provides
    @Singleton
    fun provideColumnCalculator(): ColumnCalculator {
        return ColumnCalculator()
    }

    @Provides
    @Singleton
    fun provideSlabCalculator(): SlabCalculator {
        return SlabCalculator()
    }

    @Provides
    @Singleton
    fun provideFootingCalculator(): FootingCalculator {
        return FootingCalculator()
    }

    @Provides
    @Singleton
    fun provideRetainingWallCalculator(): RetainingWallCalculator {
        return RetainingWallCalculator()
    }

    @Provides
    @Singleton
    fun provideCostCalculator(): CostCalculator {
        return CostCalculator()
    }

    @Provides
    @Singleton
    fun providePDFExporter(@ApplicationContext context: Context): PDFExporter {
        return PDFExporter(context)
    }

    @Provides
    @Singleton
    fun provideExcelExporter(@ApplicationContext context: Context): ExcelExporter {
        return ExcelExporter(context)
    }
}