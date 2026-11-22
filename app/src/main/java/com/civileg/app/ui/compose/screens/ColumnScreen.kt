package com.civileg.app.ui.compose.screens

import com.civileg.app.ui.responsive.dimens

import kotlin.math.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import com.civileg.app.viewmodel.ProjectViewModel
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.civileg.app.db.Project
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import java.util.concurrent.atomic.AtomicReference
import com.civileg.app.domain.entities.ColumnDesignSnapshot
import com.civileg.app.viewmodel.ColumnDrawingCapture
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.calculations.utils.BbsGenerator
import com.civileg.app.viewmodel.ColumnViewModel
import com.civileg.app.ui.compose.components.common.BbsTableCard
import com.civileg.app.ui.compose.components.common.BoqTableCard
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.ui.compose.components.drawings.columnDrawingSummary
import com.civileg.app.ui.compose.components.drawings.ProfessionalColumnDrawing
import com.civileg.app.ui.compose.components.drawings.BarInfo
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.captureToAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import com.civileg.app.domain.entities.StirrupZone
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScreen(
    viewModel: ColumnViewModel = hiltViewModel(),
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val projects by projectViewModel.allProjects.observeAsState(emptyList())
    val errorMessage by viewModel.errorMessage.observeAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
        }
    }
    val pdfCaptureLayer = ComposeDrawingCaptureUtil.rememberDrawingCaptureLayer()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * density.density).toInt()
    val screenHeightPx = (config.screenHeightDp * density.density).toInt()

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveSnapshot by remember { mutableStateOf<ColumnDesignSnapshot?>(null) }
    val renderedDrawingRevision = remember { AtomicReference<String?>(null) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    val columnDefaultName = stringResource(R.string.column_default_name)
    var designName by remember { mutableStateOf(columnDefaultName) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.screen_column_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
        LazyColumn(
            modifier = Modifier.testTag("column-content")
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DesignCodeSelector(
                            selectedCode = uiState.designCode,
                            onCodeSelected = { viewModel.updateDesignCode(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LoadCombinationSelector(
                            selected = uiState.loadCombination,
                            code = uiState.designCode,
                            onSelected = { viewModel.updateLoadCombination(it) }
                        )
                        
                        AppText(
                            text = uiState.loadCombination.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            
            item { SectionHeader(stringResource(R.string.column_section_props), R.drawable.ic_column) }
            
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColumnInputField(uiState.width, stringResource(R.string.column_width_t_mm), { viewModel.updateInputs(width = it) }, Modifier.weight(1f))
                    ColumnInputField(uiState.depth, stringResource(R.string.column_depth_t_mm), { viewModel.updateInputs(depth = it) }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColumnInputField(
                        uiState.fcu,
                        stringResource(R.string.column_fcu_label),
                        { viewModel.updateInputs(fcu = it) },
                        Modifier.weight(1f),
                        validate = { v -> v.toDoubleOrNull()?.let { it in 20.0..60.0 } ?: true }
                    )
                    ColumnInputField(
                        uiState.fy,
                        stringResource(R.string.column_fy_label),
                        { viewModel.updateInputs(fy = it) },
                        Modifier.weight(1f),
                        validate = { v -> v.toDoubleOrNull()?.let { it in 200.0..600.0 } ?: true }
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.column_applied_loads), R.drawable.ic_design) }
            
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColumnInputField(
                        uiState.axialLoad,
                        stringResource(R.string.column_axial_load_pu),
                        { viewModel.updateInputs(axialLoad = it) },
                        Modifier.weight(1f),
                        validate = { v -> v.isEmpty() || (v.toDoubleOrNull()?.let { it >= 0 } ?: false) }
                    )
                    ColumnInputField(
                        uiState.height,
                        stringResource(R.string.column_height_label),
                        { viewModel.updateInputs(height = it) },
                        Modifier.weight(1f),
                        validate = { v -> v.toDoubleOrNull()?.let { it in 1.0..15.0 } ?: true }
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColumnInputField(
                        uiState.mx,
                        "Moment Mx (kN.m)",
                        { viewModel.updateInputs(mx = it) },
                        Modifier.weight(1f)
                    )
                    ColumnInputField(
                        uiState.my,
                        "Moment My (kN.m)",
                        { viewModel.updateInputs(my = it) },
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            AppText("Seismic Confinement", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            AppText("Enable special detailing for earthquake zones", style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(
                            checked = uiState.isSeismic,
                            onCheckedChange = { viewModel.updateInputs(isSeismic = it) }
                        )
                    }
                }
            }

            item {
                AppButton(
                    label = stringResource(R.string.column_design_now),
                    onClick = { viewModel.calculateManual() },
                    modifier = Modifier.fillMaxWidth().testTag("column-calculate"),
                    fontWeight = FontWeight.Bold,
                    icon = Icons.Default.Calculate
                )
            }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppButton(
                            label = stringResource(R.string.column_economy_option),
                            onClick = { viewModel.applyEconomicalDesign() },
                            modifier = Modifier.weight(1f),
                            fontSize = 12.sp
                        )
                        AppButton(
                            label = stringResource(R.string.column_safest_option),
                            onClick = { viewModel.applySafetyDesign() },
                            modifier = Modifier.weight(1f),
                            style = AppButtonStyle.Secondary,
                            fontSize = 12.sp
                        )
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                AppText(stringResource(R.string.column_custom_rebar), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = uiState.autoOptimize,
                                    onCheckedChange = { viewModel.updateAutoOptimize(it) },
                                    thumbContent = { if (uiState.autoOptimize) Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp)) }
                                )
                            }
                            AppText(stringResource(R.string.column_candidate_request_limit), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ColumnInputField(
                                    value = if (uiState.autoOptimize && uiState.manualNumBars.isEmpty()) (uiState.result?.reinforcement?.numBars?.toString() ?: "") else uiState.manualNumBars,
                                    label = stringResource(R.string.column_num_bars_label),
                                    onValueChange = { viewModel.updateInputs(manualNumBars = it) },
                                    modifier = Modifier.weight(1f).testTag("column-candidate-count"),
                                    enabled = true
                                )
                                ColumnInputField(
                                    value = uiState.preferredDiameter,
                                    label = stringResource(R.string.column_bar_diameter_label),
                                    onValueChange = { viewModel.updateInputs(preferredDiameter = it) },
                                    modifier = Modifier.weight(1f).testTag("column-candidate-diameter"),
                                    enabled = true
                                )
                            }
                            if (!uiState.autoOptimize) {
                                Spacer(Modifier.height(8.dp))
                                AppButton(
                                    label = stringResource(R.string.column_update_rebar),
                                    onClick = { viewModel.calculateManual() },
                                    modifier = Modifier.fillMaxWidth(),
                                    style = AppButtonStyle.Secondary
                                )
                            }
                        }
                    }
                }


            uiState.completedDesign?.let { completed ->
                val result = completed.result()
                val calculationInput = completed.input()
                item { SectionHeader(stringResource(R.string.column_results_economy), R.drawable.ic_calculator) }
                item {
                    AppText(stringResource(R.string.column_completed_revision, completed.revisionSha256), modifier = Modifier.testTag("column-completed-revision"), style = MaterialTheme.typography.bodySmall)
                    AppText(stringResource(R.string.column_completed_output_limit), style = MaterialTheme.typography.bodySmall)
                }
                
                item {
                    val ecoColor = when {
                        !result.isSafe || result.utilizationRatio > 1.0 -> MaterialTheme.colorScheme.error
                        result.utilizationRatio > 0.9 -> MaterialTheme.colorScheme.tertiary
                        result.utilizationRatio > 0.4 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (result.isSafe && result.utilizationRatio <= 1.0) Icons.Default.Verified
                                            else Icons.Default.Dangerous, 
                                            contentDescription = null, 
                                            tint = ecoColor
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        AppText(
                                            if (!result.isSafe || result.utilizationRatio > 1.0) stringResource(R.string.column_section_unsafe)
                                            else if (result.utilizationRatio > 0.9) stringResource(R.string.design_caution)
                                            else if (result.utilizationRatio > 0.4) stringResource(R.string.column_section_ideal)
                                            else stringResource(R.string.column_section_large),
                                            fontWeight = FontWeight.Bold,
                                            color = ecoColor
                                        )
                                    }
                                    AppText(
                                        stringResource(R.string.consultant_ratio),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Box(contentAlignment = Alignment.Center) {
                                    val animatedRatio by animateFloatAsState(
                                        targetValue = result.utilizationRatio.toFloat(),
                                        animationSpec = tween(1000), label = ""
                                    )
                                    CircularProgressIndicator(
                                        progress = { animatedRatio },
                                        modifier = Modifier.size(60.dp),
                                        strokeWidth = 6.dp,
                                        color = ecoColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    AppText(
                                        "${(result.utilizationRatio * 100).toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                item { ColumnResultCard(result) }

                val bbsEntries = runCatching {
                    BbsGenerator.generateColumnBbs("C1", result, calculationInput.heightM * 1000.0)
                }.getOrDefault(emptyList())
                if (bbsEntries.isNotEmpty()) {
                    item { BbsTableCard(bbsEntries, title = "Column Bar Bending Schedule") }
                }

                val boqRows = runCatching {
                    BoqEstimator.columnRows(result, calculationInput.heightM, "C1")
                }.getOrDefault(emptyList())
                if (boqRows.isNotEmpty()) {
                    item { BoqTableCard(boqRows, title = "Column Bill of Quantities") }
                }

                if (result.mx != 0.0 || result.my != 0.0) {
                    item {
                        AppText("Biaxial Interaction (P-M)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        com.civileg.app.ui.compose.components.charts.BiaxialInteractionChart(
                            mxPoints = emptyList(), // Needs wiring to ViewModel for actual curve
                            myPoints = emptyList(),
                            appliedMx = result.mx,
                            appliedMy = result.my,
                            appliedP = result.appliedAxial,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            label = stringResource(R.string.pdf_report),
                            onClick = {
                                scope.launch {
                                    val renderedBefore = renderedDrawingRevision.get()
                                    val captureBitmap = try {
                                        pdfCaptureLayer.captureToAndroidBitmap()
                                    } catch (_: Exception) { null }
                                    val capture = if (captureBitmap != null && renderedBefore == completed.revisionSha256 &&
                                        renderedDrawingRevision.get() == completed.revisionSha256)
                                        runCatching { ColumnDrawingCapture(completed.revisionSha256, captureBitmap) }.getOrNull() else null
                                    viewModel.exportToPdf(context, completed, capture) { /* Handle completion */ }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("column-pdf-export"),
                            enabled = !uiState.isExporting,
                            loading = uiState.isExporting,
                            icon = Icons.Default.PictureAsPdf
                        )

                        AppButton(
                            label = stringResource(R.string.save),
                            onClick = { saveSnapshot = completed; showSaveDialog = true },
                            modifier = Modifier.weight(1f).testTag("column-save"),
                            icon = Icons.Default.Save,
                            containerOverride = MaterialTheme.colorScheme.secondary,
                            contentOverride = onColorFor(MaterialTheme.colorScheme.secondary)
                        )
                    }
                }

                item {
                    AppText(stringResource(R.string.column_equations_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    EngineeringFormulasCard(calculationInput.code)
                }

                item {
                    AppText(stringResource(R.string.column_adopted_coordinates_unavailable),
                        modifier = Modifier.testTag("column-adopted-layout-hold"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                item {
                    var selectedViewMode by remember { mutableStateOf(0) }
                    val configuration = LocalConfiguration.current
                    val screenW = configuration.screenWidthDp.dp
                    val wRatio = screenW.value / 360f
                    val drawingHeight = when (selectedViewMode) {
                        1 -> (450 * wRatio).toInt().coerceIn(300, 600)
                        2 -> (450 * wRatio).toInt().coerceIn(300, 600)
                        3 -> (400 * wRatio).toInt().coerceIn(250, 550)
                        else -> (700 * wRatio).toInt().coerceIn(450, 1000)
                    }
                    InteractiveDrawingScreen(
                        onExportDxf = null, // Reinforcement coordinates/detailing are not verified.
                        title = stringResource(R.string.column_drawing_title),
                        subtitle = stringResource(R.string.column_drawing_subtitle),
                        drawingHeightDp = drawingHeight,
                        viewModes = listOf(stringResource(R.string.view_all), stringResource(R.string.view_perspective), stringResource(R.string.view_section), stringResource(R.string.view_reinforcement)),
                        selectedViewMode = selectedViewMode,
                        onViewModeChanged = { selectedViewMode = it },
                        drawingContent = {
                            ProfessionalColumnDrawing(
                                columnWidth = result.width.toDouble(),
                                columnDepth = result.depth.toDouble(),
                                columnHeight = calculationInput.heightM * 1000.0,
                                longitudinalBars = columnCheckedCoordinateInputs(completed),
                                tieDia = result.stirrups.diameter.toDouble(),
                                tieSpacing = result.stirrups.spacing.toDouble(),
                                cover = 40.0,
                                isSpiral = false,
                                designCode = calculationInput.code,
                                sectionType = if (result.columnType.contains("CIRCULAR", ignoreCase = true)) "Circular" else "Rectangular",
                                zones = result.stirrups.zones.map { com.civileg.app.domain.entities.StirrupZone(it.name, it.startLocation, it.endLocation, it.spacing, it.numLegs, it.diameter, it.description) },
                                viewMode = selectedViewMode,
                                resultSummary = columnDrawingSummary(result),
                                modifier = Modifier.fillMaxWidth().testTag("column-visible-drawing")
                            )
                        }
                    )
                }
            }
        }
        // PDF drawing capture area (invisible, renders at viewMode=0)
        uiState.completedDesign?.let { completed ->
                val result = completed.result()
                val calculationInput = completed.input()
            CompletedColumnCaptureArea(
                captureLayer = pdfCaptureLayer,
                widthPx = screenWidthPx,
                heightPx = screenHeightPx,
                revision = completed.revisionSha256,
                recordedRevision = renderedDrawingRevision
            ) {
                Box(modifier = Modifier.background(Color(0xFF1A1A2E))) {
                    ProfessionalColumnDrawing(
                        columnWidth = result.width.toDouble(),
                        columnDepth = result.depth.toDouble(),
                        columnHeight = calculationInput.heightM * 1000.0,
                        longitudinalBars = columnCheckedCoordinateInputs(completed),
                        tieDia = result.stirrups.diameter.toDouble(),
                        tieSpacing = result.stirrups.spacing.toDouble(),
                        cover = 40.0,
                        isSpiral = false,
                        designCode = calculationInput.code,
                        sectionType = if (result.columnType.contains("CIRCULAR", ignoreCase = true)) "Circular" else "Rectangular",
                        zones = result.stirrups.zones.map { StirrupZone(it.name, it.startLocation, it.endLocation, it.spacing, it.numLegs, it.diameter, it.description) },
                        viewMode = 0,
                        resultSummary = columnDrawingSummary(result),
                        modifier = Modifier.fillMaxWidth().testTag("column-pdf-drawing")
                    )
                }
            }
        }
        }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp,
                        color = Color(0xFF1565C0)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AppText(
                        "Calculating... / جاري الحساب...",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isSaving) showSaveDialog = false },
            title = { AppText(stringResource(R.string.save_design_in_project)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = designName,
                        enabled = !uiState.isSaving,
                        onValueChange = { designName = it },
                        label = { AppText(stringResource(R.string.column_name_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    AppText(stringResource(R.string.select_project), style = MaterialTheme.typography.labelMedium)
                    if (projects.isEmpty()) {
                        AppText(stringResource(R.string.no_projects_available), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    } else {
                        projects.forEach { project ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedProjectId == project.id,
                                    enabled = !uiState.isSaving,
                                    onClick = { selectedProjectId = project.id }
                                )
                                AppText(project.name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    label = stringResource(R.string.save),
                    fillMaxWidth = false,
                    modifier = Modifier.testTag("column-save-confirm"),
                    enabled = designName.isNotBlank() && projects.any { it.id == selectedProjectId } &&
                        saveSnapshot?.let { viewModel.isCurrent(it) } == true && !uiState.isSaving,
                    loading = uiState.isSaving,
                    onClick = {
                        saveSnapshot?.let { snapshot ->
                            if (projects.any { it.id == selectedProjectId }) {
                                viewModel.saveColumn(selectedProjectId, designName, snapshot) { saved ->
                                    if (saved) { showSaveDialog = false; saveSnapshot = null }
                                }
                            }
                        }
                    }
                )
            },
            dismissButton = {
                AppButton(
                    label = stringResource(R.string.cancel),
                    enabled = !uiState.isSaving,
                    onClick = { showSaveDialog = false },
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false
                )
            }
        )
    }
}

/** Marks the revision only after the parent layer record succeeds, not merely after a child draw. */
@Composable
private fun CompletedColumnCaptureArea(
    captureLayer: GraphicsLayer,
    widthPx: Int,
    heightPx: Int,
    revision: String,
    recordedRevision: AtomicReference<String?>,
    content: @Composable () -> Unit
) {
    val width = widthPx.coerceAtLeast(1)
    val height = heightPx.coerceAtLeast(1)
    Box(Modifier.layout { measurable, _ ->
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(0, 0) { placeable.place(0, 0) }
    }.drawWithContent {
        recordedRevision.set(null)
        try {
            captureLayer.record(size = IntSize(width, height)) { this@drawWithContent.drawContent() }
            recordedRevision.set(revision)
        } catch (_: Throwable) {
            recordedRevision.set(null)
        }
    }) { content() }
}

@Composable
private fun ColumnResultCard(result: CalculatorEngine.ColumnResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (result.isSafe)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (result.isSafe) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (result.isSafe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                AppText(
                    if (result.isSafe) stringResource(R.string.column_section_safe) else stringResource(R.string.column_section_unsafe_label),
                    fontWeight = FontWeight.Bold,
                    color = if (result.isSafe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            
            ResultDataRow("Applied Pu", "${String.format("%.1f", result.appliedAxial)} kN")
            ResultDataRow("Axial Capacity", "${String.format("%.1f", result.axialCapacity)} kN")
            if (result.isSlender) {
                ResultDataRow("Slenderness", "${String.format("%.1f", result.slenderness)} (Slender)")
            } else {
                ResultDataRow("Slenderness", "${String.format("%.1f", result.slenderness)} (Short)")
            }
            if (result.mx != 0.0 || result.my != 0.0) {
                ResultDataRow("Applied Mx / My", "${result.mx} / ${result.my} kN.m")
            }
            ResultDataRow(stringResource(R.string.column_provided_reinforcement), result.reinforcement.barString)
            
            if (result.stirrups.zones.isNotEmpty()) {
                 AppText(stringResource(R.string.stirrups_distribution), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                 result.stirrups.zones.forEach { zone ->
                     ResultDataRow("${zone.name} (${String.format(java.util.Locale.US, "%.1f", (zone.endLocation - zone.startLocation)/1000.0)}m)", zone.description)
                 }
            } else {
                ResultDataRow(stringResource(R.string.stirrups), result.stirrups.description)
            }
            ResultDataRow(stringResource(R.string.column_reinforcement_ratio), String.format("%.2f %%", result.reinforcementRatio))
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
            
            AppText(stringResource(R.string.column_quantities), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ResultDataRow(stringResource(R.string.column_concrete_vol), String.format("%.3f m³", result.concreteVolume))
            ResultDataRow(stringResource(R.string.column_steel_weight), String.format("%.2f kg", result.steelWeight))
            ResultDataRow(stringResource(R.string.column_estimated_cost), String.format("%.2f", result.cost))

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
            
            AppText(stringResource(R.string.safety_checks), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            result.safetyChecks.forEach { check ->
                ResultCheckRow(check)
            }

            if (result.warnings.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                AppText("Warnings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                result.warnings.forEach { warning ->
                    AppText("• $warning", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            if (result.codeNotes.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                AppText("Code Notes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                result.codeNotes.forEach { note ->
                    AppText("• $note", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ResultCheckRow(check: CalculatorEngine.DesignSafetyCheck) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppText(
            if (check.isSafe) stringResource(R.string.safe_check) else stringResource(R.string.unsafe_check),
            color = if (check.isSafe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            AppText(check.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            AppText(
                "${String.format("%.2f", check.value)} / ${String.format("%.2f", check.limit)} ${check.unit}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EngineeringFormulasCard(designCode: DesignCode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            FormulaItem(
                when (designCode) {
                    DesignCode.ACI -> stringResource(R.string.column_formula_aci_pu)
                    DesignCode.SBC -> stringResource(R.string.column_formula_sbc_pu)
                    else -> stringResource(R.string.column_formula_ecp_pu)
                }
            )
            FormulaItem(stringResource(R.string.column_formula_shear))
            FormulaItem(stringResource(R.string.column_formula_punching))
            FormulaItem(
                when (designCode) {
                    DesignCode.ACI -> stringResource(R.string.column_formula_as_min_aci)
                    DesignCode.SBC -> stringResource(R.string.column_formula_as_min_sbc)
                    else -> stringResource(R.string.column_formula_as_min_ecp)
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ColumnInputField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
    validate: (String) -> Boolean = { true }
) {
    val isValid = validate(value)
    val hasValue = value.isNotEmpty()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { AppText(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        isError = hasValue && !isValid,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        supportingText = if (hasValue && !isValid) {
            { AppText(stringResource(R.string.column_validation_positive), color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
        } else null
    )
}

@Composable
private fun ResultDataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label, style = MaterialTheme.typography.bodyMedium)
        AppText(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FormulaItem(formula: String) {
    AppText(
        text = formula,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
private fun DesignCodeSelector(selectedCode: DesignCode, onCodeSelected: (DesignCode) -> Unit) {
    AppText(stringResource(R.string.column_select_code), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DesignCode.values().forEach { code ->
            val isSelected = code == selectedCode
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(45.dp)
                    .clickable { onCodeSelected(code) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppText(
                        text = when (code) {
                            DesignCode.SBC -> stringResource(R.string.column_code_sbc)
                            DesignCode.ECP -> stringResource(R.string.column_code_ecp)
                            DesignCode.ACI -> stringResource(R.string.column_code_aci)
                        },
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadCombinationSelector(selected: LoadCombination, code: com.civileg.app.domain.entities.DesignCode, onSelected: (LoadCombination) -> Unit) {
    AppText(stringResource(R.string.column_load_combo), style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        LoadCombination.getByCode(code).forEach { combo ->
            FilterChip(
                selected = combo == selected,
                onClick = { onSelected(combo) },
                label = { AppText(combo.description, fontSize = 11.sp) }
            )
        }
    }
}

/** A recorded count/diameter is not an engine-adopted coordinate/detailing contract.
 * Keep that absence explicit rather than arranging bars solely for a plausible picture.
 */
internal fun columnCheckedCoordinateInputs(snapshot: ColumnDesignSnapshot): List<BarInfo> {
    require(snapshot.hasValidFingerprint())
    return emptyList()
}
