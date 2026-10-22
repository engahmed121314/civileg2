package com.civileg.app.domain.modeler

import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.SlabDistribution
import com.civileg.app.domain.entities.SlabPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlabLoadDistributorTest {

    private val nodes = listOf(
        FrameNode(1, 0.0, 0.0),
        FrameNode(2, 8.0, 0.0),
        FrameNode(3, 8.0, 6.0),
        FrameNode(4, 0.0, 6.0)
    )

    private val members = listOf(
        FrameMember(1, 1, 2, memberType = FrameMemberType.Beam),
        FrameMember(2, 2, 3, memberType = FrameMemberType.Beam),
        FrameMember(3, 3, 4, memberType = FrameMemberType.Beam),
        FrameMember(4, 4, 1, memberType = FrameMemberType.Beam)
    )

    @Test
    fun `two way slab distributes total load to all four edges`() {
        val panel = SlabPanel(
            id = 1, nodeIds = listOf(1, 2, 3, 4),
            loadKNm2 = 10.0, distribution = SlabDistribution.TwoWay
        )

        val result = SlabLoadDistributor.distribute(panel, nodes, members)

        assertEquals(480.0, result.totalLoadKN, 1e-9)
        assertEquals(4, result.memberLoads.size)
        assertTrue(result.warnings.isEmpty())
        // long edges (8 m): 10 * (3 - 36/32) = 18.75 kN/m
        assertEquals(18.75, result.memberLoads.first { it.memberId == 1 }.value, 1e-9)
        assertEquals(18.75, result.memberLoads.first { it.memberId == 3 }.value, 1e-9)
        // short edges (6 m): 10 * 6 / 4 = 15 kN/m
        assertEquals(15.0, result.memberLoads.first { it.memberId == 2 }.value, 1e-9)
        assertEquals(15.0, result.memberLoads.first { it.memberId == 4 }.value, 1e-9)
        assertEquals(
            result.totalLoadKN,
            result.memberLoads.sumOf { it.value * edgeLength(it.memberId) },
            1e-6
        )
    }

    @Test
    fun `one way slab sends load to the two long edges`() {
        val panel = SlabPanel(
            id = 1, nodeIds = listOf(1, 2, 3, 4),
            loadKNm2 = 10.0, distribution = SlabDistribution.OneWay
        )

        val result = SlabLoadDistributor.distribute(panel, nodes, members)

        assertEquals(480.0, result.totalLoadKN, 1e-9)
        assertEquals(2, result.memberLoads.size)
        // q * shortSpan / 2 = 10 * 6 / 2 = 30 kN/m on each 8 m edge
        assertTrue(result.memberLoads.all { it.value == 30.0 })
        assertEquals(480.0, result.memberLoads.sumOf { it.value * 8.0 }, 1e-9)
    }

    @Test
    fun `missing supporting member is reported as warning`() {
        val panel = SlabPanel(
            id = 1, nodeIds = listOf(1, 2, 3, 4),
            loadKNm2 = 10.0, distribution = SlabDistribution.TwoWay
        )

        val result = SlabLoadDistributor.distribute(panel, nodes, members.drop(1))

        assertEquals(3, result.memberLoads.size)
        assertTrue(result.warnings.any { it.contains("1-2") })
    }

    private fun edgeLength(memberId: Int): Double = when (memberId) {
        1, 3 -> 8.0
        else -> 6.0
    }
}
