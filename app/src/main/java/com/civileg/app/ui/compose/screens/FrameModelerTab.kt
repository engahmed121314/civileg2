package com.civileg.app.ui.compose.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider

import com.civileg.app.R
import com.civileg.app.domain.entities.ConcreteSectionProps
import com.civileg.app.domain.entities.FrameMaterialType
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.MemberLoadType
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SlabDistribution
import com.civileg.app.domain.entities.SlabPanel
import com.civileg.app.domain.entities.SupportType
import com.civileg.app.domain.entities.orderQuadJoints
import com.civileg.app.utils.FrameDxfExporter
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.utils.SteelTables
import com.civileg.app.viewmodel.FrameAnalysisViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private fun t(ar: String, en: String): String = if (LocaleHelper.isArabic()) ar else en

private data class GridOption(val labelAr: String, val labelEn: String, val mm: Int)

private val gridOptions = listOf(
    GridOption("1 م", "1 m", 1000),
    GridOption("100 مم", "100 mm", 100),
    GridOption("10 مم", "10 mm", 10),
    GridOption("1 مم", "1 mm", 1)
)

private val snapOptions = listOf(
    GridOption("إيقاف", "Off", 0),
    GridOption("1 م", "1 m", 1000),
    GridOption("100 مم", "100 mm", 100),
    GridOption("10 مم", "10 mm", 10),
    GridOption("1 مم", "1 mm", 1)
)

