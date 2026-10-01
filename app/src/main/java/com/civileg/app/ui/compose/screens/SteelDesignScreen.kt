package com.civileg.app.ui.compose.screens

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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
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
import com.civileg.app.domain.calculations.aci.SteelFlexuralResult
import com.civileg.app.domain.calculations.ecp.BoltDesignResult
import com.civileg.app.domain.calculations.ecp.BlockShearResult
import com.civileg.app.domain.calculations.ecp.SteelConnectionDesign
import com.civileg.app.domain.calculations.ecp.SteelBasePlateDesign
import com.civileg.app.domain.calculations.ecp.WeldDesignResult
import com.civileg.app.domain.entities.*
import com.civileg.app.ui.compose.components.PremiumDesignSystem
import com.civileg.app.ui.compose.components.PremiumSectionHeader
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen as AppInteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalSteelDrawing
import com.civileg.app.ui.compose.components.drawings.ProfessionalWarehouseDrawing
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.SteelViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import java.io.File
import java.util.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelDesignScreen(
    viewModel: SteelViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val result by viewModel.result.observeAsState()
    val warehouseResult by viewModel.warehouseResult.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.steel_warehouse_tab),
        stringResource(R.string.steel_sections_tab),
        stringResource(R.string.steel_connections_tab),
        stringResource(R.string.steel_baseplate_tab)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_steel), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetResult() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reset))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> SteelWarehouseTab(viewModel, warehouseResult, isLoading)
                1 -> SteelSectionTab(viewModel, result, isLoading)
                2 -> ConnectionDesignTab(viewModel)
                3 -> BasePlateDesignTab(viewModel)
            }


        }
    }

    if (isLoading) {
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
                    Text(
                        stringResource(R.string.steel_calculating),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SteelWarehouseTab(viewModel: SteelViewModel, result: SteelWarehouseAnalysisResult?, isLoading: Boolean) {
    val context = LocalContext.current
    var span by remember { mutableStateOf("20.0") }
    var spacing by remember { mutableStateOf("6.0") }
    var eaveHeight by remember { mutableStateOf("6.0") }
    var totalLength by remember { mutableStateOf("60.0") }
    var roofSlope by remember { mutableStateOf("10") }
    var deadLoad by remember { mutableStateOf("0.25") }
    var liveLoad by remember { mutableStateOf("0.60") }
    var windSpeed by remember { mutableStateOf("150") }
    var numberOfStories by remember { mutableIntStateOf(1) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SteelModuleSectionHeader(stringResource(R.string.steel_warehouse_inputs), R.drawable.ic_frame) }
        
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
                    Text(stringResource(R.string.steel_hangar_floors_config), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = numberOfStories == 1,
                            onClick = { numberOfStories = 1 },
                            label = { Text(stringResource(R.string.steel_1_floor_standard_hangar)) }
                        )
                        FilterChip(
                            selected = numberOfStories == 2,
                            onClick = { numberOfStories = 2 },
                            label = { Text(stringResource(R.string.steel_2_floors_mezzanine)) }
                        )
                    }
                }
            }
        }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.steel_loads_header), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(deadLoad, stringResource(R.string.steel_dl_knm2), { deadLoad = it }, Modifier.weight(1f))
                        SteelInputField(liveLoad, stringResource(R.string.steel_ll_knm2), { liveLoad = it }, Modifier.weight(1f))
                        SteelInputField(windSpeed, stringResource(R.string.steel_wind_kmh), { windSpeed = it }, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val s = span.toDoubleOrNull() ?: 20.0
                    val l = totalLength.toDoubleOrNull() ?: 60.0
                    val h = eaveHeight.toDoubleOrNull() ?: 6.0
                    val sl = roofSlope.toDoubleOrNull() ?: 10.0
                    val ridgeH = h + (s / 2.0 * tan(Math.toRadians(sl)))
                    
                    val inputs = SteelWarehouseInputs(
                        span = s,
                        length = l,
                        eaveHeight = h,
                        ridgeHeight = ridgeH,
                        baySpacing = spacing.toDoubleOrNull() ?: 6.0,
                        slope = tan(Math.toRadians(sl)),
                        deadLoad = deadLoad.toDoubleOrNull() ?: 0.25,
                        liveLoad = liveLoad.toDoubleOrNull() ?: 0.60,
                        windLoad = windSpeed.toDoubleOrNull() ?: 150.0,
                        numberOfStories = numberOfStories
                    )
                    viewModel.calculateWarehouse(inputs)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.steel_design_warehouse))
            }
        }

        result?.let { res ->
            item { WarehouseResultSummary(res) }
            
            item {
                Text(stringResource(R.string.steel_cost_structure_feasibility), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                            Text(stringResource(R.string.steel_engineering_calc_trace), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            res.calculationTrace.forEach { step ->
                                Text("• $step", fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
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
                        title = { Text(stringResource(R.string.steel_export_pro_pdf_dialog)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = projectName, onValueChange = { projectName = it }, label = { Text(stringResource(R.string.steel_project_name_label)) }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = clientName, onValueChange = { clientName = it }, label = { Text(stringResource(R.string.steel_client_name_label)) }, modifier = Modifier.fillMaxWidth())
                                Text(stringResource(R.string.steel_report_english_note), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                viewModel.exportWarehouseProToPdf(context, clientName, projectName) { }
                                showExportDialog = false
                            }) { Text(stringResource(R.string.steel_export_now)) }
                        },
                        dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text(stringResource(R.string.cancel)) } }
                    )
                }

                Button(
                    onClick = { showExportDialog = true },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.steel_generate_english_pdf), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }

            item { StructuralAnalysisVisualizer(lastWarehouseInputs(span, spacing, eaveHeight, totalLength, roofSlope, deadLoad, liveLoad, windSpeed), res) }
            item { SteelWarehouseVisualizer(lastWarehouseInputs(span, spacing, eaveHeight, totalLength, roofSlope, deadLoad, liveLoad, windSpeed), res) }
        }
    }
}

