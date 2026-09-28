package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.base.WaffleSlabDesign
import com.civileg.app.domain.calculations.base.WaffleSlabDesign.WaffleSlabInput
import com.civileg.app.domain.calculations.ecp.ECPWaffleSlabDesign
import com.civileg.app.domain.calculations.aci.ACIWaffleSlabDesign
import com.civileg.app.domain.calculations.sbc.SBCWaffleSlabDesign
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Waffle Slab Design (ECP 203-2020, ACI 318-19, SBC 304-2018).
 * Tests verify fundamental structural design behavior for two-way ribbed slabs.
 */
class WaffleSlabTest {

    private lateinit var ecpWaffle: WaffleSlabDesign
    private lateinit var aciWaffle: WaffleSlabDesign
    private lateinit var sbcWaffle: WaffleSlabDesign

    @Before
    fun setup() {
        ecpWaffle = ECPWaffleSlabDesign()
        aciWaffle = ACIWaffleSlabDesign()
        sbcWaffle = SBCWaffleSlabDesign()
    }

    @Test
    fun `All WaffleSlabDesign instances are created successfully`() {
        assertNotNull(ecpWaffle)
        assertNotNull(aciWaffle)
        assertNotNull(sbcWaffle)
    }

    @Test
    fun `All implementations are instance of WaffleSlabDesign`() {
        assertTrue(ecpWaffle is WaffleSlabDesign)
        assertTrue(aciWaffle is WaffleSlabDesign)
        assertTrue(sbcWaffle is WaffleSlabDesign)
    }

    @Test
    fun `CalculationFactory returns correct WaffleSlabDesign for each code`() {
        val factory = CalculationFactory

        val ecp = factory.getWaffleSlabDesign(DesignCode.ECP)
        val aci = factory.getWaffleSlabDesign(DesignCode.ACI)
        val sbc = factory.getWaffleSlabDesign(DesignCode.SBC)

        assertEquals("com.civileg.app.domain.calculations.ecp.ECPWaffleSlabDesign", ecp.javaClass.name)
        assertEquals("com.civileg.app.domain.calculations.aci.ACIWaffleSlabDesign", aci.javaClass.name)
        assertEquals("com.civileg.app.domain.calculations.sbc.SBCWaffleSlabDesign", sbc.javaClass.name)
    }

    @Test
    fun `ECP Waffle Slab full design returns valid result`() {
        val input = WaffleSlabInput(
            lx = 6000.0, ly = 7500.0,
            ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 300.0,
            toppingThickness = 50.0, solidHeadSize = 1000.0,
            columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 400.0,
            liveLoad = 3.0, deadLoad = 3.0,
            clearCover = 25.0, designCode = DesignCode.ECP
        )

        val result = ecpWaffle.design(input)

        assertNotNull(result)
        assertTrue(result.concreteVolume > 0)
        assertTrue(result.steelWeight > 0)
        assertTrue(result.cost > 0)
        assertNotNull(result.ribDesign)
        assertNotNull(result.solidHeadDesign)
        assertNotNull(result.punchingShearCheck)
        assertNotNull(result.deflectionCheck)
    }

    @Test
    fun `ACI Waffle Slab full design returns valid result`() {
        val input = WaffleSlabInput(
            lx = 6000.0, ly = 7500.0,
            ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 300.0,
            toppingThickness = 50.0, solidHeadSize = 1000.0,
            columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 60.0,  // ACI uses ksi
            liveLoad = 3.0, deadLoad = 3.0,
            clearCover = 25.0, designCode = DesignCode.ACI
        )

        val result = aciWaffle.design(input)

        assertNotNull(result)
        assertTrue(result.concreteVolume > 0)
        assertTrue(result.steelWeight > 0)
        assertNotNull(result.ribDesign)
        assertNotNull(result.solidHeadDesign)
    }

    @Test
    fun `SBC Waffle Slab full design returns valid result`() {
        val input = WaffleSlabInput(
            lx = 6000.0, ly = 7500.0,
            ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 300.0,
            toppingThickness = 50.0, solidHeadSize = 1000.0,
            columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 400.0,
            liveLoad = 3.0, deadLoad = 3.0,
            clearCover = 25.0, designCode = DesignCode.SBC
        )

        val result = sbcWaffle.design(input)

        assertNotNull(result)
        assertTrue(result.concreteVolume > 0)
        assertTrue(result.steelWeight > 0)
        assertNotNull(result.ribDesign)
        assertNotNull(result.solidHeadDesign)
    }

