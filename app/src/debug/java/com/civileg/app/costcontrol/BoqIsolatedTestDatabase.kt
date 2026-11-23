package com.civileg.app.costcontrol

import androidx.room.*
import com.civileg.app.db.Project
import com.civileg.app.db.Converters
import com.civileg.app.db.costcontrol.*

/** Debug-only verification database, never bound to production AppDatabase or DI. */
@Dao interface BoqTestProjectDao {
    @Insert suspend fun insert(project:Project)
    @Query("DELETE FROM projects WHERE id=:id") suspend fun delete(id:Long)
}
@Database(entities=[Project::class,CostBoqRevisionEntity::class,CostBoqItemEntity::class,CostBoqHeadEntity::class],version=1,exportSchema=false)
@TypeConverters(Converters::class)
abstract class BoqIsolatedTestDatabase:RoomDatabase() {
    abstract fun boq():CostBoqDao
    abstract fun projects():BoqTestProjectDao
}
