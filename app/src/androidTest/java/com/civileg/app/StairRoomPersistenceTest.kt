package com.civileg.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.civileg.app.db.AppDatabase
import com.civileg.app.db.DesignRepository
import com.civileg.app.db.Project
import com.civileg.app.domain.calculations.base.StairSupportCondition
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.SettingsManager
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual Room save, database close/reopen, and the repository's serialized result. */
@RunWith(AndroidJUnit4::class)
class StairRoomPersistenceTest {
    @Test fun partialQuantityContractSurvivesDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "stair-contract-verification.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open()
        try {
            val projectId = db.projectDao().insertProject(Project(name = "Stair persistence diagnostic"))
            val result = CalculatorEngine(SettingsManager(context)).designStaircase(
                CalculatorEngine.StairType.SINGLE_FLIGHT,4.0,150.0,300.0,5.0,4.0,25.0,360.0,12,
                DesignCode.ECP,1400.0,StairSupportCondition.SIMPLY_SUPPORTED)
            assertNotNull(result.quantityReview)
            DesignRepository(db.designDao(),db.footingDao(),db.columnDao(),db.slabDao(),db.beamDao(),
                db.stairDao(),db.retainingWallDao(),db.tankDao(),db.inventoryDao())
                .saveStairDesign(projectId,"ST-persistence",result)
            db.close()
            db = open()
            val saved = db.designDao().getDesignsForProjectList(projectId).single()
            val restored = Gson().fromJson(saved.results,CalculatorEngine.StairResult::class.java)
            assertEquals(result.stairWidthMm,restored.stairWidthMm,0.0)
            assertEquals(result.supportCondition,restored.supportCondition)
            assertNull(restored.landingLengthMm)
            assertEquals(result.quantityReview,restored.quantityReview)
            assertNull(restored.quantityReview!!.finalSteelKg)
            assertFalse(restored.quantityReview!!.fabricationScheduleAvailable)
            assertFalse(saved.isSafe)
            assertFalse(restored.codeAcceptanceComplete)
            assertEquals(result.steelWeight,db.stairDao().getStairsForProject(projectId).single().steelWeight,1e-8)
            val out = File(context.filesDir,"drawing-samples/stair").apply { mkdirs() }
            File(out,"room-persistence.txt").writeText(
                "Actual Room repository save and database close/reopen passed.\n" +
                "width_mm=${restored.stairWidthMm}\nsupport=${restored.supportCondition}\n" +
                "net_flight_steel_kg=${restored.quantityReview!!.netFlightSteelKg}\n" +
                "final_steel=null\nfabrication_schedule=false\ncode_acceptance=false\n")
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
