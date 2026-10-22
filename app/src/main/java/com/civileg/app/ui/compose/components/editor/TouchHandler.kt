package com.civileg.app.ui.compose.components.editor

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import com.civileg.app.domain.editor.AddMemberCommand
import com.civileg.app.domain.editor.AddNodeCommand
import com.civileg.app.domain.editor.DeleteMemberCommand
import com.civileg.app.domain.editor.DeleteNodeCommand
import com.civileg.app.domain.editor.EditorTool
import com.civileg.app.domain.editor.UndoManager
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.SupportType
import com.civileg.app.viewmodel.FrameAnalysisViewModel
import kotlin.math.hypot

/** Helper to find closest node to screen coordinate within hitRadius px */
fun findNodeAtScreen(
    nodes: List<FrameNode>,
    screenPos: Offset,
    mapper: CoordinateMapper,
    hitRadius: Float = 30f
): FrameNode? {
    var closest: FrameNode? = null
    var minDist = hitRadius
    for (node in nodes) {
        val nodeScreen = mapper.toScreen(node.x, node.y)
        val dist = hypot(screenPos.x - nodeScreen.x, screenPos.y - nodeScreen.y)
        if (dist < minDist) {
            minDist = dist
            closest = node
        }
    }
    return closest
}

/** Helper to find closest member to screen coordinate within hitRadius px */
fun findMemberAtScreen(
    members: List<FrameMember>,
    nodes: List<FrameNode>,
    screenPos: Offset,
    mapper: CoordinateMapper,
    hitRadius: Float = 24f
): FrameMember? {
    var closest: FrameMember? = null
    var minDist = hitRadius
    for (m in members) {
        val ni = nodes.find { it.id == m.nodeI } ?: continue
        val nj = nodes.find { it.id == m.nodeJ } ?: continue
        val pI = mapper.toScreen(ni.x, ni.y)
        val pJ = mapper.toScreen(nj.x, nj.y)
        val dist = pointToSegmentDist(screenPos, pI, pJ)
        if (dist < minDist) {
            minDist = dist
            closest = m
        }
    }
    return closest
}

private fun pointToSegmentDist(p: Offset, a: Offset, b: Offset): Float {
    val l2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
    if (l2 < 1e-6f) return hypot(p.x - a.x, p.y - a.y)
    var t = ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2
    t = t.coerceIn(0f, 1f)
    val projX = a.x + t * (b.x - a.x)
    val projY = a.y + t * (b.y - a.y)
    return hypot(p.x - projX, p.y - projY)
}

suspend fun PointerInputScope.handleToolGestures(
    editorState: EditorState,
    mapper: CoordinateMapper,
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    viewModel: FrameAnalysisViewModel,
    undoManager: UndoManager,
    cameraState: CameraState,
    onOpenNodalLoadDialog: (Int) -> Unit = {},
    onOpenMemberLoadDialog: (Int) -> Unit = {},
    onOpenSupportPicker: (Int) -> Unit = {}
) {
    when (editorState.activeTool) {
        EditorTool.Select -> {
            detectTapGestures { offset ->
                val hitNode = findNodeAtScreen(nodes, offset, mapper)
                if (hitNode != null) {
                    editorState.selectedNodeId = hitNode.id
                    editorState.selectedMemberId = null
                    editorState.showPropertyPanel = true
                    return@detectTapGestures
                }
                val hitMember = findMemberAtScreen(members, nodes, offset, mapper)
                if (hitMember != null) {
                    editorState.selectedMemberId = hitMember.id
                    editorState.selectedNodeId = null
                    editorState.showPropertyPanel = true
                    return@detectTapGestures
                }
                editorState.clearSelection()
            }
        }
        EditorTool.AddNode -> {
            detectTapGestures { offset ->
                val (worldX, worldY) = mapper.toWorld(offset.x, offset.y)
                val (snapX, snapY) = if (editorState.gridSnapEnabled) {
                    mapper.snapToGrid(worldX, worldY)
                } else {
                    Pair(worldX, worldY)
                }
                val cmd = AddNodeCommand(viewModel, snapX, snapY, SupportType.Free)
                undoManager.execute(cmd)
            }
        }
        EditorTool.DrawMember -> {
            detectTapGestures { offset ->
                val hitNode = findNodeAtScreen(nodes, offset, mapper)
                if (hitNode != null) {
                    if (editorState.dragStartNodeId == null) {
                        editorState.dragStartNodeId = hitNode.id
                    } else if (editorState.dragStartNodeId != hitNode.id) {
                        val cmd = AddMemberCommand(
                            viewModel,
                            editorState.dragStartNodeId!!,
                            hitNode.id,
                            editorState.newMemberMaterialType,
                            editorState.newMemberType
                        )
                        undoManager.execute(cmd)
                        editorState.dragStartNodeId = null
                    }
                } else {
                    val (worldX, worldY) = mapper.toWorld(offset.x, offset.y)
                    val (snapX, snapY) = if (editorState.gridSnapEnabled) {
                        mapper.snapToGrid(worldX, worldY)
                    } else {
                        Pair(worldX, worldY)
                    }
                    val addNodeCmd = AddNodeCommand(viewModel, snapX, snapY, SupportType.Free)
                    undoManager.execute(addNodeCmd)
                    val newNodeId = addNodeCmd.resultingNodeId
                    if (editorState.dragStartNodeId != null && newNodeId >= 0 && editorState.dragStartNodeId != newNodeId) {
                        val addMemCmd = AddMemberCommand(
                            viewModel,
                            editorState.dragStartNodeId!!,
                            newNodeId,
                            editorState.newMemberMaterialType,
                            editorState.newMemberType
                        )
                        undoManager.execute(addMemCmd)
                    }
                    editorState.dragStartNodeId = newNodeId.takeIf { it >= 0 }
                }
            }
        }
        EditorTool.AddSupport -> {
            detectTapGestures { offset ->
                val hitNode = findNodeAtScreen(nodes, offset, mapper)
                if (hitNode != null) {
                    onOpenSupportPicker(hitNode.id)
                }
            }
        }
        EditorTool.AddNodalLoad -> {
            detectTapGestures { offset ->
                val hitNode = findNodeAtScreen(nodes, offset, mapper)
                if (hitNode != null) {
                    onOpenNodalLoadDialog(hitNode.id)
                }
            }
        }
        EditorTool.AddMemberLoad -> {
            detectTapGestures { offset ->
                val hitMember = findMemberAtScreen(members, nodes, offset, mapper)
                if (hitMember != null) {
                    onOpenMemberLoadDialog(hitMember.id)
                }
            }
        }
        EditorTool.Delete -> {
            detectTapGestures { offset ->
                val hitNode = findNodeAtScreen(nodes, offset, mapper)
                if (hitNode != null) {
                    val connected = members.filter { it.nodeI == hitNode.id || it.nodeJ == hitNode.id }
                    val cmd = DeleteNodeCommand(viewModel, hitNode, connected)
                    undoManager.execute(cmd)
                    editorState.clearSelection()
                    return@detectTapGestures
                }
                val hitMember = findMemberAtScreen(members, nodes, offset, mapper)
                if (hitMember != null) {
                    val cmd = DeleteMemberCommand(viewModel, hitMember)
                    undoManager.execute(cmd)
                    editorState.clearSelection()
                }
            }
        }
        EditorTool.Pan -> {
            detectDragGestures { change, dragAmount ->
                change.consume()
                cameraState.pan(dragAmount.x, dragAmount.y)
            }
        }
    }
}
