package com.civileg.app.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database Migrations
 * Every schema change MUST have a corresponding migration to preserve user data.
 * To add a new migration: increment version in AppDatabase, add Migration_X_Y here,
 * and add .addMigrations(MIGRATION_X_Y) in both AppDatabase companion and AppModule.
 */
object Migrations {

    /**
     * Placeholder migration from version 6 to 7.
     * This establishes the migration pattern. When the schema actually changes,
     * replace this with real ALTER TABLE statements.
     * 
     * Example for adding a column:
     *   database.execSQL("ALTER TABLE Beam ADD COLUMN newColumn REAL NOT NULL DEFAULT 0.0")
     * 
     * Example for creating a new table:
     *   database.execSQL("""
     *       CREATE TABLE IF NOT EXISTS NewEntity (
     *           id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
     *           name TEXT
     *       )
     *   """)
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // No schema changes between v6 and v7 — no-op migration
        }
    }

    /**
     * Migration from version 7 to 8.
     * Adds projectId indexes to all child entity tables for O(log n) lookups.
     * Also adds designId index on site_inspections.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add projectId indexes for all child tables
            val tablesWithProjectId = listOf(
                "designs", "footings", "columns_table", "slabs", "beams",
                "stairs", "retaining_walls", "tanks", "materials", "pour_logs", "site_inspections"
            )
            for (table in tablesWithProjectId) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_projectId ON $table(projectId)")
            }
            // Add designId index on site_inspections
            database.execSQL("CREATE INDEX IF NOT EXISTS index_site_inspections_designId ON site_inspections(designId)")
        }
    }

    /**
     * Helper to create a no-op migration between consecutive versions.
     * Use only when no schema changes occurred between versions.
     */
    fun noOpMigration(from: Int, to: Int) = object : Migration(from, to) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // No-op: schema unchanged between these versions
        }
    }
}
