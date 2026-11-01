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

class ModelerClipboardTest {

    private val nodes = listOf(
        FrameNode(1, 0.0, 0.0, SupportType.Pin),
        FrameNode(2, 4.0, 0.0),
        FrameNode(3, 4.0, 3.0),
        FrameNode(4, 0.0, 3.0),
        FrameNode(5, 10.0, 0.0)
    )

    private val members = listOf(
        FrameMember(1, 1, 2),
        FrameMember(2, 2, 3),
        FrameMember(3, 3, 4),
        FrameMember(4, 4, 1),
        FrameMember(5, 2, 5)
    )

    private val slab = SlabPanel(id = 1, nodeIds = listOf(1, 2, 3, 4), thicknessMm = 200.0)

    @Test
    fun selectingMemberAlsoCapturesItsEndJoints() {
        val content = ModelerClipboard.capture(
            nodes = nodes,
            members = members,
            nodalLoads = emptyList(),
            memberLoads = emptyList(),
            slabs = emptyList(),
            selectedNodeIds = emptySet(),
            selectedMemberIds = setOf(1)
        )

        assertEquals(listOf(1, 2), content.nodes.map { it.id })
        assertEquals(listOf(1), content.members.map { it.id })
        assertFalse(content.isEmpty)
    }

    @Test
    fun copyingSlabCornersAlsoCopiesPerimeterMembers() {
        val content = ModelerClipboard.capture(
            nodes = nodes,
            members = members,
            nodalLoads = emptyList(),
            memberLoads = emptyList(),
            slabs = listOf(slab),
            selectedNodeIds = setOf(1, 2, 3, 4),
            selectedMemberIds = emptySet()
        )

        assertEquals(listOf(1, 2, 3, 4), content.members.map { it.id })
        assertEquals(listOf(1), content.slabs.map { it.id })
    }

    @Test
    fun emptySelectionProducesEmptyClipboard() {
        val content = ModelerClipboard.capture(
            nodes = nodes,
            members = members,
            nodalLoads = listOf(NodalLoad(nodeId = 1, fx = 5.0)),
            memberLoads = listOf(MemberLoad(memberId = 1, value = 4.0)),
            slabs = listOf(slab),
            selectedNodeIds = emptySet(),
            selectedMemberIds = emptySet()
        )

        assertTrue(content.isEmpty)
        assertTrue(content.nodes.isEmpty())
        assertTrue(content.members.isEmpty())
        assertTrue(content.slabs.isEmpty())
        assertTrue(content.nodalLoads.isEmpty())
        assertTrue(content.memberLoads.isEmpty())
    }

    @Test
    fun capturesSlabOnlyWhenAllItsJointsAreSelected() {
        val full = ModelerClipboard.capture(
            nodes = nodes,
            members = members,
            nodalLoads = emptyList(),
            memberLoads = emptyList(),
            slabs = listOf(slab),
            selectedNodeIds = setOf(1, 2, 3, 4),
            selectedMemberIds = emptySet()
        )
        assertEquals(listOf(1), full.slabs.map { it.id })

        val partial = ModelerClipboard.capture(
            nodes = nodes,
            members = members,
            nodalLoads = emptyList(),
            memberLoads = emptyList(),
            slabs = listOf(slab),
            selectedNodeIds = setOf(1, 2, 3),
            selectedMemberIds = emptySet()
        )
        assertTrue(partial.slabs.isEmpty())
    }

    @Test
    fun capturesLoadsOnlyForCopiedObjects() {
        val content = ModelerClipboard.capture(
            nodes = nodes,
            members = members,
            nodalLoads = listOf(
                NodalLoad(nodeId = 1, fx = 10.0),
                NodalLoad(nodeId = 5, fx = 99.0)
            ),
            memberLoads = listOf(
                MemberLoad(memberId = 1, loadType = MemberLoadType.UDL, value = 12.0),
                MemberLoad(memberId = 5, loadType = MemberLoadType.UDL, value = 77.0)
            ),
            slabs = emptyList(),
            selectedNodeIds = setOf(1, 2),
            selectedMemberIds = setOf(1)
        )

        assertEquals(listOf(10.0), content.nodalLoads.map { it.fx })
        assertEquals(listOf(12.0), content.memberLoads.map { it.value })
    }

