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
    private fun heldViewModel(legacy: Boolean = false): SlabViewModel {
        val vm = SlabViewModel(mockk(relaxed = true), mockk(relaxed = true))
        val result = if (legacy) com.google.gson.Gson().fromJson(
            """{"type":"HOLLOW_BLOCK","code":"ECP","isSafe":true}""", CalculatorEngine.SlabResult::class.java
        ) else CalculatorEngine.SlabResult(
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
    @Test fun `old result with no flag also stops shared exports`() {
        val vm = heldViewModel(legacy = true)
        val context = RuntimeEnvironment.getApplication<android.app.Application>()
        assertNull(vm.buildDxfDrawing())
        var callbacks = 0
        vm.exportToPdf(context) { file -> callbacks++; assertNull(file) }
        vm.exportToDxf(context) { file -> callbacks++; assertNull(file) }
        assertEquals(2, callbacks)
    }

    @Test fun `old saved record cannot produce CSV execution output`() {
        val old = com.civileg.app.db.Design(
            projectId = 2L, name = "Archived slab", type = com.civileg.app.db.DesignType.SLAB,
            codeUsed = "Egyptian Code", inputData = "{}", isSafe = true,
            results = """{"type":"HOLLOW_BLOCK","code":"ECP","isSafe":true}"""
        )
        assertNull(com.civileg.app.utils.ExcelExporter.exportDesignToCsv(RuntimeEnvironment.getApplication(), old))
    }

}
