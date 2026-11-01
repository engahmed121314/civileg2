package com.civileg.app.domain.modeler

import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.MemberLoadType
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SlabPanel
import com.civileg.app.domain.entities.SupportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun writesAc1027HeaderTablesAndBlocks() {
        val dxf = FrameDxfBuilder.build(squareNodes, squareMembers, emptyList(), emptyList(), emptyList())

        assertTrue(dxf.startsWith("0\nSECTION\n2\nHEADER"))
        assertTrue(dxf.contains("\$ACADVER\n1\nAC1027"))
        assertTrue(dxf.contains("\$HANDSEED\n5\nFFFFFFFF"))
        // Geometry is written in millimeters, so the header must declare INSUNITS = 4.
        assertTrue(dxf.contains("\$INSUNITS\n70\n4"))
        assertTrue(dxf.contains("0\nSECTION\n2\nTABLES"))
        assertTrue(dxf.contains("2\nLTYPE"))
        assertTrue(dxf.contains("2\nLAYER"))
        assertTrue(dxf.contains("2\nSTYLE"))
        assertTrue(dxf.contains("2\nBLOCK_RECORD"))
        assertTrue(dxf.contains("2\n*Model_Space"))
        assertTrue(dxf.contains("0\nSECTION\n2\nBLOCKS"))
        assertTrue(dxf.contains("0\nSECTION\n2\nOBJECTS"))
        assertTrue(dxf.contains("2\nMEMBERS"))
        assertTrue(dxf.endsWith("0\nENDSEC\n0\nEOF\n"))
    }

    @Test
    fun declaresDefaultLayerAndWellFormedLinetypes() {
        val dxf = FrameDxfBuilder.build(squareNodes, squareMembers, emptyList(), emptyList(), emptyList())
        val tags = tags(dxf)

        // Layer "0" is mandatory in every DXF file.
        assertTrue(tags.any { it.first == 2 && it.second == "0" })

        // Every LTYPE entry must declare exactly as many dash items (73) as it emits (49).
        var i = 0
        var linetypes = 0
        while (i < tags.size) {
            if (tags[i] == (0 to "LTYPE") && tags.getOrNull(i + 1)?.first == 5) {
                var j = i + 1
                var dashCount = -1
                var emitted = 0
                while (j < tags.size && tags[j].first != 0) {
                    when (tags[j].first) {
                        73 -> dashCount = tags[j].second.toInt()
                        49 -> emitted++
                    }
                    j++
                }
                assertEquals("LTYPE dash count mismatch", dashCount, emitted)
                linetypes++
                i = j
            } else {
                i++
            }
        }
        assertEquals("Expected three linetype definitions", 3, linetypes)
    }

    @Test
    fun assignsUniqueHandlesToEveryObjectAndEntity() {
        val dxf = FrameDxfBuilder.build(
            squareNodes,
            squareMembers,
            listOf(NodalLoad(nodeId = 2, fx = 10.0, fy = -20.0, loadCase = "LL")),
            listOf(MemberLoad(memberId = 1, loadType = MemberLoadType.UDL, value = 12.5)),
            listOf(SlabPanel(id = 1, nodeIds = listOf(1, 2, 3, 4)))
        )
        val tags = tags(dxf)
        val handles = tags.filter { it.first == 5 }.map { it.second }

        assertTrue("Handles must not be empty", handles.isNotEmpty())
        assertTrue("Handles must be unique", handles.size == handles.distinct().size)
        assertTrue("Handles must be hexadecimal", handles.all { it.matches(Regex("[0-9A-F]+")) })

        // All model entities are owned by the *Model_Space block record.
        val modelSpaceHandle = handleOf(tags, "BLOCK_RECORD", "*Model_Space")
        assertTrue(modelSpaceHandle.isNotEmpty())
        val entitiesSection = dxf.substringAfter("2\nENTITIES").substringBefore("0\nENDSEC")
        assertTrue(entitiesSection.contains("330\n$modelSpaceHandle"))
    }

    @Test
    fun everyEntityCarriesRequiredSubclassMarker() {
        val dxf = FrameDxfBuilder.build(
            squareNodes,
            squareMembers,
            emptyList(),
            emptyList(),
            listOf(SlabPanel(id = 1, nodeIds = listOf(1, 2, 3, 4)))
        )

        assertTrue(dxf.contains("100\nAcDbLine"))
        assertTrue(dxf.contains("100\nAcDbCircle"))
        assertTrue(dxf.contains("100\nAcDbText"))
        assertTrue(dxf.contains("100\nAcDbPolyline"))
        assertTrue(dxf.contains("100\nAcDbEntity"))
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

        // LWPOLYLINE (DXF 2000+) with 4 corners and closed flag = 1 (no duplicated vertex)
        assertTrue(dxf.contains("8\nSLABS\n100\nAcDbPolyline\n90\n4\n70\n1"))
        // LWPOLYLINE embeds vertices directly, no separate VERTEX entities
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
    fun hiddenLayersOmitTheirEntitiesButKeepLayerTable() {
        val slab = SlabPanel(id = 1, nodeIds = listOf(1, 2, 3, 4), thicknessMm = 200.0)
        val nodalLoad = NodalLoad(nodeId = 2, fx = 10.0, fy = -20.0, loadCase = "LL")
        val memberLoad = MemberLoad(
            memberId = 1,
            loadType = MemberLoadType.UDL,
            value = 12.5,
            loadCase = "DL"
        )
        val dxf = FrameDxfBuilder.build(
            squareNodes,
            squareMembers,
            listOf(nodalLoad),
            listOf(memberLoad),
            listOf(slab),
            layers = ModelerLayerVisibility(
                grid = false,
                joints = false,
                members = false,
                supports = false,
                loads = false,
                slabs = false
            )
        )
        val entities = dxf.substringAfter("2\nENTITIES").substringBefore("0\nENDSEC")

        assertFalse(entities.contains("8\nGRID"))
        assertFalse(entities.contains("8\nSLABS"))
        assertFalse(entities.contains("8\nMEMBERS"))
        assertFalse(entities.contains("8\nSUPPORTS"))
        assertFalse(entities.contains("8\nJOINTS"))
        assertFalse(entities.contains("8\nLOAD_LL"))
        assertTrue(dxf.contains("2\nMEMBERS"))
        assertTrue(dxf.contains("2\nGRID"))
    }

    @Test
    fun emptyModelStillProducesValidFile() {
        val dxf = FrameDxfBuilder.build(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        assertTrue(dxf.startsWith("0\nSECTION\n2\nHEADER"))
        assertTrue(dxf.contains("0\nSECTION\n2\nENTITIES\n0\nENDSEC"))
        assertTrue(dxf.contains("0\nSECTION\n2\nOBJECTS"))
        assertTrue(dxf.endsWith("0\nEOF\n"))
    }

    private fun tags(dxf: String): List<Pair<Int, String>> {
        val lines = dxf.trimEnd('\n').split('\n')
        assertEquals("DXF group codes must pair with a value", 0, lines.size % 2)
        return (lines.indices step 2).map { index -> lines[index].toInt() to lines[index + 1] }
    }

    /** Returns the group-5 handle of the first record of [type], optionally matching a group-2 name. */
    private fun handleOf(tags: List<Pair<Int, String>>, type: String, name: String? = null): String {
        var index = 0
        while (index < tags.size) {
            if (tags[index] == (0 to type)) {
                var cursor = index + 1
                var handle = ""
                var recordName: String? = null
                while (cursor < tags.size && tags[cursor].first != 0) {
                    when (tags[cursor].first) {
                        5 -> handle = tags[cursor].second
                        2 -> recordName = tags[cursor].second
                    }
                    cursor++
                }
                if (name == null || recordName == name) return handle
                index = cursor
            } else {
                index++
            }
        }
        return ""
    }
}
