package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.sbc.SbcLiveLoads301
import com.civileg.app.domain.entities.BuildingColumn
import com.civileg.app.domain.entities.BuildingColumnCategory
import com.civileg.app.domain.entities.BuildingFooting
import com.civileg.app.domain.entities.BuildingModel
import com.civileg.app.domain.entities.BuildingNode
import kotlin.math.abs
import kotlin.math.hypot

/** Unfactored (service) load take-down of one column or footing. All values in kN. */
data class BuildingElementLoad(val id: String, val dead: Double, val live: Double) {
    val service: Double get() = dead + live
}

data class BuildingLoadTakedown(
    val model: BuildingModel,
    val columnLoads: List<BuildingElementLoad>,
    val footingLoads: List<BuildingElementLoad>,
    val warnings: List<String>
)

/**
 * Single-level gravity take-down for the 2D building designer.
 *
 * Method (stated, simple, conservative about nothing it cannot know): each slab panel load is shared EQUALLY by the columns that stand on the
 * panel's corner nodes (within [NODE_TOLERANCE_M]). A column that is not on a slab corner receives nothing from that slab, and a slab with no
 * column on any corner is reported, not given to far-away columns. Continuity factors on interior supports, beam self-weight and walls are
 * NOT included (NOT ASSESSED); only slab self-weight, the entered slab dead load (finishes, partitions) and the live load, plus the column and
 * footing self-weight.
 *
 * The result axialLoad is the SERVICE load D + L in kN (unfactored). Factored loads are produced by [com.civileg.app.domain.calculations.sbc.SbcLoadCombinations301]
 * (Eq 2-1, SBC 301-CR-2018 p.13) from the dead and live parts returned here: no "average" factor is applied.
 *
 * @param concreteUnitWeight kN/m3 of reinforced concrete. Default 24.0 is the stone-aggregate value of SBC 301-CR-2018 Table 3-2 (printed p.22);
 *        ECP 201 is not in hand, so no other value is assumed.
 */
object BuildingLoadDistributionEngine {
    const val NODE_TOLERANCE_M = 0.05
    private const val FOOTING_COLUMN_TOLERANCE_M = 0.05

    fun distributeLoads(model: BuildingModel, concreteUnitWeight: Double = SbcLiveLoads301.REINFORCED_CONCRETE_STONE_KN_M3): BuildingModel =
        takedown(model, concreteUnitWeight).model

