package com.civilengineer.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.civilengineer.app.data.models.ColumnDesign
import com.civilengineer.app.data.models.SlabDesign
import com.civilengineer.app.data.models.FootingDesign
import com.civilengineer.app.data.models.RetainingWall
import com.civilengineer.app.data.database.dao.*

/**
 * قاعدة البيانات الرئيسية للتطبيق
 */
@Database(
    entities = [
        ColumnDesign::class,
        SlabDesign::class,
        FootingDesign::class,
        RetainingWall::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun columnDesignDao(): ColumnDesignDao
    abstract fun slabDesignDao(): SlabDesignDao
    abstract fun footingDesignDao(): FootingDesignDao
    abstract fun retainingWallDao(): RetainingWallDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "civil_engineer_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}