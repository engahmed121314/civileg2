package com.civileg.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Project::class, 
        Design::class, 
        MaterialItem::class, 
        Footing::class,
        ColumnEntity::class,
        Slab::class,
        Beam::class,
        Stair::class,
        RetainingWall::class,
        Tank::class,
        InventoryItem::class,
        PourLog::class,
        SiteInspection::class,
        FlatSlabDesignEntity::class,
        PileFoundationDesignEntity::class,
        ShearWallDesignEntity::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun projectDao(): ProjectDao
    abstract fun designDao(): DesignDao
    abstract fun materialDao(): MaterialDao
    abstract fun footingDao(): FootingDao
    abstract fun columnDao(): ColumnDao
    abstract fun slabDao(): SlabDao
    abstract fun beamDao(): BeamDao
    abstract fun stairDao(): StairDao
    abstract fun retainingWallDao(): RetainingWallDao
    abstract fun tankDao(): TankDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun constructionDao(): ConstructionDao
    abstract fun flatSlabDao(): FlatSlabDao
    abstract fun pileFoundationDao(): PileFoundationDao
    abstract fun shearWallDao(): ShearWallDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "civil_eg_database"
                )
                // ADR-001: Strict data persistence — no destructive migration in production.
                // Every schema change MUST be handled by Migrations.kt chain.
                .addMigrations(
                    Migrations.MIGRATION_6_7,
                    Migrations.MIGRATION_7_8
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
