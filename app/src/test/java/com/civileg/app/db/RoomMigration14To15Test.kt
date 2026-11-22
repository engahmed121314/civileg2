package com.civileg.app.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Runs the real Room open path (migration, then Room's own schema validation of every
 * table) against a file that is a version 14 database holding existing design rows.
 * The v14 shape is produced from the current v15 schema by removing exactly the two
 * columns the migration adds, so every other table is the real one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMigration14To15Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "mig_14_15_test.db"

    @After fun tearDown() { context.deleteDatabase(name) }

    @Test fun `existing rows survive Room validated migration and read as legacy unverified`() = runBlocking {
        context.deleteDatabase(name)
        // 1. Create the current schema, then turn it into a v14 file with one old design row.
        val fresh = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        fresh.openHelper.writableDatabase.close()
        fresh.close()
        val file: File = context.getDatabasePath(name)
        val raw = android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
        raw.execSQL("INSERT INTO projects (id,name,location,clientName,description,status,code,createdAt,updatedAt) VALUES (1,'p','','','','ACTIVE','ECP',0,0)")
        // The test SQLite has no ALTER ... DROP COLUMN: rebuild designs as a fresh v14 install has it
        // (createdAt NOT NULL, as Room's entity expects).
        raw.execSQL(
            "CREATE TABLE designs_v14 (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, " +
                "type TEXT NOT NULL, name TEXT NOT NULL, inputData TEXT NOT NULL, results TEXT NOT NULL, " +
                "isSafe INTEGER NOT NULL, utilizationRatio REAL NOT NULL DEFAULT 0.0, codeUsed TEXT NOT NULL, " +
                "concreteVolume REAL NOT NULL DEFAULT 0.0, steelWeight REAL NOT NULL DEFAULT 0.0, " +
                "totalCost REAL NOT NULL DEFAULT 0.0, createdAt INTEGER NOT NULL, " +
                "FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE)"
        )
        raw.execSQL("DROP TABLE designs")
        raw.execSQL("ALTER TABLE designs_v14 RENAME TO designs")
        raw.execSQL("CREATE INDEX index_designs_projectId ON designs(projectId)")
        raw.execSQL(
            "INSERT INTO designs (projectId,type,name,inputData,results,isSafe,utilizationRatio,codeUsed," +
                "concreteVolume,steelWeight,totalCost,createdAt) VALUES " +
                "(1,'SLAB','old','{\"spanX\":4.0}','{}',1,0.5,'ECP',3.25,61.5,999.5,1700000000000)"
        )
        raw.version = 14
        raw.close()

        // 2. Open through Room with the real migration; Room validates the whole schema.
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(Migrations.MIGRATION_14_15)
            .allowMainThreadQueries()
            .build()
        val rows = migrated.designDao().getDesignsForProjectList(1L)
        assertEquals(1, rows.size)
        val d = rows.single()
        assertEquals(3.25, d.concreteVolume, 0.0)
        assertEquals(61.5, d.steelWeight, 0.0)
        assertEquals(999.5, d.totalCost, 0.0)
        assertEquals("old", d.name)
        assertEquals(QuantityBasis.LEGACY_UNVERIFIED, d.quantityBasis)
        assertNull(d.snapshotRevision)
        val id = migrated.designDao().insertDesign(
            Design(projectId = 1L, type = DesignType.HORDI_SLAB, name = "new", inputData = "{}", results = "{}",
                isSafe = false, codeUsed = "ECP", quantityBasis = QuantityBasis.UNKNOWN, snapshotRevision = "abc")
        )
        val saved = migrated.designDao().getDesignById(id)!!
        assertEquals(QuantityBasis.UNKNOWN, saved.quantityBasis)
        assertEquals("abc", saved.snapshotRevision)
        assertTrue(migrated.designDao().getDesignsForProjectList(1L).size == 2)
        migrated.close()
    }
}
