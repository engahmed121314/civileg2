package com.civileg.app.ui.compose.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.domain.entities.*
import com.civileg.app.viewmodel.DiagramType
import com.civileg.app.viewmodel.FrameAnalysisViewModel
import com.civileg.app.utils.FrameAnalysisPdfExporter
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameAnalysisScreen(
    viewModel: FrameAnalysisViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val nodes by viewModel.nodes.observeAsState(emptyList())
    val members by viewModel.members.observeAsState(emptyList())
    val nodalLoads by viewModel.nodalLoads.observeAsState(emptyList())
    val memberLoads by viewModel.memberLoads.observeAsState(emptyList())
    val settings by viewModel.settings.observeAsState(FrameAnalysisSettings())
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMsg by viewModel.errorMessage.observeAsState()
    val diagramType by viewModel.activeDiagramType.observeAsState(DiagramType.BMD)
    val selectedMemberId by viewModel.selectedMemberId.observeAsState()
    val concreteResults by viewModel.concreteResults.observeAsState(emptyList())
    val steelResults by viewModel.steelResults.observeAsState(emptyList())
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.frame_tab_drawing),
        stringResource(R.string.frame_tab_nodes),
        stringResource(R.string.frame_tab_members),
        stringResource(R.string.frame_tab_loads),
        stringResource(R.string.frame_tab_results)
    )

    // Show errors
    LaunchedEffect(errorMsg) {
        errorMsg?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.frame_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1565C0),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                actions = {
                    // Template buttons
                    IconButton(onClick = {
                        var span = 6.0; var h = 4.0; var udl = 20.0
                        viewModel.loadSimplePortalFrame(span, h, udl)
                    }) {
                        Icon(Icons.Default.ViewInAr, stringResource(R.string.frame_template_simple), tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.clearAll() }) {
                        Icon(Icons.Default.DeleteSweep, stringResource(R.string.frame_clear_all), tint = Color.White)
                    }
                    // PDF Export
                    if (result?.hasResults == true) {
                        IconButton(onClick = {
                            val inputs = viewModel.getStoredInputs()
                            try {
                                val file = FrameAnalysisPdfExporter.generateFrameAnalysisPdf(
                                    context, inputs.nodes, inputs.members, inputs.nodalLoads,
                                    inputs.memberLoads, inputs.settings, inputs.result
                                )
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, "${context.packageName}.provider", file
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Frame Analysis Report")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, stringResource(R.string.frame_share_report)))
                            } catch (e: Exception) {
                                Toast.makeText(context, stringResource(R.string.frame_pdf_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Icon(Icons.Default.PictureAsPdf, stringResource(R.string.frame_export_pdf), tint = Color.White)
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Diagram type selector (when solved)
            if (result?.hasResults == true) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (type in DiagramType.entries) {
                            val isSelected = diagramType == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setDiagramType(type) },
                                label = { Text(type.displayNameAr, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (type) {
                                        DiagramType.BMD -> Color(0xFF2196F3)
                                        DiagramType.SFD -> Color(0xFF4CAF50)
                                        DiagramType.AFD -> Color(0xFFFF9800)
                                    },
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1565C0).copy(alpha = 0.1f),
                contentColor = Color(0xFF1565C0)
            ) {
                for ((index, title) in tabs.withIndex()) {
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 13.sp) }
                    )
                }
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> DrawingTab(
                        modifier = Modifier.fillMaxSize(),
                        nodes, members, memberLoads, nodalLoads, result, diagramType, selectedMemberId, viewModel
                    )
                    1 -> NodesTab(nodes, members, viewModel)
                    2 -> MembersTab(nodes, members, settings, viewModel)
                    3 -> LoadsTab(nodes, members, nodalLoads, memberLoads, viewModel)
                    4 -> ResultsTab(result, concreteResults, steelResults, selectedMemberId, viewModel)
                }
            }
        }
    }
}

