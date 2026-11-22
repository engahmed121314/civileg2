package com.civileg.core.engine.rc.ecp

import org.junit.Assert.*
import org.junit.Test

class Ecp203LoadCrackWidthTest {
    private fun input() = Ecp203LoadCrackWidth.Input(400.0,
        Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED,
        20.0, 600.0, 20000.0, 150.0, 100.0,
        Ecp203LoadCrackWidth.TensionDistribution.PURE_BENDING,
        Ecp203LoadCrackWidth.Loading.PERMANENT_OR_REPEATED,
        Ecp203LoadCrackWidth.Exposure.III_HARMFUL)
    private fun assess(i: Ecp203LoadCrackWidth.Input = input()) = Ecp203LoadCrackWidth.assessCrackedSection(i)

    @Test fun `independent permanent deformed vector follows the printed equations`() {
        val a = assess()
        assertEquals(0.03, a.rhoR, 1e-15)
        assertEquals(1.7, a.beta, 0.0)
        assertEquals(0.8, a.beta1, 0.0)
        assertEquals(0.5, a.beta2, 0.0)
        assertEquals(0.8, a.k1, 0.0)
        assertEquals(0.5, a.k2, 0.0)
        assertEquals(350.0 / 3.0, a.meanCrackSpacingMm, 1e-12)
        assertEquals(37.0 / 60000.0, a.meanRelativeSteelStrain, 1e-15)
        // Exact independent rational result: 4403/36000 mm.
        assertEquals(4403.0 / 36000.0, a.characteristicCrackWidthMm, 1e-14)
        assertTrue(a.widthLimitPass)
        assertEquals(220.0, a.sourceWorkingStressLimitMpa!!, 0.0)
        assertEquals(true, a.workingStressLimitPass)
        assertEquals(true, a.boundedChecksPass)
    }
    @Test fun `short term duration uses beta2 one without importing another code strain floor`() {
        val a = assess(input().copy(loading = Ecp203LoadCrackWidth.Loading.SHORT_TERM))
        assertEquals(1.0, a.beta2, 0.0)
        assertEquals(3451.0 / 36000.0, a.characteristicCrackWidthMm, 1e-14)
        val onset = assess(input().copy(loading = Ecp203LoadCrackWidth.Loading.SHORT_TERM,
            crackedServiceSteelStressMpa = 100.0))
        assertEquals(0.0001, onset.meanRelativeSteelStrain, 1e-15)
    }
    @Test fun `smooth mild independent vector uses its own bond coefficients`() {
        val a = assess(input().copy(characteristicYieldMpa = 240.0,
            surface = Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD,
            crackedServiceSteelStressMpa = 140.0, firstCrackSteelStressMpa = 90.0,
            exposure = Ecp203LoadCrackWidth.Exposure.II_UNPROTECTED))
        assertEquals(0.5, a.beta1, 0.0)
        assertEquals(1.6, a.k1, 0.0)
        assertEquals(131461.0 / 672000.0, a.characteristicCrackWidthMm, 1e-14)
        assertEquals(140.0, a.sourceWorkingStressLimitMpa!!, 0.0)
        assertEquals(true, a.boundedChecksPass)
    }
    @Test fun `pure axial tension changes k2 not the restraint factor`() {
        val a = assess(input().copy(distribution = Ecp203LoadCrackWidth.TensionDistribution.PURE_AXIAL_TENSION))
        assertEquals(1.0, a.k2, 0.0)
        assertEquals(1.7, a.beta, 0.0)
        assertEquals(550.0 / 3.0, a.meanCrackSpacingMm, 1e-12)
        assertTrue(a.characteristicCrackWidthMm > assess().characteristicCrackWidthMm)
    }
    @Test fun `four explicit exposure limits are not inferred from place or material`() {
        val expected = listOf(0.30, 0.20, 0.15, 0.10)
        Ecp203LoadCrackWidth.Exposure.entries.forEachIndexed { i, exposure ->
            val a = assess(input().copy(exposure = exposure))
            assertEquals(expected[i], a.maximumCrackWidthMm, 0.0)
            assertEquals(i != 3, a.widthLimitPass)
        }
    }
    @Test fun `source service stress caps are literal table rows`() {
        val limits = mapOf(240.0 to 140.0, 350.0 to 200.0, 400.0 to 220.0, 420.0 to 230.0)
        limits.forEach { (fy, limit) ->
            val surface = if (fy == 240.0) Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD
                else Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED
            val base = input().copy(characteristicYieldMpa = fy, surface = surface,
                crackedServiceSteelStressMpa = limit, firstCrackSteelStressMpa = 100.0,
                exposure = Ecp203LoadCrackWidth.Exposure.I_PROTECTED)
            assertEquals(limit, assess(base).sourceWorkingStressLimitMpa!!, 0.0)
            assertEquals(true, assess(base).workingStressLimitPass)
            val failed = assess(base.copy(crackedServiceSteelStressMpa = limit + 0.01))
            assertEquals(false, failed.workingStressLimitPass)
            assertEquals(false, failed.boundedChecksPass)
        }
    }
    @Test fun `fy500 width is calculated but absent source stress cap never becomes a pass`() {
        val a = assess(input().copy(characteristicYieldMpa = 500.0))
        assertEquals(4403.0 / 36000.0, a.characteristicCrackWidthMm, 1e-14)
        assertTrue(a.widthLimitPass)
        assertNull(a.sourceWorkingStressLimitMpa)
        assertNull(a.workingStressLimitPass)
        assertNull(a.boundedChecksPass)
    }
    @Test fun `known width failure remains a failure even when the grade cap is unknown`() {
        val a = assess(input().copy(characteristicYieldMpa = 500.0,
            exposure = Ecp203LoadCrackWidth.Exposure.IV_CORROSIVE))
        assertFalse(a.widthLimitPass)
        assertNull(a.workingStressLimitPass)
        assertEquals(false, a.boundedChecksPass)
    }
    @Test fun `larger effective tension area increases spacing and calculated width`() {
        val base = assess()
        val larger = assess(input().copy(effectiveConcreteTensionAreaMm2 = 30000.0))
        assertTrue(larger.meanCrackSpacingMm > base.meanCrackSpacingMm)
        assertTrue(larger.characteristicCrackWidthMm > base.characteristicCrackWidthMm)
    }
    @Test fun `larger diameter increases width at otherwise fixed actual inputs`() {
        assertTrue(assess(input().copy(commonIndividualBarDiameterMm = 25.0)).characteristicCrackWidthMm >
            assess().characteristicCrackWidthMm)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `unsupported fy is not interpolated`() { assess(input().copy(characteristicYieldMpa = 360.0)) }
    @Test(expected = IllegalArgumentException::class)
    fun `smooth high strength grade is not silently treated as deformed`() {
        assess(input().copy(surface = Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `service steel yielding is outside the elastic method`() {
        assess(input().copy(crackedServiceSteelStressMpa = 400.1))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `unloading below first crack is not clamped into a fabricated width`() {
        assess(input().copy(crackedServiceSteelStressMpa = 90.0))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `zero service stress is not a cracked section input`() { assess(input().copy(crackedServiceSteelStressMpa = 0.0)) }
    @Test(expected = IllegalArgumentException::class)
    fun `steel area cannot replace effective concrete tension area`() {
        assess(input().copy(effectiveConcreteTensionAreaMm2 = 600.0))
    }
    @Test fun `nonfinite nonpositive or overflowing physical values are rejected`() {
        val invalid = listOf(input().copy(commonIndividualBarDiameterMm = Double.NaN),
            input().copy(tensionSteelAreaMm2 = -1.0), input().copy(effectiveConcreteTensionAreaMm2 = Double.POSITIVE_INFINITY),
            input().copy(firstCrackSteelStressMpa = 0.0),
            input().copy(commonIndividualBarDiameterMm = Double.MAX_VALUE,
                effectiveConcreteTensionAreaMm2 = Double.MAX_VALUE))
        invalid.forEach { value ->
            try { assess(value); fail("Expected rejected physical input") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
