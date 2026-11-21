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

/** Shared action blue of the frame editor (one token instead of three raw literals, R13). */
internal val FrameActionBlue = Color(0xFF1565C0)

/**
 * Frame Analysis and Design Screen (2D Frames).
 * Allows creating nodes, members, loads, solving the frame, and viewing diagrams and results.
 *
 * @param viewModel The view model managing frame state and calculations.
 * @param onNavigateBack Callback invoked when navigating back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameAnalysisScreen(
    viewModel: FrameAnalysisViewModel = hiltViewModel(),
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val nodes by viewModel.nodes.observeAsState(emptyList())
    val members by viewModel.members.observeAsState(emptyList())
    val nodalLoads by viewModel.nodalLoads.observeAsState(emptyList())
    val memberLoads by viewModel.memberLoads.observeAsState(emptyList())
    val settingsVal by viewModel.settings.observeAsState(FrameAnalysisSettings())
    val steelFyVal by viewModel.steelFy.observeAsState(355.0)
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMsg by viewModel.errorMessage.observeAsState()
    val validationIssues by viewModel.validationIssues.observeAsState(emptyList())
    val designNotice by viewModel.designNotice.observeAsState()
    val modelLoadVersion by viewModel.modelLoadVersion.observeAsState(0)
    val diagramType by viewModel.activeDiagramType.observeAsState(DiagramType.BMD)
    val selectedMemberId by viewModel.selectedMemberId.observeAsState()
    val concreteResults by viewModel.concreteResults.observeAsState(emptyList())
    val steelResults by viewModel.steelResults.observeAsState(emptyList())
    val projects by projectViewModel.allProjects.observeAsState(emptyList())
    val allDesigns by projectViewModel.allDesigns.observeAsState(emptyList())
    val savedFrameDesigns = remember(allDesigns) {
        allDesigns.filter { it.type == DesignType.FRAME_ANALYSIS }
    }
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSaveDesignDialog by remember { mutableStateOf(false) }
    var showOpenDesignDialog by remember { mutableStateOf(false) }
    val defaultDesignName = stringResource(R.string.frame_default_design_name)
    val savedMessage = stringResource(R.string.frame_design_saved)
    val loadedMessage = stringResource(R.string.frame_design_loaded)
    LaunchedEffect(designNotice) {
        when (designNotice) {
            FrameDesignNotice.SAVED -> Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
            FrameDesignNotice.LOADED -> Toast.makeText(context, loadedMessage, Toast.LENGTH_SHORT).show()
            null -> Unit
        }
        if (designNotice != null) viewModel.clearDesignNotice()
    }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AppText(
                        text = stringResource(R.string.frame_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
                    val scope = rememberCoroutineScope()
                    Box {
                        var showActionMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showActionMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showActionMenu,
                            onDismissRequest = { showActionMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { AppText(stringResource(R.string.frame_save_design)) },
                                onClick = { showActionMenu = false; showSaveDesignDialog = true },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { AppText(stringResource(R.string.frame_open_design)) },
                                onClick = { showActionMenu = false; showOpenDesignDialog = true },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                            )
                            var showTemplatesMenu by remember { mutableStateOf(false) }
                            DropdownMenuItem(
                                text = { AppText("Templates") },
                                onClick = { showTemplatesMenu = true },
                                leadingIcon = { Icon(Icons.Default.Dashboard, contentDescription = null) }
                            )
                            DropdownMenu(
                                expanded = showTemplatesMenu,
                                onDismissRequest = { showTemplatesMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { AppText(stringResource(R.string.frame_simple_portal_desc)) },
                                    onClick = {
                                        showTemplatesMenu = false; showActionMenu = false
                                        viewModel.loadSimplePortalFrame(6.0, 4.0, 20.0)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { AppText(stringResource(R.string.frame_two_story_desc)) },
                                    onClick = {
                                        showTemplatesMenu = false; showActionMenu = false
                                        viewModel.loadTwoStoryFrame(6.0, 4.0, 3.5, 15.0)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { AppText(stringResource(R.string.frame_clear_all)) },
                                onClick = { showActionMenu = false; viewModel.clearAll() },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) }
                            )
                            if (result?.hasResults == true) {
                                DropdownMenuItem(
                                    text = { AppText(stringResource(R.string.frame_export_pdf)) },
                                    onClick = {
                                        showActionMenu = false
                                        val inputs = viewModel.getStoredInputs()
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val file = FrameAnalysisPdfExporter.generateFrameAnalysisPdf(
                                                    context, inputs.nodes, inputs.members, inputs.nodalLoads,
                                                    inputs.memberLoads, inputs.settings, inputs.result,
                                                    inputs.slabs, inputs.sceneView
                                                )
                                                withContext(Dispatchers.Main) {
                                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                                        context, "${context.packageName}.provider", file
                                                    )
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "application/pdf"
                                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Share Report"))
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) { Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show() }
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Diagram type selector (shown on Drawing tab when results exist)
            if (result?.hasResults == true && selectedTab == 0) {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (type in DiagramType.entries) {
                            val isSelected = diagramType == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setDiagramType(type) },
                                label = {
                                    AppText(
                                        text = type.localizedLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
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
            // Tab Row — Scrollable to prevent any tab from disappearing or truncating
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1565C0).copy(alpha = 0.1f),
                contentColor = Color(0xFF1565C0),
                edgePadding = 0.dp
            ) {
                for ((index, title) in tabs.withIndex()) {
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { AppText(title, fontSize = 13.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> DrawingTab(
                        modifier = Modifier.fillMaxSize(),
                        nodes, members, memberLoads, nodalLoads, result, diagramType, selectedMemberId, modelLoadVersion, viewModel
                    )
                    1 -> NodesTab(nodes, members, viewModel)
                    2 -> MembersTab(nodes, members, settingsVal, viewModel)
                    3 -> LoadsTab(nodes, members, nodalLoads, memberLoads, viewModel)
                    4 -> ResultsTab(result, concreteResults, steelResults, selectedMemberId, viewModel)
                }
            }
        }
    }
    if (showSaveDesignDialog) {
        FrameSaveDesignDialog(
            projects = projects,
            defaultName = "$defaultDesignName ${savedFrameDesigns.size + 1}",
            onDismiss = { showSaveDesignDialog = false },
            onSave = { projectId, name ->
                showSaveDesignDialog = false
                viewModel.saveFrameDesign(projectId, name)
            }
        )
    }
    if (showOpenDesignDialog) {
        FrameOpenDesignDialog(
            designs = savedFrameDesigns,
            projects = projects,
            onDismiss = { showOpenDesignDialog = false },
            onOpen = { designId ->
                showOpenDesignDialog = false
                viewModel.loadFrameDesign(designId)
            }
        )
    }
    if (validationIssues.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.clearValidationIssues() },
            title = { AppText(stringResource(R.string.frame_validation_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    validationIssues.forEachIndexed { index, issue ->
                        AppText(
                            "${index + 1}. ${issue.message}",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            },
            confirmButton = {
                AppButton(
                    label = stringResource(R.string.frame_validation_ok),
                    onClick = { viewModel.clearValidationIssues() },
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false
                )
            }
        )
    }
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }
  }
}

// ============================================================================
// Save / Open dialogs
// ============================================================================
@Composable
