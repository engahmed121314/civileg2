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

fun SteelWarehouseTab(viewModel: SteelViewModel, result: SteelWarehouseAnalysisResult?, isLoading: Boolean) {
    val context = LocalContext.current
    val pdfCaptureLayer = ComposeDrawingCaptureUtil.rememberDrawingCaptureLayer()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * density.density).toInt().coerceAtLeast(800)
    val screenHeightPx = (config.screenHeightDp * density.density).toInt().coerceAtLeast(600)
    var span by rememberSaveable { mutableStateOf("20.0") }
    var spacing by rememberSaveable { mutableStateOf("6.0") }
    var eaveHeight by rememberSaveable { mutableStateOf("6.0") }
    var totalLength by rememberSaveable { mutableStateOf("60.0") }
    var roofSlope by rememberSaveable { mutableStateOf("10") }
    var deadLoad by rememberSaveable { mutableStateOf("0.25") }
    var liveLoad by rememberSaveable { mutableStateOf("0.60") }
    var windSpeed by rememberSaveable { mutableStateOf("150") }
    var numberOfStories by rememberSaveable { mutableIntStateOf(1) }
    var selectedCode by rememberSaveable { mutableStateOf(defaultDesignCode()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SteelModuleSectionHeader(stringResource(R.string.steel_warehouse_inputs), R.drawable.ic_frame) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesignCodeSelectorRow(
                        selectedCode = selectedCode,
                        onCodeSelected = { selectedCode = it }
                    )
                }
            }
        }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(span, stringResource(R.string.steel_span_m), { span = it }, Modifier.weight(1f))
                        SteelInputField(spacing, stringResource(R.string.steel_spacing_m), { spacing = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(eaveHeight, stringResource(R.string.steel_height_m), { eaveHeight = it }, Modifier.weight(1f))
                        SteelInputField(totalLength, stringResource(R.string.steel_length_m), { totalLength = it }, Modifier.weight(1f))
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    AppText(stringResource(R.string.steel_hangar_floors_config), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = numberOfStories == 1,
                            onClick = { numberOfStories = 1 },
                            label = { AppText(stringResource(R.string.steel_1_floor_standard_hangar)) }
                        )
                        FilterChip(
                            selected = numberOfStories == 2,
                            onClick = { numberOfStories = 2 },
                            label = { AppText(stringResource(R.string.steel_2_floors_mezzanine)) }
                        )
                    }
                }
            }
        }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(R.string.steel_loads_header), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(deadLoad, stringResource(R.string.steel_dl_knm2), { deadLoad = it }, Modifier.weight(1f))
                        SteelInputField(liveLoad, stringResource(R.string.steel_ll_knm2), { liveLoad = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(windSpeed, stringResource(R.string.steel_wind_kmh), { windSpeed = it }, Modifier.weight(1f))
                        SteelInputField(roofSlope, stringResource(R.string.steel_roof_slope_deg), { roofSlope = it }, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            val warehouseInputsValid = listOf(span, spacing, eaveHeight, totalLength, roofSlope, deadLoad, liveLoad, windSpeed)
                .all { it.trim().toDoubleOrNull() != null } &&
                listOf(span, spacing, eaveHeight, totalLength).all { (it.trim().toDoubleOrNull() ?: 0.0) > 0.0 }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!warehouseInputsValid) {
                    AppText(
                        text = stringResource(R.string.input_invalid_numbers),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                AppButton(
                    label = stringResource(R.string.steel_design_warehouse),
                    onClick = {
                        val s = span.toDoubleOrNull() ?: return@AppButton
                        val l = totalLength.toDoubleOrNull() ?: return@AppButton
                        val h = eaveHeight.toDoubleOrNull() ?: return@AppButton
                        val sl = roofSlope.toDoubleOrNull() ?: return@AppButton
                        val ridgeH = h + (s / 2.0 * tan(Math.toRadians(sl)))

                        val inputs = SteelWarehouseInputs(
                            span = s,
                            length = l,
                            eaveHeight = h,
                            ridgeHeight = ridgeH,
                            baySpacing = spacing.toDoubleOrNull() ?: return@AppButton,
                            slope = tan(Math.toRadians(sl)),
                            code = selectedCode,
                            deadLoad = deadLoad.toDoubleOrNull() ?: return@AppButton,
                            liveLoad = liveLoad.toDoubleOrNull() ?: return@AppButton,
                            windLoad = windSpeed.toDoubleOrNull() ?: return@AppButton,
                            numberOfStories = numberOfStories
                        )
                        viewModel.calculateWarehouse(inputs)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && warehouseInputsValid,
                    loading = isLoading
                )
            }
        }

        result?.let { res ->
            item { WarehouseResultSummary(res) }
            
            item {
                AppText(stringResource(R.string.steel_cost_structure_feasibility), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                FeasibilityChart(res)
            }

            // Calculation Trace (Detailed for English Report)
            if (res.calculationTrace.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            AppText(stringResource(R.string.steel_engineering_calc_trace), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            res.calculationTrace.forEach { step ->
                                AppText("• $step", fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            item {
                var showExportDialog by remember { mutableStateOf(false) }
                var clientName by remember { mutableStateOf("") }
                var projectName by remember { mutableStateOf("") }

                if (showExportDialog) {
                    AlertDialog(
                        onDismissRequest = { showExportDialog = false },
                        title = { AppText(stringResource(R.string.steel_export_pro_pdf_dialog)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = projectName, onValueChange = { projectName = it }, label = { AppText(stringResource(R.string.steel_project_name_label)) }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = clientName, onValueChange = { clientName = it }, label = { AppText(stringResource(R.string.steel_client_name_label)) }, modifier = Modifier.fillMaxWidth())
                                AppText(stringResource(R.string.steel_report_english_note), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                        },
                        confirmButton = {
                            AppButton(
                                label = stringResource(R.string.steel_export_now),
                                onClick = {
                                    showExportDialog = false
                                    scope.launch {
                                        val captureBitmap = try {
                                            pdfCaptureLayer.captureToAndroidBitmap()
                                        } catch (_: Exception) { null }
                                        viewModel.pendingDrawingBitmap = captureBitmap
                                        viewModel.exportWarehouseProToPdf(context, clientName, projectName) { }
                                    }
                                },
                                fillMaxWidth = false
                            )
                        },
                        dismissButton = {
                            AppButton(
                                label = stringResource(R.string.cancel),
                                onClick = { showExportDialog = false },
                                style = AppButtonStyle.Text,
                                fillMaxWidth = false
                            )
                        }
                    )
                }

                AppButton(
                    label = stringResource(R.string.steel_generate_english_pdf),
                    onClick = { showExportDialog = true },
                    icon = Icons.Default.PictureAsPdf,
                    modifier = Modifier.fillMaxWidth(),
                    containerOverride = MaterialTheme.colorScheme.tertiary,
                    contentOverride = onColorFor(MaterialTheme.colorScheme.tertiary)
                )
            }

            item { StructuralAnalysisVisualizer(lastWarehouseInputs(span, spacing, eaveHeight, totalLength, roofSlope, deadLoad, liveLoad, windSpeed, numberOfStories, selectedCode), res) }
            item { SteelWarehouseVisualizer(lastWarehouseInputs(span, spacing, eaveHeight, totalLength, roofSlope, deadLoad, liveLoad, windSpeed, numberOfStories, selectedCode), res, pdfCaptureLayer, screenWidthPx, screenHeightPx) }
        }
    }
}

fun lastWarehouseInputs(
    span: String, spacing: String, height: String, length: String, slope: String,
    dl: String, ll: String, ws: String, stories: Int = 1,
    code: DesignCode = DesignCode.ECP
): SteelWarehouseInputs {
    val s = span.toDoubleOrNull() ?: 20.0
    val l = length.toDoubleOrNull() ?: 60.0
    val h = height.toDoubleOrNull() ?: 6.0
    val sp = spacing.toDoubleOrNull() ?: 6.0
    val sl = slope.toDoubleOrNull() ?: 10.0
    val ridgeH = h + (s / 2.0 * tan(Math.toRadians(sl)))
    
    return SteelWarehouseInputs(
        span = s,
        length = l,
        eaveHeight = h,
        ridgeHeight = ridgeH,
        baySpacing = sp,
        slope = tan(Math.toRadians(sl)),
        code = code,
        deadLoad = dl.toDoubleOrNull() ?: 0.25,
        liveLoad = ll.toDoubleOrNull() ?: 0.60,
        windLoad = ws.toDoubleOrNull() ?: 150.0,
        numberOfStories = stories
    )
}

@Composable
fun WarehouseResultSummary(res: SteelWarehouseAnalysisResult) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(stringResource(R.string.steel_design_summary), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            ResultRow(stringResource(R.string.steel_total_weight), "${String.format("%.1f", res.totalWeight)} Tons")
            ResultRow(stringResource(R.string.steel_weight_m2), "${String.format("%.1f", res.weightPerM2)} kg/m\u00B2")
            ResultRow(stringResource(R.string.steel_base_plate_thickness), "${res.mainFrame.basePlateThickness.toInt()} mm")
            ResultRow(stringResource(R.string.steel_base_plate_anchor_bolts), "${res.mainFrame.basePlateBoltsCount}xM24 Bolts")
            if (res.mainFrame.floorBeamSection != null) {
                ResultRow(stringResource(R.string.steel_intermediate_floor_beam), res.mainFrame.floorBeamSection.sectionName)
                ResultRow(stringResource(R.string.steel_floor_beam_moment), "${String.format("%.1f", res.mainFrame.floorBeamMaxMoment)} kN.m")
            }
            ResultRow(stringResource(R.string.steel_status), if (res.safetyStatus) stringResource(R.string.steel_status_safe) else stringResource(R.string.steel_status_unsafe))
            ResultRow(stringResource(R.string.steel_max_moment), "${String.format("%.1f", res.mainFrame.maxMoment)} kN.m")
            ResultRow(stringResource(R.string.steel_max_shear), "${String.format("%.1f", res.mainFrame.maxShear)} kN")
            ResultRow(stringResource(R.string.steel_max_axial), "${String.format("%.1f", res.mainFrame.maxAxial)} kN")
            ResultRow(
                stringResource(R.string.steel_deflection),
                "${String.format("%.1f", res.mainFrame.maxDeflection)} / ${String.format("%.0f", res.mainFrame.allowableDeflection)} mm"
            )
            if (res.mainFrame.utilizationMoment > 0.0 || res.mainFrame.utilizationShear > 0.0 || res.mainFrame.utilizationAxial > 0.0) {
                Spacer(Modifier.height(4.dp))
                UtilizationBar(stringResource(R.string.steel_max_moment), res.mainFrame.utilizationMoment)
                UtilizationBar(stringResource(R.string.steel_max_shear), res.mainFrame.utilizationShear)
                UtilizationBar(stringResource(R.string.steel_max_axial), res.mainFrame.utilizationAxial)
            }
        }
    }
}

@Composable
fun FeasibilityChart(result: SteelWarehouseAnalysisResult) {
    val costStructureLabel = stringResource(R.string.steel_cost_structure)
    val steelStructureLabel = stringResource(R.string.steel_steel_structure_label)
    val claddingRoofLabel = stringResource(R.string.steel_cladding_roof)
    val foundationLabel = stringResource(R.string.steel_foundation_label)
    AndroidView(
        factory = { context ->
            PieChart(context).apply {
                description.isEnabled = false
                isRotationEnabled = true
                holeRadius = 40f
                setTransparentCircleAlpha(0)
                setCenterText(costStructureLabel)
                setCenterTextSize(10f)
                setDrawEntryLabels(true)
                legend.isEnabled = false
            }
        },
        update = { chart ->
            val entries = listOf(
                PieEntry(60f, steelStructureLabel),
                PieEntry(25f, claddingRoofLabel),
                PieEntry(15f, foundationLabel)
            )
            val dataSet = PieDataSet(entries, "")
            dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
            dataSet.valueTextSize = 12f
            dataSet.valueTextColor = android.graphics.Color.WHITE
            
            chart.data = PieData(dataSet)
            chart.animateY(800)
            chart.invalidate()
        },
        modifier = Modifier.fillMaxWidth().height(200.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructuralAnalysisVisualizer(inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
    var selectedView by remember { mutableIntStateOf(0) } // 0: Moments, 1: Shear, 2: Deflection
    
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        AppText(stringResource(R.string.steel_structural_analysis), fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = selectedView == 0, onClick = { selectedView = 0 }, label = { AppText("BMD", role = AppTextRole.Caption) })
            FilterChip(selected = selectedView == 1, onClick = { selectedView = 1 }, label = { AppText("SFD", role = AppTextRole.Caption) })
            FilterChip(selected = selectedView == 2, onClick = { selectedView == 2 }, label = { AppText(stringResource(R.string.steel_deflection_label), role = AppTextRole.Caption) })
        }
        
        Card(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
        ) {
            ComposeCanvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val w = size.width
                val h = size.height
                val pad = 40f
                val frameW = w - 2 * pad
                val frameH = h - 2 * pad
                
                val colH = frameH * 0.7f
                val apexH = frameH * 0.9f
                
                val p1 = Offset(pad, h - pad)
                val p2 = Offset(pad, h - pad - colH)
                val p3 = Offset(pad + frameW / 2, h - pad - apexH)
                val p4 = Offset(pad + frameW, h - pad - colH)
                val p5 = Offset(pad + frameW, h - pad)
                
                val path = Path().apply {
                    moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); lineTo(p4.x, p4.y); lineTo(p5.x, p5.y)
                }
                drawPath(path, Color.Gray, style = Stroke(2f))
                
                val diagColor = when(selectedView) {
                    0 -> Color(0xFFE91E63)
                    1 -> Color(0xFF2196F3)
                    else -> Color(0xFF4CAF50)
                }
                
                when(selectedView) {
                    0 -> { // BMD
                        val bmdPath = Path().apply {
                            moveTo(p1.x, p1.y)
                            lineTo(p2.x - 20f, p2.y)
                            lineTo(p3.x, p3.y + 30f)
                            lineTo(p4.x + 20f, p4.y)
                            lineTo(p5.x, p5.y)
                        }
                        drawPath(bmdPath, diagColor.copy(alpha = 0.3f))
                        drawPath(bmdPath, diagColor, style = Stroke(2f))
                    }
                    1 -> { // SFD
                        drawLine(diagColor, p1, Offset(p1.x + 15f, p1.y), 2f)
                        drawLine(diagColor, Offset(p2.x + 15f, p2.y), Offset(p2.x - 15f, p2.y), 2f)
                    }
                    2 -> { // Deflection
                        val defPath = Path().apply {
                            moveTo(p1.x, p1.y)
                            quadraticTo(p2.x + 10f, p2.y, p3.x, p3.y + 5f)
                            quadraticTo(p4.x - 10f, p4.y, p5.x, p5.y)
                        }
                        drawPath(defPath, diagColor, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryLine(label: String, value: String, isPrimary: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label, style = if (isPrimary) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium)
        AppText(value, fontWeight = FontWeight.Bold, color = if (isPrimary) MaterialTheme.colorScheme.primary else Color.Unspecified)
    }
}

@Composable
fun AnalysisDetailCard(res: MainFrameResult) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppText(stringResource(R.string.steel_frame_analysis_results_label), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            ResultRow(stringResource(R.string.steel_max_moment), "${String.format("%.1f", res.maxMoment)} kN.m")
            ResultRow(stringResource(R.string.steel_max_shear), "${String.format("%.1f", res.maxShear)} kN")
            ResultRow(stringResource(R.string.steel_max_deflection), "${String.format("%.1f", res.maxDeflection)} mm")
        }
    }
}

@Composable
fun SteelWarehouseVisualizer(
    inputs: SteelWarehouseInputs,
    result: SteelWarehouseAnalysisResult,
    captureLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    captureWidthPx: Int,
    captureHeightPx: Int
) {
    var viewMode by remember { mutableIntStateOf(0) } // 0: Front, 1: Plan, 2: Side, 3: 3D
    val viewModes = listOf(stringResource(R.string.steel_view_front_elevation), stringResource(R.string.steel_view_plan), stringResource(R.string.steel_view_side_elevation), stringResource(R.string.steel_view_3d_perspective))
    
    Column(modifier = Modifier.fillMaxWidth()) {
        AppText(stringResource(R.string.steel_detailed_design_drawings), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        
        ScrollableTabRow(selectedTabIndex = viewMode, edgePadding = 0.dp, containerColor = Color.Transparent) {
            viewModes.forEachIndexed { index, title ->
                Tab(selected = viewMode == index, onClick = { viewMode = index }, text = { AppText(title, fontSize = 10.sp, role = AppTextRole.Caption) })
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth().height(350.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C))
        ) {
            ProfessionalWarehouseDrawing(
                inputs = inputs,
                result = result,
                viewMode = viewMode,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Invisible duplicate at full opacity, so the PDF embeds exactly the view that is
        // currently on screen (Front / Plan / Side / 3D) instead of a separate renderer.
        ComposeDrawingCaptureUtil.DrawingCaptureArea(
            captureLayer = captureLayer,
            widthPx = captureWidthPx,
            heightPx = captureHeightPx
        ) {
            Box(modifier = Modifier.background(Color(0xFF1E1E2C))) {
                ProfessionalWarehouseDrawing(
                    inputs = inputs,
                    result = result,
                    viewMode = viewMode,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun SectionResultCard(title: String, section: SteelSectionType) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppText(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            AppText(stringResource(R.string.steel_section_format, section.sectionName), fontSize = 13.sp)
            AppText(stringResource(R.string.steel_weight_format, section.weight), fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun SecondaryMembersCard(res: SecondaryMembersResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppText(stringResource(R.string.steel_secondary_members), fontWeight = FontWeight.Bold)
            AppText(stringResource(R.string.steel_purlins_format, res.purlinSection.sectionName), fontSize = 13.sp)
            AppText(stringResource(R.string.steel_girts_format, res.girtSection.sectionName), fontSize = 13.sp)
        }
    }
}

@Composable
fun ConnectionDetailCard(detail: SteelConnectionDetail) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppText(stringResource(R.string.steel_connection_name_format, detail.name), fontWeight = FontWeight.Bold)
            AppText(stringResource(R.string.steel_capacity_demand_format, detail.capacity, detail.demand), fontSize = 12.sp)
        }
    }
}

@Composable
fun RecommendationsCard(items: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppText(stringResource(R.string.steel_recommendations), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            items.forEach { item ->
                AppText("• $item", fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
