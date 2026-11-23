package com.civileg.app.costcontrol

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.civileg.app.data.repository.*
import com.civileg.app.db.costcontrol.CostBoqHeadEntity
import com.civileg.app.domain.repository.*
import com.civileg.app.viewmodel.costcontrol.*
import java.io.File
import java.nio.file.Files

internal class DraftContext : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
    val root:File=Files.createTempDirectory("boq-native-").toFile()
    override fun getNoBackupFilesDir():File=root
    fun checkpoint(project:Long)=File(root,"cost-boq-drafts-v1/project-$project.checkpoint")
}
internal fun draft(project:Long=10,count:Int=1):SavedCostBoq {
    val initial=CostBoqDraftEditor.add(project,null,null,1)
    val source=initial.items.single()
    val candidate=initial.copy(items=(0 until count).map { source.copy(itemId="item-$it",originalDescription="Work item $it") })
    val hash=RoomCostBoqRepository.contentHash(candidate)
    return candidate.copy(revision=candidate.revision.copy(contentHash=hash),items=candidate.items.map{it.copy(contentHash=hash)})
}
internal fun checkpoint(value:SavedCostBoq)=CostBoqCheckpoint(projectId=value.revision.projectId,
    method=BoqWorkMethod.ENTER_ITEMS_AND_QUANTITIES,base=null,candidate=value,inputs=emptyMap())
internal class NativeRepo:CostBoqRepository {
    var value:SavedCostBoq?=null
    var writes=0
    override suspend fun loadCurrent(projectId:Long)=value?.takeIf{it.revision.projectId==projectId}
    override suspend fun append(expectedHead:CostBoqHeadEntity?,candidate:SavedCostBoq):SavedCostBoq {writes++;value=candidate;return candidate}
}
