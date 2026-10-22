package com.civileg.app.ui.compose.components.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.editor.AddMemberLoadCommand
import com.civileg.app.domain.editor.AddNodalLoadCommand
import com.civileg.app.domain.editor.ChangeSupportCommand
import com.civileg.app.domain.editor.UndoManager
import com.civileg.app.domain.entities.FrameAnalysisResult
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SupportType
import com.civileg.app.ui.compose.components.editor.GridRenderer.drawGrid
import com.civileg.app.viewmodel.DiagramType
import com.civileg.app.viewmodel.FrameAnalysisViewModel

@Composable
fun InteractiveFrameCanvas(
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    nodalLoads: List<NodalLoad>,
    memberLoads: List<MemberLoad>,
    result: FrameAnalysisResult?,
    diagramType: DiagramType,
    viewModel: FrameAnalysisViewModel,
    modifier: Modifier = Modifier
) {
    val editorState = remember { EditorState() }
    val cameraState = remember { CameraState() }
    val undoManager = remember { UndoManager() }

    var activeSupportDialogNodeId by remember { mutableStateOf<Int?>(null) }
    var activeNodalLoadDialogNodeId by remember { mutableStateOf<Int?>(null) }
    var activeMemberLoadDialogMemberId by remember { mutableStateOf<Int?>(null) }

    var cursorWorldX by remember { mutableDoubleStateOf(0.0) }
    var cursorWorldY by remember { mutableDoubleStateOf(0.0) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val canvasW = with(density) { this@BoxWithConstraints.maxWidth.toPx() }
        val canvasH = with(density) { this@BoxWithConstraints.maxHeight.toPx() }

        val basePixelsPerMeter = remember(nodes, canvasW, canvasH) {
            if (nodes.isNotEmpty()) {
                CoordinateMapper.computePixelsPerMeter(
                    nodes.map { it.x },
                    nodes.map { it.y },
                    canvasW,
                    canvasH
                )
            } else 100f
        }

        val activeGridSpacing = CoordinateMapper.computeActiveGridSpacing(
            basePixelsPerMeter, cameraState.scale
        )

        val mapper = remember(
            basePixelsPerMeter, canvasW, canvasH,
            cameraState.offsetX, cameraState.offsetY, cameraState.scale,
            activeGridSpacing
        ) {
            CoordinateMapper(
                basePixelsPerMeter, canvasW, canvasH,
                cameraState.offsetX, cameraState.offsetY, cameraState.scale,
                activeGridSpacing
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cameraState) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        cameraState.zoom(zoom, centroid.x, centroid.y)
                        cameraState.pan(pan.x, pan.y)
                    }
                }
                .pointerInput(editorState.activeTool, mapper, nodes, members) {
                    handleToolGestures(
                        editorState = editorState,
                        mapper = mapper,
                        nodes = nodes,
                        members = members,
                        viewModel = viewModel,
                        undoManager = undoManager,
                        cameraState = cameraState,
                        onOpenNodalLoadDialog = { nodeId -> activeNodalLoadDialogNodeId = nodeId },
                        onOpenMemberLoadDialog = { memberId -> activeMemberLoadDialogMemberId = memberId },
                        onOpenSupportPicker = { nodeId -> activeSupportDialogNodeId = nodeId }
                    )
                }
        ) {
            // 1. Background
            drawRect(Color.White)

            // 2. Dynamic Grid
            drawGrid(mapper)

            // 3. Frame Members
            drawMembers(members, nodes, mapper, editorState.selectedMemberId)

            // 4. Frame Supports
            drawSupports(nodes, mapper)

            // 5. Frame Nodes
            drawNodes(nodes, mapper, editorState.selectedNodeId)

            // 6. Preview Line during Member Drawing
            editorState.dragStartNodeId?.let { startId ->
                val startNode = nodes.find { it.id == startId }
                if (startNode != null) {
                    val startScreen = mapper.toScreen(startNode.x, startNode.y)
                    drawCircle(Color(0xFF1565C0), radius = 12f, center = startScreen)
                }
            }
        }

        // Toolbar
        EditorToolbar(
            editorState = editorState,
            undoManager = undoManager,
            cameraState = cameraState,
            nodes = nodes,
            pixelsPerMeter = basePixelsPerMeter,
            canvasW = canvasW,
            canvasH = canvasH,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Status bar
        StatusBar(
            cursorWorldX = cursorWorldX,
            cursorWorldY = cursorWorldY,
            mapper = mapper,
            editorState = editorState,
            nodesCount = nodes.size,
            membersCount = members.size,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Property panel
        if (editorState.showPropertyPanel) {
            PropertyPanel(
                selectedNodeId = editorState.selectedNodeId,
                selectedMemberId = editorState.selectedMemberId,
                nodes = nodes,
                members = members,
                viewModel = viewModel,
                editorState = editorState,
                undoManager = undoManager,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            )
        }

        // Support Picker Dialog
        activeSupportDialogNodeId?.let { nodeId ->
            val node = nodes.find { it.id == nodeId }
            if (node != null) {
                SupportPicker(
                    nodeId = nodeId,
                    currentSupport = node.support,
                    onSupportSelected = { newSupport ->
                        val cmd = ChangeSupportCommand(viewModel, nodeId, node.x, node.y, node.support, newSupport)
                        undoManager.execute(cmd)
                        activeSupportDialogNodeId = null
                    },
                    onDismiss = { activeSupportDialogNodeId = null }
                )
            }
        }

        // Nodal Load Dialog
        activeNodalLoadDialogNodeId?.let { nodeId ->
            NodalLoadDialog(
                nodeId = nodeId,
                onConfirm = { load ->
                    val cmd = AddNodalLoadCommand(viewModel, load)
                    undoManager.execute(cmd)
                    activeNodalLoadDialogNodeId = null
                },
                onDismiss = { activeNodalLoadDialogNodeId = null }
            )
        }

        // Member Load Dialog
        activeMemberLoadDialogMemberId?.let { memberId ->
            MemberLoadDialog(
                memberId = memberId,
                onConfirm = { type, value, pos, loadCase ->
                    val load = MemberLoad(memberId, type, value, pos, loadCase)
                    val cmd = AddMemberLoadCommand(viewModel, load)
                    undoManager.execute(cmd)
                    activeMemberLoadDialogMemberId = null
                },
                onDismiss = { activeMemberLoadDialogMemberId = null }
            )
        }
    }
}