    @Test
    fun `Rib design produces valid reinforcement for all codes`() {
        val loadCombination = LoadCombination.DEAD_LIVE

        val ecpRib = ecpWaffle.designRib(30.0, 400.0, 150.0, 300.0, 600.0, 6000.0, 15.0, loadCombination)
        val aciRib = aciWaffle.designRib(30.0, 60.0, 150.0, 300.0, 600.0, 6000.0, 15.0, loadCombination)
        val sbcRib = sbcWaffle.designRib(30.0, 400.0, 150.0, 300.0, 600.0, 6000.0, 15.0, loadCombination)

        assertNotNull(ecpRib.flexureReinforcement)
        assertTrue(ecpRib.flexureReinforcement.bars > 0)
        assertTrue(ecpRib.flexureReinforcement.diameter > 0)

        assertNotNull(aciRib.flexureReinforcement)
        assertTrue(aciRib.flexureReinforcement.bars > 0)

        assertNotNull(sbcRib.flexureReinforcement)
        assertTrue(sbcRib.flexureReinforcement.bars > 0)
    }

    @Test
    fun `Solid head design produces valid reinforcement for all codes`() {
        val ecpHead = ecpWaffle.designSolidHead(30.0, 400.0, 1000.0, 150.0, 400.0, 1000.0, 50.0)
        val aciHead = aciWaffle.designSolidHead(30.0, 60.0, 1000.0, 150.0, 400.0, 1000.0, 50.0)
        val sbcHead = sbcWaffle.designSolidHead(30.0, 400.0, 1000.0, 150.0, 400.0, 1000.0, 50.0)

        assertNotNull(ecpHead.flexureReinforcement)
        assertTrue(ecpHead.flexureReinforcement.bars > 0)
        assertNotNull(ecpHead.punchingShear)

        assertNotNull(aciHead.flexureReinforcement)
        assertTrue(aciHead.flexureReinforcement.bars > 0)

        assertNotNull(sbcHead.flexureReinforcement)
        assertTrue(sbcHead.flexureReinforcement.bars > 0)
    }

    @Test
    fun `Punching shear check works for all codes`() {
        val ecpPunch = ecpWaffle.checkPunchingShear(30.0, 400.0, 1000.0, 400.0, 1000.0, 200.0)
        val aciPunch = aciWaffle.checkPunchingShear(30.0, 60.0, 1000.0, 400.0, 1000.0, 200.0)
        val sbcPunch = sbcWaffle.checkPunchingShear(30.0, 400.0, 1000.0, 400.0, 1000.0, 200.0)

        assertNotNull(ecpPunch)
        assertTrue(ecpPunch.vu >= 0.0)
        assertTrue(ecpPunch.vc > 0.0)

        assertNotNull(aciPunch)
        assertTrue(aciPunch.vu >= 0.0)
        assertTrue(aciPunch.vc > 0.0)

        assertNotNull(sbcPunch)
        assertTrue(sbcPunch.vu >= 0.0)
        assertTrue(sbcPunch.vc > 0.0)
    }

    @Test
    fun `Deflection check works for all codes`() {
        val loadCombination = LoadCombination.DEAD_LIVE

        val ecpDefl = ecpWaffle.checkDeflection(6000.0, 350.0, 150.0, 300.0, 30.0, 400.0, 500.0, loadCombination)
        val aciDefl = aciWaffle.checkDeflection(6000.0, 350.0, 150.0, 300.0, 30.0, 60.0, 500.0, loadCombination)
        val sbcDefl = sbcWaffle.checkDeflection(6000.0, 350.0, 150.0, 300.0, 30.0, 400.0, 500.0, loadCombination)

        assertNotNull(ecpDefl)
        assertTrue(ecpDefl.allowable > 0.0)
        assertTrue(ecpDefl.immediate >= 0.0)
        assertTrue(ecpDefl.longTerm >= 0.0)

        assertNotNull(aciDefl)
        assertTrue(aciDefl.allowable > 0.0)

        assertNotNull(sbcDefl)
        assertTrue(sbcDefl.allowable > 0.0)
    }

    @Test
    fun `Different codes produce different results for same geometry`() {
        val input = WaffleSlabInput(
            lx = 6000.0, ly = 7500.0,
            ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 300.0,
            toppingThickness = 50.0, solidHeadSize = 1000.0,
            columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 400.0,
            liveLoad = 3.0, deadLoad = 3.0,
            clearCover = 25.0
        )

        val ecpResult = ecpWaffle.design(input.copy(designCode = DesignCode.ECP))
        val aciResult = aciWaffle.design(input.copy(designCode = DesignCode.ACI))
        val sbcResult = sbcWaffle.design(input.copy(designCode = DesignCode.SBC))

        // Each code should produce different steel weights due to different load factors and formulas
        assertTrue(ecpResult.steelWeight > 0.0)
        assertTrue(aciResult.steelWeight > 0.0)
        assertTrue(sbcResult.steelWeight > 0.0)

        // At least one should be different (different safety factors)
        val allDifferent = (ecpResult.steelWeight != aciResult.steelWeight) ||
                          (aciResult.steelWeight != sbcResult.steelWeight) ||
                          (ecpResult.steelWeight != sbcResult.steelWeight)
        assertTrue(allDifferent)
    }
}