package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.ui.compose.components.SafetyCheckRow
import com.civileg.app.utils.SoilBearingCalculator
import com.civileg.app.utils.SoilBearingInput
import com.civileg.app.utils.SoilBearingResult
import com.civileg.app.utils.BearingMethod
import com.civileg.app.utils.QualityControlEngine
import com.civileg.app.utils.SoilType
import com.civileg.app.viewmodel.QualityEngineerViewModel
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityEngineerScreen(
    onNavigateBack: () -> Unit,
    viewModel: QualityEngineerViewModel = hiltViewModel()
) {
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
        stringResource(R.string.quality_tab_reports)
    )

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
            0 -> ConcreteQCTab(viewModel)
            1 -> SoilFoundationQCTab(viewModel)
            2 -> RebarQCTab(viewModel)
            3 -> SteelStructureQCTab(viewModel)
            4 -> RoadsQCTab(viewModel)
            5 -> BridgesQCTab(viewModel)
            6 -> TunnelsQCTab(viewModel)
            7 -> BuildingsQCTab(viewModel)
            8 -> QualityReportsTab(viewModel)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 1: Concrete Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ConcreteQCTab(viewModel: QualityEngineerViewModel) {
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
                    passed = uiState.slump.toDoubleOrNull()?.let { it in 50.0..150.0 } ?: false,
                    detail = "الحد: 50-150 مم"
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

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 2: Soil & Foundation Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun SoilFoundationQCTab(viewModel: QualityEngineerViewModel) {
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
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 3: Reinforcement Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun RebarQCTab(viewModel: QualityEngineerViewModel) {
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
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 4: Steel Structure Quality Control
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteelStructureQCTab(viewModel: QualityEngineerViewModel) {
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
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 5: Roads Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun RoadsQCTab(viewModel: QualityEngineerViewModel) {
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

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 6: Bridges Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun BridgesQCTab(viewModel: QualityEngineerViewModel) {
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

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 7: Tunnels Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun TunnelsQCTab(viewModel: QualityEngineerViewModel) {
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

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 8: Buildings / High-Rise / Hospitals QC
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun BuildingsQCTab(viewModel: QualityEngineerViewModel) {
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

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 9: Quality Reports
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun QualityReportsTab(viewModel: QualityEngineerViewModel) {
    val uiState by viewModel.reportsState.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val context = LocalContext.current

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

        Button(
            onClick = { viewModel.exportQualityReport(context) },
            enabled = !isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isExporting) "جارٍ التصدير..." else "تصدير تقرير الجودة PDF")
        }

        if (uiState.records.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("السجلات المحفوظة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.records.forEach { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
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
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.clearAllRecords() }) {
                        Text("مسح كل السجلات")
                    }
                }
            }
        }
    }
}

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
