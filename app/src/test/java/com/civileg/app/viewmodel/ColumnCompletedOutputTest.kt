package com.civileg.app.viewmodel

import android.content.Context
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.entities.ColumnDesignSnapshot
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.SettingsManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic engine/repository boundaries: consistency and stale-output containment, not engineering proof. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ColumnCompletedOutputTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: DesignRepository
    private lateinit var engine: CalculatorEngine
    private lateinit var vm: ColumnViewModel
    private var failCalculation = false

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        engine = mockk()
        val settings = mockk<SettingsManager>()
        every { settings.defaultDesignCode } returns DesignCode.ECP
        every { engine.designColumn(width = any(), depth = any(), pu = any(), mx = any(), my = any(),
            fcu = any(), fy = any(), code = any(), isCircular = any(), connectedSlab = any(), hasCap = any(),
            clearHeight = any(), preferredDiameter = any(), autoOptimize = any(), manualNumBars = any(),
            autoIncludeSelfWeight = any(), isSeismic = any(), reinforcementIntent = any()) } answers {
            if (failCalculation) throw IllegalArgumentException("Unsupported manual candidate")
            CalculatorEngine.ColumnResult(width = 300.0, depth = 300.0, code = DesignCode.ECP,
                reinforcement = CalculatorEngine.ReinforcementBar(numBars = 10, diameter = 16))
        }
        vm = ColumnViewModel(repository, engine, settings)
    }

    @After fun teardown() { Dispatchers.resetMain(); unmockkAll() }

    private fun completed(): ColumnDesignSnapshot {
        vm.calculateManual()
        dispatcher.scheduler.advanceUntilIdle()
        return requireNotNull(vm.uiState.value.completedDesign)
    }

    @Test fun `completed drawing pixels cannot be changed by later caller edits or recycling`() {
        val original = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
        original.eraseColor(android.graphics.Color.RED)
        val capture = ColumnDrawingCapture("pixel-fixture-not-engineering-proof", original)
        original.eraseColor(android.graphics.Color.BLUE)
        original.recycle()
        val first = capture.bitmap
        assertEquals(android.graphics.Color.RED, first.getPixel(0, 0))
        first.recycle()
        assertEquals(android.graphics.Color.RED, capture.bitmap.getPixel(0, 0))
    }

    @Test fun `manual diameter and count edits each invalidate completed output and disable optimization`() {
        completed()
        vm.updateInputs(preferredDiameter = "18")
        assertFalse(vm.uiState.value.autoOptimize)
        assertNull(vm.uiState.value.completedDesign)
        vm.reset()
        completed()
        vm.updateInputs(manualNumBars = "12")
        assertFalse(vm.uiState.value.autoOptimize)
        assertNull(vm.uiState.value.completedDesign)
    }

    @Test fun `input edit while calculation is queued prevents old completion publication`() {
        vm.calculateManual()
        assertTrue(vm.uiState.value.isLoading)
        vm.updateInputs(height = "4.5")
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.completedDesign)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun `owned calculation rejection cannot keep an old result available`() {
        completed()
        failCalculation = true
        vm.calculateManual()
        assertNull(vm.uiState.value.completedDesign)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.result)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.errors.single().contains("Unsupported manual candidate"))
    }

    @Test fun `invalid current numeric input cannot leave a prior snapshot available`() {
        completed()
        vm.updateInputs(height = "")
        vm.calculateManual()
        assertNull(vm.uiState.value.completedDesign)
        assertTrue(vm.uiState.value.errors.isNotEmpty())
    }

    @Test fun `completed input retains actual actions materials height and self weight convention`() {
        val snapshot = completed()
        assertEquals(3.0, snapshot.input().heightM, 0.0)
        assertEquals(25.0, snapshot.input().fcuMpa, 0.0)
        assertEquals(400.0, snapshot.input().fyMpa, 0.0)
        assertEquals(1000.0, snapshot.input().enteredAxialLoadKn, 0.0)
        assertTrue(snapshot.input().autoIncludeSelfWeight)
        assertTrue(snapshot.input().actionBasis.endsWith("UNVERIFIED"))
        vm.updateInputs(height = "7.0", fcu = "60", fy = "500")
        assertFalse(vm.isCurrent(snapshot))
        assertEquals(3.0, snapshot.input().heightM, 0.0)
        assertEquals(25.0, snapshot.input().fcuMpa, 0.0)
    }

    @Test fun `Pro diameter caller retains explicit requested layout intent despite automatic flag`() {
        vm.calculateColumnPro(300.0, 300.0, 3.0, 25.0, 400.0, 500.0, 25, DesignCode.ECP)
        dispatcher.scheduler.advanceUntilIdle()
        val input = requireNotNull(vm.uiState.value.completedDesign).input()
        assertEquals(com.civileg.app.domain.entities.ColumnReinforcementIntent.REQUESTED_LAYOUT, input.reinforcementIntent)
        verify(exactly = 1) { engine.designColumn(width = any(), depth = any(), pu = any(), mx = any(), my = any(),
            fcu = any(), fy = any(), code = any(), isCircular = any(), connectedSlab = any(), hasCap = any(),
            clearHeight = any(), preferredDiameter = 25, autoOptimize = true, manualNumBars = null,
            autoIncludeSelfWeight = any(), isSeismic = any(),
            reinforcementIntent = com.civileg.app.domain.entities.ColumnReinforcementIntent.REQUESTED_LAYOUT) }
    }

    @Test fun `reselecting automatic engine layout clears a prior manual count`() {
        vm.updateInputs(manualNumBars = "12")
        vm.updateAutoOptimize(true)
        dispatcher.scheduler.advanceUntilIdle()
        val input = requireNotNull(vm.uiState.value.completedDesign).input()
        assertTrue(input.autoOptimize)
        assertNull(input.manualBarCount)
        assertEquals(com.civileg.app.domain.entities.ColumnReinforcementIntent.AUTOMATIC_ENGINE_LAYOUT, input.reinforcementIntent)
    }

    @Test fun `stale snapshot cannot export or save through current-output actions`() {
        val snapshot = completed()
        vm.updateInputs(height = "4.0")
        var exportCalled = false
        vm.exportToPdf(mockk<Context>(), snapshot) { exportCalled = true; assertNull(it) }
        assertTrue(exportCalled)
        var saved: Boolean? = null
        vm.saveColumn(1L, "Old result", snapshot) { saved = it }
        assertEquals(false, saved)
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { repository.saveColumnDesignSnapshot(any(), any(), any()) }
    }

    @Test fun `failed persistence reports failure and retains the current completed snapshot`() {
        val snapshot = completed()
        coEvery { repository.saveColumnDesignSnapshot(any(), any(), any()) } throws IllegalStateException("Save fixture failure")
        var saved: Boolean? = null
        vm.saveColumn(1L, "Frozen column", snapshot) { saved = it }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, saved)
        assertFalse(vm.uiState.value.isSaving)
        assertTrue(vm.isCurrent(snapshot))
        assertTrue(vm.errorMessage.value.orEmpty().contains("Save fixture failure"))
    }

    @Test fun `accepted pending save uses captured materials not later edits and rejects duplicate confirm`() {
        val snapshot = completed()
        var first: Boolean? = null
        var duplicate: Boolean? = null
        vm.saveColumn(1L, "Frozen column", snapshot) { first = it }
        vm.saveColumn(1L, "Frozen column", snapshot) { duplicate = it }
        assertEquals(false, duplicate)
        vm.updateInputs(fcu = "99", fy = "600", height = "9")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, first)
        coVerify(exactly = 1) { repository.saveColumnDesignSnapshot(1L, "Frozen column", match {
            it.revisionSha256 == snapshot.revisionSha256 && it.input().fcuMpa == 25.0 &&
                it.input().fyMpa == 400.0 && it.input().heightM == 3.0
        }) }
        assertFalse(vm.uiState.value.isSaving)
    }
}
