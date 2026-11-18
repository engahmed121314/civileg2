package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import com.civileg.app.R
import com.civileg.app.ui.responsive.dimens
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.utils.QuickToolsEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickToolsScreen(onNavigateBack: () -> Unit = {}) {
    val tabTitles = listOf(
        "Rebar Weight",
        "Rebar Sub",
        "Dev. Length",
        "Concrete Batch",
        "Pre-Dimension",
        "Slope/Level",
        "Masonry",
        "Formwork",
        "Unit Conv.",
        "Earthwork",
        "Plaster/Paint"
    )
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.home_quick_tools), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            AppText(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> RebarWeightTab()
                1 -> RebarSubstitutionTab()
                2 -> DevelopmentLengthTab()
                3 -> ConcreteBatchTab()
                4 -> PreDimensioningTab()
                5 -> SlopeTab()
                6 -> MasonryTab()
                7 -> FormworkTab()
                8 -> UnitConverterTab()
                9 -> EarthworkVolumeTab()
                10 -> PlasterPaintTab()
            }
        }
    }
}

// ── helpers ────────────────────────────────────────────────
@Composable
private fun QInput(label: String, value: String, onChange: (String) -> Unit, kt: KeyboardType = KeyboardType.Decimal, mod: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { AppText(label, fontSize = 13.sp) },
        modifier = mod.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = kt),
        shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun QRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppText(value, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun QCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

// ── Tab 0: Rebar Weight ───────────────────────────────────
@Composable
private fun RebarWeightTab() {
    var d by remember { mutableStateOf("16") }
    var len by remember { mutableStateOf("12.0") }
    var cnt by remember { mutableStateOf("10") }
    var res by remember { mutableStateOf<QuickToolsEngine.RebarWeightResult?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QCard {
            AppText("Rebar Weight & Count", fontWeight = FontWeight.Bold)
            QInput("Diameter (mm)", d, { d = it })
            QInput("Length per bar (m)", len, { len = it })
            QInput("Number of bars", cnt, { cnt = it }, KeyboardType.Number)
            AppButton(label = "Calculate", onClick = {
                val dd = d.toDoubleOrNull() ?: return@AppButton
                val ll = len.toDoubleOrNull() ?: return@AppButton
                val cc = cnt.toIntOrNull() ?: return@AppButton
                res = QuickToolsEngine.calculateRebarWeight(dd, ll, cc)
            }, modifier = Modifier.fillMaxWidth())
        }
        res?.let { r ->
            QCard {
                AppText("Results", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                QRow("Weight (kg/m)", "%.3f".format(r.weightPerMeterKg))
                QRow("Total length (m)", "%.2f".format(r.totalLengthM))
                QRow("Total weight (kg)", "%.2f".format(r.totalWeightKg))
                QRow("Total weight (ton)", "%.3f".format(r.totalWeightTon))
                QRow("Area per bar (mm²)", "%.1f".format(r.singleBarAreaMm2))
                QRow("Total area (mm²)", "%.1f".format(r.totalAreaMm2))
                QRow("Total area (cm²)", "%.2f".format(r.totalAreaCm2))
                QRow("12m bars per ton", r.bars12mPerTon.toString())
            }
        }
    }
}

// ── Tab 1: Rebar Substitution ──────────────────────────────
@Composable
private fun RebarSubstitutionTab() {
    var cnt by remember { mutableStateOf("6") }
    var d1 by remember { mutableStateOf("16") }
    var d2 by remember { mutableStateOf("12") }
    var res by remember { mutableStateOf<QuickToolsEngine.RebarSubstitutionResult?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QCard {
            AppText("Equivalent Rebar Substitution", fontWeight = FontWeight.Bold)
            QInput("Original count", cnt, { cnt = it }, KeyboardType.Number)
            QInput("Original diameter (mm)", d1, { d1 = it })
            QInput("New diameter (mm)", d2, { d2 = it })
            AppButton(label = "Calculate", onClick = {
                val c = cnt.toIntOrNull() ?: return@AppButton
                val a = d1.toDoubleOrNull() ?: return@AppButton
                val b = d2.toDoubleOrNull() ?: return@AppButton
                res = QuickToolsEngine.calculateRebarSubstitution(c, a, b)
            }, modifier = Modifier.fillMaxWidth())
        }
        res?.let { r ->
            QCard {
                AppText("Results", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                QRow("Original area (mm²)", "%.1f".format(r.origAreaMm2))
                QRow("New diameter (mm)", "%.0f".format(r.newDiameterMm))
                QRow("Required count", r.requiredCount.toString(), true)
                QRow("Provided area (mm²)", "%.1f".format(r.providedAreaMm2))
                QRow("Area difference (mm²)", "%.1f".format(r.areaDifferenceMm2))
                QRow("Change (%)", "%.2f".format(r.percentageChange))
                QRow("Safe", if (r.isSafe) "YES" else "NO")
            }
        }
    }
}

// ── Tab 2: Development Length ──────────────────────────────
@Composable
private fun DevelopmentLengthTab() {
    var code by remember { mutableStateOf("ECP_203") }
    var d by remember { mutableStateOf("16") }
    var fc by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("360") }
    var top by remember { mutableStateOf(false) }
    var res by remember { mutableStateOf<QuickToolsEngine.DevelopmentLengthResult?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QCard {
            AppText("Development Length & Lap Splice", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = code, onValueChange = {}, readOnly = true, label = { AppText("Code") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                DropdownSelector(options = listOf("ECP_203", "ACI_318", "SBC_304"), selected = code, onSelect = { code = it })
            }
            QInput("Diameter (mm)", d, { d = it })
            QInput("fc / fcu (MPa)", fc, { fc = it })
            QInput("fy (MPa)", fy, { fy = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = top, onCheckedChange = { top = it })
                AppText("Top bar")
            }
            AppButton(label = "Calculate", onClick = {
                val dd = d.toDoubleOrNull() ?: return@AppButton
                val fcc = fc.toDoubleOrNull() ?: return@AppButton
                val fyy = fy.toDoubleOrNull() ?: return@AppButton
                val c = when (code) { "ACI_318" -> QuickToolsEngine.CodeStandard.ACI_318; "SBC_304" -> QuickToolsEngine.CodeStandard.SBC_304; else -> QuickToolsEngine.CodeStandard.ECP_203 }
                val pos = if (top) QuickToolsEngine.BarPosition.TOP else QuickToolsEngine.BarPosition.BOTTOM
                res = QuickToolsEngine.calculateDevelopmentAndSplice(c, dd, fcc, fyy, QuickToolsEngine.StressType.TENSION, pos)
            }, modifier = Modifier.fillMaxWidth())
        }
        res?.let { r ->
            QCard {
                AppText("Results", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                QRow("Code", r.code.name)
                QRow("Basic Ld (cm)", "%.1f".format(r.basicLdCm))
                QRow("Ld multiple (d)", "%.1f".format(r.basicLdMultiple))
                QRow("Splice Class A (cm)", "%.1f".format(r.tensionSpliceClassACm))
                QRow("Splice Class B (cm)", "%.1f".format(r.tensionSpliceClassBCm))
                QRow("Compression splice (cm)", "%.1f".format(r.compressionSpliceCm))
                QRow("90° hook (mm)", "%.0f".format(r.standard90HookMm))
                QRow("180° hook (mm)", "%.0f".format(r.standard180HookMm))
                AppText(r.explanation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Tab 3: Concrete Batch ──────────────────────────────────
@Composable
private fun ConcreteBatchTab() {
    var vol by remember { mutableStateOf("10.0") }
    var bags by remember { mutableStateOf("7.0") }
    var res by remember { mutableStateOf<QuickToolsEngine.ConcreteBatchResult?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QCard {
            AppText("Concrete Volume & Batch", fontWeight = FontWeight.Bold)
            QInput("Volume (m³)", vol, { vol = it })
            QInput("Cement bags per m³", bags, { bags = it })
            AppButton(label = "Calculate", onClick = {
                val v = vol.toDoubleOrNull() ?: return@AppButton
                val b = bags.toDoubleOrNull() ?: return@AppButton
                res = QuickToolsEngine.calculateConcreteBatch(v, b)
            }, modifier = Modifier.fillMaxWidth())
        }
        res?.let { r ->
            QCard {
                AppText("Results", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                QRow("Volume (m³)", "%.2f".format(r.volumeM3))
                QRow("Cement (kg/m³)", "%.0f".format(r.cementContentKgPerM3))
                QRow("Total cement (kg)", "%.0f".format(r.totalCementKg))
                QRow("Cement bags (50kg)", r.totalCementBags50Kg.toString(), true)
                QRow("Cement (ton)", "%.2f".format(r.totalCementTon))
                QRow("Sand (m³)", "%.2f".format(r.sandVolumeM3))
                QRow("Gravel (m³)", "%.2f".format(r.gravelVolumeM3))
                QRow("Water (L)", "%.0f".format(r.waterLiters))
            }
        }
    }
}

// ── Tab 4: Pre-Dimensioning ────────────────────────────────
@Composable
private fun PreDimensioningTab() {
    var type by remember { mutableStateOf("BEAM_SIMPLE") }
    var span by remember { mutableStateOf("6.0") }
    var res by remember { mutableStateOf<QuickToolsEngine.PreDimensioningResult?>(null) }
    val types = QuickToolsEngine.StructuralMemberType.values().map { it.name }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QCard {
            AppText("Structural Pre-Dimensioning", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = type, onValueChange = {}, readOnly = true, label = { AppText("Member type") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                DropdownSelector(options = types, selected = type, onSelect = { type = it })
            }
            QInput("Span (m)", span, { span = it })
            AppButton(label = "Calculate", onClick = {
                val s = span.toDoubleOrNull() ?: return@AppButton
                val t = QuickToolsEngine.StructuralMemberType.valueOf(type)
                res = QuickToolsEngine.calculatePreDimensioning(t, s)
            }, modifier = Modifier.fillMaxWidth())
        }
        res?.let { r ->
            QCard {
                AppText("Results", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                QRow("Member type", r.memberType.name)
                QRow("Span (m)", "%.2f".format(r.spanM))
                QRow("Depth (cm)", "%.0f".format(r.recommendedDepthCm), true)
                QRow("Total thickness (cm)", "%.0f".format(r.recommendedTotalThicknessCm))
                QRow("Width (cm)", "%.0f".format(r.recommendedWidthCm))
                AppText(r.formulaUsed, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppText(r.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Tab 5: Slope ──────────────────────────────────────────
@Composable
private fun SlopeTab() {
    var l1 by remember { mutableStateOf("10.0") }
    var l2 by remember { mutableStateOf("10.5") }
    var dist by remember { mutableStateOf("25.0") }
    var res by remember { mutableStateOf<QuickToolsEngine.SlopeLevelResult?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QCard {
            AppText("Slope & Level Difference", fontWeight = FontWeight.Bold)
            QInput("Level 1 (m)", l1, { l1 = it })
            QInput("Level 2 (m)", l2, { l2 = it })
            QInput("Horizontal distance (m)", dist, { dist = it })
            AppButton(label = "Calculate", onClick = {
                val a = l1.toDoubleOrNull() ?: return@AppButton
                val b = l2.toDoubleOrNull() ?: return@AppButton
                val d = dist.toDoubleOrNull() ?: return@AppButton
                res = QuickToolsEngine.calculateSlope(a, b, d)
            }, modifier = Modifier.fillMaxWidth())
        }
        res?.let { r ->
            QCard {
                AppText("Results", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                QRow("Elevation diff (m)", "%.3f".format(r.elevationDifferenceM))
                QRow("Elevation diff (cm)", "%.1f".format(r.elevationDifferenceCm))
                QRow("Slope (%)", "%.2f".format(r.slopePercentage))
                QRow("Slope ratio 1:X", "%.1f".format(r.slopeRatio1ToX))
                QRow("Angle (deg)", "%.2f".format(r.slopeAngleDegrees))
                QRow("Direction", r.direction.name)
            }
        }
    }
}

// ── Tab 6: Masonry ────────────────────────────────────────
@Composable
private fun MasonryTab() {
    var area by remember { mutableStateOf("20.0") }
    var brick by remember { mutableStateOf("RED_BRICK_25_12_6") }
    var thick by remember { mutableStateOf("HALF_BRICK_12CM") }
    var res by remember { mutableStateOf<QuickToolsEngine.MasonryResult?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QCard {
            AppText("Brickwork & Mortar", fontWeight = FontWeight.Bold)
            QInput("Wall area (m²)", area, { area = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = brick, onValueChange = {}, readOnly = true, label = { AppText("Brick type") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                DropdownSelector(options = QuickToolsEngine.BrickType.values().map { it.name }, selected = brick, onSelect = { brick = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = thick, onValueChange = {}, readOnly = true, label = { AppText("Thickness") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                DropdownSelector(options = QuickToolsEngine.WallThickness.values().map { it.name }, selected = thick, onSelect = { thick = it })
            }
            AppButton(label = "Calculate", onClick = {
                val a = area.toDoubleOrNull() ?: return@AppButton
                val b = QuickToolsEngine.BrickType.valueOf(brick)
                val t = QuickToolsEngine.WallThickness.valueOf(thick)
                res = QuickToolsEngine.calculateMasonry(a, b, t)
            }, modifier = Modifier.fillMaxWidth())
        }
        res?.let { r ->
            QCard {
                AppText("Results", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                QRow("Wall area (m²)", "%.2f".format(r.wallAreaM2))
                QRow("Bricks", r.numberOfBricks.toString(), true)
                QRow("Mortar (m³)", "%.2f".format(r.mortarVolumeM3))
                QRow("Cement bags (50kg)", r.cementBags50Kg.toString())
                QRow("Sand (m³)", "%.2f".format(r.sandVolumeM3))
            }
        }
    }
}

// ── Tab 7: Formwork ───────────────────────────────────────
@Composable
private fun FormworkTab() {
    var type by remember { mutableStateOf("VERTICAL_SIDES") }
    var span by remember { mutableStateOf("4.0") }
    var res by remember { mutableStateOf<QuickToolsEngine.FormworkStrippingResult?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QCard {
            AppText("Formwork Stripping Time", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = type, onValueChange = {}, readOnly = true, label = { AppText("Element type") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                DropdownSelector(options = QuickToolsEngine.FormworkElementType.values().map { it.name }, selected = type, onSelect = { type = it })
            }
            QInput("Span (m)", span, { span = it })
            AppButton(label = "Calculate", onClick = {
                val s = span.toDoubleOrNull() ?: return@AppButton
                val t = QuickToolsEngine.FormworkElementType.valueOf(type)
                res = QuickToolsEngine.calculateFormworkStrippingTime(t, s)
            }, modifier = Modifier.fillMaxWidth())
        }
        res?.let { r ->
            QCard {
                AppText("Results", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                QRow("Element type", r.elementType.name)
                QRow("Span (m)", "%.2f".format(r.spanM))
                QRow("Min days (ordinary)", r.minimumDaysOrdinaryCement.toString(), true)
                QRow("Min days (rapid)", r.minimumDaysRapidCement.toString())
                AppText(r.codeFormula, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppText(r.safetyRule, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DropdownSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AppButton(
            label = selected,
            onClick = { expanded = true },
            style = AppButtonStyle.Outline,
            fontSize = 13.sp
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { AppText(opt) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

// ── Tab 8: Unit Converter ─────────────────────────────────
@Composable
private fun UnitConverterTab() {
    var qtyType by remember { mutableStateOf("Length") }
    var value by remember { mutableStateOf("1") }
    var fromUnit by remember { mutableStateOf("m") }
    var toUnit by remember { mutableStateOf("ft") }
    var result by remember { mutableStateOf<Double?>(null) }

    val types = QuickToolsEngine.QuantityType.values().map { it.label }
    val unitsForType = QuickToolsEngine.QuantityType.values()
        .firstOrNull { it.label == qtyType }
        ?.let { QuickToolsEngine.unitsFor(it) } ?: emptyList()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QCard {
            AppText("Unit Converter", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = qtyType, onValueChange = {}, readOnly = true, label = { AppText("Quantity") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                DropdownSelector(options = types, selected = qtyType, onSelect = { qtyType = it; fromUnit = QuickToolsEngine.unitsFor(QuickToolsEngine.QuantityType.values().first { q -> q.label == it }).firstOrNull() ?: fromUnit; toUnit = fromUnit; result = null })
            }
            QInput("Value", value, { value = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = fromUnit, onValueChange = {}, readOnly = true, label = { AppText("From") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                DropdownSelector(options = unitsForType, selected = fromUnit, onSelect = { fromUnit = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = toUnit, onValueChange = {}, readOnly = true, label = { AppText("To") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                DropdownSelector(options = unitsForType, selected = toUnit, onSelect = { toUnit = it })
            }
            AppButton(
                label = "Convert",
                onClick = {
                    val v = value.toDoubleOrNull() ?: return@AppButton
                    val type = QuickToolsEngine.QuantityType.values().firstOrNull { it.label == qtyType } ?: return@AppButton
                    result = try { QuickToolsEngine.convertUnit(type, v, fromUnit, toUnit) } catch (_: Exception) { null }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        result?.let { r ->
            QCard {
                AppText("Result", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                AppText(
                    "%.6g  $fromUnit = %.6g  $toUnit".format(value.toDoubleOrNull() ?: 0.0, r),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                )
                QRow("Reverse (1 $toUnit in $fromUnit)", "%.6g".format(
                    try {
                        QuickToolsEngine.convertUnit(
                            QuickToolsEngine.QuantityType.values().first { it.label == qtyType },
                            1.0, toUnit, fromUnit
                        )
                    } catch (_: Exception) { 0.0 }
                ))
            }
        }
    }
}

// ── Tab 9: Earthwork Volume ───────────────────────────────
@Composable
private fun EarthworkVolumeTab() {
    var a1 by remember { mutableStateOf("12.0") }
    var a2 by remember { mutableStateOf("16.0") }
    var dist by remember { mutableStateOf("50.0") }
    var am by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<QuickToolsEngine.EarthworkVolumeResult?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QCard {
            AppText("Earthwork Volume (Cut/Fill)", fontWeight = FontWeight.Bold)
            QInput("End area 1 (m²)", a1, { a1 = it })
            QInput("End area 2 (m²)", a2, { a2 = it })
            QInput("Distance between sections (m)", dist, { dist = it })
            QInput("Mid-section area m² (optional)", am, { am = it })
            AppButton(label = "Calculate", onClick = {
                val x1 = a1.toDoubleOrNull() ?: return@AppButton
                val x2 = a2.toDoubleOrNull() ?: return@AppButton
                val d = dist.toDoubleOrNull() ?: return@AppButton
                val mid = am.toDoubleOrNull() ?: -1.0
                res = QuickToolsEngine.calculateEarthworkVolume(x1, x2, d, mid)
            }, modifier = Modifier.fillMaxWidth())
        }
        res?.let { r ->
            QCard {
                AppText("Results", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                QRow("Average end area method (m³)", "%.2f".format(r.averageEndAreaVolumeM3))
                QRow("Prismoidal method (m³)", "%.2f".format(r.prismoidalVolumeM3), true)
                QRow("Prismoidal correction (%)", "%.2f".format(r.prismoidalCorrectionPct))
                QRow("Mid-section area (m²)", "%.2f".format(r.midSectionAreaM2))
                QRow("Volume per meter run (m³/m)", "%.2f".format(r.volumePerMeterRunM3))
                AppText("Recommended volume: %.2f m³ (prismoidal)".format(r.recommendedVolumeM3), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Tab 10: Plaster & Paint ───────────────────────────────
@Composable
private fun PlasterPaintTab() {
    var gross by remember { mutableStateOf("100.0") }
    var openings by remember { mutableStateOf("15.0") }
    var thick by remember { mutableStateOf("2.0") }
    var coats by remember { mutableStateOf("2") }
    var coverage by remember { mutableStateOf("10.0") }
    var res by remember { mutableStateOf<QuickToolsEngine.PlasterAndPaintResult?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QCard {
            AppText("Plaster & Paint Estimator", fontWeight = FontWeight.Bold)
            QInput("Gross wall area (m²)", gross, { gross = it })
            QInput("Openings area (m²)", openings, { openings = it })
            QInput("Plaster thickness (cm)", thick, { thick = it })
            QInput("Paint coats", coats, { coats = it }, KeyboardType.Number)
            QInput("Paint coverage (m²/L per coat)", coverage, { coverage = it })
            AppButton(label = "Calculate", onClick = {
                val g = gross.toDoubleOrNull() ?: return@AppButton
                val o = openings.toDoubleOrNull() ?: 0.0
                val t = thick.toDoubleOrNull() ?: return@AppButton
                val c = coats.toIntOrNull() ?: return@AppButton
                val cov = coverage.toDoubleOrNull() ?: return@AppButton
                res = QuickToolsEngine.calculatePlasterAndPaint(g, o, t, c, cov)
            }, modifier = Modifier.fillMaxWidth())
        }
        res?.let { r ->
            QCard {
                AppText("Results", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                QRow("Net plaster area (m²)", "%.2f".format(r.netAreaM2), true)
                QRow("Plaster mortar (m³)", "%.2f".format(r.plasterMortarVolumeM3))
                QRow("Cement bags (50kg)", r.plasterCementBags50Kg.toString())
                QRow("Sand (m³)", "%.2f".format(r.plasterSandVolumeM3))
                QRow("Paint required (L)", "%.1f".format(r.paintRequiredLiters), true)
                QRow("Paint (gallons US)", "%.1f".format(r.paintRequiredLiters / 3.785411784))
            }
        }
    }
}