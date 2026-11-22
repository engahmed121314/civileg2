package com.civileg.app.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.entities.ColumnCalculationInputs
import com.civileg.app.domain.entities.ColumnDesignSnapshot
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Persistence/provenance fixtures, never engineering acceptance vectors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColumnSnapshotPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: DesignRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<android.content.Context>(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = DesignRepository(db.designDao(), db.footingDao(), db.columnDao(),
            db.slabDao(), db.beamDao(), db.stairDao(), db.retainingWallDao(),
            db.tankDao(), db.inventoryDao(), database = db)
    }
    @After fun tearDown() { db.close() }

    private fun snapshot(): ColumnDesignSnapshot {
        val input = ColumnCalculationInputs(widthMm = 300.0, depthMm = 450.0,
            heightM = 3.25, fcuMpa = 30.0, fyMpa = 420.0,
            enteredAxialLoadKn = 500.0, axialLoadPassedToEngineKn = 500.0,
            momentXKnM = 20.0, momentYKnM = 10.0, code = DesignCode.ECP,
            loadCombination = LoadCombination.DEAD_LIVE,
            actionBasis = "PERSISTENCE_TEST_FIXTURE_NOT_A_DESIGN_APPROVAL",
            appliedAggregateLoadMultiplier = 1.0, isCircular = false,
            connectedSlab = "SOLID", hasCap = false, preferredDiameterMm = 16,
            autoOptimize = true, manualBarCount = null, isSeismic = false,
            autoIncludeSelfWeight = false)
        val result = CalculatorEngine.ColumnResult(width = 300.0, depth = 450.0,
            pu = 500.0, mx = 20.0, my = 10.0, code = DesignCode.ECP,
            reinforcement = CalculatorEngine.ReinforcementBar(numBars = 8, diameter = 16),
            isSafe = false, concreteVolume = 0.43875, steelWeight = 40.0,
            warnings = listOf("Persistence fixture, not a verified member design"))
        return ColumnDesignSnapshot.capture(input, result)
    }

    private suspend fun rejected(block: suspend () -> Unit): Exception {
        try { block() } catch (failure: Exception) { return failure }
        throw AssertionError("Expected the save to fail")
    }

    @Test fun exactCompletedRevisionAndLegacyKeysRoundTrip() = runBlocking {
        val project = db.projectDao().insertProject(Project(name = "Snapshot project"))
        val original = snapshot()
        repository.saveColumnDesignSnapshot(project, "C1", original)
        val stored = db.designDao().getDesignsForProjectList(project).single()
        val json = JSONObject(stored.inputData)
        assertEquals(30.0, json.getDouble("fcu"), 0.0)
        assertEquals(420.0, json.getDouble("fy"), 0.0)
        assertEquals(3.25, json.getDouble("height"), 0.0)
        val record = json.getJSONObject("completedColumnRevision")
        val restored = ColumnDesignSnapshot.restoreStored(record.getInt("schemaVersion"),
            record.getString("revisionSha256"), record.getString("inputJson"), record.getString("resultJson"))
        assertTrue(restored.hasValidFingerprint())
        assertEquals(original.revisionSha256, restored.revisionSha256)
        assertEquals(original.inputJson, restored.inputJson)
        assertEquals(original.resultJson, stored.results)
        assertFalse(restored.input().autoIncludeSelfWeight)
        val projected = db.columnDao().getColumnsForProject(project).single()
        assertEquals(30.0, projected.fcu, 0.0)
        assertEquals(original.result().reinforcement.barString, projected.reinforcement)
    }

    @Test fun nonexistentProjectCannotReceiveEitherRow() = runBlocking {
        val failure = rejected { repository.saveColumnDesignSnapshot(999L, "C1", snapshot()) }
        assertTrue(failure is IllegalArgumentException)
        assertTrue(db.designDao().getDesignsForProjectList(999L).isEmpty())
        assertTrue(db.columnDao().getColumnsForProject(999L).isEmpty())
    }

    @Test fun projectionFailureRollsBackTheGeneralRevision() = runBlocking {
        val project = db.projectDao().insertProject(Project(name = "Rollback project"))
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER column_projection_fixture BEFORE INSERT ON columns_table BEGIN SELECT RAISE(ABORT, 'column projection fixture failure'); END")
        val failure = rejected { repository.saveColumnDesignSnapshot(project, "C1", snapshot()) }
        assertTrue(generateSequence(failure as Throwable?) { it.cause }.any {
            it.message.orEmpty().contains("column projection fixture failure")
        })
        assertTrue(db.designDao().getDesignsForProjectList(project).isEmpty())
        assertTrue(db.columnDao().getColumnsForProject(project).isEmpty())
    }

    @Test fun noProjectAndBlankNameAreRejected() = runBlocking {
        val project = db.projectDao().insertProject(Project(name = "Input validation"))
        assertTrue(rejected { repository.saveColumnDesignSnapshot(0L, "C1", snapshot()) } is IllegalArgumentException)
        assertTrue(rejected { repository.saveColumnDesignSnapshot(project, "  ", snapshot()) } is IllegalArgumentException)
        assertTrue(db.designDao().getDesignsForProjectList(project).isEmpty())
    }

    @Test fun oldColumnSaveCallRemainsReadable() = runBlocking {
        val project = db.projectDao().insertProject(Project(name = "Legacy call"))
        repository.saveColumnDesign(project, "Legacy C1", snapshot().result(), 30.0, 420.0)
        val stored = db.designDao().getDesignsForProjectList(project).single()
        assertEquals(30.0, JSONObject(stored.inputData).getDouble("fcu"), 0.0)
        assertEquals(1, db.columnDao().getColumnsForProject(project).size)
    }
}
