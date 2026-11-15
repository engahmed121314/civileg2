package com.civileg.app.ui.compose.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.civileg.app.ui.defaultDesignCode
import com.civileg.app.ui.DesignCodeStore
import com.civileg.app.ui.compose.components.*
import com.civileg.app.ui.compose.components.charts.*
import com.civileg.app.ui.compose.components.drawings.*
import com.civileg.app.ui.compose.components.drawings.DisciplineQcGenericDrawing
import com.civileg.app.utils.SoilBearingCalculator
import com.civileg.app.utils.SoilBearingInput
import com.civileg.app.utils.SoilBearingResult
import com.civileg.app.utils.BearingMethod
import com.civileg.app.utils.QualityControlEngine
import com.civileg.app.utils.QualityControlEngineNew
import com.civileg.app.utils.QcLimits
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.SoilType
import com.civileg.app.viewmodel.QualityEngineerViewModel
import com.civileg.app.viewmodel.ReportsQCState
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.*

private fun runQcInputAction(
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    action: () -> Unit
) {
    try {
        action()
    } catch (error: IllegalArgumentException) {
        scope.launch {
            snackbarHostState.showSnackbar(error.message ?: "Please check input values / يرجى مراجعة المدخلات")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityEngineerScreen(
    onNavigateBack: () -> Unit,
    viewModel: QualityEngineerViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedGroup by rememberSaveable { mutableStateOf(QcGroup.CONCRETE) }
    var selectedTab by rememberSaveable { mutableStateOf(QcTab.CONCRETE) }
    val groupTabs = QcTab.entries.filter { it.group == selectedGroup }
    // Derived from the selected module so it stays correct after process restoration.
    val selectedTabIndex = groupTabs.indexOf(selectedTab).coerceAtLeast(0)

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

            // Level 1 — discipline group (5 instead of 25 tabs)
            ScrollableTabRow(
                selectedTabIndex = QcGroup.entries.indexOf(selectedGroup),
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 0.dp
            ) {
                QcGroup.entries.forEach { group ->
                    Tab(
                        selected = selectedGroup == group,
                        onClick = {
                            if (selectedGroup != group) {
                                selectedGroup = group
                                selectedTab = QcTab.entries.first { it.group == group }
                            }
                        },
                        text = {
                            Text(
                                stringResource(group.titleRes),
                                fontSize = 12.sp,
                                maxLines = 1,
                                fontWeight = if (selectedGroup == group) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Level 2 — modules inside the selected group
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex.coerceIn(0, (groupTabs.size - 1).coerceAtLeast(0)),
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                groupTabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                        },
                        text = {
                            Text(
                                stringResource(tab.titleRes),
                                fontSize = 11.sp,
                                maxLines = 1,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                QcTab.CONCRETE -> ConcreteQCTab(viewModel, snackbarHostState, scope)
                QcTab.SOIL -> SoilFoundationQCTab(viewModel, snackbarHostState, scope)
                QcTab.REBAR -> RebarQCTab(viewModel, snackbarHostState, scope)
                QcTab.STEEL -> SteelStructureQCTab(viewModel, snackbarHostState, scope)
                QcTab.PRECAST -> DisciplineQcTab(viewModel, QcDiscipline.PRECAST, snackbarHostState, scope)
                QcTab.POST_TENSION -> DisciplineQcTab(viewModel, QcDiscipline.POST_TENSION, snackbarHostState, scope)
                QcTab.GROUTING -> DisciplineQcTab(viewModel, QcDiscipline.GROUTING, snackbarHostState, scope)
                QcTab.EARTHWORKS -> DisciplineQcTab(viewModel, QcDiscipline.EARTHWORKS, snackbarHostState, scope)
                QcTab.PILING -> DisciplineQcTab(viewModel, QcDiscipline.PILING, snackbarHostState, scope)
                QcTab.RETAINING -> DisciplineQcTab(viewModel, QcDiscipline.RETAINING_WALLS, snackbarHostState, scope)
                QcTab.SOIL_IMPROVEMENT -> DisciplineQcTab(viewModel, QcDiscipline.SOIL_IMPROVEMENT, snackbarHostState, scope)
                QcTab.WATER_TANKS -> DisciplineQcTab(viewModel, QcDiscipline.WATER_TANKS, snackbarHostState, scope)
                QcTab.DRAINAGE -> DisciplineQcTab(viewModel, QcDiscipline.DRAINAGE, snackbarHostState, scope)
                QcTab.ROADS -> RoadsQCTab(viewModel, snackbarHostState, scope)
                QcTab.BRIDGES -> BridgesQCTab(viewModel, snackbarHostState, scope)
                QcTab.TUNNELS -> TunnelsQCTab(viewModel, snackbarHostState, scope)
                QcTab.BUILDINGS -> BuildingsQCTab(viewModel, snackbarHostState, scope)
                QcTab.MASONRY -> DisciplineQcTab(viewModel, QcDiscipline.MASONRY, snackbarHostState, scope)
                QcTab.FLOORING -> DisciplineQcTab(viewModel, QcDiscipline.FLOORING, snackbarHostState, scope)
                QcTab.WATERPROOFING -> DisciplineQcTab(viewModel, QcDiscipline.WATERPROOFING, snackbarHostState, scope)
                QcTab.PAINTING -> DisciplineQcTab(viewModel, QcDiscipline.PAINTING, snackbarHostState, scope)
                QcTab.SITE_WORKS -> DisciplineQcTab(viewModel, QcDiscipline.SITE_WORKS, snackbarHostState, scope)
                QcTab.FIRE_PROTECTION -> DisciplineQcTab(viewModel, QcDiscipline.FIRE_PROTECTION, snackbarHostState, scope)
                QcTab.REPORTS -> QualityReportsTab(viewModel, snackbarHostState, scope)
                QcTab.COMPLIANCE -> QualityComplianceScreen()
            }
        }
        SnackbarHost(snackbarHostState)
    }
}

// ── Quality navigation model ────────────────────────────────────────────
// The screen used to expose 25 flat tabs in a single scroll row, which made the
// discipline you needed hard to find. Groups + rememberSaveable keep the last
// module open across configuration changes and keep the row short.

private enum class QcGroup(val titleRes: Int) {
    CONCRETE(R.string.qc_group_concrete),
    GROUND(R.string.qc_group_ground),
    INFRA(R.string.qc_group_infra),
    BUILDING(R.string.qc_group_building),
    REPORTS(R.string.qc_group_reports)
}

private enum class QcTab(val group: QcGroup, val titleRes: Int) {
    CONCRETE(QcGroup.CONCRETE, R.string.quality_tab_concrete),
    REBAR(QcGroup.CONCRETE, R.string.quality_tab_rebar),
    STEEL(QcGroup.CONCRETE, R.string.quality_tab_steel),
    PRECAST(QcGroup.CONCRETE, R.string.quality_tab_precast),
    POST_TENSION(QcGroup.CONCRETE, R.string.quality_tab_post_tension),
    GROUTING(QcGroup.CONCRETE, R.string.quality_tab_grouting),

    SOIL(QcGroup.GROUND, R.string.quality_tab_soil),
    EARTHWORKS(QcGroup.GROUND, R.string.quality_tab_earthworks),
    PILING(QcGroup.GROUND, R.string.quality_tab_piling),
    RETAINING(QcGroup.GROUND, R.string.quality_tab_retaining),
    SOIL_IMPROVEMENT(QcGroup.GROUND, R.string.quality_tab_soil_improvement),
    WATER_TANKS(QcGroup.GROUND, R.string.quality_tab_water_tanks),
    DRAINAGE(QcGroup.GROUND, R.string.quality_tab_drainage),

    ROADS(QcGroup.INFRA, R.string.quality_tab_roads),
    BRIDGES(QcGroup.INFRA, R.string.quality_tab_bridges),
    TUNNELS(QcGroup.INFRA, R.string.quality_tab_tunnels),

    BUILDINGS(QcGroup.BUILDING, R.string.quality_tab_buildings),
    MASONRY(QcGroup.BUILDING, R.string.quality_tab_masonry),
    FLOORING(QcGroup.BUILDING, R.string.quality_tab_flooring),
    WATERPROOFING(QcGroup.BUILDING, R.string.quality_tab_waterproofing),
    PAINTING(QcGroup.BUILDING, R.string.quality_tab_painting),
    SITE_WORKS(QcGroup.BUILDING, R.string.quality_tab_site_works),
    FIRE_PROTECTION(QcGroup.BUILDING, R.string.quality_tab_fire_protection),

    REPORTS(QcGroup.REPORTS, R.string.quality_tab_reports),
    COMPLIANCE(QcGroup.REPORTS, R.string.quality_tab_compliance)
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
                Text(stringResource(R.string.qc_concrete_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.elementId,
                    onValueChange = { viewModel.updateConcreteField("elementId", it) },
                    label = { Text(stringResource(R.string.qc_concrete_element_id)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.volume,
                        onValueChange = { viewModel.updateConcreteField("volume", it) },
                        label = { Text(stringResource(R.string.qc_concrete_volume)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.slump,
                        onValueChange = { viewModel.updateConcreteField("slump", it) },
                        label = { Text(stringResource(R.string.qc_concrete_slump)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.fcu,
                        onValueChange = { viewModel.updateConcreteField("fcu", it) },
                        label = { Text(stringResource(R.string.qc_concrete_fcu)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.sampleId,
                        onValueChange = { viewModel.updateConcreteField("sampleId", it) },
                        label = { Text(stringResource(R.string.qc_concrete_sample_id)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.strength7Days,
                        onValueChange = { viewModel.updateConcreteField("strength7Days", it) },
                        label = { Text(stringResource(R.string.qc_concrete_strength_7days)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.strength28Days,
                        onValueChange = { viewModel.updateConcreteField("strength28Days", it) },
                        label = { Text(stringResource(R.string.qc_concrete_strength_28days)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.airContent,
                        onValueChange = { viewModel.updateConcreteField("airContent", it) },
                        label = { Text(stringResource(R.string.qc_concrete_air_content)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.concreteTemp,
                        onValueChange = { viewModel.updateConcreteField("concreteTemp", it) },
                        label = { Text(stringResource(R.string.qc_concrete_temp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.qc_concrete_checks_header), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                QualityCheckRow(
                    label = stringResource(R.string.qc_concrete_check_28days),
                    passed = uiState.strength28Days.toDoubleOrNull()?.let {
                        uiState.fcu.toDoubleOrNull()?.let { fcu -> it >= fcu } ?: false
                    } ?: false,
                    detail = stringResource(R.string.qc_concrete_check_28days_detail, uiState.fcu.ifBlank { "—" })
                )

                val slumpRange = QcLimits.slumpRange(uiState.designCode)

                QualityCheckRow(
                    label = stringResource(R.string.qc_concrete_check_slump),
                    passed = uiState.slump.toDoubleOrNull()?.let { it in slumpRange } ?: false,
                    detail = stringResource(
                        R.string.qc_concrete_check_slump_detail,
                        slumpRange.start, slumpRange.endInclusive, uiState.designCode.version
                    )
                )

                QualityCheckRow(
                    label = stringResource(R.string.qc_concrete_check_air),
                    passed = uiState.airContent.toDoubleOrNull()?.let { it in 3.0..8.0 } ?: false,
                    detail = stringResource(R.string.qc_concrete_check_air_detail)
                )

                QualityCheckRow(
                    label = stringResource(R.string.qc_concrete_check_temp),
                    passed = uiState.concreteTemp.toDoubleOrNull()?.let { it in 5.0..40.0 } ?: false,
                    detail = stringResource(R.string.qc_concrete_check_temp_detail)
                )

                QualityCheckRow(
                    label = stringResource(R.string.qc_concrete_check_ratio),
                    passed = uiState.strength7Days.toDoubleOrNull()?.let { s7 ->
                        val s28 = uiState.strength28Days.toDoubleOrNull() ?: 0.0
                        if (s28 > 0) s7 / s28 >= 0.65 else false
                    } ?: false,
                    detail = stringResource(R.string.qc_concrete_check_ratio_detail)
                )
            }
        }

        Button(
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.saveConcretePour(context)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_concrete_save))
        }

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = context.getString(R.string.qc_concrete_report_title),
                    subtitle = context.getString(R.string.qc_concrete_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_concrete_export))
        }

        QcOutcomeCard(uiState.outcome)

        // Drawing preview
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.qc_concrete_drawing_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                ConcreteQcDrawing(
                    elementId = uiState.elementId,
                    volumeM3 = uiState.volume.toDoubleOrNull() ?: 0.0,
                    slumpMm = uiState.slump.toDoubleOrNull() ?: 0.0,
                    fcuDesignMpa = uiState.fcu.toDoubleOrNull() ?: 0.0,
                    strength7DaysMpa = uiState.strength7Days.toDoubleOrNull() ?: 0.0,
                    strength28DaysMpa = uiState.strength28Days.toDoubleOrNull() ?: 0.0,
                    airContent = uiState.airContent.toDoubleOrNull() ?: 0.0,
                    concreteTemp = uiState.concreteTemp.toDoubleOrNull() ?: 0.0,
                    designCode = uiState.designCode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
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
                Text(stringResource(R.string.qc_soil_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Text(stringResource(R.string.qc_soil_calc_method), style = MaterialTheme.typography.labelLarge)
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
                                        BearingMethod.TERZAGHI -> stringResource(R.string.qc_soil_method_terzaghi)
                                        BearingMethod.MEYERHOF -> stringResource(R.string.qc_soil_method_meyerhof)
                                        BearingMethod.HANSEN -> stringResource(R.string.qc_soil_method_hansen)
                                        BearingMethod.VESIC -> stringResource(R.string.qc_soil_method_vesic)
                                    },
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text(stringResource(R.string.qc_soil_soil_type), style = MaterialTheme.typography.labelLarge)
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
                                        SoilType.CLAY -> stringResource(R.string.qc_soil_type_clay)
                                        SoilType.SAND -> stringResource(R.string.qc_soil_type_sand)
                                        SoilType.ROCK -> stringResource(R.string.qc_soil_type_rock)
                                        SoilType.MIXED -> stringResource(R.string.qc_soil_type_mixed)
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
                        label = { Text(stringResource(R.string.qc_soil_cohesion)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.frictionAngle,
                        onValueChange = { viewModel.updateSoilField("frictionAngle", it) },
                        label = { Text(stringResource(R.string.qc_soil_friction_angle)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.unitWeight,
                        onValueChange = { viewModel.updateSoilField("unitWeight", it) },
                        label = { Text(stringResource(R.string.qc_soil_unit_weight)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.waterTableDepth,
                        onValueChange = { viewModel.updateSoilField("waterTableDepth", it) },
                        label = { Text(stringResource(R.string.qc_soil_water_table)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.qc_soil_foundation_dims), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.foundationWidth,
                        onValueChange = { viewModel.updateSoilField("foundationWidth", it) },
                        label = { Text(stringResource(R.string.qc_soil_width_b)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.foundationLength,
                        onValueChange = { viewModel.updateSoilField("foundationLength", it) },
                        label = { Text(stringResource(R.string.qc_soil_length_l)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.foundationDepth,
                        onValueChange = { viewModel.updateSoilField("foundationDepth", it) },
                        label = { Text(stringResource(R.string.qc_soil_depth_df)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.safetyFactor,
                        onValueChange = { viewModel.updateSoilField("safetyFactor", it) },
                        label = { Text(stringResource(R.string.qc_soil_safety_factor)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Button(
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateSoilBearing(selectedMethod, selectedSoilType)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_soil_calculate))
        }

        uiState.soilResult?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.qc_soil_results), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    ResultRow(stringResource(R.string.qc_soil_gross), "${String.format("%.1f", result.grossBearingCapacity)} kPa")
                    ResultRow(stringResource(R.string.qc_soil_net), "${String.format("%.1f", result.netBearingCapacity)} kPa")
                    ResultRow(stringResource(R.string.qc_soil_allowable), "${String.format("%.1f", result.allowableBearingCapacity)} kPa")
                    ResultRow(stringResource(R.string.qc_soil_settlement), "${String.format("%.1f", result.settlement)} mm")
                    ResultRow(stringResource(R.string.qc_soil_water_correction), String.format("%.2f", result.waterTableCorrection))
                    Spacer(modifier = Modifier.height(8.dp))

                    QualityCheckRow(
                        label = stringResource(R.string.qc_soil_safe_case),
                        passed = result.isSafe,
                        detail = if (result.isSafe) stringResource(R.string.design_status_safe) else stringResource(R.string.design_status_unsafe)
                    )
                }
            }
        }

        QcOutcomeCard(uiState.outcome)

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = context.getString(R.string.qc_soil_report_title),
                    subtitle = context.getString(R.string.qc_soil_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_soil_export))
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
                Text(stringResource(R.string.qc_rebar_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.barDiameter,
                        onValueChange = { viewModel.updateRebarField("barDiameter", it) },
                        label = { Text(stringResource(R.string.qc_rebar_bar_diameter)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.numBars,
                        onValueChange = { viewModel.updateRebarField("numBars", it) },
                        label = { Text(stringResource(R.string.qc_rebar_num_bars)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.fy,
                        onValueChange = { viewModel.updateRebarField("fy", it) },
                        label = { Text(stringResource(R.string.qc_rebar_fy)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.cover,
                        onValueChange = { viewModel.updateRebarField("cover", it) },
                        label = { Text(stringResource(R.string.qc_rebar_cover)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.beamWidth,
                        onValueChange = { viewModel.updateRebarField("beamWidth", it) },
                        label = { Text(stringResource(R.string.qc_rebar_beam_width)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.effectiveDepth,
                        onValueChange = { viewModel.updateRebarField("effectiveDepth", it) },
                        label = { Text(stringResource(R.string.qc_rebar_effective_depth)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.fcu,
                    onValueChange = { viewModel.updateRebarField("fcu", it) },
                    label = { Text(stringResource(R.string.qc_rebar_fcu)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateRebarChecks()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_rebar_calculate))
        }

        uiState.rebarChecks?.let { checks ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.qc_check_results), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    title = context.getString(R.string.qc_rebar_report_title),
                    subtitle = context.getString(R.string.qc_rebar_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_rebar_export))
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
    var selectedSection by remember { mutableStateOf(uiState.sectionKey.ifBlank { "IPE 200" }) }
    val memberTypes = listOf(
        "BEAM" to stringResource(R.string.qc_steel_member_beam),
        "COLUMN" to stringResource(R.string.qc_steel_member_column),
        "BEAM_COLUMN" to stringResource(R.string.qc_steel_member_beam_column)
    )
    var selectedMemberType by remember { mutableStateOf(uiState.memberType) }
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
                Text(stringResource(R.string.qc_steel_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                        label = { Text(stringResource(R.string.qc_steel_section)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sections.forEach { section ->
                            DropdownMenuItem(
                                text = { Text(section) },
                                onClick = {
                                    selectedSection = section
                                    viewModel.updateSteelField("sectionKey", section)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Member type
                Text(stringResource(R.string.qc_steel_member_type), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    memberTypes.forEachIndexed { index, (key, label) ->
                        FilterChip(
                            selected = selectedMemberType == key,
                            onClick = {
                                selectedMemberType = key
                                viewModel.updateSteelField("memberType", key)
                            },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.appliedMoment,
                        onValueChange = { viewModel.updateSteelField("appliedMoment", it) },
                        label = { Text(stringResource(R.string.qc_steel_moment)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.appliedShear,
                        onValueChange = { viewModel.updateSteelField("appliedShear", it) },
                        label = { Text(stringResource(R.string.qc_steel_shear)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.steelFy,
                        onValueChange = { viewModel.updateSteelField("steelFy", it) },
                        label = { Text(stringResource(R.string.qc_steel_fy)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.span,
                        onValueChange = { viewModel.updateSteelField("span", it) },
                        label = { Text(stringResource(R.string.qc_steel_span)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.axialLoad,
                        onValueChange = { viewModel.updateSteelField("axialLoad", it) },
                        label = { Text(stringResource(R.string.qc_steel_axial_load)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.unbracedLength,
                        onValueChange = { viewModel.updateSteelField("unbracedLength", it) },
                        label = { Text(stringResource(R.string.qc_steel_unbraced_length)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.allowableDeflection,
                        onValueChange = { viewModel.updateSteelField("allowableDeflection", it) },
                        label = { Text(stringResource(R.string.qc_steel_allowable_deflection)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.qc_steel_lb_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Button(
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateSteelChecks(selectedSection)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_steel_check))
        }

        uiState.steelChecks?.let { checks ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.qc_check_results), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    title = context.getString(R.string.qc_steel_report_title),
                    subtitle = context.getString(R.string.qc_steel_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_steel_export))
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
                Text(stringResource(R.string.qc_roads_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.surfaceLayerThickness,
                        onValueChange = { viewModel.updateRoadsField("surfaceLayerThickness", it) },
                        label = { Text(stringResource(R.string.qc_roads_surface)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.baseLayerThickness,
                        onValueChange = { viewModel.updateRoadsField("baseLayerThickness", it) },
                        label = { Text(stringResource(R.string.qc_roads_base)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.subbaseLayerThickness,
                    onValueChange = { viewModel.updateRoadsField("subbaseLayerThickness", it) },
                    label = { Text(stringResource(R.string.qc_roads_subbase)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(stringResource(R.string.qc_roads_compaction_checks), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.compactionPercent,
                    onValueChange = { viewModel.updateRoadsField("compactionPercent", it) },
                    label = { Text(stringResource(R.string.qc_roads_compaction)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.asphaltDensity,
                    onValueChange = { viewModel.updateRoadsField("asphaltDensity", it) },
                    label = { Text(stringResource(R.string.qc_roads_asphalt_density)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.airVoids,
                    onValueChange = { viewModel.updateRoadsField("airVoids", it) },
                    label = { Text(stringResource(R.string.qc_roads_air_voids)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateRoadsChecks()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_roads_check))
        }

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = context.getString(R.string.qc_roads_report_title),
                    subtitle = context.getString(R.string.qc_roads_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_roads_export))
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
                Text(stringResource(R.string.qc_bridges_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.concreteStrength,
                        onValueChange = { viewModel.updateBridgesField("concreteStrength", it) },
                        label = { Text(stringResource(R.string.qc_bridges_concrete_strength)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.span,
                        onValueChange = { viewModel.updateBridgesField("span", it) },
                        label = { Text(stringResource(R.string.qc_bridges_span)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.requiredConcreteStrength,
                    onValueChange = { viewModel.updateBridgesField("requiredConcreteStrength", it) },
                    label = { Text(stringResource(R.string.qc_bridges_required_strength)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.naturalFrequency,
                    onValueChange = { viewModel.updateBridgesField("naturalFrequency", it) },
                    label = { Text(stringResource(R.string.qc_bridges_natural_frequency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.qc_bridges_freq_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.measuredDeflection,
                        onValueChange = { viewModel.updateBridgesField("measuredDeflection", it) },
                        label = { Text(stringResource(R.string.qc_bridges_measured_deflection)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.allowableDeflection,
                        onValueChange = { viewModel.updateBridgesField("allowableDeflection", it) },
                        label = { Text(stringResource(R.string.qc_bridges_allowable_deflection)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Button(
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateBridgeChecks()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_bridges_check))
        }

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = context.getString(R.string.qc_bridges_report_title),
                    subtitle = context.getString(R.string.qc_bridges_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_bridges_export))
        }

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 7: Tunnels Quality Control
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
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
                Text(stringResource(R.string.qc_tunnels_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                val classes = listOf("I", "II", "III", "IV", "V")

                Text(stringResource(R.string.qc_tunnels_rock_class), style = MaterialTheme.typography.labelLarge)
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
                        label = { Text(stringResource(R.string.qc_tunnels_convergence)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.tunnelDiameter,
                        onValueChange = { viewModel.updateTunnelsField("tunnelDiameter", it) },
                        label = { Text(stringResource(R.string.qc_tunnels_diameter)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.supportThickness,
                    onValueChange = { viewModel.updateTunnelsField("supportThickness", it) },
                    label = { Text(stringResource(R.string.qc_tunnels_support_thickness)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Additional detailed tunnel fields
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var groundwaterExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = groundwaterExpanded,
                        onExpandedChange = { groundwaterExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = if (uiState.groundwaterPresent == "true") stringResource(R.string.qc_tunnels_groundwater_yes) else stringResource(R.string.qc_tunnels_groundwater_no),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.qc_tunnels_groundwater)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groundwaterExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                        )
                        ExposedDropdownMenu(
                            expanded = groundwaterExpanded,
                            onDismissRequest = { groundwaterExpanded = false }
                        ) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.qc_tunnels_groundwater_no)) }, onClick = { viewModel.updateTunnelsField("groundwaterPresent", "false"); groundwaterExpanded = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.qc_tunnels_groundwater_yes)) }, onClick = { viewModel.updateTunnelsField("groundwaterPresent", "true"); groundwaterExpanded = false })
                        }
                    }
                    OutlinedTextField(
                        value = uiState.shotcreteStrength,
                        onValueChange = { viewModel.updateTunnelsField("shotcreteStrength", it) },
                        label = { Text(stringResource(R.string.qc_tunnels_shotcrete_strength)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.rockUcs,
                    onValueChange = { viewModel.updateTunnelsField("rockUcs", it) },
                    label = { Text(stringResource(R.string.qc_tunnels_rock_ucs)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateTunnelChecks()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_tunnels_check))
        }

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = context.getString(R.string.qc_tunnels_report_title),
                    subtitle = context.getString(R.string.qc_tunnels_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_tunnels_export))
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
    val defaultBuildingType = stringResource(R.string.qc_buildings_type_residential)
    var buildingType by remember(defaultBuildingType) { mutableStateOf(defaultBuildingType) }
    val types = listOf(
        stringResource(R.string.qc_buildings_type_residential),
        stringResource(R.string.qc_buildings_type_apartment),
        stringResource(R.string.qc_buildings_type_hospital),
        stringResource(R.string.qc_buildings_type_commercial)
    )

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
                Text(stringResource(R.string.qc_buildings_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                Text(stringResource(R.string.qc_buildings_foundation_params), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.numFloors,
                        onValueChange = { viewModel.updateBuildingsField("numFloors", it) },
                        label = { Text(stringResource(R.string.qc_buildings_num_floors)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.floorHeight,
                        onValueChange = { viewModel.updateBuildingsField("floorHeight", it) },
                        label = { Text(stringResource(R.string.qc_buildings_floor_height)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.totalWeight,
                        onValueChange = { viewModel.updateBuildingsField("totalWeight", it) },
                        label = { Text(stringResource(R.string.qc_buildings_total_weight)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.importanceFactor,
                        onValueChange = { viewModel.updateBuildingsField("importanceFactor", it) },
                        label = { Text(stringResource(R.string.qc_buildings_importance_factor)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.sds,
                        onValueChange = { viewModel.updateBuildingsField("sds", it) },
                        label = { Text(stringResource(R.string.qc_buildings_sds)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.responseR,
                        onValueChange = { viewModel.updateBuildingsField("responseR", it) },
                        label = { Text(stringResource(R.string.qc_buildings_response_r)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.storyDrift,
                    onValueChange = { viewModel.updateBuildingsField("storyDrift", it) },
                    label = { Text(stringResource(R.string.qc_buildings_story_drift)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateBuildingChecks(buildingType)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_buildings_check))
        }

        uiState.buildingChecks?.let { checks ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.qc_check_results), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    title = context.getString(R.string.qc_buildings_report_title),
                    subtitle = context.getString(R.string.qc_buildings_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_buildings_export))
        }

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Discipline QC tabs (Earthworks … Fire Protection)
// ═══════════════════════════════════════════════════════════════════════

private enum class QcFieldType { NUMBER, TEXT, BOOL }

private data class QcField(
    val key: String,
    @StringRes val labelRes: Int,
    val type: QcFieldType,
    val default: String = ""
)

private fun Map<String, String>.num(k: String, def: Double = 0.0) =
    this[k]?.trim()?.toDoubleOrNull() ?: def

private fun Map<String, String>.intg(k: String, def: Int = 0) =
    this[k]?.trim()?.toIntOrNull() ?: def

private fun Map<String, String>.bool(k: String) =
    this[k]?.equals("true", ignoreCase = true) ?: false

private fun Map<String, String>.str(k: String, def: String = "") =
    this[k]?.trim().takeUnless { it.isNullOrBlank() } ?: def

private enum class QcDiscipline(
    val category: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val elementId: String,
    val fields: List<QcField>
) {
    EARTHWORKS(
        QualityControlEngine.Category.EARTHWORKS,
        R.string.qc_d_earthworks_title, R.string.qc_d_earthworks_subtitle, "EARTHWORKS",
        listOf(
            QcField("excavationDepth", R.string.qc_f_excavation_depth, QcFieldType.NUMBER, "3.0"),
            QcField("excavationLength", R.string.qc_f_excavation_length, QcFieldType.NUMBER, "20.0"),
            QcField("excavationWidth", R.string.qc_f_excavation_width, QcFieldType.NUMBER, "10.0"),
            QcField("slopeAngle", R.string.qc_f_slope_angle, QcFieldType.NUMBER, "45"),
            QcField("soilType", R.string.qc_f_soil_type_classified, QcFieldType.TEXT, "SAND"),
            QcField("groundwaterLevel", R.string.qc_f_groundwater_level, QcFieldType.NUMBER, "5.0"),
            QcField("backfillType", R.string.qc_f_backfill_type, QcFieldType.TEXT, "Compacted Sand"),
            QcField("compactionMethod", R.string.qc_f_compaction_method, QcFieldType.TEXT, "Vibratory"),
            QcField("layerThickness", R.string.qc_f_layer_thickness, QcFieldType.NUMBER, "250"),
            QcField("fieldDensity", R.string.qc_f_field_density, QcFieldType.NUMBER, "19.0"),
            QcField("maxDryDensity", R.string.qc_f_max_dry_density, QcFieldType.NUMBER, "20.0"),
            QcField("optimumMoisture", R.string.qc_f_optimum_moisture, QcFieldType.NUMBER, "12.0"),
            QcField("fieldMoisture", R.string.qc_f_field_moisture, QcFieldType.NUMBER, "12.5")
        )
    ),
    PILING(
        QualityControlEngine.Category.PILING,
        R.string.qc_d_piling_title, R.string.qc_d_piling_subtitle, "PILES",
        listOf(
            QcField("pileType", R.string.qc_f_pile_type, QcFieldType.TEXT, "BORED"),
            QcField("pileDiameter", R.string.qc_f_pile_diameter, QcFieldType.NUMBER, "600"),
            QcField("pileLength", R.string.qc_f_pile_length, QcFieldType.NUMBER, "15"),
            QcField("numberOfPiles", R.string.qc_f_number_of_piles, QcFieldType.NUMBER, "9"),
            QcField("pileSpacing", R.string.qc_f_pile_spacing, QcFieldType.NUMBER, "1.8"),
            QcField("axialLoad", R.string.qc_f_axial_load, QcFieldType.NUMBER, "1200"),
            QcField("lateralLoad", R.string.qc_f_lateral_load, QcFieldType.NUMBER, "80"),
            QcField("momentLoad", R.string.qc_f_moment_load, QcFieldType.NUMBER, "150"),
            QcField("cu", R.string.qc_f_cohesion_cu, QcFieldType.NUMBER, "75"),
            QcField("phi", R.string.qc_f_friction_angle, QcFieldType.NUMBER, "30"),
            QcField("gammaSoil", R.string.qc_f_unit_weight, QcFieldType.NUMBER, "18"),
            QcField("waterTableDepth", R.string.qc_f_water_table_depth, QcFieldType.NUMBER, "3"),
            QcField("safetyFactor", R.string.qc_f_safety_factor, QcFieldType.NUMBER, "2.5"),
            QcField("pileGroupPattern", R.string.qc_f_pile_group_pattern, QcFieldType.TEXT, "3x3"),
            QcField("scourDepth", R.string.qc_f_scour_depth, QcFieldType.NUMBER, "1.0"),
            QcField("capConcreteCover", R.string.qc_f_cap_concrete_cover, QcFieldType.NUMBER, "75"),
            QcField("columnWidth", R.string.qc_f_column_width, QcFieldType.NUMBER, "600"),
            QcField("columnLength", R.string.qc_f_column_length, QcFieldType.NUMBER, "600")
        )
    ),
    RETAINING_WALLS(
        QualityControlEngine.Category.RETAINING_WALLS,
        R.string.qc_d_retaining_walls_title, R.string.qc_d_retaining_walls_subtitle, "RETAINING",
        listOf(
            QcField("wallHeight", R.string.qc_f_wall_height, QcFieldType.NUMBER, "5"),
            QcField("wallThickness", R.string.qc_f_wall_thickness, QcFieldType.NUMBER, "300"),
            QcField("soilType", R.string.qc_f_soil_type, QcFieldType.TEXT, "SAND"),
            QcField("backfillUnitWeight", R.string.qc_f_backfill_unit_weight, QcFieldType.NUMBER, "18"),
            QcField("backfillFrictionAngle", R.string.qc_f_backfill_friction_angle, QcFieldType.NUMBER, "30"),
            QcField("backfillCohesion", R.string.qc_f_backfill_cohesion, QcFieldType.NUMBER, "0"),
            QcField("surchargeLoad", R.string.qc_f_surcharge_load, QcFieldType.NUMBER, "10"),
            QcField("groundwaterLevel", R.string.qc_f_groundwater_level, QcFieldType.NUMBER, "3"),
            QcField("foundationDepth", R.string.qc_f_foundation_depth, QcFieldType.NUMBER, "1.5"),
            QcField("foundationWidth", R.string.qc_f_foundation_width, QcFieldType.NUMBER, "4"),
            QcField("foundationSoilBearing", R.string.qc_f_foundation_soil_bearing, QcFieldType.NUMBER, "200"),
            QcField("concreteStrength", R.string.qc_f_concrete_strength, QcFieldType.NUMBER, "30"),
            QcField("steelGrade", R.string.qc_f_steel_grade, QcFieldType.NUMBER, "400"),
            QcField("seismicCoefficient", R.string.qc_f_seismic_coefficient, QcFieldType.NUMBER, "0.1")
        )
    ),
    WATERPROOFING(
        QualityControlEngine.Category.WATERPROOFING,
        R.string.qc_d_waterproofing_title, R.string.qc_d_waterproofing_subtitle, "WATERPROOFING",
        listOf(
            QcField("waterproofingType", R.string.qc_f_waterproofing_type, QcFieldType.TEXT, "Bituminous Membrane"),
            QcField("membraneThickness", R.string.qc_f_membrane_thickness, QcFieldType.NUMBER, "2.0"),
            QcField("coatingThickness", R.string.qc_f_layer_thickness, QcFieldType.NUMBER, "1.5"),
            QcField("adhesionStrength", R.string.qc_f_adhesion_strength, QcFieldType.NUMBER, "0.6"),
            QcField("overlapWidth", R.string.qc_f_overlap_width, QcFieldType.NUMBER, "100"),
            QcField("leakTestResult", R.string.qc_f_leak_test_result, QcFieldType.BOOL, "true"),
            QcField("cornerDetails", R.string.qc_f_corner_details, QcFieldType.BOOL, "true"),
            QcField("drainageLayer", R.string.qc_f_drainage_layer, QcFieldType.BOOL, "true"),
            QcField("protectionLayer", R.string.qc_f_protection_layer, QcFieldType.BOOL, "true"),
            QcField("substrateCondition", R.string.qc_f_substrate_condition, QcFieldType.TEXT, "Dry"),
            QcField("applicationTemperature", R.string.qc_f_application_temperature, QcFieldType.NUMBER, "25"),
            QcField("curingTime", R.string.qc_f_curing_time_hours, QcFieldType.NUMBER, "24")
        )
    ),
    MASONRY(
        QualityControlEngine.Category.MASONRY,
        R.string.qc_d_masonry_title, R.string.qc_d_masonry_subtitle, "MASONRY",
        listOf(
            QcField("masonryType", R.string.qc_f_masonry_type, QcFieldType.TEXT, "Block"),
            QcField("blockDimensions", R.string.qc_f_block_dimensions, QcFieldType.TEXT, "200x200x400"),
            QcField("mortarType", R.string.qc_f_mortar_type, QcFieldType.TEXT, "M10"),
            QcField("mortarStrength", R.string.qc_f_mortar_strength, QcFieldType.NUMBER, "10"),
            QcField("wallThickness", R.string.qc_f_partition_thickness, QcFieldType.NUMBER, "200"),
            QcField("plasterThickness", R.string.qc_f_plaster_thickness, QcFieldType.NUMBER, "15"),
            QcField("plasterType", R.string.qc_f_plaster_type, QcFieldType.TEXT, "Cement"),
            QcField("surfaceFlatness", R.string.qc_f_flatness_per_m, QcFieldType.NUMBER, "2.0"),
            QcField("verticality", R.string.qc_f_verticality, QcFieldType.NUMBER, "3.0"),
            QcField("adhesionStrength", R.string.qc_f_adhesion_strength, QcFieldType.NUMBER, "0.4"),
            QcField("curingMethod", R.string.qc_f_curing_method, QcFieldType.TEXT, "Wet"),
            QcField("jointThickness", R.string.qc_f_joint_thickness, QcFieldType.NUMBER, "10")
        )
    ),
    FLOORING(
        QualityControlEngine.Category.FLOORING,
        R.string.qc_d_flooring_title, R.string.qc_d_flooring_subtitle, "FLOORING",
        listOf(
            QcField("flooringType", R.string.qc_f_flooring_type, QcFieldType.TEXT, "Granite"),
            QcField("slabThickness", R.string.qc_f_slab_thickness, QcFieldType.NUMBER, "150"),
            QcField("surfaceFlatness", R.string.qc_f_flatness_per_2m, QcFieldType.NUMBER, "2.0"),
            QcField("compressiveStrength", R.string.qc_f_compressive_strength, QcFieldType.NUMBER, "30"),
            QcField("tensileStrength", R.string.qc_f_tensile_strength, QcFieldType.NUMBER, "3"),
            QcField("jointSpacing", R.string.qc_f_joint_spacing, QcFieldType.NUMBER, "5"),
            QcField("jointWidth", R.string.qc_f_joint_width, QcFieldType.NUMBER, "6"),
            QcField("finishType", R.string.qc_f_finish_type, QcFieldType.TEXT, "Polished"),
            QcField("curingMethod", R.string.qc_f_curing_method, QcFieldType.TEXT, "Wet"),
            QcField("levelness", R.string.qc_f_levelness, QcFieldType.NUMBER, "5")
        )
    ),
    PRECAST(
        QualityControlEngine.Category.PRECAST,
        R.string.qc_d_precast_title, R.string.qc_d_precast_subtitle, "PRECAST",
        listOf(
            QcField("elementType", R.string.qc_f_element_type, QcFieldType.TEXT, "Beam"),
            QcField("elementDimensions", R.string.qc_f_element_dimensions, QcFieldType.TEXT, "300x600x6000"),
            QcField("concreteStrength", R.string.qc_f_concrete_strength, QcFieldType.NUMBER, "40"),
            QcField("steelGrade", R.string.qc_f_steel_grade, QcFieldType.NUMBER, "400"),
            QcField("coverThickness", R.string.qc_f_cover_thickness, QcFieldType.NUMBER, "40"),
            QcField("surfaceFinish", R.string.qc_f_surface_finish, QcFieldType.TEXT, "Fair"),
            QcField("liftingAnchors", R.string.qc_f_lifting_anchors, QcFieldType.BOOL, "true"),
            QcField("connectionDetails", R.string.qc_f_connection_details, QcFieldType.TEXT, "Bolted"),
            QcField("transportDamage", R.string.qc_f_transport_damage, QcFieldType.BOOL, "false"),
            QcField("erectionTolerance", R.string.qc_f_erection_tolerance, QcFieldType.NUMBER, "5"),
            QcField("groutStrength", R.string.qc_f_grout_strength, QcFieldType.NUMBER, "50")
        )
    ),
    POST_TENSION(
        QualityControlEngine.Category.POST_TENSION,
        R.string.qc_d_post_tension_title, R.string.qc_d_post_tension_subtitle, "POST_TENSION",
        listOf(
            QcField("tendonType", R.string.qc_f_tendon_type, QcFieldType.TEXT, "7-wire strand"),
            QcField("tendonDiameter", R.string.qc_f_tendon_diameter, QcFieldType.NUMBER, "15.2"),
            QcField("jackingForce", R.string.qc_f_jacking_force, QcFieldType.NUMBER, "200"),
            QcField("elongation", R.string.qc_f_elongation, QcFieldType.NUMBER, "180"),
            QcField("expectedElongation", R.string.qc_f_expected_elongation, QcFieldType.NUMBER, "180"),
            QcField("groutStrength", R.string.qc_f_grout_strength, QcFieldType.NUMBER, "35"),
            QcField("groutFlow", R.string.qc_f_grout_flow, QcFieldType.NUMBER, "20"),
            QcField("anchorageType", R.string.qc_f_anchorage_type, QcFieldType.TEXT, "Wedge"),
            QcField("ductMaterial", R.string.qc_f_duct_material, QcFieldType.TEXT, "HDPE"),
            QcField("corrosionProtection", R.string.qc_f_corrosion_protection, QcFieldType.TEXT, "Galvanized"),
            QcField("stressLoss", R.string.qc_f_stress_loss, QcFieldType.NUMBER, "15"),
            QcField("frictionCoefficient", R.string.qc_f_friction_coefficient, QcFieldType.NUMBER, "0.20"),
            QcField("wobbleCoefficient", R.string.qc_f_wobble_coefficient, QcFieldType.NUMBER, "0.002")
        )
    ),
    GROUTING(
        QualityControlEngine.Category.GROUTING,
        R.string.qc_d_grouting_title, R.string.qc_d_grouting_subtitle, "GROUTING",
        listOf(
            QcField("groutType", R.string.qc_f_grout_type, QcFieldType.TEXT, "Cementitious"),
            QcField("flowTime", R.string.qc_f_flow_time, QcFieldType.NUMBER, "20"),
            QcField("shrinkage", R.string.qc_f_shrinkage, QcFieldType.NUMBER, "0.05"),
            QcField("compressiveStrength", R.string.qc_f_compressive_strength, QcFieldType.NUMBER, "40"),
            QcField("viscosity", R.string.qc_f_viscosity, QcFieldType.NUMBER, "500"),
            QcField("gelTime", R.string.qc_f_gel_time, QcFieldType.NUMBER, "10"),
            QcField("bondStrength", R.string.qc_f_bond_strength, QcFieldType.NUMBER, "2.0"),
            QcField("injectionPressure", R.string.qc_f_injection_pressure, QcFieldType.NUMBER, "0.35"),
            QcField("injectionVolume", R.string.qc_f_injection_volume, QcFieldType.NUMBER, "100"),
            QcField("curingTime", R.string.qc_f_curing_time_days, QcFieldType.NUMBER, "7"),
            QcField("substrateCondition", R.string.qc_f_substrate_condition, QcFieldType.TEXT, "Clean")
        )
    ),
    SOIL_IMPROVEMENT(
        QualityControlEngine.Category.SOIL_IMPROVEMENT,
        R.string.qc_d_soil_improvement_title, R.string.qc_d_soil_improvement_subtitle, "SOIL_IMPROVEMENT",
        listOf(
            QcField("improvementType", R.string.qc_f_improvement_type, QcFieldType.TEXT, "Stone Columns"),
            QcField("improvementDepth", R.string.qc_f_improvement_depth, QcFieldType.NUMBER, "8"),
            QcField("improvementDiameter", R.string.qc_f_improvement_diameter, QcFieldType.NUMBER, "0.8"),
            QcField("spacing", R.string.qc_f_pile_spacing, QcFieldType.NUMBER, "2.0"),
            QcField("preImprovementBearing", R.string.qc_f_pre_improvement_bearing, QcFieldType.NUMBER, "80"),
            QcField("postImprovementBearing", R.string.qc_f_post_improvement_bearing, QcFieldType.NUMBER, "160"),
            QcField("preImprovementSPT", R.string.qc_f_pre_improvement_spt, QcFieldType.NUMBER, "8"),
            QcField("postImprovementSPT", R.string.qc_f_post_improvement_spt, QcFieldType.NUMBER, "18"),
            QcField("settlementBefore", R.string.qc_f_settlement_before, QcFieldType.NUMBER, "50"),
            QcField("settlementAfter", R.string.qc_f_settlement_after, QcFieldType.NUMBER, "20"),
            QcField("improvementMaterial", R.string.qc_f_improvement_material, QcFieldType.TEXT, "Crushed Stone"),
            QcField("qualityControlTest", R.string.qc_f_quality_control_test, QcFieldType.TEXT, "Plate Load")
        )
    ),
    WATER_TANKS(
        QualityControlEngine.Category.WATER_TANKS,
        R.string.qc_d_water_tanks_title, R.string.qc_d_water_tanks_subtitle, "WATER_TANKS",
        listOf(
            QcField("tankType", R.string.qc_f_tank_type, QcFieldType.TEXT, "Rectangular"),
            QcField("tankCapacity", R.string.qc_f_tank_capacity, QcFieldType.NUMBER, "500"),
            QcField("tankDimensions", R.string.qc_f_element_dimensions, QcFieldType.TEXT, "20x10x3"),
            QcField("concreteStrength", R.string.qc_f_concrete_strength, QcFieldType.NUMBER, "30"),
            QcField("steelGrade", R.string.qc_f_steel_grade, QcFieldType.NUMBER, "400"),
            QcField("wallThickness", R.string.qc_f_wall_thickness, QcFieldType.NUMBER, "250"),
            QcField("floorThickness", R.string.qc_f_floor_thickness, QcFieldType.NUMBER, "200"),
            QcField("roofThickness", R.string.qc_f_roof_thickness, QcFieldType.NUMBER, "150"),
            QcField("waterproofingType", R.string.qc_f_waterproofing_type, QcFieldType.TEXT, "Crystalline"),
            QcField("leakTestResult", R.string.qc_f_leak_test_result, QcFieldType.BOOL, "true"),
            QcField("hydrostaticTest", R.string.qc_f_hydrostatic_test, QcFieldType.BOOL, "true"),
            QcField("valveType", R.string.qc_f_valve_type, QcFieldType.TEXT, "Gate"),
            QcField("drainageSystem", R.string.qc_f_drainage_system, QcFieldType.BOOL, "true")
        )
    ),
    DRAINAGE(
        QualityControlEngine.Category.DRAINAGE,
        R.string.qc_d_drainage_title, R.string.qc_d_drainage_subtitle, "DRAINAGE",
        listOf(
            QcField("pipeMaterial", R.string.qc_f_pipe_material, QcFieldType.TEXT, "RCP"),
            QcField("pipeDiameter", R.string.qc_f_pipe_diameter, QcFieldType.NUMBER, "600"),
            QcField("pipeLength", R.string.qc_f_pipe_length, QcFieldType.NUMBER, "100"),
            QcField("slope", R.string.qc_f_slope_percent, QcFieldType.NUMBER, "1.5"),
            QcField("jointType", R.string.qc_f_joint_type, QcFieldType.TEXT, "Rubber Gasket"),
            QcField("leakTestResult", R.string.qc_f_leak_test_result, QcFieldType.BOOL, "true"),
            QcField("manholeSpacing", R.string.qc_f_manhole_spacing, QcFieldType.NUMBER, "40"),
            QcField("manholeDepth", R.string.qc_f_manhole_depth, QcFieldType.NUMBER, "3"),
            QcField("flowRate", R.string.qc_f_flow_rate, QcFieldType.NUMBER, "150"),
            QcField("invertLevel", R.string.qc_f_invert_level, QcFieldType.NUMBER, "0"),
            QcField("backfillCompaction", R.string.qc_f_backfill_compaction, QcFieldType.NUMBER, "96")
        )
    ),
    PAINTING(
        QualityControlEngine.Category.PAINTING,
        R.string.qc_d_painting_title, R.string.qc_d_painting_subtitle, "PAINTING",
        listOf(
            QcField("paintType", R.string.qc_f_paint_type, QcFieldType.TEXT, "Epoxy"),
            QcField("dft", R.string.qc_f_dry_film_thickness, QcFieldType.NUMBER, "150"),
            QcField("adhesionStrength", R.string.qc_f_adhesion_strength, QcFieldType.NUMBER, "2.0"),
            QcField("colorUniformity", R.string.qc_f_color_uniformity, QcFieldType.BOOL, "true"),
            QcField("surfacePreparation", R.string.qc_f_surface_preparation, QcFieldType.TEXT, "SSPC-SP6"),
            QcField("numberOfCoats", R.string.qc_f_number_of_coats, QcFieldType.NUMBER, "2"),
            QcField("coatingThickness", R.string.qc_f_coating_thickness, QcFieldType.NUMBER, "0.15"),
            QcField("fireRating", R.string.qc_f_fire_rating, QcFieldType.TEXT, "None"),
            QcField("curingTime", R.string.qc_f_curing_time_hours, QcFieldType.NUMBER, "24"),
            QcField("ambientTemperature", R.string.qc_f_application_temperature, QcFieldType.NUMBER, "25"),
            QcField("humidity", R.string.qc_f_humidity, QcFieldType.NUMBER, "60")
        )
    ),
    SITE_WORKS(
        QualityControlEngine.Category.SITE_WORKS,
        R.string.qc_d_site_works_title, R.string.qc_d_site_works_subtitle, "SITE_WORKS",
        listOf(
            QcField("gradingLevel", R.string.qc_f_grading_level, QcFieldType.NUMBER, "100"),
            QcField("slope", R.string.qc_f_slope_percent, QcFieldType.NUMBER, "0.5"),
            QcField("fenceType", R.string.qc_f_fence_type, QcFieldType.TEXT, "Chain Link"),
            QcField("gateType", R.string.qc_f_gate_type, QcFieldType.TEXT, "Swing"),
            QcField("lightingType", R.string.qc_f_lighting_type, QcFieldType.TEXT, "LED"),
            QcField("landscapingType", R.string.qc_f_landscaping_type, QcFieldType.TEXT, "Grass"),
            QcField("pavementType", R.string.qc_f_pavement_type, QcFieldType.TEXT, "Interlock"),
            QcField("drainageSystem", R.string.qc_f_drainage_system, QcFieldType.BOOL, "true"),
            QcField("safetySigns", R.string.qc_f_safety_signs, QcFieldType.BOOL, "true"),
            QcField("fireExtinguishers", R.string.qc_f_fire_extinguishers, QcFieldType.BOOL, "true"),
            QcField("firstAidKit", R.string.qc_f_first_aid_kit, QcFieldType.BOOL, "true")
        )
    ),
    FIRE_PROTECTION(
        QualityControlEngine.Category.FIRE_PROTECTION,
        R.string.qc_d_fire_protection_title, R.string.qc_d_fire_protection_subtitle, "FIRE_PROTECTION",
        listOf(
            QcField("protectionType", R.string.qc_f_protection_type, QcFieldType.TEXT, "Intumescent"),
            QcField("coatingThickness", R.string.qc_f_coating_thickness, QcFieldType.NUMBER, "0.8"),
            QcField("adhesionStrength", R.string.qc_f_adhesion_strength, QcFieldType.NUMBER, "1.2"),
            QcField("fireRating", R.string.qc_f_fire_rating, QcFieldType.TEXT, "2 hr"),
            QcField("coverageArea", R.string.qc_f_coverage_area, QcFieldType.NUMBER, "500"),
            QcField("applicationMethod", R.string.qc_f_application_method, QcFieldType.TEXT, "Spray"),
            QcField("curingTime", R.string.qc_f_curing_time_hours, QcFieldType.NUMBER, "24"),
            QcField("ambientTemperature", R.string.qc_f_application_temperature, QcFieldType.NUMBER, "25"),
            QcField("humidity", R.string.qc_f_humidity, QcFieldType.NUMBER, "60"),
            QcField("surfacePreparation", R.string.qc_f_surface_preparation, QcFieldType.TEXT, "SSPC-SP6")
        )
    )
}

private fun computeDisciplineOutcome(
    d: QcDiscipline,
    m: Map<String, String>,
    designCode: DesignCode = DesignCode.ECP
): QualityControlEngine.QcOutcome = when (d) {
    QcDiscipline.EARTHWORKS -> QualityControlEngineNew.earthworksQc(
        excavationDepth = m.num("excavationDepth"),
        excavationLength = m.num("excavationLength"),
        excavationWidth = m.num("excavationWidth"),
        slopeAngle = m.num("slopeAngle"),
        soilType = m.str("soilType", "SAND"),
        groundwaterLevel = m.num("groundwaterLevel"),
        backfillType = m.str("backfillType"),
        compactionMethod = m.str("compactionMethod"),
        layerThickness = m.num("layerThickness"),
        fieldDensity = m.num("fieldDensity"),
        maxDryDensity = m.num("maxDryDensity"),
        optimumMoisture = m.num("optimumMoisture"),
        fieldMoisture = m.num("fieldMoisture")
    )
    QcDiscipline.PILING -> QualityControlEngineNew.pilingQc(
        pileType = m.str("pileType", "BORED"),
        pileDiameter = m.num("pileDiameter"),
        pileLength = m.num("pileLength"),
        numberOfPiles = m.intg("numberOfPiles"),
        pileSpacing = m.num("pileSpacing"),
        axialLoad = m.num("axialLoad"),
        lateralLoad = m.num("lateralLoad"),
        momentLoad = m.num("momentLoad"),
        cu = m.num("cu"),
        phi = m.num("phi"),
        gammaSoil = m.num("gammaSoil"),
        waterTableDepth = m.num("waterTableDepth"),
        safetyFactor = m.num("safetyFactor", 2.5),
        pileGroupPattern = m.str("pileGroupPattern"),
        scourDepth = m.num("scourDepth"),
        capConcreteCover = m.num("capConcreteCover"),
        columnWidth = m.num("columnWidth"),
        columnLength = m.num("columnLength")
    )
    QcDiscipline.RETAINING_WALLS -> QualityControlEngineNew.retainingWallQc(
        wallHeight = m.num("wallHeight"),
        wallThickness = m.num("wallThickness"),
        soilType = m.str("soilType", "SAND"),
        backfillUnitWeight = m.num("backfillUnitWeight"),
        backfillFrictionAngle = m.num("backfillFrictionAngle"),
        backfillCohesion = m.num("backfillCohesion"),
        surchargeLoad = m.num("surchargeLoad"),
        groundwaterLevel = m.num("groundwaterLevel"),
        foundationDepth = m.num("foundationDepth"),
        foundationWidth = m.num("foundationWidth"),
        foundationSoilBearing = m.num("foundationSoilBearing"),
        concreteStrength = m.num("concreteStrength"),
        steelGrade = m.num("steelGrade"),
        seismicCoefficient = m.num("seismicCoefficient"),
        designCode = designCode
    )
    QcDiscipline.WATERPROOFING -> QualityControlEngineNew.waterproofingQc(
        waterproofingType = m.str("waterproofingType"),
        membraneThickness = m.num("membraneThickness"),
        coatingThickness = m.num("coatingThickness"),
        adhesionStrength = m.num("adhesionStrength"),
        overlapWidth = m.num("overlapWidth"),
        leakTestResult = m.bool("leakTestResult"),
        cornerDetails = m.bool("cornerDetails"),
        drainageLayer = m.bool("drainageLayer"),
        protectionLayer = m.bool("protectionLayer"),
        substrateCondition = m.str("substrateCondition"),
        applicationTemperature = m.num("applicationTemperature"),
        curingTime = m.num("curingTime")
    )
    QcDiscipline.MASONRY -> QualityControlEngineNew.masonryQc(
        masonryType = m.str("masonryType"),
        blockDimensions = m.str("blockDimensions"),
        mortarType = m.str("mortarType"),
        mortarStrength = m.num("mortarStrength"),
        wallThickness = m.num("wallThickness"),
        plasterThickness = m.num("plasterThickness"),
        plasterType = m.str("plasterType"),
        surfaceFlatness = m.num("surfaceFlatness"),
        verticality = m.num("verticality"),
        adhesionStrength = m.num("adhesionStrength"),
        curingMethod = m.str("curingMethod"),
        jointThickness = m.num("jointThickness")
    )
    QcDiscipline.FLOORING -> QualityControlEngineNew.flooringQc(
        flooringType = m.str("flooringType"),
        slabThickness = m.num("slabThickness"),
        surfaceFlatness = m.num("surfaceFlatness"),
        compressiveStrength = m.num("compressiveStrength"),
        tensileStrength = m.num("tensileStrength"),
        jointSpacing = m.num("jointSpacing"),
        jointWidth = m.num("jointWidth"),
        finishType = m.str("finishType"),
        curingMethod = m.str("curingMethod"),
        levelness = m.num("levelness")
    )
    QcDiscipline.PRECAST -> QualityControlEngineNew.precastQc(
        elementType = m.str("elementType"),
        elementDimensions = m.str("elementDimensions"),
        concreteStrength = m.num("concreteStrength"),
        steelGrade = m.num("steelGrade"),
        coverThickness = m.num("coverThickness"),
        surfaceFinish = m.str("surfaceFinish"),
        liftingAnchors = m.bool("liftingAnchors"),
        connectionDetails = m.str("connectionDetails"),
        transportDamage = m.bool("transportDamage"),
        erectionTolerance = m.num("erectionTolerance"),
        groutStrength = m.num("groutStrength"),
        designCode = designCode
    )
    QcDiscipline.POST_TENSION -> QualityControlEngineNew.postTensionQc(
        tendonType = m.str("tendonType"),
        tendonDiameter = m.num("tendonDiameter"),
        jackingForce = m.num("jackingForce"),
        elongation = m.num("elongation"),
        expectedElongation = m.num("expectedElongation"),
        groutStrength = m.num("groutStrength"),
        groutFlow = m.num("groutFlow"),
        anchorageType = m.str("anchorageType"),
        ductMaterial = m.str("ductMaterial"),
        corrosionProtection = m.str("corrosionProtection"),
        stressLoss = m.num("stressLoss"),
        frictionCoefficient = m.num("frictionCoefficient"),
        wobbleCoefficient = m.num("wobbleCoefficient")
    )
    QcDiscipline.GROUTING -> QualityControlEngineNew.groutingQc(
        groutType = m.str("groutType"),
        flowTime = m.num("flowTime"),
        shrinkage = m.num("shrinkage"),
        compressiveStrength = m.num("compressiveStrength"),
        viscosity = m.num("viscosity"),
        gelTime = m.num("gelTime"),
        bondStrength = m.num("bondStrength"),
        injectionPressure = m.num("injectionPressure"),
        injectionVolume = m.num("injectionVolume"),
        curingTime = m.num("curingTime"),
        substrateCondition = m.str("substrateCondition")
    )
    QcDiscipline.SOIL_IMPROVEMENT -> QualityControlEngineNew.soilImprovementQc(
        improvementType = m.str("improvementType"),
        improvementDepth = m.num("improvementDepth"),
        improvementDiameter = m.num("improvementDiameter"),
        spacing = m.num("spacing"),
        preImprovementBearing = m.num("preImprovementBearing"),
        postImprovementBearing = m.num("postImprovementBearing"),
        preImprovementSPT = m.intg("preImprovementSPT"),
        postImprovementSPT = m.intg("postImprovementSPT"),
        settlementBefore = m.num("settlementBefore"),
        settlementAfter = m.num("settlementAfter"),
        improvementMaterial = m.str("improvementMaterial"),
        qualityControlTest = m.str("qualityControlTest")
    )
    QcDiscipline.WATER_TANKS -> QualityControlEngineNew.waterTanksQc(
        tankType = m.str("tankType"),
        tankCapacity = m.num("tankCapacity"),
        tankDimensions = m.str("tankDimensions"),
        concreteStrength = m.num("concreteStrength"),
        steelGrade = m.num("steelGrade"),
        wallThickness = m.num("wallThickness"),
        floorThickness = m.num("floorThickness"),
        roofThickness = m.num("roofThickness"),
        waterproofingType = m.str("waterproofingType"),
        leakTestResult = m.bool("leakTestResult"),
        hydrostaticTest = m.bool("hydrostaticTest"),
        valveType = m.str("valveType"),
        drainageSystem = m.bool("drainageSystem"),
        designCode = designCode
    )
    QcDiscipline.DRAINAGE -> QualityControlEngineNew.drainageQc(
        pipeMaterial = m.str("pipeMaterial"),
        pipeDiameter = m.num("pipeDiameter"),
        pipeLength = m.num("pipeLength"),
        slope = m.num("slope"),
        jointType = m.str("jointType"),
        leakTestResult = m.bool("leakTestResult"),
        manholeSpacing = m.num("manholeSpacing"),
        manholeDepth = m.num("manholeDepth"),
        flowRate = m.num("flowRate"),
        invertLevel = m.num("invertLevel"),
        backfillCompaction = m.num("backfillCompaction")
    )
    QcDiscipline.PAINTING -> QualityControlEngineNew.paintingQc(
        paintType = m.str("paintType"),
        dft = m.num("dft"),
        adhesionStrength = m.num("adhesionStrength"),
        colorUniformity = m.bool("colorUniformity"),
        surfacePreparation = m.str("surfacePreparation"),
        numberOfCoats = m.intg("numberOfCoats"),
        coatingThickness = m.num("coatingThickness"),
        fireRating = m.str("fireRating"),
        curingTime = m.num("curingTime"),
        ambientTemperature = m.num("ambientTemperature"),
        humidity = m.num("humidity")
    )
    QcDiscipline.SITE_WORKS -> QualityControlEngineNew.siteWorksQc(
        gradingLevel = m.num("gradingLevel"),
        slope = m.num("slope"),
        fenceType = m.str("fenceType"),
        gateType = m.str("gateType"),
        lightingType = m.str("lightingType"),
        landscapingType = m.str("landscapingType"),
        pavementType = m.str("pavementType"),
        drainageSystem = m.bool("drainageSystem"),
        safetySigns = m.bool("safetySigns"),
        fireExtinguishers = m.bool("fireExtinguishers"),
        firstAidKit = m.bool("firstAidKit")
    )
    QcDiscipline.FIRE_PROTECTION -> QualityControlEngineNew.fireProtectionQc(
        protectionType = m.str("protectionType"),
        coatingThickness = m.num("coatingThickness"),
        adhesionStrength = m.num("adhesionStrength"),
        fireRating = m.str("fireRating"),
        coverageArea = m.num("coverageArea"),
        applicationMethod = m.str("applicationMethod"),
        curingTime = m.num("curingTime"),
        ambientTemperature = m.num("ambientTemperature"),
        humidity = m.num("humidity"),
        surfacePreparation = m.str("surfacePreparation")
    )
}

@Composable
private fun DisciplineQcTab(
    viewModel: QualityEngineerViewModel,
    discipline: QcDiscipline,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    var designCode by remember(discipline) { mutableStateOf(defaultDesignCode()) }
    val values = remember(discipline) {
        mutableStateMapOf<String, String>().apply {
            discipline.fields.forEach { put(it.key, it.default) }
        }
    }
    var outcome by remember(discipline) { mutableStateOf<QualityControlEngine.QcOutcome?>(null) }

    val disciplineTitle = stringResource(discipline.titleRes)
    val disciplineSubtitle = stringResource(discipline.subtitleRes)
    val inspectionPassedMessage = stringResource(R.string.qc_msg_inspection_passed_saved)
    val inspectionReviewMessage = stringResource(R.string.qc_msg_inspection_needs_review)
    val invalidNumberMessage = stringResource(R.string.qc_msg_enter_valid_number)

    // Any numeric field that cannot be parsed would silently become 0.0 and
    // change the result (a zero load or zero strength is not a valid entry).
    // Block the calculation instead and flag the offending fields.
    val invalidNumberKeys = discipline.fields
        .filter { it.type == QcFieldType.NUMBER }
        .map { it.key }
        .filter { values[it]?.trim()?.toDoubleOrNull() == null }
        .toSet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DesignCodeSelectorRow(
            selectedCode = designCode,
            onCodeSelected = { designCode = it; outcome = null }
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    disciplineSubtitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                discipline.fields.forEach { field ->
                    when (field.type) {
                        QcFieldType.NUMBER -> OutlinedTextField(
                            value = values[field.key] ?: "",
                            onValueChange = { values[field.key] = it },
                            label = { Text(stringResource(field.labelRes)) },
                            isError = field.key in invalidNumberKeys,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        QcFieldType.TEXT -> OutlinedTextField(
                            value = values[field.key] ?: "",
                            onValueChange = { values[field.key] = it },
                            label = { Text(stringResource(field.labelRes)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        QcFieldType.BOOL -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(field.labelRes), style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = (values[field.key] ?: "false").equals("true", true),
                                onCheckedChange = { values[field.key] = it.toString() }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Button(
            onClick = {
                if (invalidNumberKeys.isEmpty()) {
                    val result = computeDisciplineOutcome(discipline, values, designCode)
                    outcome = result
                    viewModel.persistDisciplineOutcome(discipline.category, discipline.elementId, designCode, result)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (result.passed) inspectionPassedMessage else inspectionReviewMessage
                        )
                    }
                } else {
                    scope.launch { snackbarHostState.showSnackbar(invalidNumberMessage) }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_reports_execute_inspection))
        }

        Button(
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = disciplineTitle,
                    subtitle = disciplineSubtitle,
                    outcome = outcome,
                    designCode = designCode
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_reports_export_pdf_btn))
        }

        DisciplineDrawingCard(discipline, values, designCode, outcome?.passed == true)
        QcOutcomeCard(outcome)
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
                        if (ok) context.getString(R.string.qc_reports_import_success) else context.getString(R.string.qc_reports_import_failed)
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
                Text(stringResource(R.string.qc_reports_quality_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                ResultRow(stringResource(R.string.qc_reports_total_inspections), "${uiState.totalInspections}")
                ResultRow(stringResource(R.string.qc_reports_passed), "${uiState.passedInspections}")
                ResultRow(stringResource(R.string.qc_reports_failed), "${uiState.failedInspections}")
                ResultRow(
                    stringResource(R.string.qc_reports_success_rate),
                    "${String.format("%.1f", uiState.successRate)}%"
                )
            }
        }

        if (uiState.categorySummary.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.qc_reports_category_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                                    stringResource(
                                        R.string.qc_reports_passed_ratio,
                                        row.passedCount,
                                        row.total
                                    ),
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

        ItpSection(
            uiState = uiState,
            onLoadDefault = { viewModel.loadDefaultItp() },
            onClear = { viewModel.clearItp() },
            onUpdateStatus = { id, status -> viewModel.updateItpStatus(id, status) },
            onExport = { viewModel.exportItpCsv(context) }
        )

        NcrSection(
            uiState = uiState,
            onCreate = { discipline, element, desc, ref, severity ->
                viewModel.createNcr(discipline, element, desc, ref, severity)
            },
            onClose = { id -> viewModel.closeNcr(id, "", "") },
            onExport = {
                viewModel.exportNcrCsv(context) { file ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (file != null) context.getString(R.string.qc_reports_ncr_export_success) else context.getString(R.string.qc_reports_ncr_export_failed)
                        )
                    }
                }
            }
        )

        // Search & Filter Bar
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.qc_reports_filter_records), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(stringResource(R.string.qc_reports_search_hint)) },
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
                            value = selectedCategory?.takeIf { it.isNotBlank() } ?: stringResource(R.string.qc_reports_all_categories),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.qc_reports_category)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.qc_reports_all_categories)) },
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
                                true -> stringResource(R.string.qc_reports_passed_only)
                                false -> stringResource(R.string.qc_reports_failed_only)
                                null -> stringResource(R.string.qc_reports_all_statuses)
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.qc_reports_status)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.qc_reports_all_statuses)) },
                                onClick = { selectedPassFilter = null; statusExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.qc_reports_passed_only)) },
                                onClick = { selectedPassFilter = true; statusExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.qc_reports_failed_only)) },
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
                Text(if (isExporting) stringResource(R.string.qc_reports_exporting) else stringResource(R.string.qc_reports_export_pdf_btn))
            }
            Button(
                onClick = { viewModel.exportQualityCsv(context) },
                enabled = !isExporting,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.TableChart, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.qc_reports_export_csv))
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
                Text(stringResource(R.string.qc_reports_json_backup))
            }
            Button(
                onClick = { jsonImportLauncher.launch(arrayOf("application/json", "text/json", "*/*")) },
                enabled = !isExporting,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.qc_reports_json_restore))
            }
        }

        Button(
            onClick = { showSettings = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.qc_reports_project_settings))
        }

        if (filteredRecords.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.qc_reports_saved_records_format, filteredRecords.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                                            if (record.inspectorName.isNotBlank()) append(stringResource(R.string.qc_reports_inspector, record.inspectorName))
                                            if (photoCount > 0) {
                                                if (isNotEmpty()) append("  ·  ")
                                                append(stringResource(R.string.qc_reports_photo_count, photoCount))
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.qc_reports_detail), tint = Color.Gray)
                            IconButton(onClick = { showDeleteConfirm = record.id }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.qc_reports_delete), tint = Color(0xFFF44336))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.clearAllRecords() }) {
                        Text(stringResource(R.string.qc_reports_clear_all))
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.qc_reports_no_matching), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
        }

        if (showSettings) {
            ProjectSettingsDialog(onDismiss = { showSettings = false })
        }

        showDeleteConfirm?.let { id ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text(stringResource(R.string.qc_reports_delete_record)) },
                text = { Text(stringResource(R.string.qc_reports_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteQualityRecord(id); showDeleteConfirm = null }) {
                        Text(stringResource(R.string.qc_reports_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) {
                        Text(stringResource(R.string.qc_reports_cancel))
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


@Composable
private fun ItpSection(
    uiState: com.civileg.app.viewmodel.ReportsQCState,
    onLoadDefault: () -> Unit,
    onClear: () -> Unit,
    onUpdateStatus: (Long, String) -> Unit,
    onExport: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.qc_reports_itp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${uiState.itpItems.size}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onLoadDefault, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.qc_reports_load_itp), fontSize = 12.sp) }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.qc_reports_clear), fontSize = 12.sp) }
            }
            if (uiState.itpItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.qc_reports_export_itp_csv), fontSize = 12.sp)
                }
            }
            if (uiState.itpItems.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.qc_reports_no_itp), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                uiState.itpItems.take(40).forEach { item ->
                    val statusColor = when (item.status) {
                        "passed" -> Color(0xFF4CAF50)
                        "failed" -> Color(0xFFF44336)
                        "in_progress" -> Color(0xFFFF9800)
                        else -> Color.Gray
                    }
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${item.discipline} — ${item.inspectionItem}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(item.acceptanceCriteria, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(stringResource(R.string.qc_reports_reference, item.referenceCode), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.status,
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onUpdateStatus(item.id, "passed") }) { Text(stringResource(R.string.qc_reports_status_passed), fontSize = 11.sp) }
                            TextButton(onClick = { onUpdateStatus(item.id, "failed") }) { Text(stringResource(R.string.qc_reports_status_failed), fontSize = 11.sp) }
                        }
                    }
                    Divider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NcrSection(
    uiState: com.civileg.app.viewmodel.ReportsQCState,
    onCreate: (String, String, String, String, String) -> Unit,
    onClose: (Long) -> Unit,
    onExport: () -> Unit
) {
    var discipline by remember { mutableStateOf("CONCRETE") }
    var elementId by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf("minor") }
    var severityExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.qc_reports_ncr), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.qc_reports_ncr_open_count, uiState.openNcrCount), fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
            }
            if (uiState.ncrItems.isNotEmpty()) {
                TextButton(onClick = onExport, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.qc_reports_export_csv), fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(value = discipline, onValueChange = { discipline = it }, label = { Text(stringResource(R.string.qc_reports_ncr_field_discipline)) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(value = elementId, onValueChange = { elementId = it }, label = { Text(stringResource(R.string.qc_reports_ncr_field_element)) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.qc_reports_ncr_field_description)) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(value = reference, onValueChange = { reference = it }, label = { Text(stringResource(R.string.qc_reports_ncr_field_reference)) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))

            ExposedDropdownMenuBox(
                expanded = severityExpanded,
                onExpandedChange = { severityExpanded = !severityExpanded }
            ) {
                OutlinedTextField(
                    value = severity,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.qc_reports_ncr_field_severity)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = severityExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = severityExpanded, onDismissRequest = { severityExpanded = false }) {
                    listOf("minor", "major", "critical").forEach { s ->
                        DropdownMenuItem(text = { Text(s) }, onClick = { severity = s; severityExpanded = false })
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (description.isNotBlank()) {
                        onCreate(discipline, elementId, description, reference, severity)
                        elementId = ""; description = ""; reference = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.qc_reports_open_ncr_report))
            }

            if (uiState.ncrItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.qc_reports_export_ncr_csv))
                }
            }

            if (uiState.ncrItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                uiState.ncrItems.take(30).forEach { ncr ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${ncr.ncrNumber} • ${ncr.discipline}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(ncr.description, style = MaterialTheme.typography.bodySmall)
                        val sevColor = when (ncr.severity) {
                            "critical" -> Color(0xFFF44336)
                            "major" -> Color(0xFFFF9800)
                            else -> Color.Gray
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${ncr.severity} • ${ncr.status}",
                                style = MaterialTheme.typography.labelMedium,
                                color = sevColor,
                                modifier = Modifier.weight(1f)
                            )
                            if (ncr.status != "closed") {
                                TextButton(onClick = { onClose(ncr.id) }) { Text(stringResource(R.string.qc_reports_close), fontSize = 11.sp) }
                            }
                        }
                    }
                    Divider()
                }
            }
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
        title = { Text(stringResource(R.string.qc_reports_inspection_details)) },
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
                    label = { Text(stringResource(R.string.qc_reports_inspector_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.qc_reports_location)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weather,
                    onValueChange = { weather = it },
                    label = { Text(stringResource(R.string.qc_reports_weather)) },
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
                        label = { Text(stringResource(R.string.qc_reports_inspection_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        INSPECTION_TYPES.forEach { (key, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
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
                        value = complianceStandard.ifBlank { stringResource(R.string.qc_reports_not_specified) },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.qc_reports_compliance_code)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = standardExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = standardExpanded, onDismissRequest = { standardExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.qc_reports_not_specified)) },
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
                    label = { Text(stringResource(R.string.qc_reports_notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()
                Text(stringResource(R.string.qc_reports_photos_format, photos.size), style = MaterialTheme.typography.titleSmall)
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
                        Text(stringResource(R.string.qc_reports_camera))
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
                        Text(stringResource(R.string.qc_reports_gallery))
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
                                        contentDescription = stringResource(R.string.qc_reports_photo_delete),
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
                Text(stringResource(R.string.qc_reports_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.qc_reports_cancel))
            }
        }
    )
}

private val INSPECTION_TYPES = listOf(
    "routine" to R.string.qc_reports_type_routine,
    "special" to R.string.qc_reports_type_special,
    "final" to R.string.qc_reports_type_final
)

private val COMPLIANCE_CODES = listOf("ECP", "ACI", "SBC", "AISC", "AASHTO", "ASCE", "TUNNEL")

@Composable
private fun inspectionTypeLabel(key: String): String {
    val res = INSPECTION_TYPES.firstOrNull { it.first == key }?.second
    return if (res == null) key else stringResource(res)
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
    val passedCount = outcome.checks.count { it.passed }
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
            if (outcome.checks.isNotEmpty()) {
                Text(
                    "Checks: $passedCount / ${outcome.checks.size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (outcome.passed) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
                Spacer(modifier = Modifier.height(4.dp))
                outcome.checks.forEach { check ->
                    QualityCheckRow(
                        label = check.name,
                        passed = check.passed,
                        detail = qcCheckDetail(check)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
            }
            outcome.details.forEach { (key, value) ->
                ResultRow(key, value)
            }
            if (outcome.utilizationRatio.isFinite() && outcome.utilizationRatio > 0.0) {
                ResultRow("Utilization ratio", "%.2f".format(outcome.utilizationRatio))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(outcome.summary, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.quality_control_screening_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Compact formatting for check values, limits and units. */
private fun fmtQc(v: Double): String = when {
    v.isNaN() || v.isInfinite() -> "-"
    abs(v) >= 1000.0 -> "%.0f".format(v)
    abs(v) >= 10.0 -> "%.1f".format(v)
    else -> "%.2f".format(v)
}

/** "calculated / limit unit | reference" line for a single QC check. */
private fun qcCheckDetail(check: com.civileg.app.domain.entities.GenericSafetyCheck): String {
    val reference = check.reference
    val unit = check.unit
    return if (unit.isBlank() || unit == "-") {
        reference ?: ""
    } else {
        buildString {
            append(fmtQc(check.calculated))
            append(" / ")
            append(fmtQc(check.limit))
            append(" ")
            append(unit)
            if (!reference.isNullOrBlank()) {
                append("  |  ")
                append(reference)
            }
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
        title = { Text(stringResource(R.string.qc_reports_project_settings)) },
        text = {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text(stringResource(R.string.qc_reports_project_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = engineerName,
                    onValueChange = { engineerName = it },
                    label = { Text(stringResource(R.string.qc_reports_engineer_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = { Text(stringResource(R.string.qc_reports_company_name)) },
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
                Text(stringResource(R.string.qc_reports_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.qc_reports_cancel))
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════
// Discipline drawing previews (input-driven, mirrors the QC checks)
// ═══════════════════════════════════════════════════════════════════════


@Composable
private fun DisciplineDrawingCard(
    discipline: QcDiscipline,
    values: Map<String, String>,
    designCode: DesignCode,
    passed: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.qc_reports_discipline_drawing_preview), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            when (discipline) {
                QcDiscipline.RETAINING_WALLS -> {
                    val baseWidth = values.num("foundationWidth", 4.0) * 1000.0
                    ProfessionalRetainingWallDrawing(
                        wallHeight = values.num("wallHeight", 5.0),
                        wallTopThickness = values.num("wallThickness", 300.0) * 0.8,
                        wallBottomThickness = values.num("wallThickness", 300.0),
                        baseWidth = baseWidth,
                        baseThickness = 300.0,
                        toeLength = baseWidth * 0.25,
                        heelLength = baseWidth * 0.35,
                        mainRebarDia = 16.0,
                        mainRebarSpacing = 200.0,
                        distRebarDia = 12.0,
                        distRebarSpacing = 250.0,
                        baseRebarDia = 16.0,
                        baseRebarSpacing = 200.0,
                        cover = 50.0,
                        backfillAngle = values.num("backfillFrictionAngle", 30.0),
                        fsOverturning = 2.0,
                        fsSliding = 1.5,
                        allowableBearingPressure = values.num("foundationSoilBearing", 200.0),
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(320.dp)
                    )
                }
                QcDiscipline.PILING -> {
                    val n = values.intg("numberOfPiles", 9).coerceAtLeast(1)
                    val spacing = values.num("pileSpacing", 1.8) * 1000.0
                    val capSize = maxOf(spacing * (ceil(sqrt(n.toDouble())).toInt() + 1), 2000.0)
                    ProfessionalPileDrawing(
                        pileDiameter = values.num("pileDiameter", 600.0),
                        pileLength = values.num("pileLength", 15.0),
                        numberOfPiles = n,
                        pileSpacing = spacing,
                        pattern = values.str("pileGroupPattern", "3x3"),
                        capWidth = capSize,
                        capLength = capSize,
                        capThickness = 1000.0,
                        columnWidth = values.num("columnWidth", 600.0),
                        columnLength = values.num("columnLength", 600.0),
                        longitBars = 8,
                        longitDia = 20,
                        tiesDia = 10,
                        tiesSpacing = 200,
                        capRebarDia = 16,
                        capRebarCount = 12,
                        soilType = values.str("soilType", "SAND"),
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    )
                }
                QcDiscipline.WATER_TANKS -> {
                    val dims = values.str("tankDimensions", "20x10x3")
                        .split(Regex("[xX*]"))
                        .mapNotNull { it.trim().toDoubleOrNull() }
                    val length = dims.getOrNull(0) ?: 20.0
                    val width = dims.getOrNull(1) ?: 10.0
                    val height = dims.getOrNull(2) ?: 3.0
                    ProfessionalTankDrawing(
                        tankType = values.str("tankType", "Rectangular"),
                        length = length,
                        width = width,
                        height = height,
                        wallThickness = values.num("wallThickness", 250.0),
                        baseThickness = values.num("floorThickness", 200.0),
                        waterLevel = height * 0.9,
                        verticalRebarDia = 16.0,
                        verticalRebarSpacing = 200.0,
                        horizontalRebarDia = 12.0,
                        horizontalRebarSpacing = 200.0,
                        foundationDepth = 0.5,
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    )
                }
                QcDiscipline.MASONRY -> {
                    MasonryQcDrawing(
                        blockDimensions = values.str("blockDimensions", "200x200x400"),
                        mortarType = values.str("mortarType", "M10"),
                        jointThickness = values.num("jointThickness", 10.0),
                        wallThickness = values.num("wallThickness", 200.0),
                        plasterThickness = values.num("plasterThickness", 15.0),
                        surfaceFlatness = values.num("surfaceFlatness", 2.0),
                        verticality = values.num("verticality", 3.0),
                        mortarStrength = values.num("mortarStrength", 10.0),
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(360.dp)
                    )
                }
                QcDiscipline.DRAINAGE -> {
                    DrainageQcDrawing(
                        pipeMaterial = values.str("pipeMaterial", "RCP"),
                        pipeDiameter = values.num("pipeDiameter", 600.0),
                        pipeLength = values.num("pipeLength", 100.0),
                        slope = values.num("slope", 1.5),
                        manholeSpacing = values.num("manholeSpacing", 40.0),
                        manholeDepth = values.num("manholeDepth", 3.0),
                        invertLevel = values.num("invertLevel", 0.0),
                        backfillCompaction = values.num("backfillCompaction", 96.0),
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    )
                }
                QcDiscipline.EARTHWORKS -> {
                    val depth = values.num("excavationDepth", 3.0)
                    DisciplineQcGenericDrawing(
                        title = "EARTHWORKS QC - EXCAVATION SECTION",
                        subtitle = "${values.str("soilType", "SAND")} | " +
                            values.str("backfillType", "Backfill"),
                        layers = listOf(
                            QcDrawingLayer("Backfill layer", values.num("layerThickness", 250.0)),
                            QcDrawingLayer("Compacted subgrade", depth * 400.0),
                            QcDrawingLayer("Natural ground", depth * 600.0)
                        ),
                        metrics = listOf(
                            QcDrawingMetric("Excavation depth", "%.2f m".format(depth)),
                            QcDrawingMetric("Slope angle", "%.0f deg".format(values.num("slopeAngle", 45.0))),
                            QcDrawingMetric("Field density", "%.1f kN/m3".format(values.num("fieldDensity", 19.0))),
                            QcDrawingMetric("Moisture", "%.1f %%".format(values.num("fieldMoisture", 12.5)))
                        ),
                        passed = passed,
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    )
                }
                QcDiscipline.WATERPROOFING -> {
                    DisciplineQcGenericDrawing(
                        title = "WATERPROOFING QC - LAYER SECTION",
                        subtitle = values.str("waterproofingType", "Membrane"),
                        layers = listOf(
                            QcDrawingLayer("Protection layer", 50.0),
                            QcDrawingLayer("Waterproof membrane", values.num("membraneThickness", 2.0)),
                            QcDrawingLayer("Primer / coating", values.num("coatingThickness", 1.5)),
                            QcDrawingLayer("Concrete substrate", 150.0)
                        ),
                        metrics = listOf(
                            QcDrawingMetric("Membrane", "%.1f mm".format(values.num("membraneThickness", 2.0))),
                            QcDrawingMetric("Overlap", "%.0f mm".format(values.num("overlapWidth", 100.0))),
                            QcDrawingMetric("Adhesion", "%.2f MPa".format(values.num("adhesionStrength", 0.6))),
                            QcDrawingMetric("Leak test", if (values.bool("leakTestResult")) "PASS" else "FAIL")
                        ),
                        passed = passed,
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    )
                }
                QcDiscipline.FLOORING -> {
                    DisciplineQcGenericDrawing(
                        title = "FLOORING QC - SLAB SECTION",
                        subtitle = "${values.str("flooringType", "Floor")} | " +
                            values.str("finishType", "Finish"),
                        layers = listOf(
                            QcDrawingLayer("Finish", 10.0),
                            QcDrawingLayer("Screed / bedding", 50.0),
                            QcDrawingLayer("Slab", values.num("slabThickness", 150.0))
                        ),
                        metrics = listOf(
                            QcDrawingMetric("Slab thickness", "%.0f mm".format(values.num("slabThickness", 150.0))),
                            QcDrawingMetric("Flatness", "%.1f mm/2m".format(values.num("surfaceFlatness", 2.0))),
                            QcDrawingMetric("Compressive", "%.0f MPa".format(values.num("compressiveStrength", 30.0))),
                            QcDrawingMetric("Joint spacing", "%.1f m".format(values.num("jointSpacing", 5.0)))
                        ),
                        passed = passed,
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    )
                }
                QcDiscipline.PRECAST -> {
                    val dims = values.str("elementDimensions", "300x600x6000")
                        .split(Regex("[xX*]"))
                        .mapNotNull { it.trim().toDoubleOrNull() }
                    val elementHeight = dims.getOrNull(1) ?: 600.0
                    DisciplineQcGenericDrawing(
                        title = "PRECAST QC - ELEMENT SECTION",
                        subtitle = "${values.str("elementType", "Element")} | " +
                            values.str("elementDimensions", "-"),
                        layers = listOf(
                            QcDrawingLayer("Precast concrete", elementHeight),
                            QcDrawingLayer("Cover zone", values.num("coverThickness", 40.0) * 2.0),
                            QcDrawingLayer("Grout bed", 30.0)
                        ),
                        metrics = listOf(
                            QcDrawingMetric("Concrete", "%.0f MPa".format(values.num("concreteStrength", 40.0))),
                            QcDrawingMetric("Cover", "%.0f mm".format(values.num("coverThickness", 40.0))),
                            QcDrawingMetric("Erection tol.", "%.0f mm".format(values.num("erectionTolerance", 5.0))),
                            QcDrawingMetric("Transport", if (values.bool("transportDamage")) "DAMAGED" else "OK")
                        ),
                        passed = passed,
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    )
                }
                QcDiscipline.POST_TENSION -> {
                    val tendonType = values.str("tendonType", "7-wire strand")
                    val fpu = when {
                        tendonType.contains("strand", true) -> 1860.0
                        tendonType.contains("wire", true) -> 1670.0
                        tendonType.contains("bar", true) -> 1030.0
                        else -> 1860.0
                    }
                    DisciplineQcGenericDrawing(
                        title = "POST-TENSION QC - TENDON SECTION",
                        subtitle = "$tendonType | ${values.str("anchorageType", "Anchorage")}",
                        layers = listOf(
                            QcDrawingLayer("Duct", 10.0),
                            QcDrawingLayer("Grout", 8.0),
                            QcDrawingLayer("Tendon", values.num("tendonDiameter", 15.2))
                        ),
                        metrics = listOf(
                            QcDrawingMetric("Tendon dia.", "%.1f mm".format(values.num("tendonDiameter", 15.2))),
                            QcDrawingMetric("Jacking force", "%.0f kN".format(values.num("jackingForce", 1500.0))),
                            QcDrawingMetric("Fpu", "%.0f MPa".format(fpu)),
                            QcDrawingMetric("Elongation", "%.1f mm".format(values.num("elongation", 180.0))),
                            QcDrawingMetric("Expected", "%.1f mm".format(values.num("expectedElongation", 180.0))),
                            QcDrawingMetric("Stress loss", "%.1f %%".format(values.num("stressLoss", 15.0)))
                        ),
                        passed = passed,
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    )
                }
                QcDiscipline.GROUTING -> {
                    DisciplineQcGenericDrawing(
                        title = "GROUTING QC - INJECTION SECTION",
                        subtitle = values.str("groutType", "Grout"),
                        layers = listOf(
                            QcDrawingLayer(
                                "Grout",
                                (values.num("injectionVolume", 100.0).coerceAtLeast(1.0) / 100.0)
                            ),
                            QcDrawingLayer("Hole / void", 120.0)
                        ),
                        metrics = listOf(
                            QcDrawingMetric("Flow time", "%.1f s".format(values.num("flowTime", 30.0))),
                            QcDrawingMetric("Viscosity", "%.0f cP".format(values.num("viscosity", 500.0))),
                            QcDrawingMetric("Gel time", "%.0f min".format(values.num("gelTime", 10.0))),
                            QcDrawingMetric("Pressure", "%.2f MPa".format(values.num("injectionPressure", 0.35))),
                            QcDrawingMetric("Volume", "%.0f L".format(values.num("injectionVolume", 100.0)))
                        ),
                        passed = passed,
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    )
                }
                QcDiscipline.SOIL_IMPROVEMENT -> {
                    val depth = values.num("improvementDepth", 8.0)
                    DisciplineQcGenericDrawing(
                        title = "SOIL IMPROVEMENT QC - SECTION",
                        subtitle = "${values.str("improvementType", "Improvement")} | " +
                            values.str("improvementMaterial", "Material"),
                        layers = listOf(
                            QcDrawingLayer("Improvement zone", depth * 1000.0),
                            QcDrawingLayer("Natural soil", depth * 600.0)
                        ),
                        metrics = listOf(
                            QcDrawingMetric("Depth", "%.1f m".format(depth)),
                            QcDrawingMetric("Diameter", "%.2f m".format(values.num("improvementDiameter", 0.8))),
                            QcDrawingMetric("Spacing", "%.2f m".format(values.num("spacing", 2.0))),
                            QcDrawingMetric("Bearing", "%.0f kPa".format(values.num("postImprovementBearing", 160.0))),
                            QcDrawingMetric("Settlement", "%.0f mm".format(values.num("settlementAfter", 20.0)))
                        ),
                        passed = passed,
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    )
                }
                QcDiscipline.PAINTING -> {
                    val dft = values.num("dft", 150.0)
                    DisciplineQcGenericDrawing(
                        title = "PAINTING QC - COATING BUILD-UP",
                        subtitle = "${values.str("paintType", "Paint")} | " +
                            values.str("surfacePreparation", "Surface"),
                        layers = listOf(
                            QcDrawingLayer("Topcoat", dft / 1000.0),
                            QcDrawingLayer("Primer", dft / 2000.0),
                            QcDrawingLayer("Steel substrate", 5.0)
                        ),
                        metrics = listOf(
                            QcDrawingMetric("DFT", "%.0f um".format(dft)),
                            QcDrawingMetric("Adhesion", "%.2f MPa".format(values.num("adhesionStrength", 2.0))),
                            QcDrawingMetric("Coats", "%.0f".format(values.num("numberOfCoats", 2.0))),
                            QcDrawingMetric("Fire rating", values.str("fireRating", "None")),
                            QcDrawingMetric("Humidity", "%.0f %%".format(values.num("humidity", 60.0)))
                        ),
                        passed = passed,
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    )
                }
                QcDiscipline.SITE_WORKS -> {
                    DisciplineQcGenericDrawing(
                        title = "SITE WORKS QC - PAVEMENT SECTION",
                        subtitle = "${values.str("pavementType", "Pavement")} | " +
                            values.str("fenceType", "Fence"),
                        layers = listOf(
                            QcDrawingLayer("Pavement", 80.0),
                            QcDrawingLayer("Base course", 200.0),
                            QcDrawingLayer("Subgrade", 300.0)
                        ),
                        metrics = listOf(
                            QcDrawingMetric("Grading level", "%.2f m".format(values.num("gradingLevel", 100.0))),
                            QcDrawingMetric("Slope", "%.2f %%".format(values.num("slope", 0.5))),
                            QcDrawingMetric("Drainage", if (values.bool("drainageSystem")) "YES" else "NO"),
                            QcDrawingMetric("Safety signs", if (values.bool("safetySigns")) "YES" else "NO"),
                            QcDrawingMetric("First aid", if (values.bool("firstAidKit")) "YES" else "NO")
                        ),
                        passed = passed,
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    )
                }
                QcDiscipline.FIRE_PROTECTION -> {
                    DisciplineQcGenericDrawing(
                        title = "FIRE PROTECTION QC - COATING SECTION",
                        subtitle = "${values.str("protectionType", "Protection")} | " +
                            values.str("fireRating", "-"),
                        layers = listOf(
                            QcDrawingLayer("Intumescent coating", values.num("coatingThickness", 0.8)),
                            QcDrawingLayer("Primer", 0.05),
                            QcDrawingLayer("Steel substrate", 10.0)
                        ),
                        metrics = listOf(
                            QcDrawingMetric(
                                "Coating",
                                "%.0f um".format(values.num("coatingThickness", 0.8) * 1000.0)
                            ),
                            QcDrawingMetric("Fire rating", values.str("fireRating", "-")),
                            QcDrawingMetric("Adhesion", "%.2f MPa".format(values.num("adhesionStrength", 1.2))),
                            QcDrawingMetric("Coverage", "%.0f m2".format(values.num("coverageArea", 500.0))),
                            QcDrawingMetric("Humidity", "%.0f %%".format(values.num("humidity", 60.0)))
                        ),
                        passed = passed,
                        designCode = designCode,
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    )
                }
            }
        }
    }
}