// ============================================================================
// Tab 0: Drawing
// ============================================================================
@Composable
private fun DrawingTab(
    modifier: Modifier = Modifier,
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    memberLoads: List<MemberLoad>,
    nodalLoads: List<NodalLoad>,
    result: FrameAnalysisResult?,
    diagramType: DiagramType,
    selectedMemberId: Int?,
    viewModel: FrameAnalysisViewModel
) {
    Box(modifier = modifier.fillMaxSize()) {
        FrameDrawingCanvas(
            nodes = nodes,
            members = members,
            memberLoads = memberLoads,
            nodalLoads = nodalLoads,
            result = result,
            diagramType = diagramType,
            selectedMemberId = selectedMemberId,
            onMemberTap = { viewModel.setSelectedMember(it) }
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
                    Text(stringResource(R.string.frame_solve), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                Text("${stringResource(R.string.frame_tab_nodes)}: ${nodes.size}  |  ${stringResource(R.string.frame_tab_members)}: ${members.size}", fontSize = 11.sp, color = Color.Gray)
                if (result?.hasResults == true) {
                    Text(
                        "${stringResource(R.string.safe)} ✓  |  ${stringResource(R.string.frame_tab_loads)}: ${memberLoads.size + nodalLoads.size}",
                        fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ============================================================================
// Tab 1: Nodes
// ============================================================================
@Composable
private fun NodesTab(
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
            Text(stringResource(R.string.frame_nodes_count, nodes.size), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(
                onClick = {
                    editX = ""
                    editY = ""
                    editSupport = SupportType.Free
                    editingNode = null
                    showAddDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.frame_add_node))
            }
        }

        if (nodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.frame_no_nodes), color = Color.Gray)
                    Text(stringResource(R.string.frame_add_or_template), color = Color.Gray, fontSize = 12.sp)
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
            Text("ID", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("X (m)", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Y (m)", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(stringResource(R.string.frame_support), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("", modifier = Modifier.width(60.dp))
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
                    Text("${node.id}", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
                    Text("${node.x}", modifier = Modifier.width(60.dp))
                    Text("${node.y}", modifier = Modifier.width(60.dp))
                    Text(node.support.displayNameAr, modifier = Modifier.weight(1f), fontSize = 12.sp)
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
            title = { Text(if (editingNode != null) stringResource(R.string.frame_edit_node) else stringResource(R.string.frame_add_node_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editX, onValueChange = { editX = it },
                        label = { Text(stringResource(R.string.frame_x_m)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editY, onValueChange = { editY = it },
                        label = { Text(stringResource(R.string.frame_y_m)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.frame_support_type), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    for (st in SupportType.entries) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(2.dp)) {
                            RadioButton(
                                selected = editSupport == st,
                                onClick = { editSupport = st }
                            )
                            Text(st.displayNameAr, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val x = editX.toDoubleOrNull() ?: return@TextButton
                    val y = editY.toDoubleOrNull() ?: return@TextButton
                    if (editingNode != null) {
                        viewModel.updateNode(editingNode!!.id, x, y, editSupport)
                    } else {
                        viewModel.addNode(x, y, editSupport)
                    }
                    showAddDialog = false
                }) { Text(stringResource(R.string.save), color = Color(0xFF1565C0)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ============================================================================
// Tab 2: Members
// ============================================================================
@Composable
private fun MembersTab(
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
    var editSteelSection by remember { mutableStateOf("IPE 300") }
    var editingMemberId by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.frame_members_count, members.size), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(
                onClick = {
                    editNodeI = ""; editNodeJ = ""; editMaterial = FrameMaterialType.Concrete
                    editMemberType = FrameMemberType.Beam; editName = ""
                    editWidth = "250"; editDepth = "500"; editFcu = "25"; editFy = "400"
                    editSteelSection = "IPE 300"; editingMemberId = null
                    showAddDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.frame_add_member))
            }
        }

        if (members.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Straighten, null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.frame_no_members), color = Color.Gray)
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
                            Text("#${member.id} - ${member.name.ifEmpty { stringResource(R.string.frame_no_name) }}", fontWeight = FontWeight.Bold)
                            Text(
                                "عقدة ${member.nodeI} ← عقدة ${member.nodeJ}  |  ${member.memberType.displayNameAr}  |  ${member.materialType.displayNameAr}",
                                fontSize = 11.sp, color = Color.Gray
                            )
                            if (member.materialType == FrameMaterialType.Concrete && member.concreteSection != null) {
                                Text(
                                    "b=${member.concreteSection!!.width} h=${member.concreteSection!!.depth} f'c=${member.concreteSection!!.fcu}",
                                    fontSize = 10.sp, color = Color(0xFF1565C0)
                                )
                            } else if (member.materialType == FrameMaterialType.Steel) {
                                Text(member.steelSectionName ?: "IPE 300", fontSize = 10.sp, color = Color(0xFFE65100))
                            }
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
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (editingMemberId != null) stringResource(R.string.frame_edit_member) else stringResource(R.string.frame_add_member_title)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        OutlinedTextField(
                            value = editNodeI, onValueChange = { editNodeI = it },
                            label = { Text(stringResource(R.string.frame_node_i)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = editNodeJ, onValueChange = { editNodeJ = it },
                            label = { Text(stringResource(R.string.frame_node_j)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(value = editName, onValueChange = { editName = it },
                            label = { Text(stringResource(R.string.frame_member_name_hint)) }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        Text(stringResource(R.string.frame_material_type), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row {
                            for (mt in FrameMaterialType.entries) {
                                FilterChip(
                                    selected = editMaterial == mt,
                                    onClick = { editMaterial = mt },
                                    label = { Text(mt.displayNameAr, fontSize = 12.sp) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }
                    item {
                        Text(stringResource(R.string.frame_member_type), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row {
                            for (mtype in FrameMemberType.entries) {
                                FilterChip(
                                    selected = editMemberType == mtype,
                                    onClick = { editMemberType = mtype },
                                    label = { Text(mtype.displayNameAr, fontSize = 12.sp) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }
                    if (editMaterial == FrameMaterialType.Concrete) {
                        item {
                            Text(stringResource(R.string.frame_concrete_section_props), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = editWidth, onValueChange = { editWidth = it },
                                    label = { Text("b (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f))
                                OutlinedTextField(value = editDepth, onValueChange = { editDepth = it },
                                    label = { Text("h (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = editFcu, onValueChange = { editFcu = it },
                                    label = { Text("f'c (MPa)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f))
                                OutlinedTextField(value = editFy, onValueChange = { editFy = it },
                                    label = { Text("fy (MPa)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        item {
                            Text(stringResource(R.string.frame_steel_section), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            OutlinedTextField(value = editSteelSection, onValueChange = { editSteelSection = it },
                                label = { Text(stringResource(R.string.frame_section_name_hint)) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val nI = editNodeI.toIntOrNull() ?: return@TextButton
                    val nJ = editNodeJ.toIntOrNull() ?: return@TextButton
                    val concreteSec = if (editMaterial == FrameMaterialType.Concrete)
                        ConcreteSectionProps(
                            width = editWidth.toDoubleOrNull() ?: 250.0,
                            depth = editDepth.toDoubleOrNull() ?: 500.0,
                            fcu = editFcu.toDoubleOrNull() ?: 25.0,
                            fy = editFy.toDoubleOrNull() ?: 400.0
                        ) else null
                    if (editingMemberId != null) {
                        val existing = members.find { it.id == editingMemberId }
                        if (existing != null) {
                            viewModel.updateMember(existing.copy(
                                nodeI = nI, nodeJ = nJ, materialType = editMaterial,
                                memberType = editMemberType, concreteSection = concreteSec,
                                steelSectionName = if (editMaterial == FrameMaterialType.Steel) editSteelSection else null,
                                name = editName
                            ))
                        }
                    } else {
                        viewModel.addMember(nI, nJ, editMaterial, editMemberType, concreteSec, editSteelSection, editName)
                    }
                    showAddDialog = false
                }) { Text(stringResource(R.string.save), color = Color(0xFF1565C0)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ============================================================================
// Tab 3: Loads
// ============================================================================
@Composable
private fun LoadsTab(
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
            Tab(selected = loadTabIndex == 0, onClick = { loadTabIndex = 0 }, text = { Text(stringResource(R.string.frame_nodal_loads_tab)) })
            Tab(selected = loadTabIndex == 1, onClick = { loadTabIndex = 1 }, text = { Text(stringResource(R.string.frame_member_loads_tab)) })
        }

        if (loadTabIndex == 0) {
            // Nodal loads
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.frame_nodal_loads), fontWeight = FontWeight.Bold)
                Button(onClick = { showNodalDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Text(stringResource(R.string.add))
                }
            }
            if (nodalLoads.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.frame_no_nodal_loads), color = Color.Gray) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(nodalLoads) { index, load ->
                        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("عقدة ${load.nodeId}", fontWeight = FontWeight.Bold)
                                    Text("Fx=${load.fx}, Fy=${load.fy} kN, M=${load.mz} kN.m", fontSize = 11.sp, color = Color.Gray)
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
                Text(stringResource(R.string.frame_member_loads), fontWeight = FontWeight.Bold)
                Button(onClick = { showMemberDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Text(stringResource(R.string.add))
                }
            }
            if (memberLoads.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.frame_no_member_loads), color = Color.Gray) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(memberLoads) { index, load ->
                        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    val mName = members.find { it.id == load.memberId }?.name ?: "عضو ${load.memberId}"
                                    Text("على: $mName (#${load.memberId})", fontWeight = FontWeight.Bold)
                                    Text("${load.loadType.displayNameAr}: ${load.value} kN/m", fontSize = 11.sp, color = Color.Gray)
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
            title = { Text("إضافة حمولة عقدية") },
            text = {
                Column {
                    OutlinedTextField(value = nlNodeId, onValueChange = { nlNodeId = it },
                        label = { Text("رقم العقدة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = nlFx, onValueChange = { nlFx = it },
                        label = { Text("Fx - أفقي (kN)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = nlFy, onValueChange = { nlFy = it },
                        label = { Text("Fy - رأسي (kN) - موجب لأعلى") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = nlMz, onValueChange = { nlMz = it },
                        label = { Text("Mz - عزم (kN.m)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val nid = nlNodeId.toIntOrNull() ?: return@TextButton
                    viewModel.addNodalLoad(nid, nlFx.toDoubleOrNull() ?: 0.0, nlFy.toDoubleOrNull() ?: 0.0, nlMz.toDoubleOrNull() ?: 0.0, "DL")
                    showNodalDialog = false
                }) { Text("إضافة", color = Color(0xFF1565C0)) }
            },
            dismissButton = { TextButton(onClick = { showNodalDialog = false }) { Text("إلغاء") } }
        )
    }

    // Member Load Dialog
    if (showMemberDialog) {
        AlertDialog(
            onDismissRequest = { showMemberDialog = false },
            title = { Text("إضافة حمولة على عضو") },
            text = {
                Column {
                    OutlinedTextField(value = mlMemberId, onValueChange = { mlMemberId = it },
                        label = { Text("رقم العضو") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text("نوع الحمولة:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row {
                        for (lt in MemberLoadType.entries) {
                            FilterChip(selected = mlType == lt, onClick = { mlType = lt },
                                label = { Text(lt.displayNameAr, fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = mlValue, onValueChange = { mlValue = it },
                        label = { Text("قيمة الحمولة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    if (mlType != MemberLoadType.UDL) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(value = mlPosition, onValueChange = { mlPosition = it },
                            label = { Text("الموقع من بداية العضو (م)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val mid = mlMemberId.toIntOrNull() ?: return@TextButton
                    viewModel.addMemberLoad(mid, mlType, mlValue.toDoubleOrNull() ?: 0.0, mlPosition.toDoubleOrNull() ?: 0.0, "DL")
                    showMemberDialog = false
                }) { Text("إضافة", color = Color(0xFF1565C0)) }
            },
            dismissButton = { TextButton(onClick = { showMemberDialog = false }) { Text("إلغاء") } }
        )
    }
}

// ============================================================================
// Tab 4: Results
// ============================================================================
@Composable
private fun ResultsTab(
    result: FrameAnalysisResult?,
    concreteResults: List<ConcreteMemberDesignResult>,
    steelResults: List<SteelMemberDesignResult>,
    selectedMemberId: Int?,
    viewModel: FrameAnalysisViewModel
) {
    if (result == null || !result.hasResults) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Analytics, null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.frame_press_solve), color = Color.Gray, fontSize = 14.sp)
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

    TabRow(selectedTabIndex = resultSubTab) {
        for ((i, title) in subTabs.withIndex()) {
            Tab(selected = resultSubTab == i, onClick = { resultSubTab = i }, text = { Text(title, fontSize = 12.sp) })
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // === Displacements ===
        if (resultSubTab == 0) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.frame_node_displacements), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        for (nr in result.nodeResults.filter { it.nodeId in (viewModel.nodes.value?.map { it.id } ?: emptyList()) }) {
                            Text(
                                "عقدة ${nr.nodeId}: dx=${String.format("%.4f", nr.dx * 1000)} mm, dy=${String.format("%.4f", nr.dy * 1000)} mm, θ=${String.format("%.6f", nr.rz)} rad",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // === Reactions ===
        if (resultSubTab == 1) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.frame_reactions), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        for (nr in result.nodeResults.filter { abs(it.reactionFx) > 0.01 || abs(it.reactionFy) > 0.01 || abs(it.reactionMz) > 0.01 }) {
                            Text(
                                "عقدة ${nr.nodeId}: Rx=${String.format("%.2f", nr.reactionFx)} kN, Ry=${String.format("%.2f", nr.reactionFy)} kN, M=${String.format("%.2f", nr.reactionMz)} kN.m",
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
                        Text(stringResource(R.string.frame_internal_forces), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        for (mf in result.memberEndForces) {
                            val mname = viewModel.members.value?.find { it.id == mf.memberId }?.name ?: "عضو ${mf.memberId}"
                            Text("عضو $mname (#${mf.memberId}):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("  I: N=${String.format("%.1f", mf.fi_x)} kN, V=${String.format("%.1f", mf.fi_y)} kN, M=${String.format("%.1f", mf.mi_z)} kN.m", fontSize = 10.sp, color = Color.Gray)
                            Text("  J: N=${String.format("%.1f", mf.fj_x)} kN, V=${String.format("%.1f", mf.fj_y)} kN, M=${String.format("%.1f", mf.mj_z)} kN.m", fontSize = 10.sp, color = Color.Gray)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        // === Concrete Design Results ===
        if (resultSubTab == 2 && concreteResults.isNotEmpty()) {
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
                            Text("${cr.memberName} (#${cr.memberId})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("القطاع: ${cr.section.width}×${cr.section.depth} mm | f'c=${cr.section.fcu} MPa", fontSize = 11.sp, color = Color.Gray)
                        Spacer(Modifier.height(2.dp))
                        Text("Max M=${String.format("%.1f", cr.maxMoment)} kN.m | Max V=${String.format("%.1f", cr.maxShear)} kN | N=${String.format("%.1f", cr.axialForce)} kN", fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("التسليح المطلوب: ${String.format("%.0f", cr.asRequired)} mm²", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                        if (cr.numBarsBot > 0) {
                            Text("سفلي: ${cr.numBarsBot}Ø${cr.barDia.toInt()} (As=${String.format("%.0f", cr.asBot)} mm²)", fontSize = 11.sp)
                        }
                        if (cr.numBarsTop > 0) {
                            Text("علوي: ${cr.numBarsTop}Ø${cr.barDia.toInt()} (As=${String.format("%.0f", cr.asTop)} mm²)", fontSize = 11.sp)
                        }
                        if (cr.stirrupDia > 0) {
                            Text("الكانات: Ø${cr.stirrupDia.toInt()} @ ${cr.stirrupSpacing.toInt()} mm", fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(4.dp))

                        // Utilization bars
                        Row {
                            Text("انحناء: ", fontSize = 11.sp)
                            UtilizationBar(cr.momentUtilization, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(2.dp))
                        Row {
                            Text("قص:     ", fontSize = 11.sp)
                            UtilizationBar(cr.shearUtilization, Modifier.weight(1f))
                        }

                        if (cr.warnings.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            for (w in cr.warnings) {
                                Text("⚠ $w", fontSize = 10.sp, color = Color(0xFFF44336))
                            }
                        }
                    }
                }
            }
        }

        // === Steel Design Results ===
        if (resultSubTab == 3 && steelResults.isNotEmpty()) {
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
                            Text("${sr.memberName} (#${sr.memberId})", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("القطاع المختار: ${sr.selectedSection}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        Text("الوزن: ${sr.sectionWeight} kg/m | Ix=${sr.sectionIx} cm⁴ | Sx=${sr.sectionSx} cm³", fontSize = 11.sp, color = Color.Gray)
                        Spacer(Modifier.height(2.dp))
                        Text("Max M=${String.format("%.1f", sr.maxMoment)} kN.m | Max V=${String.format("%.1f", sr.maxShear)} kN | N=${String.format("%.1f", sr.axialForce)} kN", fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))

                        Row { Text("انحناء: ", fontSize = 11.sp); UtilizationBar(sr.flexuralUtilization, Modifier.weight(1f)) }
                        Spacer(Modifier.height(2.dp))
                        Row { Text("قص:     ", fontSize = 11.sp); UtilizationBar(sr.shearUtilization, Modifier.weight(1f)) }
                        Spacer(Modifier.height(2.dp))
                        Row { Text("محوري:  ", fontSize = 11.sp); UtilizationBar(sr.axialUtilization, Modifier.weight(1f)) }
                        Spacer(Modifier.height(2.dp))
                        Row { Text("مدمج:   ", fontSize = 11.sp); UtilizationBar(sr.combinedUtilization, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun UtilizationBar(ratio: Double, modifier: Modifier = Modifier) {
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
        Text(
            "${String.format("%.0f", ratio * 100)}%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}