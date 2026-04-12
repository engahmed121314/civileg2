package com.civileg.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.civileg.app.data.local.dao.ProjectDao
import com.civileg.app.data.local.entities.ProjectEntity

@Database(
    entities = [ProjectEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun projectDao(): ProjectDao
}