private fun DrawScope.drawMembers(
    members: List<FrameMember>,
    nodes: List<FrameNode>,
    mapper: CoordinateMapper,
    selectedMemberId: Int?
) {
    for (m in members) {
        val ni = nodes.find { it.id == m.nodeI } ?: continue
        val nj = nodes.find { it.id == m.nodeJ } ?: continue
        val pI = mapper.toScreen(ni.x, ni.y)
        val pJ = mapper.toScreen(nj.x, nj.y)
        val isSelected = m.id == selectedMemberId

        val color = if (isSelected) Color(0xFFFF9800) else Color(0xFF212121)
        val strokeWidth = if (isSelected) 6f else 4f

        drawLine(color = color, start = pI, end = pJ, strokeWidth = strokeWidth)
    }
}

private fun DrawScope.drawNodes(
    nodes: List<FrameNode>,
    mapper: CoordinateMapper,
    selectedNodeId: Int?
) {
    for (n in nodes) {
        val screenPos = mapper.toScreen(n.x, n.y)
        val isSelected = n.id == selectedNodeId
        val radius = if (isSelected) 10f else 7f
        val color = if (isSelected) Color(0xFFFF9800) else Color(0xFF1565C0)

        drawCircle(color = color, radius = radius, center = screenPos)
        drawCircle(color = Color.White, radius = radius * 0.5f, center = screenPos)
    }
}

private fun DrawScope.drawSupports(
    nodes: List<FrameNode>,
    mapper: CoordinateMapper
) {
    for (n in nodes) {
        if (n.support == SupportType.Free) continue
        val pos = mapper.toScreen(n.x, n.y)
        val size = 20f
        when (n.support) {
            SupportType.Pin -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(pos.x, pos.y)
                    lineTo(pos.x - size / 2, pos.y + size)
                    lineTo(pos.x + size / 2, pos.y + size)
                    close()
                }
                drawPath(path, Color(0xFF388E3C))
            }
            SupportType.Fixed -> {
                drawLine(
                    color = Color(0xFFD32F2F),
                    start = Offset(pos.x - size, pos.y),
                    end = Offset(pos.x + size, pos.y),
                    strokeWidth = 6f
                )
            }
            SupportType.Roller -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(pos.x, pos.y)
                    lineTo(pos.x - size / 2, pos.y + size * 0.7f)
                    lineTo(pos.x + size / 2, pos.y + size * 0.7f)
                    close()
                }
                drawPath(path, Color(0xFF1976D2))
                drawCircle(Color(0xFF1976D2), radius = size * 0.2f, center = Offset(pos.x, pos.y + size * 0.9f))
            }
            else -> {
                drawCircle(Color(0xFF7B1FA2), radius = size * 0.5f, center = pos)
            }
        }
    }
}
