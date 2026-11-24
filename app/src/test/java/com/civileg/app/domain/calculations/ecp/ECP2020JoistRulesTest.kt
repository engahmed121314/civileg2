package com.civileg.app.domain.calculations.ecp

import org.junit.Assert.*
import org.junit.Test

class ECP2020JoistRulesTest {
    @Test fun `geometry uses clear block width not rib pitch`() {
        assertTrue(ECP2020JoistRules.geometry(150.0, 850.0, 300.0, 70.0).all { it.passed })
        assertFalse(ECP2020JoistRules.geometry(150.0, 851.0, 300.0, 71.0).all { it.passed })
    }
    @Test fun `geometry enforces all three limits`() {
        assertFalse(ECP2020JoistRules.geometry(99.0, 600.0, 300.0, 51.0).all { it.passed })
        assertFalse(ECP2020JoistRules.geometry(150.0, 750.0, 300.0, 59.0).all { it.passed })
    }
    @Test fun `cross rib limits preserve exact boundary cases`() {
        assertEquals(0, ECP2020JoistRules.crossRibCount(5000.0, 3.0))
        assertEquals(1, ECP2020JoistRules.crossRibCount(5001.0, 3.0))
        assertEquals(0, ECP2020JoistRules.crossRibCount(3999.0, 3.1))
        assertEquals(1, ECP2020JoistRules.crossRibCount(4000.0, 3.1))
        assertEquals(1, ECP2020JoistRules.crossRibCount(7000.0, 3.1))
        assertEquals(3, ECP2020JoistRules.crossRibCount(7001.0, 3.1))
    }
    @Test fun `minimum area retains gross web floor`() {
        assertEquals(81.0, ECP2020JoistRules.minimumFlexuralSteelMm2(30.0, 400.0, 150.0, 300.0, 260.0, 0.0, true), 1e-8)
        assertEquals(67.5, ECP2020JoistRules.minimumFlexuralSteelMm2(25.0, 400.0, 150.0, 300.0, 260.0, 0.0, true), 1e-8)
        assertEquals(112.5, ECP2020JoistRules.minimumFlexuralSteelMm2(25.0, 240.0, 150.0, 300.0, 260.0, 0.0, false), 1e-8)
    }
    @Test fun `thirty percent alternative is capped at equation area`() {
        assertEquals(130.0, ECP2020JoistRules.minimumFlexuralSteelMm2(60.0, 400.0, 150.0, 300.0, 260.0, 100.0, true), 1e-8)
        val equation = 0.225 * kotlin.math.sqrt(60.0) / 400 * 150 * 260
        assertEquals(equation, ECP2020JoistRules.minimumFlexuralSteelMm2(60.0, 400.0, 150.0, 300.0, 260.0, 150.0, true), 1e-8)
    }
    @Test fun `shear has no ACI resistance factor`() {
        assertEquals(0.16 * kotlin.math.sqrt(20.0) * 150 * 260 / 1000, ECP2020JoistRules.concreteOnlyShearKn(30.0, 150.0, 260.0), 1e-8)
    }
    @Test fun `development uses explicit top casting position and minimum`() {
        val bottom = ECP2020JoistRules.straightTensionDevelopmentMm(30.0, 400.0, 16, 300.0)
        val top = ECP2020JoistRules.straightTensionDevelopmentMm(30.0, 400.0, 16, 301.0)
        assertEquals(1.3 * bottom, top, 1e-8)
        assertTrue(bottom >= 640)
        assertEquals(640.0, ECP2020JoistRules.straightTensionDevelopmentMm(60.0, 350.0, 16, 0.0), 1e-8)
    }
    @Test fun `concrete only exception includes flange and web alternatives`() {
        assertTrue(ECP2020JoistRules.concreteOnlyGeometryEligible(250.0, 50.0, 150.0))
        assertFalse(ECP2020JoistRules.concreteOnlyGeometryEligible(251.0, 50.0, 150.0))
        assertTrue(ECP2020JoistRules.concreteOnlyGeometryEligible(300.0, 120.0, 150.0))
        assertTrue(ECP2020JoistRules.concreteOnlyGeometryEligible(300.0, 50.0, 600.0))
    }
    @Test fun `single bars use diameter or aggregate minimum not imported fixed spacing`() {
        assertEquals(30.0, ECP2020JoistRules.minimumIndividualBarClearMm(16, 20.0), 0.0)
        assertEquals(32.0, ECP2020JoistRules.minimumIndividualBarClearMm(32, 10.0), 0.0)
        assertTrue(ECP2020JoistRules.individualBarsFit(112.0, 25.0, 0.0, 16, 2, 20.0))
        assertFalse(ECP2020JoistRules.individualBarsFit(111.0, 25.0, 0.0, 16, 2, 20.0))
    }
    @Test fun `rib cover follows general branch strength boundary and largest bar`() {
        assertEquals(25.0, ECP2020JoistRules.minimumCrackControlRibCoverMm(25.0, com.civileg.app.domain.calculations.utils.JoistDetailCase.Exposure.PROTECTED_INTERIOR, 16.0), 0.0)
        assertEquals(20.0, ECP2020JoistRules.minimumCrackControlRibCoverMm(30.0, com.civileg.app.domain.calculations.utils.JoistDetailCase.Exposure.PROTECTED_INTERIOR, 16.0), 0.0)
        assertEquals(45.0, ECP2020JoistRules.minimumCrackControlRibCoverMm(25.0, com.civileg.app.domain.calculations.utils.JoistDetailCase.Exposure.CORROSIVE, 16.0), 0.0)
        assertEquals(40.0, ECP2020JoistRules.minimumCrackControlRibCoverMm(30.0, com.civileg.app.domain.calculations.utils.JoistDetailCase.Exposure.CORROSIVE, 16.0), 0.0)
        assertEquals(32.0, ECP2020JoistRules.minimumCrackControlRibCoverMm(30.0, com.civileg.app.domain.calculations.utils.JoistDetailCase.Exposure.PROTECTED_INTERIOR, 32.0), 0.0)
    }
    @Test fun `rib effective span uses greater option total slab depth and support center cap`() {
        assertEquals(5250.0, ECP2020JoistRules.simpleRibEffectiveSpanMm(5000.0, 250.0, 1000.0, 1000.0), 0.0)
        assertEquals(5300.0, ECP2020JoistRules.simpleRibEffectiveSpanMm(5000.0, 300.0, 1000.0, 1000.0), 0.0)
        assertEquals(5100.0, ECP2020JoistRules.simpleRibEffectiveSpanMm(5000.0, 300.0, 100.0, 100.0), 0.0)
        assertEquals(5500.0, ECP2020JoistRules.simpleRibEffectiveSpanMm(5000.0, 500.0, 800.0, 1200.0), 0.0)
    }
    @Test fun `cross ribs use calculated rib span at threshold instead of clear span`() {
        val effective = ECP2020JoistRules.simpleRibEffectiveSpanMm(6900.0, 300.0, 1000.0, 1000.0)
        assertEquals(7245.0, effective, 0.0)
        assertEquals(1, ECP2020JoistRules.crossRibCount(6900.0, 4.0))
        assertEquals(3, ECP2020JoistRules.crossRibCount(effective, 4.0))
    }
    @Test fun `huge bar count cannot overflow fit arithmetic`() {
        assertFalse(ECP2020JoistRules.individualBarsFit(150.0, 25.0, 0.0, 32, 67108865, 20.0))
    }
    @Test(expected = IllegalArgumentException::class) fun `rib effective span rejects nonfinite support`() {
        ECP2020JoistRules.simpleRibEffectiveSpanMm(5000.0, 250.0, Double.NaN, 1000.0)
    }
    @Test(expected = IllegalArgumentException::class) fun `fy500 on concrete25 is refused`() {
        ECP2020JoistRules.straightTensionDevelopmentMm(25.0, 500.0, 16, 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun `unknown live load is not zero`() {
        ECP2020JoistRules.crossRibCount(6000.0, Double.NaN)
    }
    @Test fun `cross rib minimums preserve main geometry and adopted bottom area without designing bars`() {
        val r = ECP2020JoistRules.crossRibMinimums(5250.0, 3.0, 150.0, 250.0, 402.123)!!
        assertEquals(1, r.count)
        assertEquals(150.0, r.minimumWebWidthMm, 0.0)
        assertEquals(250.0, r.minimumTotalDepthMm, 0.0)
        assertEquals(402.123, r.minimumBottomAreaMm2, 0.0)
        assertEquals(0.5, r.topToActualBottomAreaRatio, 0.0)
    }
    @Test fun `no cross rib minimums are fabricated below the code threshold`() {
        assertNull(ECP2020JoistRules.crossRibMinimums(5000.0, 3.0, 150.0, 250.0, 402.0))
        assertEquals(3, ECP2020JoistRules.crossRibMinimums(7001.0, 4.0, 150.0, 250.0, 402.0)!!.count)
    }
    @Test(expected = IllegalArgumentException::class) fun `cross rib minimums refuse an unknown main steel area`() {
        ECP2020JoistRules.crossRibMinimums(5250.0, 3.0, 150.0, 250.0, Double.NaN)
    }
}
