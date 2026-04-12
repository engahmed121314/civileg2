package com.civileg.app.db

import androidx.room.*

@Dao
interface ColumnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertColumn(column: ColumnEntity): Long

    @Query("SELECT * FROM columns_table WHERE projectId = :projectId")
    suspend fun getColumnsForProject(projectId: Long): List<ColumnEntity>

    @Delete
    suspend fun deleteColumn(column: ColumnEntity)
}
