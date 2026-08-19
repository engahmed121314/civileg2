package com.civileg.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Refresh
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
import com.civileg.app.utils.BearingMethod
import com.civileg.app.utils.SoilBearingResult
import com.civileg.app.utils.SoilType
import com.civileg.app.viewmodel.SoilBearingViewModel

// ============================================================
// Color palette
// ============================================================

private val SoilClay  = Color(0xFF8B6914)
private val SoilSand  = Color(0xFFE8C84A)
private val SoilRock  = Color(0xFF909090)
private val WaterBlue = Color(0xFF64B5F6)
private val FoundationGray = Color(0xFF455A64)
private val GrassGreen = Color(0xFF66BB6A)
private val AccentOrange = Color(0xFFFF7043)
private val SafeGreen = Color(0xFF43A047)
private val UnsafeRed = Color(0xFFE53935)

// ============================================================
// Main screen composable
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoilBearingScreen(
    viewModel: SoilBearingViewModel = hiltViewModel()
) {
    val result by viewModel.result.collectAsState()
    val isCalculating by viewModel.isCalculating.collectAsState()
    val comparisonResults by viewModel.comparisonResults.collectAsState()
    val selectedMethod by viewModel.method.collectAsState()
    val selectedSoilType by viewModel.soilType.collectAsState()

    // Local state for text fields (controlled by ViewModel)
    var foundationWidth by remember { mutableStateOf(viewModel.foundationWidth.value ?: "1.5") }
    var foundationLength by remember { mutableStateOf(viewModel.foundationLength.value ?: "1.5") }
    var foundationDepth by remember { mutableStateOf(viewModel.foundationDepth.value ?: "1.0") }
    var cohesion by remember { mutableStateOf(viewModel.cohesion.value ?: "25.0") }
    var frictionAngle by remember { mutableStateOf(viewModel.frictionAngle.value ?: "30.0") }
    var unitWeight by remember { mutableStateOf(viewModel.unitWeight.value ?: "18.0") }
    var waterTableDepth by remember { mutableStateOf(viewModel.waterTableDepth.value ?: "5.0") }
    var eccentricityX by remember { mutableStateOf(viewModel.eccentricityX.value ?: "0.0") }
    var eccentricityY by remember { mutableStateOf(viewModel.eccentricityY.value ?: "0.0") }
    var loadInclinationX by remember { mutableStateOf(viewModel.loadInclinationX.value ?: "0.0") }
    var loadInclinationY by remember { mutableStateOf(viewModel.loadInclinationY.value ?: "0.0") }
    var safetyFactor by remember { mutableStateOf(viewModel.safetyFactor.value ?: "3.0") }

    // Sync local → ViewModel on change
    val sync: (String) -> (MutableList<String>?) -> Unit = { newVal ->
        { _ ->
            viewModel.foundationWidth.postValue(foundationWidth)
            viewModel.foundationLength.postValue(foundationLength)
            viewModel.foundationDepth.postValue(foundationDepth)
            viewModel.cohesion.postValue(cohesion)
            viewModel.frictionAngle.postValue(frictionAngle)
            viewModel.unitWeight.postValue(unitWeight)
            viewModel.waterTableDepth.postValue(waterTableDepth)
            viewModel.eccentricityX.postValue(eccentricityX)
            viewModel.eccentricityY.postValue(eccentricityY)
            viewModel.loadInclinationX.postValue(loadInclinationX)
            viewModel.loadInclinationY.postValue(loadInclinationY)
            viewModel.safetyFactor.postValue(safetyFactor)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Soil Bearing Capacity", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Method selector chips ----
            MethodChips(
                selectedMethod = selectedMethod,
                onMethodSelected = viewModel::onMethodSelected
            )

            // ---- Soil type dropdown ----
            SoilTypeDropdown(
                selectedSoilType = selectedSoilType,
                onSoilTypeSelected = viewModel::onSoilTypeSelected,
                onPresetApplied = {
                    viewModel.applySoilPreset(it)
                    // Update local state after preset
                    when (it) {
                        SoilType.CLAY -> { cohesion = "25.0"; frictionAngle = "5.0"; unitWeight = "17.0" }
                        SoilType.SAND -> { cohesion = "0.0"; frictionAngle = "35.0"; unitWeight = "19.0" }
                        SoilType.ROCK -> { cohesion = "100.0"; frictionAngle = "45.0"; unitWeight = "24.0" }
                        SoilType.MIXED -> { cohesion = "15.0"; frictionAngle = "20.0"; unitWeight = "18.5" }
                    }
                }
            )

            // ---- Foundation dimensions ----
            SectionCard(title = "Foundation Dimensions") {
                InputRow("Width B (m)", foundationWidth) { foundationWidth = it }
                InputRow("Length L (m)", foundationLength) { foundationLength = it }
                InputRow("Depth Df (m)", foundationDepth) { foundationDepth = it }
            }

            // ---- Soil properties ----
            SectionCard(title = "Soil Properties") {
                InputRow("Cohesion c (kPa)", cohesion) { cohesion = it }
                InputRow("Friction angle φ (°)", frictionAngle) { frictionAngle = it }
                InputRow("Unit weight γ (kN/m³)", unitWeight) { unitWeight = it }
                InputRow("Water table depth (m)", waterTableDepth) { waterTableDepth = it }
            }

            // ---- Load eccentricity & inclination ----
            SectionCard(title = "Load Eccentricity & Inclination") {
                InputRow("Eccentricity ex (m)", eccentricityX) { eccentricityX = it }
                InputRow("Eccentricity ey (m)", eccentricityY) { eccentricityY = it }
                InputRow("Inclination αx (°)", loadInclinationX) { loadInclinationX = it }
                InputRow("Inclination αy (°)", loadInclinationY) { loadInclinationY = it }
            }

            // ---- Safety factor ----
            SectionCard(title = "Safety Factor") {
                InputRow("FOS", safetyFactor) { safetyFactor = it }
            }

            // ---- Action buttons ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        sync("")(null)
                        viewModel.calculate()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isCalculating
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calculate")
                }
                OutlinedButton(
                    onClick = {
                        sync("")(null)
                        viewModel.compareAllMethods()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isCalculating
                ) {
                    Icon(Icons.Default.CompareArrows, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compare All")
                }
                IconButton(onClick = {
                    viewModel.resetToDefaults()
                    foundationWidth = "1.5"; foundationLength = "1.5"; foundationDepth = "1.0"
                    cohesion = "25.0"; frictionAngle = "30.0"; unitWeight = "18.0"
                    waterTableDepth = "5.0"; eccentricityX = "0.0"; eccentricityY = "0.0"
                    loadInclinationX = "0.0"; loadInclinationY = "0.0"; safetyFactor = "3.0"
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                }
            }

            if (isCalculating) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ---- Results ----
            result?.let { res ->
                Spacer(modifier = Modifier.height(8.dp))
                ResultsSummaryCard(result = res)
                FactorsDetailCard(result = res, method = selectedMethod)

                // Cross-section drawing
                Spacer(modifier = Modifier.height(8.dp))
                SectionCard(title = "Foundation Cross-Section") {
                    FoundationCrossSectionCanvas(
                        B = res.effectiveWidth,
                        L = res.effectiveLength,
                        Df = foundationDepth.toDoubleOrNull() ?: 1.0,
                        waterTableDepth = waterTableDepth.toDoubleOrNull() ?: 5.0,
                        soilType = selectedSoilType
                    )
                }
            }

            // ---- Comparison table ----
            if (comparisonResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                ComparisonTable(results = comparisonResults)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ============================================================
// Method chips
// ============================================================

@Composable
private fun MethodChips(
    selectedMethod: BearingMethod,
    onMethodSelected: (BearingMethod) -> Unit
) {
    Column {
        Text(
            "Calculation Method",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            BearingMethod.entries.forEach { method ->
                FilterChip(
                    selected = method == selectedMethod,
                    onClick = { onMethodSelected(method) },
                    label = { Text(method.name) },
                    leadingIcon = if (method == selectedMethod) {
                        { Text("●", color = MaterialTheme.colorScheme.primary) }
                    } else null
                )
            }
        }
    }
}

// ============================================================
// Soil type dropdown
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoilTypeDropdown(
    selectedSoilType: SoilType,
    onSoilTypeSelected: (SoilType) -> Unit,
    onPresetApplied: (SoilType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedSoilType.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Soil Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SoilType.entries.forEach { soil ->
                DropdownMenuItem(
                    text = { Text(soil.name) },
                    onClick = {
                        onSoilTypeSelected(soil)
                        onPresetApplied(soil)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ============================================================
// Reusable section card
// ============================================================

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

// ============================================================
// Input row
// ============================================================

@Composable
private fun InputRow(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.foundation.text.KeyboardType.Decimal
        ),
        singleLine = true,
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp)
    )
}

// ============================================================
// Results summary card
// ============================================================

@Composable
private fun ResultsSummaryCard(result: SoilBearingResult) {
    val statusColor = if (result.isSafe) SafeGreen else UnsafeRed
    val statusText = if (result.isSafe) "✓ SAFE" else "✗ REVIEW REQUIRED"

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (result.isSafe)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider()
            ResultRow("Gross Bearing Capacity (qu)", "%.1f kPa".format(result.grossBearingCapacity))
            ResultRow("Net Bearing Capacity (qnet)", "%.1f kPa".format(result.netBearingCapacity))
            ResultRow(
                "Allowable Bearing Capacity (qall)",
                "%.1f kPa".format(result.allowableBearingCapacity),
                highlight = true
            )
            ResultRow("Est. Settlement", "%.1f mm".format(result.settlement))
            ResultRow("Water Table Correction (rw)", "%.2f".format(result.waterTableCorrection))
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = if (highlight) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            color = if (highlight) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ============================================================
// Factors detail card
// ============================================================

@Composable
private fun FactorsDetailCard(result: SoilBearingResult, method: BearingMethod) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Bearing Capacity Factors (${method.name})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            // N factors
            Text("N Factors", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            ResultRow("Nc", "%.2f".format(result.nc))
            ResultRow("Nq", "%.2f".format(result.nq))
            ResultRow("Nγ", "%.2f".format(result.ngamma))

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Shape factors
            Text("Shape Factors", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            ResultRow("sc", "%.3f".format(result.shapeFactorC))
            ResultRow("sq", "%.3f".format(result.shapeFactorQ))
            ResultRow("sγ", "%.3f".format(result.shapeFactorGamma))

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Depth factors
            Text("Depth Factors", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            ResultRow("dc", "%.3f".format(result.depthFactorC))
            ResultRow("dq", "%.3f".format(result.depthFactorQ))
            ResultRow("dγ", "%.3f".format(result.depthFactorGamma))

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Inclination factors
            Text("Inclination Factors", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            ResultRow("ic", "%.3f".format(result.inclinationFactorC))
            ResultRow("iq", "%.3f".format(result.inclinationFactorQ))
            ResultRow("iγ", "%.3f".format(result.inclinationFactorGamma))

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Effective dimensions
            Text("Effective Dimensions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            ResultRow("B' (effective width)", "%.2f m".format(result.effectiveWidth))
            ResultRow("L' (effective length)", "%.2f m".format(result.effectiveLength))
        }
    }
}

// ============================================================
// Comparison table
// ============================================================

@Composable
private fun ComparisonTable(results: Map<BearingMethod, SoilBearingResult>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                "Method Comparison",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Table header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("", modifier = Modifier.width(100.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                BearingMethod.entries.forEach { method ->
                    Text(
                        method.name,
                        modifier = Modifier.width(90.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider()

            // Rows
            val rows = listOf(
                "qu (kPa)" to { r: SoilBearingResult -> "%.0f".format(r.grossBearingCapacity) },
                "qnet (kPa)" to { r: SoilBearingResult -> "%.0f".format(r.netBearingCapacity) },
                "qall (kPa)" to { r: SoilBearingResult -> "%.0f".format(r.allowableBearingCapacity) },
                "Nc" to { r: SoilBearingResult -> "%.1f".format(r.nc) },
                "Nq" to { r: SoilBearingResult -> "%.1f".format(r.nq) },
                "Nγ" to { r: SoilBearingResult -> "%.1f".format(r.ngamma) },
                "Settle. (mm)" to { r: SoilBearingResult -> "%.1f".format(r.settlement) },
                "Safe?" to { r: SoilBearingResult -> if (r.isSafe) "✓" else "✗" }
            )

            rows.forEach { (label, formatter) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        label,
                        modifier = Modifier.width(100.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    BearingMethod.entries.forEach { method ->
                        val res = results[method]
                        Text(
                            res?.let(formatter) ?: "—",
                            modifier = Modifier.width(90.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (label.startsWith("qall")) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// Foundation cross-section Canvas
// ============================================================

@Composable
private fun FoundationCrossSectionCanvas(
    B: Double,
    L: Double,
    Df: Double,
    waterTableDepth: Double,
    soilType: SoilType
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // Scale: map physical dimensions to canvas pixels
        val maxPhysHeight = maxOf(waterTableDepth, Df + B, 5.0)
        val scale = (h * 0.75f) / maxPhysHeight.toFloat()
        val groundY = h * 0.2f  // ground surface position
        val foundationPixelW = (B * scale).coerceIn(40f, w * 0.7f)
        val dfPixel = (Df * scale).coerceIn(20f, h * 0.4f)
        val baseY = groundY + dfPixel
        val wtPixel = ((waterTableDepth - Df) * scale).coerceAtLeast(0f)
        val waterY = baseY + wtPixel

        // --- Sky ---
        drawRect(color = Color(0xFFE3F2FD), topLeft = Offset(0f, 0f), size = Size(w, groundY))

        // --- Soil layers ---
        val soilColor = when (soilType) {
            SoilType.CLAY  -> SoilClay
            SoilType.SAND  -> SoilSand
            SoilType.ROCK  -> SoilRock
            SoilType.MIXED -> Color(0xFFA1887F)
        }
        drawRect(
            color = soilColor.copy(alpha = 0.35f),
            topLeft = Offset(0f, groundY),
            size = Size(w, h - groundY)
        )

        // --- Water table (if within view) ---
        if (waterTableDepth > Df && waterY < h) {
            // Water layer below water table
            drawRect(
                color = WaterBlue.copy(alpha = 0.3f),
                topLeft = Offset(0f, waterY),
                size = Size(w, h - waterY)
            )
            // Dashed water table line
            drawLine(
                color = WaterBlue,
                start = Offset(0f, waterY),
                end = Offset(w, waterY),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
            )
            // Label
            drawContext.canvas.nativeCanvas.drawText(
                "WT (Dw=${waterTableDepth}m)",
                12f, waterY - 8f,
                android.graphics.Paint().apply {
                    color = WaterBlue.toArgb()
                    textSize = 28f
                    isFakeBoldText = true
                }
            )
        }

        // --- Ground surface line ---
        drawLine(
            color = Color(0xFF388E3C),
            start = Offset(0f, groundY),
            end = Offset(w, groundY),
            strokeWidth = 3f
        )
        // Grass tufts
        for (i in 0..20) {
            val x = i * w / 20f
            drawLine(
                color = GrassGreen,
                start = Offset(x, groundY),
                end = Offset(x - 3f, groundY - 8f),
                strokeWidth = 1.5f
            )
            drawLine(
                color = GrassGreen,
                start = Offset(x, groundY),
                end = Offset(x + 3f, groundY - 8f),
                strokeWidth = 1.5f
            )
        }

        // --- Foundation ---
        val fLeft = cx - foundationPixelW / 2f
        val fRight = cx + foundationPixelW / 2f
        drawRect(
            color = FoundationGray,
            topLeft = Offset(fLeft, baseY - 12f),
            size = Size(foundationPixelW, 12f)
        )
        // Hatching on foundation
        for (i in 0 until (foundationPixelW.toInt() / 10)) {
            val hx = fLeft + i * 10f
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = Offset(hx, baseY - 12f),
                end = Offset(hx + 6f, baseY),
                strokeWidth = 1f
            )
        }

        // --- Depth dimension line ---
        drawDimensionLine(
            x = fRight + 24f,
            y1 = groundY, y2 = baseY,
            label = "Df=${Df}m"
        )

        // --- Width dimension line ---
        drawDimensionLineH(
            y = baseY + 24f,
            x1 = fLeft, x2 = fRight,
            label = "B'=${"%.2f".format(B)}m"
        )

        // --- Pressure arrows (load) ---
        val arrowCount = 5
        for (i in 0 until arrowCount) {
            val ax = fLeft + (i + 0.5f) * foundationPixelW / arrowCount
            drawArrow(
                from = Offset(ax, baseY - 50f),
                to = Offset(ax, baseY - 14f),
                color = AccentOrange,
                strokeWidth = 2f
            )
        }
        drawContext.canvas.nativeCanvas.drawText(
            "Q",
            cx - 10f, baseY - 56f,
            android.graphics.Paint().apply {
                color = AccentOrange.toArgb()
                textSize = 32f
                isFakeBoldText = true
            }
        )

        // --- Failure surface (general shear) ---
        drawFailureSurface(cx, baseY, foundationPixelW, h)

        // --- Soil label ---
        drawContext.canvas.nativeCanvas.drawText(
            soilType.name,
            12f, h - 12f,
            android.graphics.Paint().apply {
                color = soilColor.toArgb()
                textSize = 28f
                isFakeBoldText = true
            }
        )
    }
}

// ---- Drawing helpers ----

private fun DrawScope.drawDimensionLine(x: Float, y1: Float, y2: Float, label: String) {
    drawLine(Color(0xFF424242), Offset(x, y1), Offset(x, y2), strokeWidth = 1.5f)
    drawLine(Color(0xFF424242), Offset(x - 6f, y1), Offset(x + 6f, y1), strokeWidth = 1.5f)
    drawLine(Color(0xFF424242), Offset(x - 6f, y2), Offset(x + 6f, y2), strokeWidth = 1.5f)
    drawContext.canvas.nativeCanvas.drawText(
        label,
        x + 10f, (y1 + y2) / 2f + 6f,
        android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 24f
        }
    )
}

private fun DrawScope.drawDimensionLineH(y: Float, x1: Float, x2: Float, label: String) {
    drawLine(Color(0xFF424242), Offset(x1, y), Offset(x2, y), strokeWidth = 1.5f)
    drawLine(Color(0xFF424242), Offset(x1, y - 6f), Offset(x1, y + 6f), strokeWidth = 1.5f)
    drawLine(Color(0xFF424242), Offset(x2, y - 6f), Offset(x2, y + 6f), strokeWidth = 1.5f)
    val textW = android.graphics.Paint().apply { textSize = 24f }.measureText(label)
    drawContext.canvas.nativeCanvas.drawText(
        label,
        (x1 + x2) / 2f - textW / 2f, y + 22f,
        android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 24f
        }
    )
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, strokeWidth: Float) {
    drawLine(color, from, to, strokeWidth = strokeWidth)
    val angle = kotlin.math.atan2(to.y - from.y, to.x - from.x)
    val headLen = 10f
    val headAngle = 0.5f
    drawLine(
        color, to,
        to - Offset(
            headLen * kotlin.math.cos(angle - headAngle),
            headLen * kotlin.math.sin(angle - headAngle)
        ),
        strokeWidth = strokeWidth
    )
    drawLine(
        color, to,
        to - Offset(
            headLen * kotlin.math.cos(angle + headAngle),
            headLen * kotlin.math.sin(angle + headAngle)
        ),
        strokeWidth = strokeWidth
    )
}

private fun DrawScope.drawFailureSurface(cx: Float, baseY: Float, fWidth: Float, canvasH: Float) {
    val halfW = fWidth / 2f
    // Draw approximate logarithmic spiral / slip lines
    val path = androidx.compose.ui.graphics.Path()
    // Left failure surface
    path.moveTo(cx - halfW, baseY)
    path.cubicTo(
        cx - halfW - halfW * 0.3f, baseY + 40f,
        cx - halfW - halfW * 0.8f, baseY + 80f,
        cx - halfW - halfW * 1.2f, baseY + 120f
    )
    // Right failure surface
    path.moveTo(cx + halfW, baseY)
    path.cubicTo(
        cx + halfW + halfW * 0.3f, baseY + 40f,
        cx + halfW + halfW * 0.8f, baseY + 80f,
        cx + halfW + halfW * 1.2f, baseY + 120f
    )
    drawPath(
        path = path,
        color = Color(0xFFE53935).copy(alpha = 0.5f),
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f)))
    )
}
