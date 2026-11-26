package com.civileg.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.civileg.app.db.*
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.SlabDesignResult
import com.civileg.app.utils.SettingsManager
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real repository/database proof, plus absence-of-new-fields compatibility. */
@RunWith(AndroidJUnit4::class)
class HordiRoomPersistenceTest {
 @Test fun unassessedContractSurvivesDatabaseCloseReopen() = runBlocking {
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext
  val name="hordi-contract-verification.db";ctx.deleteDatabase(name)
  fun open()=Room.databaseBuilder(ctx,AppDatabase::class.java,name).build()
  var db=open()
  try {
   val projectId=db.projectDao().insertProject(Project(name="Hordi persistence diagnostic"))
   val r=CalculatorEngine(SettingsManager(ctx)).designSlab(4.0,5.0,5.0,3.0,30.0,400.0,250.0,12,
    DesignCode.ECP,CalculatorEngine.SlabType.HOLLOW_BLOCK,ribWidth=160.0,ribSpacing=500.0)
   assertFalse(r.isSafe);assertFalse(r.bondClassAssessed)
   assertTrue(r.warnings.any{it.contains("UNKNOWN")})
   DesignRepository(db.designDao(),db.footingDao(),db.columnDao(),db.slabDao(),db.beamDao(),
    db.stairDao(),db.retainingWallDao(),db.tankDao(),db.inventoryDao())
    .saveSlabDesign(projectId,"Hordi diagnostic",r,4.0,5.0,30.0,400.0)
   db.close();db=open()
   val saved=db.designDao().getDesignsForProjectList(projectId).single()
   val restored=Gson().fromJson(saved.results,CalculatorEngine.SlabResult::class.java)
   assertFalse(saved.isSafe);assertFalse(restored.isSafe);assertFalse(restored.bondClassAssessed)
   assertEquals(r.warnings,restored.warnings);assertEquals(r.codeNotes,restored.codeNotes)
   val oldGeneric=Gson().fromJson("{\"isSafe\":true}",CalculatorEngine.SlabResult::class.java)
   val oldDomain=Gson().fromJson("{\"isSafe\":true}",SlabDesignResult::class.java)
   assertFalse(oldGeneric.bondClassAssessed);assertFalse(oldDomain.bondClassAssessed)
   // Historical numeric status can remain true. It must not be treated as acceptance.
   assertTrue(oldGeneric.isSafe);assertTrue(oldDomain.isSafe)
   assertFalse(oldGeneric.engineeringAcceptance);assertFalse(oldDomain.engineeringAcceptance)
   assertNull(oldGeneric.barBondClass);assertNull(oldDomain.barBondClass)
   assertFalse(oldGeneric.serviceabilityAssessed);assertFalse(oldDomain.serviceabilityAssessed)
   // Even an old green numeric status cannot become a green saved Hordi record.
   val oldHordi=oldGeneric.copy(type=CalculatorEngine.SlabType.HOLLOW_BLOCK)
   DesignRepository(db.designDao(),db.footingDao(),db.columnDao(),db.slabDao(),db.beamDao(),
    db.stairDao(),db.retainingWallDao(),db.tankDao(),db.inventoryDao())
    .saveSlabDesign(projectId,"Legacy Hordi diagnostic",oldHordi)
   db.close();db=open()
   assertTrue(db.designDao().getDesignsForProjectList(projectId).all{!it.isSafe})
   val out=File(ctx.filesDir,"drawing-samples/hordi").apply{mkdirs()}
   File(out,"room-persistence.txt").writeText("Actual Room save-close-reopen passed. UNKNOWN material false, warnings/notes preserved. Old Gson fields absent: material assessed false, legacy numeric isSafe true. No construction acceptance or full UI restore proved.\n")
  } finally {db.close();ctx.deleteDatabase(name)}
 }
}
