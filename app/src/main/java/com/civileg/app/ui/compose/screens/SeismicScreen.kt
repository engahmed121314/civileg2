package com.civileg.app.ui.compose.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import com.civileg.app.db.Project
import com.civileg.app.domain.calculations.aci.ACISeismic
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.calculations.ecp.ECPSeismic
import com.civileg.app.domain.calculations.sbc.SBCSeismic
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.ui.compose.components.charts.SeismicResponseChart
import com.civileg.app.utils.PdfExportHelper
import com.civileg.app.viewmodel.ProjectViewModel
import kotlin.math.pow

// ─── Soil Type Display Names per Code ───────────────────────────────────────
private enum class SeismicCodeOption(val displayName: String, val designCode: DesignCode) {
    ECP_201("ECP 201 - Egyptian Code", DesignCode.ECP),
    ASCE_7("ASCE 7-16 / ACI 318", DesignCode.ACI),
    SBC_301("SBC 301 - Saudi Code", DesignCode.SBC)
}

private fun getSoilTypesForCode(code: SeismicCodeOption): List<SoilType> = SoilType.entries

// ─── Local result holder (bridges domain → UI) ─────────────────────────────
private data class SeismicUiResult(
    val baseShearResult: SeismicBaseShearResult,
    val spectrumValues: List<SpectrumValue>,
    val floorForces: List<SeismicForceDistribution>,
    val fundamentalPeriod: Double,
    val spectralAccel: Double,
    val code: DesignCode
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeismicScreen(
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val projects by projectViewModel.allProjects.observeAsState(emptyList())

    // ── Code / Zone / Soil selectors ────────────────────────────────────────
    var selectedCode by remember { mutableStateOf(SeismicCodeOption.ECP_201) }
    var expandedCode by remember { mutableStateOf(false) }

    var selectedZone by remember { mutableStateOf(SeismicZone.ZONE_3) }
    var expandedZone by remember { mutableStateOf(false) }

    var selectedSoil by remember { mutableStateOf(SoilType.C) }
    var expandedSoil by remember { mutableStateOf(false) }

    // ── Building parameters ─────────────────────────────────────────────────
    var numFloors by remember { mutableStateOf("5") }
    var avgFloorHeight by remember { mutableStateOf("3.0") }
    var totalWeight by remember { mutableStateOf("5000") }
    var importanceFactor by remember { mutableStateOf("1.0") }
    var reductionFactor by remember { mutableStateOf("5.0") }

    // ── UI state ────────────────────────────────────────────────────────────
    var result by remember { mutableStateOf<SeismicUiResult?>(null) }
    var isCalculating by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    val seismicDefaultName = stringResource(R.string.seismic_default_name)
    var designName by remember { mutableStateOf(seismicDefaultName) }
    var showSuccessMsg by remember { mutableStateOf(false) }

    // ── Helpers ─────────────────────────────────────────────────────────────
    val getDesigner: () -> SeismicDesign = {
        when (selectedCode) {
            SeismicCodeOption.ECP_201 -> ECPSeismic()
            SeismicCodeOption.ASCE_7 -> ACISeismic()
            SeismicCodeOption.SBC_301 -> SBCSeismic()
        }
    }

    val totalHeight = (numFloors.toIntOrNull() ?: 5) * (avgFloorHeight.toDoubleOrNull() ?: 3.0)

    fun calculate() {
        isCalculating = true
        val designer = getDesigner()
        val nFloors = numFloors.toIntOrNull()?.coerceIn(1, 50) ?: 5
        val floorH = avgFloorHeight.toDoubleOrNull()?.coerceIn(2.0, 6.0) ?: 3.0
        val w = totalWeight.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 5000.0
        val I = importanceFactor.toDoubleOrNull()?.coerceIn(0.5, 2.0) ?: 1.0
        val R = reductionFactor.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 5.0

        // Floor weights distributed equally
        val floorWeights = List(nFloors) { w / nFloors }
        val floorHeights = List(nFloors) { (it + 1) * floorH }

        val baseShearResult = designer.calculateBaseShear(
            totalWeight = w,
            seismicZone = selectedZone,
            soilType = selectedSoil,
            importanceFactor = I,
            responseModificationFactor = R,
            buildingHeight = totalHeight
        )

        // Fundamental period (approximate)
        val T = 0.075 * totalHeight.pow(0.75)

        // Spectrum values (generate curve)
        val spectrumValues = (1..50).map { i ->
            val period = i * 0.06          // 0.06s → 3.0s
            designer.getResponseSpectrum(
                period = period,
                dampingRatio = 0.05,
                soilType = selectedSoil,
                importanceFactor = I
            )
        }

        val floorForces = designer.distributeSeismicForces(
            baseShear = baseShearResult.baseShear,
            floorWeights = floorWeights,
            floorHeights = floorHeights
        )

        // Sa at T
        val saAtT = designer.getResponseSpectrum(
            period = T, dampingRatio = 0.05,
            soilType = selectedSoil, importanceFactor = I
        )

        result = SeismicUiResult(
            baseShearResult = baseShearResult,
            spectrumValues = spectrumValues,
            floorForces = floorForces,
            fundamentalPeriod = T,
            spectralAccel = saAtT.spectralAcceleration,
            code = selectedCode.designCode
        )
        isCalculating = false
    }

    // ── Snackbar ────────────────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    val savedSuccessMsg = stringResource(R.string.seismic_saved_success)
    LaunchedEffect(showSuccessMsg) {
        if (showSuccessMsg) {
            snackbarHostState.showSnackbar(savedSuccessMsg)
            showSuccessMsg = false
        }
    }

    // ── Scaffold ────────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_seismic_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ═══ Section 1: Code Selector ═════════════════════════════════════
            item { SectionHeader(stringResource(R.string.seismic_section_code), R.drawable.ic_design) }

            item {
                ExposedDropdownMenuBox(
                    expanded = expandedCode,
                    onExpandedChange = { expandedCode = !expandedCode },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCode.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.design_code_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCode) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expandedCode, onDismissRequest = { expandedCode = false }) {
                        SeismicCodeOption.entries.forEach { code ->
                            DropdownMenuItem(
                                text = { Text(code.displayName) },
                                onClick = { selectedCode = code; expandedCode = false }
                            )
                        }
                    }
                }
            }

            // ═══ Section 2: Site Parameters ═══════════════════════════════════
            item { SectionHeader(stringResource(R.string.seismic_site_data), R.drawable.ic_calculator) }

            item {
                ExposedDropdownMenuBox(
                    expanded = expandedZone,
                    onExpandedChange = { expandedZone = !expandedZone },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedZone.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.seismic_zone)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedZone) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expandedZone, onDismissRequest = { expandedZone = false }) {
                        SeismicZone.entries.forEach { zone ->
                            DropdownMenuItem(
                                text = { Text(zone.displayName) },
                                onClick = { selectedZone = zone; expandedZone = false }
                            )
                        }
                    }
                }
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = expandedSoil,
                    onExpandedChange = { expandedSoil = !expandedSoil },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = "${selectedSoil.displayName} - ${selectedSoil.description}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.seismic_soil_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSoil) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expandedSoil, onDismissRequest = { expandedSoil = false }) {
                        getSoilTypesForCode(selectedCode).forEach { soil ->
                            DropdownMenuItem(
                                text = { Text("${soil.displayName} - ${soil.description}") },
                                onClick = { selectedSoil = soil; expandedSoil = false }
                            )
                        }
                    }
                }
            }

            // ═══ Section 3: Building Parameters ══════════════════════════════
            item { SectionHeader(stringResource(R.string.seismic_building_data), R.drawable.ic_beam) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SeismicInputField(numFloors, stringResource(R.string.seismic_num_floors), { numFloors = it }, Modifier.weight(1f), KeyboardType.Number)
                    SeismicInputField(avgFloorHeight, stringResource(R.string.seismic_avg_floor_height), { avgFloorHeight = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SeismicInputField(totalWeight, stringResource(R.string.seismic_total_weight), { totalWeight = it }, Modifier.weight(1f))
                    SeismicInputField(importanceFactor, stringResource(R.string.seismic_importance_factor), { importanceFactor = it }, Modifier.weight(1f))
                }
            }

            item {
                SeismicInputField(reductionFactor, stringResource(R.string.seismic_response_modification), { reductionFactor = it }, Modifier.fillMaxWidth())
            }

            // Total height info chip
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.seismic_est_height, "%.1f".format(totalHeight), numFloors.toIntOrNull() ?: 0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ═══ Calculate Button ════════════════════════════════════════════
            item {
                Button(
                    onClick = { calculate() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(16.dp),
                    enabled = !isCalculating
                ) {
                    if (isCalculating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Calculate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.seismic_calc_button), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // ═══ Results ══════════════════════════════════════════════════════
            result?.let { res ->
                item { SectionHeader(stringResource(R.string.seismic_results), R.drawable.ic_calculator) }

                // ── Base Shear Card with Utilization Bar ──────────────────────
                item {
                    val utilization = (res.baseShearResult.baseShear / (totalWeight.toDoubleOrNull() ?: 5000.0)).coerceIn(0.0, 1.0)
                    val utilColor = when {
                        utilization > 0.3 -> Color(0xFFE53935)
                        utilization > 0.15 -> Color(0xFFFF9800)
                        utilization > 0.05 -> Color(0xFF4CAF50)
                        else -> Color(0xFF2196F3)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Base shear with circular progress
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.seismic_base_shear_v),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${"%.2f".format(res.baseShearResult.baseShear)} kN",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        res.baseShearResult.calculationFormula,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Box(contentAlignment = Alignment.Center) {
                                    val animProgress by animateFloatAsState(
                                        targetValue = utilization.toFloat(),
                                        animationSpec = tween(1000), label = ""
                                    )
                                    CircularProgressIndicator(
                                        progress = { animProgress },
                                        modifier = Modifier.size(56.dp),
                                        strokeWidth = 5.dp,
                                        color = utilColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Text(
                                        "${(utilization * 100).toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Utilization bar
                            Column {
                                LinearProgressIndicator(
                                    progress = { utilization.toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = utilColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("V/W ratio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${"%.3f".format(utilization)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = utilColor)
                                }
                            }
                        }
                    }
                }

                // ── Detailed Results Card ────────────────────────────────────
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ResultRow(stringResource(R.string.seismic_fundamental_period), "${"%.3f".format(res.fundamentalPeriod)} sec")
                            ResultRow(stringResource(R.string.seismic_spectral_accel), "${"%.4f".format(res.spectralAccel)} g")
                            ResultRow(stringResource(R.string.seismic_zone_factor), "${"%.3f".format(res.baseShearResult.zoneFactor)}")
                            ResultRow(stringResource(R.string.seismic_soil_factor), "${"%.2f".format(res.baseShearResult.soilFactor)}")
                            ResultRow(stringResource(R.string.seismic_importance_factor_val), "${"%.2f".format(res.baseShearResult.importanceFactor)}")
                            ResultRow(stringResource(R.string.seismic_response_mod_val), "${"%.1f".format(res.baseShearResult.responseModification)}")
                            Text(
                                stringResource(R.string.seismic_reference, res.baseShearResult.codeReference),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                // ── Warnings ─────────────────────────────────────────────────
                if (res.baseShearResult.warnings.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.seismic_code_warnings), fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                }
                                Spacer(Modifier.height(4.dp))
                                res.baseShearResult.warnings.forEach { w ->
                                    Text("• $w", style = MaterialTheme.typography.bodySmall, color = Color(0xFF795548))
                                }
                            }
                        }
                    }
                }

                // ── Response Spectrum Chart ──────────────────────────────────
                item {
                    SeismicResponseChart(
                        spectrumValues = res.spectrumValues,
                        designPeriod = res.fundamentalPeriod,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Floor Force Distribution ─────────────────────────────────
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(Icons.Default.Business, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.seismic_force_distribution), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Table header
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.seismic_floor), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.seismic_force_kn), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                Text(stringResource(R.string.seismic_shear_kn), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                Text(stringResource(R.string.seismic_moment_knm), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))

                            // Floor rows (reversed so top floor first)
                            res.floorForces.reversed().forEach { f ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.seismic_floor_n, f.floorIndex + 1), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text("${"%.2f".format(f.lateralForce)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary)
                                    Text("${"%.2f".format(f.storyShear)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.secondary)
                                    Text("${"%.1f".format(f.overturningMoment)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            }

                            // Totals
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.seismic_total), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                Text(
                                    "${"%.2f".format(res.floorForces.sumOf { it.lateralForce })}",
                                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.weight(2f))
                            }
                        }
                    }
                }

                // ── Spectrum Canvas (mini inline) ────────────────────────────
                item {
                    Text(stringResource(R.string.seismic_response_spectrum), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    SeismicSpectrumCanvas(
                        spectrumValues = res.spectrumValues,
                        designPeriod = res.fundamentalPeriod,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                }

                // ── Action Buttons (PDF + Save) ──────────────────────────────
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val details = mapOf(
                                    stringResource(R.string.seismic_pdf_code) to selectedCode.displayName,
                                    stringResource(R.string.seismic_zone) to selectedZone.displayName,
                                    stringResource(R.string.seismic_soil_type) to "${selectedSoil.displayName} - ${selectedSoil.description}",
                                    stringResource(R.string.seismic_num_floors) to (numFloors.toIntOrNull() ?: 5).toString(),
                                    stringResource(R.string.seismic_pdf_height) to "${"%.1f".format(totalHeight)} m",
                                    stringResource(R.string.seismic_pdf_weight) to "${totalWeight} kN",
                                    stringResource(R.string.seismic_importance_factor) to importanceFactor,
                                    stringResource(R.string.seismic_response_modification) to reductionFactor,
                                    stringResource(R.string.seismic_base_shear_v) to "${"%.2f".format(res.baseShearResult.baseShear)} kN",
                                    stringResource(R.string.seismic_fundamental_period) to "${"%.3f".format(res.fundamentalPeriod)} sec",
                                    stringResource(R.string.seismic_spectral_accel) to "${"%.4f".format(res.spectralAccel)} g",
                                    stringResource(R.string.seismic_pdf_equation) to res.baseShearResult.calculationFormula,
                                    stringResource(R.string.seismic_pdf_code_ref) to res.baseShearResult.codeReference
                                )
                                val filePath = PdfExportHelper.exportCalculationReport(
                                    context = context,
                                    title = stringResource(R.string.seismic_pdf_title, selectedCode.displayName),
                                    details = details,
                                    fileName = "Seismic_Report_${System.currentTimeMillis()}"
                                )
                                if (filePath != null) {
                                    ExportUtils.openPdf(context, java.io.File(filePath))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pdf_report))
                        }

                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.save_in_project))
                        }
                    }
                }

                // ── Formula Reference ────────────────────────────────────────
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.seismic_reference_equations), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            FormulaItem("V = Sd(T₁) × W")
                            FormulaItem(stringResource(R.string.seismic_formula_period))
                            FormulaItem("Fi = (Wi × hi) / Σ(Wj × hj) × V")
                            FormulaItem(stringResource(R.string.seismic_formula_v))
                            FormulaItem(stringResource(R.string.seismic_formula_mot))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(48.dp)) }
        }
    }

    // ── Save Dialog ─────────────────────────────────────────────────────────
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.seismic_save_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = designName,
                        onValueChange = { designName = it },
                        label = { Text(stringResource(R.string.seismic_design_name_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(stringResource(R.string.select_project), style = MaterialTheme.typography.labelMedium)
                    if (projects.isEmpty()) {
                        Text(stringResource(R.string.no_projects_available), color = Color.Gray, fontSize = 12.sp)
                    } else {
                        projects.forEach { project ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedProjectId == project.id,
                                    onClick = { selectedProjectId = project.id }
                                )
                                Text(project.name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    result?.let { res ->
                        val engineResult = CalculatorEngine.SeismicResult(
                            baseShear = res.baseShearResult.baseShear,
                            storyDrift = 0.0,
                            timePeriod = res.fundamentalPeriod,
                            spectralAcceleration = res.spectralAccel,
                            isSafe = true,
                            code = when (res.code) {
                                DesignCode.ECP -> CalculatorEngine.DesignCode.EGYPTIAN
                                DesignCode.ACI -> CalculatorEngine.DesignCode.ACI
                                DesignCode.SBC -> CalculatorEngine.DesignCode.SAUDI
                            },
                            zone = res.baseShearResult.zoneFactor,
                            importance = res.baseShearResult.importanceFactor,
                            reductionFactor = res.baseShearResult.responseModification,
                            totalWeight = totalWeight.toDoubleOrNull() ?: 5000.0,
                            height = totalHeight
                        )
                        val pId = if (selectedProjectId == -1L) 1L else selectedProjectId
                        projectViewModel.saveSeismic(pId, designName, engineResult)
                        showSuccessMsg = true
                    }
                    showSaveDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ─── Seismic Spectrum Mini Canvas ────────────────────────────────────────────
@Composable
private fun SeismicSpectrumCanvas(
    spectrumValues: List<SpectrumValue>,
    designPeriod: Double,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
            if (spectrumValues.isEmpty()) return@Canvas

            val w = size.width
            val h = size.height
            val pad = 50f

            val maxT = spectrumValues.maxOf { it.period }
            val maxSa = spectrumValues.maxOf { it.spectralAcceleration }

            fun toX(t: Double) = pad + (t / maxT * (w - 2 * pad)).toFloat()
            fun toY(sa: Double) = h - pad - (sa / maxSa * (h - 2 * pad)).toFloat()

            // Grid
            val gridColor = Color.Gray.copy(alpha = 0.15f)
            for (i in 1..4) {
                val x = toX(maxT * i / 4.0)
                drawLine(gridColor, Offset(x, pad), Offset(x, h - pad), 1f)
                val y = toY(maxSa * i / 4.0)
                drawLine(gridColor, Offset(pad, y), Offset(w - pad, y), 1f)
            }

            // Axes
            drawLine(Color.Gray, Offset(pad, h - pad), Offset(w - pad, h - pad), 2f)
            drawLine(Color.Gray, Offset(pad, h - pad), Offset(pad, pad), 2f)

            // Spectrum curve
            val path = Path()
            spectrumValues.forEachIndexed { idx, sv ->
                val x = toX(sv.period)
                val y = toY(sv.spectralAcceleration)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFF1565C0), style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Design period marker
            val designX = toX(designPeriod)
            val nearestSa = spectrumValues.minByOrNull { kotlin.math.abs(it.period - designPeriod) }?.spectralAcceleration ?: 0.0
            val designY = toY(nearestSa)

            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            drawLine(Color.Red, Offset(designX, h - pad), Offset(designX, designY), 2f, pathEffect = dash)
            drawLine(Color.Red, Offset(pad, designY), Offset(designX, designY), 2f, pathEffect = dash)
            drawCircle(Color.Red, 6f, Offset(designX, designY))
            drawCircle(Color.White, 3f, Offset(designX, designY))

            // Labels
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    textSize = 22f
                    color = android.graphics.Color.GRAY
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText("T (sec)", w / 2, h - 5f, paint)
                save()
                rotate(-90f, 12f, h / 2)
                drawText("Sa (g)", 12f, h / 2, paint)
                restore()
            }
        }
    }
}

// ─── Reusable Components ─────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(
            painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SeismicInputField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun FormulaItem(formula: String) {
    Text(
        text = formula,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 1.dp),
        color = MaterialTheme.colorScheme.secondary
    )
}