    @Test
    fun pasteAllocatesFreshIdsAndOffsetsCoordinates() {
        val content = ModelerClipboard.capture(
            nodes = nodes,
            members = members,
            nodalLoads = emptyList(),
            memberLoads = emptyList(),
            slabs = emptyList(),
            selectedNodeIds = setOf(1, 2),
            selectedMemberIds = setOf(1)
        )

        val result = ModelerClipboard.paste(
            content = content,
            existingNodes = nodes,
            existingMembers = members,
            existingSlabs = emptyList(),
            offsetMeters = 1.0
        )

        assertEquals(listOf(6, 7), result.nodes.map { it.id })
        assertEquals(listOf(1.0, 5.0), result.nodes.map { it.x })
        assertEquals(listOf(1.0, 1.0), result.nodes.map { it.y })
        assertEquals(listOf(6), result.members.map { it.id })
        assertEquals(6, result.members.single().nodeI)
        assertEquals(7, result.members.single().nodeJ)
    }

    @Test
    fun pasteRemapsSlabsAndTheirJoints() {
        val content = ModelerClipboard.capture(
            nodes = nodes,
            members = members,
            nodalLoads = emptyList(),
            memberLoads = emptyList(),
            slabs = listOf(slab),
            selectedNodeIds = setOf(1, 2, 3, 4),
            selectedMemberIds = emptySet()
        )

        val result = ModelerClipboard.paste(
            content = content,
            existingNodes = nodes,
            existingMembers = members,
            existingSlabs = listOf(slab),
            offsetMeters = 2.0
        )

        val pastedSlab = result.slabs.single()
        assertEquals(2, pastedSlab.id)
        assertEquals(listOf(6, 7, 8, 9), pastedSlab.nodeIds)
        assertEquals(200.0, pastedSlab.thicknessMm, 1e-9)
        assertEquals(2.0, result.nodes.first().x, 1e-9)
    }

    @Test
    fun pasteRemapsLoadsToTheNewObjects() {
        val content = ModelerClipboard.capture(
            nodes = nodes,
            members = members,
            nodalLoads = listOf(NodalLoad(nodeId = 1, fx = 10.0)),
            memberLoads = listOf(MemberLoad(memberId = 1, value = 12.0)),
            slabs = emptyList(),
            selectedNodeIds = setOf(1, 2),
            selectedMemberIds = setOf(1)
        )

        val result = ModelerClipboard.paste(
            content = content,
            existingNodes = nodes,
            existingMembers = members,
            existingSlabs = emptyList(),
            offsetMeters = 1.0
        )

        assertEquals(6, result.nodalLoads.single().nodeId)
        assertEquals(6, result.memberLoads.single().memberId)
    }

    @Test
    fun repeatedPastesNeverCollide() {
        val content = ModelerClipboard.capture(
            nodes = nodes,
            members = members,
            nodalLoads = emptyList(),
            memberLoads = emptyList(),
            slabs = emptyList(),
            selectedNodeIds = setOf(1, 2),
            selectedMemberIds = setOf(1)
        )

        val first = ModelerClipboard.paste(
            content = content,
            existingNodes = nodes,
            existingMembers = members,
            existingSlabs = emptyList(),
            offsetMeters = 1.0
        )
        val second = ModelerClipboard.paste(
            content = content,
            existingNodes = nodes + first.nodes,
            existingMembers = members + first.members,
            existingSlabs = emptyList(),
            offsetMeters = 2.0
        )

        assertEquals(listOf(6, 7), first.nodes.map { it.id })
        assertEquals(listOf(8, 9), second.nodes.map { it.id })
        assertEquals(listOf(6), first.members.map { it.id })
        assertEquals(listOf(7), second.members.map { it.id })
        assertEquals(2.0, second.nodes.first().x, 1e-9)
    }

    @Test
    fun pastingEmptyContentIsANoOp() {
        val result = ModelerClipboard.paste(
            content = ModelerClipboard.Content(),
            existingNodes = nodes,
            existingMembers = members,
            existingSlabs = listOf(slab),
            offsetMeters = 1.0
        )

        assertTrue(result.isEmpty)
    }
}