    fun takedown(model: BuildingModel, concreteUnitWeight: Double = SbcLiveLoads301.REINFORCED_CONCRETE_STONE_KN_M3): BuildingLoadTakedown {
        InputGuard.positive("concreteUnitWeight", concreteUnitWeight)
        if (model.columns.isEmpty() && model.footings.isEmpty()) return BuildingLoadTakedown(model, emptyList(), emptyList(), emptyList())
        val warnings = mutableListOf<String>()
        val nodeMap = model.nodes.associateBy { it.id }
        val colDead = model.columns.associate { it.id to 0.0 }.toMutableMap()
        val colLive = model.columns.associate { it.id to 0.0 }.toMutableMap()

        model.slabs.forEach { slab ->
            InputGuard.positive("slab ${slab.id} thickness (m)", slab.thickness)
            InputGuard.nonNegative("slab ${slab.id} dead load (kN/m2)", slab.deadLoad)
            InputGuard.nonNegative("slab ${slab.id} live load (kN/m2)", slab.liveLoad)
            val corners = slab.nodeIds.mapNotNull { nodeMap[it] }
            if (corners.size != slab.nodeIds.size) {
                warnings.add("Slab ${slab.id}: a node id does not exist, slab load not distributed")
                return@forEach
            }
            if (corners.size < 3) {
                warnings.add("Slab ${slab.id}: fewer than 3 nodes, slab load not distributed")
                return@forEach
            }
            val area = polygonArea(corners)
            if (area <= 0.0) {
                warnings.add("Slab ${slab.id}: zero area, slab load not distributed")
                return@forEach
            }
            val dead = (slab.thickness * concreteUnitWeight + slab.deadLoad) * area
            val live = slab.liveLoad * area
            val supports = model.columns.filter { c -> corners.any { n -> hypot(c.x - n.x, c.y - n.y) <= NODE_TOLERANCE_M } }
            if (supports.isEmpty()) {
                warnings.add("Slab ${slab.id}: no column stands on a slab corner, its load (D %.1f kN, L %.1f kN) is not carried to any column".format(dead, live))
                return@forEach
            }
            if (supports.size < corners.size) warnings.add("Slab ${slab.id}: only ${supports.size} of ${corners.size} corners have a column, the load is shared by those ${supports.size}")
            supports.forEach { c ->
                colDead[c.id] = colDead.getValue(c.id) + dead / supports.size
                colLive[c.id] = colLive.getValue(c.id) + live / supports.size
            }
        }

        val columnLoads = model.columns.map { c ->
            InputGuard.positive("column ${c.id} width (m)", c.width)
            InputGuard.positive("column ${c.id} depth (m)", c.depth)
            InputGuard.positive("column ${c.id} height (m)", c.height)
            val self = if (c.category == BuildingColumnCategory.RC_RECTANGULAR || c.category == BuildingColumnCategory.RC_CIRCULAR || c.category == BuildingColumnCategory.SHEAR_WALL) {
                columnSelfWeight(c, concreteUnitWeight)
            } else {
                warnings.add("Column ${c.id}: steel profile self-weight is not included (profile weight not in the model)")
                0.0
            }
            if (colDead.getValue(c.id) == 0.0 && colLive.getValue(c.id) == 0.0) warnings.add("Column ${c.id}: no slab corner at its position, only self-weight is counted")
            BuildingElementLoad(c.id, colDead.getValue(c.id) + self, colLive.getValue(c.id))
        }
        val loadById = columnLoads.associateBy { it.id }
        val updatedColumns = model.columns.map { c -> c.copy(axialLoad = round1(loadById.getValue(c.id).service)) }

        val footingLoads = mutableListOf<BuildingElementLoad>()
        val updatedFootings = model.footings.map { f ->
            InputGuard.positive("footing ${f.id} length (m)", f.length)
            InputGuard.positive("footing ${f.id} width (m)", f.width)
            InputGuard.positive("footing ${f.id} depth (m)", f.depth)
            val col = nearestColumn(model.columns, f)
            if (col == null) {
                warnings.add("Footing ${f.id}: no column at its position, its load is NOT updated (left as entered, ${f.axialLoad} kN)")
                f.copy(status = "NO_COLUMN")
            } else {
                val cl = loadById.getValue(col.id)
                val self = f.length * f.width * f.depth * concreteUnitWeight
                val fl = BuildingElementLoad(f.id, cl.dead + self, cl.live)
                footingLoads.add(fl)
                f.copy(axialLoad = round1(fl.service), status = "OK")
            }
        }
        return BuildingLoadTakedown(model.copy(columns = updatedColumns, footings = updatedFootings), columnLoads, footingLoads, warnings)
    }

    private fun columnSelfWeight(c: BuildingColumn, gamma: Double): Double =
        if (c.category == BuildingColumnCategory.RC_CIRCULAR) Math.PI * c.width * c.width / 4.0 * c.height * gamma else c.width * c.depth * c.height * gamma

    private fun nearestColumn(columns: List<BuildingColumn>, f: BuildingFooting): BuildingColumn? =
        columns.minByOrNull { hypot(it.x - f.x, it.y - f.y) }?.takeIf { hypot(it.x - f.x, it.y - f.y) <= FOOTING_COLUMN_TOLERANCE_M }

    private fun round1(v: Double) = Math.round(v * 10.0) / 10.0

    private fun polygonArea(nodes: List<BuildingNode>): Double {
        var a = 0.0
        for (i in nodes.indices) {
            val j = (i + 1) % nodes.size
            a += nodes[i].x * nodes[j].y - nodes[j].x * nodes[i].y
        }
        return abs(a) / 2.0
    }
}
