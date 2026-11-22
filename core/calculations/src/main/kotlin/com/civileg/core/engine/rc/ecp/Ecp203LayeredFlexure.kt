package com.civileg.core.engine.rc.ecp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Layer-by-layer tension-steel compatibility, ECP203-2020 printed4-1..4-4.
 * Plane sections, epsilon_cu=0.003, Es=200000MPa, design steel stress <=fy/1.15.
 * The same .67fcu/1.5, .8c block and verified no-redistribution envelope apply.
 * Compression reinforcement is outside this singly reinforced component.
 */
object Ecp203LayeredFlexure {
    data class Assessment(val requiredSteelMm2: Double?, val maximumSteelMm2: Double,
        val momentResistanceKnM: Double, val equilibriumMomentKnM: Double, val neutralAxisDepthMm: Double,
        val layerStressMpa: List<Double>, val allLayersAtDesignYield: Boolean,
        val flexureChecksPass: Boolean)

    private data class Response(val moment: Double, val equilibriumMoment: Double, val c: Double, val stresses: List<Double>)

    fun assess(section: Ecp203RectangularFlexure.Section, flangeWidthMm: Double,
        flangeThicknessMm: Double, layers: List<Ecp203BarLayout.Layer>,
        centersFromTensionFaceMm: List<Double>, designMomentKnM: Double): Assessment {
        val rectangular = Ecp203RectangularFlexure.assess(section, designMomentKnM)
        require(layers.isNotEmpty() && layers.size == centersFromTensionFaceMm.size)
        require(layers.all { it.count > 0 && it.diameterMm.isFinite() && it.diameterMm > 0.0 })
        val depths = centersFromTensionFaceMm.map { section.totalDepthMm - it }
        require(depths.all { it.isFinite() && it > 0.0 && it < section.totalDepthMm })
        val areas = layers.map { it.count * PI * it.diameterMm * it.diameterMm / 4.0 }
        val area = areas.sum()
        require(area.isFinite() && area > 0.0)
        val centroid = areas.indices.sumOf { areas[it] * depths[it] } / area
        require(abs(centroid - section.effectiveDepthMm) <= 1e-8 * section.totalDepthMm) {
            "Section depth must match the supplied actual layer centroid."
        }
        require(flangeWidthMm.isFinite() && flangeWidthMm >= section.widthMm)
        require(flangeThicknessMm.isFinite() && flangeThicknessMm > 0.0 && flangeThicknessMm < section.totalDepthMm)
        val fcd = 0.67 * section.fcuMpa / Ecp203RectangularFlexure.GAMMA_C
        val fyd = section.fyMpa / Ecp203RectangularFlexure.GAMMA_S
        val minimum = rectangular.minimumSteelMm2
        val rectangularEnvelope = Ecp203RectangularFlexure.assess(section, 0.0)
        val aMax = rectangularEnvelope.maximumSinglyReinforcedSteelMm2 * fyd / (fcd * section.widthMm)
        val envelopeC = aMax / 0.8
        val allTensionC = depths.min() * (1.0 - 1e-12)

        fun concrete(c: Double): Pair<Double, Double> {
            val a = 0.8 * c
            val af = min(a, flangeThicknessMm)
            val aw = max(0.0, a - flangeThicknessMm)
            val force = fcd * (flangeWidthMm * af + section.widthMm * aw)
            val first = fcd * (flangeWidthMm * af * af / 2.0 +
                section.widthMm * aw * (flangeThicknessMm + aw / 2.0))
            return force to first
        }
        fun stresses(c: Double): List<Double> = depths.map { d ->
            min(fyd, max(0.0, 200000.0 * 0.003 * (d - c) / c))
        }
        fun unscaledTension(c: Double): Double {
            val stress = stresses(c)
            return areas.indices.sumOf { areas[it] * stress[it] }
        }
        fun response(steelArea: Double): Response {
            if (steelArea <= 0.0) return Response(0.0, 0.0, 0.0, List(layers.size) { fyd })
            val scale = steelArea / area
            require(concrete(allTensionC).first + 1e-7 >= scale * unscaledTension(allTensionC)) {
                "Selected layers include compression reinforcement at ultimate equilibrium; use a verified mixed-reinforcement method."
            }
            var lo = 0.0
            var hi = allTensionC
            repeat(100) {
                val c = (lo + hi) / 2.0
                if (concrete(c).first < scale * unscaledTension(c)) lo = c else hi = c
            }
            val c = (lo + hi) / 2.0
            val (compression, first) = concrete(c)
            val stress = stresses(c)
            val steelMoment = areas.indices.sumOf { scale * areas[it] * stress[it] * depths[it] }
            val moment = min(steelMoment - first, compression * 0.95 * centroid) / 1e6
            require(moment.isFinite() && moment >= 0.0)
            return Response(moment, (steelMoment - first) / 1e6, c, stress)
        }
        val (maximumCompression, _) = concrete(envelopeC)
        // Keep the existing conservative printed-table steel ceiling. Do not
        // expand it merely because an inner layer has not reached design yield.
        val tableAreaCeiling = maximumCompression / fyd
        val boundaryC = min(envelopeC, allTensionC)
        val compatibilityCeiling = concrete(boundaryC).first / unscaledTension(boundaryC) * area
        val maximumArea = min(tableAreaCeiling, compatibilityCeiling)
        require(maximumArea.isFinite() && maximumArea > 0.0)
        val maximumResponse = response(maximumArea)
        val actual = response(area)
        val capacity = min(actual.moment, maximumResponse.moment)
        val required = if (designMomentKnM > maximumResponse.moment + 1e-9 || minimum > maximumArea + 1e-9) null else {
            var lo = 0.0
            var hi = maximumArea
            repeat(80) {
                val trial = (lo + hi) / 2.0
                if (response(trial).moment < designMomentKnM) lo = trial else hi = trial
            }
            max(minimum, (lo + hi) / 2.0)
        }
        return Assessment(required, maximumArea, capacity, actual.equilibriumMoment, actual.c, actual.stresses,
            actual.stresses.all { it + 1e-9 >= fyd },
            required != null && area + 1e-9 >= minimum && area <= maximumArea + 1e-9 &&
                actual.c <= envelopeC + 1e-9 && designMomentKnM <= capacity + 1e-9)
    }
}
