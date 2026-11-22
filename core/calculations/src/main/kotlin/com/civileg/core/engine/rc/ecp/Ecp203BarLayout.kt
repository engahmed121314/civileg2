package com.civileg.core.engine.rc.ecp

import kotlin.math.PI
import kotlin.math.max

/** Individual, nonbundled bars: ECP203-2020 printed7-6/7-7, clause7-3-3-1, Fig7-2a.
 * Cover is an explicit geometric input, not an exposure/fire-cover approval.
 * Layers run from the tension face inward. No anchorage, bend or crack-width check.
 */
object Ecp203BarLayout {
    data class Layer(val count: Int, val diameterMm: Double)
    data class Assessment(val providedSteelMm2: Double, val minimumClearGapMm: Double,
        val layerCentersFromTensionFaceMm: List<Double>, val fitsInsideLinks: Boolean,
        val effectiveDepthMm: Double?)

    data class PlacedBar(val layerIndex: Int, val indexInLayer: Int,
        val xFromWebLeftMm: Double, val distanceFromTensionFaceMm: Double, val diameterMm: Double)

    /**
     * Explicit adopted design arrangement, not as-built/user-entered x coordinates.
     * Each row is symmetric: one bar is centered; multiple bars are spread between
     * the two covered cage limits. Vertical centres are the SAME assessed layers.
     * The renderer must consume these positions, never repeat this choice itself.
     * Null only denotes the coordinate-export budget, not an engineering failure.
     */
    fun adoptSymmetricRows(widthMm: Double, coverToOutsideLinksMm: Double,
        linkDiameterMm: Double, layers: List<Layer>, assessment: Assessment): List<PlacedBar>? {
        require(assessment.fitsInsideLinks && assessment.effectiveDepthMm != null)
        require(widthMm.isFinite() && widthMm > 0.0 &&
            coverToOutsideLinksMm.isFinite() && coverToOutsideLinksMm >= 0.0 &&
            linkDiameterMm.isFinite() && linkDiameterMm >= 0.0)
        require(layers.isNotEmpty() && layers.size == assessment.layerCentersFromTensionFaceMm.size)
        require(layers.all { it.count > 0 && it.diameterMm.isFinite() && it.diameterMm > 0.0 })
        if (layers.sumOf { it.count.toLong() } > 4096L) return null
        val inset = coverToOutsideLinksMm + linkDiameterMm
        require(inset.isFinite())
        return layers.flatMapIndexed { row, layer ->
            val left = inset + layer.diameterMm / 2.0
            val right = widthMm - left
            require(right >= left)
            val step = if (layer.count == 1) 0.0 else (right - left) / (layer.count - 1)
            require(layer.count == 1 || step - layer.diameterMm >= assessment.minimumClearGapMm - 1e-9)
            val y = assessment.layerCentersFromTensionFaceMm[row]
            require(y.isFinite() && y > 0.0)
            (0 until layer.count).map { index ->
                val x = if (layer.count == 1) widthMm / 2.0 else left + index * step
                require(x.isFinite())
                PlacedBar(row, index, x, y, layer.diameterMm)
            }
        }.toList()
    }

    fun assess(widthMm: Double, totalDepthMm: Double, coverToOutsideLinksMm: Double,
        linkDiameterMm: Double, nominalMaximumAggregateMm: Double, layers: List<Layer>): Assessment {
        require(listOf(widthMm, totalDepthMm, nominalMaximumAggregateMm).all { it.isFinite() && it > 0.0 })
        require(coverToOutsideLinksMm.isFinite() && coverToOutsideLinksMm >= 0.0)
        require(linkDiameterMm.isFinite() && linkDiameterMm >= 0.0)
        require(layers.isNotEmpty())
        require(layers.all { it.count > 0 && it.diameterMm.isFinite() && it.diameterMm > 0.0 })
        val gap = max(layers.maxOf { it.diameterMm }, 1.5 * nominalMaximumAggregateMm)
        val inset = coverToOutsideLinksMm + linkDiameterMm
        val availableWidth = widthMm - 2.0 * inset
        require(gap.isFinite() && inset.isFinite() && availableWidth.isFinite())
        var cursor = inset
        var totalArea = 0.0
        var firstMoment = 0.0
        var horizontalFit = availableWidth > 0.0
        val centers = layers.map { layer ->
            val rowWidth = layer.count * layer.diameterMm + (layer.count - 1.0) * gap
            require(rowWidth.isFinite())
            horizontalFit = horizontalFit && rowWidth <= availableWidth + 1e-9
            val center = cursor + layer.diameterMm / 2.0
            val area = layer.count * PI * layer.diameterMm * layer.diameterMm / 4.0
            totalArea += area
            firstMoment += area * center
            cursor += layer.diameterMm + gap
            require(center.isFinite() && area.isFinite() && totalArea.isFinite() && firstMoment.isFinite() && cursor.isFinite())
            center
        }
        val lastBarOuterEdge = centers.last() + layers.last().diameterMm / 2.0
        val verticalFit = lastBarOuterEdge <= totalDepthMm - inset + 1e-9
        val depth = totalDepthMm - firstMoment / totalArea
        val fits = horizontalFit && verticalFit && depth.isFinite() && depth > 0.0 && depth < totalDepthMm
        return Assessment(totalArea, gap, centers, fits, if (fits) depth else null)
    }
}
