package com.civileg.app.costcontrol

import androidx.room.*
import androidx.test.core.app.ApplicationProvider
import com.civileg.app.db.Project
import com.civileg.app.db.Converters
import com.civileg.app.db.costcontrol.*
import com.civileg.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class BoqRoomTransactionTest {
    @Test fun exactHistoricalRetryDoesNotMoveNewerHeadAndProjectCascadeIsOwned()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),BoqIsolatedTestDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.projects().insert(Project(id=10,name="First"))
            db.projects().insert(Project(id=20,name="Other"))
            val repo=RoomCostBoqRepository(db.boq(),StrictCostBoqPayloadValidator())
            val first=draft();repo.append(null,first)
            val expected=CostBoqHeadEntity(10,first.revision.revisionId,first.revision.contentHash)
            val second=CostBoqDraftEditor.add(10,first,first,2);repo.append(expected,second)
            assertEquals(first,repo.append(null,first))
            assertEquals(second,repo.loadCurrent(10))
            val stale=CostBoqDraftEditor.add(10,first,first,3)
            var rejected=false;try{repo.append(expected,stale)}catch(_:Exception){rejected=true}
            assertTrue(rejected);assertNull(db.boq().revision(10,stale.revision.revisionId))
            assertEquals(second,repo.loadCurrent(10))
            assertNull(repo.loadCurrent(20))
            db.projects().delete(10)
            assertNull(repo.loadCurrent(10));assertTrue(db.boq().items(10,first.revision.revisionId).isEmpty())
        } finally {db.close()}
    }
}
