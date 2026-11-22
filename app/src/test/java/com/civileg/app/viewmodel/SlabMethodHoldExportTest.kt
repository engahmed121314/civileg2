package com.civileg.app.viewmodel

import androidx.lifecycle.MutableLiveData
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SlabMethodHoldExportTest {
    private fun heldViewModel(): SlabViewModel {
        val vm = SlabViewModel(mockk(relaxed = true), mockk(relaxed = true))
        val result = CalculatorEngine.SlabResult(
            type = CalculatorEngine.SlabType.HOLLOW_BLOCK,
            safetyAssessmentBlocked = true
        )
        SlabViewModel::class.java.getDeclaredField("_result").apply {
            isAccessible = true
            set(vm, MutableLiveData(result))
        }
        return vm
    }

    @Test fun `held shared result has no DXF drawing`() {
        assertNull(heldViewModel().buildDxfDrawing())
    }

    @Test fun `held shared result stops PDF export before drawing or report creation`() {
        val vm = heldViewModel()
        var called = false
        vm.exportToPdf(RuntimeEnvironment.getApplication()) { file ->
            called = true
            assertNull(file)
        }
        assertTrue(called)
        assertNotNull(vm.error.value)
        assertFalse(vm.isExporting.value == true)
    }

    @Test fun `held shared result stops DXF export before file creation`() {
        val vm = heldViewModel()
        var called = false
        vm.exportToDxf(RuntimeEnvironment.getApplication()) { file ->
            called = true
            assertNull(file)
        }
        assertTrue(called)
        assertNotNull(vm.error.value)
    }
}
