package com.civileg.app.qa

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Disposable synthetic-upgrade QA only. Runs in the QA workflow AFTER the
 * re-signed old v1.8.0 -> candidate `adb install -r` upgrade and one real
 * MainActivity launch, so the production Hilt AppModule migration chain
 * (6_7..14_15) has already executed against the seeded database.
 *
 * Expectations are produced on the host by tools/qa/host/seed_upgrade_db.py,
 * pushed to the device, and passed via `am instrument -e qaExpectations <path>`.
 * The only write is one insert+delete that proves next-auto-ID behavior on the
 * disposable emulator database. UNCOMPILED until a real androidTest build.
 */
@RunWith(AndroidJUnit4::class)
class UpgradePreservationTest {

    private fun expectations(): JSONObject {
        val path = InstrumentationRegistry.getArguments().getString("qaExpectations")
            ?: "/data/local/tmp/qa_expectations.json"
        val file = File(path)
        assertTrue("expectations file missing at $path", file.exists())
        return JSONObject(file.readText())
    }

    private fun dbPath(): String {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val f = ctx.getDatabasePath("civil_eg_database")
        assertTrue("database file missing at ${f.absolutePath}", f.exists())
        return f.absolutePath
    }

    @Test
    fun migratesToVersion15WithoutDataLoss() {
        val exp = expectations()
        val case = exp.getString("case")
        assertTrue(
            "unknown QA case label: $case",
            case == "fresh-old-14" || case == "historical-8to9-derived-modified-fixture"
        )
        SQLiteDatabase.openDatabase(dbPath(), null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA user_version", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("user_version must reach 15", 15, c.getInt(0))
            }
            val cols = mutableSetOf<String>()
            db.rawQuery("PRAGMA table_info(designs)", null).use { c ->
                while (c.moveToNext()) cols += c.getString(1)
            }
            assertTrue("quantityBasis column missing", "quantityBasis" in cols)
            assertTrue("snapshotRevision column missing", "snapshotRevision" in cols)

            // An index covering designs(projectId) must exist after migration.
            var projectIdIndexed = false
            db.rawQuery("PRAGMA index_list(designs)", null).use { idx ->
                val nameCol = idx.getColumnIndexOrThrow("name")
                while (idx.moveToNext() && !projectIdIndexed) {
                    db.rawQuery("PRAGMA index_info(${idx.getString(nameCol)})", null).use { info ->
                        while (info.moveToNext()) {
                            if (info.getString(info.getColumnIndexOrThrow("name")) == "projectId") projectIdIndexed = true
                        }
                    }
                }
            }
            assertTrue("no index covers designs(projectId)", projectIdIndexed)

            val seededProjects = exp.getInt("seededProjectCount")
            db.rawQuery("SELECT COUNT(*) FROM projects", null).use { c ->
                assertTrue(c.moveToFirst())
                assertTrue("projects lost: ${c.getInt(0)} < $seededProjects", c.getInt(0) >= seededProjects)
            }

            val sentinel = exp.getJSONObject("sentinelProject")
            db.rawQuery("SELECT id, name FROM projects WHERE name = ?", arrayOf(sentinel.getString("name"))).use { c ->
                assertTrue("sentinel project missing", c.moveToFirst())
                assertEquals(sentinel.getLong("id"), c.getLong(0))
            }

            val designs = exp.getJSONArray("sentinelDesigns")
            for (i in 0 until designs.length()) {
                val d = designs.getJSONObject(i)
                db.rawQuery(
                    "SELECT name, type, inputData, results, codeUsed, quantityBasis, createdAt FROM designs WHERE id = ?",
                    arrayOf(d.getLong("id").toString())
                ).use { c ->
                    assertTrue("sentinel design ${d.getLong("id")} missing", c.moveToFirst())
                    assertEquals(d.getString("name"), c.getString(0))
                    assertEquals(d.getString("type"), c.getString(1))
                    assertEquals(d.getString("inputData"), c.getString(2))
                    assertEquals(d.getString("results"), c.getString(3))
                    assertEquals(d.getString("codeUsed"), c.getString(4))
                    assertEquals("old quantities must stay LEGACY_UNVERIFIED", "LEGACY_UNVERIFIED", c.getString(5))
                    assertEquals("createdAt must be preserved/normalized", d.getLong("createdAtAfterUpgrade"), c.getLong(6))
                }
            }

            val links = exp.getJSONArray("inspectionLinks")
            for (i in 0 until links.length()) {
                val l = links.getJSONObject(i)
                db.rawQuery("SELECT designId FROM site_inspections WHERE id = ?", arrayOf(l.getLong("inspectionId").toString())).use { c ->
                    assertTrue("inspection ${l.getLong("inspectionId")} missing", c.moveToFirst())
                    if (l.isNull("designId")) assertTrue("orphan-null link must stay null", c.isNull(0))
                    else assertEquals(l.getLong("designId"), c.getLong(0))
                }
            }

            db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name = 'designs'", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("design id sequence must be preserved", exp.getLong("designSeqAfterSeeding"), c.getLong(0))
            }
        }

        // Next auto-ID behavior on the migrated disposable database: one insert
        // must receive exactly the preserved sequence + 1, then it is deleted.
        val expectedNext = exp.getLong("designSeqAfterSeeding") + 1
        val sentinelProjectId = exp.getJSONObject("sentinelProject").getLong("id")
        SQLiteDatabase.openDatabase(dbPath(), null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            // The probe must satisfy the designs table's NOT NULL columns.
            // Donor: the first original sentinel design row (already asserted
            // intact above); its four remaining NOT NULL REAL columns are read
            // back as exact typed doubles and only those are added. The probe
            // keeps its own name/type/input/results and no id is supplied.
            val donor = exp.getJSONArray("sentinelDesigns").getJSONObject(0)
            val donorId = donor.getLong("id")
            val donorCols = db.rawQuery(
                "SELECT utilizationRatio, concreteVolume, steelWeight, totalCost FROM designs WHERE id = ?",
                arrayOf(donorId.toString())
            ).use { c ->
                assertTrue("donor sentinel row " + donorId + " must exist", c.moveToFirst())
                (0..3).map { c.getDouble(it) }
            }
            val v = ContentValues().apply {
                put("projectId", sentinelProjectId)
                put("type", "BEAM")
                put("name", "QA_NEXTID_PROBE")
                put("inputData", "{}")
                put("results", "{}")
                put("isSafe", 1)
                put("codeUsed", "ECP")
                put("createdAt", 0L)
                put("utilizationRatio", donorCols[0])
                put("concreteVolume", donorCols[1])
                put("steelWeight", donorCols[2])
                put("totalCost", donorCols[3])
            }
            val newId = db.insertOrThrow("designs", null, v)
            try {
                assertEquals("next design id must continue the preserved sequence", expectedNext, newId)
            } finally {
                db.delete("designs", "id = ?", arrayOf(newId.toString()))
            }
        }
    }
}
