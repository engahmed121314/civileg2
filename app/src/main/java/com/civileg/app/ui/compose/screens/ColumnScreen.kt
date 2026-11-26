package com.civileg.app.ui.compose.screens

import com.civileg.app.ui.responsive.dimens
import com.civileg.app.ui.compose.validation.FormErrorSummary
import com.civileg.app.ui.compose.validation.ValidatedNumberField
import com.civileg.app.ui.compose.validation.rememberFormValidationState
import com.civileg.app.ui.compose.validation.scrollItemToCenter
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect

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
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.BarInfo
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextBlock
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

    val form = rememberFormValidationState()
    val listState = rememberLazyListState()
    // Item index of each field's row. Result items follow the Design button: header 9, economy card 10,
    // result card 11, options 12, custom-rebar card 13.
    val fieldItemIndex = mapOf(
        "width" to 2, "depth" to 2, "fcu" to 3, "fy" to 3,
        "axialLoad" to 5, "height" to 5, "mx" to 6, "my" to 6,
        "manualNumBars" to 13, "preferredDiameter" to 13
    )
    // The custom-rebar card sits after the conditional BBS / BOQ / biaxial items; the list builder
    // below records its real index each time it runs.
    val rebarCardIndex = remember { intArrayOf(13) }
    // Every failed Design attempt (button, economy/safe, code, load combination, rebar update)
    // arrives here: show the reasons, scroll the first bad field to the middle and focus it.
    LaunchedEffect(uiState.validationTick) {
        val v = uiState.validation
        if (v != null) {
            form.apply(v)
            form.focusFirstInvalid(v) { id ->
                val idx = if (id == "manualNumBars" || id == "preferredDiameter") rebarCardIndex[0] else fieldItemIndex[id]
                idx?.let { listState.scrollItemToCenter(it) }
            }
        } else form.clearAll()
    }
    var showSaveDialog by remember { mutableStateOf(false) }
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
            state = listState,
            modifier = Modifier
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
                    ValidatedNumberField(form, "width", uiState.width, stringResource(R.string.column_width_t_mm), { form.clear("width"); viewModel.updateInputs(width = it) }, Modifier.weight(1f))
                    ValidatedNumberField(form, "depth", uiState.depth, stringResource(R.string.column_depth_t_mm), { form.clear("depth"); viewModel.updateInputs(depth = it) }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValidatedNumberField(form, "fcu", uiState.fcu, stringResource(R.string.column_fcu_label), { form.clear("fcu"); viewModel.updateInputs(fcu = it) }, Modifier.weight(1f))
                    ValidatedNumberField(form, "fy", uiState.fy, stringResource(R.string.column_fy_label), { form.clear("fy"); viewModel.updateInputs(fy = it) }, Modifier.weight(1f))
                }
            }

            item { SectionHeader(stringResource(R.string.column_applied_loads), R.drawable.ic_design) }
            
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValidatedNumberField(form, "axialLoad", uiState.axialLoad, stringResource(R.string.column_axial_load_pu), { form.clear("axialLoad"); viewModel.updateInputs(axialLoad = it) }, Modifier.weight(1f))
                    ValidatedNumberField(form, "height", uiState.height, stringResource(R.string.column_height_label), { form.clear("height"); viewModel.updateInputs(height = it) }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValidatedNumberField(form, "mx", uiState.mx, "Moment Mx (kN.m)", { form.clear("mx"); viewModel.updateInputs(mx = it) }, Modifier.weight(1f))
                    ValidatedNumberField(form, "my", uiState.my, "Moment My (kN.m)", { form.clear("my"); viewModel.updateInputs(my = it) }, Modifier.weight(1f))
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
                            AppText(stringResource(R.string.column_seismic_confinement_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            AppText(stringResource(R.string.column_seismic_confinement_sub), style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(
                            checked = uiState.isSeismic,
                            onCheckedChange = { viewModel.updateInputs(isSeismic = it) }
                        )
                    }
                }
            }

            item {
                FormErrorSummary(form)
                AppButton(
                    label = stringResource(R.string.column_design_now),
                    onClick = { viewModel.calculateManual() },
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.Bold,
                    icon = Icons.Default.Calculate
                )
            }

            uiState.result?.let { result ->
                val solvedHeightM = uiState.solvedHeightM ?: return@let
                item { SectionHeader(stringResource(R.string.column_results_economy), R.drawable.ic_calculator) }
                
                item {
                    val ecoColor = when {
                        result.utilizationRatio > 1.0 -> MaterialTheme.colorScheme.error
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
                                            if (result.utilizationRatio <= 1.0) Icons.Default.Verified 
                                            else Icons.Default.Dangerous, 
                                            contentDescription = null, 
                                            tint = ecoColor
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        AppText(
                                            if (result.utilizationRatio > 1.0) stringResource(R.string.column_section_unsafe)
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

                item {
                    AppText(
                        com.civileg.app.domain.calculations.column.ColumnOutputModel.bbsStatus(result),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                val boqRows = runCatching {
                    BoqEstimator.columnRows(result, solvedHeightM, "C1")
                }.getOrDefault(emptyList())
                if (boqRows.isNotEmpty()) {
                    item { BoqTableCard(boqRows, title = stringResource(R.string.column_boq_title)) }
                }

                if (result.mx != 0.0 || result.my != 0.0) {
                    item {
                        AppText(stringResource(R.string.column_biaxial_interaction_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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

                rebarCardIndex[0] = 13 + /* BBS card is not issued for columns */ 0 +
                    (if (boqRows.isNotEmpty()) 1 else 0) + (if (result.mx != 0.0 || result.my != 0.0) 1 else 0)
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
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ValidatedNumberField(
                                    form, "manualNumBars",
                                    if (uiState.autoOptimize && uiState.manualNumBars.isEmpty()) result.reinforcement.numBars.toString() else uiState.manualNumBars,
                                    stringResource(R.string.column_num_bars_label),
                                    { form.clear("manualNumBars"); viewModel.updateInputs(manualNumBars = it) },
                                    Modifier.weight(1f)
                                )
                                ValidatedNumberField(form, "preferredDiameter", uiState.preferredDiameter, stringResource(R.string.column_bar_diameter_label), { form.clear("preferredDiameter"); viewModel.updateInputs(preferredDiameter = it) }, Modifier.weight(1f))
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

                item {
                    Column {
                    if (uiState.resultIsStale) {
                        AppText(stringResource(R.string.column_rebar_edits_not_applied), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            label = stringResource(R.string.pdf_report),
                            onClick = {
                                scope.launch {
                                    val captureBitmap = try {
                                        pdfCaptureLayer.captureToAndroidBitmap()
                                    } catch (_: Exception) { null }
                                    viewModel.pendingDrawingBitmap = captureBitmap
                                    viewModel.exportToPdf(context) { /* Handle completion */ }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isExporting && !uiState.resultIsStale,
                            loading = uiState.isExporting,
                            icon = Icons.Default.PictureAsPdf
                        )

                        AppButton(
                            label = stringResource(R.string.save),
                            onClick = { showSaveDialog = true },
                            enabled = !uiState.resultIsStale,
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Save,
                            containerOverride = MaterialTheme.colorScheme.secondary,
                            contentOverride = onColorFor(MaterialTheme.colorScheme.secondary)
                        )
                    }
                    }
                }

                item {
                    AppText(stringResource(R.string.column_equations_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    EngineeringFormulasCard(uiState.designCode)
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
                        onExportDxf = { viewModel.exportToDxf(context) },
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
                                columnHeight = solvedHeightM * 1000.0,
                                longitudinalBars = generateBarPositions(
                                    result.width.toDouble(), result.depth.toDouble(),
                                    result.reinforcement.numBars,
                                    result.reinforcement.diameter.toDouble(),
                                    result.columnType.contains("CIRCULAR", ignoreCase = true),
                                    com.civileg.app.domain.calculations.column.ColumnCover.mm(result.code)
                                ),
                                tieDia = result.stirrups.diameter.toDouble(),
                                tieSpacing = result.stirrups.spacing.toDouble(),
                                cover = com.civileg.app.domain.calculations.column.ColumnCover.mm(result.code),
                                isSpiral = false,
                                sectionType = if (result.columnType.contains("CIRCULAR", ignoreCase = true)) "Circular" else "Rectangular",
                                zones = result.stirrups.zones.map { com.civileg.app.domain.entities.StirrupZone(it.name, it.startLocation, it.endLocation, it.spacing, it.numLegs, it.diameter, it.description) },
                                viewMode = selectedViewMode,
                                resultSummary = columnDrawingSummary(result),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }
            }
        }
        // PDF drawing capture area (invisible, renders at viewMode=0)
        uiState.result?.let { result ->
            val solvedHeightM = uiState.solvedHeightM ?: return@let
            ComposeDrawingCaptureUtil.DrawingCaptureArea(
                captureLayer = pdfCaptureLayer,
                widthPx = screenWidthPx,
                heightPx = screenHeightPx
            ) {
                Box(modifier = Modifier.background(Color(0xFF1A1A2E))) {
                    ProfessionalColumnDrawing(
                        columnWidth = result.width.toDouble(),
                        columnDepth = result.depth.toDouble(),
                        columnHeight = solvedHeightM * 1000.0,
                        longitudinalBars = generateBarPositions(
                            result.width.toDouble(), result.depth.toDouble(),
                            result.reinforcement.numBars,
                            result.reinforcement.diameter.toDouble(),
                            result.columnType.contains("CIRCULAR", ignoreCase = true),
                            com.civileg.app.domain.calculations.column.ColumnCover.mm(result.code)
                        ),
                        tieDia = result.stirrups.diameter.toDouble(),
                        tieSpacing = result.stirrups.spacing.toDouble(),
                        cover = com.civileg.app.domain.calculations.column.ColumnCover.mm(result.code),
                        isSpiral = false,
                        sectionType = if (result.columnType.contains("CIRCULAR", ignoreCase = true)) "Circular" else "Rectangular",
                        zones = result.stirrups.zones.map { StirrupZone(it.name, it.startLocation, it.endLocation, it.spacing, it.numLegs, it.diameter, it.description) },
                        viewMode = 0,
                        resultSummary = columnDrawingSummary(result),
                        modifier = Modifier.fillMaxWidth()
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
            onDismissRequest = { showSaveDialog = false },
            title = { AppText(stringResource(R.string.save_design_in_project)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = designName,
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
                    onClick = {
                        val pId = if (selectedProjectId == -1L) 1L else selectedProjectId
                        uiState.result?.let { viewModel.saveColumn(pId, designName, it) }
                        showSaveDialog = false
                    }
                )
            },
            dismissButton = {
                AppButton(
                    label = stringResource(R.string.cancel),
                    onClick = { showSaveDialog = false },
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false
                )
            }
        )
    }
}

@Composable
private fun ColumnResultCard(result: CalculatorEngine.ColumnResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (result.confirmedSafe)
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
                    if (result.confirmedSafe) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (result.confirmedSafe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                AppText(
                    when (com.civileg.app.domain.calculations.column.ColumnStatus.of(result)) {
                        com.civileg.app.domain.calculations.column.ColumnStatus.NOT_ASSESSED -> stringResource(R.string.column_axial_not_assessed_label)
                        com.civileg.app.domain.calculations.column.ColumnStatus.PRELIMINARY -> stringResource(R.string.column_preliminary_label)
                        com.civileg.app.domain.calculations.column.ColumnStatus.SAFE -> stringResource(R.string.column_section_safe)
                        com.civileg.app.domain.calculations.column.ColumnStatus.FAIL -> stringResource(R.string.column_section_unsafe_label)
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (result.confirmedSafe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            
            ResultDataRow("Applied Pu", "${String.format("%.1f", result.appliedAxial)} kN")
            ResultDataRow("Axial Capacity", result.axialCapacityLabel)
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
            ResultDataRow(stringResource(R.string.column_estimated_cost), "Unavailable (no price basis)")

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
            
            AppText(stringResource(R.string.safety_checks), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            result.safetyChecks.forEach { check ->
                ResultCheckRow(check)
            }

            if (result.warnings.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                AppText(stringResource(R.string.column_warnings_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                result.warnings.forEach { warning ->
                    AppTextBlock("• $warning", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            if (result.codeNotes.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                AppText(stringResource(R.string.column_code_notes_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                result.codeNotes.forEach { note ->
                    AppTextBlock("• $note", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun ResultDataRow(label: String, value: String) {
    com.civileg.app.ui.components.ContentDirection(label) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label, style = MaterialTheme.typography.bodyMedium)
        AppText(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
    }
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

private fun generateBarPositions(width: Double, depth: Double, numBars: Int, diameter: Double, isCircular: Boolean = false, cover: Double = 40.0): List<BarInfo> {
    val totalBars = maxOf(4, numBars)
    val positions = mutableListOf<BarInfo>()

    if (isCircular) {
        val radius = (minOf(width, depth) - 2 * cover) / 2.0
        val cx = width / 2.0
        val cy = depth / 2.0
        for (i in 0 until totalBars) {
            val angle = 2.0 * Math.PI * i / totalBars
            val x = cx + radius * cos(angle)
            val y = cy + radius * sin(angle)
            positions.add(BarInfo(x, y, diameter, isCorner = true))
        }
        return positions
    }

    val xMin = cover
    val xMax = width - cover
    val yMin = cover
    val yMax = depth - cover
    val effW = width - 2 * cover
    val effD = depth - 2 * cover

    positions.add(BarInfo(xMin, yMin, diameter, isCorner = true))
    positions.add(BarInfo(xMax, yMin, diameter, isCorner = true))
    positions.add(BarInfo(xMax, yMax, diameter, isCorner = true))
    positions.add(BarInfo(xMin, yMax, diameter, isCorner = true))

    val remaining = totalBars - 4
    if (remaining <= 0) return positions

    val aspect = effW / effD
    val barsY = maxOf(0, round((remaining / (2.0 * (aspect + 1.0)))).toInt())
    val barsX = maxOf(0, (remaining - 2 * barsY) / 2)
    var leftover = remaining - 2 * (barsX + barsY)

    for (i in 1..barsX + (if (leftover > 0) 1 else 0)) {
        if (leftover > 0 && i == barsX + 1) leftover--
        val t = i.toDouble() / (barsX + 1 + (if (leftover > 0) 1 else 0))
        positions.add(BarInfo(xMin + t * effW, yMin, diameter))
    }
    for (i in 1..barsX + (if (leftover > 0) 1 else 0)) {
        if (leftover > 0 && i == barsX + 1) leftover--
        val t = i.toDouble() / (barsX + 1 + (if (leftover > 0) 1 else 0))
        positions.add(BarInfo(xMin + t * effW, yMax, diameter))
    }
    for (i in 1..barsY) {
        val t = i.toDouble() / (barsY + 1)
        positions.add(BarInfo(xMin, yMin + t * effD, diameter))
    }
    for (i in 1..barsY) {
        val t = i.toDouble() / (barsY + 1)
        positions.add(BarInfo(xMax, yMin + t * effD, diameter))
    }

    return positions
}
