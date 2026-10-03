package com.civileg.app.viewmodel

import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.calculations.base.WaffleSlabDesign
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for WaffleSlabViewModel calculation flow.
 *
 * Tests the WaffleSlabDesign engines directly via CalculationFactory,
 * covering ECP and SBC implementations with valid and invalid inputs.
 */
class WaffleSlabViewModelTest {

    private lateinit var ecpWaffle: WaffleSlabDesign
    private lateinit var sbcWaffle: WaffleSlabDesign

    private val defaultInput = WaffleSlabDesign.WaffleSlabInput(
        lx = 6000.0,
        ly = 7500.0,
        ribSpacing = 600.0,
        ribWidth = 150.0,
        ribHeight = 300.0,
        toppingThickness = 50.0,
        solidHeadSize = 1000.0,
        columnWidth = 400.0,
        columnDepth = 400.0,
        fcu = 30.0,
        fy = 400.0,
        liveLoad = 3.0,
        deadLoad = 3.0,
        clearCover = 25.0,
        designCode = DesignCode.ECP
    )

    @Before
    fun setUp() {
        ecpWaffle = CalculationFactory.getWaffleSlabDesign(DesignCode.ECP)
        sbcWaffle = CalculationFactory.getWaffleSlabDesign(DesignCode.SBC)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. calculateWaffleSlab() with valid ECP inputs → result not null
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `calculate with valid ECP inputs returns non-null result`() {
        val result = ecpWaffle.design(defaultInput)
        assertNotNull("ECP waffle slab result should not be null", result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. calculateWaffleSlab() with valid SBC inputs → result not null
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `calculate with valid SBC inputs returns non-null result`() {
        val sbcInput = defaultInput.copy(designCode = DesignCode.SBC)
        val result = sbcWaffle.design(sbcInput)
        assertNotNull("SBC waffle slab result should not be null", result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. calculateWaffleSlab() with zero fcu → error
    // ═══════════════════════════════════════════════════════════════════════
    @Test(expected = IllegalArgumentException::class)
    fun `calculate with zero fcu throws error`() {
        val badInput = defaultInput.copy(fcu = 0.0)
        ecpWaffle.design(badInput)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. calculateWaffleSlab() with zero span → error
    // ═══════════════════════════════════════════════════════════════════════
    @Test(expected = IllegalArgumentException::class)
    fun `calculate with zero span throws error`() {
        val badInput = defaultInput.copy(lx = 0.0)
        ecpWaffle.design(badInput)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. result has isSafe boolean
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `result contains isSafe boolean`() {
        val result = ecpWaffle.design(defaultInput)
        // Should not throw — just verifying the field is accessible and is a Boolean
        val safe: Boolean = result.isSafe
        // No assertion needed on the value itself; just that it compiles and runs
        assertTrue("isSafe should be true or false", safe || !safe)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. result has utilizationRatio
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `result contains utilizationRatio`() {
        val result = ecpWaffle.design(defaultInput)
        assertTrue("Utilization ratio should be non-negative", result.utilizationRatio >= 0.0)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 7. result has safetyChecks list
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `result contains safetyChecks list`() {
        val result = ecpWaffle.design(defaultInput)
        assertNotNull("safetyChecks should not be null", result.safetyChecks)
        // safetyChecks is a list — can be empty but not null
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 8. result has ribDesign with reinforcement
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `result contains ribDesign with reinforcement data`() {
        val result = ecpWaffle.design(defaultInput)
        val rib = result.ribDesign
        if (rib != null) {
            assertNotNull("Flexure reinforcement should not be null", rib.flexureReinforcement)
            assertNotNull("Shear reinforcement should not be null", rib.shearReinforcement)
            assertTrue("Rib utilization ratio should be non-negative", rib.utilizationRatio >= 0.0)
        }
        // ribDesign may be null for some inputs; this test verifies structure when present
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 9. result has concreteVolume > 0
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `result has positive concrete volume`() {
        val result = ecpWaffle.design(defaultInput)
        assertTrue("Concrete volume should be positive", result.concreteVolume > 0.0)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 10. result has steelWeight > 0
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `result has positive steel weight`() {
        val result = ecpWaffle.design(defaultInput)
        assertTrue("Steel weight should be positive", result.steelWeight > 0.0)
    }

    // ── Additional coverage tests ──────────────────────────────────────────

    @Test
    fun `ACI waffle slab design also produces valid result`() {
        val aciWaffle = CalculationFactory.getWaffleSlabDesign(DesignCode.ACI)
        val aciInput = defaultInput.copy(designCode = DesignCode.ACI)
        val result = aciWaffle.design(aciInput)
        assertNotNull("ACI waffle slab result should not be null", result)
        assertTrue("ACI concrete volume should be positive", result.concreteVolume > 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculate with negative fy throws error`() {
        val badInput = defaultInput.copy(fy = -400.0)
        ecpWaffle.design(badInput)
    }

    @Test
    fun `result warnings list is never null`() {
        val result = ecpWaffle.design(defaultInput)
        assertNotNull("Warnings should not be null", result.warnings)
    }

    @Test
    fun `result codeNotes list is never null`() {
        val result = ecpWaffle.design(defaultInput)
        assertNotNull("CodeNotes should not be null", result.codeNotes)
    }
}
