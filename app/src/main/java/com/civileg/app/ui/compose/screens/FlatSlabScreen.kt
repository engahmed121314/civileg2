package com.civileg.app.ui.compose.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.domain.*
import com.civileg.app.ui.compose.components.*
import com.civileg.app.ui.compose.components.drawings.ProfessionalFlatSlabDrawing
import com.civileg.app.viewmodel.FlatSlabViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlatSlabScreen(
    viewModel: FlatSlabViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Input state ────────────────────────────────────────────────
    var panelType by remember { mutableStateOf(PanelType.INTERIOR) }
    var designMethod by remember { mutableStateOf(DesignMethod.DDM) }
    var designCode by remember { mutableStateOf("ECP") }
    var lx by remember { mutableStateOf("6.0") }
    var ly by remember { mutableStateOf("7.5") }
    var slabThickness by remember { mutableStateOf("250") }
    var dropThickness by remember { mutableStateOf("0") }
    var dropSizeX by remember { mutableStateOf("0") }
    var dropSizeY by remember { mutableStateOf("0") }
    var columnWidth by remember { mutableStateOf("400") }
    var columnDepth by remember { mutableStateOf("400") }
    var fcu by remember { mutableStateOf("30") }
    var fy by remember { mutableStateOf("400") }
    var liveLoad by remember { mutableStateOf("3.0") }
    var floorFinish by remember { mutableStateOf("2.0") }
    var clearCover by remember { mutableStateOf("25") }
    var showDropInputs by remember { mutableStateOf(false) }

    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)
    val errorMsg by viewModel.error.observeAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fs_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Panel Type Selector ─────────────────────────────
                item {
                    PremiumSectionHeader(
                        title = stringResource(R.string.fs_config_section),
                        icon = Icons.Default.ViewModule
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PanelType.entries.forEach { type ->
                            val selected = type == panelType
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = PremiumDesignSystem.ChipShape,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shadowElevation = if (selected) 4.dp else 0.dp
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.clickable { panelType = type }
                                ) {
                                    Text(
                                        type.name,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Design Code Selector ─────────────────────────────
                item {
                    CodeSelectorChips(
                        selectedCode = designCode,
                        codes = listOf("ECP" to "ECP 203", "ACI" to "ACI 318", "SBC" to "SBC 304"),
                        onCodeSelected = { designCode = it }
                    )
                }

                // ── Geometry ──────────────────────────────────────────
                item { PremiumSectionHeader(stringResource(R.string.fs_geometry_section), icon = Icons.Default.SquareFoot) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = lx, onValueChange = { lx = it },
                            label = { Text(stringResource(R.string.fs_lx), fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = PremiumDesignSystem.InputShape, singleLine = true
                        )
                        OutlinedTextField(
                            value = ly, onValueChange = { ly = it },
                            label = { Text(stringResource(R.string.fs_ly), fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                            slabThickness, stringResource(R.string.fs_slab_thk), { slabThickness = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            columnWidth, stringResource(R.string.fs_col_width), { columnWidth = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    PremiumInputField(
                        columnDepth, stringResource(R.string.fs_col_depth), { columnDepth = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Drop Panel Toggle ─────────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.fs_drop_panel), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Switch(checked = showDropInputs, onCheckedChange = { showDropInputs = it })
                    }
                }
                if (showDropInputs) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumInputField(
                                dropThickness, stringResource(R.string.fs_drop_thk), { dropThickness = it },
                                modifier = Modifier.weight(1f)
                            )
                            PremiumInputField(
                                dropSizeX, stringResource(R.string.fs_drop_size_x), { dropSizeX = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        PremiumInputField(
                            dropSizeY, stringResource(R.string.fs_drop_size_y), { dropSizeY = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ── Material Properties ───────────────────────────────
                item { PremiumSectionHeader(stringResource(R.string.fs_materials_section), icon = Icons.Default.Science) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(fcu, stringResource(R.string.fs_fcu), { fcu = it }, modifier = Modifier.weight(1f))
                        PremiumInputField(fy, stringResource(R.string.fs_fy), { fy = it }, modifier = Modifier.weight(1f))
                    }
                }

                // ── Loading ───────────────────────────────────────────
                item { PremiumSectionHeader(stringResource(R.string.fs_loading_section), icon = Icons.Default.Layers) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(liveLoad, stringResource(R.string.fs_live_load), { liveLoad = it }, modifier = Modifier.weight(1f))
                        PremiumInputField(floorFinish, stringResource(R.string.fs_floor_finish), { floorFinish = it }, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    PremiumInputField(
                        clearCover, stringResource(R.string.fs_clear_cover), { clearCover = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Calculate Button ──────────────────────────────────
                item {
                    Button(
                        onClick = {
                            viewModel.clearResult()
                            viewModel.calculateFlatSlab(
                                panelType = panelType, designMethod = designMethod,
                                lx = (lx.toDoubleOrNull() ?: 0.0) * 1000.0,
                                ly = (ly.toDoubleOrNull() ?: 0.0) * 1000.0,
                                slabThickness = slabThickness.toDoubleOrNull() ?: 250.0,
                                dropThickness = if (showDropInputs) (dropThickness.toDoubleOrNull() ?: 0.0) else 0.0,
                                dropSizeX = if (showDropInputs) (dropSizeX.toDoubleOrNull() ?: 0.0) else 0.0,
                                dropSizeY = if (showDropInputs) (dropSizeY.toDoubleOrNull() ?: 0.0) else 0.0,
                                columnWidth = columnWidth.toDoubleOrNull() ?: 400.0,
                                columnDepth = columnDepth.toDoubleOrNull() ?: 400.0,
                                fcu = fcu.toDoubleOrNull() ?: 30.0,
                                fy = fy.toDoubleOrNull() ?: 400.0,
                                liveLoad = liveLoad.toDoubleOrNull() ?: 3.0,
                                floorFinish = floorFinish.toDoubleOrNull() ?: 2.0,
                                numberOfFloors = 10, clearCover = clearCover.toDoubleOrNull() ?: 25.0,
                                storyHeight = 3.0, designCode = designCode
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = PremiumDesignSystem.ButtonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                        } else {
                            Icon(Icons.Default.Engineering, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            if (isLoading) stringResource(R.string.pile_designing) else stringResource(R.string.fs_design_button),
                            fontWeight = FontWeight.Bold, fontSize = 15.sp
                        )
                    }
                }

                // ── Error Message ─────────────────────────────────────
                errorMsg?.let { err ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x1AE53935)),
                            shape = PremiumDesignSystem.CardShape
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, stringResource(R.string.error), tint = Color(0xFFE53935))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(err, fontSize = 13.sp, color = Color(0xFFE53935))
                            }
                        }
                    }
                }

                // ── Results ───────────────────────────────────────────
                result?.let { res ->
                    // Utilization Gauge
                    item {
                        SafetyStatusCard(
                            utilizationRatio = res.utilizationRatio, isSafe = res.isSafe,
                            title = "${stringResource(R.string.home_flat_slab)} — ${panelType.displayName}"
                        )
                    }

                    // Load Summary
                    item {
                        ResultDataCard(
                            stringResource(R.string.fs_load_summary),
                            listOf(
                                stringResource(R.string.fs_dead_load) to String.format("%.2f kN/m²", res.totalDeadLoad),
                                stringResource(R.string.fs_factored_wu) to String.format("%.2f kN/m²", res.totalFactoredLoad),
                                stringResource(R.string.fs_mox) to String.format("%.1f kN.m", res.panelMomentX),
                                stringResource(R.string.fs_moy) to String.format("%.1f kN.m", res.panelMomentY)
                            ),
                            icon = Icons.Default.Layers, accentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    // Column Strip
                    item {
                        ResultDataCard(
                            stringResource(R.string.fs_col_strip),
                            listOf(
                                stringResource(R.string.fs_pos_m) to String.format("%.1f kN.m", res.columnStripMomentPos),
                                stringResource(R.string.fs_neg_m) to String.format("%.1f kN.m", res.columnStripMomentNeg),
                                stringResource(R.string.fs_top_rebar) to res.columnStripTopRebar.barString,
                                stringResource(R.string.fs_top_as) to String.format("%.0f/%.0f mm²", res.columnStripTopRebar.requiredArea, res.columnStripTopRebar.providedArea),
                                stringResource(R.string.fs_bot_rebar) to res.columnStripBotRebar.barString,
                                stringResource(R.string.fs_bot_as) to String.format("%.0f/%.0f mm²", res.columnStripBotRebar.requiredArea, res.columnStripBotRebar.providedArea),
                                stringResource(R.string.fs_width) to String.format("%.0f mm", res.columnStripWidthX)
                            ),
                            icon = Icons.Default.ViewColumn, accentColor = Color(0xFF1565C0)
                        )
                    }

                    // Middle Strip
                    item {
                        ResultDataCard(
                            stringResource(R.string.fs_mid_strip),
                            listOf(
                                stringResource(R.string.fs_pos_m) to String.format("%.1f kN.m", res.middleStripMomentPos),
                                stringResource(R.string.fs_neg_m) to String.format("%.1f kN.m", res.middleStripMomentNeg),
                                stringResource(R.string.fs_top_rebar) to res.middleStripTopRebar.barString,
                                stringResource(R.string.fs_bot_rebar) to res.middleStripBotRebar.barString
                            ),
                            icon = Icons.Default.ViewStream, accentColor = Color(0xFF2E7D32)
                        )
                    }

                    // Punching Shear
                    item {
                        ResultDataCard(
                            stringResource(R.string.fs_punching_shear),
                            listOf(
                                "Vu" to String.format("%.1f kN", res.punchingShearVu),
                                "Vc" to String.format("%.1f kN", res.punchingShearVc),
                                stringResource(R.string.fs_perimeter) to String.format("%.0f mm", res.punchingPerimeter),
                                stringResource(R.string.status) to if (res.punchingShearOk) "PASS ✓" else "FAIL ✗"
                            ),
                            icon = Icons.Default.Shield,
                            accentColor = if (res.punchingShearOk) Color(0xFF2E7D32) else Color(0xFFE53935)
                        )
                    }

                    // Punching reinforcement
                    res.punchingReinforcement?.let { pr ->
                        item {
                            ResultDataCard(
                                stringResource(R.string.fs_shear_reinf),
                                listOf(
                                    stringResource(R.string.type) to "Shear Studs",
                                    stringResource(R.string.rebar_dia_label) to "φ${pr.diameter} mm",
                                    stringResource(R.string.rebar_bar_spacing_label) to "@${pr.spacing} mm",
                                    stringResource(R.string.total) to "${pr.bars} studs"
                                ),
                                icon = Icons.Default.Build, accentColor = Color(0xFFF57C00)
                            )
                        }
                    }

                    // Deflection
                    item {
                        ResultDataCard(
                            stringResource(R.string.fs_deflection),
                            listOf(
                                stringResource(R.string.fs_long_term_delta) to String.format("%.2f mm", res.deflection),
                                stringResource(R.string.pile_allowable_set) to String.format("%.2f mm", res.allowableDeflection),
                                stringResource(R.string.status) to if (res.deflectionOk) "PASS ✓" else "FAIL ✗"
                            ),
                            icon = Icons.Default.Straighten,
                            accentColor = if (res.deflectionOk) Color(0xFF2E7D32) else Color(0xFFE53935)
                        )
                    }

                    // Drop Panel
                    item {
                        ResultDataCard(
                            stringResource(R.string.fs_drop_panel),
                            listOf(
                                "Required" to if (res.dropRequired) "Yes" else "No",
                                "Recommended" to String.format("%.0f mm", res.dropThickness)
                            ),
                            icon = Icons.Default.Dashboard,
                            accentColor = if (res.dropRequired) Color(0xFFF57C00) else Color(0xFF2E7D32)
                        )
                    }

                    // Quantities
                    item {
                        ResultDataCard(
                            stringResource(R.string.fs_quantities),
                            listOf(
                                stringResource(R.string.fs_concrete) to String.format("%.3f m³/panel", res.concreteVolumePerPanel),
                                stringResource(R.string.fs_steel) to String.format("%.1f kg/panel", res.steelWeightPerPanel)
                            ),
                            icon = Icons.Default.Inventory2, accentColor = Color(0xFF6A1B9A)
                        )
                    }

                    // Safety Checks
                    item {
                        SafetyCheckList(res.safetyChecks.map {
                            Triple(it.name, String.format("%.1f / %.1f %s", it.calculated, it.limit, it.unit), it.passed)
                        })
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
                                        Text("Warnings", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFF9800))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    res.warnings.forEach { w ->
                                        Text("• $w", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }

                    // Drawing
                    item { PremiumSectionHeader(stringResource(R.string.fs_panel_plan), icon = Icons.Default.Draw) }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().height(320.dp),
                            shape = PremiumDesignSystem.CardShape,
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            ProfessionalFlatSlabDrawing(
                                lx = lx.toDoubleOrNull() ?: 6.0,
                                ly = ly.toDoubleOrNull() ?: 7.5,
                                slabThickness = slabThickness.toDoubleOrNull() ?: 250.0,
                                columnWidth = columnWidth.toDoubleOrNull() ?: 400.0,
                                columnDepth = columnDepth.toDoubleOrNull() ?: 400.0,
                                dropSizeX = if (showDropInputs) (dropSizeX.toDoubleOrNull() ?: 0.0) else 0.0,
                                dropSizeY = if (showDropInputs) (dropSizeY.toDoubleOrNull() ?: 0.0) else 0.0,
                                colStripWidthX = res.columnStripWidthX,
                                colStripWidthY = res.columnStripWidthY,
                                colTopRebar = res.columnStripTopRebar,
                                colBotRebar = res.columnStripBotRebar,
                                midTopRebar = res.middleStripTopRebar,
                                midBotRebar = res.middleStripBotRebar,
                                isSafe = res.isSafe, panelType = panelType,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Code Notes
                    if (res.codeNotes.isNotEmpty()) {
                        item { FormulaCard(res.codeNotes, title = "Design Calculations") }
                    }

                    // Export
                    item {
                        PremiumActionButtons(
                            onExportPdf = { viewModel.exportToPdf(context) {} },
                            onSave = {}, isExporting = isExporting,
                            extraActions = {
                                // ADR-004: canonical P043 model-derived single-sheet DXF
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val outcome = withContext(Dispatchers.IO) {
                                                com.civileg.app.utils.CadDxfExporter.exportFlatSlab(
                                                    context, res,
                                                    FlatSlabInput(
                                                        panelType = panelType,
                                                        designMethod = designMethod,
                                                        lx = (lx.toDoubleOrNull() ?: 0.0) * 1000.0,
                                                        ly = (ly.toDoubleOrNull() ?: 0.0) * 1000.0,
                                                        slabThickness = slabThickness.toDoubleOrNull() ?: 250.0,
                                                        dropThickness = if (showDropInputs) (dropThickness.toDoubleOrNull() ?: 0.0) else 0.0,
                                                        dropSizeX = if (showDropInputs) (dropSizeX.toDoubleOrNull() ?: 0.0) else 0.0,
                                                        dropSizeY = if (showDropInputs) (dropSizeY.toDoubleOrNull() ?: 0.0) else 0.0,
                                                        columnWidth = columnWidth.toDoubleOrNull() ?: 400.0,
                                                        columnDepth = columnDepth.toDoubleOrNull() ?: 400.0,
                                                        clearCover = clearCover.toDoubleOrNull() ?: 25.0,
                                                        fcu = fcu.toDoubleOrNull() ?: 30.0,
                                                        fy = fy.toDoubleOrNull() ?: 400.0,
                                                        liveLoad = liveLoad.toDoubleOrNull() ?: 3.0,
                                                        floorFinish = floorFinish.toDoubleOrNull() ?: 2.0
                                                    ),
                                                    designCode
                                                )
                                            }
                                            com.civileg.app.utils.ExportUtils.handleDxfOutcome(context, outcome)
                                        }
                                    },
                                    modifier = Modifier.width(96.dp),
                                    shape = PremiumDesignSystem.ButtonShape,
                                    enabled = !isLoading
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("DXF", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}
