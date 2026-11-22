package com.civileg.app.viewmodel

import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.entities.FrozenDesignRecord
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.core.engine.rc.ecp.Ecp203BarLayout
import com.civileg.core.engine.rc.ecp.Ecp203RectangularFlexure
import com.civileg.core.engine.rc.ecp.Ecp203RibbedSection
import com.civileg.core.engine.rc.ecp.Ecp203WebShear
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HordiFrozenSaveIntegrationTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<DesignRepository>(relaxed = true)
    private val saved = mutableListOf<FrozenDesignRecord>()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.saveHordiSlabSnapshot(any(), any(), capture(saved)) } returns 17L
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun calculate(vm: HordiSlabViewModel, moment: Double = 62.0) {
        val context = Ecp203RibbedSection.Context(
            25.0, 20.0, listOf(Ecp203BarLayout.Layer(2, 20.0)),
            Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED,
            Ecp203RibbedSection.Region.FLANGE_IN_COMPRESSION, 5000.0, 8.0,
            Ecp203WebShear.VerticalLinks(240.0,
                Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD, 8.0, 2, 200.0)
        )
        vm.calculateHordiSlab(30.0, 400.0, 200.0, 600.0, 350.0, 60.0,
            5000.0, moment, 10.0, LoadCombination.DEAD_LIVE, "ECP", context)
    }

    @Test fun `no completed result and a cleared result never save an invented case`() = runTest {
        val vm = HordiSlabViewModel(repository)
        vm.saveDesign(3L, "No result")
        runCurrent()
        calculate(vm)
        runCurrent()
        assertNotNull(vm.currentDesignSnapshot())
        vm.clearResult()
        vm.saveDesign(3L, "Cleared")
        runCurrent()
        assertTrue(saved.isEmpty())
        coVerify(exactly = 0) { repository.saveHordiSlabSnapshot(any(), any(), any()) }
        coVerify(exactly = 0) { repository.saveSlabDesign(any(), any(), any()) }
    }

    @Test fun `Save passes the actual completed record verbatim without generic slab reprojection`() = runTest {
        val vm = HordiSlabViewModel(repository)
        calculate(vm)
        runCurrent()
        val expected = requireNotNull(vm.currentDesignSnapshot())
        vm.saveDesign(3L, "R1")
        runCurrent()
        assertSame(expected.record, saved.single())
        assertTrue(saved.single().hasValidFingerprint())
        assertEquals(expected.inputJson, saved.single().inputJson)
        assertEquals(expected.resultJson, saved.single().resultJson)
        assertEquals(expected.revisionSha256, saved.single().revisionSha256)
        assertEquals(600.0, expected.result().calculatedSection!!.enteredRibPitchMm, 0.0)
        assertFalse(saved.single().inputJson.contains("spanY"))
        assertFalse(saved.single().resultJson.contains("concreteVolume"))
        assertFalse(saved.single().resultJson.contains("steelWeight"))
        assertTrue(expected.result().safetyAssessmentBlocked)
        coVerify(exactly = 1) { repository.saveHordiSlabSnapshot(3L, "R1", expected.record) }
        coVerify(exactly = 0) { repository.saveSlabDesign(any(), any(), any()) }
    }

    @Test fun `queued Save retains its captured revision after clear and a new calculation`() = runTest {
        val vm = HordiSlabViewModel(repository)
        calculate(vm)
        runCurrent()
        val first = requireNotNull(vm.currentDesignSnapshot())
        vm.saveDesign(3L, "First")
        assertTrue(saved.isEmpty())
        vm.clearResult()
        calculate(vm, 50.0)
        runCurrent()
        val second = requireNotNull(vm.currentDesignSnapshot())
        assertNotEquals(first.revisionSha256, second.revisionSha256)
        assertSame(first.record, saved.single())
        vm.saveDesign(3L, "Second")
        runCurrent()
        assertEquals(2, saved.size)
        assertSame(second.record, saved[1])
        assertEquals(62.0, first.input().designMoment, 0.0)
        assertEquals(50.0, second.input().designMoment, 0.0)
    }

    @Test fun `storage failure is surfaced without replacing or clearing the completed record`() = runTest {
        val vm = HordiSlabViewModel(repository)
        calculate(vm)
        runCurrent()
        val expected = requireNotNull(vm.currentDesignSnapshot())
        coEvery { repository.saveHordiSlabSnapshot(any(), any(), any()) } throws
            IllegalStateException("synthetic storage failure")
        vm.saveDesign(3L, "R1")
        runCurrent()
        assertTrue(vm.error.value.orEmpty().contains("synthetic storage failure"))
        assertSame(expected, vm.currentDesignSnapshot())
    }

    @Test fun `dialog-open snapshot survives a later completed calculation before confirm`() = runTest {
        val vm = HordiSlabViewModel(repository)
        calculate(vm)
        runCurrent()
        val dialogSnapshot = requireNotNull(vm.currentDesignSnapshot())
        vm.clearResult()
        calculate(vm, 50.0)
        runCurrent()
        val laterSnapshot = requireNotNull(vm.currentDesignSnapshot())
        var confirmedId: Long? = null
        vm.saveDesign(8L, "Explicit destination", dialogSnapshot) { confirmedId = it }
        assertNull(confirmedId)
        assertTrue(vm.isSaving.value == true)
        runCurrent()
        assertSame(dialogSnapshot.record, saved.single())
        assertSame(laterSnapshot, vm.currentDesignSnapshot())
        assertEquals(17L, confirmedId)
        assertFalse(vm.isSaving.value == true)
        coVerify(exactly = 1) {
            repository.saveHordiSlabSnapshot(8L, "Explicit destination", dialogSnapshot.record)
        }
    }

    @Test fun `dialog-open snapshot can save after clear and duplicate confirm cannot insert twice`() = runTest {
        val vm = HordiSlabViewModel(repository)
        calculate(vm)
        runCurrent()
        val dialogSnapshot = requireNotNull(vm.currentDesignSnapshot())
        vm.clearResult()
        assertNull(vm.currentDesignSnapshot())
        vm.saveDesign(9L, "Chosen snapshot", dialogSnapshot)
        vm.saveDesign(9L, "Chosen snapshot", dialogSnapshot)
        runCurrent()
        assertEquals(1, saved.size)
        assertSame(dialogSnapshot.record, saved.single())
        assertFalse(vm.isSaving.value == true)
        coVerify(exactly = 1) {
            repository.saveHordiSlabSnapshot(9L, "Chosen snapshot", dialogSnapshot.record)
        }
    }
}
