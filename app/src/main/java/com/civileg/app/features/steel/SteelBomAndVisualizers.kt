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

fun SteelStructureAndBOMTab(
    viewModel: SteelViewModel,
    warehouseResult: SteelWarehouseAnalysisResult?,
    isLoading: Boolean
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf(
        stringResource(R.string.steel_structural_visualization),
        stringResource(R.string.steel_bill_of_materials),
        stringResource(R.string.steel_moment_frame_analysis),
        stringResource(R.string.steel_shop_drawing)
    )

    val warehouseResultState by viewModel.warehouseResult.observeAsState()
    val finalResult = warehouseResult ?: warehouseResultState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 4.dp)
            }
        } else {
            ScrollableTabRow(selectedTabIndex = selectedSubTab, edgePadding = 16.dp) {
                subTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedSubTab == index,
                        onClick = { selectedSubTab = index },
                        text = { AppText(title, fontSize = 11.sp, role = AppTextRole.Caption) }
                    )
                }
            }

            when (selectedSubTab) {
                0 -> {
                    // Structural Visualization
                    val lastInputs = viewModel.getLastWarehouseInputs
                    SteelFrameVisualizer(
                        inputs = lastInputs,
                        result = finalResult,
                        memberUtilization = if (finalResult != null) finalResult.totalWeight / 20000.0 else 0.0,
                        isSafe = finalResult?.safetyStatus ?: true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                1 -> {
                    // Bill of Materials
                    val lastInputs = viewModel.getLastWarehouseInputs
                    SteelBOMTable(
                        inputs = lastInputs,
                        result = finalResult,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                2 -> {
                    // Moment Frame Analysis
                    SteelMomentFrameAnalyzer(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    // Shop Drawing
                    val lastInputs = viewModel.getLastWarehouseInputs
                    val sectionName = finalResult?.mainFrame?.columnSection?.sectionName ?: "HEB 300"
                    SteelShopDrawingPreview(
                        sectionName = sectionName,
                        memberLengthMm = lastInputs?.span ?: 20.0 * 1000.0,
                        utilizationRatio = if (finalResult != null) finalResult.totalWeight / 20000.0 else 0.0,
                        isSafe = finalResult?.safetyStatus ?: true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        SnackbarHost(snackbarHostState)
    }
}

@Composable
fun SteelResultCard(res: SteelMemberResult) {
    var showFormulas by remember { mutableStateOf(false) }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                AppText(stringResource(R.string.steel_detailed_design_results), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
    AppText(
        text = formula,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}


@Composable
fun SteelModuleSectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun SteelInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { AppText(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label, style = MaterialTheme.typography.bodyMedium)
        AppText(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun UtilizationBar(label: String, utilization: Double) {
    val pct = utilization.coerceIn(0.0, 5.0).toFloat()
    val barColor = when {
        utilization < 0.85f -> Color(0xFF43A047)
        utilization <= 1.0f -> Color(0xFFFFA000)
        else -> Color(0xFFE53935)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("$label — ${stringResource(R.string.steel_utilization_ratio)}", fontSize = 12.sp)
            AppText("${String.format("%.0f", utilization * 100)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(pct / 5f)
                    .height(6.dp)
                    .background(barColor, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
fun PropertyLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        AppText(label, fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.weight(1f))
        AppText(value, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

fun Color.toArgbInt(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}

// ============================================================================
// STAGE 1: ENHANCED STRUCTURAL VISUALIZATION (Front / Side / Plan / 3D)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelFrameVisualizer(
    inputs: SteelWarehouseInputs?,
    result: SteelWarehouseAnalysisResult?,
    memberUtilization: Double = 0.0,
    isSafe: Boolean = true,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableIntStateOf(0) }
    val viewModes = listOf(
        stringResource(R.string.steel_view_front_elevation),
        stringResource(R.string.steel_view_side_elevation),
        stringResource(R.string.steel_view_plan),
        stringResource(R.string.steel_view_3d_perspective)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        AppText(stringResource(R.string.steel_structural_visualization), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        ScrollableTabRow(selectedTabIndex = viewMode, edgePadding = 16.dp) {
            viewModes.forEachIndexed { index, title ->
                Tab(
                    selected = viewMode == index,
                    onClick = { viewMode = index },
                    text = { AppText(title, fontSize = 10.sp, role = AppTextRole.Caption) }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().height(400.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
        ) {
            ComposeCanvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f

                val inputs = inputs ?: SteelWarehouseInputs(
                    span = 20.0, length = 60.0, eaveHeight = 6.0, ridgeHeight = 9.0,
                    baySpacing = 6.0, slope = 0.17, deadLoad = 0.25, liveLoad = 0.60, windLoad = 150.0
                )
                val spanPx = ((inputs.span * 8.0).toFloat()).coerceIn(100f, w - 100f)
                val heightPx = ((inputs.eaveHeight * 25.0).toFloat()).coerceIn(80f, h - 100f)
                val ridgePx = heightPx + (spanPx / 2f * inputs.slope.toFloat() * 25f).coerceAtLeast(0f)

                val urColor = if (isSafe) Color(0xFF2E7D32) else Color.Red

                when (viewMode) {
                    0 -> { // Front Elevation
                        // Ground line
                        drawLine(Color(0xFF4A5568), Offset(50f, cy + 80f), Offset(w - 50f, cy + 80f), 3f)

                        // Left column
                        drawLine(urColor, Offset(cx - spanPx / 2, cy + 80f), Offset(cx - spanPx / 2, cy + 80f - heightPx), 6f)
                        // Right column
                        drawLine(urColor, Offset(cx + spanPx / 2, cy + 80f), Offset(cx + spanPx / 2, cy + 80f - heightPx), 6f)

                        // Rafters
                        drawLine(urColor, Offset(cx - spanPx / 2, cy + 80f - heightPx), Offset(cx, cy + 80f - ridgePx), 5f)
                        drawLine(urColor, Offset(cx + spanPx / 2, cy + 80f - heightPx), Offset(cx, cy + 80f - ridgePx), 5f)

                        // Dimension lines
                        drawLine(Color.Gray, Offset(cx - spanPx / 2 - 30f, cy + 80f + 30f), Offset(cx + spanPx / 2 + 30f, cy + 80f + 30f), 2f)
                        drawText(text = "${inputs.span.toInt()}m", offset = Offset(cx - 20f, cy + 80f + 35f), fontSize = 12f, color = Color.Gray)

                        drawText(text = "FRONT ELEVATION", offset = Offset(cx - 60f, 40f), fontSize = 16f, fontWeight = FontWeight.Bold, color = urColor)
                        drawText(text = "Eave: ${inputs.eaveHeight.toInt()}m", offset = Offset(cx - spanPx / 2 - 70f, cy + 80f - heightPx / 2), fontSize = 11f, color = Color.Gray)
                    }

                    1 -> { // Side Elevation
                        // Ground line
                        drawLine(Color(0xFF4A5568), Offset(50f, cy + 80f), Offset(w - 50f, cy + 80f), 3f)

                        // Columns along length
                        val bayCount = (inputs.length / inputs.baySpacing).toInt().coerceAtLeast(1)
                        val bayPx = (w - 200f) / bayCount.coerceAtLeast(1)

                        repeat(bayCount + 1) { i ->
                            val x = 100f + i * bayPx
                            drawLine(urColor, Offset(x, cy + 80f), Offset(x, cy + 80f - heightPx), 5f)
                        }

                        // Rofline (simplified - flat or slight slope)
                        val roofH = heightPx * 0.9f
                        repeat(bayCount) { i ->
                            val x1 = 100f + i * bayPx
                            val x2 = 100f + (i + 1) * bayPx
                            drawLine(urColor, Offset(x1, cy + 80f - heightPx), Offset(x2, cy + 80f - roofH), 4f)
                        }

                        drawText(text = "SIDE ELEVATION", offset = Offset(cx - 60f, 40f), fontSize = 16f, fontWeight = FontWeight.Bold, color = urColor)
                        drawText(text = "Length: ${inputs.length.toInt()}m  Bays: $bayCount", offset = Offset(cx - 80f, cy + 80f + 40f), fontSize = 12f, color = Color.Gray)
                    }

                    2 -> { // Plan View
                        // Building footprint
                        val planW = ((inputs.length * 6.0).toFloat()).coerceIn(100f, w - 100f)
                        val planD = ((inputs.span * 6.0).toFloat()).coerceIn(80f, h - 100f)
                        drawRoundRect(Color.Gray.copy(alpha = 0.2f), Offset(cx - planW / 2, cy - planD / 2), Size(planW, planD), cornerRadius = CornerRadius(4f))

                        // Grid lines (bays)
                        val bayCount = (inputs.length / inputs.baySpacing).toInt().coerceAtLeast(1)
                        val bayPx = planW / bayCount.coerceAtLeast(1).toFloat()
                        repeat(bayCount - 1) { i ->
                            val x = cx - planW / 2 + (i + 1) * bayPx
                            drawLine(Color.Gray.copy(alpha = 0.4f), Offset(x, cy - planD / 2), Offset(x, cy + planD / 2), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
                        }

                        // Columns (small squares)
                        repeat(bayCount + 1) { i ->
                            val x = cx - planW / 2 + i * bayPx
                            drawRect(urColor, Offset(x - 6f, cy - planD / 2 - 6f), Size(12f, 12f))
                            drawRect(urColor, Offset(x - 6f, cy + planD / 2 - 6f), Size(12f, 12f))
                        }

                        drawText(text = "PLAN VIEW (Top)", offset = Offset(cx - 70f, 40f), fontSize = 16f, fontWeight = FontWeight.Bold, color = urColor)
                        drawText(text = "${inputs.length.toInt()}m × ${inputs.span.toInt()}m", offset = Offset(cx - 40f, cy + planD / 2 + 25f), fontSize = 12f, color = Color.Gray)
                    }

                    else -> { // 3D Perspective (simplified wireframe)
                        // Draw a simple 3D box representation
                        val boxW = ((inputs.length * 5.0).toFloat()).coerceIn(120f, w - 100f)
                        val boxD = ((inputs.span * 5.0).toFloat()).coerceIn(100f, h - 120f)
                        val boxH = heightPx * 0.8f

                        val offsetX = 40f
                        val offsetY = 60f

                        // Back face
                        drawRoundRect(Color.Gray.copy(alpha = 0.15f), Offset(cx - boxW / 2 + offsetX, cy - boxH / 2 - offsetY), Size(boxW, boxH), cornerRadius = CornerRadius(4f))
                        // Front face (offset)
                        drawRoundRect(Color.Gray.copy(alpha = 0.3f), Offset(cx - boxW / 2, cy - boxH / 2), Size(boxW, boxH), cornerRadius = CornerRadius(4f))

                        // Connecting lines (depth)
                        drawLine(Color.Gray.copy(alpha = 0.5f), Offset(cx - boxW / 2 + offsetX, cy - boxH / 2 - offsetY), Offset(cx - boxW / 2, cy - boxH / 2), 2f)
                        drawLine(Color.Gray.copy(alpha = 0.5f), Offset(cx + boxW / 2 + offsetX, cy - boxH / 2 - offsetY), Offset(cx + boxW / 2, cy - boxH / 2), 2f)
                        drawLine(Color.Gray.copy(alpha = 0.5f), Offset(cx - boxW / 2 + offsetX, cy + boxH / 2 - offsetY), Offset(cx - boxW / 2, cy + boxH / 2), 2f)
                        drawLine(Color.Gray.copy(alpha = 0.5f), Offset(cx + boxW / 2 + offsetX, cy + boxH / 2 - offsetY), Offset(cx + boxW / 2, cy + boxH / 2), 2f)

                        // Roof slope lines
                        drawLine(urColor, Offset(cx - boxW / 2, cy - boxH / 2), Offset(cx, cy - boxH / 2 - 30f), 3f)
                        drawLine(urColor, Offset(cx + boxW / 2, cy - boxH / 2), Offset(cx, cy - boxH / 2 - 30f), 3f)

                        drawText(text = "3D PERSPECTIVE (Wireframe)", offset = Offset(cx - 90f, 40f), fontSize = 14f, fontWeight = FontWeight.Bold, color = urColor)
                    }
                }

                // Utilization indicator
                drawRoundRect(
                    if (memberUtilization <= 1.0) Color(0xFF2E7D32).copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f),
                    Offset(16f, h - 40f),
                    Size(120f, 20f),
                    cornerRadius = CornerRadius(10f)
                )
                drawText(
                    text = "Utilization: ${(memberUtilization * 100).toInt()}%",
                    offset = Offset(24f, h - 26f),
                    fontSize = 12f,
                    color = if (memberUtilization <= 1.0) Color(0xFF2E7D32) else Color.Red
                )
            }
        }
    }
}

// ============================================================================
// STAGE 1: ENHANCED CONNECTION DETAIL VISUALIZER
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelConnectionDetailVisualizer(
    boltRes: BoltDesignResult?,
    weldRes: WeldDesignResult?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppText(stringResource(R.string.steel_connection_details), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (boltRes != null) {
                ConnectionDetailPanel(
                    title = stringResource(R.string.steel_bolted_connection),
                    icon = Icons.Default.Build,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                    items = listOf(
                        stringResource(R.string.steel_bolt_dia) to "M${boltRes.boltDiameter.toInt()}",
                        stringResource(R.string.steel_bolt_count) to "${boltRes.numberOfBolts}",
                        stringResource(R.string.steel_shear_capacity_label) to "${String.format("%.1f", boltRes.shearCapacity)} kN",
                        stringResource(R.string.steel_bearing_capacity) to "${String.format("%.1f", boltRes.bearingCapacity)} kN",
                        stringResource(R.string.steel_tension_capacity) to "${String.format("%.1f", boltRes.tensionCapacity)} kN",
                        stringResource(R.string.steel_utilization_ratio) to "${(boltRes.utilizationRatio * 100).toInt()}%"
                    ),
                    isSafe = boltRes.isSafe
                )
            }

            if (weldRes != null) {
                ConnectionDetailPanel(
                    title = stringResource(R.string.steel_welded_connection),
                    icon = Icons.Default.Build,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    items = listOf(
                        stringResource(R.string.steel_weld_size_label) to "${weldRes.weldSize.toInt()} mm",
                        stringResource(R.string.steel_weld_throat_area) to "${String.format("%.1f", weldRes.throatArea)} mm²",
                        stringResource(R.string.steel_weld_capacity) to "${String.format("%.1f", weldRes.capacity)} kN",
                        stringResource(R.string.steel_weld_electrode) to weldRes.electrodeType.displayName,
                        stringResource(R.string.steel_utilization_ratio) to "${(weldRes.utilizationRatio * 100).toInt()}%"
                    ),
                    isSafe = weldRes.isSafe
                )
            }
        }

        // Visual connection drawing
        if (boltRes != null || weldRes != null) {
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
            ) {
                ComposeCanvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f

                    // Member plates
                    drawRect(Color(0xFF4A5568), Offset(cx - 140f, cy - 60f), Size(280f, 40f)) // Top plate
                    drawRect(Color(0xFF4A5568), Offset(cx - 140f, cy + 20f), Size(280f, 40f)) // Bottom plate

                    boltRes?.let { res ->
                        val r = (res.boltDiameter.toFloat() / 3f).coerceIn(4f, 10f)
                        val numRows = if (res.boltPattern == BoltPattern.DOUBLE_ROW) 2 else 1
                        val boltsPerRow = res.numberOfBolts / numRows.coerceAtLeast(1)
                        val spacing = 160f / max(boltsPerRow - 1, 1)

                        for (row in 0 until numRows) {
                            val y = cy + (if (row == 0) -25f else 25f)
                            for (i in 0 until boltsPerRow) {
                                val x = cx - 80f + i * spacing
                                drawCircle(Color(0xFFF39C12), r, Offset(x, y))
                                drawCircle(Color.Black.copy(alpha = 0.3f), r * 0.6f, Offset(x, y)) // Bolt head
                            }
                        }

                        // Weld lines
                        drawLine(Color(0xFFE74C3C), Offset(cx - 140f, cy - 58f), Offset(cx + 140f, cy - 58f), 3f)
                        drawLine(Color(0xFFE74C3C), Offset(cx - 140f, cy + 58f), Offset(cx + 140f, cy + 58f), 3f)
                    }

                    weldRes?.let { res ->
                        val s = res.weldSize.toFloat() / 2f
                        drawLine(Color(0xFFE74C3C), Offset(cx - 120f, cy - 20f), Offset(cx + 120f, cy - 20f), s * 2f)
                        drawLine(Color(0xFFE74C3C), Offset(cx - 120f, cy + 20f), Offset(cx + 120f, cy + 20f), s * 2f)
                        drawLine(Color(0xFFE74C3C), Offset(cx - 120f, cy - 20f), Offset(cx - 120f, cy + 20f), s * 2f)
                        drawLine(Color(0xFFE74C3C), Offset(cx + 120f, cy - 20f), Offset(cx + 120f, cy + 20f), s * 2f)
                    }

                    drawText(text = "CONNECTION DETAIL", offset = Offset(cx - 60f, 30f), fontSize = 14f, fontWeight = FontWeight.Bold, color = Color(0xFFE0E0E0))
                }
            }
        }
    }
}

@Composable
fun ConnectionDetailPanel(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    items: List<Pair<String, String>>,
    isSafe: Boolean
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                AppText(title, fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
            }
            Divider()
            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(label, fontSize = 11.sp, color = Color(0xFF94A3B8))
                    AppText(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            AppText(
                if (isSafe) stringResource(R.string.steel_status_safe) else stringResource(R.string.steel_status_unsafe),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSafe) Color(0xFF2E7D32) else Color.Red
            )
        }
    }
}

// ============================================================================
// STAGE 2: STEEL BILL OF MATERIALS (BOM) TABLE
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelBOMTable(
    inputs: SteelWarehouseInputs?,
    result: SteelWarehouseAnalysisResult?,
    modifier: Modifier = Modifier
) {
    val inputs = inputs ?: SteelWarehouseInputs(
        span = 20.0, length = 60.0, eaveHeight = 6.0, ridgeHeight = 9.0,
        baySpacing = 6.0, slope = 0.17, deadLoad = 0.25, liveLoad = 0.60, windLoad = 150.0
    )
    val result = result ?: SteelWarehouseAnalysisResult(
        totalWeight = 12500.0,
        weightPerM2 = 105.0,
        safetyStatus = true,
        mainFrame = MainFrameResult(
            columnSection = SteelSectionType.ISection(h = 300.0, bf = 300.0, tf = 15.0, tw = 10.0, grade = SteelGrade.ST37, customName = "HEB 300"),
            rafterSection = SteelSectionType.ISection(h = 300.0, bf = 150.0, tf = 10.7, tw = 7.1, grade = SteelGrade.ST37, customName = "IPE 300"),
            maxMoment = 0.0,
            maxShear = 0.0,
            maxAxial = 0.0,
            maxDeflection = 0.0,
            allowableDeflection = 0.0,
            isSafe = true,
            basePlateThickness = 25.0,
            basePlateBoltsCount = 4
        ),
        secondaryMembers = SecondaryMembersResult(
            purlinSection = SteelSectionType.ISection(h = 140.0, bf = 73.0, tf = 6.8, tw = 4.9, grade = SteelGrade.ST37, customName = "IPE 140"),
            girtSection = SteelSectionType.ISection(h = 120.0, bf = 64.0, tf = 6.2, tw = 4.7, grade = SteelGrade.ST37, customName = "IPE 120"),
            bracingSection = SteelSectionType.ISection(h = 50.0, bf = 50.0, tf = 5.0, tw = 5.0, grade = SteelGrade.ST37, customName = "L 50x5"),
            purlinCount = 10,
            isSafe = true
        ),
        connections = emptyList(),
        totalCladdingArea = 1000.0,
        resultsByCode = "ECP",
        recommendations = emptyList(),
        materialTakeoff = emptyMap(),
        calculationTrace = emptyList()
    )

    val totalWeight = result.totalWeight
    val columnsWeight = totalWeight * 0.35
    val raftersWeight = totalWeight * 0.30
    val purlinsWeight = totalWeight * 0.20
    val girtsWeight = totalWeight * 0.10
    val platesWeight = totalWeight * 0.05

    Column(modifier = modifier.fillMaxWidth()) {
        AppText(stringResource(R.string.steel_bill_of_materials), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        // A LazyColumn here would sit inside the verticalScroll Column of
        // SteelStructureAndBOMTab and crash ("infinity maximum height
        // constraints"). The BOM is a short fixed list, so a plain Column is enough.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppText(stringResource(R.string.steel_bom_item), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                AppText(stringResource(R.string.steel_bom_quantity), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                AppText(stringResource(R.string.steel_bom_weight), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                AppText(stringResource(R.string.steel_bom_section), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Divider()

            BOMRow(
                item = stringResource(R.string.steel_bom_columns),
                quantity = "${(inputs.length / inputs.baySpacing).toInt() + 1}",
                weight = "${String.format("%.1f", columnsWeight)} kg",
                section = result.mainFrame.columnSection.sectionName
            )

            BOMRow(
                item = stringResource(R.string.steel_bom_rafters),
                quantity = "${(inputs.length / inputs.baySpacing).toInt() + 1}",
                weight = "${String.format("%.1f", raftersWeight)} kg",
                section = result.mainFrame.rafterSection.sectionName
            )

            BOMRow(
                item = stringResource(R.string.steel_bom_purlins),
                quantity = "${(inputs.span / 1.5).toInt() * ((inputs.length / inputs.baySpacing).toInt() + 1)}",
                weight = "${String.format("%.1f", purlinsWeight)} kg",
                section = result.secondaryMembers.purlinSection.sectionName
            )

            BOMRow(
                item = stringResource(R.string.steel_bom_girts),
                quantity = "${(inputs.eaveHeight / 1.5).toInt() * 2 * ((inputs.length / inputs.baySpacing).toInt() + 1)}",
                weight = "${String.format("%.1f", girtsWeight)} kg",
                section = result.secondaryMembers.girtSection.sectionName
            )

            BOMRow(
                item = stringResource(R.string.steel_bom_plates),
                quantity = "${result.mainFrame.basePlateBoltsCount * 4}",
                weight = "${String.format("%.1f", platesWeight)} kg",
                section = "${result.mainFrame.basePlateThickness.toInt()} mm"
            )

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(stringResource(R.string.steel_bom_total), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                AppText("${String.format("%.1f", totalWeight)} kg", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF1E3A8A))
            }
        }
    }
}

@Composable
fun BOMRow(item: String, quantity: String, weight: String, section: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            AppText(item, fontSize = 13.sp, color = Color(0xFF1E293B))
        }
        Column {
            AppText(quantity, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
        }
        Column {
            AppText(weight, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
        }
        Column {
            AppText(section, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
        }
    }
}

// ============================================================================
// STAGE 3: MOMENT RESISTING FRAME ANALYSIS
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelMomentFrameAnalyzer(
    spanM: Double = 20.0,
    heightM: Double = 6.0,
    storyCount: Int = 2,
    lateralLoadKnM2: Double = 1.5,
    modifier: Modifier = Modifier
) {
    val storyHeightPx = 120f
    val spanPx = 300f
    val cx = 200f

    // Simplified moment distribution (triangular for wind)
    val moments = mutableListOf<Double>()
    val shears = mutableListOf<Double>()
    var cumulativeShear = 0.0
    for (i in storyCount downTo 1) {
        val storyLoad = lateralLoadKnM2 * spanM * heightM
        cumulativeShear += storyLoad
        shears.add(cumulativeShear)
        moments.add(cumulativeShear * heightM * 0.5)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AppText(stringResource(R.string.steel_moment_frame_analysis), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SteelInputField(spanM.toString(), stringResource(R.string.steel_span_m), {}, Modifier.weight(1f))
            SteelInputField(heightM.toString(), stringResource(R.string.steel_height_m), {}, Modifier.weight(1f))
            SteelInputField(storyCount.toString(), " Floors", {}, Modifier.weight(1f))
            SteelInputField(lateralLoadKnM2.toString(), " Wind (kN/m²)", {}, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Moment diagram visualization
        Card(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            ComposeCanvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val w = size.width
                val h = size.height
                val pad = 60f
                val plotW = w - 2 * pad
                val plotH = h - 2 * pad

                // Ground line
                drawLine(Color(0xFF4A5568), Offset(pad, h - pad), Offset(w - pad, h - pad), 3f)

                // Frame columns
                drawLine(Color(0xFF2196F3), Offset(pad + 40f, h - pad), Offset(pad + 40f, pad), 5f)
                drawLine(Color(0xFF2196F3), Offset(pad + 40f + spanPx, h - pad), Offset(pad + 40f + spanPx, pad), 5f)

                // Beams
                repeat(storyCount) { i ->
                    val y = h - pad - (i + 1) * storyHeightPx
                    drawLine(Color(0xFF2196F3), Offset(pad + 40f, y), Offset(pad + 40f + spanPx, y), 4f)
                }

                // Moment diagram (simplified - parabolic curve)
                val maxMoment = moments.maxOrNull() ?: 0.0
                val momentColor = Color(0xFFFFA726)
                repeat(storyCount) { i ->
                    val y = h - pad - (i + 1) * storyHeightPx
                    val momentHeight = if (maxMoment > 0.0) (moments[i] / maxMoment * 60.0).toFloat() else 0f
                    drawArc(
                        color = momentColor,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(pad + 40f - momentHeight, y - momentHeight),
                        size = Size(momentHeight * 2, momentHeight * 2),
                        style = Stroke(3f)
                    )
                }

                // Labels
                drawText(text = "MOMENT DIAGRAM (kN·m)", offset = Offset(pad + 20f, 30f), fontSize = 14f, fontWeight = FontWeight.Bold, color = Color(0xFFFFA726))
                drawText(text = "Max: ${String.format("%.1f", maxMoment)} kN·m", offset = Offset(pad + 20f, h - pad + 20f), fontSize = 12f, color = Color.Gray)
                drawText(text = "${spanM.toInt()}m", offset = Offset(pad + 40f + spanPx / 2 - 15f, h - pad + 15f), fontSize = 11f, color = Color.Gray)

                for (i in 0 until storyCount) {
                    drawText(text = "${(i + 1)}", offset = Offset(pad + 10f, h - pad - (i + 1) * storyHeightPx - 8f), fontSize = 10f, color = Color(0xFF94A3B8))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Results summary
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            ResultRow(stringResource(R.string.steel_max_moment), "${String.format("%.1f", moments.maxOrNull() ?: 0.0)} kN·m")
            ResultRow(stringResource(R.string.steel_max_shear), "${String.format("%.1f", shears.maxOrNull() ?: 0.0)} kN")
            ResultRow(stringResource(R.string.steel_story_count), storyCount.toString())
            ResultRow(stringResource(R.string.steel_frame_type), stringResource(R.string.steel_moment_resisting_frame))
        }
    }
}

// ============================================================================
// Stage 4: Shop Drawing Export Preview (placeholder for DXF/CNC)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelShopDrawingPreview(
    sectionName: String,
    memberLengthMm: Double,
    utilizationRatio: Double,
    isSafe: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppText(stringResource(R.string.steel_shop_drawing), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth().height(320.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            ComposeCanvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f

                // Member profile (I-section schematic)
                val urColor = if (isSafe) Color(0xFF2E7D32) else Color.Red

                // Top flange
                drawRect(urColor, Offset(cx - 90f, cy - 100f), Size(180f, 12f))
                // Web
                drawRect(urColor, Offset(cx - 8f, cy - 94f), Size(16f, 188f))
                // Bottom flange
                drawRect(urColor, Offset(cx - 90f, cy + 88f), Size(180f, 12f))

                // Dimensions
                drawLine(Color.Gray, Offset(cx - 100f, cy - 110f), Offset(cx + 100f, cy - 110f), 1f)
                drawText(text = sectionName, offset = Offset(cx - 40f, cy - 115f), fontSize = 12f, color = Color.Gray)

                drawLine(Color.Gray, Offset(cx - 100f, cy + 100f), Offset(cx + 100f, cy + 100f), 1f)
                drawText(text = "${memberLengthMm.toInt()} mm", offset = Offset(cx - 40f, cy + 105f), fontSize = 12f, color = Color.Gray)

                // Bolt holes (simulated)
                drawCircle(Color(0xFF374151), 5f, Offset(cx - 30f, cy - 60f))
                drawCircle(Color(0xFF374151), 5f, Offset(cx + 30f, cy - 60f))
                drawCircle(Color(0xFF374151), 5f, Offset(cx - 30f, cy + 60f))
                drawCircle(Color(0xFF374151), 5f, Offset(cx + 30f, cy + 60f))

                // Title block
                drawRoundRect(Color(0xFF0F172A), Offset(16f, h - 40f), Size(w - 32f, 24f), cornerRadius = CornerRadius(4f))
                drawText(text = "SHOP DRAWING - $sectionName", offset = Offset(24f, h - 26f), fontSize = 11f, color = Color.White)
                drawText(text = "Utilization: ${(utilizationRatio * 100).toInt()}%", offset = Offset(w - 120f, h - 26f), fontSize = 11f, color = if (isSafe) Color(0xFF2E7D32) else Color.Red)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppButton(
                label = stringResource(R.string.steel_export_dxf),
                onClick = {},
                modifier = Modifier.weight(0.5f),
                containerOverride = MaterialTheme.colorScheme.secondary,
                contentOverride = onColorFor(MaterialTheme.colorScheme.secondary),
                enabled = false
            )
            AppButton(
                label = stringResource(R.string.steel_export_cnc),
                onClick = {},
                modifier = Modifier.weight(0.5f),
                enabled = false
            )
        }
    }
}

fun DrawScope.drawText(
    text: String,
    offset: Offset,
    fontSize: Float = 12f,
    color: Color = Color.White,
    fontWeight: FontWeight = FontWeight.Normal
) {
    val paint = android.graphics.Paint().apply {
        textSize = fontSize * density
        this.color = color.toArgb()
        isAntiAlias = true
        if (fontWeight == FontWeight.Bold) {
            isFakeBoldText = true
        }
    }
    drawContext.canvas.nativeCanvas.drawText(text, offset.x, offset.y, paint)
}
