package com.civileg.app.ui.compose.screens

import com.civileg.app.ui.responsive.dimens

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.calculations.sbc.SbcElfInput
import com.civileg.app.domain.calculations.sbc.SbcElfResult
import com.civileg.app.domain.calculations.sbc.SbcRiskCategory
import com.civileg.app.domain.calculations.sbc.SbcSeismic301
import com.civileg.app.domain.calculations.sbc.SbcSeismic301Adapter
import com.civileg.app.domain.calculations.sbc.SbcSeismicSystems
import com.civileg.app.domain.calculations.sbc.SbcSiteClass
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.captureToAndroidBitmap
import com.civileg.app.ui.compose.components.charts.SeismicResponseChart
import com.civileg.app.utils.PdfExportHelper
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.viewmodel.SeismicViewModel
import com.civileg.app.ui.compose.components.drawings.ProfessionalSeismicDrawing
import com.civileg.app.ui.compose.components.drawings.FloorForce
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextBlock
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import kotlinx.coroutines.launch
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
    val code: DesignCode,
    val sbcElf: SbcElfResult? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeismicScreen(
    projectViewModel: ProjectViewModel = hiltViewModel(),
    viewModel: SeismicViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val projects by projectViewModel.allProjects.observeAsState(emptyList())
    val uiState by viewModel.uiState.collectAsState()

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

    // ── SBC 301-CR-2018 inputs (Ss, S1, TL come from the code maps and stay blank until entered) ──
    var ssText by remember { mutableStateOf("") }
    var s1Text by remember { mutableStateOf("") }
    var tlText by remember { mutableStateOf("") }
    var sbcSite by remember { mutableStateOf(SbcSiteClass.D) }          // 11.4.2.2: Site Class D where soil data are not known
    var sbcRisk by remember { mutableStateOf(SbcRiskCategory.II) }
    var sbcSystemCode by remember { mutableStateOf("C4") }

    // ── UI state ────────────────────────────────────────────────────────────
    var result by remember { mutableStateOf<SeismicUiResult?>(null) }
    var isCalculating by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    val seismicDefaultName = stringResource(R.string.seismic_default_name)
    var designName by remember { mutableStateOf(seismicDefaultName) }
    var showSuccessMsg by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // PDF drawing capture: the export embeds the view that is on screen right now.
    val pdfCaptureLayer = ComposeDrawingCaptureUtil.rememberDrawingCaptureLayer()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * density.density).toInt()
    val screenHeightPx = (config.screenHeightDp * density.density).toInt()
    // Hoisted out of the drawing item so the invisible capture area (below the list) can
    // render the same tab the user has selected.
    var selectedViewMode by remember { mutableStateOf(0) }

    // ── Helpers ─────────────────────────────────────────────────────────────
    val getDesigner: () -> SeismicDesign = {
        CalculationFactory.getSeismicDesign(selectedCode.designCode)
    }

    val totalHeight = (numFloors.toIntOrNull() ?: 5) * (avgFloorHeight.toDoubleOrNull() ?: 3.0)

    fun calculate() {
        isCalculating = true
        try {
            val designer = getDesigner()
            val nFloors = numFloors.toIntOrNull()?.takeIf { it in 1..50 }
                ?: throw IllegalArgumentException("Number of floors must be a whole number from 1 to 50 / عدد الأدوار عدد صحيح من 1 إلى 50")
            val floorH = avgFloorHeight.toDoubleOrNull()?.takeIf { it in 2.0..6.0 }
                ?: throw IllegalArgumentException("Average floor height must be a number from 2 to 6 m / متوسط ارتفاع الدور من 2 إلى 6 م")
            val w = totalWeight.toDoubleOrNull()?.takeIf { it > 0.0 }
                ?: throw IllegalArgumentException("Total weight must be a positive number (kN) / الوزن الكلي رقم موجب (kN)")

            if (selectedCode == SeismicCodeOption.SBC_301) {
                val ss = ssText.toDoubleOrNull()?.takeIf { it > 0.0 }
                    ?: throw IllegalArgumentException("Enter Ss (g) from SBC 301 Figure 22-1 / أدخل Ss من الشكل 22-1")
                val s1 = s1Text.toDoubleOrNull()?.takeIf { it > 0.0 }
                    ?: throw IllegalArgumentException("Enter S1 (g) from SBC 301 Figure 22-2 / أدخل S1 من الشكل 22-2")
                val tl = tlText.toDoubleOrNull()?.takeIf { it > 0.0 }
                    ?: throw IllegalArgumentException("Enter TL (s) from SBC 301 Figure 22-4 / أدخل TL من الشكل 22-4")
                val sbcInput = SbcElfInput(
                    ss = ss, s1 = s1, tl = tl, siteClass = sbcSite, riskCategory = sbcRisk,
                    system = SbcSeismicSystems.byCode(sbcSystemCode), hn = nFloors * floorH,
                    storyWeights = List(nFloors) { w / nFloors }, storyHeights = List(nFloors) { (it + 1) * floorH }
                )
                val elf = SbcSeismic301.compute(sbcInput)
                result = SeismicUiResult(
                    baseShearResult = SbcSeismic301Adapter.baseShear(sbcInput, elf),
                    spectrumValues = SbcSeismic301Adapter.spectrum(sbcInput, elf),
                    floorForces = SbcSeismic301Adapter.floorForces(sbcInput, elf),
                    fundamentalPeriod = elf.t,
                    spectralAccel = SbcSeismic301Adapter.spectralAccelAtT(sbcInput, elf),
                    code = DesignCode.SBC,
                    sbcElf = elf
                )
                return
            }

            val I = importanceFactor.toDoubleOrNull()?.takeIf { it in 0.5..2.0 }
                ?: throw IllegalArgumentException("Importance factor must be a number from 0.5 to 2.0 / معامل الأهمية من 0.5 إلى 2.0")
            val R = reductionFactor.toDoubleOrNull()?.takeIf { it >= 1.0 }
                ?: throw IllegalArgumentException("Response modification factor must be a number of at least 1 / معامل تعديل الاستجابة 1 أو أكبر")

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

            // Fundamental period (approximate) — guard against NaN/Infinity
            val safeHeight = if (totalHeight > 0) totalHeight else 15.0
            val T = (0.075 * safeHeight.pow(0.75)).coerceAtLeast(0.01)

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
        } catch (e: Exception) {
            android.util.Log.e("SeismicScreen", "Calculate failed", e)
            // Defensive: ensure isCalculating is reset and show error via snackbar
            showSuccessMsg = false
            errorMessage = e.message ?: "Calculation failed"
        } finally {
            isCalculating = false
        }
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
    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar("Error: $msg")
            errorMessage = null
        }
    }

    // ── Scaffold ────────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.screen_seismic_title), fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ═══ Section 1: Code Selector ═════════════════════════════════════
            item { SectionHeader(stringResource(R.string.seismic_section_code), R.drawable.ic_design) }

            // DesignCode FilterChips (ECP / ACI / SBC)
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SeismicCodeOption.entries.forEach { code ->
                        FilterChip(
                            selected = code == selectedCode,
                            onClick = {
                                selectedCode = code
                                viewModel.updateDesignCode(code.designCode.name)
                            },
                            label = { AppText(code.displayName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

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
                        label = { AppText(stringResource(R.string.design_code_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCode) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expandedCode, onDismissRequest = { expandedCode = false }) {
                        SeismicCodeOption.entries.forEach { code ->
                            DropdownMenuItem(
                                text = { AppText(code.displayName) },
                                onClick = {
                                    selectedCode = code
                                    viewModel.updateDesignCode(code.designCode.name)
                                    expandedCode = false
                                }
                            )
                        }
                    }
                }
            }

            // ═══ Section 2: Site Parameters ═══════════════════════════════════
            item { SectionHeader(stringResource(R.string.seismic_site_data), R.drawable.ic_calculator) }

            if (selectedCode != SeismicCodeOption.SBC_301) item {
                ExposedDropdownMenuBox(
                    expanded = expandedZone,
                    onExpandedChange = { expandedZone = !expandedZone },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedZone.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { AppText(stringResource(R.string.seismic_zone)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedZone) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expandedZone, onDismissRequest = { expandedZone = false }) {
                        SeismicZone.entries.forEach { zone ->
                            DropdownMenuItem(
                                text = { AppText(zone.displayName) },
                                onClick = { selectedZone = zone; expandedZone = false }
                            )
                        }
                    }
                }
            }

            if (selectedCode != SeismicCodeOption.SBC_301) item {
                ExposedDropdownMenuBox(
                    expanded = expandedSoil,
                    onExpandedChange = { expandedSoil = !expandedSoil },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = "${selectedSoil.displayName} - ${selectedSoil.description}",
                        onValueChange = {},
                        readOnly = true,
                        label = { AppText(stringResource(R.string.seismic_soil_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSoil) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expandedSoil, onDismissRequest = { expandedSoil = false }) {
                        getSoilTypesForCode(selectedCode).forEach { soil ->
                            DropdownMenuItem(
                                text = { AppText("${soil.displayName} - ${soil.description}") },
                                onClick = { selectedSoil = soil; expandedSoil = false }
                            )
                        }
                    }
                }
            }

            if (selectedCode == SeismicCodeOption.SBC_301) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SeismicInputField(ssText, stringResource(R.string.seismic_sbc_ss), { ssText = it; result = null }, Modifier.weight(1f))
                        SeismicInputField(s1Text, stringResource(R.string.seismic_sbc_s1), { s1Text = it; result = null }, Modifier.weight(1f))
                        SeismicInputField(tlText, stringResource(R.string.seismic_sbc_tl), { tlText = it; result = null }, Modifier.weight(1f))
                    }
                }
                item {
                    SbcSeismicDropdown(
                        stringResource(R.string.seismic_sbc_site_class), "${sbcSite.label}",
                        SbcSiteClass.entries.map { it.label }
                    ) { sbcSite = SbcSiteClass.entries[it]; result = null }
                }
                item {
                    SbcSeismicDropdown(
                        stringResource(R.string.seismic_sbc_risk), "${sbcRisk.label}  (Ie = ${"%.2f".format(sbcRisk.ie)})",
                        SbcRiskCategory.entries.map { "${it.label}  (Ie = ${"%.2f".format(it.ie)})" }
                    ) { sbcRisk = SbcRiskCategory.entries[it]; result = null }
                }
                item {
                    val sys = SbcSeismicSystems.byCode(sbcSystemCode)
                    SbcSeismicDropdown(
                        stringResource(R.string.seismic_sbc_system), "${sys.code}  ${sys.name}  (R = ${sys.r})",
                        SbcSeismicSystems.all.map { "${it.code}  ${it.name}  (R = ${it.r})" }
                    ) { sbcSystemCode = SbcSeismicSystems.all[it].code; result = null }
                }
                item {
                    AppText(
                        stringResource(R.string.seismic_sbc_inputs_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    if (selectedCode != SeismicCodeOption.SBC_301) SeismicInputField(importanceFactor, stringResource(R.string.seismic_importance_factor), { importanceFactor = it }, Modifier.weight(1f))
                }
            }

            if (selectedCode != SeismicCodeOption.SBC_301) item {
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
                        AppText(
                            stringResource(R.string.seismic_est_height, totalHeight, numFloors.toIntOrNull() ?: 0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ═══ Calculate Button ════════════════════════════════════════════
            item {
                AppButton(
                    label = stringResource(R.string.seismic_calc_button),
                    onClick = { calculate() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCalculating,
                    loading = isCalculating,
                    icon = Icons.Default.Calculate
                )
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
                                    AppText(
                                        stringResource(R.string.seismic_base_shear_v),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    AppText(
                                        "${"%.2f".format(res.baseShearResult.baseShear)} kN",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    AppText(
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
                                    AppText(
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
                                    AppText("V/W ratio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    AppText("${"%.3f".format(utilization)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = utilColor)
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
                            val elf = res.sbcElf
                            if (elf == null) {
                                ResultRow(stringResource(R.string.seismic_zone_factor), "${"%.3f".format(res.baseShearResult.zoneFactor)}")
                                ResultRow(stringResource(R.string.seismic_soil_factor), "${"%.2f".format(res.baseShearResult.soilFactor)}")
                            } else {
                                ResultRow("Fa / Fv", "${"%.2f".format(elf.fa)} / ${"%.2f".format(elf.fv)}")
                                ResultRow("SDS / SD1", "${"%.3f".format(elf.sds)} / ${"%.3f".format(elf.sd1)} g")
                                ResultRow("SDC", elf.sdc)
                                ResultRow("Cs", "${"%.4f".format(elf.cs)}  (${elf.csGoverning})")
                                ResultRow("Ta / Cu / T", "${"%.3f".format(elf.ta)} / ${"%.2f".format(elf.cu)} / ${"%.3f".format(elf.t)} s")
                                ResultRow("k", "${"%.3f".format(elf.k)}")
                                AppText(elf.sdcNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AppText(elf.periodNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AppText(elf.systemNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                elf.warnings.forEach { AppText(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                                AppText(
                                    stringResource(R.string.seismic_sbc_not_assessed) + ": " + elf.notAssessed.joinToString("; "),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ResultRow(stringResource(R.string.seismic_importance_factor_val), "${"%.2f".format(res.baseShearResult.importanceFactor)}")
                            ResultRow(stringResource(R.string.seismic_response_mod_val), "${"%.1f".format(res.baseShearResult.responseModification)}")
                            AppText(
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
                                    AppText(stringResource(R.string.seismic_code_warnings), fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                }
                                Spacer(Modifier.height(4.dp))
                                res.baseShearResult.warnings.forEach { w ->
                                    AppTextBlock("• $w", style = MaterialTheme.typography.bodySmall, color = Color(0xFF795548))
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
                        AppText(stringResource(R.string.seismic_force_distribution), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                                AppText(stringResource(R.string.seismic_floor), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                AppText(stringResource(R.string.seismic_force_kn), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                AppText(stringResource(R.string.seismic_shear_kn), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                AppText(stringResource(R.string.seismic_moment_knm), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))

                            // Floor rows (reversed so top floor first)
                            res.floorForces.reversed().forEach { f ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppText(stringResource(R.string.seismic_floor_n, f.floorIndex + 1), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    AppText("${"%.2f".format(f.lateralForce)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary)
                                    AppText("${"%.2f".format(f.storyShear)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.secondary)
                                    AppText("${"%.1f".format(f.overturningMoment)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            }

                            // Totals
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                AppText(stringResource(R.string.seismic_total), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                AppText(
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

                item {
                    SeismicStabilityCard(res.baseShearResult, totalHeight)
                }

                // ── Spectrum Canvas (mini inline) ────────────────────────────
                item {
                    AppText(stringResource(R.string.seismic_response_spectrum), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    SeismicSpectrumCanvas(
                        spectrumValues = res.spectrumValues,
                        designPeriod = res.fundamentalPeriod,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                }

                // ── Professional Seismic Drawing ─────────────────────────────
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                    ) {
                        Column {
                            // View mode selector
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("All", "Elevation", "Spectrum", "Forces").forEachIndexed { idx, label ->
                                    FilterChip(
                                        selected = selectedViewMode == idx,
                                        onClick = { selectedViewMode = idx },
                                        label = { AppText(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            ProfessionalSeismicDrawing(
                                buildingHeight = totalHeight,
                                totalWeight = totalWeight.toDoubleOrNull() ?: 5000.0,
                                baseShear = res.baseShearResult.baseShear,
                                zoneFactor = res.baseShearResult.zoneFactor,
                                soilFactor = res.baseShearResult.soilFactor,
                                importanceFactor = res.baseShearResult.importanceFactor,
                                responseModFactor = res.baseShearResult.responseModification,
                                numFloors = numFloors.toIntOrNull() ?: 5,
                                floorForces = res.floorForces.mapIndexed { i, f ->
                                    FloorForce(i + 1, f.lateralForce, f.floorHeight)
                                },
                                viewMode = selectedViewMode,
                                designCode = selectedCode.designCode,
                                modifier = Modifier.fillMaxWidth().height(400.dp)
                            )
                        }
                    }
                }

                // ── Action Buttons (PDF + Save) ──────────────────────────────
                item {
                    val labelPdfCode = stringResource(R.string.seismic_pdf_code)
                    val labelZone = stringResource(R.string.seismic_zone)
                    val labelSoilType = stringResource(R.string.seismic_soil_type)
                    val labelNumFloors = stringResource(R.string.seismic_num_floors)
                    val labelHeight = stringResource(R.string.seismic_pdf_height)
                    val labelWeight = stringResource(R.string.seismic_pdf_weight)
                    val labelImportance = stringResource(R.string.seismic_importance_factor)
                    val labelReduction = stringResource(R.string.seismic_response_modification)
                    val labelBaseShear = stringResource(R.string.seismic_base_shear_v)
                    val labelPeriod = stringResource(R.string.seismic_fundamental_period)
                    val labelSpectral = stringResource(R.string.seismic_spectral_accel)
                    val labelEquation = stringResource(R.string.seismic_pdf_equation)
                    val labelCodeRef = stringResource(R.string.seismic_pdf_code_ref)
                    val labelTitle = stringResource(R.string.seismic_pdf_title, selectedCode.displayName)
                    // Exporting state managed inline
                    val labelError = stringResource(R.string.beam_pdf_error)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            label = stringResource(R.string.pdf_report),
                            onClick = {
                                if (isExporting) return@AppButton
                                isExporting = true
                                val details = mapOf(
                                    labelPdfCode to selectedCode.displayName,
                                    labelZone to (res.sbcElf?.let { "Ss=$ssText g, S1=$s1Text g, TL=$tlText s" } ?: selectedZone.displayName),
                                    labelSoilType to (res.sbcElf?.let { "${sbcSite.label} / SDC ${it.sdc}" } ?: "${selectedSoil.displayName} - ${selectedSoil.description}"),
                                    labelNumFloors to (numFloors.toIntOrNull() ?: 5).toString(),
                                    labelHeight to "${"%.1f".format(totalHeight)} m",
                                    labelWeight to "${totalWeight} kN",
                                    labelImportance to (res.sbcElf?.let { "%.2f".format(it.ie) } ?: importanceFactor),
                                    labelReduction to (res.sbcElf?.let { "${it.r}" } ?: reductionFactor),
                                    labelBaseShear to "${"%.2f".format(res.baseShearResult.baseShear)} kN",
                                    labelPeriod to "${"%.3f".format(res.fundamentalPeriod)} sec",
                                    labelSpectral to "${"%.4f".format(res.spectralAccel)} g",
                                    labelEquation to res.baseShearResult.calculationFormula,
                                    labelCodeRef to res.baseShearResult.codeReference
                                )
                                scope.launch {
                                    try {
                                        // Capture the drawing exactly as it appears on screen at the
                                        // currently selected view mode, so the PDF cannot diverge from
                                        // what the user is looking at.
                                        val capturedBitmap = try {
                                            pdfCaptureLayer.captureToAndroidBitmap()
                                        } catch (e: Exception) {
                                            android.util.Log.e("SeismicScreen", "Drawing capture failed", e)
                                            null
                                        }
                                        // Fallback (§9.10): generate the elevation + force distribution
                                        // chart + response spectrum from the real result values when no
                                        // capture area has been composed yet.
                                        val drawingBitmap = if (capturedBitmap != null) {
                                            capturedBitmap
                                        } else try {
                                            com.civileg.app.shared.reporting.PdfDrawingGenerator.generateSeismicDrawing(
                                                totalHeight = totalHeight,
                                                numFloors = numFloors.toIntOrNull() ?: 5,
                                                floorForces = res.floorForces,
                                                spectrumValues = res.spectrumValues,
                                                baseShear = res.baseShearResult.baseShear,
                                                fundamentalPeriod = res.fundamentalPeriod,
                                                spectralAccel = res.spectralAccel,
<<<<<<< ours
                                                isSafe = com.civileg.app.viewmodel.SeismicExportStatus.isSafe(res.baseShearResult)
=======
                                                isSafe = res.sbcElf?.heightLimitOk != false
>>>>>>> theirs
                                            )
                                        } catch (e: Exception) {
                                            android.util.Log.e("SeismicScreen", "Drawing gen failed", e)
                                            null
                                        }

                                        val filePath = PdfExportHelper.exportDesignReportAsync(
                                            context = context,
                                            title = labelTitle,
                                            subtitle = "${labelPdfCode}: ${selectedCode.displayName}  •  ${labelZone}: ${res.sbcElf?.let { "Ss=$ssText g" } ?: selectedZone.displayName}",
                                            designType = "Seismic Analysis — ${selectedCode.displayName}",
                                            inputs = details,
                                            results = emptyMap(),
<<<<<<< ours
                                            safetyChecks = com.civileg.app.viewmodel.SeismicExportStatus.checks(res.baseShearResult),
                                            isSafe = com.civileg.app.viewmodel.SeismicExportStatus.isSafe(res.baseShearResult),
=======
                                            safetyChecks = emptyList(),
                                            isSafe = res.sbcElf?.heightLimitOk != false,
>>>>>>> theirs
                                            drawingBitmap = drawingBitmap,
                                            fileName = "Seismic_Report_${System.currentTimeMillis()}",
                                            assessmentPending = com.civileg.app.viewmodel.SeismicExportStatus.assessmentPending(res.baseShearResult)
                                        )
                                        if (filePath == null) {
                                            android.widget.Toast.makeText(context, labelError, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("SeismicScreen", "PDF export failed", e)
                                        android.widget.Toast.makeText(context, "$labelError: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isExporting,
                            loading = isExporting,
                            icon = Icons.Default.PictureAsPdf
                        )

                        AppButton(
                            label = "DXF",
                            onClick = { viewModel.exportToDxf(context) },
                            modifier = Modifier.weight(1f),
                            style = AppButtonStyle.Outline,
                            fontWeight = FontWeight.Bold
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

                // ── Formula Reference ────────────────────────────────────────
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            AppText(stringResource(R.string.seismic_reference_equations), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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

    // Invisible duplicate of the on-screen seismic drawing, composed at the currently selected
    // view mode so the exported PDF embeds exactly what the user sees. It lives outside the
    // LazyColumn because list items are only composed when scrolled into view.
    result?.let { res ->
        ComposeDrawingCaptureUtil.DrawingCaptureArea(
            captureLayer = pdfCaptureLayer,
            widthPx = screenWidthPx,
            heightPx = screenHeightPx
        ) {
            Box(modifier = Modifier.background(Color(0xFF1A1A2E))) {
                ProfessionalSeismicDrawing(
                    buildingHeight = totalHeight,
                    totalWeight = totalWeight.toDoubleOrNull() ?: 5000.0,
                    baseShear = res.baseShearResult.baseShear,
                    zoneFactor = res.baseShearResult.zoneFactor,
                    soilFactor = res.baseShearResult.soilFactor,
                    importanceFactor = res.baseShearResult.importanceFactor,
                    responseModFactor = res.baseShearResult.responseModification,
                    numFloors = numFloors.toIntOrNull() ?: 5,
                    floorForces = res.floorForces.mapIndexed { i, f ->
                        FloorForce(i + 1, f.lateralForce, f.floorHeight)
                    },
                    viewMode = selectedViewMode,
                    designCode = selectedCode.designCode,
                    modifier = Modifier.fillMaxWidth().height(400.dp)
                )
            }
        }
    }

    if (isCalculating) {
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

    // ── Save Dialog ─────────────────────────────────────────────────────────
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { AppText(stringResource(R.string.seismic_save_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = designName,
                        onValueChange = { designName = it },
                        label = { AppText(stringResource(R.string.seismic_design_name_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    AppText(stringResource(R.string.select_project), style = MaterialTheme.typography.labelMedium)
                    if (projects.isEmpty()) {
                        AppText(stringResource(R.string.no_projects_available), color = Color.Gray, fontSize = 12.sp)
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
                        result?.let { res ->
                            val engineResult = CalculatorEngine.SeismicResult(
                                baseShear = res.baseShearResult.baseShear,
                                storyDrift = res.baseShearResult.maxStoryDrift,
                                timePeriod = res.fundamentalPeriod,
                                spectralAcceleration = res.spectralAccel,
<<<<<<< ours
                                isSafe = com.civileg.app.viewmodel.SeismicExportStatus.archivedIsSafe(res.baseShearResult),
=======
                                isSafe = res.sbcElf?.let { it.heightLimitOk != false } ?: res.baseShearResult.driftIsSafe,
>>>>>>> theirs
                                code = when (res.code) {
                                    DesignCode.ECP -> DesignCode.ECP
                                    DesignCode.ACI -> DesignCode.ACI
                                    DesignCode.SBC -> DesignCode.SBC
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
<<<<<<< ours
fun SeismicStabilityCard(res: SeismicBaseShearResult, @Suppress("UNUSED_PARAMETER") totalHeight: Double) {
    // Only the engine's equivalent-SDOF drift estimate is shown. When the engine did not compute one, the card says
    // so. It used to invent a drift and a limit from the building height, which no code supplies.
    val assessed = res.driftAssessed
    val drift = res.maxStoryDrift / 1000.0
    val limit = res.allowableDrift / 1000.0
    val aboveReference = assessed && res.maxStoryDrift > res.allowableDrift
=======
fun SeismicStabilityCard(res: SeismicBaseShearResult, totalHeight: Double) {
    // Drift is shown only when the engine produced one. No heuristic drift or pass verdict is invented:
    // SBC 301 12.8.6 needs the elastic deflections of the structural model.
    val hasEngineDrift = res.maxStoryDrift > 0.0
    if (!hasEngineDrift) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AppText("Story drift and stability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                AppText(
                    "NOT ASSESSED: the story drift (SBC 301 12.8.6, Eq 12-26, Table 12-8) and P-delta (12.8.7) need the elastic deflections from a structural analysis. غير مقيَّم: يحتاج الانحراف الطابقي إلى تحليل إنشائي.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        return
    }
    val drift = res.maxStoryDrift / 1000.0
    val limit = res.allowableDrift / 1000.0
    val isSafe = res.driftIsSafe
>>>>>>> theirs

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText(stringResource(R.string.seismic_stability_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (!assessed) {
                AppText(stringResource(R.string.seismic_drift_not_computed), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AppText(stringResource(R.string.seismic_max_story_drift), style = MaterialTheme.typography.bodySmall)
                    AppText("${String.format(java.util.Locale.US, "%.1f", drift * 1000)} mm", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AppText(stringResource(R.string.seismic_allowable_drift), style = MaterialTheme.typography.bodySmall)
                    AppText("${String.format(java.util.Locale.US, "%.1f", limit * 1000)} mm", color = MaterialTheme.colorScheme.secondary)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ReportProblem,
                        contentDescription = null,
                        tint = Color(0xFFF9A825)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        AppText(
                            stringResource(R.string.seismic_drift_unverified),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (aboveReference) {
                            AppText(stringResource(R.string.seismic_drift_above_reference), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
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

            // CRITICAL FIX (2026-07-27): Use maxOfOrNull instead of maxOf to prevent
            // NoSuchElementException if spectrumValues is empty (defensive against
            // any upstream calculation that might produce an empty list).
            val maxT = spectrumValues.maxOfOrNull { it.period }?.takeIf { it > 0 } ?: 3.0
            val maxSa = spectrumValues.maxOfOrNull { it.spectralAcceleration }?.takeIf { it > 0 } ?: 1.0

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
        AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
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
        label = { AppText(label) },
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
    com.civileg.app.ui.components.ContentDirection(label) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AppText(label, style = MaterialTheme.typography.bodyLarge)
        AppText(
            value,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
}

@Composable
private fun FormulaItem(formula: String) {
    AppText(
        text = formula,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 1.dp),
        color = MaterialTheme.colorScheme.secondary
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SbcSeismicDropdown(label: String, value: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { AppText(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { AppText(o) }, onClick = { onSelect(i); expanded = false })
            }
        }
    }
}