private fun lastWarehouseInputs(span: String, spacing: String, height: String, length: String, slope: String, dl: String, ll: String, ws: String): SteelWarehouseInputs {
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
        deadLoad = dl.toDoubleOrNull() ?: 0.25,
        liveLoad = ll.toDoubleOrNull() ?: 0.60,
        windLoad = ws.toDoubleOrNull() ?: 150.0
    )
}

@Composable
fun WarehouseResultSummary(res: SteelWarehouseAnalysisResult) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.steel_design_summary), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            ResultRow(stringResource(R.string.steel_total_weight), "${String.format("%.1f", res.totalWeight)} Tons")
            ResultRow(stringResource(R.string.steel_weight_m2), "${String.format("%.1f", res.weightPerM2)} kg/m\u00B2")
            ResultRow(stringResource(R.string.steel_base_plate_thickness), "${res.mainFrame.basePlateThickness.toInt()} mm")
            ResultRow(stringResource(R.string.steel_base_plate_anchor_bolts), "${res.mainFrame.basePlateBoltsCount}xM24 Bolts")
            if (res.mainFrame.floorBeamSection != null) {
                ResultRow(stringResource(R.string.steel_intermediate_floor_beam), res.mainFrame.floorBeamSection.sectionName)
                ResultRow(stringResource(R.string.steel_floor_beam_moment), "${String.format("%.1f", res.mainFrame.floorBeamMaxMoment)} kN.m")
            }
            ResultRow(stringResource(R.string.steel_status), if (res.safetyStatus) stringResource(R.string.steel_status_safe) else stringResource(R.string.steel_status_unsafe))
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
        Text(stringResource(R.string.steel_structural_analysis), fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = selectedView == 0, onClick = { selectedView = 0 }, label = { Text("BMD") })
            FilterChip(selected = selectedView == 1, onClick = { selectedView = 1 }, label = { Text("SFD") })
            FilterChip(selected = selectedView == 2, onClick = { selectedView = 2 }, label = { Text(stringResource(R.string.steel_deflection_label)) })
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
        Text(label, style = if (isPrimary) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, color = if (isPrimary) MaterialTheme.colorScheme.primary else Color.Unspecified)
    }
}

