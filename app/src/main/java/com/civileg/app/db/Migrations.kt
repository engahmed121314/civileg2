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
     * Migration from version 8 to 9.
     * Adds ForeignKey constraints (CASCADE delete from Project, SET NULL on Design).
     * Since SQLite ALTER TABLE doesn't support adding FK constraints to existing tables,
     * we must recreate each table with FK, copy data, then swap.
     * Also makes site_inspections.designId nullable to support SET NULL.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Enable foreign key enforcement
            database.execSQL("PRAGMA foreign_keys = OFF")

            // ── designs ──
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS designs_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    name TEXT NOT NULL,
                    inputData TEXT NOT NULL,
                    results TEXT NOT NULL,
                    isSafe INTEGER NOT NULL,
                    utilizationRatio REAL NOT NULL DEFAULT 0.0,
                    codeUsed TEXT NOT NULL,
                    concreteVolume REAL NOT NULL DEFAULT 0.0,
                    steelWeight REAL NOT NULL DEFAULT 0.0,
                    totalCost REAL NOT NULL DEFAULT 0.0,
                    createdAt INTEGER,
                    FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE
                )
            """)
            database.execSQL("INSERT INTO designs_new SELECT * FROM designs")
            database.execSQL("DROP TABLE designs")
            database.execSQL("ALTER TABLE designs_new RENAME TO designs")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_designs_projectId ON designs(projectId)")

            // ── footings ──
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS footings_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    type TEXT NOT NULL, load REAL NOT NULL, soilPressure REAL NOT NULL,
                    fcu REAL NOT NULL, fy REAL NOT NULL, colWidth REAL NOT NULL, colDepth REAL NOT NULL,
                    width REAL NOT NULL, length REAL NOT NULL, thickness REAL NOT NULL,
                    reinforcementBottom TEXT NOT NULL, reinforcementTop TEXT,
                    concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL,
                    utilizationRatio REAL NOT NULL DEFAULT 0.0,
                    FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE
                )
            """)
            database.execSQL("INSERT INTO footings_new SELECT * FROM footings")
            database.execSQL("DROP TABLE footings")
            database.execSQL("ALTER TABLE footings_new RENAME TO footings")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_footings_projectId ON footings(projectId)")

            // ── columns_table ──
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS columns_table_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    load REAL NOT NULL, fcu REAL NOT NULL, fy REAL NOT NULL,
                    width REAL NOT NULL, depth REAL NOT NULL,
                    reinforcement TEXT NOT NULL, ties TEXT NOT NULL,
                    concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL,
                    utilizationRatio REAL NOT NULL DEFAULT 0.0,
                    FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE
                )
            """)
            database.execSQL("INSERT INTO columns_table_new SELECT * FROM columns_table")
            database.execSQL("DROP TABLE columns_table")
            database.execSQL("ALTER TABLE columns_table_new RENAME TO columns_table")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_columns_table_projectId ON columns_table(projectId)")

            // ── slabs ──
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS slabs_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    type TEXT NOT NULL, spanX REAL NOT NULL, spanY REAL NOT NULL,
                    thickness REAL NOT NULL, load REAL NOT NULL, fcu REAL NOT NULL, fy REAL NOT NULL,
                    reinforcement TEXT NOT NULL,
                    concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL,
                    utilizationRatio REAL NOT NULL DEFAULT 0.0,
                    FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE
                )
            """)
            database.execSQL("INSERT INTO slabs_new SELECT * FROM slabs")
            database.execSQL("DROP TABLE slabs")
            database.execSQL("ALTER TABLE slabs_new RENAME TO slabs")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_slabs_projectId ON slabs(projectId)")

            // ── beams ──
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS beams_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    span REAL NOT NULL, load REAL NOT NULL, fcu REAL NOT NULL, fy REAL NOT NULL,
                    width REAL NOT NULL, depth REAL NOT NULL,
                    reinforcement TEXT NOT NULL, stirrups TEXT NOT NULL,
                    concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL,
                    utilizationRatio REAL NOT NULL DEFAULT 0.0,
                    FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE
                )
            """)
            database.execSQL("INSERT INTO beams_new SELECT * FROM beams")
            database.execSQL("DROP TABLE beams")
            database.execSQL("ALTER TABLE beams_new RENAME TO beams")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_beams_projectId ON beams(projectId)")

            // ── stairs ──
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS stairs_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    thickness REAL NOT NULL, load REAL NOT NULL, fcu REAL NOT NULL, fy REAL NOT NULL,
                    reinforcement TEXT NOT NULL,
                    concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL,
                    utilizationRatio REAL NOT NULL DEFAULT 0.0,
                    FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE
                )
            """)
            database.execSQL("INSERT INTO stairs_new SELECT * FROM stairs")
            database.execSQL("DROP TABLE stairs")
            database.execSQL("ALTER TABLE stairs_new RENAME TO stairs")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_stairs_projectId ON stairs(projectId)")

            // ── retaining_walls ──
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS retaining_walls_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    height REAL NOT NULL, stemThickness REAL NOT NULL, baseWidth REAL NOT NULL,
                    baseThickness REAL NOT NULL, reinforcement TEXT NOT NULL,
                    concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL,
                    utilizationRatio REAL NOT NULL DEFAULT 0.0,
                    FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE
                )
            """)
            database.execSQL("INSERT INTO retaining_walls_new SELECT * FROM retaining_walls")
            database.execSQL("DROP TABLE retaining_walls")
            database.execSQL("ALTER TABLE retaining_walls_new RENAME TO retaining_walls")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_retaining_walls_projectId ON retaining_walls(projectId)")

            // ── tanks ──
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS tanks_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    length REAL NOT NULL, width REAL NOT NULL, height REAL NOT NULL,
                    wallThickness REAL NOT NULL, baseThickness REAL NOT NULL,
                    reinforcement TEXT NOT NULL,
                    concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL,
                    utilizationRatio REAL NOT NULL DEFAULT 0.0,
                    FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE
                )
            """)
            database.execSQL("INSERT INTO tanks_new SELECT * FROM tanks")
            database.execSQL("DROP TABLE tanks")
            database.execSQL("ALTER TABLE tanks_new RENAME TO tanks")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_tanks_projectId ON tanks(projectId)")

            // ── materials ──
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS materials_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    name TEXT NOT NULL, unit TEXT NOT NULL, quantity REAL NOT NULL,
                    unitPrice REAL NOT NULL, totalPrice REAL NOT NULL,
                    category TEXT NOT NULL, createdAt INTEGER,
                    FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE
                )
            """)
            database.execSQL("INSERT INTO materials_new SELECT * FROM materials")
            database.execSQL("DROP TABLE materials")
            database.execSQL("ALTER TABLE materials_new RENAME TO materials")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_materials_projectId ON materials(projectId)")

            // ── pour_logs ──
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS pour_logs_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    elementId TEXT NOT NULL, date INTEGER, volumeM3 REAL NOT NULL,
                    slumpMm REAL NOT NULL, truckId TEXT NOT NULL, sampleId TEXT NOT NULL,
                    strength7Days REAL NOT NULL DEFAULT 0.0, strength28Days REAL NOT NULL DEFAULT 0.0,
                    status TEXT NOT NULL,
                    FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE
                )
            """)
            database.execSQL("INSERT INTO pour_logs_new SELECT * FROM pour_logs")
            database.execSQL("DROP TABLE pour_logs")
            database.execSQL("ALTER TABLE pour_logs_new RENAME TO pour_logs")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_pour_logs_projectId ON pour_logs(projectId)")

            // ── site_inspections (designId now nullable for SET NULL FK) ──
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS site_inspections_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    designId INTEGER,
                    inspectorName TEXT NOT NULL, date INTEGER,
                    formworkSafe INTEGER NOT NULL DEFAULT 0, rebarMatchesDesign INTEGER NOT NULL DEFAULT 0,
                    coverAdequate INTEGER NOT NULL DEFAULT 0, cleanlinessPassed INTEGER NOT NULL DEFAULT 0,
                    comments TEXT NOT NULL,
                    FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE,
                    FOREIGN KEY (designId) REFERENCES designs(id) ON DELETE SET NULL
                )
            """)
            database.execSQL("INSERT INTO site_inspections_new SELECT * FROM site_inspections")
            database.execSQL("DROP TABLE site_inspections")
            database.execSQL("ALTER TABLE site_inspections_new RENAME TO site_inspections")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_site_inspections_projectId ON site_inspections(projectId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_site_inspections_designId ON site_inspections(designId)")

            // Re-enable foreign key enforcement
            database.execSQL("PRAGMA foreign_keys = ON")
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
