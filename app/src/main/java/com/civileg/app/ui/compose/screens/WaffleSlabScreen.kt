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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.ui.responsive.dimens
import com.civileg.app.domain.*
import com.civileg.app.ui.compose.components.*
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalWaffleSlabDrawing
import com.civileg.app.ui.compose.components.drawings.waffleSlabDrawingSummary
import com.civileg.app.ui.compose.components.common.BbsTableCard
import com.civileg.app.ui.compose.components.common.BoqTableCard
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.domain.calculations.utils.BbsGenerator
import com.civileg.app.viewmodel.WaffleSlabViewModel
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

/**
 * شاشة تصميم بلاطة الوافل — Waffle Slab Design Screen
 * Two-way ribbed slab with ribs in both directions, solid heads at columns.
 * Supports ECP 203 / ACI 318 / SBC 304 design codes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaffleSlabScreen(
    viewModel: WaffleSlabViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    val pdfCaptureLayer = ComposeDrawingCaptureUtil.rememberDrawingCaptureLayer()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * density.density).toInt()
    val screenHeightPx = (config.screenHeightDp * density.density).toInt()

    // ── Input state ────────────────────────────────────────────────
    var designCode by remember { mutableStateOf(defaultDesignCode().name) }
    var lx by remember { mutableStateOf("6000") }
    var ly by remember { mutableStateOf("7500") }
    var ribSpacing by remember { mutableStateOf("600") }
    var ribWidth by remember { mutableStateOf("150") }
    var ribHeight by remember { mutableStateOf("300") }
    var toppingThickness by remember { mutableStateOf("50") }
    var solidHeadSize by remember { mutableStateOf("1000") }
    var columnWidth by remember { mutableStateOf("400") }
    var fcu by remember { mutableStateOf("30") }
    var fy by remember { mutableStateOf("400") }
    var liveLoad by remember { mutableStateOf("3") }
    var deadLoad by remember { mutableStateOf("3") }
    var clearCover by remember { mutableStateOf("25") }

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
                title = { AppText(stringResource(R.string.waffle_slab_title), fontWeight = FontWeight.Bold) },
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
                        title = stringResource(R.string.waffle_slab_config),
                        icon = Icons.Default.ViewModule
                    )
                }

                // Design Code Selector
                item {
                    CodeSelectorChips(
                        selectedCode = designCode,
                        codes = listOf("ECP" to "ECP 203", "ACI" to "ACI 318", "SBC" to "SBC 304"),
                        onCodeSelected = { designCode = it }
                    )
                }

                // ── Geometry ──────────────────────────────────────────
                item { PremiumSectionHeader(stringResource(R.string.waffle_slab_geometry), icon = Icons.Default.SquareFoot) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = lx, onValueChange = { lx = it },
                            label = { AppText("Lx (mm)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = PremiumDesignSystem.InputShape, singleLine = true
                        )
                        OutlinedTextField(
                            value = ly, onValueChange = { ly = it },
                            label = { AppText("Ly (mm)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = PremiumDesignSystem.InputShape, singleLine = true
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(
                            ribSpacing, "Rib Spacing (mm)", { ribSpacing = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            ribWidth, "Rib Width (mm)", { ribWidth = it },
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
                            ribHeight, "Rib Height (mm)", { ribHeight = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            toppingThickness, "Topping Thk (mm)", { toppingThickness = it },
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
                            solidHeadSize, "Solid Head (mm)", { solidHeadSize = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            columnWidth, "Column Width (mm)", { columnWidth = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── Material Properties ───────────────────────────────
                item { PremiumSectionHeader(stringResource(R.string.waffle_slab_material_properties), icon = Icons.Default.Science) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(fcu, "fcu (MPa)", { fcu = it }, modifier = Modifier.weight(1f))
                        PremiumInputField(fy, "fy (MPa)", { fy = it }, modifier = Modifier.weight(1f))
                    }
                }

                // ── Loading ───────────────────────────────────────────
                item { PremiumSectionHeader(stringResource(R.string.waffle_slab_loading), icon = Icons.Default.Layers) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(liveLoad, "Live Load (kN/m²)", { liveLoad = it }, modifier = Modifier.weight(1f))
                        PremiumInputField(deadLoad, "Dead Load (kN/m²)", { deadLoad = it }, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    PremiumInputField(
                        clearCover, "Clear Cover (mm)", { clearCover = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Calculate Button ──────────────────────────────────
                item {
                    AppButton(
                        label = stringResource(R.string.waffle_slab_design_button),
                        onClick = {
                            viewModel.clearResult()
                            viewModel.calculateWaffleSlab(
                                lx = lx.toDoubleOrNull() ?: 6000.0,
                                ly = ly.toDoubleOrNull() ?: 7500.0,
                                ribSpacing = ribSpacing.toDoubleOrNull() ?: 600.0,
                                ribWidth = ribWidth.toDoubleOrNull() ?: 150.0,
                                ribHeight = ribHeight.toDoubleOrNull() ?: 300.0,
                                toppingThickness = toppingThickness.toDoubleOrNull() ?: 50.0,
                                solidHeadSize = solidHeadSize.toDoubleOrNull() ?: 1000.0,
                                columnWidth = columnWidth.toDoubleOrNull() ?: 400.0,
                                columnDepth = columnWidth.toDoubleOrNull() ?: 400.0,
                                fcu = fcu.toDoubleOrNull() ?: 30.0,
                                fy = fy.toDoubleOrNull() ?: 400.0,
                                liveLoad = liveLoad.toDoubleOrNull() ?: 3.0,
                                deadLoad = deadLoad.toDoubleOrNull() ?: 3.0,
                                clearCover = clearCover.toDoubleOrNull() ?: 25.0,
                                designCode = designCode
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
                        BbsGenerator.generateWaffleSlabBbs(
                            mark = "WS1",
                            result = res,
                            lxMm = lx.toDoubleOrNull() ?: 6000.0,
                            lyMm = ly.toDoubleOrNull() ?: 7500.0,
                            ribSpacingMm = ribSpacing.toDoubleOrNull() ?: 600.0,
                            solidHeadSizeMm = solidHeadSize.toDoubleOrNull() ?: 1000.0,
                            coverMm = clearCover.toDoubleOrNull() ?: 25.0
                        )
                    }.getOrDefault(emptyList())
                    if (bbsEntries.isNotEmpty()) {
                        item { BbsTableCard(bbsEntries, title = "Waffle Slab Bar Bending Schedule") }
                    }

                    val boqRows = runCatching {
                        BoqEstimator.waffleSlabRows(
                            res,
                            (lx.toDoubleOrNull() ?: 6000.0) / 1000.0,
                            (ly.toDoubleOrNull() ?: 7500.0) / 1000.0,
                            "WS1"
                        )
                    }.getOrDefault(emptyList())
                    if (boqRows.isNotEmpty()) {
                        item { BoqTableCard(boqRows, title = "Waffle Slab Bill of Quantities") }
                    }

                    // Safety Status Gauge
                    item {
                        ResultDataCard(
                            stringResource(R.string.waffle_slab_safety_title, designCode),
                            listOf(
                                "Verdict" to res.status.label,
                                "Utilization (assessed checks)" to String.format("%.1f%%", res.utilizationRatio * 100),
                                "Not assessed" to (if (res.notAssessed.isEmpty()) "none" else res.notAssessed.joinToString("\n"))
                            ),
                            icon = Icons.Default.Shield,
                            accentColor = waffleStatusColor(res.status)
                        )
                    }

                    // Rib Design
                    res.ribDesign?.let { rib ->
                        item {
                            ResultDataCard(
                                stringResource(R.string.waffle_slab_rib_design),
                                listOf(
                                    stringResource(R.string.waffle_slab_bottom_bars) to rib.flexureReinforcement.barString,
                                    "As prov/req" to String.format("%.0f / %.0f mm²", rib.flexureReinforcement.providedArea, rib.flexureReinforcement.requiredArea),
                                    stringResource(R.string.waffle_slab_shear_reinf) to (if (rib.shearReinforcement.requiredShearReinforcement > 0.0) String.format("Av/s %.3f mm²/mm required, not detailed", rib.shearReinforcement.requiredShearReinforcement) else "not required (Vu ≤ φVc)"),
                                    "Rib Utilization" to String.format("%.1f%%", rib.utilizationRatio * 100),
                                    "Rib check" to rib.status.label
                                ),
                                icon = Icons.Default.ViewColumn,
                                accentColor = waffleStatusColor(rib.status)
                            )
                        }
                    }

                    // Solid Head Design
                    res.solidHeadDesign?.let { head ->
                        item {
                            ResultDataCard(
                                stringResource(R.string.waffle_slab_solid_head_design),
                                listOf(
                                    "Flexure Rebar" to (if (head.flexureReinforcement.bars > 0) head.flexureReinforcement.barString + " (indicative)" else "not designed"),
                                    "As prov/req" to String.format("%.0f / %.0f mm²", head.flexureReinforcement.providedArea, head.flexureReinforcement.requiredArea),
                                    "Punching Vu" to String.format("%.1f kN", head.punchingShear.vu),
                                    "Punching Vc" to String.format("%.1f kN", head.punchingShear.vc),
                                    "Punching" to head.punchingShear.status.label,
                                    "Head check" to head.status.label
                                ),
                                icon = Icons.Default.Dashboard,
                                accentColor = waffleStatusColor(head.status)
                            )
                        }
                    }

                    // Punching Shear (top-level)
                    res.punchingShearCheck?.let { punch ->
                        item {
                            ResultDataCard(
                                stringResource(R.string.waffle_slab_punching_shear_check),
                                listOf(
                                    "Vu" to String.format("%.1f kN", punch.vu),
                                    "Vc" to String.format("%.1f kN", punch.vc),
                                    "Utilization" to String.format("%.1f%%", punch.utilizationRatio * 100),
                                    "Status" to punch.status.label
                                ),
                                icon = Icons.Default.Shield,
                                accentColor = waffleStatusColor(punch.status)
                            )
                        }
                    }

                    // Deflection Check
                    res.deflectionCheck?.let { defl ->
                        item {
                            ResultDataCard(
                                stringResource(R.string.waffle_slab_deflection_check),
                                listOf("Status" to "NOT ASSESSED (no supported method)"),
                                icon = Icons.Default.Straighten,
                                accentColor = waffleStatusColor(com.civileg.app.domain.calculations.base.CheckStatus.NOT_ASSESSED)
                            )
                        }
                    }

                    // Quantities
                    item {
                        ResultDataCard(
                            stringResource(R.string.waffle_slab_quantities),
                            listOf(
                                stringResource(R.string.waffle_slab_concrete_volume) to String.format("%.3f m³", res.concreteVolume),
                                stringResource(R.string.waffle_slab_steel_weight) to String.format("%.1f kg", res.steelWeight)
                            ),
                            icon = Icons.Default.Inventory2,
                            accentColor = Color(0xFF6A1B9A)
                        )
                    }

                    // Safety Checks List
                    if (res.safetyChecks.isNotEmpty()) {
                        item {
                            SafetyCheckList(res.safetyChecks.map {
                                Triple(it.name, String.format("%.1f / %.1f %s", it.calculated, it.limit, it.unit), it.passed)
                            })
                        }
                    }

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
                                        AppText(stringResource(R.string.waffle_slab_warnings), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFF9800))
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
                            title = stringResource(R.string.waffle_slab_title),
                            subtitle = "Two-Way Ribbed Slab Detail",
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
                                viewModel.lastCalculatedInput?.let { input ->
                                    ProfessionalWaffleSlabDrawing(
                                        lx = input.lx,
                                        ly = input.ly,
                                        ribSpacing = input.ribSpacing,
                                        ribWidth = input.ribWidth,
                                        ribHeight = input.ribHeight,
                                        toppingThickness = input.toppingThickness,
                                        solidHeadSize = input.solidHeadSize,
                                        columnWidth = input.columnWidth,
                                        ribBottomDia = res.ribDesign?.flexureReinforcement?.diameter?.toDouble() ?: 16.0,
                                        ribBottomCount = res.ribDesign?.flexureReinforcement?.bars ?: 2,
                                        ribTopDia = res.ribDesign?.shearReinforcement?.stirrupDiameter ?: 10.0,
                                        ribTopCount = res.ribDesign?.shearReinforcement?.numLegs ?: 2,
                                        headBottomDia = res.solidHeadDesign?.flexureReinforcement?.diameter?.toDouble() ?: 16.0,
                                        headBottomCount = res.solidHeadDesign?.flexureReinforcement?.bars ?: 4,
                                        cover = input.clearCover,
                                        viewMode = viewMode,
                                        resultSummary = waffleSlabDrawingSummary(res),
                                        designCode = input.designCode,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
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
                                viewModel.lastCalculatedInput?.let { input ->
                                    ProfessionalWaffleSlabDrawing(
                                        lx = input.lx,
                                        ly = input.ly,
                                        ribSpacing = input.ribSpacing,
                                        ribWidth = input.ribWidth,
                                        ribHeight = input.ribHeight,
                                        toppingThickness = input.toppingThickness,
                                        solidHeadSize = input.solidHeadSize,
                                        columnWidth = input.columnWidth,
                                        ribBottomDia = res.ribDesign?.flexureReinforcement?.diameter?.toDouble() ?: 16.0,
                                        ribBottomCount = res.ribDesign?.flexureReinforcement?.bars ?: 2,
                                        ribTopDia = res.ribDesign?.shearReinforcement?.stirrupDiameter ?: 10.0,
                                        ribTopCount = res.ribDesign?.shearReinforcement?.numLegs ?: 2,
                                        headBottomDia = res.solidHeadDesign?.flexureReinforcement?.diameter?.toDouble() ?: 16.0,
                                        headBottomCount = res.solidHeadDesign?.flexureReinforcement?.bars ?: 4,
                                        cover = input.clearCover,
                                        viewMode = viewMode,
                                        resultSummary = waffleSlabDrawingSummary(res),
                                        designCode = input.designCode,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    // Code Notes
                    if (res.codeNotes.isNotEmpty()) {
                        item { FormulaCard(res.codeNotes, title = stringResource(R.string.waffle_slab_design_calculations)) }
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
                            AppText(stringResource(R.string.waffle_slab_calculating), fontSize = 14.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}


private fun waffleStatusColor(status: com.civileg.app.domain.calculations.base.CheckStatus): Color = when (status) {
    com.civileg.app.domain.calculations.base.CheckStatus.PASS -> Color(0xFF2E7D32)
    com.civileg.app.domain.calculations.base.CheckStatus.FAIL -> Color(0xFFE53935)
    else -> Color(0xFFF9A825)
}
