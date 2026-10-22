package com.civileg.app.domain.modeler

import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.MemberLoadType
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SlabPanel
import com.civileg.app.domain.entities.SupportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameDxfBuilderTest {

    private val squareNodes = listOf(
        FrameNode(1, 0.0, 0.0, SupportType.Pin),
        FrameNode(2, 4.0, 0.0, SupportType.Roller),
        FrameNode(3, 4.0, 3.0),
        FrameNode(4, 0.0, 3.0)
    )

    private val squareMembers = listOf(
        FrameMember(1, 1, 2),
        FrameMember(2, 2, 3),
        FrameMember(3, 3, 4),
        FrameMember(4, 4, 1)
    )

    @Test
    fun writesR12HeaderBlocksAndLayerTable() {
        val dxf = FrameDxfBuilder.build(squareNodes, squareMembers, emptyList(), emptyList(), emptyList())

        assertTrue(dxf.startsWith("0\nSECTION\n2\nHEADER"))
        assertTrue(dxf.contains("\$ACADVER\n1\nAC1009"))
        assertTrue(dxf.contains("0\nSECTION\n2\nBLOCKS\n0\nENDSEC"))
        assertTrue(dxf.contains("0\nSECTION\n2\nTABLES"))
        assertTrue(dxf.contains("2\nLTYPE"))
        assertTrue(dxf.contains("2\nSTYLE"))
        assertTrue(dxf.contains("2\nMEMBERS"))
        assertTrue(dxf.endsWith("0\nENDSEC\n0\nEOF\n"))
    }

    @Test
    fun convertsMetersToMillimeters() {
        val nodes = listOf(
            FrameNode(1, 1.25, -2.5),
            FrameNode(2, 2.0, 0.0)
        )
        val dxf = FrameDxfBuilder.build(
            nodes,
            listOf(FrameMember(1, 1, 2)),
            emptyList(),
            emptyList(),
            emptyList()
        )

        assertTrue(dxf.contains("1250.000"))
        assertTrue(dxf.contains("-2500.000"))
        assertTrue(dxf.contains("2000.000"))
    }

    @Test
    fun writesClosedSlabPolylineWithFourVertices() {
        val slab = SlabPanel(
            id = 1,
            nodeIds = listOf(1, 2, 3, 4),
            thicknessMm = 200.0
        )
        val dxf = FrameDxfBuilder.build(
            squareNodes,
            squareMembers,
            emptyList(),
            emptyList(),
            listOf(slab)
        )

        assertTrue(dxf.contains("0\nPOLYLINE\n8\nSLABS\n66\n1\n70\n1"))
        assertEquals(4, dxf.windowed("VERTEX".length).count { it == "VERTEX" })
        assertTrue(dxf.contains("S1 200.00mm"))
    }

    @Test
    fun writesLoadCaseLayersAndLoadLabels() {
        val nodalLoad = NodalLoad(nodeId = 2, fx = 10.0, fy = -20.0, loadCase = "LL")
        val memberLoad = MemberLoad(
            memberId = 1,
            loadType = MemberLoadType.UDL,
            value = 12.5,
            loadCase = "Wind X"
        )
        val dxf = FrameDxfBuilder.build(
            squareNodes,
            squareMembers,
            listOf(nodalLoad),
            listOf(memberLoad),
            emptyList()
        )

        assertTrue(dxf.contains("2\nLOAD_LL"))
        assertTrue(dxf.contains("2\nLOAD_WIND_X"))
        assertTrue(dxf.contains("Fx=10.00 Fy=-20.00"))
        assertTrue(dxf.contains("w=12.50 kN/m"))
    }

    @Test
    fun writesSupportsAndJoints() {
        val dxf = FrameDxfBuilder.build(squareNodes, squareMembers, emptyList(), emptyList(), emptyList())

        assertTrue(dxf.contains("8\nSUPPORTS"))
        assertTrue(dxf.contains("8\nJOINTS"))
        assertTrue(dxf.contains("8\nTEXT"))
    }

    @Test
    fun emptyModelStillProducesValidFile() {
        val dxf = FrameDxfBuilder.build(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        assertTrue(dxf.startsWith("0\nSECTION\n2\nHEADER"))
        assertTrue(dxf.contains("0\nSECTION\n2\nENTITIES\n0\nENDSEC"))
        assertTrue(dxf.endsWith("0\nEOF\n"))
    }
}
