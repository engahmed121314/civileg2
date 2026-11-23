package com.civileg.app.costcontrol

import android.util.AtomicFile
import com.civileg.app.data.repository.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class BoqAtomicCheckpointTest {
    @Test fun actualAtomicFileFailedAndInterruptedWritesRecoverAcknowledgedCheckpoint() {
        val context=DraftContext()
        try {
            val store=BoundedCostBoqCheckpointStore(AndroidCostBoqAtomicStorage(context))
            val cp=checkpoint(draft(1099511627776L))
            store.write(cp)
            val atomic=AtomicFile(context.checkpoint(cp.projectId))
            val failed=atomic.startWrite();failed.write("partial".toByteArray());atomic.failWrite(failed)
            assertEquals(cp,BoundedCostBoqCheckpointStore(AndroidCostBoqAtomicStorage(context)).load(cp.projectId))
            val interrupted=atomic.startWrite();interrupted.write("partial".toByteArray());interrupted.close()
            assertEquals(cp,BoundedCostBoqCheckpointStore(AndroidCostBoqAtomicStorage(context)).load(cp.projectId))
            // Explicit legacy .bak recovery is supported by Android AtomicFile.openRead.
            val file=context.checkpoint(cp.projectId)
            val acknowledged=file.readBytes()
            File(file.path+".bak").writeBytes(acknowledged);file.writeBytes("bad base".toByteArray())
            assertEquals(cp,BoundedCostBoqCheckpointStore(AndroidCostBoqAtomicStorage(context)).load(cp.projectId))
            // Backup-only state must recover even when the base has disappeared.
            File(file.path+".bak").writeBytes(acknowledged);assertTrue(file.delete())
            assertEquals(cp,BoundedCostBoqCheckpointStore(AndroidCostBoqAtomicStorage(context)).load(cp.projectId))
            assertTrue(file.exists())
            assertNull(store.load(999))
        } finally { context.root.deleteRecursively() }
    }

    @Test fun corruptAndOversizeRecoveryRetainedAndCannotBeSilentlyReplaced() {
        val context=DraftContext()
        try {
            val store=BoundedCostBoqCheckpointStore(AndroidCostBoqAtomicStorage(context))
            val cp=checkpoint(draft());store.write(cp)
            val file=context.checkpoint(10);val bad=file.readBytes().also{it[80]=0};file.writeBytes(bad)
            assertThrows(Exception::class.java){store.load(10)}
            assertThrows(Exception::class.java){store.write(cp)}
            assertArrayEquals(bad,file.readBytes())
            file.outputStream().use{it.write(ByteArray(BoundedCostBoqCheckpointStore.MAX_BYTES+1))}
            assertThrows(Exception::class.java){store.load(10)}
            assertEquals(BoundedCostBoqCheckpointStore.MAX_BYTES+1L,file.length())
            store.delete(10);assertNull(store.load(10))
        } finally { context.root.deleteRecursively() }
    }
}
