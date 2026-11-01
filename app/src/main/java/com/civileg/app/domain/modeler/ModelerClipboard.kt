package com.civileg.app.domain.modeler

import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SlabPanel

/**
 * Pure copy/paste engine for the frame modeler.
 *
 * Keeping this logic outside the ViewModel makes the ID remapping and the
 * selection rules unit-testable without Android dependencies.
 *
 * Selection rules:
 * - Selecting a member also captures its end joints, otherwise the pasted
 *   member could not be rebuilt.
 * - Members whose two end joints are both captured are included, so a slab
 *   copied through its four corners keeps the members that support its edges.
 * - Slabs are captured only when every one of their joints is part of the
 *   copied set, so a partially selected panel is never pasted as a broken slab.
 * - Loads follow their node/member: only loads attached to copied objects move.
 */
object ModelerClipboard {

    data class Content(
        val nodes: List<FrameNode> = emptyList(),
        val members: List<FrameMember> = emptyList(),
        val nodalLoads: List<NodalLoad> = emptyList(),
        val memberLoads: List<MemberLoad> = emptyList(),
        val slabs: List<SlabPanel> = emptyList()
    ) {
        val isEmpty: Boolean
            get() = nodes.isEmpty() && members.isEmpty() && slabs.isEmpty()
    }

    data class PasteResult(
        val nodes: List<FrameNode> = emptyList(),
        val members: List<FrameMember> = emptyList(),
        val nodalLoads: List<NodalLoad> = emptyList(),
        val memberLoads: List<MemberLoad> = emptyList(),
        val slabs: List<SlabPanel> = emptyList()
    ) {
        val isEmpty: Boolean
            get() = nodes.isEmpty() && members.isEmpty() && slabs.isEmpty()
    }

    fun capture(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        slabs: List<SlabPanel>,
        selectedNodeIds: Set<Int>,
        selectedMemberIds: Set<Int>
    ): Content {
        val selectedMembers = members.filter { it.id in selectedMemberIds }
        val capturedNodeIds = selectedNodeIds +
            selectedMembers.flatMap { listOf(it.nodeI, it.nodeJ) }
        if (capturedNodeIds.isEmpty()) return Content()

        val capturedMembers = members.filter {
            it.nodeI in capturedNodeIds && it.nodeJ in capturedNodeIds
        }
        val capturedMemberIds = capturedMembers.map { it.id }.toSet()

        return Content(
            nodes = nodes.filter { it.id in capturedNodeIds },
            members = capturedMembers,
            nodalLoads = nodalLoads.filter { it.nodeId in capturedNodeIds },
            memberLoads = memberLoads.filter { it.memberId in capturedMemberIds },
            slabs = slabs.filter { panel -> panel.nodeIds.all { it in capturedNodeIds } }
        )
    }

    /**
     * Builds the new objects for a paste, offset by [offsetMeters] in both axes.
     * All identifiers are allocated above the current model maxima so repeated
     * pastes can never collide with existing geometry.
     */
    fun paste(
        content: Content,
        existingNodes: List<FrameNode>,
        existingMembers: List<FrameMember>,
        existingSlabs: List<SlabPanel>,
        offsetMeters: Double
    ): PasteResult {
        if (content.isEmpty) return PasteResult()

        val nodeIdMap = LinkedHashMap<Int, Int>()
        var nextNodeId = (existingNodes.maxOfOrNull { it.id } ?: 0) + 1
        val newNodes = content.nodes.map { node ->
            val id = nextNodeId++
            nodeIdMap[node.id] = id
            node.copy(id = id, x = node.x + offsetMeters, y = node.y + offsetMeters)
        }

        val memberIdMap = LinkedHashMap<Int, Int>()
        var nextMemberId = (existingMembers.maxOfOrNull { it.id } ?: 0) + 1
        val newMembers = content.members.mapNotNull { member ->
            val nodeI = nodeIdMap[member.nodeI] ?: return@mapNotNull null
            val nodeJ = nodeIdMap[member.nodeJ] ?: return@mapNotNull null
            val id = nextMemberId++
            memberIdMap[member.id] = id
            member.copy(id = id, nodeI = nodeI, nodeJ = nodeJ)
        }

        val newNodalLoads = content.nodalLoads.mapNotNull { load ->
            nodeIdMap[load.nodeId]?.let { load.copy(nodeId = it) }
        }

        val newMemberLoads = content.memberLoads.mapNotNull { load ->
            memberIdMap[load.memberId]?.let { load.copy(memberId = it) }
        }

        var nextSlabId = (existingSlabs.maxOfOrNull { it.id } ?: 0) + 1
        val newSlabs = content.slabs.mapNotNull { panel ->
            val mapped = panel.nodeIds.map { nodeIdMap[it] }
            if (mapped.any { it == null }) return@mapNotNull null
            panel.copy(id = nextSlabId++, nodeIds = mapped.filterNotNull())
        }

        return PasteResult(
            nodes = newNodes,
            members = newMembers,
            nodalLoads = newNodalLoads,
            memberLoads = newMemberLoads,
            slabs = newSlabs
        )
    }
}
