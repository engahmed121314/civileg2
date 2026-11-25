package com.civileg.app.viewmodel

import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.entities.DesignCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A shown result must not survive an edited input or a failed attempt, otherwise the old design can be
 * read, exported or saved as if it belonged to the new case.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StaleResultViewModelTest {

    private fun engine() = spyk(CalculatorEngine(mockk(relaxed = true)))
    private val repo = mockk<DesignRepository>(relaxed = true)

    @Test
    fun `tank result is dropped by clearResult`() {
        val vm = TankViewModel(repo, engine())
        vm.calculateTankPro(CalculatorEngine.TankType.RECTANGULAR_GROUND, 50.0, 3.5, 30.0, 400.0, 12, DesignCode.ECP)
        assertNotNull("precondition: a result exists", vm.result.value)
        vm.clearResult()
        assertNull(vm.result.value)
        assertNull(vm.validationReport.value)
    }

    @Test
    fun `tank failed attempt does not keep the previous result`() {
        val e = engine()
        val vm = TankViewModel(repo, e)
        vm.calculateTankPro(CalculatorEngine.TankType.RECTANGULAR_GROUND, 50.0, 3.5, 30.0, 400.0, 12, DesignCode.ECP)
        assertNotNull(vm.result.value)
        every { e.designTank(any(), any(), any(), any(), any(), any(), any()) } throws IllegalStateException("boom")
        vm.calculateTankPro(CalculatorEngine.TankType.RECTANGULAR_GROUND, 60.0, 3.5, 30.0, 400.0, 12, DesignCode.ECP)
        assertNull(vm.result.value)
    }

    @Test
    fun `retaining wall result is dropped by clearResult and by a failed attempt`() {
        val e = engine()
        val vm = RetainingWallViewModel(repo, e)
        vm.calculateRetainingWallPro(4.0, 18.0, 30.0, 10.0, 25.0, 400.0, 16, DesignCode.ECP)
        assertNotNull("precondition: a result exists", vm.result.value)
        vm.clearResult()
        assertNull(vm.result.value)
        vm.calculateRetainingWallPro(4.0, 18.0, 30.0, 10.0, 25.0, 400.0, 16, DesignCode.ECP)
        assertNotNull(vm.result.value)
        every { e.designRetainingWall(any(), any(), any(), any(), any(), any(), any(), any()) } throws IllegalStateException("boom")
        vm.calculateRetainingWallPro(5.0, 18.0, 30.0, 10.0, 25.0, 400.0, 16, DesignCode.ECP)
        assertNull(vm.result.value)
    }

    @Test
    fun `strap footing result is dropped by clearResult and by a failed attempt`() {
        val e = engine()
        val vm = StrapFootingViewModel(repo, e)
        vm.calculate(1200.0, 1800.0, 5000.0, 300.0, 600.0, 300.0, 700.0, 150.0, 25.0, 360.0, DesignCode.ECP, 18, 400.0)
        assertNotNull("precondition: a result exists", vm.result.value)
        vm.clearResult()
        assertNull(vm.result.value)
        vm.calculate(1200.0, 1800.0, 5000.0, 300.0, 600.0, 300.0, 700.0, 150.0, 25.0, 360.0, DesignCode.ECP, 18, 400.0)
        assertNotNull(vm.result.value)
        every { e.calculateStrapFooting(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws IllegalStateException("boom")
        vm.calculate(1300.0, 1800.0, 5000.0, 300.0, 600.0, 300.0, 700.0, 150.0, 25.0, 360.0, DesignCode.ECP, 18, 400.0)
        assertNull(vm.result.value)
    }

    @Test
    fun `flat slab input error drops the result and shows the message`() {
        val vm = FlatSlabViewModel(repo)
        vm.reportInputError("bad input")
        assertNull(vm.result.value)
        org.junit.Assert.assertEquals("bad input", vm.error.value)
    }
}
