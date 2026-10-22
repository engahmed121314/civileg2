package com.civileg.app.domain.entities

import org.junit.Assert.assertEquals
import org.junit.Test

class SlabPanelTest {

    private val nodes = listOf(
        FrameNode(1, 0.0, 0.0),
        FrameNode(2, 6.0, 0.0),
        FrameNode(3, 6.0, 4.0),
        FrameNode(4, 0.0, 4.0)
    )

    @Test
    fun orderQuadJoints_sortsTapOrderIntoSimplePolygon() {
        val ordered = orderQuadJoints(listOf(3, 1, 4, 2), nodes)
        assertEquals(listOf(1, 2, 3, 4), ordered)
    }

    @Test
    fun orderQuadJoints_keepsInvalidSelectionUnchanged() {
        val ids = listOf(1, 2, 3)
        assertEquals(ids, orderQuadJoints(ids, nodes))
    }
}
