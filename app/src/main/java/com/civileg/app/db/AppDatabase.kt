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
        InventoryItem::class
    ],
    version = 6,
    exportSchema = false
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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
