package com.civileg.app.domain.modeler

import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.MemberLoadType
import com.civileg.app.domain.entities.SlabDistribution
import com.civileg.app.domain.entities.SlabPanel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object SlabLoadDistributor {

    data class Result(
        val memberLoads: List<MemberLoad>,
        val totalLoadKN: Double,
        val warnings: List<String> = emptyList()
    )

    private data class Edge(
        val startId: Int,
        val endId: Int,
        val length: Double,
        val memberId: Int?
    )

    fun distribute(
        panel: SlabPanel,
        nodes: List<FrameNode>,
        members: List<FrameMember>
    ): Result {
        if (panel.nodeIds.size != 4) {
            return Result(emptyList(), 0.0, listOf("Slab panel ${panel.id} needs exactly 4 joints"))
        }
        val points = panel.nodeIds.map { id -> nodes.find { it.id == id } }
        if (points.any { it == null }) {
            return Result(emptyList(), 0.0, listOf("Slab panel ${panel.id} references a missing joint"))
        }
        val p = points.filterNotNull()
        val edges = p.indices.map { i ->
            val a = p[i]
            val b = p[(i + 1) % 4]
            val length = kotlin.math.hypot(b.x - a.x, b.y - a.y)
            val member = members.firstOrNull {
                (it.nodeI == a.id && it.nodeJ == b.id) || (it.nodeI == b.id && it.nodeJ == a.id)
            }
            Edge(a.id, b.id, length, member?.id)
        }
        if (edges.any { it.length < 1e-9 }) {
            return Result(emptyList(), 0.0, listOf("Slab panel ${panel.id} has a zero-length edge"))
        }

        // Opposite edge pairs: (0,2) and (1,3).
        val pairA = (edges[0].length + edges[2].length) / 2.0
        val pairB = (edges[1].length + edges[3].length) / 2.0
        val longIsA = pairA >= pairB
        val longSpan = max(pairA, pairB)
        val shortSpan = min(pairA, pairB)
        if (shortSpan < 1e-9) {
            return Result(emptyList(), 0.0, listOf("Slab panel ${panel.id} has an invalid span"))
        }

        val q = panel.loadKNm2
        val area = polygonArea(p)
        val total = q * area
        if (abs(total) < 1e-12) return Result(emptyList(), 0.0)

        val warnings = mutableListOf<String>()
        val loads = mutableListOf<MemberLoad>()

        fun addTo(index: Int, udl: Double) {
            val edge = edges[index]
            val memberId = edge.memberId
            if (memberId == null) {
                warnings += "No supporting member on slab edge ${edge.startId}-${edge.endId}"
                return
            }
            if (udl > 1e-9) {
                loads += MemberLoad(
                    memberId = memberId,
                    loadType = MemberLoadType.UDL,
                    value = udl,
                    position = 0.0,
                    loadCase = panel.loadCase
                )
            }
        }

        if (panel.distribution == SlabDistribution.OneWay) {
            val loadOnLongEdges = q * shortSpan / 2.0
            if (longIsA) {
                addTo(0, loadOnLongEdges)
                addTo(2, loadOnLongEdges)
            } else {
                addTo(1, loadOnLongEdges)
                addTo(3, loadOnLongEdges)
            }
        } else {
            val shortEdgeUdl = q * shortSpan / 4.0
            val longEdgeUdl = q * (shortSpan / 2.0 - shortSpan * shortSpan / (4.0 * longSpan))
            if (longIsA) {
                // Edges 0 and 2 are the long pair.
                addTo(0, longEdgeUdl)
                addTo(2, longEdgeUdl)
                addTo(1, shortEdgeUdl)
                addTo(3, shortEdgeUdl)
            } else {
                addTo(1, longEdgeUdl)
                addTo(3, longEdgeUdl)
                addTo(0, shortEdgeUdl)
                addTo(2, shortEdgeUdl)
            }
        }

        return Result(loads, total, warnings)
    }

    private fun polygonArea(points: List<FrameNode>): Double {
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2.0
    }
}
