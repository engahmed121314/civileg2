package com.civileg.core.engine.rc.ecp

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class Ecp203ShortTermDeflectionTest {
    private val as20 = 2.0 * PI * 20.0.pow(2) / 4.0
    private fun section(top: Boolean = true) = Ecp203ShortTermDeflection.Section(
        30.0, 200.0, 600.0, 60.0, 350.0,
        if (top) Ecp203ShortTermDeflection.FlangeLocation.COMPRESSION_FACE
        else Ecp203ShortTermDeflection.FlangeLocation.TENSION_FACE,
        listOf(Ecp203ShortTermDeflection.SteelLayer(as20, 307.0)))
    private fun reject(block: () -> Unit) {
        try { block(); fail("Out-of-scope input must not receive a numerical result") }
        catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test fun tCompressionIndependentHighPrecisionGolden() {
        val k = Ecp203ShortTermDeflection.stiffness(section())
        // Independently evaluated closed-form web/flange rectangle integrals.
        assertEquals(24099.79253022731, k.elasticModulusMpa, 1e-8)
        assertEquals(8.298826628866153, k.modularRatio, 1e-12)
        assertEquals(94000.0, k.grossAreaMm2, 1e-8)
        assertEquals(137.97872340425532, k.grossCentroidFromCompressionFaceMm, 1e-10)
        assertEquals(1097549290.7801418, k.grossInertiaMm4, 1e-5)
        assertEquals(64.98489050794066, k.crackedNeutralAxisMm, 1e-10)
        assertEquals(360278972.7326054, k.crackedInertiaMm4, 1e-5)
        assertEquals(17.01204277758261, k.crackingMomentKnM, 1e-10)
    }
    @Test fun flangeInTensionUsesCorrectGrossTensionDistanceAndWebCompression() {
        val k = Ecp203ShortTermDeflection.stiffness(section(false))
        assertEquals(212.02127659574468, k.grossCentroidFromCompressionFaceMm, 1e-10)
        assertEquals(1097549290.7801418, k.grossInertiaMm4, 1e-5)
        assertEquals(103.10913893450397, k.crackedNeutralAxisMm, 1e-10)
        assertEquals(289846740.5679718, k.crackedInertiaMm4, 1e-5)
        assertEquals(26.141095802407202, k.crackingMomentKnM, 1e-10)
    }
    @Test fun rectangularCrackedSectionMatchesClosedForm() {
        val s = section().copy(flangeWidthMm = 200.0, flangeThicknessMm = 0.0)
        val k = Ecp203ShortTermDeflection.stiffness(s)
        val nAs = k.modularRatio * as20
        val c = (-nAs + sqrt(nAs * nAs + 2.0 * 200.0 * nAs * 307.0)) / 200.0
        assertEquals(175.0, k.grossCentroidFromCompressionFaceMm, 1e-12)
        assertEquals(200.0 * 350.0.pow(3) / 12.0, k.grossInertiaMm4, 1e-6)
        assertEquals(c, k.crackedNeutralAxisMm, 1e-10)
        assertEquals(200.0 * c.pow(3) / 3.0 + nAs * (307.0 - c).pow(2), k.crackedInertiaMm4, 1e-5)
    }
    @Test fun neutralAxisInsideFlangeUsesFlangeWidthOnlyForCrackedConcrete() {
        val k = Ecp203ShortTermDeflection.stiffness(section().copy(flangeThicknessMm = 150.0))
        val nAs = k.modularRatio * as20
        val c = (-nAs + sqrt(nAs * nAs + 2.0 * 600.0 * nAs * 307.0)) / 600.0
        assertTrue(c < 150.0)
        assertEquals(c, k.crackedNeutralAxisMm, 1e-10)
        assertEquals(600.0 * c.pow(3) / 3.0 + nAs * (307.0 - c).pow(2), k.crackedInertiaMm4, 1e-5)
    }
    @Test fun compressionSteelReplacesConcreteRatherThanDoubleCountingIt() {
        val k = Ecp203ShortTermDeflection.stiffness(section().copy(flangeWidthMm = 200.0,
            flangeThicknessMm = 0.0, steelLayers = listOf(
                Ecp203ShortTermDeflection.SteelLayer(1000.0, 50.0),
                Ecp203ShortTermDeflection.SteelLayer(600.0, 300.0))))
        val nt = k.modularRatio * 600.0
        val nc = (k.modularRatio - 1.0) * 1000.0
        val b = nt + nc
        val c = (-b + sqrt(b * b + 400.0 * (nt * 300.0 + nc * 50.0))) / 200.0
        assertTrue(c > 50.0 && c < 300.0)
        assertEquals(c, k.crackedNeutralAxisMm, 1e-10)
        assertEquals(200.0 * c.pow(3) / 3.0 + nt * (300.0 - c).pow(2) +
            nc * (50.0 - c).pow(2), k.crackedInertiaMm4, 1e-5)
    }
    @Test fun actualLayerPositionsAreNotReplacedByOneCentroidBar() {
        val single = Ecp203ShortTermDeflection.stiffness(section())
        val split = Ecp203ShortTermDeflection.stiffness(section().copy(steelLayers = listOf(
            Ecp203ShortTermDeflection.SteelLayer(as20 / 2.0, 282.0),
            Ecp203ShortTermDeflection.SteelLayer(as20 / 2.0, 332.0))))
        assertEquals(single.crackedNeutralAxisMm, split.crackedNeutralAxisMm, 1e-10)
        assertEquals(single.crackedInertiaMm4 + single.modularRatio * as20 * 25.0.pow(2),
            split.crackedInertiaMm4, 1e-5)
    }
    @Test fun separateDeadAndTotalStiffnessIndependentDeflectionGolden() {
        val a = Ecp203ShortTermDeflection.assessSimplySupportedUdl(section(), 5000.0, 4.0, 8.0)
        assertEquals(12.5, a.dead.maximumMomentKnM, 1e-12)
        assertEquals(37.5, a.deadPlusLive.maximumMomentKnM, 1e-12)
        assertEquals(1097549290.7801418, a.dead.effectiveInertiaMm4, 1e-5)
        assertEquals(429112867.72865057, a.deadPlusLive.effectiveInertiaMm4, 1e-5)
        assertEquals(1.2306695392227779, a.dead.immediateDeflectionMm, 1e-10)
        assertEquals(9.44311332662817, a.deadPlusLive.immediateDeflectionMm, 1e-10)
        assertEquals(8.212443787405392, a.immediateLiveDeflectionMm, 1e-10)
        assertEquals(a.stiffness.modularRatio * 37.5e6 * (307.0 - a.stiffness.crackedNeutralAxisMm) /
            a.stiffness.crackedInertiaMm4, a.deadPlusLive.fullyCrackedElasticTensionSteelStressMpa, 1e-10)
        val wrongSingleStiffness = 5.0 * 8.0 * 5000.0.pow(4) /
            (384.0 * a.stiffness.elasticModulusMpa * a.deadPlusLive.effectiveInertiaMm4)
        assertTrue(a.immediateLiveDeflectionMm > wrongSingleStiffness + 1.0)
    }
    @Test fun tensionFlangeDeflectionUsesItsOwnCrackingMoment() {
        val a = Ecp203ShortTermDeflection.assessSimplySupportedUdl(section(false), 5000.0, 4.0, 8.0)
        assertEquals(563454882.657185, a.deadPlusLive.effectiveInertiaMm4, 1e-5)
        assertEquals(7.191634263184568, a.deadPlusLive.immediateDeflectionMm, 1e-10)
        assertEquals(5.96096472396179, a.immediateLiveDeflectionMm, 1e-10)
    }
    @Test fun inertiaIsGrossBelowCrackingAndContinuousAtCracking() {
        val k = Ecp203ShortTermDeflection.stiffness(section())
        assertEquals(k.grossInertiaMm4, k.effectiveInertiaMm4(0.0), 0.0)
        assertEquals(k.grossInertiaMm4, k.effectiveInertiaMm4(k.crackingMomentKnM), 0.0)
        assertEquals(k.grossInertiaMm4, k.effectiveInertiaMm4(k.crackingMomentKnM * (1.0 + 1e-12)), .01)
    }
    @Test fun inertiaStaysWithinSourceBoundsAndDecreasesAfterCracking() {
        val k = Ecp203ShortTermDeflection.stiffness(section())
        val values = listOf(0.0, 1.0, 2.0, 10.0, 10000.0).map {
            k.effectiveInertiaMm4(it * k.crackingMomentKnM)
        }
        assertTrue(values.all { it >= k.crackedInertiaMm4 && it <= k.grossInertiaMm4 })
        assertTrue(values.zipWithNext().all { (a, b) -> a >= b })
    }
    @Test fun zeroLoadsReturnZeroDeflectionWithoutDivisionByZero() {
        val a = Ecp203ShortTermDeflection.assessSimplySupportedUdl(section(), 5000.0, 0.0, 0.0)
        assertEquals(0.0, a.deadPlusLive.immediateDeflectionMm, 0.0)
        assertEquals(0.0, a.immediateLiveDeflectionMm, 0.0)
    }
    @Test fun zeroLiveLoadDoesNotFabricateALiveIncrement() {
        val a = Ecp203ShortTermDeflection.assessSimplySupportedUdl(section(), 5000.0, 12.0, 0.0)
        assertEquals(0.0, a.immediateLiveDeflectionMm, 0.0)
    }
    @Test fun zeroDeadLoadKeepsEntireActualLiveDeflection() {
        val a = Ecp203ShortTermDeflection.assessSimplySupportedUdl(section(), 5000.0, 0.0, 12.0)
        assertEquals(a.deadPlusLive.immediateDeflectionMm, a.immediateLiveDeflectionMm, 0.0)
    }
    @Test fun sourceCrackedInertiaCeilingIsAppliedBeforeLoadCaseCalculation() {
        val k = Ecp203ShortTermDeflection.stiffness(section().copy(flangeWidthMm = 200.0,
            flangeThicknessMm = 0.0, steelLayers = listOf(Ecp203ShortTermDeflection.SteelLayer(5000.0, 307.0))))
        assertEquals(k.grossInertiaMm4, k.crackedInertiaMm4, 1e-6)
        assertEquals(k.grossInertiaMm4, k.effectiveInertiaMm4(1000.0), 1e-6)
    }
    @Test fun sourceConcreteStrengthEndpointsAcceptedAndOutsideRejected() {
        listOf(20.0, 60.0).forEach { assertTrue(Ecp203ShortTermDeflection.stiffness(section().copy(fcuMpa = it)).elasticModulusMpa > 0.0) }
        listOf(19.99, 60.01, Double.NaN).forEach { reject { Ecp203ShortTermDeflection.stiffness(section().copy(fcuMpa = it)) } }
    }
    @Test fun invalidGeometryAndSteelDoNotReceiveStiffness() {
        listOf(section().copy(webWidthMm = 0.0), section().copy(flangeWidthMm = 100.0),
            section().copy(flangeThicknessMm = 0.0), section().copy(totalDepthMm = Double.POSITIVE_INFINITY),
            section().copy(steelLayers = emptyList()),
            section().copy(steelLayers = listOf(Ecp203ShortTermDeflection.SteelLayer(-1.0, 300.0))),
            section().copy(steelLayers = listOf(Ecp203ShortTermDeflection.SteelLayer(as20, 350.0))),
            section().copy(steelLayers = listOf(Ecp203ShortTermDeflection.SteelLayer(94000.0, 300.0))))
            .forEach { reject { Ecp203ShortTermDeflection.stiffness(it) } }
    }
    @Test fun negativeNonfiniteAndOverflowingLoadsAreNotSilentlyNormalized() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE).forEach {
            reject { Ecp203ShortTermDeflection.assessSimplySupportedUdl(section(), 5000.0, it, 1.0) }
        }
        reject { Ecp203ShortTermDeflection.assessSimplySupportedUdl(section(), 0.0, 1.0, 1.0) }
        reject { Ecp203ShortTermDeflection.stiffness(section()).effectiveInertiaMm4(-1.0) }
    }
}
