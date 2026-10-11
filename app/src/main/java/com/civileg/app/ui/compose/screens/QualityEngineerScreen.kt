package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.db.QualityRecord
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.ui.compose.components.SafetyCheckRow
import com.civileg.app.utils.SoilBearingCalculator
import com.civileg.app.utils.SoilBearingInput
import com.civileg.app.utils.SoilBearingResult
import com.civileg.app.utils.BearingMethod
import com.civileg.app.utils.QualityControlEngine
import com.civileg.app.utils.SoilType
import com.civileg.app.ui.compose.components.charts.DailyTrendChart
import com.civileg.app.viewmodel.QualityEngineerViewModel
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityEngineerScreen(
    onNavigateBack: () -> Unit,
    viewModel: QualityEngineerViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.quality_tab_concrete),
        stringResource(R.string.quality_tab_soil),
        stringResource(R.string.quality_tab_rebar),
        stringResource(R.string.quality_tab_steel),
        stringResource(R.string.quality_tab_roads),
        stringResource(R.string.quality_tab_bridges),
        stringResource(R.string.quality_tab_tunnels),
        stringResource(R.string.quality_tab_buildings),
        stringResource(R.string.quality_tab_reports),
        stringResource(R.string.quality_tab_compliance)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.home_quality_engineer)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 11.sp,
                                maxLines = 1,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> ConcreteQCTab(viewModel, snackbarHostState, scope)
                1 -> SoilFoundationQCTab(viewModel, snackbarHostState, scope)
                2 -> RebarQCTab(viewModel, snackbarHostState, scope)
                3 -> SteelStructureQCTab(viewModel, snackbarHostState, scope)
                4 -> RoadsQCTab(viewModel, snackbarHostState, scope)
                5 -> BridgesQCTab(viewModel, snackbarHostState, scope)
                6 -> TunnelsQCTab(viewModel, snackbarHostState, scope)
                7 -> BuildingsQCTab(viewModel, snackbarHostState, scope)
                8 -> QualityReportsTab(viewModel, snackbarHostState, scope)
                9 -> QualityComplianceScreen()
            }
        }
        SnackbarHost(snackbarHostState)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 1: Concrete Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ConcreteQCTab(
    viewModel: QualityEngineerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val uiState by viewModel.concreteState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DesignCodeSelectorRow(
            selectedCode = uiState.designCode,
            onCodeSelected = { viewModel.updateDesignCode(it) }
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("بيانات الصب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.elementId,
                    onValueChange = { viewModel.updateConcreteField("elementId", it) },
                    label = { Text("العنصر (مثال: B1, C2)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.volume,
                        onValueChange = { viewModel.updateConcreteField("volume", it) },
                        label = { Text("الحجم (م³)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.slump,
                        onValueChange = { viewModel.updateConcreteField("slump", it) },
                        label = { Text("الهبوط (مم)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.fcu,
                        onValueChange = { viewModel.updateConcreteField("fcu", it) },
                        label = { Text("fcu (MPa)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.sampleId,
                        onValueChange = { viewModel.updateConcreteField("sampleId", it) },
                        label = { Text("رقم العينة") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.strength7Days,
                        onValueChange = { viewModel.updateConcreteField("strength7Days", it) },
                        label = { Text("قوة 7 أيام") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.strength28Days,
                        onValueChange = { viewModel.updateConcreteField("strength28Days", it) },
                        label = { Text("قوة 28 يوم") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("فحوصات الجودة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                QualityCheckRow(
                    label = "قوة 28 يوم ≥ fcu",
                    passed = uiState.strength28Days.toDoubleOrNull()?.let {
                        val fcu = uiState.fcu.toDoubleOrNull() ?: 25.0
                        it >= fcu
                    } ?: false,
                    detail = "المطلوب: ${uiState.fcu.ifEmpty { "25" }} MPa"
                )

                QualityCheckRow(
                    label = "الهبوط ضمن الحدود",
                    passed = uiState.slump.toDoubleOrNull()?.let { it in 25.0..100.0 } ?: false,
                    detail = "الحد: 25-100 مم (ACI 26.12.1.3)"
                )

                QualityCheckRow(
                    label = "نسبة قوة 7/28 يوم",
                    passed = uiState.strength7Days.toDoubleOrNull()?.let { s7 ->
                        val s28 = uiState.strength28Days.toDoubleOrNull() ?: 0.0
                        if (s28 > 0) s7 / s28 >= 0.65 else false
                    } ?: false,
                    detail = "المطلوب: ≥ 65%"
                )
            }
        }

        Button(
            onClick = { viewModel.saveConcretePour(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("حفظ وتسجيل")
        }

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = "Concrete Quality Control",
                    subtitle = "Pour QC Report",
                    outcome = uiState.outcome
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تصدير تقرير الخرسانة PDF")
        }

        QcOutcomeCard(uiState.outcome)

        // Drawing preview
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("معاينة الرسم - مقطع عمود/كمرة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                ConcreteDrawingElement(
                    elementId = uiState.elementId,
                    width = uiState.volume.toDoubleOrNull() ?: 1.0,
                    height = 1.0,
                    depth = 1.0
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 2: Soil & Foundation Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun SoilFoundationQCTab(
    viewModel: QualityEngineerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val uiState by viewModel.soilState.collectAsState()
    var selectedMethod by remember { mutableStateOf(BearingMethod.TERZAGHI) }
    var selectedSoilType by remember { mutableStateOf(SoilType.CLAY) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DesignCodeSelectorRow(
            selectedCode = uiState.designCode,
            onCodeSelected = { viewModel.updateDesignCode(it) }
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("معاملات التربة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Text("طريقة الحساب", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BearingMethod.entries.forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = {
                                Text(
                                    when (method) {
                                        BearingMethod.TERZAGHI -> "Terzaghi"
                                        BearingMethod.MEYERHOF -> "Meyerhof"
                                        BearingMethod.HANSEN -> "Hansen"
                                        BearingMethod.VESIC -> "Vesic"
                                    },
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text("نوع التربة", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SoilType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedSoilType == type,
                            onClick = { selectedSoilType = type },
                            label = {
                                Text(
                                    when (type) {
                                        SoilType.CLAY -> "طين"
                                        SoilType.SAND -> "رمل"
                                        SoilType.ROCK -> "صخر"
                                        SoilType.MIXED -> "مختلط"
                                    },
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.cohesion,
                        onValueChange = { viewModel.updateSoilField("cohesion", it) },
                        label = { Text("التماسك c (kPa)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.frictionAngle,
                        onValueChange = { viewModel.updateSoilField("frictionAngle", it) },
                        label = { Text("φ (درجة)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.unitWeight,
                        onValueChange = { viewModel.updateSoilField("unitWeight", it) },
                        label = { Text("γ (kN/m³)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.waterTableDepth,
                        onValueChange = { viewModel.updateSoilField("waterTableDepth", it) },
                        label = { Text("منسوب المياه (م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("أبعاد الأساس", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.foundationWidth,
                        onValueChange = { viewModel.updateSoilField("foundationWidth", it) },
                        label = { Text("B (م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.foundationLength,
                        onValueChange = { viewModel.updateSoilField("foundationLength", it) },
                        label = { Text("L (م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.foundationDepth,
                        onValueChange = { viewModel.updateSoilField("foundationDepth", it) },
                        label = { Text("Df (م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.safetyFactor,
                        onValueChange = { viewModel.updateSoilField("safetyFactor", it) },
                        label = { Text("معامل الأمان") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Button(
            onClick = { viewModel.calculateSoilBearing(selectedMethod, selectedSoilType) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("حساب قدرة التحمل")
        }

        uiState.soilResult?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("النتائج", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    ResultRow("قدرة التحمل الكلية qu", "${String.format("%.1f", result.grossBearingCapacity)} kPa")
                    ResultRow("قدرة التحمل الصافية qnet", "${String.format("%.1f", result.netBearingCapacity)} kPa")
                    ResultRow("قدرة التحمل المسموحة", "${String.format("%.1f", result.allowableBearingCapacity)} kPa")
                    ResultRow("الهبوط المقدر", "${String.format("%.1f", result.settlement)} mm")
                    ResultRow("معامل تصحيح المياه", String.format("%.2f", result.waterTableCorrection))
                    Spacer(modifier = Modifier.height(8.dp))

                    QualityCheckRow(
                        label = "الحالة الآمنة",
                        passed = result.isSafe,
                        detail = if (result.isSafe) "SAFE" else "UNSAFE"
                    )
                }
            }
        }

        QcOutcomeCard(uiState.outcome)

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = "Soil & Foundation Quality Control",
                    subtitle = "Bearing Capacity Report",
                    outcome = uiState.outcome
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تصدير تقرير التربة PDF")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 3: Reinforcement Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun RebarQCTab(
    viewModel: QualityEngineerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val uiState by viewModel.rebarState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DesignCodeSelectorRow(
            selectedCode = uiState.designCode,
            onCodeSelected = { viewModel.updateDesignCode(it) }
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("بيانات التسليح", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.barDiameter,
                        onValueChange = { viewModel.updateRebarField("barDiameter", it) },
                        label = { Text("قطر السيخ (مم)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.numBars,
                        onValueChange = { viewModel.updateRebarField("numBars", it) },
                        label = { Text("عدد الأسياخ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.fy,
                        onValueChange = { viewModel.updateRebarField("fy", it) },
                        label = { Text("fy (MPa)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.cover,
                        onValueChange = { viewModel.updateRebarField("cover", it) },
                        label = { Text("الغطاء (مم)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.beamWidth,
                        onValueChange = { viewModel.updateRebarField("beamWidth", it) },
                        label = { Text("عرض الكمرة (مم)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.effectiveDepth,
                        onValueChange = { viewModel.updateRebarField("effectiveDepth", it) },
                        label = { Text("d (مم)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.fcu,
                    onValueChange = { viewModel.updateRebarField("fcu", it) },
                    label = { Text("fcu (MPa)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = { viewModel.calculateRebarChecks() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("فحص التسليح")
        }

        uiState.rebarChecks?.let { checks ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("نتائج الفحص", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    checks.forEach { check ->
                        SafetyCheckRow(
                            label = check.name,
                            isSafe = check.passed,
                            detail = "${"%.2f".format(check.calculated)} / ${"%.2f".format(check.limit)} ${check.unit}"
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = "Reinforcement Quality Control",
                    subtitle = "Rebar Detail Report",
                    outcome = uiState.outcome
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تصدير تقرير التسليح PDF")
        }

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 4: Steel Structure Quality Control
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteelStructureQCTab(
    viewModel: QualityEngineerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val uiState by viewModel.steelState.collectAsState()
    var selectedSection by remember { mutableStateOf("IPE 200") }
    val sections = listOf("IPE 100", "IPE 120", "IPE 140", "IPE 160", "IPE 180", "IPE 200", "IPE 220", "IPE 240", "IPE 270", "IPE 300")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DesignCodeSelectorRow(
            selectedCode = uiState.designCode,
            onCodeSelected = { viewModel.updateDesignCode(it) }
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("فحص القطاع المعدني", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedSection,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("القطاع") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sections.forEach { section ->
                            DropdownMenuItem(
                                text = { Text(section) },
                                onClick = {
                                    selectedSection = section
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.appliedMoment,
                        onValueChange = { viewModel.updateSteelField("appliedMoment", it) },
                        label = { Text("Mu (kN.m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.appliedShear,
                        onValueChange = { viewModel.updateSteelField("appliedShear", it) },
                        label = { Text("Vu (kN)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.steelFy,
                        onValueChange = { viewModel.updateSteelField("steelFy", it) },
                        label = { Text("Fy (MPa)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.span,
                        onValueChange = { viewModel.updateSteelField("span", it) },
                        label = { Text("البحر (م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Button(
            onClick = { viewModel.calculateSteelChecks(selectedSection) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("فحص المعدن")
        }

        uiState.steelChecks?.let { checks ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("نتائج الفحص", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    checks.forEach { check ->
                        SafetyCheckRow(
                            label = check.name,
                            isSafe = check.passed,
                            detail = "${"%.2f".format(check.calculated)} / ${"%.2f".format(check.limit)} ${check.unit}"
                        )
                    }
                }
            }
        }

        QcOutcomeCard(uiState.outcome)

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = "Steel Structure Quality Control",
                    subtitle = "Steel Member Report",
                    outcome = uiState.outcome
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تصدير تقرير المعدن PDF")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 5: Roads Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun RoadsQCTab(
    viewModel: QualityEngineerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val uiState by viewModel.roadsState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("فحص طبقات الطريق", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.surfaceLayerThickness,
                        onValueChange = { viewModel.updateRoadsField("surfaceLayerThickness", it) },
                        label = { Text("السطحية (مم)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.baseLayerThickness,
                        onValueChange = { viewModel.updateRoadsField("baseLayerThickness", it) },
                        label = { Text("الأساس (مم)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.subbaseLayerThickness,
                    onValueChange = { viewModel.updateRoadsField("subbaseLayerThickness", it) },
                    label = { Text("الأساس المساعد (مم)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                Text("فحوصات الدمك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.compactionPercent,
                    onValueChange = { viewModel.updateRoadsField("compactionPercent", it) },
                    label = { Text("نسبة الدمك (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.asphaltDensity,
                    onValueChange = { viewModel.updateRoadsField("asphaltDensity", it) },
                    label = { Text("كثافة الأسفلت (kN/m³)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.airVoids,
                    onValueChange = { viewModel.updateRoadsField("airVoids", it) },
                    label = { Text("نسبة الفراغات (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = { viewModel.calculateRoadsChecks() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("فحص طبقات الطريق")
        }

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = "Roads Quality Control",
                    subtitle = "Pavement QC Report",
                    outcome = uiState.outcome
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تصدير تقرير الطرق PDF")
        }

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 6: Bridges Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun BridgesQCTab(
    viewModel: QualityEngineerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val uiState by viewModel.bridgesState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("فحص الجسر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.concreteStrength,
                        onValueChange = { viewModel.updateBridgesField("concreteStrength", it) },
                        label = { Text("قوة الخرسانة (MPa)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.span,
                        onValueChange = { viewModel.updateBridgesField("span", it) },
                        label = { Text("البحر (م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.naturalFrequency,
                    onValueChange = { viewModel.updateBridgesField("naturalFrequency", it) },
                    label = { Text("التردد الطبيعي (Hz)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                QualityCheckRow(
                    label = "التردد الطبيعي مقبول",
                    passed = (uiState.naturalFrequency.toDoubleOrNull() ?: 0.0) in 0.5..5.0,
                    detail = "المطلوب: 0.5-5 Hz"
                )

                QualityCheckRow(
                    label = "قوة الخرسانة ≥ المطلوب",
                    passed = (uiState.concreteStrength.toDoubleOrNull() ?: 0.0) >= 25.0,
                    detail = "المطلوب: ≥ 25 MPa"
                )
            }
        }

        Button(
            onClick = { viewModel.calculateBridgeChecks() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("فحص الجسر")
        }

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = "Bridges Quality Control",
                    subtitle = "Bridge QC Report",
                    outcome = uiState.outcome
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تصدير تقرير الجسور PDF")
        }

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 7: Tunnels Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun TunnelsQCTab(
    viewModel: QualityEngineerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val uiState by viewModel.tunnelsState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("فحص النفق", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                val classes = listOf("I", "II", "III", "IV", "V")

                Text("تصنيف الصخر", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    classes.forEach { cls ->
                        FilterChip(
                            selected = uiState.rockClass == cls,
                            onClick = { viewModel.updateTunnelsField("rockClass", cls) },
                            label = { Text(cls) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.tunnelConvergence,
                        onValueChange = { viewModel.updateTunnelsField("tunnelConvergence", it) },
                        label = { Text("التقارب (مم)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.tunnelDiameter,
                        onValueChange = { viewModel.updateTunnelsField("tunnelDiameter", it) },
                        label = { Text("القطر (م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.supportThickness,
                    onValueChange = { viewModel.updateTunnelsField("supportThickness", it) },
                    label = { Text("سمك الدعم (مم)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = { viewModel.calculateTunnelChecks() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("فحص النفق")
        }

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = "Tunnels Quality Control",
                    subtitle = "Tunnel Support Report",
                    outcome = uiState.outcome
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تصدير تقرير الأنفاق PDF")
        }

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 8: Buildings / High-Rise / Hospitals QC
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun BuildingsQCTab(
    viewModel: QualityEngineerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val uiState by viewModel.buildingsState.collectAsState()
    var buildingType by remember { mutableStateOf("مباني سكنية") }
    val types = listOf("مباني سكنية", "عمائر", "مستشفيات", "مباني تجارية")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DesignCodeSelectorRow(
            selectedCode = uiState.designCode,
            onCodeSelected = { viewModel.updateDesignCode(it) }
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("نوع المبنى", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                types.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { buildingType = type }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = buildingType == type,
                            onClick = { buildingType = type }
                        )
                        Text(type, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("معاملات المبنى", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.numFloors,
                        onValueChange = { viewModel.updateBuildingsField("numFloors", it) },
                        label = { Text("عدد الطوابق") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.floorHeight,
                        onValueChange = { viewModel.updateBuildingsField("floorHeight", it) },
                        label = { Text("ارتفاع الطابق (م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.totalWeight,
                        onValueChange = { viewModel.updateBuildingsField("totalWeight", it) },
                        label = { Text("الوزن الكلي (kN)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.importanceFactor,
                        onValueChange = { viewModel.updateBuildingsField("importanceFactor", it) },
                        label = { Text("معامل الأهمية") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.sds,
                        onValueChange = { viewModel.updateBuildingsField("sds", it) },
                        label = { Text("S_DS (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.responseR,
                        onValueChange = { viewModel.updateBuildingsField("responseR", it) },
                        label = { Text("معامل الاستجابة R") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.storyDrift,
                    onValueChange = { viewModel.updateBuildingsField("storyDrift", it) },
                    label = { Text("الانزلاق النسبي للطابق") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = { viewModel.calculateBuildingChecks(buildingType) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("فحص المبنى")
        }

        uiState.buildingChecks?.let { checks ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("نتائج الفحص", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    checks.forEach { check ->
                        SafetyCheckRow(
                            label = check.name,
                            isSafe = check.passed,
                            detail = "${"%.2f".format(check.calculated)} / ${"%.2f".format(check.limit)} ${check.unit}"
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = "Buildings Quality Control",
                    subtitle = "High-Rise / Hospitals QC Report",
                    outcome = uiState.outcome
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تصدير تقرير المباني PDF")
        }

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 9: Quality Reports
// ══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityReportsTab(
    viewModel: QualityEngineerViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val uiState by viewModel.reportsState.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val context = LocalContext.current

    var searchText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedPassFilter by remember { mutableStateOf<Boolean?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Long?>(null) }
    var detailRecordId by remember { mutableStateOf<Long?>(null) }

    val jsonImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importQualityBackupUri(context, uri) { ok ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (ok) "تم استيراد النسخة الاحتياطية بنجاح" else "فشل استيراد النسخة الاحتياطية"
                    )
                }
            }
        }
    }

    val categories = uiState.records.map { it.category }.distinct().sorted()

    val filteredRecords = uiState.records.filter { record ->
        (searchText.isBlank() || record.category.contains(searchText, ignoreCase = true) ||
            record.elementId.contains(searchText, ignoreCase = true) ||
            record.summary.contains(searchText, ignoreCase = true)) &&
        (selectedCategory == null || record.category == selectedCategory) &&
        (selectedPassFilter == null || record.passed == selectedPassFilter)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("ملخص الجودة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                ResultRow("إجمالي الفحوصات", "${uiState.totalInspections}")
                ResultRow("ناجح", "${uiState.passedInspections}")
                ResultRow("فاشل", "${uiState.failedInspections}")
                ResultRow("نسبة النجاح", "${String.format("%.1f", uiState.successRate)}%")
            }
        }

        if (uiState.categorySummary.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("الملخص حسب التخصص", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.categorySummary.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.category, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${row.passedCount}/${row.total} ناجح",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Text(
                                "${if (row.total > 0) row.passedCount * 100 / row.total else 0}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // الاتجاه التاريخي
        DailyTrendChart(trend = uiState.dailyTrend, modifier = Modifier.fillMaxWidth())

        // Search & Filter Bar
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("تصفية السجلات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("بحث (فئة، عنصر، ملخص)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var categoryExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = !categoryExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedCategory?.takeIf { it.isNotBlank() } ?: "كل الفئات",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("الفئة") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("كل الفئات") },
                                onClick = { selectedCategory = null; categoryExpanded = false }
                            )
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = { selectedCategory = cat; categoryExpanded = false }
                                )
                            }
                        }
                    }
                    var statusExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = statusExpanded,
                        onExpandedChange = { statusExpanded = !statusExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = when (selectedPassFilter) {
                                true -> "ناجح فقط"
                                false -> "فاشل فقط"
                                null -> "كل الحالات"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("الحالة") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("كل الحالات") },
                                onClick = { selectedPassFilter = null; statusExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("ناجح فقط") },
                                onClick = { selectedPassFilter = true; statusExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("فاشل فقط") },
                                onClick = { selectedPassFilter = false; statusExpanded = false }
                            )
                        }
}
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.exportQualityReport(context) },
                enabled = !isExporting,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isExporting) "جارٍ التصدير..." else "تصدير PDF")
            }
            Button(
                onClick = { viewModel.exportQualityCsv(context) },
                enabled = !isExporting,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.TableChart, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تصدير CSV")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.exportQualityBackup(context) },
                enabled = !isExporting,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Backup, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("نسخة احتياطية JSON")
            }
            Button(
                onClick = { jsonImportLauncher.launch(arrayOf("application/json", "text/json", "*/*")) },
                enabled = !isExporting,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("استعادة JSON")
            }
        }

        Button(
            onClick = { showSettings = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("إعدادات المشروع")
        }

        if (filteredRecords.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("السجلات المحفوظة (${filteredRecords.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    filteredRecords.forEach { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { detailRecordId = record.id }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (record.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (record.passed) Color(0xFF4CAF50) else Color(0xFFF44336),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${record.category} · ${record.elementId}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${record.designCode} — ${record.summary}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                val photoCount = parsePhotoPaths(record.photoPathsJson).size
                                if (record.inspectorName.isNotBlank() || photoCount > 0) {
                                    Text(
                                        buildString {
                                            if (record.inspectorName.isNotBlank()) append("المفتش: ${record.inspectorName}")
                                            if (photoCount > 0) {
                                                if (isNotEmpty()) append("  ·  ")
                                                append("$photoCount صورة")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "تفاصيل", tint = Color.Gray)
                            IconButton(onClick = { showDeleteConfirm = record.id }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFF44336))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.clearAllRecords() }) {
                        Text("مسح كل السجلات")
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("لا توجد سجلات مطابقة", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
        }

        if (showSettings) {
            ProjectSettingsDialog(onDismiss = { showSettings = false })
        }

        showDeleteConfirm?.let { id ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text("حذف السجل") },
                text = { Text("هل تريد حذف هذا السجل؟ لا يمكن التراجع.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteQualityRecord(id); showDeleteConfirm = null }) {
                        Text("حذف")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) {
                        Text("إلغاء")
                    }
                }
            )
        }

        detailRecordId?.let { id ->
            uiState.records.firstOrNull { it.id == id }?.let { record ->
                InspectionDetailDialog(
                    record = record,
                    context = context,
                    onDismiss = { detailRecordId = null },
                    onSave = { viewModel.updateQualityRecord(it); detailRecordId = null },
                    onAttachPhoto = { rec, uri, cb -> viewModel.attachPhoto(context, rec, uri, cb) },
                    onRemovePhoto = { rec, path, cb -> viewModel.removePhoto(rec, path, cb) }
                )
            }
        }
    }


// ═══════════════════════════════════════════════════════════════════════
// Inspection detail dialog
// ═══════════════════════════════════════════════════════════════════════

private fun parsePhotoPaths(json: String): List<String> =
    runCatching { Gson().fromJson(json, Array<String>::class.java)?.toList() }
        .getOrNull() ?: emptyList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectionDetailDialog(
    record: QualityRecord,
    context: Context,
    onDismiss: () -> Unit,
    onSave: (QualityRecord) -> Unit,
    onAttachPhoto: (QualityRecord, Uri, (String?) -> Unit) -> Unit,
    onRemovePhoto: (QualityRecord, String, () -> Unit) -> Unit
) {
    var inspectorName by remember(record.id) { mutableStateOf(record.inspectorName) }
    var location by remember(record.id) { mutableStateOf(record.location) }
    var weather by remember(record.id) { mutableStateOf(record.weatherConditions) }
    var inspectionType by remember(record.id) { mutableStateOf(record.inspectionType) }
    var complianceStandard by remember(record.id) { mutableStateOf(record.complianceStandard) }
    var notes by remember(record.id) { mutableStateOf(record.notes) }
    var photos by remember(record.id) { mutableStateOf(parsePhotoPaths(record.photoPathsJson)) }

    val currentRecord = record.copy(
        inspectorName = inspectorName,
        location = location,
        weatherConditions = weather,
        inspectionType = inspectionType,
        complianceStandard = complianceStandard,
        notes = notes,
        photoPathsJson = Gson().toJson(photos)
    )

    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) {
            onAttachPhoto(currentRecord, uri) { path ->
                if (path != null) photos = photos + path
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAttachPhoto(currentRecord, uri) { path ->
                if (path != null) photos = photos + path
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تفاصيل الفحص") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${record.category} · ${record.elementId}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${record.designCode} — ${record.summary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = inspectorName,
                    onValueChange = { inspectorName = it },
                    label = { Text("اسم المفتش") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("الموقع / المحور") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weather,
                    onValueChange = { weather = it },
                    label = { Text("حالة الجو") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = inspectionTypeLabel(inspectionType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("نوع الفحص") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        INSPECTION_TYPES.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { inspectionType = key; typeExpanded = false }
                            )
                        }
                    }
                }

                var standardExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = standardExpanded,
                    onExpandedChange = { standardExpanded = !standardExpanded }
                ) {
                    OutlinedTextField(
                        value = complianceStandard.ifBlank { "غير محدد" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("كود الامتثال") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = standardExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = standardExpanded, onDismissRequest = { standardExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("غير محدد") },
                            onClick = { complianceStandard = ""; standardExpanded = false }
                        )
                        COMPLIANCE_CODES.forEach { code ->
                            DropdownMenuItem(
                                text = { Text(code) },
                                onClick = { complianceStandard = code; standardExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()
                Text("الصور (${photos.size})", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val uri = runCatching {
                                val dir = File(context.cacheDir, "qc_photos").apply { mkdirs() }
                                val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            }.getOrNull()
                            if (uri != null) {
                                cameraUri = uri
                                cameraLauncher.launch(uri)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("كاميرا")
                    }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("المعرض")
                    }
                }

                if (photos.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        photos.forEach { path ->
                            Box {
                                val bmp = remember(path) {
                                    runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.Gray)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        onRemovePhoto(currentRecord, path) {
                                            photos = photos.filterNot { it == path }
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.5f),
                                            RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "حذف الصورة",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(currentRecord) }) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

private val INSPECTION_TYPES = listOf(
    "routine" to "اعتيادي",
    "special" to "خاص",
    "final" to "نهائي"
)

private val COMPLIANCE_CODES = listOf("ECP", "ACI", "SBC", "AISC", "AASHTO", "ASCE", "TUNNEL")

private fun inspectionTypeLabel(key: String): String =
    INSPECTION_TYPES.firstOrNull { it.first == key }?.second ?: key


// ═══════════════════════════════════════════════════════════════════════
// Shared Components
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun QualityCheckRow(
    label: String,
    passed: Boolean,
    detail: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (passed) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QcOutcomeCard(outcome: QualityControlEngine.QcOutcome?) {
    outcome ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (outcome.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (outcome.passed) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    outcome.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            outcome.details.forEach { (key, value) ->
                ResultRow(key, value)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(outcome.summary, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun ProjectSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("project_settings", Context.MODE_PRIVATE) }
    var projectName by remember { mutableStateOf(prefs.getString("project_name", "Civil EG Project") ?: "Civil EG Project") }
    var engineerName by remember { mutableStateOf(prefs.getString("engineer_name", "Structural Engineer") ?: "Structural Engineer") }
    var companyName by remember { mutableStateOf(prefs.getString("company_name", "Civil EG") ?: "Civil EG") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات المشروع") },
        text = {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("اسم المشروع") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = engineerName,
                    onValueChange = { engineerName = it },
                    label = { Text("اسم المهندس") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = { Text("اسم الشركة") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.edit()
                    .putString("project_name", projectName)
                    .putString("engineer_name", engineerName)
                    .putString("company_name", companyName)
                    .apply()
                onDismiss()
            }) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════
// Simple Drawing Previews (placeholder for PDF export)
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ConcreteDrawingElement(
    elementId: String,
    width: Double,
    height: Double,
    depth: Double
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color.White)
            .border(1.dp, Color.Gray)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📐 Concrete Element: $elementId", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Width: ${"%.2f".format(width)} m", fontSize = 14.sp, color = Color.Gray)
            Text("Height: ${"%.2f".format(height)} m", fontSize = 14.sp, color = Color.Gray)
            Text("Depth: ${"%.2f".format(depth)} m", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))
            Text("📋 Reinforcement: Main bars Ø16-20, Stirrups Ø8-10", fontSize = 12.sp, color = Color(0xFFB71C1C))
            Text("📋 Cover: 40mm, Clear spacing: max(Ø, 25mm)", fontSize = 12.sp, color = Color(0xFFB71C1C))
        }
    }
}