@Composable
fun FrameModelerTab(
    modifier: Modifier = Modifier,
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    nodalLoads: List<NodalLoad>,
    memberLoads: List<MemberLoad>,
    slabs: List<SlabPanel>,

    externalFitRequest: Int = 0,
    viewModel: FrameAnalysisViewModel
) {
    var tool by rememberSaveable { mutableStateOf(ModelerTool.Select) }
    var supportType by rememberSaveable { mutableStateOf(SupportType.Pin) }
    var gridMm by rememberSaveable { mutableIntStateOf(1000) }
    var snapMm by rememberSaveable { mutableIntStateOf(100) }
    var pendingStart by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingSlabNodes by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var memberDragPreview by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var selectedNodeId by rememberSaveable { mutableStateOf<Int?>(null) }
    var fitRequest by remember { mutableIntStateOf(0) }
    var viewport by remember { mutableStateOf(ModelerViewport()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(viewport, canvasSize, gridMm) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            viewModel.updateModelerSceneView(
                scale = viewport.scale,
                panX = viewport.panX,
                panY = viewport.panY,
                width = canvasSize.width,
                height = canvasSize.height,
                gridMm = gridMm
            )
        }
    }

    LaunchedEffect(externalFitRequest) {
        if (externalFitRequest > 0) fitRequest++
    }

    var loadNodeTarget by remember { mutableStateOf<Int?>(null) }
    var loadMemberTarget by remember { mutableStateOf<Int?>(null) }
    var editingNodalLoad by remember { mutableStateOf<Pair<Int, NodalLoad>?>(null) }
    var editingMemberLoad by remember { mutableStateOf<Pair<Int, MemberLoad>?>(null) }
    var hingeTarget by remember { mutableStateOf<FrameMember?>(null) }
    var editingMemberProps by remember { mutableStateOf<FrameMember?>(null) }
    var slabDraft by remember { mutableStateOf<List<Int>?>(null) }
    var editSlabTarget by remember { mutableStateOf<SlabPanel?>(null) }

    val selectedMemberId by viewModel.selectedMemberId.observeAsState()
    val historyVersion by viewModel.historyState.observeAsState(0)
    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()
    var isDxfExporting by remember { mutableStateOf(false) }
    val exportDxfLabel = stringResource(R.string.frame_export_dxf)
    val shareDxfLabel = stringResource(R.string.frame_share_dxf)
    val dxfSubject = stringResource(R.string.frame_dxf_subject)
    val dxfErrorFormat = stringResource(R.string.frame_dxf_error)

    val hint = when (tool) {
        ModelerTool.Select -> t("اضغط على عقدة أو عضو أو بلاطة للتحديد", "Tap a joint, member, or slab to select")
        ModelerTool.Pan -> t("اسحب بإصبع أو إصبعين للتحريك، وقرّب بإصبعين للتكبير", "Drag to pan and pinch to zoom")
        ModelerTool.Move -> t(
            "اسحب عقدة بإصبع، وبإصبعين للتحريك/التكبير",
            "Drag a joint, or use two fingers to pan/zoom"
        )
        ModelerTool.Joint -> t("اضغط على الشبكة لإضافة عقدة", "Tap the grid to add a joint")
        ModelerTool.Member -> if (pendingStart == null) {
            t("اختر عقدة البداية، أو اسحب منها؛ بإصبعين للتحريك/التكبير", "Pick or drag from the start joint; two fingers pan/zoom")
        } else {
            t("اختر عقدة النهاية", "Pick the end joint")
        }
        ModelerTool.Support -> t("اختر العقدة ثم نوع الاستناد بالأعلى", "Pick a joint, then choose the support type above")
        ModelerTool.Hinge -> t("اضغط على العضو لضبط المفاصل", "Tap a member to edit its end releases")
        ModelerTool.NodalLoad -> t("اضغط على عقدة لإضافة حمل", "Tap a joint to add a nodal load")
        ModelerTool.MemberLoad -> t("اضغط على عضو لإضافة حمل", "Tap a member to add a member load")
        ModelerTool.Slab -> t("اختر 4 عقد للبلاطة بالترتيب", "Pick 4 joints for the slab")
        ModelerTool.Erase -> t("اضغط على عنصر لحذفه", "Tap an element to delete it")
    }

    fun zoomBy(factor: Float) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return
        val cx = canvasSize.width / 2f
        val cy = canvasSize.height / 2f
        val newScale = (viewport.scale * factor).coerceIn(2f, 30000f)
        val wx = (cx - viewport.panX) / viewport.scale
        val wy = (viewport.panY - cy) / viewport.scale
        viewport = ModelerViewport(
            scale = newScale,
            panX = cx - wx * newScale,
            panY = cy + wy * newScale
        )
    }

    fun handleTap(tap: ModelerTap) {
        when (tool) {
            ModelerTool.Pan -> {
                // Pan mode: tap does not change selection or add elements
            }
            ModelerTool.Select -> {
                if (tap.nodeId != null) {
                    selectedNodeId = tap.nodeId
                    viewModel.setSelectedMember(null)
                } else if (tap.memberId != null) {
                    selectedNodeId = null
                    viewModel.setSelectedMember(tap.memberId)
                } else if (tap.panelId != null) {
                    editSlabTarget = slabs.find { it.id == tap.panelId }
                } else {
                    selectedNodeId = null
                    viewModel.setSelectedMember(null)
                }
            }
            ModelerTool.Move -> {
                if (tap.nodeId != null) {
                    selectedNodeId = tap.nodeId
                    viewModel.setSelectedMember(null)
                }
            }
            ModelerTool.Joint -> {
                if (tap.nodeId == null) {
                    val id = viewModel.addNode(tap.worldX, tap.worldY)
                    selectedNodeId = id
                } else {
                    selectedNodeId = tap.nodeId
                }
                viewModel.setSelectedMember(null)
            }
            ModelerTool.Member -> {
                val nodeId = tap.nodeId ?: return
                val start = pendingStart
                if (start == null) {
                    pendingStart = nodeId
                } else {
                    if (start != nodeId) {
                        val a = nodes.find { it.id == start }
                        val b = nodes.find { it.id == nodeId }
                        val type = if (a != null && b != null && abs(a.x - b.x) < 1e-6) {
                            FrameMemberType.Column
                        } else {
                            FrameMemberType.Beam
                        }
                        val id = viewModel.addMemberBetween(start, nodeId, type)
                        id?.let {
                            selectedNodeId = null
                            viewModel.setSelectedMember(it)
                        }
                    }
                    pendingStart = null
                }
            }
            ModelerTool.Support -> tap.nodeId?.let {
                viewModel.setNodeSupport(it, supportType)
                selectedNodeId = it
            }
            ModelerTool.Hinge -> tap.memberId?.let { id ->
                hingeTarget = members.find { it.id == id }
            }
            ModelerTool.NodalLoad -> tap.nodeId?.let { loadNodeTarget = it }
            ModelerTool.MemberLoad -> tap.memberId?.let { loadMemberTarget = it }
            ModelerTool.Slab -> {
                val id = tap.nodeId ?: return
                val current = pendingSlabNodes
                val next = if (current.contains(id)) {
                    current - id
                } else {
                    (current + id).distinct()
                }
                if (next.size == 4) {
                    val ordered = orderQuadJoints(next, nodes)
                    slabDraft = ordered
                    pendingSlabNodes = ordered
                } else {
                    pendingSlabNodes = next
                }
            }
            ModelerTool.Erase -> {
                when {
                    tap.nodeId != null -> viewModel.removeNode(tap.nodeId)
                    tap.memberId != null -> viewModel.removeMember(tap.memberId)
                    tap.panelId != null -> viewModel.removeSlabPanel(tap.panelId)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(color = Color(0xFFE3F2FD)) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = historyVersion >= 0 && viewModel.canUndo
                    ) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = Color(0xFF1565C0)) }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = historyVersion >= 0 && viewModel.canRedo
                    ) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = Color(0xFF1565C0)) }
                    IconButton(onClick = { fitRequest++ }) {
                        Icon(Icons.Default.FitScreen, contentDescription = "Fit", tint = Color(0xFF1565C0))
                    }
                    IconButton(
                        enabled = nodes.isNotEmpty() && !isDxfExporting,
                        onClick = {
                            isDxfExporting = true
                            exportScope.launch(Dispatchers.IO) {
                                try {
                                    val file = FrameDxfExporter.generateFrameDxf(
                                        context = context,
                                        nodes = nodes,
                                        members = members,
                                        nodalLoads = nodalLoads,
                                        memberLoads = memberLoads,
                                        slabs = slabs
                                    )
                                    withContext(Dispatchers.Main) {
                                        try {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.provider",
                                                file
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/octet-stream"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_SUBJECT, dxfSubject)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, shareDxfLabel))
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                dxfErrorFormat.format(e.message ?: ""),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        isDxfExporting = false
                                    }
                                } catch (e: Throwable) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            dxfErrorFormat.format(e.message ?: ""),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        isDxfExporting = false
                                    }
                                }
                            }
                        }
                    ) {
                        if (isDxfExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF1565C0)
                            )
                        } else {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = exportDxfLabel,
                                tint = Color(0xFF1565C0)
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(onClick = { zoomBy(1.35f) }, modifier = Modifier.height(34.dp)) {
                        Text("+", fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(onClick = { zoomBy(1f / 1.35f) }, modifier = Modifier.height(34.dp)) {
                        Text("−", fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    ToolChip(t("تحديد", "Select"), ModelerTool.Select, tool) { tool = it; pendingStart = null }
                    ToolChip(t("تحريك العرض", "Pan"), ModelerTool.Pan, tool) { tool = it; pendingStart = null }
                    ToolChip(t("تحريك", "Move"), ModelerTool.Move, tool) { tool = it; pendingStart = null }
                    ToolChip(t("عقدة", "Joint"), ModelerTool.Joint, tool) { tool = it; pendingStart = null }
                    ToolChip(t("عضو", "Member"), ModelerTool.Member, tool) { tool = it }
                    ToolChip(t("استناد", "Support"), ModelerTool.Support, tool) { tool = it; pendingStart = null }
                    ToolChip(t("مفصل", "Hinge"), ModelerTool.Hinge, tool) { tool = it; pendingStart = null }
                    ToolChip(t("حمل عقدة", "Nodal load"), ModelerTool.NodalLoad, tool) { tool = it; pendingStart = null }
                    ToolChip(t("حمل عضو", "Member load"), ModelerTool.MemberLoad, tool) { tool = it; pendingStart = null }
                    ToolChip(t("بلاطة", "Slab"), ModelerTool.Slab, tool) { tool = it; pendingStart = null }
                    ToolChip(t("حذف", "Erase"), ModelerTool.Erase, tool) { tool = it; pendingStart = null }
                }

                if (tool == ModelerTool.Support) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            t("نوع الاستناد:", "Support:"),
                            fontSize = 11.sp,
                            color = Color(0xFF1565C0),
                            fontWeight = FontWeight.Bold
                        )
                        for (st in SupportType.entries) {
                            FilterChip(
                                selected = supportType == st,
                                onClick = { supportType = st },
                                label = { Text(st.displayName, fontSize = 10.sp) },
                                modifier = Modifier.padding(horizontal = 2.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF2E7D32),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GridDropdown(
                        label = t("الشبكة", "Grid"),
                        options = gridOptions,
                        selectedMm = gridMm,
                        onSelect = { gridMm = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    GridDropdown(
                        label = t("الالتقاط", "Snap"),
                        options = snapOptions,
                        selectedMm = snapMm,
                        onSelect = { snapMm = it }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = t(
                            "تشغيل الشبكة: ${nodes.size} عقدة / ${members.size} عضو / ${slabs.size} بلاطة",
                            "Model: ${nodes.size} joints / ${members.size} members / ${slabs.size} slabs"
                        ),
                        fontSize = 10.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { canvasSize = it }
        ) {
            FrameModelerCanvas(
                nodes = nodes,
                members = members,
                nodalLoads = nodalLoads,
                memberLoads = memberLoads,
                slabs = slabs,
                selectedNodeId = selectedNodeId,
                selectedMemberId = selectedMemberId,
                tool = tool,
                pendingStartNodeId = pendingStart,
                pendingSlabNodeIds = pendingSlabNodes,
                memberDragPreview = memberDragPreview,
                gridSizeMm = gridMm,
                snapMm = snapMm,
                viewport = viewport,
                onViewportChange = { viewport = it },
                onTap = ::handleTap,
                onNodeDragStart = { viewModel.beginNodeDrag() },
                onNodeDrag = { id, x, y ->
                    viewModel.dragNode(id, x, y)
                    selectedNodeId = id
                },
                onNodeDragEnd = { viewModel.endNodeDrag() },
                onNodeDragCancel = { viewModel.cancelNodeDrag() },
                onMemberDragStart = { nodeId ->
                    pendingStart = nodeId
                    memberDragPreview = null
                    selectedNodeId = null
                },
                onMemberDragUpdate = { x, y ->
                    if (pendingStart != null) memberDragPreview = x to y
                },
                onMemberDragEnd = { endNodeId, x, y ->
                    val startNodeId = pendingStart
                    val startNode = nodes.find { it.id == startNodeId }
                    if (startNodeId != null && startNode != null &&
                        kotlin.math.hypot(x - startNode.x, y - startNode.y) > 1e-6
                    ) {
                        val endId = endNodeId ?: viewModel.addNode(x, y)
                        if (endId != startNodeId) {
                            val endNode = nodes.find { it.id == endId }
                            val isColumn = if (endNode != null) {
                                abs(startNode.x - endNode.x) < 1e-6
                            } else {
                                abs(startNode.x - x) < 1e-6
                            }
                            val memberId = viewModel.addMemberBetween(
                                startNodeId,
                                endId,
                                if (isColumn) FrameMemberType.Column else FrameMemberType.Beam
                            )
                            if (memberId != null) {
                                selectedNodeId = null
                                viewModel.setSelectedMember(memberId)
                            }
                        }
                    }
                    pendingStart = null
                    memberDragPreview = null
                },
                onMemberDragCancel = {
                    pendingStart = null
                    memberDragPreview = null
                },
                fitRequest = fitRequest
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.92f),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(hint, fontSize = 11.sp, color = Color(0xFF37474F), fontWeight = FontWeight.Medium)
                    Text(
                        text = t(
                            "الشبكة: ${gridLabel(gridMm)} • الالتقاط: ${snapLabel(snapMm)}",
                            "Grid: ${gridLabel(gridMm)} • Snap: ${snapLabel(snapMm)}"
                        ),
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                }
            }

            FloatingActionButton(
                onClick = { viewModel.solveFrame() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = Color(0xFF1565C0),
                contentColor = Color.White
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(t("تحليل", "Solve"), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (nodes.isEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(t("???? ??????", "Start drawing"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            t(
                                "???? ???? �????� ?? ???? ??? ??????. ????? ???? ???????.",
                                "Choose the Joint tool and tap the grid, then draw members."
                            ),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            if (tool == ModelerTool.Select) {
                val selectedNode = nodes.find { it.id == selectedNodeId }
                val selectedMember = members.find { it.id == selectedMemberId }
                when {
                    selectedNode != null -> NodeInspector(
                        node = selectedNode,
                        nodalLoads = nodalLoads.withIndex()
                            .filter { it.value.nodeId == selectedNode.id },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        onUpdate = { x, y -> viewModel.updateNode(selectedNode.id, x, y, selectedNode.support) },
                        onSupportChange = { support ->
                            viewModel.updateNode(selectedNode.id, selectedNode.x, selectedNode.y, support)
                        },
                        onEditNodalLoad = { index, load -> editingNodalLoad = index to load },
                        onDeleteNodalLoad = { index -> viewModel.removeNodalLoad(index) },
                        onDelete = {
                            viewModel.removeNode(selectedNode.id)
                            selectedNodeId = null
                        },
                        onDismiss = { selectedNodeId = null }
                    )
                    selectedMember != null -> MemberInspector(
                        member = selectedMember,
                        length = memberLength(selectedMember, nodes),
                        memberLoads = memberLoads.withIndex()
                            .filter { it.value.memberId == selectedMember.id },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        onReleaseI = {
                            viewModel.updateMemberReleases(
                                selectedMember.id, !selectedMember.releaseI, selectedMember.releaseJ
                            )
                        },
                        onReleaseJ = {
                            viewModel.updateMemberReleases(
                                selectedMember.id, selectedMember.releaseI, !selectedMember.releaseJ
                            )
                        },
                        onEditMemberLoad = { index, load -> editingMemberLoad = index to load },
                        onDeleteMemberLoad = { index -> viewModel.removeMemberLoad(index) },
                        onEditProperties = { editingMemberProps = selectedMember },
                        onDelete = {
                            viewModel.removeMember(selectedMember.id)
                            viewModel.setSelectedMember(null)
                        },
                        onDismiss = { viewModel.setSelectedMember(null) }
                    )
                }
            }
        }
    }

    loadNodeTarget?.let { nodeId ->
        NodalLoadDialog(
            nodeId = nodeId,
            onDismiss = { loadNodeTarget = null },
            onConfirm = { fx, fy, mz, loadCase ->
                viewModel.addNodalLoad(nodeId, fx, fy, mz, loadCase)
                loadNodeTarget = null
            }
        )
    }

    loadMemberTarget?.let { memberId ->
        MemberLoadDialog(
            memberId = memberId,
            onDismiss = { loadMemberTarget = null },
            onConfirm = { type, value, position, loadCase ->
                viewModel.addMemberLoad(memberId, type, value, position, loadCase)
                loadMemberTarget = null
            }
        )
    }

    editingNodalLoad?.let { (index, load) ->
        NodalLoadDialog(
            nodeId = load.nodeId,
            initial = load,
            onDismiss = { editingNodalLoad = null },
            onConfirm = { fx, fy, mz, loadCase ->
                viewModel.updateNodalLoad(
                    index,
                    load.copy(fx = fx, fy = fy, mz = mz, loadCase = loadCase)
                )
                editingNodalLoad = null
            }
        )
    }

    editingMemberLoad?.let { (index, load) ->
        MemberLoadDialog(
            memberId = load.memberId,
            initial = load,
            onDismiss = { editingMemberLoad = null },
            onConfirm = { type, value, position, loadCase ->
                viewModel.updateMemberLoad(
                    index,
                    load.copy(loadType = type, value = value, position = position, loadCase = loadCase)
                )
                editingMemberLoad = null
            }
        )
    }

    editingMemberProps?.let { member ->
        MemberPropertiesDialog(
            member = member,
            onDismiss = { editingMemberProps = null },
            onSave = { updated ->
                viewModel.updateMember(updated)
                editingMemberProps = null
            }
        )
    }

    hingeTarget?.let { member ->
        HingeDialog(
            member = member,
            onDismiss = { hingeTarget = null },
            onConfirm = { releaseI, releaseJ ->
                viewModel.updateMemberReleases(member.id, releaseI, releaseJ)
                hingeTarget = null
            }
        )
    }

    slabDraft?.let { ids ->
        SlabDialog(
            onDismiss = {
                slabDraft = null
                pendingSlabNodes = emptyList()
            },
            onConfirm = { thickness, load, loadCase, distribution ->
                val id = viewModel.addSlabPanel(
                    nodeIds = ids,
                    loadKNm2 = load,
                    loadCase = loadCase,
                    thicknessMm = thickness,
                    distribution = distribution
                )
                if (id != null) {
                    slabDraft = null
                    pendingSlabNodes = emptyList()
                    tool = ModelerTool.Select
                }
            }
        )
    }

    editSlabTarget?.let { panel ->
        EditSlabDialog(
            panel = panel,
            onDismiss = { editSlabTarget = null },
            onSave = { updated ->
                viewModel.updateSlabPanel(updated)
                editSlabTarget = null
            },
            onDelete = {
                viewModel.removeSlabPanel(panel.id)
            }
        )
    }
}

@Composable
private fun MemberPropsDialog(
    member: FrameMember,
    onDismiss: () -> Unit,
    onSave: (FrameMember) -> Unit
) {
    var material by remember(member) { mutableStateOf(member.materialType) }
    var type by remember(member) { mutableStateOf(member.memberType) }
    var widthMm by remember(member) {
        mutableStateOf(member.concreteSection?.width?.let(::formatCoord) ?: "250")
    }
    var depthMm by remember(member) {
        mutableStateOf(member.concreteSection?.depth?.let(::formatCoord) ?: "500")
    }
    var steelSection by remember(member) { mutableStateOf(member.steelSectionName ?: "IPE 200") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("خصائص العضو ${member.id}", "Member ${member.id} properties")) },
        text = {
            Column {
                Text(t("المادة", "Material"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = material == FrameMaterialType.Concrete,
                        onClick = { material = FrameMaterialType.Concrete },
                        label = { Text(t("خرسانة", "Concrete"), fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = material == FrameMaterialType.Steel,
                        onClick = { material = FrameMaterialType.Steel },
                        label = { Text(t("حديد", "Steel"), fontSize = 11.sp) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(t("نوع العضو", "Member type"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (mt in FrameMemberType.entries) {
                        FilterChip(
                            selected = type == mt,
                            onClick = { type = mt },
                            label = { Text(mt.name, fontSize = 10.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (material == FrameMaterialType.Concrete) {
                    Row {
                        NumberField(widthMm, { widthMm = it }, t("العرض (مم)", "Width (mm)"), Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        NumberField(depthMm, { depthMm = it }, t("الارتفاع (مم)", "Depth (mm)"), Modifier.weight(1f))
                    }
                } else {
                    OutlinedTextField(
                        value = steelSection,
                        onValueChange = { steelSection = it },
                        label = { Text(t("المقطع الحديدي", "Steel Section")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = widthMm.toDoubleOrNull() ?: 250.0
                val d = depthMm.toDoubleOrNull() ?: 500.0
                val updated = member.copy(
                    materialType = material,
                    memberType = type,
                    concreteSection = if (material == FrameMaterialType.Concrete) ConcreteSectionProps(w, d) else null,
                    steelSectionName = if (material == FrameMaterialType.Steel) steelSection.ifBlank { "IPE 200" } else null
                )
                onSave(updated)
            }) { Text(t("حفظ", "Save"), color = Color(0xFF1565C0)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("إلغاء", "Cancel")) }
        }
    )
}

@Composable
private fun NodeInspector(
    node: FrameNode,
    nodalLoads: List<IndexedValue<NodalLoad>>,
    modifier: Modifier = Modifier,
    onUpdate: (Double, Double) -> Unit,
    onSupportChange: (SupportType) -> Unit,
    onEditNodalLoad: (Int, NodalLoad) -> Unit,
    onDeleteNodalLoad: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var xText by remember(node.id, node.x) { mutableStateOf(formatCoord(node.x)) }
    var yText by remember(node.id, node.y) { mutableStateOf(formatCoord(node.y)) }
    Card(
        modifier = modifier.widthIn(max = 430.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("العقدة ${node.id}", "Joint ${node.id}"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF1565C0)
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(xText, { xText = it }, "X (m)", Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                NumberField(yText, { yText = it }, "Y (m)", Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                TextButton(
                    enabled = xText.toDoubleOrNull() != null &&
                        yText.toDoubleOrNull() != null &&
                        (xText.toDoubleOrNull() != node.x || yText.toDoubleOrNull() != node.y),
                    onClick = {
                        val x = xText.toDoubleOrNull() ?: return@TextButton
                        val y = yText.toDoubleOrNull() ?: return@TextButton
                        onUpdate(x, y)
                    }
                ) { Text(t("تطبيق", "Apply"), fontSize = 12.sp) }
            }
            Spacer(Modifier.height(4.dp))
            Text(t("الاستناد", "Support"), fontSize = 10.sp, color = Color.Gray)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (st in SupportType.entries) {
                    FilterChip(
                        selected = node.support == st,
                        onClick = { onSupportChange(st) },
                        label = { Text(st.displayName, fontSize = 9.sp) },
                        modifier = Modifier.padding(horizontal = 2.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2E7D32),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            if (nodalLoads.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(t("أحمال العقدة", "Joint loads"), fontSize = 10.sp, color = Color.Gray)
                for (entry in nodalLoads.take(3)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${entry.value.loadCase}: " +
                                "Fx=${fmtLoad(entry.value.fx)}  " +
                                "Fy=${fmtLoad(entry.value.fy)}  " +
                                "Mz=${fmtLoad(entry.value.mz)}",
                            fontSize = 9.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onEditNodalLoad(entry.index, entry.value) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(15.dp))
                        }
                        IconButton(
                            onClick = { onDeleteNodalLoad(entry.index) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(15.dp))
                        }
                    }
                }
                if (nodalLoads.size > 3) {
                    Text(t("+${nodalLoads.size - 3} أحمال أخرى", "+${nodalLoads.size - 3} more"), fontSize = 9.sp, color = Color.Gray)
                }
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(t("حذف العقدة", "Delete joint"), fontSize = 11.sp, color = Color(0xFFC62828))
            }
        }
    }
}

@Composable
private fun MemberInspector(
    member: FrameMember,
    length: Double,
    memberLoads: List<IndexedValue<MemberLoad>>,
    modifier: Modifier = Modifier,
    onReleaseI: () -> Unit,
    onReleaseJ: () -> Unit,
    onEditMemberLoad: (Int, MemberLoad) -> Unit,
    onDeleteMemberLoad: (Int) -> Unit,
    onEditProperties: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = modifier.widthIn(max = 430.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("العضو ${member.id}", "Member ${member.id}"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF1565C0)
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEditProperties, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                }
            }
            Text(
                t(
                    "من ${member.nodeI} إلى ${member.nodeJ} • الطول ${"%.3f".format(length)} م",
                    "Joint ${member.nodeI} → ${member.nodeJ} • ${"%.3f".format(length)} m"
                ),
                fontSize = 10.sp,
                color = Color.Gray
            )
            Text(
                "${member.materialType.name} • ${member.memberType.name}" +
                    (member.concreteSection?.takeIf { member.materialType == FrameMaterialType.Concrete }
                        ?.let { " • ${it.width.toInt()}×${it.depth.toInt()}" } ?: "") +
                    (member.steelSectionName?.takeIf { member.materialType == FrameMaterialType.Steel }
                        ?.let { " • $it" } ?: ""),
                fontSize = 10.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = member.releaseI,
                    onClick = onReleaseI,
                    label = { Text(t("مفصل I", "Hinge I"), fontSize = 9.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF57C00),
                        selectedLabelColor = Color.White
                    )
                )
                Spacer(Modifier.width(4.dp))
                FilterChip(
                    selected = member.releaseJ,
                    onClick = onReleaseJ,
                    label = { Text(t("مفصل J", "Hinge J"), fontSize = 9.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF57C00),
                        selectedLabelColor = Color.White
                    )
                )
            }
            if (memberLoads.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(t("أحمال العضو", "Member loads"), fontSize = 10.sp, color = Color.Gray)
                for (entry in memberLoads.take(3)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${entry.value.loadCase}: ${entry.value.loadType.name} " +
                                "${fmtLoad(entry.value.value)} @ ${fmtLoad(entry.value.position)}m",
                            fontSize = 9.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onEditMemberLoad(entry.index, entry.value) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(15.dp))
                        }
                        IconButton(
                            onClick = { onDeleteMemberLoad(entry.index) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(15.dp))
                        }
                    }
                }
                if (memberLoads.size > 3) {
                    Text(t("+${memberLoads.size - 3} أحمال أخرى", "+${memberLoads.size - 3} more"), fontSize = 9.sp, color = Color.Gray)
                }
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(t("حذف العضو", "Delete member"), fontSize = 11.sp, color = Color(0xFFC62828))
            }
        }
    }
}

@Composable
private fun MemberPropertiesDialog(
    member: FrameMember,
    onDismiss: () -> Unit,
    onSave: (FrameMember) -> Unit
) {
    var memberType by remember(member.id) { mutableStateOf(member.memberType) }
    var materialType by remember(member.id) { mutableStateOf(member.materialType) }
    val existingSection = member.concreteSection ?: ConcreteSectionProps(250.0, 500.0)
    var width by remember(member.id) { mutableStateOf(formatCoord(existingSection.width)) }
    var depth by remember(member.id) { mutableStateOf(formatCoord(existingSection.depth)) }
    var fcu by remember(member.id) { mutableStateOf(formatCoord(existingSection.fcu)) }
    var fy by remember(member.id) { mutableStateOf(formatCoord(existingSection.fy)) }
    var cover by remember(member.id) { mutableStateOf(formatCoord(existingSection.cover)) }
    val steelSections = remember { SteelTables.getAllSections() }
    var steelSectionName by remember(member.id) {
        mutableStateOf(member.steelSectionName ?: steelSections.firstOrNull()?.name ?: "IPE 300")
    }
    var steelExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val widthValue = width.toDoubleOrNull()
    val depthValue = depth.toDoubleOrNull()
    val fcuValue = fcu.toDoubleOrNull()
    val fyValue = fy.toDoubleOrNull()
    val coverValue = cover.toDoubleOrNull()
    val concreteValid = widthValue != null && widthValue > 0.0 &&
        depthValue != null && depthValue > 0.0 &&
        fcuValue != null && fcuValue > 0.0 &&
        fyValue != null && fyValue > 0.0 &&
        coverValue != null && coverValue >= 0.0
    val isValid = if (materialType == FrameMaterialType.Concrete) {
        concreteValid
    } else {
        steelSectionName.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("خصائص العضو ${member.id}", "Member ${member.id} properties")) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(t("نوع العضو", "Member type"), fontSize = 10.sp, color = Color.Gray)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (type in FrameMemberType.entries) {
                        FilterChip(
                            selected = memberType == type,
                            onClick = { memberType = type },
                            label = { Text(type.displayName, fontSize = 10.sp) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(t("المادة", "Material"), fontSize = 10.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (material in FrameMaterialType.entries) {
                        FilterChip(
                            selected = materialType == material,
                            onClick = { materialType = material },
                            label = { Text(material.displayName, fontSize = 10.sp) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }

                if (materialType == FrameMaterialType.Concrete) {
                    Spacer(Modifier.height(6.dp))
                    Row {
                        NumberField(width, { width = it }, t("العرض b (مم)", "Width b (mm)"), Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        NumberField(depth, { depth = it }, t("العمق h (مم)", "Depth h (mm)"), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        NumberField(fcu, { fcu = it }, t("fcu (MPa)", "fcu (MPa)"), Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        NumberField(fy, { fy = it }, t("fy (MPa)", "fy (MPa)"), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    NumberField(cover, { cover = it }, t("الغطاء (مم)", "Cover (mm)"), Modifier.fillMaxWidth())
                } else {
                    Spacer(Modifier.height(6.dp))
                    Box {
                        OutlinedButton(
                            onClick = { steelExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(steelSectionName, fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = steelExpanded,
                            onDismissRequest = { steelExpanded = false },
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            for (section in steelSections) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${section.name}  (${section.depth.toInt()}×${section.width.toInt()})",
                                            fontSize = 11.sp
                                        )
                                    },
                                    onClick = {
                                        steelSectionName = section.name
                                        steelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t(
                            "تُحسب خصائص المقطع من جدول القطاعات القياسية.",
                            "Section properties are taken from the standard steel table."
                        ),
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val section = if (materialType == FrameMaterialType.Concrete) {
                        ConcreteSectionProps(
                            width = widthValue ?: existingSection.width,
                            depth = depthValue ?: existingSection.depth,
                            fcu = fcuValue ?: existingSection.fcu,
                            fy = fyValue ?: existingSection.fy,
                            cover = coverValue ?: existingSection.cover
                        )
                    } else {
                        member.concreteSection ?: existingSection
                    }
                    onSave(
                        member.copy(
                            memberType = memberType,
                            materialType = materialType,
                            concreteSection = section,
                            steelSectionName = if (materialType == FrameMaterialType.Steel) {
                                steelSectionName
                            } else {
                                member.steelSectionName
                            }
                        )
                    )
                }
            ) {
                Text(t("حفظ", "Save"), color = if (isValid) Color(0xFF1565C0) else Color.Gray)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("إلغاء", "Cancel")) }
        }
    )
}

private fun memberLength(member: FrameMember, nodes: List<FrameNode>): Double {
    val a = nodes.find { it.id == member.nodeI } ?: return 0.0
    val b = nodes.find { it.id == member.nodeJ } ?: return 0.0
    return kotlin.math.hypot(b.x - a.x, b.y - a.y)
}

private fun fmtLoad(value: Double): String = formatCoord(value)

private fun formatCoord(value: Double): String =
    if (kotlin.math.abs(value - kotlin.math.round(value)) < 1e-6) {
        kotlin.math.round(value).toInt().toString()
    } else {
        "%.3f".format(value)
    }

@Composable
private fun ToolChip(
    label: String,
    value: ModelerTool,
    selected: ModelerTool,
    onClick: (ModelerTool) -> Unit
) {
    FilterChip(
        selected = selected == value,
        onClick = { onClick(value) },
        label = { Text(label, fontSize = 10.sp, maxLines = 1) },
        modifier = Modifier.padding(horizontal = 2.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF1565C0),
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun GridDropdown(
    label: String,
    options: List<GridOption>,
    selectedMm: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val option = options.find { it.mm == selectedMm } ?: options.first()
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(
                    "$label: ${if (LocaleHelper.isArabic()) option.labelAr else option.labelEn}",
                    fontSize = 10.sp
                )
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (o in options) {
                DropdownMenuItem(
                    text = {
                        Text(if (LocaleHelper.isArabic()) o.labelAr else o.labelEn, fontSize = 12.sp)
                    },
                    onClick = {
                        expanded = false
                        onSelect(o.mm)
                    }
                )
            }
        }
    }
}

private fun gridLabel(mm: Int): String =
    if (mm >= 1000) "${mm / 1000} m" else "$mm mm"

private fun snapLabel(mm: Int): String =
    if (mm == 0) "Off" else gridLabel(mm)

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun NodalLoadDialog(
    nodeId: Int,
    initial: NodalLoad? = null,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, Double, String) -> Unit
) {
    var fx by remember(initial) { mutableStateOf(initial?.fx?.let(::formatCoord) ?: "0") }
    var fy by remember(initial) { mutableStateOf(initial?.fy?.let(::formatCoord) ?: "-10") }
    var mz by remember(initial) { mutableStateOf(initial?.mz?.let(::formatCoord) ?: "0") }
    var loadCase by remember(initial) { mutableStateOf(initial?.loadCase ?: "DL") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) t("حمل عقدة $nodeId", "Nodal load at joint $nodeId")
                else t("تعديل حمل العقدة $nodeId", "Edit load at joint $nodeId")
            )
        },
        text = {
            Column {
                Row {
                    NumberField(fx, { fx = it }, "Fx (kN)", Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    NumberField(fy, { fy = it }, t("Fy (kN)", "Fy (kN)"), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    NumberField(mz, { mz = it }, t("Mz (kN·m)", "Mz (kN·m)"), Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    NumberField(loadCase, { loadCase = it }, t("الحمل", "Case"), Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    fx.toDoubleOrNull() ?: 0.0,
                    fy.toDoubleOrNull() ?: 0.0,
                    mz.toDoubleOrNull() ?: 0.0,
                    loadCase.ifBlank { "DL" }
                )
            }) { Text(if (initial == null) t("إضافة", "Add") else t("تحديث", "Update"), color = Color(0xFF1565C0)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("إلغاء", "Cancel")) }
        }
    )
}

@Composable
private fun MemberLoadDialog(
    memberId: Int,
    initial: MemberLoad? = null,
    onDismiss: () -> Unit,
    onConfirm: (MemberLoadType, Double, Double, String) -> Unit
) {
    var loadType by remember(initial) { mutableStateOf(initial?.loadType ?: MemberLoadType.UDL) }
    var value by remember(initial) { mutableStateOf(initial?.value?.let(::formatCoord) ?: "20") }
    var position by remember(initial) { mutableStateOf(initial?.position?.let(::formatCoord) ?: "0") }
    var loadCase by remember(initial) { mutableStateOf(initial?.loadCase ?: "DL") }
    var typeExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) t("حمل عضو $memberId", "Member load $memberId")
                else t("تعديل حمل العضو $memberId", "Edit member load $memberId")
            )
        },
        text = {
            Column {
                Box {
                    OutlinedButton(
                        onClick = { typeExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when (loadType) {
                                MemberLoadType.UDL -> t("حمل موزع منتظم", "Uniform load")
                                MemberLoadType.PointLoad -> t("حمل مركز", "Point load")
                                MemberLoadType.Moment -> t("عزم", "Moment")
                                MemberLoadType.LinearVarying -> t("موزع متغير", "Varying load")
                                MemberLoadType.Trapezoidal -> t("شبه منحرف (بلاطة)", "Trapezoidal (slab)")
                            },
                            fontSize = 12.sp
                        )
                    }
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        for (type in MemberLoadType.entries) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (type) {
                                            MemberLoadType.UDL -> t("حمل موزع منتظم", "Uniform load")
                                            MemberLoadType.PointLoad -> t("حمل مركز", "Point load")
                                            MemberLoadType.Moment -> t("عزم", "Moment")
                                            MemberLoadType.LinearVarying -> t("موزع متغير", "Varying load")
                                            MemberLoadType.Trapezoidal -> t("شبه منحرف (بلاطة)", "Trapezoidal (slab)")
                                        },
                                        fontSize = 12.sp
                                    )
                                },
                                onClick = {
                                    typeExpanded = false
                                    loadType = type
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    NumberField(value, { value = it }, t("القيمة", "Value"), Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    NumberField(position, { position = it }, t("الموقع (م)", "Position (m)"), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                NumberField(loadCase, { loadCase = it }, t("الحمل", "Case"), Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    loadType,
                    value.toDoubleOrNull() ?: 0.0,
                    position.toDoubleOrNull() ?: 0.0,
                    loadCase.ifBlank { "DL" }
                )
            }) { Text(if (initial == null) t("إضافة", "Add") else t("تحديث", "Update"), color = Color(0xFF1565C0)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("إلغاء", "Cancel")) }
        }
    )
}

@Composable
private fun HingeDialog(
    member: FrameMember,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Boolean) -> Unit
) {
    var releaseI by remember { mutableStateOf(member.releaseI) }
    var releaseJ by remember { mutableStateOf(member.releaseJ) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("مفاصل العضو ${member.id}", "Member ${member.id} releases")) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = releaseI, onCheckedChange = { releaseI = it })
                    Spacer(Modifier.width(8.dp))
                    Text(t("مفصل عند العقدة ${member.nodeI}", "Hinge at joint ${member.nodeI}"), fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = releaseJ, onCheckedChange = { releaseJ = it })
                    Spacer(Modifier.width(8.dp))
                    Text(t("مفصل عند العقدة ${member.nodeJ}", "Hinge at joint ${member.nodeJ}"), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(releaseI, releaseJ) }) {
                Text(t("تطبيق", "Apply"), color = Color(0xFF1565C0))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("إلغاء", "Cancel")) }
        }
    )
}

@Composable
private fun SlabDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, String, SlabDistribution) -> Unit
) {
    var thickness by remember { mutableStateOf("200") }
    var load by remember { mutableStateOf("10") }
    var loadCase by remember { mutableStateOf("DL") }
    var distribution by remember { mutableStateOf(SlabDistribution.TwoWay) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("بلاطة جديدة", "New slab panel")) },
        text = {
            Column {
                Row {
                    NumberField(thickness, { thickness = it }, t("السماكة (مم)", "Thickness (mm)"), Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    NumberField(load, { load = it }, t("الحمل (kN/m²)", "Load (kN/m²)"), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                NumberField(loadCase, { loadCase = it }, t("الحمل", "Case"), Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(t("توزيع الحمل", "Load distribution"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = distribution == SlabDistribution.OneWay,
                        onClick = { distribution = SlabDistribution.OneWay },
                        label = { Text(t("اتجاه واحد", "One-way"), fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = distribution == SlabDistribution.TwoWay,
                        onClick = { distribution = SlabDistribution.TwoWay },
                        label = { Text(t("اتجاهين", "Two-way"), fontSize = 11.sp) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    thickness.toDoubleOrNull() ?: 200.0,
                    load.toDoubleOrNull() ?: 10.0,
                    loadCase.ifBlank { "DL" },
                    distribution
                )
            }) { Text(t("إضافة", "Add"), color = Color(0xFF1565C0)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("إلغاء", "Cancel")) }
        }
    )
}

@Composable
private fun EditSlabDialog(
    panel: SlabPanel,
    onDismiss: () -> Unit,
    onSave: (SlabPanel) -> Unit,
    onDelete: () -> Unit
) {
    var thickness by remember { mutableStateOf(panel.thicknessMm.toString()) }
    var load by remember { mutableStateOf(panel.loadKNm2.toString()) }
    var loadCase by remember { mutableStateOf(panel.loadCase) }
    var distribution by remember { mutableStateOf(panel.distribution) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("بلاطة ${panel.id}", "Slab ${panel.id}")) },
        text = {
            Column {
                Row {
                    NumberField(thickness, { thickness = it }, t("السماكة (مم)", "Thickness (mm)"), Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    NumberField(load, { load = it }, t("الحمل (kN/m²)", "Load (kN/m²)"), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                NumberField(loadCase, { loadCase = it }, t("الحمل", "Case"), Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = distribution == SlabDistribution.OneWay,
                        onClick = { distribution = SlabDistribution.OneWay },
                        label = { Text(t("اتجاه واحد", "One-way"), fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = distribution == SlabDistribution.TwoWay,
                        onClick = { distribution = SlabDistribution.TwoWay },
                        label = { Text(t("اتجاهين", "Two-way"), fontSize = 11.sp) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    panel.copy(
                        thicknessMm = thickness.toDoubleOrNull() ?: panel.thicknessMm,
                        loadKNm2 = load.toDoubleOrNull() ?: panel.loadKNm2,
                        loadCase = loadCase.ifBlank { panel.loadCase },
                        distribution = distribution
                    )
                )
            }) { Text(t("حفظ", "Save"), color = Color(0xFF1565C0)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                    Text(t("حذف", "Delete"), color = Color(0xFFC62828))
                }
                TextButton(onClick = onDismiss) { Text(t("إلغاء", "Cancel")) }
            }
        }
    )
}