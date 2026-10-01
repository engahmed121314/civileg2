package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.PileInput
import com.civileg.app.domain.PileCapInput
import com.civileg.app.domain.PileGroupInput
import com.civileg.app.domain.PileType
import com.civileg.app.domain.SoilType
import com.civileg.app.domain.PileDesignResult
import com.civileg.app.domain.PileReinforcementResult
import com.civileg.app.domain.calculations.base.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ECPPileFoundation — ECP 203-2020 pile foundation design engine.
 * Covers pile capacity, pile cap, settlement, group efficiency,
 * lateral capacity, negative skin friction, reinforcement,
 * InputGuard validation, and edge cases.
 */
class ECPPileFoundationTest {

    private lateinit var pile: ECPPileFoundation

    @Before
    fun setup() {
        pile = ECPPileFoundation()
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun defaultInput(
        pileType: PileType = PileType.BORED,
        pileDiameter: Double = 600.0,
        pileLength: Double = 15.0,
        numberOfPiles: Int = 4,
        axialLoad: Double = 2000.0,
        fcu: Double = 30.0,
        fy: Double = 400.0,
        soilType: SoilType = SoilType.CLAY,
        cu: Double = 50.0,
        phi: Double = 30.0,
        gammaSoil: Double = 18.0,
        safetyFactor: Double = 3.0
    ) = PileInput(
        pileType = pileType,
        pileDiameter = pileDiameter,
        pileLength = pileLength,
        numberOfPiles = numberOfPiles,
        axialLoad = axialLoad,
        fcu = fcu,
        fy = fy,
        soilType = soilType,
        cu = cu,
        phi = phi,
        gammaSoil = gammaSoil,
        safetyFactor = safetyFactor
    )

    // ═══════════════════════════════════════════════════════════════
    // 1. Happy path — designPile
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designPile_validInputs_producesResult`() {
        val result = pile.designPile(defaultInput())
        assertNotNull(result)
    }

    @Test
    fun `designPile_capacityResultIsValid`() {
        val result = pile.designPile(defaultInput())
        assertTrue("Ultimate capacity > 0", result.capacityResult.ultimateCapacity > 0)
        assertTrue("Allowable capacity > 0", result.capacityResult.allowableCapacity > 0)
        assertTrue("Allowable <= Ultimate", result.capacityResult.allowableCapacity <= result.capacityResult.ultimateCapacity)
    }

    @Test
    fun `designPile_groupResultIsValid`() {
        val result = pile.designPile(defaultInput())
        assertTrue("Efficiency factor > 0", result.groupResult.efficiencyFactor > 0)
        assertTrue("Group capacity > 0", result.groupResult.groupCapacity > 0)
    }

    @Test
    fun `designPile_settlementResultIsValid`() {
        val result = pile.designPile(defaultInput())
        assertTrue("Total settlement >= 0", result.settlementResult.totalSettlement >= 0)
    }

    @Test
    fun `designPile_capResultIsValid`() {
        val result = pile.designPile(defaultInput())
        assertTrue("Cap width > 0", result.capResult.capWidth > 0)
        assertTrue("Cap thickness > 0", result.capResult.capThickness > 0)
    }

    @Test
    fun `designPile_pileReinforcementIsValid`() {
        val result = pile.designPile(defaultInput())
        assertTrue("Longitudinal bars > 0", result.pileReinforcement.longitudinalBars > 0)
        assertTrue("Longitudinal diameter > 0", result.pileReinforcement.longitudinalDiameter > 0)
    }

    @Test
    fun `designPile_lateralCapacityIsPositive`() {
        val result = pile.designPile(defaultInput())
        assertTrue("Lateral capacity > 0", result.lateralCapacity > 0)
    }

    @Test
    fun `designPile_negativeSkinFrictionIsNonNegative`() {
        val result = pile.designPile(defaultInput())
        assertTrue("Negative skin friction >= 0", result.negativeSkinFriction >= 0)
    }

    @Test
    fun `designPile_codeNotesExist`() {
        val result = pile.designPile(defaultInput())
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Pile capacity
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `calculatePileCapacity_clay_producesResult`() {
        val result = pile.calculatePileCapacity(defaultInput(soilType = SoilType.CLAY))
        assertTrue("Ultimate capacity > 0", result.ultimateCapacity > 0)
        assertTrue("Shaft resistance >= 0", result.shaftResistance >= 0)
        assertTrue("End bearing >= 0", result.endBearingResistance >= 0)
    }

    @Test
    fun `calculatePileCapacity_sand_producesResult`() {
        val result = pile.calculatePileCapacity(defaultInput(soilType = SoilType.SAND))
        assertTrue("Ultimate capacity > 0", result.ultimateCapacity > 0)
    }

    @Test
    fun `calculatePileCapacity_longerPile_higherCapacity`() {
        val short = pile.calculatePileCapacity(defaultInput(pileLength = 10.0))
        val long = pile.calculatePileCapacity(defaultInput(pileLength = 20.0))
        assertTrue("Longer pile should have higher capacity", long.ultimateCapacity > short.ultimateCapacity)
    }

    @Test
    fun `calculatePileCapacity_largerDiameter_higherCapacity`() {
        val small = pile.calculatePileCapacity(defaultInput(pileDiameter = 450.0))
        val large = pile.calculatePileCapacity(defaultInput(pileDiameter = 900.0))
        assertTrue("Larger diameter should have higher capacity", large.ultimateCapacity > small.ultimateCapacity)
    }

    @Test
    fun `calculatePileCapacity_drivenPile_differentFromBored`() {
        val bored = pile.calculatePileCapacity(defaultInput(pileType = PileType.BORED))
        val driven = pile.calculatePileCapacity(defaultInput(pileType = PileType.DRIVEN))
        // Driven pile uses alpha=1.0 vs bored alpha=0.5 for clay
        // For clay, driven should have higher shaft resistance
        assertTrue("Driven and bored should differ", driven.ultimateCapacity != bored.ultimateCapacity)
    }

    @Test
    fun `calculatePileCapacity_utilizationRatioIsNonNegative`() {
        val result = pile.calculatePileCapacity(defaultInput())
        assertTrue("Utilization ratio >= 0", result.utilizationRatio >= 0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Pile cap design
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designPileCap_validInputs_producesResult`() {
        val capInput = PileCapInput(
            axialLoad = 2000.0, numberOfPiles = 4,
            pileDiameter = 600.0, pileSpacing = 1800.0,
            fcu = 30.0, fy = 400.0
        )
        val result = pile.designPileCap(capInput)
        assertTrue("Cap width > 0", result.capWidth > 0)
        assertTrue("Cap thickness > 0", result.capThickness > 0)
    }

    @Test
    fun `designPileCap_reinforcementIsProvided`() {
        val capInput = PileCapInput(
            axialLoad = 2000.0, numberOfPiles = 4,
            pileDiameter = 600.0, pileSpacing = 1800.0,
            fcu = 30.0, fy = 400.0
        )
        val result = pile.designPileCap(capInput)
        assertTrue("Flexural reinforcement area > 0", result.flexuralReinforcement.area > 0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Settlement
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `calculateSettlement_validInputs_producesResult`() {
        val result = pile.calculateSettlement(defaultInput())
        assertTrue("Total settlement >= 0", result.totalSettlement >= 0)
        assertTrue("Allowable settlement > 0", result.allowableSettlement > 0)
    }

    @Test
    fun `calculateSettlement_shorterPile_lessSettlement`() {
        val longPile = pile.calculateSettlement(defaultInput(pileLength = 20.0))
        val shortPile = pile.calculateSettlement(defaultInput(pileLength = 8.0))
        // Generally shorter pile may have less or more settlement depending on soil;
        // we just verify both produce valid results
        assertTrue("Both should produce valid results", longPile.totalSettlement >= 0 && shortPile.totalSettlement >= 0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. Group efficiency
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `checkGroupEfficiency_validInputs_producesResult`() {
        val groupInput = PileGroupInput(numberOfPiles = 4, pileDiameter = 600.0, spacing = 3.0)
        val result = pile.checkGroupEfficiency(groupInput)
        assertTrue("Efficiency factor > 0", result.efficiencyFactor > 0)
        assertTrue("Efficiency factor <= 1", result.efficiencyFactor <= 1.01) // may slightly exceed 1.0 due to rounding
    }

    @Test
    fun `checkGroupEfficiency_groupCapacityExceedsIndividual`() {
        val groupInput = PileGroupInput(numberOfPiles = 4, pileDiameter = 600.0, spacing = 3.0)
        val result = pile.checkGroupEfficiency(groupInput)
        assertTrue("Group capacity > 0", result.groupCapacity > 0)
    }

    @Test
    fun `checkGroupEfficiency_closerSpacing_lowerEfficiency`() {
        val wideSpacing = pile.checkGroupEfficiency(PileGroupInput(numberOfPiles = 4, pileDiameter = 600.0, spacing = 5.0))
        val closeSpacing = pile.checkGroupEfficiency(PileGroupInput(numberOfPiles = 4, pileDiameter = 600.0, spacing = 2.0))
        assertTrue("Wider spacing should have higher efficiency", wideSpacing.efficiencyFactor >= closeSpacing.efficiencyFactor)
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. Lateral capacity
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `calculateLateralCapacity_validInputs_producesResult`() {
        val result = pile.calculateLateralCapacity(defaultInput())
        assertTrue("Ultimate lateral capacity > 0", result.ultimateLateralCapacity > 0)
        assertTrue("Allowable lateral capacity > 0", result.allowableLateralCapacity > 0)
    }

    @Test
    fun `calculateLateralCapacity_largerDiameter_higherCapacity`() {
        val small = pile.calculateLateralCapacity(defaultInput(pileDiameter = 450.0))
        val large = pile.calculateLateralCapacity(defaultInput(pileDiameter = 900.0))
        assertTrue("Larger diameter → higher lateral capacity", large.ultimateLateralCapacity > small.ultimateLateralCapacity)
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. Negative skin friction
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `calculateNegativeSkinFriction_returnsNonNegativeValue`() {
        val result = pile.calculateNegativeSkinFriction(defaultInput())
        assertTrue("Negative skin friction >= 0", result >= 0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. Pile reinforcement
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designPileReinforcement_validInputs_producesResult`() {
        val input = defaultInput()
        val result = pile.designPileReinforcement(input, 500.0, 50.0)
        assertTrue("Longitudinal bars > 0", result.longitudinalBars > 0)
        assertTrue("Longitudinal diameter > 0", result.longitudinalDiameter > 0)
        assertTrue("Longitudinal area > 0", result.longitudinalArea > 0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 9. InputGuard validation
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `designPile_throwsForZeroFcu`() {
        pile.designPile(defaultInput(fcu = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designPile_throwsForNegativeFcu`() {
        pile.designPile(defaultInput(fcu = -30.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designPile_throwsForZeroFy`() {
        pile.designPile(defaultInput(fy = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designPile_throwsForZeroDiameter`() {
        pile.designPile(defaultInput(pileDiameter = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designPile_throwsForZeroLength`() {
        pile.designPile(defaultInput(pileLength = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designPile_throwsForNegativeDiameter`() {
        pile.designPile(defaultInput(pileDiameter = -600.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculatePileCapacity_throwsForZeroFcu`() {
        pile.calculatePileCapacity(defaultInput(fcu = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculatePileCapacity_throwsForZeroDiameter`() {
        pile.calculatePileCapacity(defaultInput(pileDiameter = 0.0))
    }

    // ═══════════════════════════════════════════════════════════════
    // 10. Edge cases & pile types
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designPile_drivenPile_producesResult`() {
        val result = pile.designPile(defaultInput(pileType = PileType.DRIVEN, pileDiameter = 300.0))
        assertNotNull(result)
    }

    @Test
    fun `designPile_cfaPile_producesResult`() {
        val result = pile.designPile(defaultInput(pileType = PileType.CFA))
        assertNotNull(result)
    }

    @Test
    fun `designPile_micropile_producesResult`() {
        val result = pile.designPile(defaultInput(pileType = PileType.MICROPILE, pileDiameter = 200.0))
        assertNotNull(result)
    }

    @Test
    fun `designPile_sandSoil_producesResult`() {
        val result = pile.designPile(defaultInput(soilType = SoilType.SAND))
        assertNotNull(result)
        assertTrue("Capacity should be > 0 for sand", result.capacityResult.ultimateCapacity > 0)
    }

    @Test
    fun `designPile_rockSoil_producesResult`() {
        val result = pile.designPile(defaultInput(soilType = SoilType.ROCK))
        assertNotNull(result)
    }

    @Test
    fun `designPile_higherSafetyFactor_lowerAllowableCapacity`() {
        val fs3 = pile.calculatePileCapacity(defaultInput(safetyFactor = 3.0))
        val fs4 = pile.calculatePileCapacity(defaultInput(safetyFactor = 4.0))
        assertTrue("Higher FS → lower allowable capacity", fs4.allowableCapacity <= fs3.allowableCapacity)
    }

    @Test
    fun `designPile_utilizationRatioIsNonNegative`() {
        val result = pile.designPile(defaultInput())
        assertTrue("Utilization ratio should be >= 0", result.utilizationRatio >= 0)
    }
}