@Composable
fun AnalysisDetailCard(res: MainFrameResult) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.steel_frame_analysis_results_label), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            ResultRow(stringResource(R.string.steel_max_moment), "${String.format("%.1f", res.maxMoment)} kN.m")
            ResultRow(stringResource(R.string.steel_max_shear), "${String.format("%.1f", res.maxShear)} kN")
            ResultRow(stringResource(R.string.steel_max_deflection), "${String.format("%.1f", res.maxDeflection)} mm")
        }
    }
}

@Composable
fun SteelWarehouseVisualizer(inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
    var viewMode by remember { mutableIntStateOf(0) } // 0: Front, 1: Plan, 2: Side, 3: 3D
    val viewModes = listOf(stringResource(R.string.steel_view_front_elevation), stringResource(R.string.steel_view_plan), stringResource(R.string.steel_view_side_elevation), stringResource(R.string.steel_view_3d_perspective))
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.steel_detailed_design_drawings), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        
        ScrollableTabRow(selectedTabIndex = viewMode, edgePadding = 0.dp, containerColor = Color.Transparent) {
            viewModes.forEachIndexed { index, title ->
                Tab(selected = viewMode == index, onClick = { viewMode = index }, text = { Text(title, fontSize = 10.sp) })
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
    }
}

@Composable
fun SectionResultCard(title: String, section: SteelSectionType) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.steel_section_format, section.sectionName), fontSize = 13.sp)
            Text(stringResource(R.string.steel_weight_format, section.weight), fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun SecondaryMembersCard(res: SecondaryMembersResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.steel_secondary_members), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.steel_purlins_format, res.purlinSection.sectionName), fontSize = 13.sp)
            Text(stringResource(R.string.steel_girts_format, res.girtSection.sectionName), fontSize = 13.sp)
        }
    }
}

@Composable
fun ConnectionDetailCard(detail: SteelConnectionDetail) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.steel_connection_name_format, detail.name), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.steel_capacity_demand_format, detail.capacity, detail.demand), fontSize = 12.sp)
        }
    }
}

