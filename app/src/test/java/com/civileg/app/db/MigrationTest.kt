package com.civileg.app.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val configuration = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
            org.robolectric.RuntimeEnvironment.getApplication()
        )
            .name(null) // In-memory database
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS projects (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "location TEXT NOT NULL DEFAULT '', " +
                            "clientName TEXT NOT NULL DEFAULT '', " +
                            "designerName TEXT NOT NULL DEFAULT '', " +
                            "codeStandard TEXT NOT NULL DEFAULT 'ECP', " +
                            "createdAt INTEGER NOT NULL, " +
                            "updatedAt INTEGER NOT NULL" +
                            ")"
                    )

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS designs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "projectId INTEGER NOT NULL, " +
                            "type TEXT NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "inputData TEXT NOT NULL, " +
                            "results TEXT NOT NULL, " +
                            "isSafe INTEGER NOT NULL, " +
                            "utilizationRatio REAL NOT NULL DEFAULT 0.0, " +
                            "codeUsed TEXT NOT NULL, " +
                            "concreteVolume REAL NOT NULL DEFAULT 0.0, " +
                            "steelWeight REAL NOT NULL DEFAULT 0.0, " +
                            "totalCost REAL NOT NULL DEFAULT 0.0, " +
                            "createdAt INTEGER" +
                            ")"
                    )

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS footings (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "projectId INTEGER NOT NULL, " +
                            "type TEXT NOT NULL, load REAL NOT NULL, soilPressure REAL NOT NULL, " +
                            "fcu REAL NOT NULL, fy REAL NOT NULL, colWidth REAL NOT NULL, colDepth REAL NOT NULL, " +
                            "width REAL NOT NULL, length REAL NOT NULL, thickness REAL NOT NULL, " +
                            "reinforcementBottom TEXT NOT NULL, reinforcementTop TEXT, " +
                            "concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL, " +
                            "utilizationRatio REAL NOT NULL DEFAULT 0.0" +
                            ")"
                    )

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS columns_table (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "projectId INTEGER NOT NULL, " +
                            "load REAL NOT NULL, fcu REAL NOT NULL, fy REAL NOT NULL, " +
                            "width REAL NOT NULL, depth REAL NOT NULL, " +
                            "reinforcement TEXT NOT NULL, ties TEXT NOT NULL, " +
                            "concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL, " +
                            "utilizationRatio REAL NOT NULL DEFAULT 0.0" +
                            ")"
                    )

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS slabs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "projectId INTEGER NOT NULL, " +
                            "type TEXT NOT NULL, spanX REAL NOT NULL, spanY REAL NOT NULL, " +
                            "thickness REAL NOT NULL, load REAL NOT NULL, fcu REAL NOT NULL, fy REAL NOT NULL, " +
                            "reinforcement TEXT NOT NULL, " +
                            "concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL, " +
                            "utilizationRatio REAL NOT NULL DEFAULT 0.0" +
                            ")"
                    )

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS beams (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "projectId INTEGER NOT NULL, " +
                            "span REAL NOT NULL, load REAL NOT NULL, fcu REAL NOT NULL, fy REAL NOT NULL, " +
                            "width REAL NOT NULL, depth REAL NOT NULL, " +
                            "reinforcement TEXT NOT NULL, stirrups TEXT NOT NULL, " +
                            "concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL, " +
                            "utilizationRatio REAL NOT NULL DEFAULT 0.0" +
                            ")"
                    )

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS stairs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "projectId INTEGER NOT NULL, " +
                            "thickness REAL NOT NULL, load REAL NOT NULL, fcu REAL NOT NULL, fy REAL NOT NULL, " +
                            "reinforcement TEXT NOT NULL, " +
                            "concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL, " +
                            "utilizationRatio REAL NOT NULL DEFAULT 0.0" +
                            ")"
                    )

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS retaining_walls (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "projectId INTEGER NOT NULL, " +
                            "height REAL NOT NULL, stemThickness REAL NOT NULL, baseWidth REAL NOT NULL, " +
                            "baseThickness REAL NOT NULL, reinforcement TEXT NOT NULL, " +
                            "concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL, " +
                            "utilizationRatio REAL NOT NULL DEFAULT 0.0" +
                            ")"
                    )

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS tanks (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "projectId INTEGER NOT NULL, " +
                            "length REAL NOT NULL, width REAL NOT NULL, height REAL NOT NULL, " +
                            "wallThickness REAL NOT NULL, baseThickness REAL NOT NULL, " +
                            "reinforcement TEXT NOT NULL, " +
                            "concreteVolume REAL NOT NULL, steelWeight REAL NOT NULL, cost REAL NOT NULL, " +
                            "utilizationRatio REAL NOT NULL DEFAULT 0.0" +
                            ")"
                    )

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS materials (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "projectId INTEGER NOT NULL, " +
                            "name TEXT NOT NULL, unit TEXT NOT NULL, quantity REAL NOT NULL, " +
                            "unitPrice REAL NOT NULL, totalPrice REAL NOT NULL, " +
                            "category TEXT NOT NULL, createdAt INTEGER" +
                            ")"
                    )

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS pour_logs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "projectId INTEGER NOT NULL, " +
                            "elementId TEXT NOT NULL, date INTEGER, volumeM3 REAL NOT NULL, " +
                            "slumpMm REAL NOT NULL, truckId TEXT NOT NULL, sampleId TEXT NOT NULL, " +
                            "strength7Days REAL NOT NULL DEFAULT 0.0, strength28Days REAL NOT NULL DEFAULT 0.0, " +
                            "status TEXT NOT NULL" +
                            ")"
                    )

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS site_inspections (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "projectId INTEGER NOT NULL, " +
                            "designId INTEGER NOT NULL, " +
                            "inspectorName TEXT NOT NULL, date INTEGER, " +
                            "formworkSafe INTEGER NOT NULL DEFAULT 0, rebarMatchesDesign INTEGER NOT NULL DEFAULT 0, " +
                            "coverAdequate INTEGER NOT NULL DEFAULT 0, cleanlinessPassed INTEGER NOT NULL DEFAULT 0, " +
                            "comments TEXT NOT NULL" +
                            ")"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        database = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    private fun hasTable(tableName: String): Boolean {
        val cursor = database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    @Test
    fun testMigration6To7() {
        Migrations.MIGRATION_6_7.migrate(database)
        assertTrue(hasTable("projects"))
    }

    @Test
    fun testMigration7To8() {
        Migrations.MIGRATION_7_8.migrate(database)
        assertTrue(hasTable("designs"))
    }

    @Test
    fun testMigration8To9() {
        database.execSQL("INSERT INTO projects (id, name, createdAt, updatedAt) VALUES (1, 'Test Project', 1000, 1000)")
        database.execSQL("INSERT INTO designs (id, projectId, type, name, inputData, results, isSafe, codeUsed) VALUES (1, 1, 'BEAM', 'Beam 1', '{}', '{}', 1, 'ECP')")
        Migrations.MIGRATION_8_9.migrate(database)
        assertTrue(hasTable("designs"))
    }

    @Test
    fun testMigration9To10() {
        Migrations.MIGRATION_9_10.migrate(database)
        assertTrue(hasTable("quality_records"))
    }

    @Test
    fun testMigration10To11() {
        Migrations.MIGRATION_9_10.migrate(database)
        Migrations.MIGRATION_10_11.migrate(database)
        assertTrue(hasTable("quality_records"))
    }

    @Test
    fun testMigration11To12() {
        Migrations.MIGRATION_11_12.migrate(database)
        assertTrue(hasTable("inspection_test_plans"))
        assertTrue(hasTable("non_conformities"))
    }

    @Test
    fun testMigration12To13() {
        Migrations.MIGRATION_12_13.migrate(database)
        assertTrue(hasTable("building_layouts"))
    }

    @Test
    fun testMigration13To14() {
        Migrations.MIGRATION_13_14.migrate(database)
        assertTrue(hasTable("exports"))
    }

    @Test
    fun testAllMigrationsInSequenceFrom6To14() {
        database.execSQL("INSERT INTO projects (id, name, createdAt, updatedAt) VALUES (1, 'Test Project', 1000, 1000)")
        database.execSQL("INSERT INTO designs (id, projectId, type, name, inputData, results, isSafe, codeUsed) VALUES (1, 1, 'BEAM', 'Beam 1', '{}', '{}', 1, 'ECP')")

        val allMigrations = arrayOf(
            Migrations.MIGRATION_6_7,
            Migrations.MIGRATION_7_8,
            Migrations.MIGRATION_8_9,
            Migrations.MIGRATION_9_10,
            Migrations.MIGRATION_10_11,
            Migrations.MIGRATION_11_12,
            Migrations.MIGRATION_12_13,
            Migrations.MIGRATION_13_14
        )

        for (migration in allMigrations) {
            migration.migrate(database)
        }

        assertTrue(hasTable("projects"))
        assertTrue(hasTable("designs"))
        assertTrue(hasTable("quality_records"))
        assertTrue(hasTable("inspection_test_plans"))
        assertTrue(hasTable("non_conformities"))
        assertTrue(hasTable("building_layouts"))
        assertTrue(hasTable("exports"))
    }
}
