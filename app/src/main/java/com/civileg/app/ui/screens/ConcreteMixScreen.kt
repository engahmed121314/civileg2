package com.civileg.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.utils.ConcreteMixDesigner
import com.civileg.app.utils.ConcreteMixDesigner.*
import com.civileg.app.viewmodel.ConcreteMixViewModel

// ============================================================
// Color palette
// ============================================================

private val CementGray    = Color(0xFF78909C)
private val WaterBlue     = Color(0xFF42A5F5)
private val FineSandBeige = Color(0xFFFFCC80)
private val CoarseBrown   = Color(0xFFA1887F)
private val AdmixtureGreen = Color(0xFF66BB6A)
private val AccentTeal    = Color(0xFF26A69A)
private val HighlightAmber = Color(0xFFFFB300)

// ============================================================
// Main screen composable
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcreteMixScreen(
    viewModel: ConcreteMixViewModel = hiltViewModel()
) {
    val mixResult by viewModel.mixResult.collectAsState()

    // ── Local UI state ──
    var targetStrength by remember { mutableStateOf("30") }
    var standardDeviation by remember { mutableStateOf("5.0") }
    var selectedGrade by remember { mutableIntStateOf(30) }
    var selectedExposure by remember { mutableStateOf(Exposure.MODERATE) }
    var maxAggSize by remember { mutableStateOf(20.0) }
    var finenessModulus by remember { mutableStateOf("2.70") }
    var fineAggSG by remember { mutableStateOf("2.65") }
    var coarseAggSG by remember { mutableStateOf("2.70") }
    var selectedSlump by remember { mutableStateOf("75-100") }
    var selectedCementType by remember { mutableStateOf(CementType.OPC) }
    var hasAdmixture by remember { mutableStateOf(false) }
    var selectedAdmixture by remember { mutableStateOf("Water Reducer") }
    var admixtureDosage by remember { mutableStateOf("1.0") }

    // Derived slump mid-point value for calculation
    val slumpValue = when (selectedSlump) {
        "25-50"   -> 37.5
        "50-75"   -> 62.5
        "75-100"  -> 87.5
        "100-150" -> 125.0
        "150-200" -> 175.0
        else      -> 87.5
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Concrete Mix Design",
                        fontWeight = FontWeight.Bold
                    )
                },
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
            // ── 1. Target Strength & Grade Selection ──
            SectionCard(title = "Target Strength") {
                InputRow(
                    label = "f'c Target Strength (MPa)",
                    value = targetStrength,
                    onValueChange = { targetStrength = it }
                )
                InputRow(
                    label = "Standard Deviation S (MPa)",
                    value = standardDeviation,
                    onValueChange = { standardDeviation = it }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Quick Grade Selection",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    STANDARD_GRADES.forEach { grade ->
                        FilterChip(
                            selected = grade == selectedGrade,
                            onClick = {
                                selectedGrade = grade
                                targetStrength = grade.toString()
                                standardDeviation = "%.1f".format(
                                    ConcreteMixDesigner.estimateStandardDeviation(grade.toDouble())
                                )
                            },
                            label = { Text("C$grade") },
                            leadingIcon = if (grade == selectedGrade) {
                                { Text("●", color = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
            }

            // ── 2. Exposure Condition ──
            SectionCard(title = "Exposure Condition") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Exposure.entries.forEach { exposure ->
                        FilterChip(
                            selected = exposure == selectedExposure,
                            onClick = { selectedExposure = exposure },
                            label = {
                                Text(
                                    exposure.name.replace("_", " "),
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = if (exposure == selectedExposure) {
                                { Text("●", color = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Min cement: ${selectedExposure.minCement.toInt()} kg/m³  •  Max w/c: ${selectedExposure.maxWCRatio}  •  Min cover: ${selectedExposure.minCover.toInt()} mm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── 3. Aggregate Properties ──
            SectionCard(title = "Aggregate Properties") {
                // Max aggregate size dropdown
                var aggExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = aggExpanded,
                    onExpandedChange = { aggExpanded = !aggExpanded }
                ) {
                    OutlinedTextField(
                        value = "${maxAggSize.toInt()} mm",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Max Aggregate Size") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = aggExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = aggExpanded,
                        onDismissRequest = { aggExpanded = false }
                    ) {
                        listOf(10.0, 20.0, 25.0, 40.0).forEach { size ->
                            DropdownMenuItem(
                                text = { Text("${size.toInt()} mm") },
                                onClick = {
                                    maxAggSize = size
                                    aggExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                InputRow(
                    label = "Fine Agg Fineness Modulus (FM)",
                    value = finenessModulus,
                    onValueChange = { finenessModulus = it }
                )
                InputRow(
                    label = "Fine Aggregate Specific Gravity",
                    value = fineAggSG,
                    onValueChange = { fineAggSG = it }
                )
                InputRow(
                    label = "Coarse Aggregate Specific Gravity",
                    value = coarseAggSG,
                    onValueChange = { coarseAggSG = it }
                )
            }

            // ── 4. Slump Selector ──
            SectionCard(title = "Required Slump") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("25-50", "50-75", "75-100", "100-150", "150-200").forEach { range ->
                        FilterChip(
                            selected = range == selectedSlump,
                            onClick = { selectedSlump = range },
                            label = { Text("$range mm") },
                            leadingIcon = if (range == selectedSlump) {
                                { Text("●", color = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
            }

            // ── 5. Cement Type Selector ──
            SectionCard(title = "Cement Type") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CementType.entries.forEach { ct ->
                        FilterChip(
                            selected = ct == selectedCementType,
                            onClick = { selectedCementType = ct },
                            label = { Text(ct.name) },
                            leadingIcon = if (ct == selectedCementType) {
                                { Text("●", color = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${selectedCementType.label}  (SG = ${selectedCementType.specificGravity})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── 6. Admixtures Toggle ──
            SectionCard(title = "Admixtures") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use Admixture", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = hasAdmixture,
                        onCheckedChange = { hasAdmixture = it }
                    )
                }
                if (hasAdmixture) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("Water Reducer", "Plasticizer", "Superplasticizer").forEach { adm ->
                            FilterChip(
                                selected = adm == selectedAdmixture,
                                onClick = { selectedAdmixture = adm },
                                label = { Text(adm, fontSize = 12.sp) },
                                leadingIcon = if (adm == selectedAdmixture) {
                                    { Text("●", color = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    InputRow(
                        label = "Dosage (% of cement weight)",
                        value = admixtureDosage,
                        onValueChange = { admixtureDosage = it }
                    )
                }
            }

            // ── 7. Calculate Button ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.designMix(
                            targetStrength = targetStrength.toDoubleOrNull() ?: 30.0,
                            standardDeviation = standardDeviation.toDoubleOrNull() ?: 5.0,
                            maxAggSize = maxAggSize,
                            slump = slumpValue,
                            exposure = selectedExposure,
                            cementType = selectedCementType,
                            fm = finenessModulus.toDoubleOrNull() ?: 2.70,
                            hasAdmixture = hasAdmixture,
                            admixtureType = if (hasAdmixture) selectedAdmixture else "None",
                            admixtureDosage = if (hasAdmixture) (admixtureDosage.toDoubleOrNull() ?: 1.0) else 0.0,
                            isPumpable = false,
                            weatherCondition = "Normal",
                            useNoTestData = false,
                            fineAggSG = fineAggSG.toDoubleOrNull() ?: 2.65,
                            coarseAggSG = coarseAggSG.toDoubleOrNull() ?: 2.70
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calculate Mix")
                }
                IconButton(
                    onClick = {
                        targetStrength = "30"
                        standardDeviation = "5.0"
                        selectedGrade = 30
                        selectedExposure = Exposure.MODERATE
                        maxAggSize = 20.0
                        finenessModulus = "2.70"
                        fineAggSG = "2.65"
                        coarseAggSG = "2.70"
                        selectedSlump = "75-100"
                        selectedCementType = CementType.OPC
                        hasAdmixture = false
                        selectedAdmixture = "Water Reducer"
                        admixtureDosage = "1.0"
                        viewModel.clearResults()
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                }
            }

            // ── 8. Results Section ──
            mixResult?.let { result ->
                Spacer(modifier = Modifier.height(4.dp))
                MixResultCard(result = result)

                // Mix Proportion Bar Chart
                Spacer(modifier = Modifier.height(4.dp))
                SectionCard(title = "Mix Proportion Chart") {
                    MixProportionBarChart(result = result)
                }

                // Notes
                if (result.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionCard(title = "Design Notes") {
                        result.notes.forEach { note ->
                            Text(
                                text = "• $note",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ============================================================
// Reusable Section Card
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
// Input Row
// ============================================================

@Composable
private fun InputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
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
// Result Row
// ============================================================

@Composable
private fun ResultRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
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
// Mix Result Card
// ============================================================

@Composable
private fun MixResultCard(result: MixResult) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Mix Design Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "C${result.targetStrength.toInt()}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            HorizontalDivider()

            // Key results
            ResultRow(
                "Required Strength f'cr",
                "%.1f MPa".format(result.requiredStrength)
            )
            ResultRow(
                "Water-Cement Ratio",
                "%.3f".format(result.waterCementRatio),
                highlight = true
            )
            if (result.admixtureContent > 0) {
                ResultRow(
                    "Effective w/cm (with admix)",
                    "%.3f".format(result.wCmRatio)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                "Material Quantities (per m³)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            ResultRow(
                "Cement Content",
                "%.0f kg/m³".format(result.cementContent),
                highlight = true
            )
            ResultRow(
                "Water Content",
                "%.0f liters/m³".format(result.waterContent)
            )
            ResultRow(
                "Fine Aggregate",
                "%.0f kg/m³".format(result.fineAggContent)
            )
            ResultRow(
                "Coarse Aggregate",
                "%.0f kg/m³".format(result.coarseAggContent)
            )
            if (result.admixtureContent > 0) {
                ResultRow(
                    "Admixture",
                    "%.1f kg/m³".format(result.admixtureContent)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            ResultRow(
                "Air Content",
                "%.1f %%".format(result.airContent)
            )
            ResultRow(
                "Unit Weight",
                "%.0f kg/m³".format(result.unitWeight)
            )
            ResultRow(
                "Yield",
                "%.2f m³".format(result.yield)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Mix Ratio prominently displayed
            Text(
                "Mix Ratio (C : FA : CA : W)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    buildMixRatioString(result),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${result.cementType}  •  ${result.exposure}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Builds full mix ratio string: 1 : x.xx : x.xx : x.xx
 */
private fun buildMixRatioString(result: MixResult): String {
    val c = result.cementContent
    if (c <= 0) return "—"
    val fa = result.fineAggContent / c
    val ca = result.coarseAggContent / c
    val w = result.waterContent / c
    return "1 : ${"%.2f".format(fa)} : ${"%.2f".format(ca)} : ${"%.2f".format(w)}"
}

// ============================================================
// Mix Proportion Bar Chart (Canvas)
// ============================================================

@Composable
private fun MixProportionBarChart(result: MixResult) {
    val cement = result.cementContent
    val water = result.waterContent
    val fine = result.fineAggContent
    val coarse = result.coarseAggContent
    val total = cement + water + fine + coarse

    if (total <= 0) return

    val segments = listOf(
        "Cement" to (cement / total) to CementGray,
        "Water"  to (water / total)  to WaterBlue,
        "Fine Agg" to (fine / total) to FineSandBeige,
        "Coarse Agg" to (coarse / total) to CoarseBrown
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Stacked horizontal bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            val w = size.width
            val h = size.height
            val cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            var xOffset = 0f

            segments.forEachIndexed { index, (label, pair) ->
                val (fraction, color) = pair
                val barWidth = (w * fraction).coerceAtLeast(1f)
                val isLast = index == segments.lastIndex

                drawRoundRect(
                    color = color,
                    topLeft = Offset(xOffset, 0f),
                    size = Size(
                        width = if (isLast) w - xOffset else barWidth,
                        height = h
                    ),
                    cornerRadius = if (xOffset <= 0f && isLast) cornerRadius
                    else if (xOffset <= 0f) CornerRadius(
                        x = cornerRadius.x,
                        y = cornerRadius.y
                    )
                    else if (isLast) CornerRadius(
                        x = cornerRadius.x,
                        y = cornerRadius.y
                    )
                    else CornerRadius.Zero
                )

                // Draw percentage label if segment is wide enough
                if (barWidth > 40.dp.toPx()) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "${"%.0f".format(fraction * 100)}%",
                        xOffset + barWidth / 2f,
                        h / 2f + 10f,
                        android.graphics.Paint().apply {
                            this.color = android.graphics.Color.WHITE
                            textSize = 28f
                            isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }

                xOffset += barWidth
            }

            // Border
            drawRoundRect(
                color = Color(0xFF455A64),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = cornerRadius,
                style = Stroke(width = 1.5f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legend & individual bars
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            segments.forEach { (label, pair) ->
                val (fraction, color) = pair
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color swatch
                    Canvas(
                        modifier = Modifier.size(16.dp)
                    ) {
                        drawRoundRect(
                            color = color,
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Label
                    Text(
                        label,
                        modifier = Modifier.width(90.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    // Bar
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                    ) {
                        val barW = size.width * fraction
                        drawRoundRect(
                            color = color.copy(alpha = 0.7f),
                            size = Size(barW, size.height),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        // Border
                        drawRoundRect(
                            color = color,
                            size = Size(barW, size.height),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = Stroke(width = 1f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Value
                    Text(
                        "${"%.0f".format(fraction * total)} kg",
                        modifier = Modifier.width(80.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Total: ${"%.0f".format(total)} kg/m³  (excl. air & admixture)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
