package com.civileg.app.features.frame

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.db.Design
import com.civileg.app.db.DesignType
import com.civileg.app.db.Project
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.calculations.SteelFrameDesign
import com.civileg.app.ui.compose.components.drawings.FrameRebarSectionDrawing
import com.civileg.app.ui.compose.components.drawings.FrameSteelSectionDrawing
import com.civileg.app.ui.compose.components.drawings.SteelSectionDrawing
import com.civileg.app.utils.SteelTables
import com.civileg.app.ui.theme.CivilEngineerTheme
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.viewmodel.DiagramType
import com.civileg.app.features.frame.FrameAnalysisViewModel
import com.civileg.app.viewmodel.FrameDesignNotice
import com.civileg.app.features.project.ProjectViewModel
import com.civileg.app.utils.FrameAnalysisPdfExporter
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

fun FrameSaveDesignDialog(
    projects: List<Project>,
    defaultName: String,
    onDismiss: () -> Unit,
    onSave: (projectId: Long, name: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var selectedProjectId by rememberSaveable {
        mutableStateOf(projects.firstOrNull()?.id ?: 0L)
    }
    var projectMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(projects) {
        if (selectedProjectId == 0L) {
            selectedProjectId = projects.firstOrNull()?.id ?: 0L
        }
    }
    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(stringResource(R.string.frame_save_design)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (projects.isEmpty()) {
                    AppText(
                        stringResource(R.string.frame_no_projects_for_save),
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Box {
                        AppButton(
                            label = selectedProject?.name ?: stringResource(R.string.frame_save_project),
                            onClick = { projectMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            style = AppButtonStyle.Outline
                        )
                        DropdownMenu(
                            expanded = projectMenuExpanded,
                            onDismissRequest = { projectMenuExpanded = false }
                        ) {
                            for (project in projects) {
                                DropdownMenuItem(
                                    text = { AppText(project.name) },
                                    onClick = {
                                        selectedProjectId = project.id
                                        projectMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { AppText(stringResource(R.string.frame_save_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            AppButton(
                label = stringResource(R.string.save),
                enabled = name.isNotBlank() && selectedProjectId > 0,
                onClick = { onSave(selectedProjectId, name.trim()) },
                style = AppButtonStyle.Text,
                fillMaxWidth = false
            )
        },
        dismissButton = {
            AppButton(
                label = stringResource(R.string.cancel),
                onClick = onDismiss,
                style = AppButtonStyle.Text,
                fillMaxWidth = false
            )
        }
    )
}

@Composable
fun FrameOpenDesignDialog(
    designs: List<Design>,
    projects: List<Project>,
    onDismiss: () -> Unit,
    onOpen: (designId: Long) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(stringResource(R.string.frame_open_design)) },
        text = {
            if (designs.isEmpty()) {
                AppText(stringResource(R.string.frame_no_saved_designs))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(designs, key = { it.id }) { design ->
                        val projectName = projects.firstOrNull { it.id == design.projectId }?.name
                        ListItem(
                            headlineContent = {
                                AppText(design.name, fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = {
                                AppText(
                                    buildString {
                                        append(dateFormat.format(design.createdAt))
                                        if (!projectName.isNullOrBlank()) append(" • $projectName")
                                        append(" • ${design.codeUsed}")
                                    },
                                    fontSize = 12.sp
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                            },
                            modifier = Modifier.clickable { onOpen(design.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            AppButton(
                label = stringResource(R.string.cancel),
                onClick = onDismiss,
                style = AppButtonStyle.Text,
                fillMaxWidth = false
            )
        }
    )
}

// ============================================================================
// Tab 0: Drawing
// ============================================================================
@Composable
fun DrawingTab(
    modifier: Modifier = Modifier,
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    memberLoads: List<MemberLoad>,
    nodalLoads: List<NodalLoad>,
    result: FrameAnalysisResult?,
    diagramType: DiagramType,
    selectedMemberId: Int?,
    modelLoadVersion: Int,
    viewModel: FrameAnalysisViewModel
) {
    var viewMode by remember { mutableIntStateOf(0) }
    var editorMode by rememberSaveable { mutableIntStateOf(0) }
    val slabs by viewModel.slabs.observeAsState(emptyList())
    // FIX: Sync local viewMode with ViewModel so it survives tab switches
    val vmViewMode by viewModel.drawingViewMode.observeAsState(0)
    LaunchedEffect(vmViewMode) { viewMode = vmViewMode }
    val settingsVal by viewModel.settings.observeAsState(FrameAnalysisSettings())
    val steelFyVal by viewModel.steelFy.observeAsState(355.0)
    val viewModes = listOf(
        stringResource(R.string.frame_view_frame_en) to stringResource(R.string.frame_view_frame_ar),
        stringResource(R.string.frame_view_long_section_en) to stringResource(R.string.frame_view_long_section_ar),
        stringResource(R.string.frame_view_cross_section_en) to stringResource(R.string.frame_view_cross_section_ar),
        stringResource(R.string.frame_view_plan_en) to stringResource(R.string.frame_view_plan_ar)
    )

    Column(modifier = modifier.fillMaxSize()) {
        Surface(color = Color(0xFFF5F7FA)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = editorMode == 0,
                    onClick = { editorMode = 0 },
                    label = { AppText(stringResource(R.string.frame_modeler_tab), fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1565C0),
                        selectedLabelColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                FilterChip(
                    selected = editorMode == 1,
                    onClick = { editorMode = 1 },
                    label = { AppText(stringResource(R.string.frame_modeler_view_tab), fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1565C0),
                        selectedLabelColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                AppText(
                    text = stringResource(R.string.frame_modeler_short_hint),
                    fontSize = 9.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (editorMode == 0) {
            FrameModelerTab(
                modifier = Modifier.fillMaxSize(),
                nodes = nodes,
                members = members,
                nodalLoads = nodalLoads,
                memberLoads = memberLoads,
                slabs = slabs,

                externalFitRequest = modelLoadVersion,
                viewModel = viewModel
            )
            return@Column
        }

        // Design Code & Steel Fy selector
        Surface(
            color = Color(0xFFE3F2FD),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(stringResource(R.string.frame_design_code_label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                Spacer(modifier = Modifier.width(8.dp))
                for (code in DesignCode.entries) {
                    FilterChip(
                        selected = settingsVal.designCode == code,
                        onClick = { viewModel.updateDesignCode(code) },
                        label = { AppText(code.displayName, fontSize = 11.sp) },
                        modifier = Modifier.padding(end = 4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1565C0),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                AppText("fy=${String.format(java.util.Locale.ROOT, "%.0f", steelFyVal)} MPa", fontSize = 11.sp, color = Color.Gray)
            }
        }

        // View mode selector
        Surface(
            color = Color(0xFF1565C0).copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            ScrollableTabRow(
                selectedTabIndex = viewMode,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF1565C0),
                edgePadding = 8.dp,
                divider = {}
            ) {
                viewModes.forEachIndexed { index, (en, ar) ->
                    Tab(
                        selected = viewMode == index,
                        onClick = {
                            viewMode = index
                            viewModel.setDrawingViewMode(index)
                        },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AppText(en, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                AppText(ar, fontSize = 9.sp, color = Color.Gray)
                            }
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            FrameDrawingCanvas(
                nodes = nodes,
                members = members,
                memberLoads = memberLoads,
                nodalLoads = nodalLoads,
                result = result,
                diagramType = diagramType,
                selectedMemberId = selectedMemberId,
                onMemberTap = { viewModel.setSelectedMember(it) },
                viewMode = viewMode
            )

            // Solve button (FAB)
            if (nodes.size >= 2 && members.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { viewModel.solveFrame() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = Color(0xFF1565C0),
                    contentColor = Color.White
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlayArrow, stringResource(R.string.frame_solve))
                        Spacer(modifier = Modifier.height(2.dp))
                        AppText(stringResource(R.string.frame_solve), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Info card
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    AppText("${stringResource(R.string.frame_tab_nodes)}: ${nodes.size}  |  ${stringResource(R.string.frame_tab_members)}: ${members.size}", fontSize = 11.sp, color = Color.Gray)
                    if (result?.hasResults == true) {
                        AppText(
                            "${stringResource(R.string.safe)} ✓  |  ${stringResource(R.string.frame_tab_loads)}: ${memberLoads.size + nodalLoads.size}",
                            fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// Tab 1: Nodes
// ============================================================================
@Composable
fun NodesTab(
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    viewModel: FrameAnalysisViewModel
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingNode by remember { mutableStateOf<FrameNode?>(null) }
    var editX by remember { mutableStateOf("") }
    var editY by remember { mutableStateOf("") }
    var editSupport by remember { mutableStateOf(SupportType.Free) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(stringResource(R.string.frame_nodes_count, nodes.size), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            AppButton(
                label = stringResource(R.string.frame_add_node),
                onClick = {
                    editX = ""
                    editY = ""
                    editSupport = SupportType.Free
                    editingNode = null
                    showAddDialog = true
                },
                fillMaxWidth = false,
                icon = Icons.Default.Add,
                containerOverride = FrameActionBlue,
                contentOverride = onColorFor(FrameActionBlue)
            )
        }

        if (nodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(stringResource(R.string.frame_no_nodes), color = Color.Gray)
                    AppText(stringResource(R.string.frame_add_or_template), color = Color.Gray, fontSize = 12.sp)
                }
            }
            return@Column
        }

        // Node list header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE3F2FD))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            AppText(stringResource(R.string.frame_node_id_header), modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            AppText(stringResource(R.string.frame_x_m_header), modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            AppText(stringResource(R.string.frame_y_m_header), modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            AppText(stringResource(R.string.frame_support), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            AppText("", modifier = Modifier.width(60.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(nodes, key = { it.id }) { node ->
                val isConnected = members.any { it.nodeI == node.id || it.nodeJ == node.id }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isConnected) Modifier else Modifier.background(Color(0xFFFFF8E1)))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText("${node.id}", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
                    AppText("${node.x}", modifier = Modifier.width(60.dp))
                    AppText("${node.y}", modifier = Modifier.width(60.dp))
                    AppText(node.support.displayName, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    IconButton(onClick = {
                        editingNode = node
                        editX = node.x.toString()
                        editY = node.y.toString()
                        editSupport = node.support
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Edit, stringResource(R.string.frame_edit), modifier = Modifier.size(18.dp), tint = Color(0xFF1565C0))
                    }
                }
                HorizontalDivider()
            }
        }
    }

    // Add/Edit Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { AppText(if (editingNode != null) stringResource(R.string.frame_edit_node) else stringResource(R.string.frame_add_node_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editX, onValueChange = { editX = it },
                        label = { AppText(stringResource(R.string.frame_x_m)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editY, onValueChange = { editY = it },
                        label = { AppText(stringResource(R.string.frame_y_m)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(stringResource(R.string.frame_support_type), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    for (st in SupportType.entries) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(2.dp)) {
                            RadioButton(
                                selected = editSupport == st,
                                onClick = { editSupport = st }
                            )
                            AppText(st.displayName, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    label = stringResource(R.string.save),
                    fillMaxWidth = false,
                    style = AppButtonStyle.Text,
                    onClick = {
                        val x = editX.toDoubleOrNull() ?: return@AppButton
                        val y = editY.toDoubleOrNull() ?: return@AppButton
                        if (editingNode != null) {
                            viewModel.updateNode(editingNode!!.id, x, y, editSupport)
                        } else {
                            viewModel.addNode(x, y, editSupport)
                        }
                        showAddDialog = false
                    }
                )
            },
            dismissButton = {
                AppButton(
                    label = stringResource(R.string.cancel),
                    onClick = { showAddDialog = false },
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false
                )
            }
        )
    }
}

// ============================================================================
// Tab 2: Members
// ============================================================================
@Composable
fun MembersTab(
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    settings: FrameAnalysisSettings,
    viewModel: FrameAnalysisViewModel
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editNodeI by remember { mutableStateOf("") }
    var editNodeJ by remember { mutableStateOf("") }
    var editMaterial by remember { mutableStateOf(FrameMaterialType.Concrete) }
    var editMemberType by remember { mutableStateOf(FrameMemberType.Beam) }
    var editName by remember { mutableStateOf("") }
    var editWidth by remember { mutableStateOf("250") }
    var editDepth by remember { mutableStateOf("500") }
    var editFcu by remember { mutableStateOf("25") }
    var editFy by remember { mutableStateOf("400") }
    var editSteelSection by remember { mutableStateOf(SteelFrameDesign.AUTO_SECTION) }
    var steelSectionExpanded by remember { mutableStateOf(false) }
    val steelSections = remember { SteelTables.getAllSections() }
    var editingMemberId by remember { mutableStateOf<Int?>(null) }

    fun openMemberEditor(member: FrameMember) {
        editingMemberId = member.id
        editNodeI = member.nodeI.toString()
        editNodeJ = member.nodeJ.toString()
        editMaterial = member.materialType
        editMemberType = member.memberType
        editName = member.name
        val section = member.concreteSection
        editWidth = section?.width?.toString() ?: "250"
        editDepth = section?.depth?.toString() ?: "500"
        editFcu = section?.fcu?.toString() ?: "25"
        editFy = section?.fy?.toString() ?: "400"
        editSteelSection = member.steelSectionName
            ?.takeUnless { SteelFrameDesign.isAutoSection(it) }
            ?: SteelFrameDesign.AUTO_SECTION
        steelSectionExpanded = false
        showAddDialog = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(stringResource(R.string.frame_members_count, members.size), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            AppButton(
                label = stringResource(R.string.frame_add_member),
                onClick = {
                    editNodeI = ""; editNodeJ = ""; editMaterial = FrameMaterialType.Concrete
                    editMemberType = FrameMemberType.Beam; editName = ""
                    editWidth = "250"; editDepth = "500"; editFcu = "25"; editFy = "400"
                    editSteelSection = SteelFrameDesign.AUTO_SECTION; editingMemberId = null
                    steelSectionExpanded = false
                    showAddDialog = true
                },
                fillMaxWidth = false,
                icon = Icons.Default.Add,
                containerOverride = FrameActionBlue,
                contentOverride = onColorFor(FrameActionBlue)
            )
        }

        if (members.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Straighten, null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(stringResource(R.string.frame_no_members), color = Color.Gray)
                }
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(members, key = { it.id }) { member ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (member.materialType == FrameMaterialType.Concrete)
                            Color(0xFFE3F2FD) else Color(0xFFFFF3E0)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            AppText("#${member.id} - ${member.name.ifEmpty { stringResource(R.string.frame_no_name) }}", fontWeight = FontWeight.Bold)
                            AppText(
                                "${stringResource(R.string.frame_member_format, member.nodeI, member.nodeJ)}  |  ${member.memberType.displayName}  |  ${member.materialType.displayName}",
                                fontSize = 11.sp, color = Color.Gray
                            )
                            if (member.materialType == FrameMaterialType.Concrete && member.concreteSection != null) {
                                AppText(
                                    "b=${member.concreteSection!!.width} h=${member.concreteSection!!.depth} f'c=${member.concreteSection!!.fcu}",
                                    fontSize = 10.sp, color = Color(0xFF1565C0)
                                )
                            } else if (member.materialType == FrameMaterialType.Steel) {
                                AppText(
                                    member.steelSectionName
                                        ?.takeUnless { SteelFrameDesign.isAutoSection(it) }
                                        ?: stringResource(R.string.frame_steel_section_auto),
                                    fontSize = 10.sp,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }
                        IconButton(onClick = { openMemberEditor(member) }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.frame_edit), tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { viewModel.removeMember(member.id) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Member Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                editingMemberId = null
                showAddDialog = false
            },
            title = { AppText(if (editingMemberId != null) stringResource(R.string.frame_edit_member) else stringResource(R.string.frame_add_member_title)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        OutlinedTextField(
                            value = editNodeI, onValueChange = { editNodeI = it },
                            label = { AppText(stringResource(R.string.frame_node_i)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = editNodeJ, onValueChange = { editNodeJ = it },
                            label = { AppText(stringResource(R.string.frame_node_j)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(value = editName, onValueChange = { editName = it },
                            label = { AppText(stringResource(R.string.frame_member_name_hint)) }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        AppText(stringResource(R.string.frame_material_type), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row {
                            for (mt in FrameMaterialType.entries) {
                                FilterChip(
                                    selected = editMaterial == mt,
                                    onClick = { editMaterial = mt },
                                    label = { AppText(mt.displayName, fontSize = 12.sp) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }
                    item {
                        AppText(stringResource(R.string.frame_member_type), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row {
                            for (mtype in FrameMemberType.entries) {
                                FilterChip(
                                    selected = editMemberType == mtype,
                                    onClick = { editMemberType = mtype },
                                    label = { AppText(mtype.displayName, fontSize = 12.sp) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }
                    if (editMaterial == FrameMaterialType.Concrete) {
                        item {
                            AppText(stringResource(R.string.frame_concrete_section_props), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = editWidth, onValueChange = { editWidth = it },
                                    label = { AppText(stringResource(R.string.frame_b_mm_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f))
                                OutlinedTextField(value = editDepth, onValueChange = { editDepth = it },
                                    label = { AppText(stringResource(R.string.frame_h_mm_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = editFcu, onValueChange = { editFcu = it },
                                    label = { AppText(stringResource(R.string.frame_fc_mpa_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f))
                                OutlinedTextField(value = editFy, onValueChange = { editFy = it },
                                    label = { AppText(stringResource(R.string.frame_fy_mpa_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        item {
                            val isAutoSection = SteelFrameDesign.isAutoSection(editSteelSection)
                            AppText(stringResource(R.string.frame_steel_section), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Box {
                                AppButton(
                                    label = if (isAutoSection) stringResource(R.string.frame_steel_section_auto) else editSteelSection,
                                    onClick = { steelSectionExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    style = AppButtonStyle.Outline,
                                    fontSize = 12.sp
                                )
                                DropdownMenu(
                                    expanded = steelSectionExpanded,
                                    onDismissRequest = { steelSectionExpanded = false },
                                    modifier = Modifier.heightIn(max = 300.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            AppText(
                                                stringResource(R.string.frame_steel_section_auto),
                                                fontSize = 11.sp
                                            )
                                        },
                                        onClick = {
                                            editSteelSection = SteelFrameDesign.AUTO_SECTION
                                            steelSectionExpanded = false
                                        }
                                    )
                                    for (section in steelSections) {
                                        DropdownMenuItem(
                                            text = {
                                                AppText(
                                                    "${section.name}  (${section.depth.toInt()}×${section.width.toInt()})",
                                                    fontSize = 11.sp
                                                )
                                            },
                                            onClick = {
                                                editSteelSection = section.name
                                                steelSectionExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            val isAutoSection = SteelFrameDesign.isAutoSection(editSteelSection)
                            if (isAutoSection) {
                                AppText(
                                    stringResource(R.string.frame_steel_section_auto_hint),
                                    fontSize = 9.sp,
                                    color = Color.Gray
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            SteelSectionDrawing(
                                sectionName = if (isAutoSection) "IPE 300" else editSteelSection,
                                memberLengthMm = run {
                                    val ni = nodes.find { it.id == editNodeI.toIntOrNull() }
                                    val nj = nodes.find { it.id == editNodeJ.toIntOrNull() }
                                    if (ni != null && nj != null) {
                                        (hypot(nj.x - ni.x, nj.y - ni.y) * 1000.0)
                                            .takeIf { it > 1.0 } ?: 6000.0
                                    } else 6000.0
                                },
                                memberType = editMemberType,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    label = stringResource(R.string.save),
                    fillMaxWidth = false,
                    style = AppButtonStyle.Text,
                    onClick = {
                        val nI = editNodeI.toIntOrNull() ?: return@AppButton
                        val nJ = editNodeJ.toIntOrNull() ?: return@AppButton
                        val concreteSec = if (editMaterial == FrameMaterialType.Concrete)
                            ConcreteSectionProps(
                                width = editWidth.toDoubleOrNull() ?: 250.0,
                                depth = editDepth.toDoubleOrNull() ?: 500.0,
                                fcu = editFcu.toDoubleOrNull() ?: 25.0,
                                fy = editFy.toDoubleOrNull() ?: 400.0
                            ) else null
                        val steelSectionChoice = if (
                            editMaterial == FrameMaterialType.Steel &&
                            !SteelFrameDesign.isAutoSection(editSteelSection)
                        ) {
                            editSteelSection.trim()
                        } else null
                        if (editingMemberId != null) {
                            val existing = members.find { it.id == editingMemberId }
                            if (existing != null) {
                                viewModel.updateMember(existing.copy(
                                    nodeI = nI, nodeJ = nJ, materialType = editMaterial,
                                    memberType = editMemberType, concreteSection = concreteSec,
                                    steelSectionName = steelSectionChoice,
                                    name = editName
                                ))
                            }
                        } else {
                            viewModel.addMember(nI, nJ, editMaterial, editMemberType, concreteSec, steelSectionChoice, editName)
                        }
                        editingMemberId = null
                        showAddDialog = false
                    }
                )
            },
            dismissButton = {
                AppButton(
                    label = stringResource(R.string.cancel),
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false,
                    onClick = {
                        editingMemberId = null
                        showAddDialog = false
                    }
                )
            }
        )
    }
}

// ============================================================================
// Tab 3: Loads
// ============================================================================
@Composable
fun LoadsTab(
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    nodalLoads: List<NodalLoad>,
    memberLoads: List<MemberLoad>,
    viewModel: FrameAnalysisViewModel
) {
    var showNodalDialog by remember { mutableStateOf(false) }
    var showMemberDialog by remember { mutableStateOf(false) }
    var loadTabIndex by remember { mutableIntStateOf(0) }

    // Nodal load dialog state
    var nlNodeId by remember { mutableStateOf("") }
    var nlFx by remember { mutableStateOf("0") }
    var nlFy by remember { mutableStateOf("-10") }
    var nlMz by remember { mutableStateOf("0") }

    // Member load dialog state
    var mlMemberId by remember { mutableStateOf("") }
    var mlType by remember { mutableStateOf(MemberLoadType.UDL) }
    var mlValue by remember { mutableStateOf("20") }
    var mlPosition by remember { mutableStateOf("0") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab for Nodal vs Member loads
        TabRow(
            selectedTabIndex = loadTabIndex,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Tab(selected = loadTabIndex == 0, onClick = { loadTabIndex = 0 }, text = { AppText(stringResource(R.string.frame_nodal_loads_tab)) })
            Tab(selected = loadTabIndex == 1, onClick = { loadTabIndex = 1 }, text = { AppText(stringResource(R.string.frame_member_loads_tab)) })
        }

        if (loadTabIndex == 0) {
            // Nodal loads
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                AppText(stringResource(R.string.frame_nodal_loads), fontWeight = FontWeight.Bold)
                AppButton(
                    label = stringResource(R.string.add),
                    onClick = { showNodalDialog = true },
                    fillMaxWidth = false,
                    icon = Icons.Default.Add,
                    containerOverride = FrameActionBlue,
                    contentOverride = onColorFor(FrameActionBlue)
                )
            }
            if (nodalLoads.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppText(stringResource(R.string.frame_no_nodal_loads), color = Color.Gray) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(nodalLoads) { index, load ->
                        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    AppText(stringResource(R.string.frame_node_label, load.nodeId), fontWeight = FontWeight.Bold)
                                    AppText("Fx=${load.fx}, Fy=${load.fy} kN, M=${load.mz} kN.m", fontSize = 11.sp, color = Color.Gray)
                                }
                                IconButton(onClick = { viewModel.removeNodalLoad(index) }) {
                                    Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Member loads
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                AppText(stringResource(R.string.frame_member_loads), fontWeight = FontWeight.Bold)
                AppButton(
                    label = stringResource(R.string.add),
                    onClick = { showMemberDialog = true },
                    fillMaxWidth = false,
                    icon = Icons.Default.Add,
                    containerOverride = FrameActionBlue,
                    contentOverride = onColorFor(FrameActionBlue)
                )
            }
            if (memberLoads.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppText(stringResource(R.string.frame_no_member_loads), color = Color.Gray) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(memberLoads) { index, load ->
                        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    val mName = members.find { it.id == load.memberId }?.name ?: stringResource(R.string.frame_tab_members, load.memberId)
                                    AppText(stringResource(R.string.frame_on_member, mName, load.memberId), fontWeight = FontWeight.Bold)
                                    AppText("${load.loadType.displayName}: ${load.value} kN/m", fontSize = 11.sp, color = Color.Gray)
                                }
                                IconButton(onClick = { viewModel.removeMemberLoad(index) }) {
                                    Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Nodal Load Dialog
    if (showNodalDialog) {
        AlertDialog(
            onDismissRequest = { showNodalDialog = false },
            title = { AppText(stringResource(R.string.frame_load_nodal_title)) },
            text = {
                Column {
                    OutlinedTextField(value = nlNodeId, onValueChange = { nlNodeId = it },
                        label = { AppText(stringResource(R.string.frame_load_node_id)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = nlFx, onValueChange = { nlFx = it },
                        label = { AppText(stringResource(R.string.frame_load_fx)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = nlFy, onValueChange = { nlFy = it },
                        label = { AppText(stringResource(R.string.frame_load_fy)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = nlMz, onValueChange = { nlMz = it },
                        label = { AppText(stringResource(R.string.frame_load_mz)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                AppButton(
                    label = stringResource(R.string.add),
                    fillMaxWidth = false,
                    style = AppButtonStyle.Text,
                    onClick = {
                        val nid = nlNodeId.toIntOrNull() ?: return@AppButton
                        viewModel.addNodalLoad(nid, nlFx.toDoubleOrNull() ?: 0.0, nlFy.toDoubleOrNull() ?: 0.0, nlMz.toDoubleOrNull() ?: 0.0, "DL")
                        showNodalDialog = false
                    }
                )
            },
            dismissButton = {
                AppButton(
                    label = stringResource(R.string.cancel),
                    onClick = { showNodalDialog = false },
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false
                )
            }
        )
    }

    // Member Load Dialog
    if (showMemberDialog) {
        AlertDialog(
            onDismissRequest = { showMemberDialog = false },
            title = { AppText(stringResource(R.string.frame_load_member_title)) },
            text = {
                Column {
                    OutlinedTextField(value = mlMemberId, onValueChange = { mlMemberId = it },
                        label = { AppText(stringResource(R.string.frame_load_member_id)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    AppText(stringResource(R.string.frame_load_type), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row {
                        for (lt in MemberLoadType.entries) {
                            FilterChip(selected = mlType == lt, onClick = { mlType = lt },
                                label = { AppText(lt.displayName, fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = mlValue, onValueChange = { mlValue = it },
                        label = { AppText(stringResource(R.string.frame_load_value)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    if (mlType != MemberLoadType.UDL) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(value = mlPosition, onValueChange = { mlPosition = it },
                            label = { AppText(stringResource(R.string.frame_load_position)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                AppButton(
                    label = stringResource(R.string.add),
                    fillMaxWidth = false,
                    style = AppButtonStyle.Text,
                    onClick = {
                        val mid = mlMemberId.toIntOrNull() ?: return@AppButton
                        viewModel.addMemberLoad(mid, mlType, mlValue.toDoubleOrNull() ?: 0.0, mlPosition.toDoubleOrNull() ?: 0.0, "DL")
                        showMemberDialog = false
                    }
                )
            },
            dismissButton = {
                AppButton(
                    label = stringResource(R.string.cancel),
                    onClick = { showMemberDialog = false },
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false
                )
            }
        )
    }
}

// ============================================================================
// Tab 4: Results
// ============================================================================
fun frameMemberLengthMm(memberId: Int, nodes: List<FrameNode>, members: List<FrameMember>): Double {
    val member = members.find { it.id == memberId } ?: return 6000.0
    val nodeI = nodes.find { it.id == member.nodeI } ?: return 6000.0
    val nodeJ = nodes.find { it.id == member.nodeJ } ?: return 6000.0
    val lengthM = hypot(nodeJ.x - nodeI.x, nodeJ.y - nodeI.y)
    return if (lengthM > 0.0) lengthM * 1000.0 else 6000.0
}

@Composable
fun ResultsTab(
    result: FrameAnalysisResult?,
    concreteResults: List<ConcreteMemberDesignResult>,
    steelResults: List<SteelMemberDesignResult>,
    selectedMemberId: Int?,
    viewModel: FrameAnalysisViewModel
) {
    val nodes by viewModel.nodes.observeAsState(emptyList())
    val members by viewModel.members.observeAsState(emptyList())
    ResultsTab(
        result = result,
        concreteResults = concreteResults,
        steelResults = steelResults,
        selectedMemberId = selectedMemberId,
        nodes = nodes,
        members = members
    )
}

@Composable
fun ResultsTab(
    result: FrameAnalysisResult?,
    concreteResults: List<ConcreteMemberDesignResult>,
    steelResults: List<SteelMemberDesignResult>,
    selectedMemberId: Int?,
    nodes: List<FrameNode>,
    members: List<FrameMember>
) {
    if (result == null || !result.hasResults) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Analytics, null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                Spacer(Modifier.height(8.dp))
                AppText(stringResource(R.string.frame_press_solve), color = Color.Gray, fontSize = 14.sp)
            }
        }
        return
    }

    var resultSubTab by remember { mutableIntStateOf(0) }
    val subTabs = buildList {
        add(stringResource(R.string.frame_result_displacements))
        add(stringResource(R.string.frame_result_reactions))
        if (concreteResults.isNotEmpty()) add(stringResource(R.string.frame_result_concrete_design))
        if (steelResults.isNotEmpty()) add(stringResource(R.string.frame_result_steel_design))
    }
    val concreteTabIndex = if (concreteResults.isNotEmpty()) 2 else -1
    val steelTabIndex = if (steelResults.isNotEmpty()) {
        if (concreteResults.isNotEmpty()) 3 else 2
    } else {
        -1
    }
    val activeSubTab = resultSubTab.coerceIn(0, (subTabs.size - 1).coerceAtLeast(0))

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = activeSubTab) {
            for ((i, title) in subTabs.withIndex()) {
                Tab(selected = activeSubTab == i, onClick = { resultSubTab = i }, text = { AppText(title, fontSize = 12.sp) })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
        // === Displacements ===
        if (activeSubTab == 0) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        AppText(stringResource(R.string.frame_node_displacements), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        for (nr in result.nodeResults.filter { it.nodeId in (nodes.map { it.id }) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                AppText(stringResource(R.string.frame_node_label_format, nr.nodeId), modifier = Modifier.width(50.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                AppText(stringResource(R.string.frame_disp_dx, nr.dx * 1000), modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.End)
                                AppText(stringResource(R.string.frame_disp_dy, nr.dy * 1000), modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.End)
                                AppText(stringResource(R.string.frame_disp_theta, nr.rz), modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.End)
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }

        // === Reactions ===
        if (activeSubTab == 1) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        AppText(stringResource(R.string.frame_reactions), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        for (nr in result.nodeResults.filter { abs(it.reactionFx) > 0.01 || abs(it.reactionFy) > 0.01 || abs(it.reactionMz) > 0.01 }) {
                            AppText(
                                stringResource(R.string.frame_reaction_format, nr.nodeId, nr.reactionFx, nr.reactionFy, nr.reactionMz),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Member end forces
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        AppText(stringResource(R.string.frame_internal_forces), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        for (mf in result.memberEndForces) {
                            val mname = members.find { it.id == mf.memberId }?.name ?: stringResource(R.string.frame_member_label_format, mf.memberId)
                            AppText(stringResource(R.string.frame_member_force_header, mname, mf.memberId), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            AppText(stringResource(R.string.frame_member_end_i, mf.fi_x, mf.fi_y, mf.mi_z), fontSize = 10.sp, color = Color.Gray)
                            AppText(stringResource(R.string.frame_member_end_j, mf.fj_x, mf.fj_y, mf.mj_z), fontSize = 10.sp, color = Color.Gray)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        // === Concrete Design Results ===
        if (activeSubTab == concreteTabIndex && concreteResults.isNotEmpty()) {
            items(concreteResults) { cr ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (cr.isSafe) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (cr.isSafe) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (cr.isSafe) Color(0xFF4CAF50) else Color(0xFFF44336),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        AppText(stringResource(R.string.frame_result_member_id, cr.memberName, cr.memberId), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        AppText(stringResource(R.string.frame_concrete_section_info, cr.section.width, cr.section.depth, cr.section.fcu), fontSize = 11.sp, color = Color.Gray)
                        Spacer(Modifier.height(2.dp))
                        AppText(stringResource(R.string.frame_max_forces, cr.maxMoment, cr.maxShear, cr.axialForce), fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        AppText(stringResource(R.string.frame_reinforcement_required, cr.asRequired), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                        if (cr.numBarsBot > 0) {
                            AppText(stringResource(R.string.frame_reinforcement_bottom, cr.numBarsBot, cr.barDia.toInt(), cr.asBot), fontSize = 11.sp)
                        }
                        if (cr.numBarsTop > 0) {
                            AppText(stringResource(R.string.frame_reinforcement_top, cr.numBarsTop, cr.barDia.toInt(), cr.asTop), fontSize = 11.sp)
                        }
                        if (cr.stirrupDia > 0) {
                            AppText(stringResource(R.string.frame_stirrups, cr.stirrupDia.toInt(), cr.stirrupSpacing.toInt()), fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        FrameRebarSectionDrawing(
                            result = cr,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        Spacer(Modifier.height(4.dp))

                        // Utilization bars
                        Row {
                            AppText(stringResource(R.string.frame_bending), fontSize = 11.sp)
                            UtilizationBar(cr.momentUtilization, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(2.dp))
                        Row {
                            AppText(stringResource(R.string.frame_shear_label), fontSize = 11.sp)
                            UtilizationBar(cr.shearUtilization, Modifier.weight(1f))
                        }

                        if (cr.warnings.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            for (w in cr.warnings) {
                                AppText("⚠ $w", fontSize = 10.sp, color = Color(0xFFF44336))
                            }
                        }
                    }
                }
            }
        }

        // === Steel Design Results ===
        if (activeSubTab == steelTabIndex && steelResults.isNotEmpty()) {
            items(steelResults) { sr ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (sr.isSafe) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (sr.isSafe) Icons.Default.CheckCircle else Icons.Default.Warning,
                                null,
                                tint = if (sr.isSafe) Color(0xFF4CAF50) else Color(0xFFF44336),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            AppText(stringResource(R.string.frame_result_member_id, sr.memberName, sr.memberId), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        AppText(stringResource(R.string.frame_selected_section, sr.selectedSection), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        AppText(stringResource(R.string.frame_steel_section_info, sr.sectionWeight, sr.sectionIx, sr.sectionSx), fontSize = 11.sp, color = Color.Gray)
                        Spacer(Modifier.height(2.dp))
                        AppText(stringResource(R.string.frame_max_forces, sr.maxMoment, sr.maxShear, sr.axialForce), fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        FrameSteelSectionDrawing(
                            result = sr,
                            memberLengthMm = frameMemberLengthMm(sr.memberId, nodes, members),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                        )
                        Spacer(Modifier.height(4.dp))

                        Row { AppText(stringResource(R.string.frame_bending), fontSize = 11.sp); UtilizationBar(sr.flexuralUtilization, Modifier.weight(1f)) }
                        Spacer(Modifier.height(2.dp))
                        Row { AppText(stringResource(R.string.frame_shear_label), fontSize = 11.sp); UtilizationBar(sr.shearUtilization, Modifier.weight(1f)) }
                        Spacer(Modifier.height(2.dp))
                        Row { AppText(stringResource(R.string.frame_axial), fontSize = 11.sp); UtilizationBar(sr.axialUtilization, Modifier.weight(1f)) }
                        Spacer(Modifier.height(2.dp))
                        Row { AppText(stringResource(R.string.frame_combined), fontSize = 11.sp); UtilizationBar(sr.combinedUtilization, Modifier.weight(1f)) }

                        if (sr.warnings.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            for (w in sr.warnings) {
                                AppText("⚠ $w", fontSize = 10.sp, color = Color(0xFFF44336))
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun UtilizationBar(ratio: Double, modifier: Modifier = Modifier) {
    val clampedRatio = ratio.coerceIn(0.0, 1.5)
    val color = when {
        clampedRatio <= 0.7 -> Color(0xFF4CAF50)
        clampedRatio <= 1.0 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    Row(modifier = modifier.height(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { clampedRatio.toFloat() / 1.5f },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = Color.LightGray
        )
        Spacer(modifier = Modifier.width(8.dp))
        AppText(
            "${String.format(java.util.Locale.ROOT, "%.0f", ratio * 100)}%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ResultsTabPreview() {
    CivilEngineerTheme {
        ResultsTab(
            result = FrameAnalysisResult(
                nodeResults = listOf(
                    NodeResult(nodeId = 1, dx = 0.001, dy = -0.002, rz = 0.01, reactionFx = 10.0, reactionFy = 50.0, reactionMz = 25.0)
                ),
                memberEndForces = listOf(
                    MemberEndForces(memberId = 1, fi_x = 10.0, fi_y = 20.0, mi_z = 30.0, fj_x = 10.0, fj_y = 20.0, mj_z = 30.0)
                ),
                isSolved = true
            ),
            concreteResults = listOf(
                ConcreteMemberDesignResult(
                    memberId = 1,
                    memberName = "Beam 1",
                    memberType = FrameMemberType.Beam,
                    section = ConcreteSectionProps(width = 250.0, depth = 500.0),
                    maxMoment = 45.0,
                    maxShear = 25.0,
                    axialForce = 10.0,
                    asRequired = 800.0,
                    asProvided = 1000.0,
                    barDia = 16.0,
                    numBarsTop = 2,
                    numBarsBot = 3,
                    asTop = 402.12,
                    asBot = 603.18,
                    stirrupDia = 8.0,
                    stirrupSpacing = 200.0,
                    momentUtilization = 0.85,
                    shearUtilization = 0.60,
                    isSafe = true
                )
            ),
            steelResults = emptyList(),
            selectedMemberId = 1,
            nodes = listOf(
                FrameNode(id = 1, x = 0.0, y = 0.0, support = SupportType.Fixed),
                FrameNode(id = 2, x = 6.0, y = 0.0, support = SupportType.Fixed)
            ),
            members = listOf(
                FrameMember(id = 1, nodeI = 1, nodeJ = 2, name = "Beam 1")
            )
        )
    }
}
