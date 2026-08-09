package com.civileg.app.utils

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.math.*

@Parcelize
data class ColumnLoad(
    val id: String,
    val x: Double, // mm from origin
    val y: Double, // mm from origin
    val axialLoad: Double, // kN (working)
    val width: Double = 400.0, // mm
    val depth: Double = 400.0,  // mm
    val isNeighbor: Boolean = false // [NEW] If true, column is on plot boundary
) : Parcelable

@Parcelize
data class FootingBound(
    val id: String,
    val centerX: Double,
    val centerY: Double,
    val width: Double,
    val length: Double,
    val type: String, // Isolated, Combined, Boundary, Strap, PileCap
    val numPiles: Int = 0, // [NEW] for deep foundations
    val pileLayout: String = "" // [NEW] e.g. "2x2", "Circular"
) : Parcelable

@Parcelize
data class LayoutRecommendation(
    val suggestedType: String,
    val totalFootingArea: Double,
    val plotArea: Double,
    val coverageRatio: Double,
    val overlapsFound: Int,
    val axesX: List<AxisInfo>,
    val axesY: List<AxisInfo>,
    val footingBounds: List<FootingBound>,
    val description: String,
    val totalConcreteEst: Double = 0.0, // [NEW] For Phase 3
    val totalSteelEst: Double = 0.0     // [NEW] For Phase 3
) : Parcelable

@Parcelize
data class AxisInfo(
    val coordinate: Double,
    val label: String
) : Parcelable

object LayoutOptimizer {

    fun analyzeLayout(
        plotWidth: Double,
        plotLength: Double,
        columns: List<ColumnLoad>,
        soilCapacity: Double,
        designCode: CalculatorEngine.DesignCode
    ): LayoutRecommendation {
        val plotArea = plotWidth * plotLength
        var totalAreaNeeded = 0.0
        val footingBounds = mutableListOf<FootingBound>()
        
        columns.forEach { col ->
            var areaReq = (col.axialLoad * 1.15) / soilCapacity
            var type = "Isolated"
            var numPiles = 0
            
            // [PHASE 2]: Deep Foundation Trigger
            // If load > 4000kN or soil capacity < 100kPa, suggest Piles
            if (col.axialLoad > 4000 || soilCapacity < 80) {
                type = "PileCap"
                val pileCapacity = soilCapacity * 2.5 // Rough estimation
                numPiles = ceil(col.axialLoad / pileCapacity).toInt().coerceAtLeast(2)
                areaReq = numPiles * 0.6 * 0.6 * 2.5 // Cap size estimate
            }
            
            val side = sqrt(areaReq)
            val ratio = col.depth / col.width
            var L = side * sqrt(ratio)
            var B = side / sqrt(ratio)
            
            var cx = col.x
            var cy = col.y

            // Boundary Logic
            if (type != "PileCap" && (col.isNeighbor || cx - B*500 < 0 || cx + B*500 > plotWidth*1000 || 
                cy - L*500 < 0 || cy + L*500 > plotLength*1000)) {
                type = "Boundary"
                if (cx - B * 500 < 0) cx = B * 500
                if (cx + B * 500 > plotWidth * 1000) cx = plotWidth * 1000 - B * 500
                if (cy - L * 500 < 0) cy = L * 500
                if (cy + L * 500 > plotLength * 1000) cy = plotLength * 1000 - L * 500
            }
            
            footingBounds.add(FootingBound(col.id, cx, cy, B * 1000.0, L * 1000.0, type, numPiles))
            totalAreaNeeded += areaReq
        }

        val overlaps = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until footingBounds.size) {
            for (j in i + 1 until footingBounds.size) {
                if (checkOverlap(footingBounds[i], footingBounds[j])) overlaps.add(i to j)
            }
        }

        val coverageRatio = totalAreaNeeded / plotArea
        val suggestion = when {
            columns.any { it.axialLoad > 4500 } || soilCapacity < 100 -> "Deep Foundation (Piles)"
            coverageRatio > 0.60 -> "Raft Foundation (Full Mat)"
            coverageRatio > 0.40 -> "Strip/Grid Foundation (REB)"
            overlaps.size > columns.size * 0.2 -> "Combined Footings (Mix)"
            else -> "Isolated Footings"
        }

        val desc = when (suggestion) {
            "Deep Foundation (Piles)" -> "Massive loads or weak soil detected. Piles required to reach stable strata."
            "Raft Foundation (Full Mat)" -> "High coverage (${(coverageRatio*100).toInt()}%). Uniform raft required."
            "Strip/Grid Foundation (REB)" -> "Ribbed raft suggested to manage dense column layout."
            else -> "Isolated footings are economical. Boundary columns require strap beams."
        }

        // [PHASE 3]: Financial / Quantity Estimation
        val totalConcrete = totalAreaNeeded * 0.7 // Average 70cm thickness
        val totalSteel = totalConcrete * 110.0  // 110kg/m3 average

        // Professional Axis Clustering with Labels
        val axesX = clusterAxes(columns.map { it.x }).mapIndexed { i, coord -> AxisInfo(coord, (i + 1).toString()) }
        val axesY = clusterAxes(columns.map { it.y }).mapIndexed { i, coord -> AxisInfo(coord, ('A'.code + i).toChar().toString()) }

        return LayoutRecommendation(
            suggestedType = suggestion,
            totalFootingArea = totalAreaNeeded,
            plotArea = plotArea,
            coverageRatio = coverageRatio,
            overlapsFound = overlaps.size,
            axesX = axesX,
            axesY = axesY,
            footingBounds = footingBounds,
            totalConcreteEst = totalConcrete,
            totalSteelEst = totalSteel,
            description = desc
        )
    }

    private fun checkOverlap(f1: FootingBound, f2: FootingBound): Boolean {
        val dx = abs(f1.centerX - f2.centerX)
        val dy = abs(f1.centerY - f2.centerY)
        return dx < (f1.width + f2.width) / 2.0 && dy < (f1.length + f2.length) / 2.0
    }

    private fun clusterAxes(coords: List<Double>): List<Double> {
        if (coords.isEmpty()) return emptyList()
        val sorted = coords.sorted()
        val result = mutableListOf<Double>()
        var currentCluster = mutableListOf(sorted[0])
        val tolerance = 700.0 // mm
        
        for (i in 1 until sorted.size) {
            if (sorted[i] - currentCluster.last() <= tolerance) {
                currentCluster.add(sorted[i])
            } else {
                result.add(currentCluster.average())
                currentCluster = mutableListOf(sorted[i])
            }
        }
        result.add(currentCluster.average())
        return result
    }
}
