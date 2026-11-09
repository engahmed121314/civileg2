package com.civileg.app.utils

import com.civileg.app.utils.ConcreteMixDesigner.CementType
import com.civileg.app.utils.ConcreteMixDesigner.Exposure
import com.civileg.app.utils.ConcreteMixDesigner.MixInput
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcreteMixDesignerTest {

    @Test
    fun designMixReturnsFiniteNonNegativeQuantitiesForDefaultInputs() {
        val result = ConcreteMixDesigner.designMix(defaultInput())

        assertTrue(result.cementContent.isFinite() && result.cementContent > 0.0)
        assertTrue(result.waterContent.isFinite() && result.waterContent > 0.0)
        assertTrue(result.fineAggContent.isFinite() && result.fineAggContent >= 0.0)
        assertTrue(result.coarseAggContent.isFinite() && result.coarseAggContent > 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designMixRejectsNonFiniteStrength() {
        ConcreteMixDesigner.designMix(defaultInput().copy(targetStrength = Double.NaN))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designMixRejectsFinenessModulusOutsideTableRange() {
        ConcreteMixDesigner.designMix(defaultInput().copy(finenessModulus = 2.3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designMixRejectsMixWithNoRemainingFineAggregateVolume() {
        ConcreteMixDesigner.designMix(defaultInput().copy(targetStrength = 43.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designMixRejectsRequiredStrengthOutsideAvailableTableRange() {
        ConcreteMixDesigner.designMix(defaultInput().copy(targetStrength = 45.0))
    }

    private fun defaultInput() = MixInput(
        targetStrength = 30.0,
        standardDeviation = 5.0,
        maxAggregateSize = 20.0,
        slump = 87.5,
        exposure = Exposure.MODERATE,
        cementType = CementType.OPC,
        finenessModulus = 2.7
    )
}
