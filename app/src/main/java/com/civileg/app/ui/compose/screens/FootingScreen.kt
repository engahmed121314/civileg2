package com.civileg.app.ui.compose.screens

import com.civileg.app.ui.responsive.dimens

import com.civileg.app.domain.entities.DesignCode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.livedata.observeAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.BbsGenerator
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import com.civileg.app.viewmodel.FootingViewModel
import com.civileg.app.ui.compose.components.common.BbsTableCard
import com.civileg.app.ui.compose.components.common.BoqTableCard
import com.civileg.app.utils.BoqEstimator
import com.civileg.app.ui.compose.components.drawings.footingDrawingSummary
import com.civileg.app.ui.compose.components.common.CalculationStepsCard
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalFootingDrawing
import com.civileg.app.ui.compose.components.drawings.ProfessionalCombinedFootingDrawing
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.app.viewmodel.CombinedFootingViewModel
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.captureToAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import com.civileg.app.ui.defaultDesignCode
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootingScreen(
    viewModel: FootingViewModel = hiltViewModel(),
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)
    val projects by projectViewModel.allProjects.observeAsState(emptyList())
    val vmError by viewModel.error.observeAsState()

    // Show errors via Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(vmError) {
        vmError?.let {
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
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    val footingDefaultName = stringResource(R.string.footing_default_name)
    var designName by remember { mutableStateOf(footingDefaultName) }

    // Pre-select the first available project instead of saving against a hardcoded id.
    LaunchedEffect(showSaveDialog, projects) {
        if (showSaveDialog && projects.isNotEmpty() && projects.none { it.id == selectedProjectId }) {
            selectedProjectId = projects.first().id
        }
    }
    
    var selectedType by remember { mutableStateOf(CalculatorEngine.FootingType.ISOLATED) }
    var expandedType by remember { mutableStateOf(false) }
    var barDiameter by remember { mutableStateOf("16") }
    var barSpacing by remember { mutableStateOf("150") }
    
    var axialLoad by remember { mutableStateOf("1200") }
    var soilCapacity by remember { mutableStateOf("150") }
    var colLength by remember { mutableStateOf("600") }
    var colWidth by remember { mutableStateOf("300") }
    var fcu by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("360") }
    
    // Combined Footing and Boundary Parameters
    var axialLoad2 by remember { mutableStateOf("1000") }
    var colDistance by remember { mutableStateOf("3.5") }
    var maxLeft by remember { mutableStateOf("") }
    var maxRight by remember { mutableStateOf("") }
    var selectedCode by remember { mutableStateOf(defaultDesignCode()) }

    // Site quantities (excavation / blinding)
    var excavationDepth by remember { mutableStateOf("1.5") }
    var workingSpace by remember { mutableStateOf("0.5") }
    var blindingThickness by remember { mutableStateOf("0.1") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.screen_footing_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SectionHeader(stringResource(R.string.footing_inputs), R.drawable.ic_footing) }

            item {
                Column {
                    AppText(stringResource(R.string.footing_type_label), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedType,
                        onExpandedChange = { expandedType = !expandedType }
                    ) {
                        OutlinedTextField(
                            value = selectedType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedType,
                            onDismissRequest = { expandedType = false }
                        ) {
                            CalculatorEngine.FootingType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { AppText(type.displayName) },
                                    onClick = {
                                        selectedType = type
                                        expandedType = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FootingInputField(colLength, stringResource(R.string.footing_column_length_mm), { colLength = it }, Modifier.weight(1f))
                    FootingInputField(colWidth, stringResource(R.string.footing_column_width_mm), { colWidth = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FootingInputField(fcu, stringResource(R.string.footing_fcu_label), { fcu = it }, Modifier.weight(1f))
                    FootingInputField(fy, stringResource(R.string.footing_fy_label), { fy = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FootingInputField(axialLoad, stringResource(R.string.footing_pu1_label), { axialLoad = it }, Modifier.weight(1f))
                    FootingInputField(soilCapacity, stringResource(R.string.footing_soil_label), { soilCapacity = it }, Modifier.weight(1f))
                }
            }

            if (selectedType == CalculatorEngine.FootingType.COMBINED) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FootingInputField(axialLoad2, stringResource(R.string.footing_pu2_label), { axialLoad2 = it }, Modifier.weight(1f))
                        FootingInputField(colDistance, stringResource(R.string.footing_distance_label), { colDistance = it }, Modifier.weight(1f))
                    }
                }
            }

            item {
                AppText(stringResource(R.string.footing_boundary_label), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FootingInputField(maxLeft, stringResource(R.string.footing_max_left_label), { maxLeft = it }, Modifier.weight(1f))
                    FootingInputField(maxRight, stringResource(R.string.footing_max_right_label), { maxRight = it }, Modifier.weight(1f))
                }
            }

            item {
                AppText(stringResource(R.string.footing_rebar_options), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FootingInputField(barDiameter, stringResource(R.string.footing_bar_diameter), { barDiameter = it }, Modifier.weight(1f))
                    FootingInputField(barSpacing, stringResource(R.string.footing_bar_spacing), { barSpacing = it }, Modifier.weight(1f))
                }
            }

            item {
                DesignCodeSelectorRow(
                    selectedCode = selectedCode,
                    onCodeSelected = { selectedCode = it }
                )
            }

            item {
                AppText(stringResource(R.string.boq_site_title), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FootingInputField(excavationDepth, stringResource(R.string.boq_excavation_depth), { excavationDepth = it }, Modifier.weight(1f))
                    FootingInputField(workingSpace, stringResource(R.string.boq_working_space), { workingSpace = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FootingInputField(blindingThickness, stringResource(R.string.boq_blinding_thickness), { blindingThickness = it }, Modifier.weight(1f))
                }
            }


            item {
                val inputValid = axialLoad.toDoubleOrNull()?.let { it > 0 } == true
                        && fcu.toDoubleOrNull()?.let { it > 0 } == true
                        && fy.toDoubleOrNull()?.let { it > 0 } == true
                        && soilCapacity.toDoubleOrNull()?.let { it > 0 } == true
                        && colWidth.toDoubleOrNull()?.let { it > 0 } == true
                        && colLength.toDoubleOrNull()?.let { it > 0 } == true
                if (!inputValid) {
                    AppText(
                        stringResource(R.string.input_invalid_numbers),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                AppButton(
                    label = stringResource(R.string.footing_design_now),
                    onClick = {
                        if (!inputValid) return@AppButton
                        viewModel.excavationDepthM = excavationDepth.toDoubleOrNull() ?: 0.0
                        viewModel.workingSpaceM = workingSpace.toDoubleOrNull() ?: 0.5
                        viewModel.blindingThicknessM = blindingThickness.toDoubleOrNull() ?: 0.0
                        viewModel.calculateFooting(
                            type = selectedType,
                            p = axialLoad.toDouble()!!,
                            fcu = fcu.toDouble()!!,
                            fy = fy.toDouble()!!,
                            soil = soilCapacity.toDouble()!!,
                            colB = colWidth.toDouble()!!,
                            colT = colLength.toDouble()!!,
                            code = selectedCode,
                            preferredDiameter = barDiameter.toIntOrNull() ?: 16,
                            preferredSpacing = barSpacing.toDoubleOrNull() ?: 150.0,
                            p2 = axialLoad2.toDoubleOrNull() ?: 0.0,
                            distance = (colDistance.toDoubleOrNull() ?: 3.5) * 1000.0,
                            maxLeft = maxLeft.toDoubleOrNull()?.let { it * 1000.0 },
                            maxRight = maxRight.toDoubleOrNull()?.let { it * 1000.0 }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && inputValid,
                    loading = isLoading,
                    icon = Icons.Default.Calculate
                )
                    }
                }
            }

            result?.let { res ->
                item { SectionHeader(stringResource(R.string.slab_results), R.drawable.ic_calculator) }
                
                item {
                    val ecoColor = if (res.isOptimal) Color(0xFF2E7D32) else Color(0xFFF57C00)
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
                                        Icon(if (res.isOptimal) Icons.Default.Verified else Icons.Default.Info, 
                                            contentDescription = null, tint = ecoColor)
                                        Spacer(Modifier.width(8.dp))
                                        AppText(
                                            if (res.isOptimal) stringResource(R.string.footing_safe_economical) 
                                            else stringResource(R.string.footing_safe_review),
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
                                        targetValue = res.utilizationRatio.toFloat(),
                                        animationSpec = tween(1000), label = ""
                                    )
                                    CircularProgressIndicator(
                                        progress = { animatedRatio },
                                        modifier = Modifier.size(60.dp),
                                        strokeWidth = 6.dp,
                                        color = when {
                                            res.utilizationRatio > 1.0 -> Color.Red
                                            res.utilizationRatio > 0.9 -> Color(0xFFFF9800)
                                            res.utilizationRatio > 0.4 -> Color(0xFF4CAF50)
                                            else -> Color(0xFF2196F3)
                                        },
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    AppText(
                                        "${(res.utilizationRatio * 100).toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))

                            ResultRow(stringResource(R.string.footing_dims), "${res.width.toInt()} x ${res.length.toInt()} mm")
                            ResultRow(stringResource(R.string.footing_thickness_label2), "${res.thickness.toInt()} mm")
                            ResultRow(stringResource(R.string.column_concrete_vol), "${"%.2f".format(res.concreteVolume)} m³")
                            ResultRow(stringResource(R.string.column_steel_weight), "${"%.2f".format(res.steelWeight)} kg")
                            ResultRow(stringResource(R.string.footing_allowable_pressure), "${"%.1f".format(res.allowablePressure)} kN/m²")
                            ResultRow(stringResource(R.string.column_estimated_cost), "${"%.2f".format(res.cost)}")
                            if (selectedType == CalculatorEngine.FootingType.COMBINED) {
                                Spacer(Modifier.height(8.dp))
                                AppText(
                                    "Method: unified combined-footing design — centroid-balanced soil pressure, " +
                                        "longitudinal and transverse bending, one-way and punching shear. " +
                                        "ECP 203-2025 uses the dedicated combined-footing engine; " +
                                        "ACI 318 / SBC 304 route through the shared footing design interface.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (res.calculationSteps.isNotEmpty()) {
                    item { CalculationStepsCard(res.calculationSteps, title = stringResource(R.string.footing_calc_steps)) }
                }

                // BBS/BOQ are heavy pure functions: remember them so recomposition
                // (typing, scrolling, snackbars) does not regenerate the tables.
                item {
                    val bbsEntries = remember(res) {
                        runCatching { BbsGenerator.generateFootingBbs("F1", res) }.getOrDefault(emptyList())
                    }
                    if (bbsEntries.isNotEmpty()) {
                        BbsTableCard(bbsEntries, title = stringResource(R.string.footing_bbs_title))
                    }
                }

                item {
                    val boqRows = remember(res, viewModel.excavationDepthM, viewModel.workingSpaceM, viewModel.blindingThicknessM) {
                        runCatching {
                            BoqEstimator.footingRows(
                                res, "F1",
                                viewModel.excavationDepthM, viewModel.workingSpaceM, viewModel.blindingThicknessM
                            )
                        }.getOrDefault(emptyList())
                    }
                    if (boqRows.isNotEmpty()) {
                        BoqTableCard(boqRows, title = stringResource(R.string.footing_boq_title))
                    }
                }

                if (res.safetyChecks.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                AppText(
                                    stringResource(R.string.safety_checks),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                res.safetyChecks.forEach { check ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppText(
                                            if (check.isSafe) "✓" else "✗",
                                            color = if (check.isSafe) Color(0xFF2E7D32) else Color(0xFFC62828),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                            AppText(check.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            AppText(
                                                "${String.format("%.2f", check.value)} / ${String.format("%.2f", check.limit)} ${check.unit}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            label = stringResource(R.string.pdf_report),
                            onClick = {
                                scope.launch {
                                    val captureBitmap = try {
                                        pdfCaptureLayer.captureToAndroidBitmap()
                                    } catch (_: Exception) { null }
                                    viewModel.pendingDrawingBitmap = captureBitmap
                                    viewModel.exportToPdf(context) { file -> }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isExporting,
                            loading = isExporting,
                            icon = Icons.Default.PictureAsPdf
                        )

                        AppButton(
                            label = stringResource(R.string.save),
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Save,
                            containerOverride = MaterialTheme.colorScheme.secondary,
                            contentOverride = onColorFor(MaterialTheme.colorScheme.secondary)
                        )
                    }
                }

                item {
                    var selectedViewMode by remember { mutableStateOf(0) }
                    val configuration = LocalConfiguration.current
                    val screenW = configuration.screenWidthDp.dp
                    // Responsive height: scale proportionally to screen width
                    val wRatio = screenW.value / 360f  // baseline 360dp
                    val drawingHeight = when (selectedViewMode) {
                        1 -> (350 * wRatio).toInt().coerceIn(250, 450)
                        2 -> (320 * wRatio).toInt().coerceIn(220, 420)
                        3 -> (280 * wRatio).toInt().coerceIn(200, 380)
                        else -> (700 * wRatio).toInt().coerceIn(500, 900)
                    }
                    // Map footing type to English name expected by the drawing
                    val footingTypeEnglish = footingDrawingTypeName(selectedType)
                    // For combined footings, compute column positions relative to footing left edge
                    val (col1XPos, col2XPos) = if (selectedType == CalculatorEngine.FootingType.COMBINED) {
                        combinedColumnPositions(
                            columnWidthMm = colWidth.toDoubleOrNull() ?: 300.0,
                            distanceM = colDistance.toDoubleOrNull() ?: 3.5
                        )
                    } else {
                        Pair(0.0, 0.0)
                    }
                    InteractiveDrawingScreen(
                        onExportDxf = { viewModel.exportToDxf(context) },
                        title = stringResource(R.string.footing_drawing_title),
                        subtitle = stringResource(R.string.footing_reinforcement_detail),
                        drawingHeightDp = drawingHeight,
                        viewModes = listOf(stringResource(R.string.view_all), stringResource(R.string.slab_view_plan), stringResource(R.string.view_section), stringResource(R.string.view_reinforcement)),
                        selectedViewMode = selectedViewMode,
                        onViewModeChanged = { selectedViewMode = it },
                        drawingContent = {
                            if (selectedType == CalculatorEngine.FootingType.COMBINED) {
                                ProfessionalCombinedFootingDrawing(
                                    footingLength = res.length.toDouble(),
                                    footingWidth = res.width.toDouble(),
                                    footingThickness = res.thickness.toDouble(),
                                    col1X = col1XPos,
                                    col1Width = colWidth.toDoubleOrNull() ?: 300.0,
                                    col1Depth = colLength.toDoubleOrNull() ?: 600.0,
                                    col2X = col2XPos,
                                    col2Width = colWidth.toDoubleOrNull() ?: 300.0,
                                    col2Depth = colLength.toDoubleOrNull() ?: 600.0,
                                    longBottomDia = res.barDiameter.toDouble(),
                                    longBottomCount = res.barsX,
                                    longTopDia = res.barDiameter.toDouble(),
                                    longTopCount = res.reinforcementTopX,
                                    transBottomDia = res.barDiameter.toDouble(),
                                    transBottomCount = res.barsY,
                                    cover = 70.0,
                                    soilPressureMax = res.soilPressure,
                                    soilPressureMin = res.soilPressure,
                                    viewMode = selectedViewMode,
                                    resultSummary = footingDrawingSummary(res),
                                    designCode = selectedCode,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                ProfessionalFootingDrawing(
                                    footingType = footingTypeEnglish,
                                    footingLengthX = res.length.toDouble(),
                                    footingLengthY = res.width.toDouble(),
                                    footingThickness = res.thickness.toDouble(),
                                    columnWidth = colWidth.toDoubleOrNull() ?: 300.0,
                                    columnDepth = colLength.toDoubleOrNull() ?: 600.0,
                                    rebarXDia = res.barDiameter.toDouble(),
                                    rebarXCount = res.barsX,
                                    rebarYDia = res.barDiameter.toDouble(),
                                    rebarYCount = res.barsY,
                                    cover = 70.0,
                                    col1X = col1XPos,
                                    col2X = col2XPos,
                                    soilPressureMax = res.soilPressure,
                                    soilPressureMin = res.soilPressure,
                                    viewMode = selectedViewMode,
                                    resultSummary = footingDrawingSummary(res),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
        // PDF drawing capture area (invisible, renders at viewMode=0)
        result?.let { res ->
            val footingTypeEnglish = footingDrawingTypeName(selectedType)
            val (col1XPos, col2XPos) = if (selectedType == CalculatorEngine.FootingType.COMBINED) {
                combinedColumnPositions(
                    columnWidthMm = colWidth.toDoubleOrNull() ?: 300.0,
                    distanceM = colDistance.toDoubleOrNull() ?: 3.5
                )
            } else {
                Pair(0.0, 0.0)
            }
            ComposeDrawingCaptureUtil.DrawingCaptureArea(
                captureLayer = pdfCaptureLayer,
                widthPx = screenWidthPx,
                heightPx = screenHeightPx
            ) {
                Box(modifier = Modifier.background(Color(0xFF1A1A2E))) {
                    if (selectedType == CalculatorEngine.FootingType.COMBINED) {
                        ProfessionalCombinedFootingDrawing(
                            footingLength = res.length.toDouble(),
                            footingWidth = res.width.toDouble(),
                            footingThickness = res.thickness.toDouble(),
                            col1X = col1XPos,
                            col1Width = colWidth.toDoubleOrNull() ?: 300.0,
                            col1Depth = colLength.toDoubleOrNull() ?: 600.0,
                            col2X = col2XPos,
                            col2Width = colWidth.toDoubleOrNull() ?: 300.0,
                            col2Depth = colLength.toDoubleOrNull() ?: 600.0,
                            longBottomDia = res.barDiameter.toDouble(),
                            longBottomCount = res.barsX,
                            longTopDia = res.barDiameter.toDouble(),
                            longTopCount = res.reinforcementTopX,
                            transBottomDia = res.barDiameter.toDouble(),
                            transBottomCount = res.barsY,
                            cover = 70.0,
                            soilPressureMax = res.soilPressure,
                            soilPressureMin = res.soilPressure,
                            viewMode = 0,
                            resultSummary = footingDrawingSummary(res),
                            designCode = selectedCode,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        ProfessionalFootingDrawing(
                            footingType = footingTypeEnglish,
                            footingLengthX = res.length.toDouble(),
                            footingLengthY = res.width.toDouble(),
                            footingThickness = res.thickness.toDouble(),
                            columnWidth = colWidth.toDoubleOrNull() ?: 300.0,
                            columnDepth = colLength.toDoubleOrNull() ?: 600.0,
                            rebarXDia = res.barDiameter.toDouble(),
                            rebarXCount = res.barsX,
                            rebarYDia = res.barDiameter.toDouble(),
                            rebarYCount = res.barsY,
                            cover = 70.0,
                            col1X = col1XPos,
                            col2X = col2XPos,
                            soilPressureMax = res.soilPressure,
                            soilPressureMin = res.soilPressure,
                            viewMode = 0,
                            resultSummary = footingDrawingSummary(res),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        // Loading overlay (single instance — the duplicate below was removed)
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AppText(stringResource(R.string.loading), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
                        label = { AppText(stringResource(R.string.footing_name_hint)) },
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
                    enabled = selectedProjectId != -1L,
                    onClick = {
                        result?.let { viewModel.saveFooting(selectedProjectId, designName, it) }
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
private fun SectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun FootingInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { AppText(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label)
        AppText(value, fontWeight = FontWeight.Bold)
    }
}

/**
 * Footing type name understood by [ProfessionalFootingDrawing].
 *
 * The drawing now implements every footing type, so each [CalculatorEngine.FootingType]
 * maps to its own plan; this used to send STRAP/STRIP/PILE_CAP to the "Isolated"
 * drawing, which produced wrong plans and reinforcement labels on screen and in PDF.
 *
 * `internal` so the regression test can assert one-to-one mapping.
 */
internal fun footingDrawingTypeName(type: CalculatorEngine.FootingType): String = when (type) {
    CalculatorEngine.FootingType.ISOLATED -> "Isolated"
    CalculatorEngine.FootingType.COMBINED -> "Combined"
    CalculatorEngine.FootingType.STRAP -> "Strap"
    CalculatorEngine.FootingType.RAFT -> "Raft"
    CalculatorEngine.FootingType.STRIP -> "Strip"
    CalculatorEngine.FootingType.PILE_CAP -> "Pile Cap"
}

/**
 * Column centres (mm, measured from the footing left edge) used by the combined
 * footing drawing: boundary column at the edge, second column one [distanceM] away.
 */
internal fun combinedColumnPositions(
    columnWidthMm: Double,
    distanceM: Double
): Pair<Double, Double> {
    val firstColCenter = columnWidthMm / 2.0 + 50.0
    return Pair(firstColCenter, firstColCenter + distanceM * 1000.0)
}


