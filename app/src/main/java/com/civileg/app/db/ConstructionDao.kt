package com.civileg.app.db

import androidx.room.*
import androidx.lifecycle.LiveData

@Dao
interface ConstructionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPourLog(log: PourLog): Long

    @Query("SELECT * FROM pour_logs WHERE projectId = :projectId ORDER BY date DESC")
    fun getPourLogsForProject(projectId: Long): LiveData<List<PourLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSiteInspection(inspection: SiteInspection): Long

    @Query("SELECT * FROM site_inspections WHERE projectId = :projectId ORDER BY date DESC")
    fun getInspectionsForProject(projectId: Long): LiveData<List<SiteInspection>>
}
