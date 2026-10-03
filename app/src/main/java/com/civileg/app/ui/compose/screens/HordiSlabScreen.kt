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
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.SlabDesignResult
import com.civileg.app.ui.compose.components.*
import com.civileg.app.ui.compose.components.drawings.ProfessionalHordiSlabDrawing
import com.civileg.app.viewmodel.HordiSlabViewModel

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

    // ── Input state ────────────────────────────────────────────────
    var designCode by remember { mutableStateOf("ECP") }
    var fcu by remember { mutableStateOf("30") }
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
                title = { Text("Hordi Slab Design", fontWeight = FontWeight.Bold) },
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
                        title = "Configuration",
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

                // ── Material Properties ───────────────────────────────
                item { PremiumSectionHeader("Material Properties", icon = Icons.Default.Science) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(fcu, "fcu (MPa)", { fcu = it }, modifier = Modifier.weight(1f))
                        PremiumInputField(fy, "fy (MPa)", { fy = it }, modifier = Modifier.weight(1f))
                    }
                }

                // ── Geometry ──────────────────────────────────────────
                item { PremiumSectionHeader("Geometry", icon = Icons.Default.SquareFoot) }
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
                item { PremiumSectionHeader("Design Forces", icon = Icons.Default.MoneyOff) }
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
                            label = { Text("Load Combination", fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = loadCombinationExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = PremiumDesignSystem.InputShape,
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = loadCombinationExpanded,
                            onDismissRequest = { loadCombinationExpanded = false }
                        ) {
                            LoadCombination.entries.forEach { combo ->
                                DropdownMenuItem(
                                    text = { Text(combo.description, fontSize = 13.sp) },
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
                    Button(
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
                            if (isLoading) "Designing..." else "Design Hordi Slab",
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
                            title = "Hordi Slab — $designCode"
                        )
                    }

                    // Reinforcement Result
                    item {
                        ResultDataCard(
                            "Reinforcement",
                            listOf(
                                "Required As" to String.format("%.1f mm²", res.requiredReinforcement),
                                "Provided As" to String.format("%.1f mm²", res.providedReinforcement),
                                "Bar Diameter" to String.format("φ%.0f mm", res.barDiameter),
                                "Bar Spacing" to String.format("@%.0f mm", res.barSpacing),
                                "Bar String" to String.format("φ%.0f @%.0f mm", res.barDiameter, res.barSpacing)
                            ),
                            icon = Icons.Default.Build,
                            accentColor = Color(0xFF1565C0)
                        )
                    }

                    // Shear Check
                    item {
                        ResultDataCard(
                            "Shear Check",
                            listOf(
                                "Shear Capacity" to String.format("%.1f kN", res.shearCapacity),
                                "Min Thickness" to String.format("%.0f mm", res.minThickness),
                                "Utilization" to String.format("%.1f%%", res.utilizationRatio * 100),
                                "Status" to if (res.isSafe) "PASS ✓" else "FAIL ✗"
                            ),
                            icon = Icons.Default.Shield,
                            accentColor = if (res.isSafe) Color(0xFF2E7D32) else Color(0xFFE53935)
                        )
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

                    // ── Drawing Section ────────────────────────────────
                    item { PremiumSectionHeader("Engineering Drawing", icon = Icons.Default.Draw) }

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
                                designCode = com.civileg.app.domain.entities.DesignCode.valueOf(designCode),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Code Notes
                    if (res.codeNotes.isNotEmpty()) {
                        item { FormulaCard(res.codeNotes, title = "Design Calculations") }
                    }

                    // Export & Save
                    item {
                        PremiumActionButtons(
                            onExportPdf = { viewModel.exportToPdf(context) {} },
                            onSave = {},
                            isExporting = isExporting
                        )
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
                            Text("Calculating...", fontSize = 14.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
