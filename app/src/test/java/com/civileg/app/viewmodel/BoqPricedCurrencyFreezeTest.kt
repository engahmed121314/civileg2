package com.civileg.app.viewmodel

import com.civileg.app.db.Design
import com.civileg.app.db.DesignType
import com.civileg.app.db.QuantityBasis
import com.civileg.app.domain.entities.MaterialPrices
import com.civileg.app.domain.entities.SavedMaterialPrices
import com.civileg.app.domain.entities.SavedPrice
import com.civileg.app.domain.repository.ProjectRepository
import com.civileg.app.domain.usecases.CalculateElementBoq
import com.civileg.app.utils.EstimationEngine
import androidx.lifecycle.Observer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The frozen result keeps the currency it was priced in when Settings changes, only the latest
 * request may publish, a cleared or superseded request never republishes, and the explicit-price
 * path reports an honestly unknown (null) currency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoqPricedCurrencyFreezeTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var currency: MutableStateFlow<String>
    private lateinit var saved: MutableStateFlow<SavedMaterialPrices>
    private lateinit var vm: BOQViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        currency = MutableStateFlow("EGP")
        saved = MutableStateFlow(
            SavedMaterialPrices(
                concrete = SavedPrice(1000.0, "EGP"),
                steel = SavedPrice(20000.0, "EGP"),
                formwork = SavedPrice(1000.0, "EGP")
            )
        )
        val repo = mockk<ProjectRepository>(relaxed = true)
        every { repo.getCurrency() } returns currency
        every { repo.getSavedPrices() } returns saved
        vm = BOQViewModel(mockk<EstimationEngine>(relaxed = true), CalculateElementBoq(), repo)
    }

    /** A repo whose saved-price read stays suspended until [gate] completes: the race window is controllable. */
    private fun gatedVm(gate: CompletableDeferred<SavedMaterialPrices>): BOQViewModel {
        val repo = mockk<ProjectRepository>(relaxed = true)
        every { repo.getCurrency() } returns currency
        every { repo.getSavedPrices() } returns flow { emit(gate.await()) }
        return BOQViewModel(mockk<EstimationEngine>(relaxed = true), CalculateElementBoq(), repo)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun design(id: Long) = Design(
        id = id, projectId = 1L, type = DesignType.BEAM, name = "B$id", inputData = "{}", results = "{}", isSafe = true,
        codeUsed = "ECP", concreteVolume = 2.0, steelWeight = 1000.0, totalCost = 0.0,
        quantityBasis = QuantityBasis.MEASURED
    )

    @Test fun frozenResultKeepsItsPricedCurrencyWhenSettingsCurrencyChanges() = runTest(dispatcher) {
        vm.calculateDesignBoq(design(1L))
        advanceUntilIdle()
        val r = vm.boqResult.value!!
        assertEquals("EGP", r.currency)
        assertEquals(1L, r.designId)
        assertTrue(r.total > 0.0)

        // actually observe the lazy asLiveData, with cleanup
        val seen = mutableListOf<String>()
        val observer = Observer<String> { seen.add(it) }
        vm.activeCurrency.observeForever(observer)
        try {
            advanceUntilIdle()
            assertEquals("EGP", seen.last())
            currency.value = "SAR"
            advanceUntilIdle()
            assertEquals("SAR", seen.last())
        } finally {
            vm.activeCurrency.removeObserver(observer)
        }

        // the live source moved; the frozen result did not
        val r2 = vm.boqResult.value!!
        assertEquals("EGP", r2.currency)
        assertEquals(r.items, r2.items)
        assertEquals(r.total, r2.total, 0.0)
    }

    @Test fun recalculationUnderNewCurrencyDoesNotReuseOtherCurrencyPrices() = runTest(dispatcher) {
        vm.calculateDesignBoq(design(1L))
        advanceUntilIdle()
        currency.value = "SAR"
        vm.calculateDesignBoq(design(1L))
        advanceUntilIdle()
        val r = vm.boqResult.value!!
        assertEquals("SAR", r.currency)
        assertEquals(0.0, r.total, 0.0)
        assertTrue(r.items.isNotEmpty())
        assertTrue(r.items.none { it.isPriced })
    }

    @Test fun supersededRequestNeverPublishes() = runTest(dispatcher) {
        vm.calculateDesignBoq(design(1L))
        vm.calculateDesignBoq(design(2L))
        advanceUntilIdle()
        val r = vm.boqResult.value!!
        assertEquals(2L, r.designId)
    }

    @Test fun clearedResultCannotBeRepopulatedByAnInflightRequest() = runTest(dispatcher) {
        vm.calculateDesignBoq(design(1L))
        vm.clearResult()
        advanceUntilIdle()
        assertNull(vm.boqResult.value)
    }

    @Test fun clearedWhileReadSuspendedNeverPublishesAndResetsLoading() = runTest(dispatcher) {
        val gate = CompletableDeferred<SavedMaterialPrices>()
        val gvm = gatedVm(gate)
        gvm.calculateDesignBoq(design(1L))
        runCurrent()
        assertTrue(gvm.isLoading.value!!)
        gvm.clearResult()
        assertFalse(gvm.isLoading.value!!)
        gate.complete(saved.value)
        advanceUntilIdle()
        assertNull(gvm.boqResult.value)
        assertFalse(gvm.isLoading.value!!)
    }

    @Test fun supersededWhileReadSuspendedOnlyTheLatestPublishes() = runTest(dispatcher) {
        val gate = CompletableDeferred<SavedMaterialPrices>()
        val gvm = gatedVm(gate)
        gvm.calculateDesignBoq(design(1L))
        runCurrent()
        gvm.calculateDesignBoq(design(2L))
        runCurrent()
        gate.complete(saved.value)
        advanceUntilIdle()
        val r = gvm.boqResult.value!!
        assertEquals(2L, r.designId)
        assertFalse(gvm.isLoading.value!!)
    }

    @Test fun sameDesignRecalculationInvalidatesThePreviousResultAtCallTime() = runTest(dispatcher) {
        vm.calculateDesignBoq(design(1L))
        advanceUntilIdle()
        assertEquals(1L, vm.boqResult.value!!.designId)
        vm.calculateDesignBoq(design(1L))
        assertNull(vm.boqResult.value)
        advanceUntilIdle()
        assertEquals(1L, vm.boqResult.value!!.designId)
    }

    @Test fun ownedFailureClearsThePreviousResult() = runTest(dispatcher) {
        val repo = mockk<ProjectRepository>(relaxed = true)
        var failRead = false
        every { repo.getCurrency() } returns currency
        every { repo.getSavedPrices() } returns flow {
            if (failRead) throw IllegalStateException("Test read failure")
            emit(saved.value)
        }
        val owner = BOQViewModel(mockk<EstimationEngine>(relaxed = true), CalculateElementBoq(), repo)
        owner.calculateDesignBoq(design(1L))
        advanceUntilIdle()
        assertEquals(1L, owner.boqResult.value!!.designId)
        failRead = true
        owner.calculateDesignBoq(design(1L))
        assertNull(owner.boqResult.value)
        advanceUntilIdle()
        assertNull(owner.boqResult.value)
        assertFalse(owner.isLoading.value!!)
        assertTrue(owner.errorMessage.value.orEmpty().isNotBlank())
    }

    @Test fun explicitPricePathPublishesNullCurrencyAndNoSavedPriceMetadata() = runTest(dispatcher) {
        vm.calculateDesignBoq(design(1L))
        advanceUntilIdle()
        vm.calculateDesignBoq(design(1L), MaterialPrices())
        advanceUntilIdle()
        val r = vm.boqResult.value!!
        assertNull(r.currency)
        assertEquals(0, r.unpricedCount)
        assertEquals(0, r.unknownCurrencyCount)
        assertEquals(1L, r.designId)
    }
}
