package com.civileg.app.domain.modeler

import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.MemberLoadType
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
        // المرحلة الثالثة (P4-1): كل الحواف تحمل شكل Trapezoidal بالشكل الكلاسيكي الدقيق
        // w = q·S/2 = 30 على الحواف الأربع، والارتفاع a = S/2 = 3
        result.memberLoads.forEach { assertTrue("All edges must be Trapezoidal, got ${it.loadType}", it.loadType == MemberLoadType.Trapezoidal) }
        assertEquals(30.0, result.memberLoads.first { it.memberId == 1 }.value, 1e-9)
        assertEquals(30.0, result.memberLoads.first { it.memberId == 3 }.value, 1e-9)
        assertEquals(30.0, result.memberLoads.first { it.memberId == 2 }.value, 1e-9)
        assertEquals(30.0, result.memberLoads.first { it.memberId == 4 }.value, 1e-9)
        assertEquals(3.0, result.memberLoads.first { it.memberId == 1 }.position, 1e-9)
        assertEquals(3.0, result.memberLoads.first { it.memberId == 2 }.position, 1e-9)
        // حفظ الكلية: W = w·(L_edge − a) لكل حافة (وليس value × L_edge كما في UDL)
        assertEquals(
            result.totalLoadKN,
            result.memberLoads.sumOf { it.value * (edgeLength(it.memberId) - it.position) },
            1e-6
        )
    }

    @Test
    fun `two way slab per-edge totals match the classical closed forms`() {
        // المرحلة الثالثة: الحمل الكلي على كل كمرة = الشكل الكلاسيكي:
        //   الطويلة (8 م): q·SL/2 − q·S²/4 = 150 kN  (شبه منحرف، هضبة من 3 إلى 5)
        //   القصيرة (6 م): q·S²/4 = 90 kN            (مثلث متماثل لأن a = 3 = L/2)
        //   مجموع الأربعة = 480 = q·S·L = الحمل الكلي للبلاطة بالضبط
        val panel = SlabPanel(
            id = 1, nodeIds = listOf(1, 2, 3, 4),
            loadKNm2 = 10.0, distribution = SlabDistribution.TwoWay
        )

        val result = SlabLoadDistributor.distribute(panel, nodes, members)

        val longBeamTotal = result.memberLoads.first { it.memberId == 1 }
            .let { it.value * (edgeLength(1) - it.position) }
        val shortBeamTotal = result.memberLoads.first { it.memberId == 2 }
            .let { it.value * (edgeLength(2) - it.position) }
        assertEquals("Long edge total = q·SL/2 − q·S²/4 = 150", 150.0, longBeamTotal, 1e-9)
        assertEquals("Short edge total = q·S²/4 = 90", 90.0, shortBeamTotal, 1e-9)
        // القصيرة: a = 3 = L_edge/2 → المثلث المتماثل (ذروة الهضبة تنكمش لنقطة)
        assertEquals("Short edge a = L/2 exactly (symmetric triangle)", 3.0,
            result.memberLoads.first { it.memberId == 2 }.position, 1e-9)
        assertEquals("Σ = q·area = 480", 480.0,
            2 * longBeamTotal + 2 * shortBeamTotal, 1e-6)
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