@Composable
fun RecommendationsCard(items: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.steel_recommendations), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            items.forEach { item ->
                Text("• $item", fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SteelSectionTab(viewModel: SteelViewModel, result: SteelMemberResult?, isLoading: Boolean) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("IPE (European I-Beams)") }
    var selectedSection by remember { mutableStateOf<SteelSectionType?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedCategory by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf(false) }
    
    var axialLoad by remember { mutableStateOf("500") }
    var moment by remember { mutableStateOf("100") }
    var shear by remember { mutableStateOf("50") }
    var length by remember { mutableStateOf("3") }
    var selectedMemberType by remember { mutableStateOf(SteelMemberType.COLUMN) }
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }
    
    val searchResults by viewModel.searchResults.observeAsState(emptyList())
    val library = viewModel.sectionLibrary
    val categories = library.keys.toList()

    val steelCodes = listOf("ECP 205", "AISC 360-16", "SBC 306")
    var selectedSteelCode by remember { mutableStateOf(steelCodes[0]) }
    var expandedSteelCode by remember { mutableStateOf(false) }
    
    val steelGrades = SteelGrade.entries
    var selectedGrade by remember { mutableStateOf(SteelGrade.ST37) }
    var expandedGrade by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SteelModuleSectionHeader(stringResource(R.string.steel_section_dictionary), R.drawable.ic_steel) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.steel_design_code_metal), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            ExposedDropdownMenuBox(
                                expanded = expandedSteelCode,
                                onExpandedChange = { expandedSteelCode = !expandedSteelCode }
                            ) {
                                OutlinedTextField(
                                    value = selectedSteelCode,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSteelCode) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedSteelCode,
                                    onDismissRequest = { expandedSteelCode = false }
                                ) {
                                    steelCodes.forEach { code ->
                                        DropdownMenuItem(
                                            text = { Text(code) },
                                            onClick = { selectedSteelCode = code; expandedSteelCode = false }
                                        )
                                    }
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.steel_grade_label), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            ExposedDropdownMenuBox(
                                expanded = expandedGrade,
                                onExpandedChange = { expandedGrade = !expandedGrade }
                            ) {
                                OutlinedTextField(
                                    value = selectedGrade.displayName,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGrade) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedGrade,
                                    onDismissRequest = { expandedGrade = false }
                                ) {
                                    steelGrades.forEach { grade ->
                                        DropdownMenuItem(
                                            text = { Text(grade.displayName) },
                                            onClick = { selectedGrade = grade; expandedGrade = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    viewModel.searchSections(it)
                },
                label = { Text(stringResource(R.string.steel_search_hint)) },
                placeholder = { Text(stringResource(R.string.steel_section_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = ""; viewModel.searchSections("") }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (searchQuery.isNotEmpty()) {
            item {
                Text(stringResource(R.string.steel_search_results), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    searchResults.take(8).forEach { section ->
                        Surface(
                            onClick = { 
                                searchQuery = ""
                                viewModel.searchSections("")
                                categories.find { cat -> library[cat]?.any { s -> s.sectionName == section.sectionName } == true }?.let { cat ->
                                    selectedCategory = cat
                                    selectedSection = section
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Architecture, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(section.displayName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.steel_section_category), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    ExposedDropdownMenuBox(
                        expanded = expandedCategory,
                        onExpandedChange = { expandedCategory = !expandedCategory }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCategory,
                            onDismissRequest = { expandedCategory = false }
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        selectedCategory = category
                                        selectedSection = library[category]?.firstOrNull()
                                        expandedCategory = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.steel_selected_section), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    ExposedDropdownMenuBox(
                        expanded = expandedSection,
                        onExpandedChange = { expandedSection = !expandedSection }
                    ) {
                        OutlinedTextField(
                            value = selectedSection?.sectionName ?: stringResource(R.string.steel_select_section),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSection) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSection,
                            onDismissRequest = { expandedSection = false }
                        ) {
                            library[selectedCategory]?.forEach { section ->
                                DropdownMenuItem(
                                    text = { Text(section.sectionName) },
                                    onClick = {
                                        selectedSection = section
                                        expandedSection = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        selectedSection?.let { section ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.steel_section_properties), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                PropertyLine("h", "${section.depth.toInt()} mm")
                                PropertyLine("b", "${section.width.toInt()} mm")
                                PropertyLine("tw", "${section.webThickness} mm")
                                PropertyLine("tf", "${section.flangeThickness} mm")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                PropertyLine("Area", "${String.format("%.1f", section.area / 100.0)} cm\u00B2")
                                PropertyLine("Weight", "${String.format("%.1f", section.weight)} kg/m")
                                PropertyLine("Ix", "${String.format("%.0f", section.ix / 10000.0)} cm\u2074")
                                PropertyLine("Zx", "${String.format("%.0f", section.zx / 1000.0)} cm\u00B3")
                            }
                        }
                    }
                }
            }
        }

        item { SteelModuleSectionHeader(stringResource(R.string.steel_applied_loads), R.drawable.ic_frame) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(axialLoad, stringResource(R.string.steel_axial_pu_kn), { axialLoad = it }, Modifier.weight(1f))
                        SteelInputField(moment, stringResource(R.string.steel_moment_mu_knm), { moment = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(shear, stringResource(R.string.steel_shear_vu_kn), { shear = it }, Modifier.weight(1f))
                        SteelInputField(length, stringResource(R.string.steel_length_m_label), { length = it }, Modifier.weight(1f))
                    }
                    
                    Text(stringResource(R.string.steel_member_type_label), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        SteelMemberType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedMemberType == type,
                                onClick = { selectedMemberType = type },
                                label = { Text(type.name, fontSize = 10.sp) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { 
                        selectedSection?.let { sec ->
                            viewModel.calculateSteelMember(
                                section = sec,
                                memberType = selectedMemberType,
                                inputs = SteelInputs(
                                    axialLoad = axialLoad.toDoubleOrNull() ?: 0.0,
                                    moment = moment.toDoubleOrNull() ?: 0.0,
                                    shear = shear.toDoubleOrNull() ?: 0.0,
                                    unbracedLength = (length.toDoubleOrNull() ?: 3.0) * 1000.0,
                                    length = (length.toDoubleOrNull() ?: 3.0) * 1000.0,
                                    grade = selectedGrade
                                ),
                                code = selectedCode
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading && selectedSection != null
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.steel_analyze_section))
                }

                if (result != null) {
                    Button(
                        onClick = { viewModel.exportToPdf(context) { } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        enabled = !viewModel.isExporting.value!!
                    ) {
                        if (viewModel.isExporting.value!!) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pdf_report))
                        }
                    }
                }
            }
        }

        result?.let { res ->
            item {
                val animatedRatio by animateFloatAsState(targetValue = res.utilizationRatio.toFloat(), animationSpec = tween(1000), label = "ur")
                val urColor = when {
                    res.utilizationRatio > 1.0 -> Color.Red
                    res.utilizationRatio > 0.9 -> Color(0xFFF57C00)
                    else -> Color(0xFF2E7D32)
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = urColor.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, urColor.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.steel_utilization_ratio), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (res.isSafe) stringResource(R.string.steel_section_safe) else stringResource(R.string.steel_section_unsafe),
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red,
                                    fontSize = 13.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    progress = { animatedRatio.coerceAtMost(1f) },
                                    modifier = Modifier.size(64.dp),
                                    strokeWidth = 7.dp,
                                    color = urColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${(res.utilizationRatio * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = urColor
                                )
                            }
                        }
                    }
                }
            }
            item { SteelResultCard(res) }

            val flexRes = res.detailedResults["flexRes"] as? SteelFlexuralResult
            if (flexRes != null && flexRes.calculationTrace.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.steel_calculation_trace), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            flexRes.calculationTrace.forEach { step ->
                                Text("• $step", fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            item {
                var selectedViewMode by remember { mutableIntStateOf(0) }
                AppInteractiveDrawingScreen(
                    title = stringResource(R.string.steel_section_drawing),
                    subtitle = stringResource(R.string.steel_member_detail),
                    viewModes = listOf(
                        stringResource(R.string.steel_view_all),
                        stringResource(R.string.steel_view_longitudinal),
                        stringResource(R.string.steel_view_cross),
                        stringResource(R.string.steel_view_connections)
                    ),
                    selectedViewMode = selectedViewMode,
                    onViewModeChanged = { selectedViewMode = it },
                    drawingContent = {
                        val conn = res.connectionDesign
                        var boltDia = 20.0
                        var boltCount = 4
                        var boltGauge = 90.0
                        var boltPitch = 75.0
                        var endPlateThk = 12.0
                        var hasStiff = false
                        var weldSz = 6.0
                        
                        if (conn != null && conn.connectionType is ConnectionType.Bolted) {
                            boltDia = conn.connectionType.boltDiameter
                            boltCount = conn.connectionType.numberOfBolts
                        }

                        ProfessionalSteelDrawing(
                            sectionName = res.sectionType.sectionName,
                            depth = res.sectionType.depth,
                            flangeWidth = res.sectionType.width,
                            webThickness = res.sectionType.webThickness,
                            flangeThickness = res.sectionType.flangeThickness,
                            memberLength = (length.toDoubleOrNull() ?: 3.0) * 1000.0,
                            isSafe = res.isSafe,
                            utilizationRatio = res.utilizationRatio * 100,
                            viewMode = selectedViewMode,
                            modifier = Modifier.fillMaxSize(),
                            sectionType = res.sectionType.displayName,
                            radius = res.sectionType.rootRadius,
                            area = res.sectionType.area,
                            ix = res.sectionType.ix,
                            sx = res.sectionType.sx,
                            zx = res.sectionType.zx,
                            weightPerMeter = res.sectionType.weight,
                            boltDia = boltDia,
                            boltCount = boltCount,
                            boltGauge = boltGauge,
                            boltPitch = boltPitch,
                            endPlateThickness = endPlateThk,
                            hasStiffener = hasStiff,
                            weldSize = weldSz,
                            isColumn = selectedMemberType == SteelMemberType.COLUMN
                        )
                    }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeldDesignTab(viewModel: SteelViewModel) {
    val context = LocalContext.current
    var weldSize by remember { mutableStateOf("6") }
    var weldLength by remember { mutableStateOf("200") }
    var expandedElectrode by remember { mutableStateOf(false) }
    var selectedElectrode by remember { mutableStateOf(ElectrodeType.E70XX) }
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }
    var capacity by remember { mutableStateOf(0.0) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SteelModuleSectionHeader(stringResource(R.string.steel_weld_design_header), R.drawable.ic_frame) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SteelInputField(weldSize, stringResource(R.string.steel_weld_size_hint), { weldSize = it }, Modifier.fillMaxWidth())
                    SteelInputField(weldLength, stringResource(R.string.steel_weld_length_hint), { weldLength = it }, Modifier.fillMaxWidth())
                    
                    Text(stringResource(R.string.steel_electrode_type), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                    text = { Text(electrode.displayName) },
                                    onClick = { selectedElectrode = electrode; expandedElectrode = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(onClick = {
                capacity = viewModel.calculateWeldCapacity(
                    weldSize.toDoubleOrNull() ?: 6.0,
                    weldLength.toDoubleOrNull() ?: 200.0,
                    selectedElectrode,
                    selectedCode
                )
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.steel_calculate_capacity))
            }
        }

        if (capacity > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.steel_weld_capacity), fontWeight = FontWeight.Bold)
                        Text("${String.format("%.1f", capacity)} kN", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Button(
                    onClick = { viewModel.exportWeldToPdf(context, weldSize.toDoubleOrNull() ?: 6.0, weldLength.toDoubleOrNull() ?: 200.0, selectedElectrode, selectedCode, capacity) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pdf_report))
                }
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
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }
    var capacity by remember { mutableStateOf(0.0) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SteelModuleSectionHeader(stringResource(R.string.steel_bolt_design_header), R.drawable.ic_frame) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SteelInputField(boltDia, stringResource(R.string.steel_bolt_diameter_hint), { boltDia = it }, Modifier.fillMaxWidth())
                    SteelInputField(boltCount, stringResource(R.string.steel_bolt_count_hint), { boltCount = it }, Modifier.fillMaxWidth())
                    
                    Text(stringResource(R.string.steel_bolt_grade_label), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                    text = { Text(grade.displayName) },
                                    onClick = { selectedBoltGrade = grade; expandedBoltGrade = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(onClick = {
                capacity = viewModel.calculateBoltCapacity(
                    boltDia.toDoubleOrNull() ?: 20.0,
                    selectedBoltGrade,
                    boltCount.toIntOrNull() ?: 4,
                    selectedCode
                )
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.steel_calculate_capacity))
            }
        }

        if (capacity > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.steel_bolt_capacity), fontWeight = FontWeight.Bold)
                        Text("${String.format("%.1f", capacity)} kN", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Button(
                    onClick = { viewModel.exportBoltToPdf(context, boltDia.toDoubleOrNull() ?: 20.0, selectedBoltGrade, boltCount.toIntOrNull() ?: 4, selectedCode, capacity) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pdf_report))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectionDesignTab(viewModel: SteelViewModel) {
    val context = LocalContext.current
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }
    var connectionType by remember { mutableIntStateOf(0) }
    val connTypes = listOf(stringResource(R.string.steel_conn_bolted_shear), stringResource(R.string.steel_conn_bolted_moment), stringResource(R.string.steel_conn_welded), stringResource(R.string.steel_conn_combined))

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

    var boltResult by remember { mutableStateOf<BoltDesignResult?>(null) }
    var weldResult by remember { mutableStateOf<WeldDesignResult?>(null) }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SteelModuleSectionHeader(stringResource(R.string.steel_connection_design), R.drawable.ic_frame) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.steel_design_code_metal), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CalculatorEngine.DesignCode.entries.forEach { code ->
                            FilterChip(
                                selected = selectedCode == code,
                                onClick = { selectedCode = code },
                                label = { Text(code.name, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.steel_conn_type), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow {
                        connTypes.forEachIndexed { idx, name ->
                            FilterChip(
                                selected = connectionType == idx,
                                onClick = { connectionType = idx; boltResult = null; weldResult = null },
                                label = { Text(name, fontSize = 11.sp) },
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
                        Text(stringResource(R.string.steel_bolt_data), fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SteelInputField(boltDiameter, stringResource(R.string.steel_bolt_dia_hint), { boltDiameter = it }, Modifier.weight(1f))
                            SteelInputField(numBolts, stringResource(R.string.steel_bolt_count_hint), { numBolts = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SteelInputField(boltSpacing, stringResource(R.string.steel_bolt_spacing_hint), { boltSpacing = it }, Modifier.weight(1f))
                            SteelInputField(edgeDistance, stringResource(R.string.steel_edge_dist_hint), { edgeDistance = it }, Modifier.weight(1f))
                        }

                        Text(stringResource(R.string.steel_bolt_grade_label), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                        text = { Text(grade.displayName) },
                                        onClick = { selectedBoltGrade = grade; expandedBoltGrade = false }
                                    )
                                }
                            }
                        }

                        Text(stringResource(R.string.steel_dist_pattern), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                        text = { Text(pattern.displayName) },
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
                        Text(stringResource(R.string.steel_applied_loads), fontWeight = FontWeight.Bold)
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
                        Text(stringResource(R.string.steel_weld_data), fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SteelInputField(weldSize, stringResource(R.string.steel_weld_size_hint), { weldSize = it }, Modifier.weight(1f))
                            SteelInputField(weldLength, stringResource(R.string.steel_weld_length_hint), { weldLength = it }, Modifier.weight(1f))
                        }
                        SteelInputField(appliedWeldForce, stringResource(R.string.steel_applied_force_hint), { appliedWeldForce = it }, Modifier.fillMaxWidth())

                        Text(stringResource(R.string.steel_electrode_type), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                        text = { Text(electrode.displayName) },
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
            Button(onClick = {
                val designer = SteelConnectionDesign()
                if (connectionType < 3) {
                    val connType = when (connectionType) {
                        0 -> BoltConnectionType.BEARING
                        1 -> BoltConnectionType.COMBINED
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
                        edgeDistance = edgeDistance.toDoubleOrNull(),
                        spacing = boltSpacing.toDoubleOrNull()
                    )
                }
                if (connectionType >= 2) {
                    weldResult = designer.designWeldedConnection(
                        weldType = WeldType.FILLET,
                        weldSize = weldSize.toDoubleOrNull() ?: 6.0,
                        weldLength = weldLength.toDoubleOrNull() ?: 200.0,
                        electrodeType = selectedElectrode,
                        appliedForce = appliedWeldForce.toDoubleOrNull() ?: 100.0
                    )
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Engineering, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.steel_design_connection))
            }
        }

        boltResult?.let { res ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.steel_bolted_results), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                        ResultRow(stringResource(R.string.steel_total_capacity), "${String.format("%.1f", res.controllingCapacity)} kN")
                        ResultRow(stringResource(R.string.steel_utilization_ratio), "${(res.utilizationRatio * 100).toInt()}%")
                        Text(if (res.isSafe) stringResource(R.string.steel_status_safe) else stringResource(R.string.steel_status_unsafe), fontWeight = FontWeight.Bold, color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red)
                        
                        Button(
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pdf_report))
                        }
                    }
                }
            }
        }

        weldResult?.let { res ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.steel_welded_results), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        ResultRow(stringResource(R.string.steel_total_capacity), "${String.format("%.1f", res.capacity)} kN")
                        ResultRow(stringResource(R.string.steel_utilization_ratio), "${(res.utilizationRatio * 100).toInt()}%")
                        Text(if (res.isSafe) stringResource(R.string.steel_status_safe) else stringResource(R.string.steel_status_unsafe), fontWeight = FontWeight.Bold, color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red)
                        
                        Button(
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pdf_report))
                        }
                    }
                }
            }
        }
        
        item {
            if (boltResult != null || weldResult != null) {
                Text(stringResource(R.string.steel_connection_visualization), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
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
    var expandedBoltGrade by remember { mutableStateOf(false) }
    var selectedBoltGrade by remember { mutableStateOf("4.6") }
    var bpResult by remember { mutableStateOf<SteelBasePlateDesign.BasePlateResult?>(null) }

    val boltGradeOptions = SteelBasePlateDesign.Companion.BoltGrade.entries.map { it.getGradeName() }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SteelModuleSectionHeader(stringResource(R.string.steel_baseplate_header), R.drawable.ic_footing) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.steel_column_data), fontWeight = FontWeight.Bold)
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
                    Text(stringResource(R.string.steel_applied_loads), fontWeight = FontWeight.Bold)
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
                    Text(stringResource(R.string.material_properties), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(fpc, stringResource(R.string.steel_fcu_mpa), { fpc = it }, Modifier.weight(1f))
                        SteelInputField(fy, stringResource(R.string.steel_fy_mpa), { fy = it }, Modifier.weight(1f))
                    }

                    Text(stringResource(R.string.steel_anchor_bolt_grade), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                    text = { Text(stringResource(R.string.steel_grade_format, grade)) },
                                    onClick = { selectedBoltGrade = grade; expandedBoltGrade = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(onClick = {
                val designer = SteelBasePlateDesign()
                val boltGrade = SteelBasePlateDesign.Companion.BoltGrade.entries.find { it.getGradeName() == selectedBoltGrade }
                    ?: SteelBasePlateDesign.Companion.BoltGrade.GRADE_4_6
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
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.steel_design_baseplate))
            }
        }

        bpResult?.let { res ->
            item {
                val urColor = if (res.utilizationRatio <= 1.0) Color(0xFF2E7D32) else Color.Red
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.steel_baseplate_results), fontWeight = FontWeight.Bold)
                        ResultRow(stringResource(R.string.steel_baseplate_dims), "${res.plateLength.toInt()} x ${res.plateWidth.toInt()} mm")
                        ResultRow(stringResource(R.string.steel_thickness_mm), "${res.plateThickness.toInt()} mm")
                        ResultRow(stringResource(R.string.steel_bolt_count), "${res.anchorBolts.numberOfBolts}")
                        ResultRow(stringResource(R.string.steel_bolt_dia), "M${res.anchorBolts.boltDiameter.toInt()}")
                        ResultRow(stringResource(R.string.steel_utilization_ratio), "${(res.utilizationRatio * 100).toInt()}%")
                        Text(if (res.isSafe) stringResource(R.string.steel_status_safe) else stringResource(R.string.steel_status_unsafe), color = urColor, fontWeight = FontWeight.Bold)
                        
                        Spacer(Modifier.height(8.dp))
                        Button(
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pdf_report), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SteelResultCard(res: SteelMemberResult) {
    var showFormulas by remember { mutableStateOf(false) }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.steel_detailed_design_results), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { showFormulas = !showFormulas }) {
                    Icon(if (showFormulas) Icons.Default.VisibilityOff else Icons.Default.Functions, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            
            ResultRow(stringResource(R.string.steel_axial_capacity_label), "${String.format("%.1f", res.axialCapacity)} kN")
            if (showFormulas) FormulaText("\u03A6Pn = \u03A6 \u00B7 Fcr \u00B7 Ag")
            
            ResultRow(stringResource(R.string.steel_moment_capacity_label), "${String.format("%.1f", res.flexuralCapacity)} kN.m")
            if (showFormulas) FormulaText("\u03A6Mn = \u03A6 \u00B7 Fy \u00B7 Zx")
            
            ResultRow(stringResource(R.string.steel_shear_capacity_label), "${String.format("%.1f", res.shearCapacity)} kN")
            if (showFormulas) FormulaText("\u03A6Vn = \u03A6 \u00B7 0.6 \u00B7 Fy \u00B7 Aw")
            
            ResultRow(stringResource(R.string.steel_utilization_ratio_label), "${(res.utilizationRatio * 100).toInt()}%")
            ResultRow(stringResource(R.string.status), if (res.isSafe) stringResource(R.string.steel_status_safe) else stringResource(R.string.steel_status_unsafe))
        }
    }
}

@Composable
fun FormulaText(formula: String) {
    Text(
        text = formula,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}


@Composable
private fun SteelModuleSectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun SteelInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PropertyLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun Color.toArgbInt(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
