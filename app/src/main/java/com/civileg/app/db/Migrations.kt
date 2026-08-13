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
            // No schema changes yet — this is a no-op migration
            // that establishes the safe migration pattern.
            // When version 7 introduces actual schema changes,
            // add the corresponding SQL statements here.
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
