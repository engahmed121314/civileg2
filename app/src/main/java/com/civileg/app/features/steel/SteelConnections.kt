package com.civileg.app.features.steel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.ui.responsive.dimens
import com.civileg.app.domain.calculations.aci.SteelFlexuralResult
import com.civileg.app.domain.calculations.ecp.BoltDesignResult
import com.civileg.app.domain.calculations.ecp.BlockShearResult
import com.civileg.app.domain.calculations.ecp.SteelConnectionDesign
import com.civileg.app.domain.calculations.ecp.SteelBasePlateDesign
import com.civileg.app.domain.calculations.ecp.WeldDesignResult
import com.civileg.app.domain.entities.*
import com.civileg.app.ui.compose.components.PremiumDesignSystem
import com.civileg.app.ui.compose.components.PremiumSectionHeader
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen as AppInteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalSteelDrawing
import com.civileg.app.ui.compose.components.drawings.ProfessionalWarehouseDrawing
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.captureToAndroidBitmap
import com.civileg.app.features.steel.SteelViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import java.io.File
import java.util.*
import kotlin.math.*
import kotlinx.coroutines.launch
import com.civileg.app.ui.defaultDesignCode

fun WeldDesignTab(viewModel: SteelViewModel) {
    val context = LocalContext.current
    var weldSize by remember { mutableStateOf("6") }
    var weldLength by remember { mutableStateOf("200") }
    var expandedElectrode by remember { mutableStateOf(false) }
    var selectedElectrode by remember { mutableStateOf(ElectrodeType.E70XX) }
    var selectedCode by remember { mutableStateOf(defaultDesignCode()) }
    var capacity by remember { mutableStateOf(0.0) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SteelModuleSectionHeader(stringResource(R.string.steel_weld_design_header), R.drawable.ic_frame) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SteelInputField(weldSize, stringResource(R.string.steel_weld_size_hint), { weldSize = it }, Modifier.fillMaxWidth())
                    SteelInputField(weldLength, stringResource(R.string.steel_weld_length_hint), { weldLength = it }, Modifier.fillMaxWidth())
                    
                    AppText(stringResource(R.string.steel_electrode_type), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    ExposedDropdownMenuBox(
                        expanded = expandedElectrode,
                        onExpandedChange = { expandedElectrode = !expandedElectrode }
                    ) {
                        OutlinedTextField(
                            value = selectedElectrode.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedElectrode) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = expandedElectrode,
                            onDismissRequest = { expandedElectrode = false }
                        ) {
                            ElectrodeType.entries.forEach { electrode ->
                                DropdownMenuItem(
                                    text = { AppText(electrode.displayName) },
                                    onClick = { selectedElectrode = electrode; expandedElectrode = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            AppButton(
                label = stringResource(R.string.steel_calculate_capacity),
                onClick = {
                    capacity = viewModel.calculateWeldCapacity(
                        weldSize.toDoubleOrNull() ?: 6.0,
                        weldLength.toDoubleOrNull() ?: 200.0,
                        selectedElectrode,
                        selectedCode
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (capacity > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        AppText(stringResource(R.string.steel_weld_capacity), fontWeight = FontWeight.Bold)
                        AppText("${String.format("%.1f", capacity)} kN", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                AppButton(
                    label = stringResource(R.string.pdf_report),
                    onClick = { viewModel.exportWeldToPdf(context, weldSize.toDoubleOrNull() ?: 6.0, weldLength.toDoubleOrNull() ?: 200.0, selectedElectrode, selectedCode, capacity) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.PictureAsPdf,
                    containerOverride = MaterialTheme.colorScheme.secondary,
                    contentOverride = onColorFor(MaterialTheme.colorScheme.secondary)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoltDesignTab(viewModel: SteelViewModel) {
    val context = LocalContext.current
    var boltDia by remember { mutableStateOf("20") }
    var boltCount by remember { mutableStateOf("4") }
    var expandedBoltGrade by remember { mutableStateOf(false) }
    var selectedBoltGrade by remember { mutableStateOf(BoltGrade.GRADE_8_8) }
    var selectedCode by remember { mutableStateOf(defaultDesignCode()) }
    var capacity by remember { mutableStateOf(0.0) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SteelModuleSectionHeader(stringResource(R.string.steel_bolt_design_header), R.drawable.ic_frame) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SteelInputField(boltDia, stringResource(R.string.steel_bolt_diameter_hint), { boltDia = it }, Modifier.fillMaxWidth())
                    SteelInputField(boltCount, stringResource(R.string.steel_bolt_count_hint), { boltCount = it }, Modifier.fillMaxWidth())
                    
                    AppText(stringResource(R.string.steel_bolt_grade_label), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    ExposedDropdownMenuBox(
                        expanded = expandedBoltGrade,
                        onExpandedChange = { expandedBoltGrade = !expandedBoltGrade }
                    ) {
                        OutlinedTextField(
                            value = selectedBoltGrade.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBoltGrade) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = expandedBoltGrade,
                            onDismissRequest = { expandedBoltGrade = false }
                        ) {
                            BoltGrade.entries.forEach { grade ->
                                DropdownMenuItem(
                                    text = { AppText(grade.displayName) },
                                    onClick = { selectedBoltGrade = grade; expandedBoltGrade = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            AppButton(
                label = stringResource(R.string.steel_calculate_capacity),
                onClick = {
                    capacity = viewModel.calculateBoltCapacity(
                        boltDia.toDoubleOrNull() ?: 20.0,
                        selectedBoltGrade,
                        boltCount.toIntOrNull() ?: 4,
                        selectedCode
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (capacity > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        AppText(stringResource(R.string.steel_bolt_capacity), fontWeight = FontWeight.Bold)
                        AppText("${String.format("%.1f", capacity)} kN", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                AppButton(
                    label = stringResource(R.string.pdf_report),
                    onClick = { viewModel.exportBoltToPdf(context, boltDia.toDoubleOrNull() ?: 20.0, selectedBoltGrade, boltCount.toIntOrNull() ?: 4, selectedCode, capacity) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.PictureAsPdf,
                    containerOverride = MaterialTheme.colorScheme.secondary,
                    contentOverride = onColorFor(MaterialTheme.colorScheme.secondary)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectionDesignTab(viewModel: SteelViewModel) {
    val context = LocalContext.current
    var selectedCode by remember { mutableStateOf(defaultDesignCode()) }
    var connectionType by remember { mutableIntStateOf(0) }
    val connTypes = listOf(stringResource(R.string.steel_conn_bolted_shear), stringResource(R.string.steel_conn_bolted_moment), stringResource(R.string.steel_conn_welded), stringResource(R.string.steel_conn_combined), stringResource(R.string.steel_conn_slip_critical))

    var boltDiameter by remember { mutableStateOf("20") }
    var numBolts by remember { mutableStateOf("4") }
    var boltSpacing by remember { mutableStateOf("75") }
    var edgeDistance by remember { mutableStateOf("40") }
    var appliedShear by remember { mutableStateOf("100") }
    var appliedTension by remember { mutableStateOf("0") }
    var expandedBoltGrade by remember { mutableStateOf(false) }
    var selectedBoltGrade by remember { mutableStateOf(BoltGrade.GRADE_8_8) }
    var expandedPattern by remember { mutableStateOf(false) }
    var selectedPattern by remember { mutableStateOf(BoltPattern.DOUBLE_ROW) }

    var weldSize by remember { mutableStateOf("6") }
    var weldLength by remember { mutableStateOf("200") }
    var expandedElectrode by remember { mutableStateOf(false) }
    var selectedElectrode by remember { mutableStateOf(ElectrodeType.E70XX) }
    var appliedWeldForce by remember { mutableStateOf("100") }

    var plateThickness by remember { mutableStateOf("10") }
    var fuPlate by remember { mutableStateOf("400") }
    var frictionClass by remember { mutableStateOf("CLASS_A") }
    var frictionClassExpanded by remember { mutableStateOf(false) }
    var isDoubleShear by remember { mutableStateOf(false) }

    var boltResult by remember { mutableStateOf<BoltDesignResult?>(null) }
    var weldResult by remember { mutableStateOf<WeldDesignResult?>(null) }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SteelModuleSectionHeader(stringResource(R.string.steel_connection_design), R.drawable.ic_frame) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(R.string.steel_design_code_metal), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DesignCode.entries.forEach { code ->
                            FilterChip(
                                selected = selectedCode == code,
                                onClick = { selectedCode = code },
                                label = { AppText(code.name, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AppText(stringResource(R.string.steel_conn_type), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow {
                        connTypes.forEachIndexed { idx, name ->
                            FilterChip(
                                selected = connectionType == idx,
                                onClick = { connectionType = idx; boltResult = null; weldResult = null },
                                label = { AppText(name, fontSize = 11.sp) },
                                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        if (connectionType < 3) {
            item {
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(stringResource(R.string.steel_bolt_data), fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SteelInputField(boltDiameter, stringResource(R.string.steel_bolt_dia_hint), { boltDiameter = it }, Modifier.weight(1f))
                            SteelInputField(numBolts, stringResource(R.string.steel_bolt_count_hint), { numBolts = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SteelInputField(boltSpacing, stringResource(R.string.steel_bolt_spacing_hint), { boltSpacing = it }, Modifier.weight(1f))
                            SteelInputField(edgeDistance, stringResource(R.string.steel_edge_dist_hint), { edgeDistance = it }, Modifier.weight(1f))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SteelInputField(plateThickness, stringResource(R.string.steel_plate_thickness_hint), { plateThickness = it }, Modifier.weight(1f))
                            SteelInputField(fuPlate, stringResource(R.string.steel_fu_plate_hint), { fuPlate = it }, Modifier.weight(1f))
                        }

                        // Slip-critical options (shown for slip-critical and combined types)
                        if (connectionType == 4 || connectionType == 1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppText(stringResource(R.string.steel_friction_class), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                                ExposedDropdownMenuBox(
                                    expanded = frictionClassExpanded,
                                    onExpandedChange = { frictionClassExpanded = it },
                                    modifier = Modifier.weight(0.4f)
                                ) {
                                    OutlinedTextField(
                                        value = frictionClass,
                                        onValueChange = { frictionClass = it },
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frictionClassExpanded) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                        shape = RoundedCornerShape(12.dp),
                                        textStyle = MaterialTheme.typography.bodySmall
                                    )
                                    ExposedDropdownMenu(expanded = frictionClassExpanded, onDismissRequest = { frictionClassExpanded = false }) {
                                        listOf("CLASS_A", "CLASS_B", "CLASS_C").forEach { cls ->
                                            DropdownMenuItem(
                                                text = { AppText(when (cls) {
                                                    "CLASS_A" -> "الفئة أ (سطح نظيف)"
                                                    "CLASS_B" -> "الفئة ب (رملي / منزوع الصدأ)"
                                                    "CLASS_C" -> "الفئة ج (مجلفن)"
                                                    else -> cls
                                                }) },
                                                onClick = { frictionClass = cls; frictionClassExpanded = false }
                                            )
                                        }
                                    }
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppText(stringResource(R.string.steel_double_shear), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                                Switch(
                                    checked = isDoubleShear,
                                    onCheckedChange = { isDoubleShear = it },
                                    modifier = Modifier.weight(0.4f)
                                )
                            }
                        }

                        AppText(stringResource(R.string.steel_bolt_grade_label), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ExposedDropdownMenuBox(
                            expanded = expandedBoltGrade,
                            onExpandedChange = { expandedBoltGrade = !expandedBoltGrade }
                        ) {
                            OutlinedTextField(
                                value = selectedBoltGrade.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBoltGrade) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            ExposedDropdownMenu(
                                expanded = expandedBoltGrade,
                                onDismissRequest = { expandedBoltGrade = false }
                            ) {
                                for (grade in BoltGrade.entries) {
                                    DropdownMenuItem(
                                        text = { AppText(grade.displayName) },
                                        onClick = { selectedBoltGrade = grade; expandedBoltGrade = false }
                                    )
                                }
                            }
                        }

                        AppText(stringResource(R.string.steel_dist_pattern), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ExposedDropdownMenuBox(
                            expanded = expandedPattern,
                            onExpandedChange = { expandedPattern = !expandedPattern }
                        ) {
                            OutlinedTextField(
                                value = selectedPattern.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPattern) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            ExposedDropdownMenu(
                                expanded = expandedPattern,
                                onDismissRequest = { expandedPattern = false }
                            ) {
                                for (pattern in BoltPattern.entries) {
                                    DropdownMenuItem(
                                        text = { AppText(pattern.displayName) },
                                        onClick = { selectedPattern = pattern; expandedPattern = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(stringResource(R.string.steel_applied_loads), fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SteelInputField(appliedShear, stringResource(R.string.steel_shear_vu), { appliedShear = it }, Modifier.weight(1f))
                            if (connectionType != 0) {
                                SteelInputField(appliedTension, stringResource(R.string.steel_tension_tu), { appliedTension = it }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        if (connectionType >= 2) {
            item {
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(stringResource(R.string.steel_weld_data), fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SteelInputField(weldSize, stringResource(R.string.steel_weld_size_hint), { weldSize = it }, Modifier.weight(1f))
                            SteelInputField(weldLength, stringResource(R.string.steel_weld_length_hint), { weldLength = it }, Modifier.weight(1f))
                        }
                        SteelInputField(appliedWeldForce, stringResource(R.string.steel_applied_force_hint), { appliedWeldForce = it }, Modifier.fillMaxWidth())

                        AppText(stringResource(R.string.steel_electrode_type), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ExposedDropdownMenuBox(
                            expanded = expandedElectrode,
                            onExpandedChange = { expandedElectrode = !expandedElectrode }
                        ) {
                            OutlinedTextField(
                                value = selectedElectrode.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedElectrode) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            ExposedDropdownMenu(
                                expanded = expandedElectrode,
                                onDismissRequest = { expandedElectrode = false }
                            ) {
                                ElectrodeType.entries.forEach { electrode ->
                                    DropdownMenuItem(
                                        text = { AppText(electrode.displayName) },
                                        onClick = { selectedElectrode = electrode; expandedElectrode = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            AppButton(
                label = stringResource(R.string.steel_design_connection),
                onClick = {
                    val designer = SteelConnectionDesign()
                    if (connectionType < 3 || connectionType == 4) {
                        val connType = when (connectionType) {
                            0 -> BoltConnectionType.BEARING
                            1 -> BoltConnectionType.COMBINED
                            4 -> BoltConnectionType.SLIP_CRITICAL
                            else -> BoltConnectionType.BEARING
                        }
                        boltResult = designer.designBoltedConnection(
                            boltDiameter = boltDiameter.toDoubleOrNull() ?: 20.0,
                            boltGrade = selectedBoltGrade,
                            numberOfBolts = numBolts.toIntOrNull() ?: 4,
                            boltPattern = selectedPattern,
                            boltConnectionType = connType,
                            appliedShear = appliedShear.toDoubleOrNull() ?: 0.0,
                            appliedTension = appliedTension.toDoubleOrNull() ?: 0.0,
                            plateThickness = plateThickness.toDoubleOrNull() ?: 10.0,
                            fuPlate = fuPlate.toDoubleOrNull() ?: 400.0,
                            isDoubleShear = isDoubleShear,
                            edgeDistance = edgeDistance.toDoubleOrNull(),
                            spacing = boltSpacing.toDoubleOrNull(),
                            frictionClass = frictionClass,
                            checkBlockShear = true
                        )
                    }
                    if (connectionType >= 2) {
                        weldResult = designer.designWeldedConnection(
                            weldType = WeldType.FILLET,
                            weldSize = weldSize.toDoubleOrNull() ?: 6.0,
                            weldLength = weldLength.toDoubleOrNull() ?: 200.0,
                            electrodeType = selectedElectrode,
                            appliedForce = appliedWeldForce.toDoubleOrNull() ?: 100.0,
                            plateThickness = plateThickness.toDoubleOrNull() ?: 10.0
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Engineering
            )
        }

        boltResult?.let { res ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(stringResource(R.string.steel_bolted_results), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                        ResultRow(stringResource(R.string.steel_total_capacity), "${String.format("%.1f", res.controllingCapacity)} kN")
                        ResultRow(stringResource(R.string.steel_utilization_ratio), "${(res.utilizationRatio * 100).toInt()}%")
                        AppText(if (res.isSafe) stringResource(R.string.steel_status_safe) else stringResource(R.string.steel_status_unsafe), fontWeight = FontWeight.Bold, color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red)

                        // Detailed individual checks
                        Spacer(modifier = Modifier.height(8.dp))
                        AppText(stringResource(R.string.steel_detailed_checks), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        ResultRow(stringResource(R.string.steel_shear_capacity_label), "${String.format("%.1f", res.shearCapacity)} kN / مسمار")
                        ResultRow(stringResource(R.string.steel_bearing_capacity), "${String.format("%.1f", res.bearingCapacity)} kN / مسمار")
                        ResultRow(stringResource(R.string.steel_tension_capacity), "${String.format("%.1f", res.tensionCapacity)} kN / مسمار")
                        ResultRow(stringResource(R.string.steel_controlling_capacity), "${String.format("%.1f", res.controllingCapacity)} kN / مسمار")
                        ResultRow(stringResource(R.string.steel_edge_distance), "${res.edgeDistance.toInt()} مم")
                        ResultRow(stringResource(R.string.steel_bolt_spacing_label), "${res.boltSpacing.toInt()} مم")

                        // Block shear result
                        res.blockShearCheck?.let { bs ->
                            ResultRow(stringResource(R.string.steel_block_shear_capacity), "${String.format("%.1f", bs.capacity)} kN")
                            ResultRow(stringResource(R.string.steel_block_shear_utilization), "${(bs.utilizationRatio * 100).toInt()}%")
                        }

                        // Warnings
                        if (res.warnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AppText(stringResource(R.string.steel_warnings), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFF57C00))
                            res.warnings.forEach { warning ->
                                AppText(warning, fontSize = 11.sp, color = Color(0xFFF57C00), modifier = Modifier.padding(top = 2.dp))
                            }
                        }

                        // Code notes
                        if (res.codeNotes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AppText(stringResource(R.string.steel_code_notes), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            res.codeNotes.forEach { note ->
                                AppText(note, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                            }
                        }

                        // Calculation formulas
                        Spacer(modifier = Modifier.height(8.dp))
                        AppText(stringResource(R.string.steel_calculation_formulas), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        val ab = Math.PI * Math.pow(res.boltDiameter.toDouble(), 2.0) / 4.0
                        FormulaText("φRn_shear = 0.75 × Fn × Ab = 0.75 × Fn × π×d²/4")
                        FormulaText("φRn_bearing = 0.75 × min(1.2×Lc×t×Fu, 2.4×d×t×Fu)")
                        FormulaText("φRn_tension = 0.75 × Ft × Ab")
                        FormulaText("Interaction: (Vu/φVn)² + (Tu/φTn)² ≤ 1.0")
                        FormulaText("Nominal area Ab = π×d²/4 = ${String.format("%.1f", ab)} mm²")

                        AppButton(
                            label = stringResource(R.string.pdf_report),
                            onClick = {
                                viewModel.exportBoltToPdf(
                                    context = context,
                                    dia = res.boltDiameter,
                                    grade = res.boltGrade,
                                    count = res.numberOfBolts,
                                    code = selectedCode,
                                    capacity = res.controllingCapacity
                                )
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            icon = Icons.Default.PictureAsPdf,
                            containerOverride = MaterialTheme.colorScheme.tertiary,
                            contentOverride = onColorFor(MaterialTheme.colorScheme.tertiary)
                        )
                    }
                }
            }
        }

        weldResult?.let { res ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(stringResource(R.string.steel_welded_results), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        ResultRow(stringResource(R.string.steel_total_capacity), "${String.format("%.1f", res.capacity)} kN")
                        ResultRow(stringResource(R.string.steel_utilization_ratio), "${(res.utilizationRatio * 100).toInt()}%")
                        AppText(if (res.isSafe) stringResource(R.string.steel_status_safe) else stringResource(R.string.steel_status_unsafe), fontWeight = FontWeight.Bold, color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red)

                        // Detailed weld checks
                        Spacer(modifier = Modifier.height(8.dp))
                        AppText(stringResource(R.string.steel_detailed_checks), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        ResultRow(stringResource(R.string.steel_weld_capacity), "${String.format("%.1f", res.capacity)} kN")
                        ResultRow(stringResource(R.string.steel_weld_throat_area), "${String.format("%.1f", res.throatArea)} مم²")
                        ResultRow(stringResource(R.string.steel_weld_electrode), res.electrodeType.displayName)

                        // Warnings
                        if (res.warnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AppText(stringResource(R.string.steel_warnings), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFF57C00))
                            res.warnings.forEach { warning ->
                                AppText(warning, fontSize = 11.sp, color = Color(0xFFF57C00), modifier = Modifier.padding(top = 2.dp))
                            }
                        }

                        // Code notes
                        if (res.codeNotes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AppText(stringResource(R.string.steel_code_notes), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            res.codeNotes.forEach { note ->
                                AppText(note, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                            }
                        }

                        // Calculation formulas
                        Spacer(modifier = Modifier.height(8.dp))
                        AppText(stringResource(R.string.steel_calculation_formulas), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        val throat = 0.707 * res.weldSize
                        FormulaText("φRn = φ × Fw × Aw = 0.75 × Fw × throat × length")
                        FormulaText("Fw = 0.60 × FEXX (electrode tensile strength)")
                        FormulaText("Throat = 0.707 × weld size = ${String.format("%.2f", throat)} mm")
                        FormulaText("Effective throat area Aw = throat × weld length")

                        AppButton(
                            label = stringResource(R.string.pdf_report),
                            onClick = {
                                viewModel.exportWeldToPdf(
                                    context = context,
                                    size = res.weldSize,
                                    length = res.weldLength,
                                    electrode = res.electrodeType,
                                    code = selectedCode,
                                    capacity = res.capacity
                                )
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            icon = Icons.Default.PictureAsPdf
                        )
                    }
                }
            }
        }
        
        item {
            if (boltResult != null || weldResult != null) {
                AppText(stringResource(R.string.steel_connection_visualization), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                SteelConnectionVisualizer(boltResult, weldResult)
            }
        }
    }
}

@Composable
fun SteelConnectionVisualizer(boltRes: BoltDesignResult?, weldRes: WeldDesignResult?) {
    Card(
        modifier = Modifier.fillMaxWidth().height(250.dp).padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        ComposeCanvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2
            val cy = h / 2
            
            drawRect(Color.Gray, Offset(cx - 100f, cy - 80f), Size(200f, 160f))
            drawRect(Color.DarkGray, Offset(cx - 120f, cy - 100f), Size(20f, 200f))
            
            boltRes?.let { res ->
                val r = (res.boltDiameter.toFloat() / 2f).coerceIn(5f, 12f)
                val num = res.numberOfBolts
                val spacing = 120f / max(num / 2, 1)
                for (i in 0 until (num / 2).coerceAtLeast(1)) {
                    val y = cy - 60f + i * spacing
                    drawCircle(Color(0xFFF39C12), r, Offset(cx - 50f, y))
                    drawCircle(Color(0xFFF39C12), r, Offset(cx + 50f, y))
                }
            }
            
            weldRes?.let { res ->
                val s = res.weldSize.toFloat()
                drawLine(Color(0xFFE74C3C), Offset(cx - 100f, cy - 80f), Offset(cx - 100f, cy + 80f), s)
                drawLine(Color(0xFFE74C3C), Offset(cx + 100f, cy - 80f), Offset(cx + 100f, cy + 80f), s)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BasePlateDesignTab(viewModel: SteelViewModel) {
    val context = LocalContext.current
    var colSection by remember { mutableStateOf("HEB 300") }
    var bf by remember { mutableStateOf("300") }
    var dc by remember { mutableStateOf("300") }
    var axialLoad by remember { mutableStateOf("500") }
    var momentM by remember { mutableStateOf("0") }
    var fpc by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("250") }
    var basePlateType by remember { mutableIntStateOf(0) }
    val basePlateTypes = listOf(stringResource(R.string.steel_baseplate_concentric), stringResource(R.string.steel_baseplate_eccentric), stringResource(R.string.steel_baseplate_pocket))
    var expandedBoltGrade by remember { mutableStateOf(false) }
    var selectedBoltGrade by remember { mutableStateOf("4.6") }
    var selectedCode by remember { mutableStateOf(defaultDesignCode()) }
    var bpResult by remember { mutableStateOf<Any?>(null) }

    val boltGradeOptions = SteelBasePlateDesign.Companion.BoltGrade.entries.map { it.getGradeName() }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SteelModuleSectionHeader(stringResource(R.string.steel_baseplate_header), R.drawable.ic_footing) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(R.string.steel_baseplate_type_label), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow {
                        basePlateTypes.forEachIndexed { idx, name ->
                            FilterChip(
                                selected = basePlateType == idx,
                                onClick = { basePlateType = idx; bpResult = null },
                                label = { AppText(name, fontSize = 11.sp) },
                                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            // Design code selector (concrete/base-plate provisions)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesignCode.entries.forEach { code ->
                    FilterChip(
                        selected = selectedCode == code,
                        onClick = { selectedCode = code },
                        label = { AppText(code.name, fontSize = 11.sp) }
                    )
                }
            }
        }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(R.string.steel_column_data), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(colSection, stringResource(R.string.steel_section_hint), { colSection = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(bf, stringResource(R.string.steel_flange_width_hint), { bf = it }, Modifier.weight(1f))
                        SteelInputField(dc, stringResource(R.string.steel_column_depth_hint), { dc = it }, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(R.string.steel_applied_loads), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(axialLoad, stringResource(R.string.steel_axial_force_hint), { axialLoad = it }, Modifier.weight(1f))
                        SteelInputField(momentM, stringResource(R.string.steel_moment_hint), { momentM = it }, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(R.string.material_properties), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(fpc, stringResource(R.string.steel_fcu_mpa), { fpc = it }, Modifier.weight(1f))
                        SteelInputField(fy, stringResource(R.string.steel_fy_mpa), { fy = it }, Modifier.weight(1f))
                    }

                    AppText(stringResource(R.string.steel_anchor_bolt_grade), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    ExposedDropdownMenuBox(
                        expanded = expandedBoltGrade,
                        onExpandedChange = { expandedBoltGrade = !expandedBoltGrade }
                    ) {
                        OutlinedTextField(
                            value = "Grade $selectedBoltGrade",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBoltGrade) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = expandedBoltGrade,
                            onDismissRequest = { expandedBoltGrade = false }
                        ) {
                            for (grade in boltGradeOptions) {
                                DropdownMenuItem(
                                    text = { AppText(stringResource(R.string.steel_grade_format, grade)) },
                                    onClick = { selectedBoltGrade = grade; expandedBoltGrade = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            AppButton(
                label = stringResource(R.string.steel_design_baseplate),
                onClick = {
                val designer = SteelBasePlateDesign()
                val boltGrade = SteelBasePlateDesign.Companion.BoltGrade.entries.find { it.getGradeName() == selectedBoltGrade }
                    ?: SteelBasePlateDesign.Companion.BoltGrade.GRADE_4_6
                when (basePlateType) {
                    0 -> {
                        // Concentric
                        val input = SteelBasePlateDesign.ConcentricInput(
                            Pu = axialLoad.toDoubleOrNull() ?: 500.0,
                            Mux = momentM.toDoubleOrNull() ?: 0.0,
                            Muy = 0.0,
                            Vu = 0.0,
                            bf = bf.toDoubleOrNull() ?: 300.0,
                            dc = dc.toDoubleOrNull() ?: 300.0,
                            Fy = fy.toDoubleOrNull() ?: 250.0,
                            fpc = fpc.toDoubleOrNull() ?: 25.0,
                            boltGrade = boltGrade
                        )
                        bpResult = designer.designConcentricBasePlate(input)
                    }
                    1 -> {
                        // Eccentric
                        val input = SteelBasePlateDesign.EccentricInput(
                            Pu = axialLoad.toDoubleOrNull() ?: 500.0,
                            Mux = momentM.toDoubleOrNull() ?: 0.0,
                            Muy = 0.0,
                            Vu = 0.0,
                            bf = bf.toDoubleOrNull() ?: 300.0,
                            dc = dc.toDoubleOrNull() ?: 300.0,
                            Fy = fy.toDoubleOrNull() ?: 250.0,
                            fpc = fpc.toDoubleOrNull() ?: 25.0,
                            boltGrade = boltGrade
                        )
                        bpResult = designer.designEccentricBasePlate(input)
                    }
                    2 -> {
                        // Pocket
                        val input = SteelBasePlateDesign.PocketInput(
                            Pu = axialLoad.toDoubleOrNull() ?: 500.0,
                            Mux = momentM.toDoubleOrNull() ?: 0.0,
                            Muy = 0.0,
                            Vu = 0.0,
                            bf = bf.toDoubleOrNull() ?: 300.0,
                            dc = dc.toDoubleOrNull() ?: 300.0,
                            Fy = fy.toDoubleOrNull() ?: 250.0,
                            fpc = fpc.toDoubleOrNull() ?: 25.0,
                            boltGrade = boltGrade
                        )
                        bpResult = designer.designPocketBasePlate(input)
                    }
                }
            },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Build
            )
        }

        bpResult?.let { res ->
            item {
                when (basePlateType) {
                    0 -> {
                        // Concentric result - BasePlateResult
                        if (res is SteelBasePlateDesign.BasePlateResult) {
                            BasePlateConcentricResultCard(context, viewModel, res, colSection, bf, dc, selectedCode)
                        }
                    }
                    1 -> {
                        // Eccentric result - EccentricBasePlateResult
                        if (res is SteelBasePlateDesign.EccentricBasePlateResult) {
                            BasePlateEccentricResultCard(context, viewModel, res, colSection, bf, dc, selectedCode)
                        }
                    }
                    2 -> {
                        // Pocket result - PocketBasePlateResult
                        if (res is SteelBasePlateDesign.PocketBasePlateResult) {
                            BasePlatePocketResultCard(context, viewModel, res, colSection, bf, dc, selectedCode)
                        }
                    }
                }
            }
        }
    }
}

// ===================== Concentric Base Plate Result Card =====================

@Composable
fun BasePlateConcentricResultCard(
    context: Context,
    viewModel: SteelViewModel,
    res: SteelBasePlateDesign.BasePlateResult,
    colSection: String,
    bf: String,
    dc: String,
    selectedCode: DesignCode
) {
    val urColor = if (res.utilizationRatio <= 1.0) Color(0xFF2E7D32) else Color.Red
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(stringResource(R.string.steel_baseplate_results), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            ResultRow(stringResource(R.string.steel_baseplate_dims), "${res.plateLength.toInt()} x ${res.plateWidth.toInt()} mm")
            ResultRow(stringResource(R.string.steel_thickness_mm), "${res.plateThickness.toInt()} mm")
            ResultRow(stringResource(R.string.steel_max_bearing_pressure), "${String.format("%.2f", res.maxBearingPressure)} MPa")
            ResultRow(stringResource(R.string.steel_concrete_capacity), "${String.format("%.2f", res.concreteCapacity)} MPa")
            ResultRow(stringResource(R.string.steel_bolt_count), "${res.anchorBolts.numberOfBolts}")
            ResultRow(stringResource(R.string.steel_bolt_dia), "M${res.anchorBolts.boltDiameter.toInt()}")
            ResultRow(stringResource(R.string.steel_bolt_spacing_label), "${res.anchorBolts.boltSpacing.toInt()} mm")
            ResultRow(stringResource(R.string.steel_bolt_edge_distance), "${res.anchorBolts.edgeDistance.toInt()} mm")
            ResultRow(stringResource(R.string.steel_embedment_length), "${res.anchorBolts.embedmentLength.toInt()} mm")
            ResultRow(stringResource(R.string.steel_utilization_ratio), "${(res.utilizationRatio * 100).toInt()}%")
            AppText(if (res.isSafe) stringResource(R.string.steel_status_safe) else stringResource(R.string.steel_status_unsafe), color = urColor, fontWeight = FontWeight.Bold)

            if (res.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AppText(stringResource(R.string.steel_warnings), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFF57C00))
                res.warnings.forEach { warning ->
                    AppText(warning, fontSize = 11.sp, color = Color(0xFFF57C00), modifier = Modifier.padding(top = 2.dp))
                }
            }

            // Calculation formulas for concentric base plate
            Spacer(modifier = Modifier.height(8.dp))
            AppText(stringResource(R.string.steel_calculation_formulas), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            FormulaText("A_req = Pu / (φ × 0.85 × f'c × √(A2/A1))")
            FormulaText("tp = √(2 × Pu × λ² / (φ × Fy × B × L))")
            FormulaText("f_pu = P/(B×L) × (1 + 6e/B)")

            Spacer(modifier = Modifier.height(8.dp))
            AppButton(
                label = stringResource(R.string.pdf_report),
                onClick = {
                    viewModel.exportBasePlateToPdf(
                        context = context,
                        result = res,
                        colSection = colSection,
                        bf = bf.toDoubleOrNull() ?: 300.0,
                        dc = dc.toDoubleOrNull() ?: 300.0
                    ) { }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.PictureAsPdf,
                containerOverride = MaterialTheme.colorScheme.secondary,
                contentOverride = onColorFor(MaterialTheme.colorScheme.secondary)
            )
        }
    }
}

// ===================== Eccentric Base Plate Result Card =====================

@Composable
fun BasePlateEccentricResultCard(
    context: Context,
    viewModel: SteelViewModel,
    res: SteelBasePlateDesign.EccentricBasePlateResult,
    colSection: String,
    bf: String,
    dc: String,
    selectedCode: DesignCode
) {
    val urColor = if (res.utilizationRatio <= 1.0) Color(0xFF2E7D32) else Color.Red
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(stringResource(R.string.steel_baseplate_results), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            ResultRow(stringResource(R.string.steel_baseplate_dims), "${res.plateLength.toInt()} x ${res.plateWidth.toInt()} mm")
            ResultRow(stringResource(R.string.steel_thickness_mm), "${res.plateThickness.toInt()} mm")
            ResultRow(stringResource(R.string.steel_max_bearing_pressure), "${String.format("%.2f", res.maxBearingPressure)} MPa")
            ResultRow(stringResource(R.string.steel_min_bearing_pressure), "${String.format("%.2f", res.minBearingPressure)} MPa")
            ResultRow(stringResource(R.string.steel_effective_bearing_length), "${res.effectiveBearingLength.toInt()} mm")
            ResultRow(stringResource(R.string.steel_bolt_count), "${res.anchorBolts.numberOfBolts}")
            ResultRow(stringResource(R.string.steel_bolt_dia), "M${res.anchorBolts.boltDiameter.toInt()}")
            ResultRow(stringResource(R.string.steel_embedment_length), "${res.anchorBolts.embedmentLength.toInt()} mm")
            ResultRow(stringResource(R.string.steel_utilization_ratio), "${(res.utilizationRatio * 100).toInt()}%")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(stringResource(R.string.steel_tension_side), style = MaterialTheme.typography.bodyMedium)
                AppText(
                    if (res.tensionSide) stringResource(R.string.steel_yes) else stringResource(R.string.steel_no),
                    fontWeight = FontWeight.Bold,
                    color = if (res.tensionSide) Color(0xFFF57C00) else Color(0xFF2E7D32)
                )
            }
            AppText(if (res.isSafe) stringResource(R.string.steel_status_safe) else stringResource(R.string.steel_status_unsafe), color = urColor, fontWeight = FontWeight.Bold)

            if (res.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AppText(stringResource(R.string.steel_warnings), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFF57C00))
                res.warnings.forEach { warning ->
                    AppText(warning, fontSize = 11.sp, color = Color(0xFFF57C00), modifier = Modifier.padding(top = 2.dp))
                }
            }

            // Calculation formulas for eccentric base plate
            Spacer(modifier = Modifier.height(8.dp))
            AppText(stringResource(R.string.steel_calculation_formulas), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            FormulaText("f_max = P/(B×L) × (1 + 6e/L)")
            FormulaText("f_min = P/(B×L) × (1 - 6e/L)")
            FormulaText("tp = λ × √(2 × Pu × momentFactor / (0.9 × Fy × B × L))")

            Spacer(modifier = Modifier.height(8.dp))
            AppButton(
                label = stringResource(R.string.pdf_report),
                onClick = {
                    // For eccentric, we need to cast or create a compatible result for PDF
                    viewModel.exportBasePlateToPdf(
                        context = context,
                        result = res as SteelBasePlateDesign.BasePlateResult,
                        colSection = colSection,
                        bf = bf.toDoubleOrNull() ?: 300.0,
                        dc = dc.toDoubleOrNull() ?: 300.0
                    ) { }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.PictureAsPdf,
                containerOverride = MaterialTheme.colorScheme.secondary,
                contentOverride = onColorFor(MaterialTheme.colorScheme.secondary),
                enabled = false
            )
        }
    }
}

// ===================== Pocket Base Plate Result Card =====================

@Composable
fun BasePlatePocketResultCard(
    context: Context,
    viewModel: SteelViewModel,
    res: SteelBasePlateDesign.PocketBasePlateResult,
    colSection: String,
    bf: String,
    dc: String,
    selectedCode: DesignCode
) {
    val urColor = if (res.utilizationRatio <= 1.0) Color(0xFF2E7D32) else Color.Red
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(stringResource(R.string.steel_baseplate_results), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            ResultRow(stringResource(R.string.steel_baseplate_dims), "${res.plateLength.toInt()} x ${res.plateWidth.toInt()} mm")
            ResultRow(stringResource(R.string.steel_thickness_mm), "${res.plateThickness.toInt()} mm")
            ResultRow(stringResource(R.string.steel_pocket_depth), "${res.pocket.pocketDepth.toInt()} mm")
            ResultRow(stringResource(R.string.steel_pocket_width), "${res.pocket.pocketWidth.toInt()} mm")
            ResultRow(stringResource(R.string.steel_pocket_length), "${res.pocket.pocketLength.toInt()} mm")
            ResultRow(stringResource(R.string.steel_concrete_cover), "${res.pocket.concreteCover.toInt()} mm")
            ResultRow(stringResource(R.string.steel_bolt_count), "${res.anchorBolts.numberOfBolts}")
            ResultRow(stringResource(R.string.steel_bolt_dia), "M${res.anchorBolts.boltDiameter.toInt()}")
            ResultRow(stringResource(R.string.steel_utilization_ratio), "${(res.utilizationRatio * 100).toInt()}%")
            AppText(if (res.isSafe) stringResource(R.string.steel_status_safe) else stringResource(R.string.steel_status_unsafe), color = urColor, fontWeight = FontWeight.Bold)

            if (res.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AppText(stringResource(R.string.steel_warnings), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFF57C00))
                res.warnings.forEach { warning ->
                    AppText(warning, fontSize = 11.sp, color = Color(0xFFF57C00), modifier = Modifier.padding(top = 2.dp))
                }
            }

            // Calculation formulas for pocket base plate
            Spacer(modifier = Modifier.height(8.dp))
            AppText(stringResource(R.string.steel_calculation_formulas), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            FormulaText("tp = λ × √(2 × Pu / (0.9 × Fy × B × L))")
            FormulaText("Pocket depth = 1.5 × bolt diameter + 50 mm")

            Spacer(modifier = Modifier.height(8.dp))
            AppButton(
                label = stringResource(R.string.pdf_report),
                onClick = {
                    viewModel.exportBasePlateToPdf(
                        context = context,
                        result = res as SteelBasePlateDesign.BasePlateResult,
                        colSection = colSection,
                        bf = bf.toDoubleOrNull() ?: 300.0,
                        dc = dc.toDoubleOrNull() ?: 300.0
                    ) { }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.PictureAsPdf,
                containerOverride = MaterialTheme.colorScheme.secondary,
                contentOverride = onColorFor(MaterialTheme.colorScheme.secondary),
                enabled = false
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
