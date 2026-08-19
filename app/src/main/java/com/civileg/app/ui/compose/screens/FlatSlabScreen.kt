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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.domain.*
import com.civileg.app.ui.compose.components.*
import com.civileg.app.ui.compose.components.drawings.ProfessionalFlatSlabDrawing
import com.civileg.app.viewmodel.FlatSlabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlatSlabScreen(
    viewModel: FlatSlabViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

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
                title = { Text("Flat Slab Design", fontWeight = FontWeight.Bold) },
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
                // ── Panel Type Selector ─────────────────────────────
                item {
                    PremiumSectionHeader(
                        title = "Panel Configuration",
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
                        codes = listOf("ECP" to "ECP 203", "ACI" to "ACI 318"),
                        onCodeSelected = { designCode = it }
                    )
                }

                // ── Geometry ──────────────────────────────────────────
                item { PremiumSectionHeader("Geometry", icon = Icons.Default.SquareFoot) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = lx, onValueChange = { lx = it },
                            label = { Text("Lx (m)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = PremiumDesignSystem.InputShape, singleLine = true
                        )
                        OutlinedTextField(
                            value = ly, onValueChange = { ly = it },
                            label = { Text("Ly (m)", fontSize = 12.sp) },
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
                            slabThickness, "Slab Thickness (mm)", { slabThickness = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            columnWidth, "Column Width (mm)", { columnWidth = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    PremiumInputField(
                        columnDepth, "Column Depth (mm)", { columnDepth = it },
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
                        Text("Drop Panel", fontWeight = FontWeight.Medium, fontSize = 14.sp)
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
                                dropThickness, "Drop Thk (mm)", { dropThickness = it },
                                modifier = Modifier.weight(1f)
                            )
                            PremiumInputField(
                                dropSizeX, "Drop Size X (mm)", { dropSizeX = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        PremiumInputField(
                            dropSizeY, "Drop Size Y (mm)", { dropSizeY = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ── Material Properties ───────────────────────────────
                item { PremiumSectionHeader("Material Properties", icon = Icons.Default.Science) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(fcu, "f'cu (MPa)", { fcu = it }, modifier = Modifier.weight(1f))
                        PremiumInputField(fy, "fy (MPa)", { fy = it }, modifier = Modifier.weight(1f))
                    }
                }

                // ── Loading ───────────────────────────────────────────
                item { PremiumSectionHeader("Loading", icon = Icons.Default.Layers) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(liveLoad, "Live Load (kN/m²)", { liveLoad = it }, modifier = Modifier.weight(1f))
                        PremiumInputField(floorFinish, "Floor Finish (kN/m²)", { floorFinish = it }, modifier = Modifier.weight(1f))
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
                            if (isLoading) "Designing..." else "Design Flat Slab",
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
                    // Utilization Gauge
                    item {
                        SafetyStatusCard(
                            utilizationRatio = res.utilizationRatio, isSafe = res.isSafe,
                            title = "Flat Slab — ${panelType.displayName}"
                        )
                    }

                    // Load Summary
                    item {
                        ResultDataCard(
                            "Load Summary",
                            listOf(
                                "Dead Load" to String.format("%.2f kN/m²", res.totalDeadLoad),
                                "Factored Wu" to String.format("%.2f kN/m²", res.totalFactoredLoad),
                                "MoX" to String.format("%.1f kN.m", res.panelMomentX),
                                "MoY" to String.format("%.1f kN.m", res.panelMomentY)
                            ),
                            icon = Icons.Default.Layers, accentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    // Column Strip
                    item {
                        ResultDataCard(
                            "Column Strip (X)",
                            listOf(
                                "+M" to String.format("%.1f kN.m", res.columnStripMomentPos),
                                "-M" to String.format("%.1f kN.m", res.columnStripMomentNeg),
                                "Top Rebar" to res.columnStripTopRebar.barString,
                                "Top As" to String.format("%.0f/%.0f mm²", res.columnStripTopRebar.requiredArea, res.columnStripTopRebar.providedArea),
                                "Bot Rebar" to res.columnStripBotRebar.barString,
                                "Bot As" to String.format("%.0f/%.0f mm²", res.columnStripBotRebar.requiredArea, res.columnStripBotRebar.providedArea),
                                "Width" to String.format("%.0f mm", res.columnStripWidthX)
                            ),
                            icon = Icons.Default.ViewColumn, accentColor = Color(0xFF1565C0)
                        )
                    }

                    // Middle Strip
                    item {
                        ResultDataCard(
                            "Middle Strip (X)",
                            listOf(
                                "+M" to String.format("%.1f kN.m", res.middleStripMomentPos),
                                "-M" to String.format("%.1f kN.m", res.middleStripMomentNeg),
                                "Top Rebar" to res.middleStripTopRebar.barString,
                                "Bot Rebar" to res.middleStripBotRebar.barString
                            ),
                            icon = Icons.Default.ViewStream, accentColor = Color(0xFF2E7D32)
                        )
                    }

                    // Punching Shear
                    item {
                        ResultDataCard(
                            "Punching Shear",
                            listOf(
                                "Vu" to String.format("%.1f kN", res.punchingShearVu),
                                "Vc" to String.format("%.1f kN", res.punchingShearVc),
                                "Perimeter bo" to String.format("%.0f mm", res.punchingPerimeter),
                                "Status" to if (res.punchingShearOk) "PASS ✓" else "FAIL ✗"
                            ),
                            icon = Icons.Default.Shield,
                            accentColor = if (res.punchingShearOk) Color(0xFF2E7D32) else Color(0xFFE53935)
                        )
                    }

                    // Punching reinforcement
                    res.punchingReinforcement?.let { pr ->
                        item {
                            ResultDataCard(
                                "Shear Reinforcement",
                                listOf(
                                    "Type" to "Shear Studs",
                                    "Diameter" to "φ${pr.diameter} mm",
                                    "Spacing" to "@${pr.spacing} mm",
                                    "Total" to "${pr.bars} studs"
                                ),
                                icon = Icons.Default.Build, accentColor = Color(0xFFF57C00)
                            )
                        }
                    }

                    // Deflection
                    item {
                        ResultDataCard(
                            "Deflection",
                            listOf(
                                "Long-term δ" to String.format("%.2f mm", res.deflection),
                                "Allowable" to String.format("%.2f mm", res.allowableDeflection),
                                "Status" to if (res.deflectionOk) "PASS ✓" else "FAIL ✗"
                            ),
                            icon = Icons.Default.Straighten,
                            accentColor = if (res.deflectionOk) Color(0xFF2E7D32) else Color(0xFFE53935)
                        )
                    }

                    // Drop Panel
                    item {
                        ResultDataCard(
                            "Drop Panel",
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
                            "Quantities",
                            listOf(
                                "Concrete" to String.format("%.3f m³/panel", res.concreteVolumePerPanel),
                                "Steel" to String.format("%.1f kg/panel", res.steelWeightPerPanel)
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
                    item { PremiumSectionHeader("Panel Plan", icon = Icons.Default.Draw) }
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
                            onSave = {}, isExporting = isExporting
                        )
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}