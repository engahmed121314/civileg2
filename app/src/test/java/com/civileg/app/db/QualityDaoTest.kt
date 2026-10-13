package com.civileg.app.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QualityDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: QualityDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.qualityDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertRecords_andCategorySummary_aggregatesCorrectly() = runBlocking {
        dao.insertRecord(QualityRecord(category = "CONCRETE", passed = true))
        dao.insertRecord(QualityRecord(category = "CONCRETE", passed = false))
        dao.insertRecord(QualityRecord(category = "SOIL", passed = true))

        assertEquals(3, dao.getAllRecords().first().size)

        val summary = dao.getCategorySummary().first().associateBy { it.category }
        assertEquals(2, summary["CONCRETE"]!!.total)
        assertEquals(1, summary["CONCRETE"]!!.passedCount)
        assertEquals(1, summary["SOIL"]!!.total)
    }

    @Test
    fun itp_insertAndUpdateStatus_roundTrips() = runBlocking {
        val id = dao.insertItp(
            InspectionTestPlan(discipline = "EARTHWORKS", activity = "Compaction")
        )
        assertEquals(1, dao.getItpByDiscipline("EARTHWORKS").first().size)

        dao.updateItpStatus(id, "passed", "98%", "Eng. Ahmed", "approved")

        val item = dao.getItpByDiscipline("EARTHWORKS").first().first()
        assertEquals("passed", item.status)
        assertEquals("98%", item.resultValue)
        assertEquals("Eng. Ahmed", item.checkedBy)
    }

    @Test
    fun ncr_openCount_andDuplicateGuard() = runBlocking {
        val id = dao.insertNcr(
            NonConformity(ncrNumber = "NCR-000001", discipline = "CONCRETE", elementId = "B1")
        )

        assertEquals(1, dao.countOpenNcr().first())
        assertEquals(1, dao.countOpenNcrFor("CONCRETE", "B1"))
        assertEquals(0, dao.countOpenNcrFor("CONCRETE", "B2"))

        dao.updateNcrStatus(id, "closed", "re-cast", "Eng. Ahmed")

        assertEquals(0, dao.countOpenNcr().first())
        assertEquals(0, dao.countOpenNcrFor("CONCRETE", "B1"))
    }
}
