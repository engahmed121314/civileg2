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
    val depth: Double = 400.0  // mm
) : Parcelable

@Parcelize
data class LayoutRecommendation(
    val suggestedType: String,
    val totalFootingArea: Double,
    val plotArea: Double,
    val coverageRatio: Double,
    val overlapsFound: Int,
    val axesX: List<Double>,
    val axesY: List<Double>,
    val description: String
) : Parcelable

object LayoutOptimizer {

    /**
     * Analyzes a site layout and recommends the most economical foundation system.
     */
    fun analyzeLayout(
        plotWidth: Double, // m
        plotLength: Double, // m
        columns: List<ColumnLoad>,
        soilCapacity: Double, // kPa
        designCode: CalculatorEngine.DesignCode
    ): LayoutRecommendation {
        val plotArea = plotWidth * plotLength
        var totalAreaNeeded = 0.0
        val axesX = columns.map { it.x }.distinct().sorted()
        val axesY = columns.map { it.y }.distinct().sorted()

        // Calculate needed area for each column as isolated
        val footingBounds = mutableListOf<RectF>()
        columns.forEach { col ->
            val areaReq = (col.axialLoad * 1.1) / soilCapacity
            val side = sqrt(areaReq)
            val halfSide = side * 1000.0 / 2.0
            totalAreaNeeded += areaReq
            
            // Create a virtual rectangle for overlap detection
            footingBounds.add(RectF(
                (col.x - halfSide).toFloat(),
                (col.y - halfSide).toFloat(),
                (col.x + halfSide).toFloat(),
                (col.y + halfSide).toFloat()
            ))
        }

        val coverageRatio = totalAreaNeeded / plotArea
        var overlapCount = 0
        for (i in 0 until footingBounds.size) {
            for (j in i + 1 until footingBounds.size) {
                if (intersects(footingBounds[i], footingBounds[j])) {
                    overlapCount++
                }
            }
        }

        val suggestion = when {
            coverageRatio > 0.65 -> "Raft Foundation (Full Mat)"
            coverageRatio > 0.45 || overlapCount > columns.size * 0.3 -> "American Hybrid Raft (REB)"
            overlapCount > 0 -> "Combined Footings & Isolated Mix"
            else -> "Isolated Footings"
        }

        val desc = when (suggestion) {
            "Raft Foundation (Full Mat)" -> "Footing area covers ${ (coverageRatio * 100).toInt() }% of plot. A full raft is mandatory."
            "American Hybrid Raft (REB)" -> "High density or varying loads detected. REB (Thickened Raft) provides stiffness with less concrete."
            else -> "Standard isolated footings are suitable for this soil capacity."
        }

        return LayoutRecommendation(
            suggestedType = suggestion,
            totalFootingArea = totalAreaNeeded,
            plotArea = plotArea,
            coverageRatio = coverageRatio,
            overlapsFound = overlapCount,
            axesX = axesX,
            axesY = axesY,
            description = desc
        )
    }

    private fun intersects(r1: RectF, r2: RectF): Boolean {
        return r1.left < r2.right && r2.left < r1.right && r1.top < r2.bottom && r2.top < r1.bottom
    }

    // Simplified RectF to avoid Android dependency in pure logic if possible, 
    // but we use it here for clarity.
    data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)
}
