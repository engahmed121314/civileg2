package com.civileg.core.engine.rc.ecp

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Immediate elastic deflection for a prismatic, simply supported member under UDL.
 * ECP203-2020: Ec Eq2-1; Ie Eq4-70; Mcr Eq4-71; live-only increment Eq4-77.
 * This is not a long-term, cracking, support-detailing or whole-member approval.
 * Loads are characteristic line loads per rib, including all applicable self weight.
 */
object Ecp203ShortTermDeflection {
    enum class FlangeLocation { COMPRESSION_FACE, TENSION_FACE }

    /** Depth is measured from the compression face for this moment region. */
    data class SteelLayer(val areaMm2: Double, val depthMm: Double)

    data class Section(
        val fcuMpa: Double,
        val webWidthMm: Double,
        val flangeWidthMm: Double,
        val flangeThicknessMm: Double,
        val totalDepthMm: Double,
        val flangeLocation: FlangeLocation,
        val steelLayers: List<SteelLayer>
    )

    class Stiffness internal constructor(
        val elasticModulusMpa: Double,
        val modularRatio: Double,
        val grossAreaMm2: Double,
        val grossCentroidFromCompressionFaceMm: Double,
        val grossInertiaMm4: Double,
        val crackedNeutralAxisMm: Double,
        val crackedInertiaMm4: Double,
        val crackingMomentKnM: Double
    ) {
        fun effectiveInertiaMm4(serviceMomentKnM: Double): Double {
            require(serviceMomentKnM.isFinite() && serviceMomentKnM >= 0.0)
            if (serviceMomentKnM <= crackingMomentKnM) return grossInertiaMm4
            val ratioCubed = (crackingMomentKnM / serviceMomentKnM).pow(3)
            return min(grossInertiaMm4, ratioCubed * grossInertiaMm4 +
                (1.0 - ratioCubed) * min(crackedInertiaMm4, grossInertiaMm4))
        }
    }

    data class LoadCase(
        val characteristicLineLoadKnPerM: Double,
        val maximumMomentKnM: Double,
        val effectiveInertiaMm4: Double,
        val immediateDeflectionMm: Double,
        val fullyCrackedElasticTensionSteelStressMpa: Double
    )

    data class Assessment(
        val stiffness: Stiffness,
        val dead: LoadCase,
        val deadPlusLive: LoadCase,
        val immediateLiveDeflectionMm: Double
    )

    private data class Strip(val width: Double, val start: Double, val end: Double) {
        val area get() = width * (end - start)
        val center get() = (start + end) / 2.0
        fun firstMomentAbout(c: Double): Double {
            val z = min(c, end)
            return if (z <= start) 0.0 else width * (z - start) * (c - (start + z) / 2.0)
        }
        fun inertiaAbout(c: Double): Double {
            val z = min(c, end)
            return if (z <= start) 0.0 else width * ((c - start).pow(3) - (c - z).pow(3)) / 3.0
        }
    }

