package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.BuildingColumn
import com.civileg.app.domain.entities.BuildingFooting
import com.civileg.app.domain.entities.BuildingModel
import com.civileg.app.domain.entities.BuildingNode
import com.civileg.app.domain.entities.BuildingSlab
import kotlin.math.hypot
import kotlin.math.max

object BuildingLoadDistributionEngine {

    /**
     * Re-calculates axial loads for all columns and footings based on slab tributary areas
     * and self-weights.
     */
    fun distributeLoads(model: BuildingModel): BuildingModel {
        if (model.columns.isEmpty() && model.footings.isEmpty()) return model

        // Calculate total load intensity (kN/m²) per slab
        val slabLoadIntensity = model.slabs.associate { slab ->
            val concreteSelfWeight = slab.thickness * 25.0 // 25 kN/m³ for reinforced concrete
            val totalLoad = concreteSelfWeight + slab.deadLoad + (slab.liveLoad * 1.5) // Ultimate load combination 1.4D + 1.6L or ~1.5 average
            slab.id to totalLoad
        }

        // Calculate tributary area for each column by nearest node to slab centers/polygons
        val nodeMap = model.nodes.associateBy { it.id }

        val columnTributaryAreas = mutableMapOf<String, Double>()
        model.columns.forEach { col -> columnTributaryAreas[col.id] = 0.0 }

        model.slabs.forEach { slab ->
            val intensity = slabLoadIntensity[slab.id] ?: 10.0
            val slabNodes = slab.nodeIds.mapNotNull { nodeMap[it] }
            if (slabNodes.size >= 3) {
                val area = calculatePolygonArea(slabNodes)
                val totalSlabLoad = area * intensity

                // Distribute total slab load among nearest columns
                val nearestColumns = model.columns.map { col ->
                    val distToSlabCenter = calculateDistanceToSlabCenter(col.x, col.y, slabNodes)
                    col to distToSlabCenter
                }.sortedBy { it.second }.take(4) // Take 4 closest columns

                if (nearestColumns.isNotEmpty()) {
                    val share = totalSlabLoad / nearestColumns.size
                    nearestColumns.forEach { (col, _) ->
                        columnTributaryAreas[col.id] = (columnTributaryAreas[col.id] ?: 0.0) + share
                    }
                }
            }
        }

        // Update columns with calculated loads
        val updatedColumns = model.columns.map { col ->
            val slabLoad = columnTributaryAreas[col.id] ?: 0.0
            val colSelfWeight = col.width * col.depth * col.height * 25.0
            val totalAxialLoad = max(100.0, slabLoad + colSelfWeight)
            col.copy(axialLoad = Math.round(totalAxialLoad * 10.0) / 10.0)
        }

        // Map updated column loads to corresponding footings (matched by x, y or proximity)
        val updatedFootings = model.footings.map { footing ->
            val matchedCol = updatedColumns.minByOrNull { hypot(it.x - footing.x, it.y - footing.y) }
            val colLoad = if (matchedCol != null && hypot(matchedCol.x - footing.x, matchedCol.y - footing.y) < 2.0) {
                matchedCol.axialLoad
            } else {
                footing.axialLoad
            }
            val footingSelfWeight = footing.length * footing.width * footing.depth * 25.0
            val totalFootingLoad = colLoad + footingSelfWeight
            footing.copy(axialLoad = Math.round(totalFootingLoad * 10.0) / 10.0)
        }

        return model.copy(
            columns = updatedColumns,
            footings = updatedFootings
        )
    }

    private fun calculatePolygonArea(nodes: List<BuildingNode>): Double {
        var area = 0.0
        val n = nodes.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += nodes[i].x * nodes[j].y
            area -= nodes[j].x * nodes[i].y
        }
        return Math.abs(area) / 2.0
    }

    private fun calculateDistanceToSlabCenter(cx: Double, cy: Double, nodes: List<BuildingNode>): Double {
        val centerX = nodes.map { it.x }.average()
        val centerY = nodes.map { it.y }.average()
        return hypot(cx - centerX, cy - centerY)
    }
}
