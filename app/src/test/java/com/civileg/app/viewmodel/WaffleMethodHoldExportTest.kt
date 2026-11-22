package com.civileg.app.viewmodel

import androidx.lifecycle.MutableLiveData
import com.civileg.app.domain.calculations.base.WaffleSlabDesign
import com.civileg.app.domain.calculations.ecp.ECPWaffleSlabDesign
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WaffleMethodHoldExportTest {
    private fun held() = WaffleSlabViewModel(mockk(relaxed = true)).also { vm ->
        val result = ECPWaffleSlabDesign().design(WaffleSlabDesign.WaffleSlabInput())
        WaffleSlabViewModel::class.java.getDeclaredField("_result").apply {
            isAccessible = true
            set(vm, MutableLiveData(result))
        }
    }

    @Test fun heldResultHasNoDxfDrawing() { assertNull(held().buildDxfDrawing()) }
    @Test fun heldResultStopsPdfBeforeReportCreation() {
        val vm = held()
        var called = false
        vm.exportToPdf(RuntimeEnvironment.getApplication()) { file -> called = true; assertNull(file) }
        assertTrue(called)
        assertNotNull(vm.error.value)
    }
    @Test fun heldResultStopsDxfBeforeFileCreation() {
        val vm = held()
        var called = false
        vm.exportToDxf(RuntimeEnvironment.getApplication()) { file -> called = true; assertNull(file) }
        assertTrue(called)
        assertNotNull(vm.error.value)
    }
}
