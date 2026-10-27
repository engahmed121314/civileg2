package com.civileg.app.domain.calculations.fireresistance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FireResistanceDesignTest {

    private fun input(code: FireCode = FireCode.ECP): FireResistanceInput =
        FireResistanceInput(
            code = code,
            elementType = FireElementType.BEAM,
            requiredRatingMinutes = 60,
            coverMm = 25.0,
            sectionWidthMm = 300.0,
            sectionDepthMm = 600.0,
            concreteStrengthMPa = 30.0,
            steelStrengthMPa = 420.0,
            reinforcementRatio = 0.01
        )

    @Test
    fun iso834AtTimeZeroIsAmbient() {
        assertEquals(20.0, FireResistanceDesign.iso834TemperatureC(0.0), 1e-9)
    }

    @Test
    fun iso834AtThirtyMinutes() {
        assertEquals(841.796, FireResistanceDesign.iso834TemperatureC(30.0), 0.01)
    }

    @Test
    fun iso834AtSixtyMinutes() {
        assertEquals(945.340, FireResistanceDesign.iso834TemperatureC(60.0), 0.01)
    }

    @Test
    fun iso834AtOneHundredTwentyMinutes() {
        assertEquals(1049.040, FireResistanceDesign.iso834TemperatureC(120.0), 0.01)
    }

    @Test
    fun iso834IntOverloadMatchesDouble() {
        assertEquals(
            FireResistanceDesign.iso834TemperatureC(90.0),
            FireResistanceDesign.iso834TemperatureC(90),
            1e-12
        )
    }

    @Test
    fun iso834IsMonotonic() {
        val times = listOf(1, 10, 30, 60, 120, 240)
        val temperatures = times.map { FireResistanceDesign.iso834TemperatureC(it) }
        temperatures.zipWithNext().forEach { (lower, upper) ->
            assertTrue(upper > lower)
        }
    }

    @Test
    fun iso834NegativeTimeRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            FireResistanceDesign.iso834TemperatureC(-1.0)
        }
    }

    @Test
    fun concreteFactorAtAmbientIsOne() {
        assertEquals(1.0, FireResistanceDesign.concreteStrengthFactor(20.0), 1e-9)
    }

    @Test
    fun concreteFactorAtFiveHundredIsSixtyPercent() {
        assertEquals(0.60, FireResistanceDesign.concreteStrengthFactor(500.0), 1e-9)
    }

    @Test
    fun concreteFactorAtSixHundredIsFortyFivePercent() {
        assertEquals(0.45, FireResistanceDesign.concreteStrengthFactor(600.0), 1e-9)
    }

    @Test
    fun concreteFactorInterpolatesAtThreeHundredFifty() {
        assertEquals(0.80, FireResistanceDesign.concreteStrengthFactor(350.0), 1e-9)
    }

    @Test
    fun concreteFactorAtHighTemperatureIsLow() {
        assertEquals(0.04, FireResistanceDesign.concreteStrengthFactor(1000.0), 1e-9)
        assertEquals(0.01, FireResistanceDesign.concreteStrengthFactor(1100.0), 1e-9)
    }

    @Test
    fun steelFactorAtAmbientIsOne() {
        assertEquals(1.0, FireResistanceDesign.steelStrengthFactor(20.0), 1e-9)
    }

    @Test
    fun steelFactorAtSixHundredIsFortySevenPercent() {
        assertEquals(0.47, FireResistanceDesign.steelStrengthFactor(600.0), 1e-9)
    }

    @Test
    fun steelFactorAtEightHundredIsElevenPercent() {
        assertEquals(0.11, FireResistanceDesign.steelStrengthFactor(800.0), 1e-9)
    }

    @Test
    fun steelFactorAtTwelveHundredIsZero() {
        assertEquals(0.0, FireResistanceDesign.steelStrengthFactor(1200.0), 1e-9)
    }

    @Test
    fun steelFactorInterpolatesAtFiveHundredFifty() {
        assertEquals(0.625, FireResistanceDesign.steelStrengthFactor(550.0), 1e-9)
    }

    @Test
    fun ecpBeamRequirementForSixtyMinutes() {
        val requirement = FireResistanceDesign.minimumRequirement(
            FireCode.ECP,
            FireElementType.BEAM,
            60
        )
        assertEquals(60, requirement.ratingMinutes)
        assertEquals(25.0, requirement.minimumCoverMm, 1e-9)
        assertEquals(200.0, requirement.minimumDimensionMm, 1e-9)
    }

    @Test
    fun aciBeamRequirementForSixtyMinutes() {
        val requirement = FireResistanceDesign.minimumRequirement(
            FireCode.ACI,
            FireElementType.BEAM,
            60
        )
        assertEquals(20.0, requirement.minimumCoverMm, 1e-9)
        assertEquals(125.0, requirement.minimumDimensionMm, 1e-9)
    }

    @Test
    fun aciSlabRequirementForNinetyMinutes() {
        val requirement = FireResistanceDesign.minimumRequirement(
            FireCode.ACI,
            FireElementType.SLAB,
            90
        )
        assertEquals(20.0, requirement.minimumCoverMm, 1e-9)
        assertEquals(80.0, requirement.minimumDimensionMm, 1e-9)
    }

    @Test
    fun requirementSnapsToNextStandardRating() {
        val requirement = FireResistanceDesign.minimumRequirement(
            FireCode.ECP,
            FireElementType.BEAM,
            75
        )
        assertEquals(90, requirement.ratingMinutes)
        assertEquals(30.0, requirement.minimumCoverMm, 1e-9)
        assertEquals(250.0, requirement.minimumDimensionMm, 1e-9)
    }

    @Test
    fun achievedRatingUsesHighestSatisfiedRequirement() {
        val rating = FireResistanceDesign.achievedRatingMinutes(
            FireCode.ECP,
            FireElementType.BEAM,
            30.0,
            250.0
        )
        assertEquals(90, rating)
    }

    @Test
    fun adequateSectionPassesPrescriptiveChecks() {
        val result = FireResistanceDesign.design(input())
        assertNotNull(result)
        assertTrue(result.coverOk)
        assertTrue(result.dimensionOk)
        assertTrue(result.ratingOk)
        assertEquals(60, result.achievedRatingMinutes)
        assertTrue(result.warnings.isEmpty())
        assertTrue(result.notes.any { it.contains("satisfied") })
    }

    @Test
    fun insufficientCoverFails() {
        val result = FireResistanceDesign.design(input().copy(coverMm = 15.0))
        assertFalse(result.coverOk)
        assertFalse(result.ratingOk)
        assertEquals(0, result.achievedRatingMinutes)
        assertTrue(result.warnings.any { it.contains("Cover") })
    }

    @Test
    fun insufficientDimensionFails() {
        val result = FireResistanceDesign.design(input().copy(sectionWidthMm = 140.0))
        assertFalse(result.dimensionOk)
        assertFalse(result.ratingOk)
        assertEquals(0, result.achievedRatingMinutes)
        assertTrue(result.warnings.any { it.contains("dimension") })
    }

    @Test
    fun reducedStrengthsFollowTemperatureFactors() {
        val result = FireResistanceDesign.design(input())
        assertEquals(30.0 * result.concreteStrengthFactor, result.reducedConcreteStrengthMPa, 1e-9)
        assertEquals(420.0 * result.steelStrengthFactor, result.reducedSteelStrengthMPa, 1e-9)
        assertTrue(result.reducedConcreteStrengthMPa < 30.0)
        assertTrue(result.reducedSteelStrengthMPa < 420.0)
        assertTrue(result.reducedCapacityIndex in 0.0..1.0)
    }

    @Test
    fun designEvaluatesIso834AtRequiredRating() {
        val result = FireResistanceDesign.design(input())
        assertEquals(945.340, result.iso834TemperatureC, 0.01)
        assertEquals(result.iso834TemperatureC, FireResistanceDesign.iso834TemperatureC(60), 1e-9)
    }

    @Test
    fun nonStandardRequiredRatingIsNoted() {
        val result = FireResistanceDesign.design(input().copy(requiredRatingMinutes = 75))
        assertEquals(90, result.standardRatingMinutes)
        assertTrue(result.notes.any { it.contains("standard 90-minute") })
    }

    @Test
    fun invalidRatingRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            input().copy(requiredRatingMinutes = 0)
        }
    }

    @Test
    fun invalidCoverRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            input().copy(coverMm = -1.0)
        }
    }

    @Test
    fun invalidReinforcementRatioRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            input().copy(reinforcementRatio = 0.5)
        }
    }

    @Test
    fun aciCodeUsesAciRequirementTable() {
        val result = FireResistanceDesign.design(input(FireCode.ACI))
        assertEquals(20.0, result.minimumCoverMm, 1e-9)
        assertEquals(125.0, result.minimumDimensionMm, 1e-9)
        assertTrue(result.coverOk)
        assertTrue(result.dimensionOk)
    }
}
