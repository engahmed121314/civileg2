package com.civileg.app.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.FrozenDesignRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuantityBasisPersistenceTest {
    private fun repository(dao: DesignDao, slabs: SlabDao = mockk(relaxed = true)) = DesignRepository(
        dao, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        slabs, mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true)
    )

    private val input = """{"fcu":30.0,"fy":400.0,"span":5000.0,"designCode":"ECP"}"""
    private val result = """{"isSafe":true,"utilizationRatio":0.8,"safetyAssessmentBlocked":false}"""

    @Test fun `migration 14 to 15 keeps every stored number and marks old rows legacy`() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE designs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "projectId INTEGER NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL, " +
                                "inputData TEXT NOT NULL, results TEXT NOT NULL, isSafe INTEGER NOT NULL, " +
                                "utilizationRatio REAL NOT NULL DEFAULT 0.0, codeUsed TEXT NOT NULL, " +
                                "concreteVolume REAL NOT NULL DEFAULT 0.0, steelWeight REAL NOT NULL DEFAULT 0.0, " +
                                "totalCost REAL NOT NULL DEFAULT 0.0, createdAt INTEGER)"
                        )
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, o: Int, n: Int) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO designs (projectId,type,name,inputData,results,isSafe,utilizationRatio,codeUsed," +
                "concreteVolume,steelWeight,totalCost,createdAt) VALUES " +
                "(1,'HORDI_SLAB','old','{\"spanX\":4.0}','{}',1,0.5,'ECP',3.25,61.5,999.5,1700000000000)"
        )
        Migrations.MIGRATION_14_15.migrate(db)
        db.query("SELECT concreteVolume, steelWeight, totalCost, utilizationRatio, name, quantityBasis, snapshotRevision FROM designs").use {
            assertTrue(it.moveToFirst())
            assertEquals(3.25, it.getDouble(0), 0.0)
            assertEquals(61.5, it.getDouble(1), 0.0)
            assertEquals(999.5, it.getDouble(2), 0.0)
            assertEquals(0.5, it.getDouble(3), 0.0)
            assertEquals("old", it.getString(4))
            assertEquals("LEGACY_UNVERIFIED", it.getString(5))
            assertTrue(it.isNull(6))
        }
        db.execSQL(
            "INSERT INTO designs (projectId,type,name,inputData,results,isSafe,codeUsed) VALUES (1,'BEAM','new','{}','{}',0,'ECP')"
        )
        db.query("SELECT quantityBasis FROM designs WHERE name='new'").use {
            assertTrue(it.moveToFirst()); assertEquals("LEGACY_UNVERIFIED", it.getString(0))
        }
        db.close()
    }

    @Test fun `unreadable stored basis never becomes a stronger claim`() {
        val c = Converters()
        assertEquals(QuantityBasis.LEGACY_UNVERIFIED, c.fromQuantityBasis("garbage"))
        assertEquals(QuantityBasis.MEASURED, c.fromQuantityBasis("MEASURED"))
        assertEquals("UNKNOWN", c.quantityBasisToString(QuantityBasis.UNKNOWN))
    }

    @Test fun `hordi snapshot is stored verbatim with unknown quantities and its fingerprint`() = runBlocking {
        val dao = mockk<DesignDao>()
        val saved = slot<Design>()
        coEvery { dao.insertDesign(capture(saved)) } returns 7L
        val record = FrozenDesignRecord.capture(input, result)
        assertEquals(7L, repository(dao).saveHordiSlabSnapshot(3L, "R1", record))
        val d = saved.captured
        assertEquals(DesignType.HORDI_SLAB, d.type)
        assertEquals(input, d.inputData)
        assertEquals(result, d.results)
        assertEquals(record.revisionSha256, d.snapshotRevision)
        assertEquals(QuantityBasis.UNKNOWN, d.quantityBasis)
        assertEquals(0.0, d.concreteVolume, 0.0)
        assertEquals(0.0, d.steelWeight, 0.0)
        assertEquals(0.0, d.totalCost, 0.0)
        // The ECP Hordi hold still applies on read even though the stored result said safe.
        assertTrue(StoredDesignSafety.isBlocked(d))
        assertFalse(StoredDesignSafety.forDisplay(d).isSafe)
    }

    @Test fun `snapshot without a readable design code is held and a blocked result is never stored safe`() = runBlocking {
        val dao = mockk<DesignDao>()
        val saved = slot<Design>()
        coEvery { dao.insertDesign(capture(saved)) } returns 1L
        repository(dao).saveHordiSlabSnapshot(
            3L, "R2", FrozenDesignRecord.capture("""{"fcu":30.0}""", """{"isSafe":true,"safetyAssessmentBlocked":true}""")
        )
        assertFalse(saved.captured.isSafe)
        assertEquals("UNSPECIFIED", saved.captured.codeUsed)
        assertTrue(StoredDesignSafety.isBlocked(saved.captured))
    }

    @Test fun `legacy hollow block save no longer writes invented dimensions or measured zero quantities`() = runBlocking {
        val dao = mockk<DesignDao>()
        val slabs = mockk<SlabDao>(relaxed = true)
        val saved = slot<Design>()
        coEvery { dao.insertDesign(capture(saved)) } returns 1L
        repository(dao, slabs).saveSlabDesign(
            1L, "H",
            CalculatorEngine.SlabResult(
                type = CalculatorEngine.SlabType.HOLLOW_BLOCK, thickness = 250.0, isSafe = true,
                concreteVolume = 12.0, steelWeight = 34.0, cost = 5.0, code = DesignCode.ACI
            )
        )
        val d = saved.captured
        assertEquals(QuantityBasis.UNKNOWN, d.quantityBasis)
        assertEquals(0.0, d.concreteVolume, 0.0)
        assertEquals(0.0, d.steelWeight, 0.0)
        assertFalse(d.inputData.contains("spanX"))
        assertFalse(d.inputData.contains("fcu"))
        coVerify(exactly = 0) { slabs.insertSlab(any()) }
    }

    @Test fun `solid slab with supplied dimensions keeps them and stays legacy unverified`() = runBlocking {
        val dao = mockk<DesignDao>()
        val saved = slot<Design>()
        coEvery { dao.insertDesign(capture(saved)) } returns 1L
        repository(dao).saveSlabDesign(
            1L, "S",
            CalculatorEngine.SlabResult(
                type = CalculatorEngine.SlabType.SOLID, thickness = 150.0, isSafe = true,
                concreteVolume = 2.0, steelWeight = 30.0, cost = 5.0
            ),
            6.0, 4.0, 30.0, 420.0
        )
        assertEquals(QuantityBasis.LEGACY_UNVERIFIED, saved.captured.quantityBasis)
        assertEquals(2.0, saved.captured.concreteVolume, 0.0)
        assertTrue(saved.captured.inputData.contains("\"spanX\":6"))
        assertNull(saved.captured.snapshotRevision)
    }

    @Test fun `fresh beam save is a nominal estimate and invents no span or materials`() = runBlocking {
        val dao = mockk<DesignDao>()
        val beams = mockk<BeamDao>(relaxed = true)
        val saved = slot<Design>()
        coEvery { dao.insertDesign(capture(saved)) } returns 1L
        val repo = DesignRepository(
            dao, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            beams, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)
        )
        repo.saveBeamDesign(1L, "B", CalculatorEngine.BeamResult(width = 250.0, depth = 600.0, isSafe = true, concreteVolume = 0.9, steelWeight = 70.0))
        assertEquals(QuantityBasis.NOMINAL_ESTIMATE, saved.captured.quantityBasis)
        assertFalse(saved.captured.inputData.contains("\"span\""))
        assertFalse(saved.captured.inputData.contains("\"fcu\""))
        coVerify(exactly = 0) { beams.insertBeam(any()) }
    }
}
