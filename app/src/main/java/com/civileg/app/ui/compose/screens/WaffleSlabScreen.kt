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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.civileg.app.ui.compose.components.drawings.ProfessionalWaffleSlabDrawing
import com.civileg.app.viewmodel.WaffleSlabViewModel
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.captureToAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch

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
    var designCode by remember { mutableStateOf("ECP") }
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
                title = { Text(stringResource(R.string.waffle_slab_title), fontWeight = FontWeight.Bold) },
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            label = { Text("Lx (mm)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = PremiumDesignSystem.InputShape, singleLine = true
                        )
                        OutlinedTextField(
                            value = ly, onValueChange = { ly = it },
                            label = { Text("Ly (mm)", fontSize = 12.sp) },
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
                    Button(
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
                            if (isLoading) stringResource(R.string.waffle_slab_designing) else stringResource(R.string.waffle_slab_design_button),
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
                                Icon(Icons.Default.Error, "Error", tint = Color(0xFFE53935))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(err, fontSize = 13.sp, color = Color(0xFFE53935))
                            }
                        }
                    }
                }

                // ── Results ───────────────────────────────────────────
                result?.let { res ->
                    // Safety Status Gauge
                    item {
                        SafetyStatusCard(
                            utilizationRatio = res.utilizationRatio,
                            isSafe = res.isSafe,
                            title = stringResource(R.string.waffle_slab_safety_title, designCode)
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
                                    stringResource(R.string.waffle_slab_shear_reinf) to String.format("φ%.0f @%.0f mm", rib.shearReinforcement.stirrupDiameter, rib.shearReinforcement.stirrupSpacing),
                                    stringResource(R.string.waffle_slab_shear_reinf) to String.format("φ%.0f @%.0f mm", rib.shearReinforcement.stirrupDiameter, rib.shearReinforcement.stirrupSpacing),
                                    "Rib Utilization" to String.format("%.1f%%", rib.utilizationRatio * 100),
                                    "Rib Safe" to if (rib.isSafe) "PASS ✓" else "FAIL ✗"
                                ),
                                icon = Icons.Default.ViewColumn,
                                accentColor = if (rib.isSafe) Color(0xFF2E7D32) else Color(0xFFE53935)
                            )
                        }
                    }

                    // Solid Head Design
                    res.solidHeadDesign?.let { head ->
                        item {
                            ResultDataCard(
                                stringResource(R.string.waffle_slab_solid_head_design),
                                listOf(
                                    "Flexure Rebar" to head.flexureReinforcement.barString,
                                    "As prov/req" to String.format("%.0f / %.0f mm²", head.flexureReinforcement.providedArea, head.flexureReinforcement.requiredArea),
                                    "Punching Vu" to String.format("%.1f kN", head.punchingShear.vu),
                                    "Punching Vc" to String.format("%.1f kN", head.punchingShear.vc),
                                    "Punching" to if (head.punchingShear.isSafe) "PASS ✓" else "FAIL ✗",
                                    "Head Safe" to if (head.isSafe) "PASS ✓" else "FAIL ✗"
                                ),
                                icon = Icons.Default.Dashboard,
                                accentColor = if (head.isSafe) Color(0xFF2E7D32) else Color(0xFFE53935)
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
                                    "Status" to if (punch.isSafe) "PASS ✓" else "FAIL ✗"
                                ),
                                icon = Icons.Default.Shield,
                                accentColor = if (punch.isSafe) Color(0xFF2E7D32) else Color(0xFFE53935)
                            )
                        }
                    }

                    // Deflection Check
                    res.deflectionCheck?.let { defl ->
                        item {
                            ResultDataCard(
                                stringResource(R.string.waffle_slab_deflection_check),
                                listOf(
                                    "Immediate" to String.format("%.2f mm", defl.immediate),
                                    "Long-Term" to String.format("%.2f mm", defl.longTerm),
                                    "Allowable" to String.format("%.2f mm", defl.allowable),
                                    "Ratio" to String.format("%.2f", defl.ratio),
                                    "Status" to if (defl.isSafe) "PASS ✓" else "FAIL ✗"
                                ),
                                icon = Icons.Default.Straighten,
                                accentColor = if (defl.isSafe) Color(0xFF2E7D32) else Color(0xFFE53935)
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
                                        Text(stringResource(R.string.waffle_slab_warnings), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFF9800))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    res.warnings.forEach { w ->
                                        Text("• $w", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }

                    // ── Drawing Section ────────────────────────────────
                    item { PremiumSectionHeader(stringResource(R.string.waffle_slab_engineering_drawing), icon = Icons.Default.Draw) }

                    // View mode selector
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            viewModeLabels.forEachIndexed { idx, label ->
                                val selected = idx == viewMode
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp),
                                    shape = PremiumDesignSystem.ChipShape,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shadowElevation = if (selected) 4.dp else 0.dp
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.clickable { viewMode = idx }
                                    ) {
                                        Text(
                                            label,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Drawing canvas
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().height(360.dp),
                            shape = PremiumDesignSystem.CardShape,
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            ProfessionalWaffleSlabDrawing(
                                lx = lx.toDoubleOrNull() ?: 6000.0,
                                ly = ly.toDoubleOrNull() ?: 7500.0,
                                ribSpacing = ribSpacing.toDoubleOrNull() ?: 600.0,
                                ribWidth = ribWidth.toDoubleOrNull() ?: 150.0,
                                ribHeight = ribHeight.toDoubleOrNull() ?: 300.0,
                                toppingThickness = toppingThickness.toDoubleOrNull() ?: 50.0,
                                solidHeadSize = solidHeadSize.toDoubleOrNull() ?: 1000.0,
                                columnWidth = columnWidth.toDoubleOrNull() ?: 400.0,
                                ribBottomDia = res.ribDesign?.flexureReinforcement?.diameter?.toDouble() ?: 16.0,
                                ribBottomCount = res.ribDesign?.flexureReinforcement?.bars ?: 2,
                                ribTopDia = res.ribDesign?.shearReinforcement?.stirrupDiameter ?: 10.0,
                                ribTopCount = res.ribDesign?.shearReinforcement?.numLegs ?: 2,
                                headBottomDia = res.solidHeadDesign?.flexureReinforcement?.diameter?.toDouble() ?: 16.0,
                                headBottomCount = res.solidHeadDesign?.flexureReinforcement?.bars ?: 4,
                                cover = clearCover.toDoubleOrNull() ?: 25.0,
                                viewMode = viewMode,
                                designCode = com.civileg.app.domain.entities.DesignCode.valueOf(designCode),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Invisible capture layer for PDF export
                    item {
                        ComposeDrawingCaptureUtil.DrawingCaptureArea(
                            captureLayer = pdfCaptureLayer,
                            widthPx = screenWidthPx,
                            heightPx = screenHeightPx
                        ) {
                            Box(modifier = Modifier.background(Color(0xFF1A1A2E))) {
                                ProfessionalWaffleSlabDrawing(
                                    lx = lx.toDoubleOrNull() ?: 6000.0,
                                    ly = ly.toDoubleOrNull() ?: 7500.0,
                                    ribSpacing = ribSpacing.toDoubleOrNull() ?: 600.0,
                                    ribWidth = ribWidth.toDoubleOrNull() ?: 150.0,
                                    ribHeight = ribHeight.toDoubleOrNull() ?: 300.0,
                                    toppingThickness = toppingThickness.toDoubleOrNull() ?: 50.0,
                                    solidHeadSize = solidHeadSize.toDoubleOrNull() ?: 1000.0,
                                    columnWidth = columnWidth.toDoubleOrNull() ?: 400.0,
                                    ribBottomDia = res.ribDesign?.flexureReinforcement?.diameter?.toDouble() ?: 16.0,
                                    ribBottomCount = res.ribDesign?.flexureReinforcement?.bars ?: 2,
                                    ribTopDia = 10.0,
                                    ribTopCount = 2,
                                    headBottomDia = res.solidHeadDesign?.flexureReinforcement?.diameter?.toDouble() ?: 16.0,
                                    headBottomCount = res.solidHeadDesign?.flexureReinforcement?.bars ?: 4,
                                    cover = clearCover.toDoubleOrNull() ?: 25.0,
                                    viewMode = viewMode,
                                    designCode = com.civileg.app.domain.entities.DesignCode.valueOf(designCode),
                                    modifier = Modifier.fillMaxSize()
                                )
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
                            Button(
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
                                shape = PremiumDesignSystem.ButtonShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                enabled = !isExporting
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isExporting) "Exporting..." else "Export PDF", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { /* save */ },
                                modifier = Modifier.weight(1f),
                                shape = PremiumDesignSystem.ButtonShape,
                                enabled = false
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
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
                            Text(stringResource(R.string.waffle_slab_calculating), fontSize = 14.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
