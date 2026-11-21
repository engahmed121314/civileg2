package com.civileg.app.features.quality

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
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
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
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.utils.SoilType
import com.civileg.app.features.quality.QualityEngineerViewModel
import com.civileg.app.viewmodel.ReportsQCState
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.*

fun ConcreteQCTab(
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
                AppText(stringResource(R.string.qc_concrete_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.elementId,
                    onValueChange = { viewModel.updateConcreteField("elementId", it) },
                    label = { AppText(stringResource(R.string.qc_concrete_element_id)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.volume,
                        onValueChange = { viewModel.updateConcreteField("volume", it) },
                        label = { AppText(stringResource(R.string.qc_concrete_volume)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.slump,
                        onValueChange = { viewModel.updateConcreteField("slump", it) },
                        label = { AppText(stringResource(R.string.qc_concrete_slump)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.fcu,
                        onValueChange = { viewModel.updateConcreteField("fcu", it) },
                        label = { AppText(stringResource(R.string.qc_concrete_fcu)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.sampleId,
                        onValueChange = { viewModel.updateConcreteField("sampleId", it) },
                        label = { AppText(stringResource(R.string.qc_concrete_sample_id)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.strength7Days,
                        onValueChange = { viewModel.updateConcreteField("strength7Days", it) },
                        label = { AppText(stringResource(R.string.qc_concrete_strength_7days)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.strength28Days,
                        onValueChange = { viewModel.updateConcreteField("strength28Days", it) },
                        label = { AppText(stringResource(R.string.qc_concrete_strength_28days)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.airContent,
                        onValueChange = { viewModel.updateConcreteField("airContent", it) },
                        label = { AppText(stringResource(R.string.qc_concrete_air_content)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.concreteTemp,
                        onValueChange = { viewModel.updateConcreteField("concreteTemp", it) },
                        label = { AppText(stringResource(R.string.qc_concrete_temp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                AppText(stringResource(R.string.qc_concrete_checks_header), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

        AppButton(
            label = stringResource(R.string.qc_concrete_save),
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.saveConcretePour(context)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Save
        )

        AppButton(
            label = stringResource(R.string.qc_concrete_export),
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = context.getString(R.string.qc_concrete_report_title),
                    subtitle = context.getString(R.string.qc_concrete_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.PictureAsPdf
        )

        QcOutcomeCard(uiState.outcome)

        // Drawing preview
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                AppText(stringResource(R.string.qc_concrete_drawing_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
fun SoilFoundationQCTab(
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
                AppText(stringResource(R.string.qc_soil_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                AppText(stringResource(R.string.qc_soil_calc_method), style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BearingMethod.entries.forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = {
                                AppText(
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

                AppText(stringResource(R.string.qc_soil_soil_type), style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SoilType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedSoilType == type,
                            onClick = { selectedSoilType = type },
                            label = {
                                AppText(
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
                        label = { AppText(stringResource(R.string.qc_soil_cohesion)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.frictionAngle,
                        onValueChange = { viewModel.updateSoilField("frictionAngle", it) },
                        label = { AppText(stringResource(R.string.qc_soil_friction_angle)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.unitWeight,
                        onValueChange = { viewModel.updateSoilField("unitWeight", it) },
                        label = { AppText(stringResource(R.string.qc_soil_unit_weight)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.waterTableDepth,
                        onValueChange = { viewModel.updateSoilField("waterTableDepth", it) },
                        label = { AppText(stringResource(R.string.qc_soil_water_table)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                AppText(stringResource(R.string.qc_soil_foundation_dims), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.foundationWidth,
                        onValueChange = { viewModel.updateSoilField("foundationWidth", it) },
                        label = { AppText(stringResource(R.string.qc_soil_width_b)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.foundationLength,
                        onValueChange = { viewModel.updateSoilField("foundationLength", it) },
                        label = { AppText(stringResource(R.string.qc_soil_length_l)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.foundationDepth,
                        onValueChange = { viewModel.updateSoilField("foundationDepth", it) },
                        label = { AppText(stringResource(R.string.qc_soil_depth_df)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.safetyFactor,
                        onValueChange = { viewModel.updateSoilField("safetyFactor", it) },
                        label = { AppText(stringResource(R.string.qc_soil_safety_factor)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        AppButton(
            label = stringResource(R.string.qc_soil_calculate),
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateSoilBearing(selectedMethod, selectedSoilType)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Calculate
        )

        uiState.soilResult?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AppText(stringResource(R.string.qc_soil_results), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

        AppButton(
            label = stringResource(R.string.qc_soil_export),
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = context.getString(R.string.qc_soil_report_title),
                    subtitle = context.getString(R.string.qc_soil_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.PictureAsPdf
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 3: Reinforcement Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun RebarQCTab(
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
                AppText(stringResource(R.string.qc_rebar_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.barDiameter,
                        onValueChange = { viewModel.updateRebarField("barDiameter", it) },
                        label = { AppText(stringResource(R.string.qc_rebar_bar_diameter)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.numBars,
                        onValueChange = { viewModel.updateRebarField("numBars", it) },
                        label = { AppText(stringResource(R.string.qc_rebar_num_bars)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.fy,
                        onValueChange = { viewModel.updateRebarField("fy", it) },
                        label = { AppText(stringResource(R.string.qc_rebar_fy)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.cover,
                        onValueChange = { viewModel.updateRebarField("cover", it) },
                        label = { AppText(stringResource(R.string.qc_rebar_cover)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.beamWidth,
                        onValueChange = { viewModel.updateRebarField("beamWidth", it) },
                        label = { AppText(stringResource(R.string.qc_rebar_beam_width)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.effectiveDepth,
                        onValueChange = { viewModel.updateRebarField("effectiveDepth", it) },
                        label = { AppText(stringResource(R.string.qc_rebar_effective_depth)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.fcu,
                    onValueChange = { viewModel.updateRebarField("fcu", it) },
                    label = { AppText(stringResource(R.string.qc_rebar_fcu)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        AppButton(
            label = stringResource(R.string.qc_rebar_calculate),
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateRebarChecks()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Calculate
        )

        uiState.rebarChecks?.let { checks ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AppText(stringResource(R.string.qc_check_results), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

        AppButton(
            label = stringResource(R.string.qc_rebar_export),
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = context.getString(R.string.qc_rebar_report_title),
                    subtitle = context.getString(R.string.qc_rebar_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.PictureAsPdf
        )

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 4: Steel Structure Quality Control
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelStructureQCTab(
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
                AppText(stringResource(R.string.qc_steel_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                        label = { AppText(stringResource(R.string.qc_steel_section)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sections.forEach { section ->
                            DropdownMenuItem(
                                text = { AppText(section) },
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
                AppText(stringResource(R.string.qc_steel_member_type), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    memberTypes.forEachIndexed { index, (key, label) ->
                        FilterChip(
                            selected = selectedMemberType == key,
                            onClick = {
                                selectedMemberType = key
                                viewModel.updateSteelField("memberType", key)
                            },
                            label = { AppText(label, fontSize = 10.sp) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.appliedMoment,
                        onValueChange = { viewModel.updateSteelField("appliedMoment", it) },
                        label = { AppText(stringResource(R.string.qc_steel_moment)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.appliedShear,
                        onValueChange = { viewModel.updateSteelField("appliedShear", it) },
                        label = { AppText(stringResource(R.string.qc_steel_shear)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.steelFy,
                        onValueChange = { viewModel.updateSteelField("steelFy", it) },
                        label = { AppText(stringResource(R.string.qc_steel_fy)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.span,
                        onValueChange = { viewModel.updateSteelField("span", it) },
                        label = { AppText(stringResource(R.string.qc_steel_span)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.axialLoad,
                        onValueChange = { viewModel.updateSteelField("axialLoad", it) },
                        label = { AppText(stringResource(R.string.qc_steel_axial_load)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.unbracedLength,
                        onValueChange = { viewModel.updateSteelField("unbracedLength", it) },
                        label = { AppText(stringResource(R.string.qc_steel_unbraced_length)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.allowableDeflection,
                        onValueChange = { viewModel.updateSteelField("allowableDeflection", it) },
                        label = { AppText(stringResource(R.string.qc_steel_allowable_deflection)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    AppText(
                        stringResource(R.string.qc_steel_lb_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AppButton(
            label = stringResource(R.string.qc_steel_check),
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateSteelChecks(selectedSection)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Calculate
        )

        uiState.steelChecks?.let { checks ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AppText(stringResource(R.string.qc_check_results), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

        AppButton(
            label = stringResource(R.string.qc_steel_export),
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = context.getString(R.string.qc_steel_report_title),
                    subtitle = context.getString(R.string.qc_steel_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.PictureAsPdf
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 5: Roads Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun RoadsQCTab(
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
                AppText(stringResource(R.string.qc_roads_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.surfaceLayerThickness,
                        onValueChange = { viewModel.updateRoadsField("surfaceLayerThickness", it) },
                        label = { AppText(stringResource(R.string.qc_roads_surface)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.baseLayerThickness,
                        onValueChange = { viewModel.updateRoadsField("baseLayerThickness", it) },
                        label = { AppText(stringResource(R.string.qc_roads_base)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.subbaseLayerThickness,
                    onValueChange = { viewModel.updateRoadsField("subbaseLayerThickness", it) },
                    label = { AppText(stringResource(R.string.qc_roads_subbase)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                AppText(stringResource(R.string.qc_roads_compaction_checks), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.compactionPercent,
                    onValueChange = { viewModel.updateRoadsField("compactionPercent", it) },
                    label = { AppText(stringResource(R.string.qc_roads_compaction)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.asphaltDensity,
                    onValueChange = { viewModel.updateRoadsField("asphaltDensity", it) },
                    label = { AppText(stringResource(R.string.qc_roads_asphalt_density)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.airVoids,
                    onValueChange = { viewModel.updateRoadsField("airVoids", it) },
                    label = { AppText(stringResource(R.string.qc_roads_air_voids)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        AppButton(
            label = stringResource(R.string.qc_roads_check),
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateRoadsChecks()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Calculate
        )

        AppButton(
            label = stringResource(R.string.qc_roads_export),
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = context.getString(R.string.qc_roads_report_title),
                    subtitle = context.getString(R.string.qc_roads_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.PictureAsPdf
        )

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 6: Bridges Quality Control
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun BridgesQCTab(
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
                AppText(stringResource(R.string.qc_bridges_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.concreteStrength,
                        onValueChange = { viewModel.updateBridgesField("concreteStrength", it) },
                        label = { AppText(stringResource(R.string.qc_bridges_concrete_strength)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.span,
                        onValueChange = { viewModel.updateBridgesField("span", it) },
                        label = { AppText(stringResource(R.string.qc_bridges_span)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.requiredConcreteStrength,
                    onValueChange = { viewModel.updateBridgesField("requiredConcreteStrength", it) },
                    label = { AppText(stringResource(R.string.qc_bridges_required_strength)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.naturalFrequency,
                    onValueChange = { viewModel.updateBridgesField("naturalFrequency", it) },
                    label = { AppText(stringResource(R.string.qc_bridges_natural_frequency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                AppText(
                    stringResource(R.string.qc_bridges_freq_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.measuredDeflection,
                        onValueChange = { viewModel.updateBridgesField("measuredDeflection", it) },
                        label = { AppText(stringResource(R.string.qc_bridges_measured_deflection)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.allowableDeflection,
                        onValueChange = { viewModel.updateBridgesField("allowableDeflection", it) },
                        label = { AppText(stringResource(R.string.qc_bridges_allowable_deflection)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        AppButton(
            label = stringResource(R.string.qc_bridges_check),
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateBridgeChecks()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Calculate
        )

        AppButton(
            label = stringResource(R.string.qc_bridges_export),
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = context.getString(R.string.qc_bridges_report_title),
                    subtitle = context.getString(R.string.qc_bridges_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.PictureAsPdf
        )

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 7: Tunnels Quality Control
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelsQCTab(
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
                AppText(stringResource(R.string.qc_tunnels_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                val classes = listOf("I", "II", "III", "IV", "V")

                AppText(stringResource(R.string.qc_tunnels_rock_class), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    classes.forEach { cls ->
                        FilterChip(
                            selected = uiState.rockClass == cls,
                            onClick = { viewModel.updateTunnelsField("rockClass", cls) },
                            label = { AppText(cls) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.tunnelConvergence,
                        onValueChange = { viewModel.updateTunnelsField("tunnelConvergence", it) },
                        label = { AppText(stringResource(R.string.qc_tunnels_convergence)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.tunnelDiameter,
                        onValueChange = { viewModel.updateTunnelsField("tunnelDiameter", it) },
                        label = { AppText(stringResource(R.string.qc_tunnels_diameter)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.supportThickness,
                    onValueChange = { viewModel.updateTunnelsField("supportThickness", it) },
                    label = { AppText(stringResource(R.string.qc_tunnels_support_thickness)) },
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
                            label = { AppText(stringResource(R.string.qc_tunnels_groundwater)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groundwaterExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                        )
                        ExposedDropdownMenu(
                            expanded = groundwaterExpanded,
                            onDismissRequest = { groundwaterExpanded = false }
                        ) {
                            DropdownMenuItem(text = { AppText(stringResource(R.string.qc_tunnels_groundwater_no)) }, onClick = { viewModel.updateTunnelsField("groundwaterPresent", "false"); groundwaterExpanded = false })
                            DropdownMenuItem(text = { AppText(stringResource(R.string.qc_tunnels_groundwater_yes)) }, onClick = { viewModel.updateTunnelsField("groundwaterPresent", "true"); groundwaterExpanded = false })
                        }
                    }
                    OutlinedTextField(
                        value = uiState.shotcreteStrength,
                        onValueChange = { viewModel.updateTunnelsField("shotcreteStrength", it) },
                        label = { AppText(stringResource(R.string.qc_tunnels_shotcrete_strength)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.rockUcs,
                    onValueChange = { viewModel.updateTunnelsField("rockUcs", it) },
                    label = { AppText(stringResource(R.string.qc_tunnels_rock_ucs)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        AppButton(
            label = stringResource(R.string.qc_tunnels_check),
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateTunnelChecks()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Calculate
        )

        AppButton(
            label = stringResource(R.string.qc_tunnels_export),
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = context.getString(R.string.qc_tunnels_report_title),
                    subtitle = context.getString(R.string.qc_tunnels_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.PictureAsPdf
        )

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 8: Buildings / High-Rise / Hospitals QC
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun BuildingsQCTab(
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
                AppText(stringResource(R.string.qc_buildings_section_inputs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                        AppText(type, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                AppText(stringResource(R.string.qc_buildings_foundation_params), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.numFloors,
                        onValueChange = { viewModel.updateBuildingsField("numFloors", it) },
                        label = { AppText(stringResource(R.string.qc_buildings_num_floors)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.floorHeight,
                        onValueChange = { viewModel.updateBuildingsField("floorHeight", it) },
                        label = { AppText(stringResource(R.string.qc_buildings_floor_height)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.totalWeight,
                        onValueChange = { viewModel.updateBuildingsField("totalWeight", it) },
                        label = { AppText(stringResource(R.string.qc_buildings_total_weight)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.importanceFactor,
                        onValueChange = { viewModel.updateBuildingsField("importanceFactor", it) },
                        label = { AppText(stringResource(R.string.qc_buildings_importance_factor)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.sds,
                        onValueChange = { viewModel.updateBuildingsField("sds", it) },
                        label = { AppText(stringResource(R.string.qc_buildings_sds)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.responseR,
                        onValueChange = { viewModel.updateBuildingsField("responseR", it) },
                        label = { AppText(stringResource(R.string.qc_buildings_response_r)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.storyDrift,
                    onValueChange = { viewModel.updateBuildingsField("storyDrift", it) },
                    label = { AppText(stringResource(R.string.qc_buildings_story_drift)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        AppButton(
            label = stringResource(R.string.qc_buildings_check),
            onClick = {
                runQcInputAction(scope, snackbarHostState) {
                    viewModel.calculateBuildingChecks(buildingType)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Calculate
        )

        uiState.buildingChecks?.let { checks ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AppText(stringResource(R.string.qc_check_results), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

        AppButton(
            label = stringResource(R.string.qc_buildings_export),
            onClick = {
                viewModel.exportDisciplineReport(
                    context = context,
                    title = context.getString(R.string.qc_buildings_report_title),
                    subtitle = context.getString(R.string.qc_buildings_report_subtitle),
                    outcome = uiState.outcome,
                    designCode = uiState.designCode
                )
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.PictureAsPdf
        )

        QcOutcomeCard(uiState.outcome)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Discipline QC tabs (Earthworks … Fire Protection)
// ═══════════════════════════════════════════════════════════════════════

