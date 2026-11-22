package com.civileg.app.ui.compose.screens

import com.civileg.app.domain.entities.DesignCode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.ui.responsive.dimens
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.SlabDesignResult
import com.civileg.app.ui.compose.components.*
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalHordiSlabDrawing
import com.civileg.app.ui.compose.components.drawings.hordiSlabDrawingSummary
import com.civileg.app.ui.compose.components.common.BbsTableCard
import com.civileg.app.ui.compose.components.common.BoqTableCard
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.domain.calculations.utils.BbsGenerator
import com.civileg.app.viewmodel.HordiSlabViewModel
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.captureToAndroidBitmap
import kotlinx.coroutines.launch
import com.civileg.app.ui.defaultDesignCode
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor

/**
 * شاشة تصميم بلاطة الهوردي — Hordi (Ribbed/Hollow-Block) Slab Design Screen
 * One-way ribbed slab with hollow blocks between ribs.
 * Supports ECP 203 / ACI 318 / SBC 304 design codes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HordiSlabScreen(
    viewModel: HordiSlabViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    val pdfCaptureLayer = ComposeDrawingCaptureUtil.rememberDrawingCaptureLayer()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * density.density).toInt()
    val screenHeightPx = (config.screenHeightDp * density.density).toInt()
    val isArabic = com.civileg.app.utils.LocaleHelper.isArabic()

    // ── Input state ────────────────────────────────────────────────
    var designCode by remember { mutableStateOf(defaultDesignCode().name) }
    var fcu by remember { mutableStateOf("30") }
    var fcCylinder by remember { mutableStateOf("") }   // SBC-only: cylinder f'c (MPa), required
    var fy by remember { mutableStateOf("400") }
    var ribWidth by remember { mutableStateOf("120") }
    var ribSpacing by remember { mutableStateOf("500") }
    var totalThickness by remember { mutableStateOf("350") }
    var toppingThickness by remember { mutableStateOf("50") }
    var span by remember { mutableStateOf("6000") }
    var designMoment by remember { mutableStateOf("80") }
    var designShear by remember { mutableStateOf("30") }
    var loadCombination by remember { mutableStateOf(LoadCombination.DEAD_LIVE) }
    var loadCombinationExpanded by remember { mutableStateOf(false) }
    var cover by remember { mutableStateOf("25") }

    // Drawing view mode: 0=All, 1=Plan, 2=Section, 3=Detail
    var viewMode by remember { mutableStateOf(0) }
    val viewModeLabels = listOf("All", "Plan", "Section", "Detail")

    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)
    val errorMsg by viewModel.error.observeAsState()

    // Show errors via Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.hordi_slab_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.dimens.screenHorizontalPadding,
                    vertical = MaterialTheme.dimens.screenVerticalPadding
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.cardSpacing)
            ) {
                // ── Configuration Section ─────────────────────────────
                item {
                    PremiumSectionHeader(
                        title = stringResource(R.string.hordi_slab_config),
                        icon = Icons.Default.ViewModule
                    )
                }

                // Design Code Selector
                item {
                    CodeSelectorChips(
                        selectedCode = designCode,
                        codes = listOf("ECP" to "ECP 203", "ACI" to "ACI 318", "SBC" to "SBC 304"),
                        onCodeSelected = { newCode ->
                            designCode = newCode
                            val combos = LoadCombination.getByCode(
                                runCatching { DesignCode.valueOf(newCode) }.getOrDefault(DesignCode.ECP)
                            )
                            if (loadCombination !in combos) loadCombination = combos.first()
                        }
                    )
                }

                // ── Material Properties ───────────────────────────────
                item { PremiumSectionHeader(stringResource(R.string.hordi_slab_material_properties), icon = Icons.Default.Science) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(fcu, "fcu (MPa)", { fcu = it }, modifier = Modifier.weight(1f))
                        PremiumInputField(fy, "fy (MPa)", { fy = it }, modifier = Modifier.weight(1f))
                    }
                }
                if (designCode == "SBC") {
                    // SBC 304-CR-2018 is cylinder-based; no cube conversion basis (SJ-8).
                    item {
                        PremiumInputField(
                            fcCylinder,
                            stringResource(R.string.hordi_slab_fc_cylinder),
                            { fcCylinder = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ── Geometry ──────────────────────────────────────────
                item { PremiumSectionHeader(stringResource(R.string.hordi_slab_geometry), icon = Icons.Default.SquareFoot) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(
                            ribWidth, "Rib Width (mm)", { ribWidth = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            ribSpacing, "Rib Spacing (mm)", { ribSpacing = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(
                            totalThickness, "Total Thickness (mm)", { totalThickness = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            toppingThickness, "Topping Thk (mm)", { toppingThickness = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    PremiumInputField(
                        span, "Span (mm)", { span = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Design Forces ─────────────────────────────────────
                item { PremiumSectionHeader(stringResource(R.string.hordi_slab_design_forces), icon = Icons.Default.MoneyOff) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(
                            designMoment, "Moment (kN.m/rib)", { designMoment = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            designShear, "Shear (kN/rib)", { designShear = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    PremiumInputField(
                        cover, "Clear Cover (mm)", { cover = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Load Combination Dropdown
                item {
                    ExposedDropdownMenuBox(
                        expanded = loadCombinationExpanded,
                        onExpandedChange = { loadCombinationExpanded = !loadCombinationExpanded }
                    ) {
                        OutlinedTextField(
                            value = loadCombination.description,
                            onValueChange = {},
                            readOnly = true,
                            label = { AppText("Load Combination", fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = loadCombinationExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = PremiumDesignSystem.InputShape,
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = loadCombinationExpanded,
                            onDismissRequest = { loadCombinationExpanded = false }
                        ) {
                            LoadCombination.getByCode(
                                runCatching { DesignCode.valueOf(designCode) }.getOrDefault(DesignCode.ECP)
                            ).forEach { combo ->
                                DropdownMenuItem(
                                    text = { AppText(combo.description, fontSize = 13.sp) },
                                    onClick = {
                                        loadCombination = combo
                                        loadCombinationExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Calculate Button ──────────────────────────────────
                item {
                    AppButton(
                        label = stringResource(R.string.hordi_slab_design_button),
                        onClick = {
                            viewModel.clearResult()
                            viewModel.calculateHordiSlab(
                                fcu = fcu.toDoubleOrNull() ?: 30.0,
                                fy = fy.toDoubleOrNull() ?: 400.0,
                                ribWidth = ribWidth.toDoubleOrNull() ?: 120.0,
                                ribSpacing = ribSpacing.toDoubleOrNull() ?: 500.0,
                                totalThickness = totalThickness.toDoubleOrNull() ?: 350.0,
                                toppingThickness = toppingThickness.toDoubleOrNull() ?: 50.0,
                                span = span.toDoubleOrNull() ?: 6000.0,
                                designMoment = designMoment.toDoubleOrNull() ?: 80.0,
                                designShear = designShear.toDoubleOrNull() ?: 30.0,
                                loadCombination = loadCombination,
                                designCode = designCode,
                                cylinderStrength = if (designCode == "SBC") fcCylinder.toDoubleOrNull() else null
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !isLoading,
                        loading = isLoading,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        icon = Icons.Default.Engineering
                    )
                }

                // ── Error Message ─────────────────────────────────────
                errorMsg?.let { err ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x1AE53935)),
                            shape = PremiumDesignSystem.CardShape
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, "Error", tint = Color(0xFFE53935))
                                Spacer(modifier = Modifier.width(10.dp))
                                AppText(err, fontSize = 13.sp, color = Color(0xFFE53935))
                            }
                        }
                    }
                }

                // ── Results ───────────────────────────────────────────
                result?.let { res ->
                    val bbsEntries = runCatching {
                        BbsGenerator.generateHordiSlabBbs(
                            mark = "H1",
                            result = res,
                            spanMm = span.toDoubleOrNull() ?: 6000.0,
                            ribSpacingMm = ribSpacing.toDoubleOrNull() ?: 500.0,
                            coverMm = cover.toDoubleOrNull() ?: 25.0
                        )
                    }.getOrDefault(emptyList())
                    if (bbsEntries.isNotEmpty()) {
                        item { BbsTableCard(bbsEntries, title = "Hordi Slab Bar Bending Schedule") }
                    }

                    val boqRows = runCatching {
                        BoqEstimator.hordiSlabRows(
                            (span.toDoubleOrNull() ?: 6000.0) / 1000.0,
                            (totalThickness.toDoubleOrNull() ?: 350.0) / 1000.0,
                            bbsEntries.sumOf { it.totalWeightKg },
                            "H1"
                        )
                    }.getOrDefault(emptyList())
                    if (boqRows.isNotEmpty()) {
                        item { BoqTableCard(boqRows, title = "Hordi Slab Bill of Quantities") }
                    }

                    // Safety Status Gauge
                    item { HordiSafetyStatusSection(res, designCode, isArabic) }

                    // Reinforcement Result
                    item { HordiReinforcementCard(res) }

                    // Shear Check
                    item { HordiShearCheckCard(res, isArabic) }

                    // Warnings
                    if (res.warnings.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x1AFF9800)),
                                shape = PremiumDesignSystem.CardShape
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, "", tint = Color(0xFFFF9800))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        AppText(stringResource(R.string.hordi_slab_warnings), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFF9800))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    res.warnings.forEach { w ->
                                        AppText("• $w", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }

                    // ── Drawing Section ────────────────────────────────
                    item {
                        val drawingHeight = if (config.smallestScreenWidthDp >= 600) 580 else 480
                        InteractiveDrawingScreen(
                            onExportDxf = { viewModel.exportToDxf(context) },
                            title = stringResource(R.string.hordi_slab_title),
                            subtitle = "Ribbed Slab Detail",
                            viewModes = viewModeLabels,
                            selectedViewMode = viewMode,
                            onViewModeChanged = { viewMode = it },
                            drawingHeightDp = drawingHeight,
                            onExportPdf = {
                                scope.launch {
                                    val captureBitmap = try {
                                        pdfCaptureLayer.captureToAndroidBitmap()
                                    } catch (_: Exception) { null }
                                    viewModel.pendingDrawingBitmap = captureBitmap
                                    viewModel.exportToPdf(context) { file -> }
                                }
                            },
                            designCode = com.civileg.app.domain.entities.DesignCode.valueOf(designCode),
                            drawingContent = {
                                ProfessionalHordiSlabDrawing(
                                    span = span.toDoubleOrNull() ?: 6000.0,
                                    ribWidth = ribWidth.toDoubleOrNull() ?: 120.0,
                                    ribSpacing = ribSpacing.toDoubleOrNull() ?: 500.0,
                                    totalThickness = totalThickness.toDoubleOrNull() ?: 350.0,
                                    toppingThickness = toppingThickness.toDoubleOrNull() ?: 50.0,
                                    ribBottomDia = res.barDiameter,
                                    ribBottomCount = if (res.barDiameter > 0) maxOf(2, (res.providedReinforcement / (kotlin.math.PI / 4.0 * res.barDiameter * res.barDiameter)).toInt()) else 2,
                                    stirrupDia = 8.0,
                                    stirrupSpacing = 200.0,
                                    cover = cover.toDoubleOrNull() ?: 25.0,
                                    viewMode = viewMode,
                                    resultSummary = hordiSlabDrawingSummary(res),
                                    designCode = com.civileg.app.domain.entities.DesignCode.valueOf(designCode),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        )
                    }

                    // Invisible capture layer for PDF export
                    item {
                        ComposeDrawingCaptureUtil.DrawingCaptureArea(
                            captureLayer = pdfCaptureLayer,
                            widthPx = screenWidthPx,
                            heightPx = screenHeightPx
                        ) {
                            Box(modifier = Modifier.background(Color(0xFF1A1A2E))) {
                                ProfessionalHordiSlabDrawing(
                                    span = span.toDoubleOrNull() ?: 6000.0,
                                    ribWidth = ribWidth.toDoubleOrNull() ?: 120.0,
                                    ribSpacing = ribSpacing.toDoubleOrNull() ?: 500.0,
                                    totalThickness = totalThickness.toDoubleOrNull() ?: 350.0,
                                    toppingThickness = toppingThickness.toDoubleOrNull() ?: 50.0,
                                    ribBottomDia = res.barDiameter,
                                    ribBottomCount = 2,
                                    stirrupDia = 8.0,
                                    stirrupSpacing = 200.0,
                                    cover = 25.0,
                                    viewMode = viewMode,
                                    resultSummary = hordiSlabDrawingSummary(res),
                                    designCode = com.civileg.app.domain.entities.DesignCode.valueOf(designCode),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Code Notes
                    if (res.codeNotes.isNotEmpty()) {
                        item { FormulaCard(res.codeNotes, title = stringResource(R.string.hordi_slab_design_calculations)) }
                    }

                    // Export & Save
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton(
                                label = "Export PDF",
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
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                icon = Icons.Default.PictureAsPdf
                            )
                            AppButton(
                                label = "Save",
                                onClick = { /* save */ },
                                modifier = Modifier.weight(1f),
                                enabled = false,
                                style = AppButtonStyle.Outline,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                icon = Icons.Default.Save
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }

            // Loading overlay
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
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
                            AppText(stringResource(R.string.hordi_slab_calculating), fontSize = 14.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

/** Production safety section: gauge when utilization exists, explicit blocked status otherwise. */
@Composable
internal fun HordiSafetyStatusSection(res: SlabDesignResult, designCode: String, isArabic: Boolean) {
    val util = res.utilizationRatio
    if (util != null) {
        SafetyStatusCard(
            utilizationRatio = util,
            isSafe = res.isSafe,
            title = stringResource(R.string.hordi_slab_safety_title, designCode)
        )
    } else {
        ResultDataCard(
            stringResource(R.string.hordi_slab_safety_title, designCode),
            listOf(stringResource(R.string.status) to HordiResultPresentation.statusText(res.isSafe, false, isArabic)),
            icon = Icons.Default.Shield,
            accentColor = Color(0xFFE53935)
        )
    }
}

/** Production reinforcement card: per-rib main bars and rib spacing (never a misleading bar-within-rib spacing). */
@Composable
internal fun HordiReinforcementCard(res: SlabDesignResult) {
    ResultDataCard(
        stringResource(R.string.hordi_slab_reinforcement),
        listOf(
            stringResource(R.string.hordi_slab_required_as) to String.format("%.1f mm²", res.requiredReinforcement),
            stringResource(R.string.hordi_slab_provided_as) to String.format("%.1f mm²", res.providedReinforcement),
            stringResource(R.string.hordi_slab_main_bar_diameter) to String.format("φ%.0f mm", res.barDiameter),
            stringResource(R.string.hordi_slab_rib_spacing_result) to String.format("@%.0f mm", res.barSpacing)
        ),
        icon = Icons.Default.Build,
        accentColor = Color(0xFF1565C0)
    )
}

/** Production shear card: blocked shear carries its not-evaluated reason, never a substitute value. */
@Composable
internal fun HordiShearCheckCard(res: SlabDesignResult, isArabic: Boolean) {
    ResultDataCard(
        stringResource(R.string.hordi_slab_shear_check),
        listOf(
            stringResource(R.string.hordi_slab_shear_capacity) to HordiResultPresentation.shearCapacityText(res.shearCapacity, isArabic),
            stringResource(R.string.hordi_slab_adopted_thickness) to String.format("%.0f mm", res.minThickness),
            stringResource(R.string.hordi_slab_utilization) to HordiResultPresentation.utilizationText(res.utilizationRatio, isArabic),
            stringResource(R.string.status) to HordiResultPresentation.statusText(res.isSafe, res.utilizationRatio != null, isArabic)
        ),
        icon = Icons.Default.Shield,
        accentColor = if (res.isSafe) Color(0xFF2E7D32) else Color(0xFFE53935)
    )
}
