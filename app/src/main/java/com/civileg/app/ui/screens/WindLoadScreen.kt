package com.civileg.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.utils.*
import com.civileg.app.viewmodel.WindLoadViewModel
import com.civileg.app.viewmodel.WindPreset

// ============================================================
// Color palette
// ============================================================

private val WindArrowColor = Color(0xFF42A5F5)
private val PressurePositive = Color(0xFFE53935)
private val PressureNegative = Color(0xFF1565C0)
private val BuildingFill = Color(0xFFECEFF1)
private val BuildingStroke = Color(0xFF37474F)
private val GroundColor = Color(0xFF8D6E63)
private val AccentGreen = Color(0xFF43A047)
private val AccentOrange = Color(0xFFFF7043)
private val LightBg = Color(0xFFF5F5F5)

// ============================================================
// Main screen
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WindLoadScreen(
    viewModel: WindLoadViewModel = hiltViewModel()
) {
    val result by viewModel.result.collectAsState()
    val isCalculating by viewModel.isCalculating.collectAsState()
    val selectedTerrain by viewModel.terrainCategory.collectAsState()
    val selectedShape by viewModel.buildingShape.collectAsState()
    val selectedRoofType by viewModel.roofType.collectAsState()

    // Local text-field state
    var basicWindSpeed by remember { mutableStateOf(viewModel.basicWindSpeed.value ?: "30.0") }
    var buildingHeight by remember { mutableStateOf(viewModel.buildingHeight.value ?: "20.0") }
    var buildingWidth by remember { mutableStateOf(viewModel.buildingWidth.value ?: "15.0") }
    var buildingDepth by remember { mutableStateOf(viewModel.buildingDepth.value ?: "10.0") }
    var roofSlope by remember { mutableStateOf(viewModel.roofSlope.value ?: "0.0") }
    var importanceFactor by remember { mutableStateOf(viewModel.importanceFactor.value ?: "1.0") }
    var topographyFactor by remember { mutableStateOf(viewModel.topographyFactor.value ?: "1.0") }
    var numberOfFloors by remember { mutableStateOf(viewModel.numberOfFloors.value ?: "5") }
    var naturalFrequency by remember { mutableStateOf(viewModel.naturalFrequency.value ?: "1.0") }
    var dampingRatio by remember { mutableStateOf(viewModel.dampingRatio.value ?: "0.02") }
    var hasOpenings by remember { mutableStateOf(viewModel.openingsInWindward.value ?: false) }
    var isFlexible by remember { mutableStateOf(viewModel.isFlexibleStructure.value ?: false) }

    val syncToVm = {
        viewModel.basicWindSpeed.postValue(basicWindSpeed)
        viewModel.buildingHeight.postValue(buildingHeight)
        viewModel.buildingWidth.postValue(buildingWidth)
        viewModel.buildingDepth.postValue(buildingDepth)
        viewModel.roofSlope.postValue(roofSlope)
        viewModel.importanceFactor.postValue(importanceFactor)
        viewModel.topographyFactor.postValue(topographyFactor)
        viewModel.numberOfFloors.postValue(numberOfFloors)
        viewModel.naturalFrequency.postValue(naturalFrequency)
        viewModel.dampingRatio.postValue(dampingRatio)
        viewModel.openingsInWindward.postValue(hasOpenings)
        viewModel.isFlexibleStructure.postValue(isFlexible)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wind Load Analysis", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = {
                        viewModel.resetToDefaults()
                        basicWindSpeed = "30.0"; buildingHeight = "20.0"; buildingWidth = "15.0"
                        buildingDepth = "10.0"; roofSlope = "0.0"; importanceFactor = "1.0"
                        topographyFactor = "1.0"; numberOfFloors = "5"; naturalFrequency = "1.0"
                        dampingRatio = "0.02"; hasOpenings = false; isFlexible = false
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---- Presets ----
            PresetRow(onPreset = { viewModel.applyPreset(it); syncToVm() })

            // ---- Wind & terrain ----
            SectionCard(title = "Wind & Terrain") {
                LabeledTextField("Basic Wind Speed (m/s)", basicWindSpeed, { basicWindSpeed = it; syncToVm() })
                Spacer(Modifier.height(6.dp))
                Text("Terrain Category", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TerrainCategory.entries.forEach { tc ->
                        FilterChip(
                            selected = selectedTerrain == tc,
                            onClick = { viewModel.onTerrainSelected(tc) },
                            label = { Text(tc.label, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // ---- Building geometry ----
            SectionCard(title = "Building Geometry") {
                Text("Building Shape", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BuildingShape.entries.forEach { bs ->
                        FilterChip(
                            selected = selectedShape == bs,
                            onClick = { viewModel.onShapeSelected(bs) },
                            label = { Text(bs.label, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = buildingHeight, onValueChange = { buildingHeight = it; syncToVm() },
                        label = { Text("Height (m)") }, modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    )
                    OutlinedTextField(
                        value = buildingWidth, onValueChange = { buildingWidth = it; syncToVm() },
                        label = { Text("Width (m)") }, modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    )
                    OutlinedTextField(
                        value = buildingDepth, onValueChange = { buildingDepth = it; syncToVm() },
                        label = { Text("Depth (m)") }, modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = numberOfFloors, onValueChange = { numberOfFloors = it; syncToVm() },
                        label = { Text("Floors") }, modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    )
                    OutlinedTextField(
                        value = importanceFactor, onValueChange = { importanceFactor = it; syncToVm() },
                        label = { Text("k₁ (Importance)") }, modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    )
                    OutlinedTextField(
                        value = topographyFactor, onValueChange = { topographyFactor = it; syncToVm() },
                        label = { Text("k₃ (Topo)") }, modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    )
                }
            }

            // ---- Roof & openings ----
            SectionCard(title = "Roof & Openings") {
                Text("Roof Type", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RoofType.entries.forEach { rt ->
                        FilterChip(
                            selected = selectedRoofType == rt,
                            onClick = { viewModel.onRoofTypeSelected(rt); roofSlope = viewModel.roofSlope.value ?: "0.0" },
                            label = { Text(rt.label, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = roofSlope, onValueChange = { roofSlope = it; syncToVm() },
                    label = { Text("Roof Slope (°)") }, modifier = Modifier.fillMaxWidth(0.5f),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = hasOpenings, onCheckedChange = { hasOpenings = it; syncToVm() })
                        Spacer(Modifier.width(6.dp))
                        Text("Windward Openings", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = isFlexible, onCheckedChange = { isFlexible = it; syncToVm() })
                        Spacer(Modifier.width(6.dp))
                        Text("Flexible Structure", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (isFlexible) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = naturalFrequency, onValueChange = { naturalFrequency = it; syncToVm() },
                            label = { Text("Freq (Hz)") }, modifier = Modifier.weight(1f),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        )
                        OutlinedTextField(
                            value = dampingRatio, onValueChange = { dampingRatio = it; syncToVm() },
                            label = { Text("Damping") }, modifier = Modifier.weight(1f),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        )
                    }
                }
            }

            // ---- Calculate button ----
            Button(
                onClick = { syncToVm(); viewModel.calculate() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !isCalculating
            ) {
                if (isCalculating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Calculate Wind Load", fontWeight = FontWeight.SemiBold)
            }

            // ---- Results ----
            result?.let { res ->
                // Summary cards
                SummaryCards(result = res)

                // Pressure distribution table
                PressureTable(pressures = res.pressureDistribution)

                // Canvas elevation
                ElevationCanvas(result = res)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ============================================================
// Reusable components
// ============================================================

@Composable
private fun PresetRow(onPreset: (WindPreset) -> Unit) {
    Column {
        Text("Quick Presets", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WindPreset.entries.forEach { preset ->
                SuggestionChip(
                    onClick = { onPreset(preset) },
                    label = { Text(preset.label, fontSize = 11.sp) },
                    icon = { Icon(Icons.Default.Apartment, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LabeledTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
        singleLine = true
    )
}

@Composable
private fun ResultValueCard(label: String, value: String, unit: String, color: Color) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

// ============================================================
// Summary cards row
// ============================================================

@Composable
private fun SummaryCards(result: WindLoadResult) {
    Text("Results Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ResultValueCard("Design Wind Speed", "%.1f".format(result.designWindSpeed), "m/s", AccentOrange)
        ResultValueCard("Design Pressure", "%.3f".format(result.designWindPressure), "kN/m²", PressureNegative)
        ResultValueCard("Gust Factor (Gf)", "%.2f".format(result.gustFactor), "", MaterialTheme.colorScheme.primary)
        ResultValueCard("Base Shear", "%.1f".format(result.totalBaseShear), "kN", PressurePositive)
        ResultValueCard("OTM", "%.0f".format(result.overturningMoment), "kN·m", Color(0xFF6A1B9A))
    }
    Spacer(Modifier.height(8.dp))
    // Pressure coefficient cards
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ResultValueCard("Windward Pₑ", "%.3f".format(result.externalPressureWindward), "kN/m²", PressurePositive)
        ResultValueCard("Leeward Pₑ", "%.3f".format(result.externalPressureLeeward), "kN/m²", PressureNegative)
        ResultValueCard("Side Pₑ", "%.3f".format(result.externalPressureSide), "kN/m²", PressureNegative)
        ResultValueCard("Roof Pₑ", "%.3f".format(result.externalPressureRoof), "kN/m²", PressureNegative)
        ResultValueCard("Internal Pᵢ", "%.3f".format(result.internalPressure), "kN/m²", Color(0xFFFF8F00))
    }
}

// ============================================================
// Pressure distribution table
// ============================================================

@Composable
private fun PressureTable(pressures: List<WindPressureAtHeight>) {
    Spacer(Modifier.height(6.dp))
    Text("Pressure Distribution by Floor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Flr", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Text("z (m)", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.2f))
                Text("Vz (m/s)", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.2f))
                Text("pz (kN/m²)", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.3f))
                Text("Pnet", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.2f))
            }
            HorizontalDivider()
            pressures.forEachIndexed { idx, p ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${idx + 1}", style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f))
                    Text("%.1f".format(p.height), style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1.2f))
                    Text("%.1f".format(p.velocity), style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1.2f))
                    Text("%.3f".format(p.dynamicPressure), style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1.3f))
                    val netColor = if (p.netPressure >= 0) PressurePositive else PressureNegative
                    Text("%.3f".format(p.netPressure), style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = netColor, fontWeight = FontWeight.Medium), modifier = Modifier.weight(1.2f))
                }
                if (idx < pressures.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
            }
        }
    }
}

// ============================================================
// Canvas: building elevation with wind arrows & pressures
// ============================================================

@Composable
private fun ElevationCanvas(result: WindLoadResult) {
    Spacer(Modifier.height(6.dp))
    Text("Building Elevation — Wind Load Diagram", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .padding(8.dp)
        ) {
            val pressures = result.pressureDistribution
            if (pressures.isEmpty()) return@Canvas

            val totalHeight = pressures.last().height
            val canvasW = size.width
            val canvasH = size.height

            // Margins
            val leftM = 60.dp.toPx()
            val rightM = 60.dp.toPx()
            val topM = 30.dp.toPx()
            val bottomM = 40.dp.toPx()

            val drawW = canvasW - leftM - rightM
            val drawH = canvasH - topM - bottomM

            if (drawW <= 0 || drawH <= 0) return@Canvas

            // Scale: pixels per metre
            val scaleX = drawW / (totalHeight * 0.7)   // building width in drawing
            val scaleY = drawH / (totalHeight * 1.15)  // leave room for arrows above roof
            val scale = minOf(scaleX, scaleY)

            val bldgDrawW = totalHeight * 0.6 * scale  // representative building width in px
            val bldgDrawH = totalHeight * scale

            val bldgLeft = leftM + (drawW - bldgDrawW) / 2
            val bldgRight = bldgLeft + bldgDrawW
            val bldgTop = topM + drawH - bldgDrawH
            val bldgBottom = topM + drawH

            val floorHeightPx = bldgDrawH / pressures.size

            // --- Ground ---
            drawLine(
                color = GroundColor, start = Offset(0f, bldgBottom), end = Offset(canvasW, bldgBottom),
                strokeWidth = 3f
            )
            // Hatching below ground
            for (i in 0..20) {
                val x1 = i * 25f
                drawLine(
                    color = GroundColor.copy(alpha = 0.4f),
                    start = Offset(x1, bldgBottom),
                    end = Offset(x1 + 12f, bldgBottom + 10f),
                    strokeWidth = 1f
                )
            }

            // --- Building outline ---
            drawRect(
                color = BuildingFill,
                topLeft = Offset(bldgLeft, bldgTop),
                size = Size(bldgDrawW, bldgDrawH)
            )
            drawRect(
                color = BuildingStroke,
                topLeft = Offset(bldgLeft, bldgTop),
                size = Size(bldgDrawW, bldgDrawH),
                style = Stroke(width = 2f)
            )

            // --- Floor lines ---
            for (i in 1 until pressures.size) {
                val y = bldgBottom - i * floorHeightPx
                drawLine(
                    color = BuildingStroke.copy(alpha = 0.3f),
                    start = Offset(bldgLeft, y), end = Offset(bldgRight, y),
                    strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )
            }

            // --- Max net pressure for scaling arrows ---
            val maxAbsPressure = pressures.maxOfOrNull { abs(it.netPressure) } ?: 1.0
            val maxArrowLen = 50.dp.toPx()

            // --- Wind arrows (left side – windward) and pressure labels ---
            pressures.forEachIndexed { idx, p ->
                val y = bldgBottom - (idx + 1) * floorHeightPx + floorHeightPx / 2
                val arrowLen = (abs(p.netPressure) / maxAbsPressure) * maxArrowLen

                // Wind direction arrows (pointing right, on the left of building)
                val arrowStartX = bldgLeft - arrowLen - 8.dp.toPx()
                val arrowEndX = bldgLeft - 4.dp.toPx()
                drawWindArrow(arrowStartX, y, arrowEndX, y, WindArrowColor, 2f)

                // Pressure value on the left
                drawContext.canvas.nativeCanvas.drawText(
                    "%.2f".format(p.netPressure),
                    arrowStartX - 2.dp.toPx(), y + 4.sp.toPx(),
                    android.graphics.TextPaint().apply {
                        color = if (p.netPressure >= 0) PressurePositive.toArgb() else PressureNegative.toArgb()
                        textSize = 9.sp.toPx()
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                )

                // Floor number on the right
                drawContext.canvas.nativeCanvas.drawText(
                    "F${idx + 1}",
                    bldgRight + 6.dp.toPx(), y + 4.sp.toPx(),
                    android.graphics.TextPaint().apply {
                        color = BuildingStroke.toArgb()
                        textSize = 9.sp.toPx()
                        textAlign = android.graphics.Paint.Align.LEFT
                    }
                )

                // Height label
                if (idx == pressures.lastIndex) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "H=${p.height}m",
                        bldgLeft + bldgDrawW / 2 - 16.dp.toPx(), bldgTop - 6.dp.toPx(),
                        android.graphics.TextPaint().apply {
                            color = BuildingStroke.toArgb()
                            textSize = 10.sp.toPx()
                            isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }

            // --- Leeward suction arrows (right side, pointing right → away from building) ---
            pressures.forEachIndexed { idx, p ->
                val y = bldgBottom - (idx + 1) * floorHeightPx + floorHeightPx / 2
                val leewardAbs = abs(result.externalPressureLeeward)
                val arrowLen = (leewardAbs / maxAbsPressure) * maxArrowLen * 0.7f
                val arrowStartX = bldgRight + 4.dp.toPx()
                val arrowEndX = bldgRight + 4.dp.toPx() + arrowLen
                drawWindArrow(arrowStartX, y, arrowEndX, y, PressureNegative.copy(alpha = 0.6f), 1.5f)
            }

            // --- Title annotations ---
            drawContext.canvas.nativeCanvas.drawText(
                "WIND →", 8.dp.toPx(), bldgTop - 6.dp.toPx(),
                android.graphics.TextPaint().apply {
                    color = WindArrowColor.toArgb()
                    textSize = 10.sp.toPx()
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.LEFT
                }
            )

            // --- Base shear annotation ---
            drawContext.canvas.nativeCanvas.drawText(
                "V = %.1f kN".format(result.totalBaseShear),
                canvasW / 2, bldgBottom + 20.dp.toPx(),
                android.graphics.TextPaint().apply {
                    color = PressurePositive.toArgb()
                    textSize = 10.sp.toPx()
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }
    }
}

// ============================================================
// Canvas helper: draw arrow
// ============================================================

private fun DrawScope.drawWindArrow(
    x1: Float, y1: Float, x2: Float, y2: Float, color: Color, strokeWidth: Float
) {
    val dx = x2 - x1
    val dy = y2 - y1
    val len = sqrt(dx * dx + dy * dy)
    if (len < 2f) return

    val headLen = 8f
    val angle = atan2(dy, dx)

    // Shaft
    drawLine(color = color, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = strokeWidth)
    // Arrowhead
    drawLine(
        color = color,
        start = Offset(x2, y2),
        end = Offset(x2 - headLen * cos(angle - 0.4f), y2 - headLen * sin(angle - 0.4f)),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(x2, y2),
        end = Offset(x2 - headLen * cos(angle + 0.4f), y2 - headLen * sin(angle + 0.4f)),
        strokeWidth = strokeWidth
    )
}