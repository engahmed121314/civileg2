package com.civileg.app.costcontrol

import com.civileg.app.data.repository.*
import com.civileg.app.viewmodel.costcontrol.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BoqCheckpointQueueTest {
    private fun await(predicate:()->Boolean) {
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while(!predicate()){assertTrue("Timed out waiting for IO checkpoint",System.nanoTime()<deadline);Thread.sleep(5)}
    }
    @Test fun suspendedWriteImmediateDiscardReloadAndDeletedProjectQueuedKeep() {
        val main=UnconfinedTestDispatcher();Dispatchers.setMain(main)
        val context=DraftContext()
        try {
            fun runCase(delete:Boolean) {
                val project=if(delete)20L else 10L
                val real=BoundedCostBoqCheckpointStore(AndroidCostBoqAtomicStorage(context))
                val entered=CountDownLatch(1);val release=CountDownLatch(1)
                val writes=AtomicInteger();val deletes=AtomicInteger()
                val blocking=object:CostBoqCheckpointStore {
                    override fun load(projectId:Long)=real.load(projectId)
                    override fun write(value:CostBoqCheckpoint) {entered.countDown();check(release.await(5,TimeUnit.SECONDS));writes.incrementAndGet();real.write(value)}
                    override fun delete(projectId:Long){real.delete(projectId);deletes.incrementAndGet()}
                }
                val vm=CostBoqViewModel(NativeRepo(),blocking)
                vm.openProject(project);await{!vm.state.value.loading}
                vm.addItem();assertTrue(entered.await(5,TimeUnit.SECONDS));assertTrue(vm.state.value.checkpointPending)
                var navigated=false
                if(delete){vm.leaveEditor(true){navigated=true};vm.projectRemoved(project)}else vm.discardAndReload()
                release.countDown()
                await{deletes.get()==1}
                if(!delete)await{!vm.state.value.loading}
                assertNull(real.load(project));assertFalse(vm.state.value.checkpointPending)
                assertEquals(1,writes.get());assertFalse(navigated)
            }
            runCase(false);runCase(true)
        } finally {Dispatchers.resetMain();context.root.deleteRecursively()}
    }
}
