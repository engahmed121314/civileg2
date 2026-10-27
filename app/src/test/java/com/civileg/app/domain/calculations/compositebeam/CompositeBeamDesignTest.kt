package com.civileg.app.domain.calculations.compositebeam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeBeamDesignTest {

    private fun input(code: CompositeDesignCode = CompositeDesignCode.ACI): CompositeBeamInput =
        CompositeBeamInput(
            code = code,
            spanM = 8.0,
            beamSpacingM = 3.0,
            slabThicknessMm = 120.0,
            steelBeamDepthMm = 500.0,
            steelBeamAreaMm2 = 8000.0,
            steelBeamInertiaMm4 = 400_000_000.0,
            steelYieldStrengthMPa = 345.0,
            concreteStrengthMPa = 30.0,
            steelFlangeWidthMm = 200.0,
            shearStudDiameterMm = 19.0,
            shearStudHeightMm = 100.0,
            shearStudUltimateStrengthMPa = 450.0,
            studsPerRow = 2,
            liveLoadKPa = 5.0,
            superimposedDeadLoadKPa = 2.0,
            steelSelfWeightKNPerM = 0.6,
            deflectionLimitRatio = 360.0
        )

    @Test
    fun effectiveWidthAciLimitedBySpanQuarter() {
        assertEquals(2000.0, CompositeBeamDesign.effectiveWidthMm(input()), 1e-9)
    }

    @Test
    fun effectiveWidthEcpLimitedBySlabWidth() {
        assertEquals(1640.0, CompositeBeamDesign.effectiveWidthMm(input(CompositeDesignCode.ECP)), 1e-9)
    }

    @Test
    fun effectiveWidthLimitedByBeamSpacing() {
        val result = CompositeBeamDesign.effectiveWidthMm(input().copy(beamSpacingM = 0.5))
        assertEquals(500.0, result, 1e-9)
    }

    @Test
    fun concreteDesignStrengthFollowsCode() {
        assertEquals(25.5, CompositeBeamDesign.concreteDesignStrengthMPa(input()), 1e-9)
        assertEquals(
            13.4,
            CompositeBeamDesign.concreteDesignStrengthMPa(input(CompositeDesignCode.ECP)),
            1e-9
        )
    }

    @Test
    fun beta1DependsOnConcreteStrength() {
        val aci = input()
        assertEquals(0.8357142857142857, CompositeBeamDesign.beta1(aci), 1e-9)
        assertEquals(
            0.6928571429,
            CompositeBeamDesign.beta1(aci.copy(concreteStrengthMPa = 50.0)),
            1e-6
        )
        assertEquals(
            0.65,
            CompositeBeamDesign.beta1(aci.copy(concreteStrengthMPa = 60.0)),
            1e-9
        )
        assertEquals(
            0.65,
            CompositeBeamDesign.beta1(
                input(CompositeDesignCode.ECP).copy(concreteStrengthMPa = 60.0)
            ),
            1e-9
        )
    }

    @Test
    fun fullShearConnectionForceIsMinimumOfSteelAndConcrete() {
        assertEquals(2760.0, CompositeBeamDesign.fullShearConnectionForceKN(input()), 1e-9)
        assertEquals(
            2637.12,
            CompositeBeamDesign.fullShearConnectionForceKN(input(CompositeDesignCode.ECP)),
            1e-6
        )
    }

    @Test
    fun shearStudCapacityAciMatchesCodeFormula() {
        assertEquals(124.5825, CompositeBeamDesign.shearStudCapacityKN(input()), 0.001)
    }

    @Test
    fun shearStudCapacityEcpUsesUltimateStrength() {
        assertEquals(
            102.0703,
            CompositeBeamDesign.shearStudCapacityKN(input(CompositeDesignCode.ECP)),
            0.001
        )
    }

    @Test
    fun designProducesCapacityAndUtilization() {
        val result = CompositeBeamDesign.design(input())
        assertNotNull(result)
        assertTrue(result.flexuralCapacityKNm > 0.0)
        assertTrue(result.momentUtilization in 0.1..1.0)
        assertTrue(result.flexureOk)
        assertTrue(result.deflectionOk)
    }

    @Test
    fun fullConnectionRequiredStudsAndRows() {
        val result = CompositeBeamDesign.design(input())
        assertEquals(23, result.requiredStuds)
        assertEquals(12, result.requiredStudRows)
        assertEquals(result.requiredStuds, result.providedStuds)
    }

    @Test
    fun partialConnectionReducesForceAndStuds() {
        val full = CompositeBeamDesign.design(input())
        val partial = CompositeBeamDesign.design(
            input().copy(partialShearConnectionRatio = 0.5)
        )
        assertEquals(full.fullShearConnectionForceKN / 2.0, partial.designShearConnectionForceKN, 1e-9)
        assertEquals(12, partial.requiredStuds)
        assertTrue(partial.flexuralCapacityKNm < full.flexuralCapacityKNm)
        assertTrue(partial.notes.any { it.contains("Partial shear connection") })
    }

    @Test
    fun partialConnectionBelowFortyPercentRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            input().copy(partialShearConnectionRatio = 0.3)
        }
    }

    @Test
    fun invalidSpanRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            input().copy(spanM = 0.0)
        }
    }

    @Test
    fun invalidStudsPerRowRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            input().copy(studsPerRow = 0)
        }
    }

    @Test
    fun designMomentMatchesSimplySupportedFormula() {
        val base = input()
        val expected = CompositeBeamDesign.factoredLoadKNPerM(base) * 64.0 / 8.0
        assertEquals(
            expected,
            CompositeBeamDesign.designMomentKNm(base),
            1e-9
        )
        assertEquals(338.304, CompositeBeamDesign.designMomentKNm(base), 1e-6)
        assertEquals(338.304, CompositeBeamDesign.design(base).designMomentKNm, 1e-6)
    }

    @Test
    fun ecpFactoredMomentExceedsAciForSameDeadLoad() {
        val aciMoment = CompositeBeamDesign.design(input()).designMomentKNm
        val ecpMoment = CompositeBeamDesign.design(input(CompositeDesignCode.ECP)).designMomentKNm
        assertTrue(ecpMoment > aciMoment)
    }

    @Test
    fun deflectionWithinLimitForTypicalBeam() {
        val result = CompositeBeamDesign.design(input())
        assertTrue(result.deflectionMm > 0.0)
        assertTrue(result.deflectionMm < result.deflectionLimitMm)
        assertTrue(result.deflectionOk)
    }

    @Test
    fun deflectionIncreasesWithSpan() {
        val short = CompositeBeamDesign.design(input())
        val long = CompositeBeamDesign.design(input().copy(spanM = 12.0))
        assertTrue(long.deflectionMm > short.deflectionMm)
    }

    @Test
    fun longSpanFailsDeflection() {
        val result = CompositeBeamDesign.design(input().copy(spanM = 20.0))
        assertFalse(result.deflectionOk)
        assertTrue(result.warnings.any { it.contains("deflection") })
    }

    @Test
    fun transformedInertiaExceedsSteelInertia() {
        val base = input()
        val result = CompositeBeamDesign.design(base)
        assertTrue(result.transformedInertiaMm4 > base.steelBeamInertiaMm4)
        assertTrue(result.modularRatio > 0.0)
        assertTrue(result.concreteModulusMPa > 0.0)
    }

    @Test
    fun providedStudsBelowRequiredFlagged() {
        val result = CompositeBeamDesign.design(input().copy(providedStuds = 5))
        assertFalse(result.shearConnectionOk)
        assertTrue(result.warnings.any { it.contains("fewer") })
    }

    @Test
    fun shallowStudTriggersHeightWarning() {
        val result = CompositeBeamDesign.design(input().copy(shearStudHeightMm = 50.0))
        assertTrue(result.warnings.any { it.contains("4 times") })
    }

    @Test
    fun steelGovernsWarningWhenSteelForceExceedsConcrete() {
        val result = CompositeBeamDesign.design(input().copy(steelBeamAreaMm2 = 20000.0))
        assertTrue(result.warnings.any { it.contains("neutral axis") })
    }

    @Test
    fun excessiveLoadFailsFlexure() {
        val result = CompositeBeamDesign.design(
            input().copy(liveLoadKPa = 50.0, superimposedDeadLoadKPa = 20.0)
        )
        assertFalse(result.flexureOk)
        assertTrue(result.momentUtilization > 1.0)
        assertTrue(result.warnings.any { it.contains("Flexural demand") })
    }

    @Test
    fun partialConnectionNoteAndWarningsOnlyWhenNeeded() {
        val full = CompositeBeamDesign.design(input())
        assertFalse(full.notes.any { it.contains("Partial shear connection") })
        assertTrue(full.warnings.isEmpty())
    }
}
