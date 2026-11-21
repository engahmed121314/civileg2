package com.civileg.app.features.frame

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.civileg.app.ui.compose.components.drawings.SteelSectionDrawing
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.domain.entities.SlabPanel
import com.civileg.app.domain.entities.SupportType
import com.civileg.app.domain.entities.orderQuadJoints
import com.civileg.app.domain.calculations.SteelFrameDesign
import com.civileg.app.domain.modeler.ModelerLayerVisibility
import com.civileg.app.utils.FrameDxfExporter
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.utils.SteelTables
import com.civileg.app.features.frame.FrameAnalysisViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal fun t(ar: String, en: String): String = if (LocaleHelper.isArabic()) ar else en

internal data class GridOption(val labelAr: String, val labelEn: String, val mm: Int)

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
    var additiveSelection by remember { mutableStateOf(false) }
    var clipboardVersion by remember { mutableIntStateOf(0) }
    var fitRequest by remember { mutableIntStateOf(0) }
    var viewport by remember { mutableStateOf(ModelerViewport()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var cursorWorld by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var layerMask by rememberSaveable { mutableIntStateOf(ModelerLayerVisibility.ALL) }
    var toolbarExpanded by rememberSaveable { mutableStateOf(true) }
    val layers = ModelerLayerVisibility.fromMask(layerMask)
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    LaunchedEffect(viewport, canvasSize, gridMm, layers) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            viewModel.updateModelerSceneView(
                scale = viewport.scale,
                panX = viewport.panX,
                panY = viewport.panY,
                width = canvasSize.width,
                height = canvasSize.height,
                gridMm = gridMm,
                layers = layers
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

    val selectedNodeIds by viewModel.selectedNodeIds.observeAsState(emptySet())
    val selectedMemberIds by viewModel.selectedMemberIds.observeAsState(emptySet())
    val selectedNodeId = selectedNodeIds.singleOrNull()
    val selectedMemberId = selectedMemberIds.singleOrNull()
    val dimensions by viewModel.dimensions.observeAsState(emptyList())
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

    fun handleShortcut(event: KeyEvent): Boolean {
        when (event.key) {
            Key.ShiftLeft, Key.ShiftRight, Key.CtrlLeft, Key.CtrlRight -> {
                additiveSelection = event.type == KeyEventType.KeyDown
                return false
            }
        }
        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return false
        return when (event.key) {
            Key.Z -> {
                if (event.isShiftPressed) viewModel.redo() else viewModel.undo()
                true
            }
            Key.Y -> {
                viewModel.redo()
                true
            }
            Key.C -> {
                viewModel.copySelection()
                clipboardVersion++
                true
            }
            Key.V -> {
                viewModel.pasteClipboard()
                true
            }
            Key.A -> {
                viewModel.selectAll()
                true
            }
            else -> false
        }
    }

    fun handleTap(tap: ModelerTap) {
        when (tool) {
            ModelerTool.Pan -> {
                // Pan mode: tap does not change selection or add elements
            }
            ModelerTool.Select -> {
                if (tap.nodeId != null) {
                    viewModel.selectNode(tap.nodeId, additiveSelection)
                } else if (tap.memberId != null) {
                    viewModel.selectMember(tap.memberId, additiveSelection)
                } else if (tap.panelId != null) {
                    editSlabTarget = slabs.find { it.id == tap.panelId }
                } else if (!additiveSelection) {
                    viewModel.clearSelection()
                }
            }
            ModelerTool.Move -> {
                if (tap.nodeId != null) {
                    viewModel.selectNode(tap.nodeId)
                }
            }
            ModelerTool.Joint -> {
                if (tap.nodeId == null) {
                    val id = viewModel.addNode(tap.worldX, tap.worldY)
                    viewModel.selectNode(id)
                } else {
                    viewModel.selectNode(tap.nodeId)
                }
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
                        id?.let { viewModel.selectMember(it) }
                    }
                    pendingStart = null
                }
            }
            ModelerTool.Support -> tap.nodeId?.let {
                viewModel.setNodeSupport(it, supportType)
                viewModel.selectNode(it)
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
                val removedLabel = when {
                    tap.nodeId != null -> t("العقدة ${tap.nodeId}", "joint ${tap.nodeId}")
                    tap.memberId != null -> t("العضو ${tap.memberId}", "member ${tap.memberId}")
                    tap.panelId != null -> t("البلاطة ${tap.panelId}", "slab ${tap.panelId}")
                    else -> null
                }
                when {
                    tap.nodeId != null -> viewModel.removeNode(tap.nodeId)
                    tap.memberId != null -> viewModel.removeMember(tap.memberId)
                    tap.panelId != null -> viewModel.removeSlabPanel(tap.panelId)
                }
                if (removedLabel != null) {
                    viewModel.clearSelection()
                    snackbarScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = t("تم حذف $removedLabel", "Deleted $removedLabel"),
                            actionLabel = t("تراجع", "Undo"),
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undo()
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { handleShortcut(it) }
    ) {
        Surface(color = Color(0xFFE3F2FD)) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { toolbarExpanded = !toolbarExpanded }) {
                        Icon(
                            imageVector = if (toolbarExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (toolbarExpanded) {
                                t("إخفاء أدوات النمذجة", "Hide modeling tools")
                            } else {
                                t("إظهار أدوات النمذجة", "Show modeling tools")
                            },
                            tint = Color(0xFF1565C0)
                        )
                    }
                    AppText(
                        text = toolLabel(tool),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 2.dp, end = 8.dp)
                    )
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = historyVersion >= 0 && viewModel.canUndo
                    ) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = t("تراجع", "Undo"), tint = Color(0xFF1565C0)) }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = historyVersion >= 0 && viewModel.canRedo
                    ) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = t("إعادة", "Redo"), tint = Color(0xFF1565C0)) }
                    IconButton(onClick = { fitRequest++ }) {
                        Icon(Icons.Default.FitScreen, contentDescription = t("ملاءمة العرض", "Fit view"), tint = Color(0xFF1565C0))
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
                                        slabs = slabs,
                                        dimensions = dimensions,
                                        layers = layers
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
                }

                AnimatedVisibility(
                    visible = tool == ModelerTool.Support,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(
                            t("نوع الاستناد:", "Support:"),
                            fontSize = 11.sp,
                            color = Color(0xFF1565C0),
                            fontWeight = FontWeight.Bold
                        )
                        for (st in SupportType.entries) {
                            FilterChip(
                                selected = supportType == st,
                                onClick = { supportType = st },
                                label = { AppText(st.displayName, fontSize = 10.sp) },
                                modifier = Modifier.padding(horizontal = 2.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF2E7D32),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = toolbarExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.copySelection()
                                    clipboardVersion++
                                },
                                enabled = selectedNodeIds.isNotEmpty() || selectedMemberIds.isNotEmpty()
                            ) { Icon(Icons.Default.ContentCopy, contentDescription = t("نسخ", "Copy"), tint = Color(0xFF1565C0)) }
                            IconButton(
                                onClick = { viewModel.pasteClipboard() },
                                enabled = clipboardVersion >= 0 && viewModel.hasClipboard()
                            ) { Icon(Icons.Default.ContentPaste, contentDescription = t("لصق", "Paste"), tint = Color(0xFF1565C0)) }
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { zoomBy(1.35f) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Add, contentDescription = t("تكبير", "Zoom in"), tint = Color(0xFF1565C0))
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { zoomBy(1f / 1.35f) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Remove, contentDescription = t("تصغير", "Zoom out"), tint = Color(0xFF1565C0))
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
                            Spacer(Modifier.width(6.dp))
                            LayerDropdown(
                                layers = layers,
                                onToggle = { mask -> layerMask = mask }
                            )
                            Spacer(Modifier.width(10.dp))
                            AppText(
                                text = t(
                                    "النموذج: ${nodes.size} عقدة / ${members.size} عضو / ${slabs.size} بلاطة",
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
                layers = layers,
                dimensions = dimensions,
                selectedNodeIds = selectedNodeIds,
                selectedMemberIds = selectedMemberIds,
                tool = tool,
                pendingStartNodeId = pendingStart,
                pendingSlabNodeIds = pendingSlabNodes,
                memberDragPreview = memberDragPreview,
                gridSizeMm = gridMm,
                snapMm = snapMm,
                viewport = viewport,
                onViewportChange = { viewport = it },
                onTap = ::handleTap,
                onMarqueeSelect = { minX, minY, maxX, maxY, additive ->
                    val nodeIds = nodes
                        .filter { it.x in minX..maxX && it.y in minY..maxY }
                        .map { it.id }
                        .toSet()
                    val memberIds = members
                        .filter { member ->
                            val a = nodes.find { it.id == member.nodeI }
                            val b = nodes.find { it.id == member.nodeJ }
                            a != null && b != null &&
                                a.x in minX..maxX && a.y in minY..maxY &&
                                b.x in minX..maxX && b.y in minY..maxY
                        }
                        .map { it.id }
                        .toSet()
                    if (additive) {
                        viewModel.setSelection(
                            selectedNodeIds + nodeIds,
                            selectedMemberIds + memberIds
                        )
                    } else {
                        viewModel.setSelection(nodeIds, memberIds)
                    }
                },
                additiveSelection = additiveSelection,
                onCursorWorldChange = { cursorWorld = it },
                onNodeDragStart = { id ->
                    viewModel.beginNodeDrag()
                    viewModel.selectNode(id)
                },
                onNodeDrag = { id, x, y -> viewModel.dragNode(id, x, y) },
                onNodeDragEnd = { viewModel.endNodeDrag() },
                onNodeDragCancel = { viewModel.cancelNodeDrag() },
                onMemberDragStart = { nodeId ->
                    pendingStart = nodeId
                    memberDragPreview = null
                    viewModel.clearSelection()
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
                                viewModel.selectMember(memberId)
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
                    AppText(hint, fontSize = 11.sp, color = Color(0xFF37474F), fontWeight = FontWeight.Medium)
                    AppText(
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
                    AppText(t("تحليل", "Solve"), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp)
            )

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
                        AppText(t("ابدأ الرسم", "Start drawing"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        AppText(
                            t(
                                "اختر أداة «عقدة» وانقر على الشبكة، ثم ارسم الأعضاء.",
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
                            viewModel.clearSelection()
                        },
                        onDismiss = { viewModel.clearSelection() }
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
                            viewModel.clearSelection()
                        },
                        onDismiss = { viewModel.clearSelection() }
                    )
                }
            }
        }

        Surface(color = Color(0xFFECEFF1)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val cursor = cursorWorld
                AppText(
                    text = if (cursor != null) {
                        String.format(
                            java.util.Locale.ROOT,
                            "X: %.3f m    Y: %.3f m",
                            cursor.first,
                            cursor.second
                        )
                    } else {
                        "X: —    Y: —"
                    },
                    fontSize = 10.sp,
                    color = Color(0xFF37474F),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(14.dp))
                AppText(
                    text = t(
                        "تكبير ${String.format(java.util.Locale.ROOT, "%.0f", viewport.scale / 60f * 100f)}%",
                        "Zoom ${String.format(java.util.Locale.ROOT, "%.0f", viewport.scale / 60f * 100f)}%"
                    ),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.width(14.dp))
                AppText(
                    text = t(
                        "شبكة ${gridLabel(gridMm)} • التقاط ${snapLabel(snapMm)}",
                        "Grid ${gridLabel(gridMm)} • Snap ${snapLabel(snapMm)}"
                    ),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.width(14.dp))
                AppText(
                    text = t(
                        "${nodes.size} عقدة / ${members.size} عضو / ${slabs.size} بلاطة",
                        "${nodes.size} joints / ${members.size} members / ${slabs.size} slabs"
                    ),
                    fontSize = 10.sp,
                    color = Color(0xFF1565C0),
                    fontWeight = FontWeight.Medium
                )
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
            memberLengthM = memberLength(member, nodes),
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