    fun stiffness(section: Section): Stiffness {
        with(section) {
            require(fcuMpa.isFinite() && fcuMpa in 20.0..60.0)
            require(webWidthMm.isFinite() && webWidthMm > 0.0)
            require(flangeWidthMm.isFinite() && flangeWidthMm >= webWidthMm)
            require(totalDepthMm.isFinite() && totalDepthMm > 0.0)
            require(flangeThicknessMm.isFinite() && flangeThicknessMm >= 0.0 && flangeThicknessMm < totalDepthMm)
            require(flangeWidthMm == webWidthMm || flangeThicknessMm > 0.0)
            val steel = steelLayers.toList()
            require(steel.isNotEmpty())
            require(steel.all { it.areaMm2.isFinite() && it.areaMm2 > 0.0 &&
                it.depthMm.isFinite() && it.depthMm > 0.0 && it.depthMm < totalDepthMm })
            val strips = mutableListOf(Strip(webWidthMm, 0.0, totalDepthMm))
            if (flangeWidthMm > webWidthMm) strips += if (flangeLocation == FlangeLocation.COMPRESSION_FACE)
                Strip(flangeWidthMm - webWidthMm, 0.0, flangeThicknessMm)
            else Strip(flangeWidthMm - webWidthMm, totalDepthMm - flangeThicknessMm, totalDepthMm)
            val area = strips.sumOf { it.area }
            val centroid = strips.sumOf { it.area * it.center } / area
            val ig = strips.sumOf { it.width * (it.end - it.start).pow(3) / 12.0 +
                it.area * (it.center - centroid).pow(2) }
            require(area.isFinite() && area > 0.0 && ig.isFinite() && ig > 0.0)
            require(steel.sumOf { it.areaMm2 }.let { it.isFinite() && it < area })
            val ec = 4400.0 * sqrt(fcuMpa)
            val n = 200000.0 / ec
            // Cracked transformed section: no tensile concrete. Compression bars
            // replace their concrete area, so their equivalent factor is n-1.
            fun factor(layer: SteelLayer, c: Double) = if (layer.depthMm < c) n - 1.0 else n
            fun equilibrium(c: Double): Double = strips.sumOf { it.firstMomentAbout(c) } -
                steel.sumOf { factor(it, c) * it.areaMm2 * (it.depthMm - c) }
            var lo = 0.0
            var hi = totalDepthMm
            repeat(100) {
                val c = (lo + hi) / 2.0
                if (equilibrium(c) < 0.0) lo = c else hi = c
            }
            val c = (lo + hi) / 2.0
            val transformedIcr = strips.sumOf { it.inertiaAbout(c) } +
                steel.sumOf { factor(it, c) * it.areaMm2 * (it.depthMm - c).pow(2) }
            val mcr = 0.6 * sqrt(fcuMpa) * ig / (totalDepthMm - centroid) / 1e6
            require(transformedIcr.isFinite() && transformedIcr > 0.0 && mcr.isFinite() && mcr > 0.0)
            // Printed4-60 defines the cracked inertia used in Eq4-70 as <=Ig.
            return Stiffness(ec, n, area, centroid, ig, c, min(transformedIcr, ig), mcr)
        }
    }

    fun assessSimplySupportedUdl(section: Section, analysisSpanMm: Double,
        characteristicDeadLineLoadKnPerM: Double, characteristicLiveLineLoadKnPerM: Double): Assessment {
        require(analysisSpanMm.isFinite() && analysisSpanMm > 0.0)
        require(characteristicDeadLineLoadKnPerM.isFinite() && characteristicDeadLineLoadKnPerM >= 0.0)
        require(characteristicLiveLineLoadKnPerM.isFinite() && characteristicLiveLineLoadKnPerM >= 0.0)
        val k = stiffness(section)
        fun response(q: Double): LoadCase {
            require(q.isFinite())
            val moment = q * (analysisSpanMm / 1000.0).pow(2) / 8.0
            val ie = k.effectiveInertiaMm4(moment)
            // Elastic simply supported UDL solution. 1 kN/m = 1 N/mm.
            val delta = 5.0 * q * analysisSpanMm.pow(4) / (384.0 * k.elasticModulusMpa * ie)
            require(delta.isFinite() && delta >= 0.0)
            val stress = k.modularRatio * moment * 1e6 *
                max(0.0, section.steelLayers.maxOf { it.depthMm } - k.crackedNeutralAxisMm) / k.crackedInertiaMm4
            require(stress.isFinite() && stress >= 0.0)
            return LoadCase(q, moment, ie, delta, stress)
        }
        val dead = response(characteristicDeadLineLoadKnPerM)
        val total = response(characteristicDeadLineLoadKnPerM + characteristicLiveLineLoadKnPerM)
        return Assessment(k, dead, total, max(0.0, total.immediateDeflectionMm - dead.immediateDeflectionMm))
    }
}
