package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.livedata.observeAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.domain.*
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalPileDrawing
import com.civileg.app.viewmodel.PileFoundationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PileFoundationScreen(
    viewModel: PileFoundationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)
    val error by viewModel.error.observeAsState()

    // ── Pile type & soil type selectors ──
    var expandedPileType by remember { mutableStateOf(false) }
    var expandedSoilType by remember { mutableStateOf(false) }
    var expandedPattern by remember { mutableStateOf(false) }
    var selectedPileType by remember { mutableStateOf(PileType.BORED) }
    var selectedSoilType by remember { mutableStateOf(SoilType.CLAY) }
    var selectedPattern by remember { mutableStateOf("2x2") }

    // ── Pile geometry ──
    var pileDiameter by remember { mutableStateOf("600") }
    var pileLength by remember { mutableStateOf("15") }
    var numberOfPiles by remember { mutableStateOf("4") }
    var spacing by remember { mutableStateOf("3") }

    // ── Loads ──
    var axialLoad by remember { mutableStateOf("2000") }
    var lateralLoad by remember { mutableStateOf("100") }
    var momentLoad by remember { mutableStateOf("50") }

    // ── Material ──
    var fcu by remember { mutableStateOf("30") }
    var fy by remember { mutableStateOf("400") }
    var fyp by remember { mutableStateOf("400") }

    // ── Soil parameters ──
    var cu by remember { mutableStateOf("50") }
    var phi by remember { mutableStateOf("30") }
    var gammaSoil by remember { mutableStateOf("18") }
    var waterTableDepth by remember { mutableStateOf("5") }
    var embedmentDepth by remember { mutableStateOf("1.5") }
    var safetyFactor by remember { mutableStateOf("3") }
    var scourDepth by remember { mutableStateOf("0") }

    // ── Eccentricity ──
    var eccentricityX by remember { mutableStateOf("0") }
    var eccentricityY by remember { mutableStateOf("0") }

    // ── Pile cap / column ──
    var capCover by remember { mutableStateOf("75") }
    var columnWidth by remember { mutableStateOf("400") }
    var columnLength by remember { mutableStateOf("400") }

    // ── Expandable sections ──
    var showSoilParams by remember { mutableStateOf(true) }
    var showCapParams by remember { mutableStateOf(true) }
    var showAdvanced by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pile Foundation Design", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── HEADER ─────────────────────────────────────────
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Foundation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Pile Foundation Design (ECP 203)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // ─── PILE TYPE & SOIL TYPE ────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pile & Soil Configuration", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))

                        // Pile type selector
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pile Type", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ExposedDropdownMenuBox(
                                    expanded = expandedPileType,
                                    onExpandedChange = { expandedPileType = !expandedPileType }
                                ) {
                                    OutlinedTextField(
                                        value = selectedPileType.displayName,
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPileType) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expandedPileType,
                                        onDismissRequest = { expandedPileType = false }
                                    ) {
                                        PileType.entries.forEach { type ->
                                            DropdownMenuItem(
                                                text = { Text(type.displayName) },
                                                onClick = { selectedPileType = type; expandedPileType = false }
                                            )
                                        }
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Soil Type", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ExposedDropdownMenuBox(
                                    expanded = expandedSoilType,
                                    onExpandedChange = { expandedSoilType = !expandedSoilType }
                                ) {
                                    OutlinedTextField(
                                        value = selectedSoilType.displayName,
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSoilType) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expandedSoilType,
                                        onDismissRequest = { expandedSoilType = false }
                                    ) {
                                        SoilType.entries.forEach { type ->
                                            DropdownMenuItem(
                                                text = { Text(type.displayName) },
                                                onClick = { selectedSoilType = type; expandedSoilType = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── PILE GEOMETRY ───────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pile Geometry", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PileInputField(pileDiameter, "Diameter (mm)", { pileDiameter = it }, Modifier.weight(1f))
                            PileInputField(pileLength, "Length (m)", { pileLength = it }, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PileInputField(numberOfPiles, "No. of Piles", { numberOfPiles = it }, Modifier.weight(1f), KeyboardType.Number)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pattern", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ExposedDropdownMenuBox(
                                    expanded = expandedPattern,
                                    onExpandedChange = { expandedPattern = !expandedPattern }
                                ) {
                                    OutlinedTextField(
                                        value = selectedPattern,
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPattern) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expandedPattern,
                                        onDismissRequest = { expandedPattern = false }
                                    ) {
                                        listOf("1x1", "2x1", "2x2", "3x2", "3x3", "4x3", "4x4").forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text(p) },
                                                onClick = { selectedPattern = p; expandedPattern = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        PileInputField(spacing, "Spacing (× Diameter)", { spacing = it }, Modifier.fillMaxWidth())
                    }
                }
            }

            // ─── LOADS ────────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowDownward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Applied Loads", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PileInputField(axialLoad, "Axial Load (kN)", { axialLoad = it }, Modifier.weight(1f))
                            PileInputField(lateralLoad, "Lateral Load (kN)", { lateralLoad = it }, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        PileInputField(momentLoad, "Moment (kN.m)", { momentLoad = it }, Modifier.fillMaxWidth())
                    }
                }
            }

            // ─── MATERIAL STRENGTHS ───────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Material Properties", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PileInputField(fcu, "fcu (MPa)", { fcu = it }, Modifier.weight(1f))
                            PileInputField(fy, "fy Cap (MPa)", { fy = it }, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        PileInputField(fyp, "fy Pile (MPa)", { fyp = it }, Modifier.fillMaxWidth())
                    }
                }
            }

            // ─── SOIL PARAMETERS (expandable) ─────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Landscape, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Soil Parameters", fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { showSoilParams = !showSoilParams }) {
                                Icon(
                                    if (showSoilParams) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle"
                                )
                            }
                        }

                        if (showSoilParams) {
                            Spacer(Modifier.height(12.dp))

                            // Dynamic soil fields based on type
                            if (selectedSoilType == SoilType.CLAY || selectedSoilType == SoilType.MIXED) {
                                PileInputField(cu, "Cu - Undrained Shear Strength (kPa)", { cu = it }, Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                            }
                            if (selectedSoilType == SoilType.SAND || selectedSoilType == SoilType.MIXED) {
                                PileInputField(phi, "Phi - Friction Angle (°)", { phi = it }, Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PileInputField(gammaSoil, "γ Soil (kN/m³)", { gammaSoil = it }, Modifier.weight(1f))
                                PileInputField(waterTableDepth, "Water Table (m)", { waterTableDepth = it }, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PileInputField(embedmentDepth, "Embedment (m)", { embedmentDepth = it }, Modifier.weight(1f))
                                PileInputField(safetyFactor, "Factor of Safety", { safetyFactor = it }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ─── PILE CAP & COLUMN (expandable) ──────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Domain, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Pile Cap & Column", fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { showCapParams = !showCapParams }) {
                                Icon(
                                    if (showCapParams) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle"
                                )
                            }
                        }

                        if (showCapParams) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PileInputField(columnWidth, "Column Width (mm)", { columnWidth = it }, Modifier.weight(1f))
                                PileInputField(columnLength, "Column Length (mm)", { columnLength = it }, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            PileInputField(capCover, "Concrete Cover (mm)", { capCover = it }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // ─── ADVANCED PARAMETERS (expandable) ────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Advanced Parameters", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { showAdvanced = !showAdvanced }) {
                                Icon(
                                    if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle"
                                )
                            }
                        }

                        if (showAdvanced) {
                            Spacer(Modifier.height(12.dp))
                            Text("Eccentricities", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PileInputField(eccentricityX, "ex (m)", { eccentricityX = it }, Modifier.weight(1f))
                                PileInputField(eccentricityY, "ey (m)", { eccentricityY = it }, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            PileInputField(scourDepth, "Scour Depth (m)", { scourDepth = it }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // ─── ERROR DISPLAY ────────────────────────────────────
            error?.let { err ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x22FF0000)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, null, tint = Color.Red)
                            Spacer(Modifier.width(8.dp))
                            Text(err, color = Color.Red, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ─── DESIGN BUTTON ────────────────────────────────────
            item {
                val inputValid = axialLoad.toDoubleOrNull()?.let { it > 0 } == true
                        && pileDiameter.toDoubleOrNull()?.let { it > 0 } == true
                        && pileLength.toDoubleOrNull()?.let { it > 0 } == true
                        && fcu.toDoubleOrNull()?.let { it > 0 } == true
                        && fy.toDoubleOrNull()?.let { it > 0 } == true
                        && numberOfPiles.toIntOrNull()?.let { it > 0 } == true

                Button(
                    onClick = {
                        if (!inputValid) return@Button
                        viewModel.designPileFoundation(
                            pileType = selectedPileType,
                            pileDiameter = pileDiameter.toDouble()!!,
                            pileLength = pileLength.toDouble()!!,
                            numberOfPiles = numberOfPiles.toInt()!!,
                            spacing = spacing.toDouble() ?: 3.0,
                            axialLoad = axialLoad.toDouble()!!,
                            lateralLoad = lateralLoad.toDoubleOrNull() ?: 0.0,
                            momentLoad = momentLoad.toDoubleOrNull() ?: 0.0,
                            fcu = fcu.toDouble()!!,
                            fy = fy.toDouble()!!,
                            fyp = fyp.toDoubleOrNull() ?: fy.toDouble()!!,
                            soilType = selectedSoilType,
                            cu = cu.toDoubleOrNull() ?: 50.0,
                            phi = phi.toDoubleOrNull() ?: 30.0,
                            gammaSoil = gammaSoil.toDoubleOrNull() ?: 18.0,
                            waterTableDepth = waterTableDepth.toDoubleOrNull() ?: 5.0,
                            embedmentDepth = embedmentDepth.toDoubleOrNull() ?: 1.5,
                            safetyFactor = safetyFactor.toDoubleOrNull() ?: 3.0,
                            pileGroupPattern = selectedPattern,
                            eccentricityX = eccentricityX.toDoubleOrNull() ?: 0.0,
                            eccentricityY = eccentricityY.toDoubleOrNull() ?: 0.0,
                            scourDepth = scourDepth.toDoubleOrNull() ?: 0.0,
                            capConcreteCover = capCover.toDoubleOrNull() ?: 75.0,
                            columnWidth = columnWidth.toDoubleOrNull() ?: 400.0,
                            columnLength = columnLength.toDoubleOrNull() ?: 400.0
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading && inputValid
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Engineering, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Design Pile Foundation")
                    }
                }
            }

            // ─── RESULTS SECTION ──────────────────────────────────
            result?.let { res ->
                val cap = res.capResult
                val capacity = res.capacityResult
                val group = res.groupResult
                val settlement = res.settlementResult
                val reinf = res.pileReinforcement

                // ── Status Card ──
                item {
                    val statusColor = if (res.isSafe) Color(0xFF2E7D32) else Color(0xFFF57C00)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (res.isSafe) Icons.Default.Verified else Icons.Default.Warning,
                                            contentDescription = null, tint = statusColor
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (res.isSafe) "Design is Safe" else "Review Required",
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                    Text(
                                        "ECP 203-2020 • ${res.pileType}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Utilization gauge
                                Box(contentAlignment = Alignment.Center) {
                                    val animatedRatio by animateFloatAsState(
                                        targetValue = res.utilizationRatio.toFloat().coerceIn(0f, 1.5f),
                                        animationSpec = tween(1000), label = ""
                                    )
                                    CircularProgressIndicator(
                                        progress = { animatedRatio.coerceIn(0f, 1f) },
                                        modifier = Modifier.size(60.dp),
                                        strokeWidth = 6.dp,
                                        color = when {
                                            res.utilizationRatio > 1.0 -> Color.Red
                                            res.utilizationRatio > 0.9 -> Color(0xFFFF9800)
                                            res.utilizationRatio > 0.4 -> Color(0xFF4CAF50)
                                            else -> Color(0xFF2196F3)
                                        },
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    Text(
                                        "${(res.utilizationRatio * 100).toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // Warnings
                            if (res.warnings.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                                res.warnings.forEach { w ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚠", color = Color(0xFFFF9800), fontSize = 13.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Text(w, fontSize = 12.sp, color = Color(0xFFFF9800))
                                    }
                                    Spacer(Modifier.height(2.dp))
                                }
                            }
                        }
                    }
                }

                // ── Pile Capacity Card ──
                item {
                    ResultCard(
                        title = "Pile Capacity",
                        icon = Icons.Default.Speed
                    ) {
                        ResultRow("Ultimate Capacity", "${"%.1f".format(capacity.ultimateCapacity)} kN")
                        ResultRow("Allowable Capacity", "${"%.1f".format(capacity.allowableCapacity)} kN")
                        ResultRow("Shaft Resistance", "${"%.1f".format(capacity.shaftResistance)} kN")
                        ResultRow("End Bearing", "${"%.1f".format(capacity.endBearingResistance)} kN")
                        ResultRow("Factor of Safety", "${"%.1f".format(capacity.fs)}")
                        ResultRow("Load / Pile", "${"%.1f".format(res.axialLoad / res.numberOfPiles)} kN")
                    }
                }

                // ── Group Efficiency Card ──
                item {
                    ResultCard(
                        title = "Group Efficiency (Converse-Labarre)",
                        icon = Icons.Default.GridView
                    ) {
                        ResultRow("Pattern", "${group.pattern} (${group.numberOfPiles} piles)")
                        ResultRow("Efficiency Factor", "${"%.3f".format(group.efficiencyFactor)}")
                        ResultRow("Individual Capacity", "${"%.1f".format(group.individualCapacity)} kN")
                        ResultRow("Group Capacity", "${"%.1f".format(group.groupCapacity)} kN")
                        ResultRow("Spacing", "${group.spacing.toInt()} mm")
                    }
                }

                // ── Settlement Card ──
                item {
                    ResultCard(
                        title = "Settlement (Meyerhof)",
                        icon = Icons.Default.Height
                    ) {
                        ResultRow("Immediate Settlement", "${"%.2f".format(settlement.immediateSettlement)} mm")
                        if (settlement.consolidationSettlement > 0.01) {
                            ResultRow("Consolidation", "${"%.2f".format(settlement.consolidationSettlement)} mm")
                        }
                        ResultRow("Total Settlement", "${"%.2f".format(settlement.totalSettlement)} mm")
                        ResultRow("Allowable", "${"%.1f".format(settlement.allowableSettlement)} mm")
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Status")
                            Text(
                                if (settlement.isOk) "✓ OK" else "✗ Exceeds limit",
                                fontWeight = FontWeight.Bold,
                                color = if (settlement.isOk) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                }

                // ── Lateral Capacity Card ──
                if (res.lateralCapacity > 0) {
                    item {
                        ResultCard(
                            title = "Lateral Capacity (Broms)",
                            icon = Icons.Default.SwapHoriz
                        ) {
                            ResultRow("Allowable Lateral", "${"%.1f".format(res.lateralCapacity)} kN")
                            ResultRow("Applied Lateral", "${"%.1f".format(res.lateralLoad / res.numberOfPiles)} kN/pile")
                            ResultRow("Utilization", "${"%.0f".format(res.lateralUtilizationRatio * 100)}%")
                            if (res.negativeSkinFriction > 0) {
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                ResultRow("Negative Skin Friction", "${"%.1f".format(res.negativeSkinFriction)} kN")
                            }
                        }
                    }
                }

                // ── Pile Cap Design Card ──
                item {
                    ResultCard(
                        title = "Pile Cap Design",
                        icon = Icons.Default.Domain
                    ) {
                        ResultRow("Cap Size", "${cap.capWidth.toInt()} × ${cap.capLength.toInt()} × ${cap.capThickness.toInt()} mm")
                        ResultRow("Concrete Volume", "${"%.3f".format(cap.concreteVolume)} m³")
                        ResultRow("Steel Weight", "${"%.1f".format(cap.steelWeight)} kg")

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("Shear Checks", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)

                        ShearCheckRow(
                            "Punching Shear",
                            cap.punchingShearStress, cap.punchingShearCapacity, "MPa",
                            cap.punchingShearOk
                        )
                        ShearCheckRow(
                            "Beam Shear",
                            cap.beamShearStress, cap.beamShearCapacity, "MPa",
                            cap.beamShearOk
                        )

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("Reinforcement", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ResultRow("Flexural", cap.flexuralReinforcement.barString)
                        if (cap.punchingReinforcement != null) {
                            ResultRow("Punching Reinforcement", cap.punchingReinforcement!!.barString)
                        }
                    }
                }

                // ── Pile Reinforcement Card ──
                item {
                    ResultCard(
                        title = "Pile Structural Reinforcement",
                        icon = Icons.Default.BuildCircle
                    ) {
                        ResultRow("Longitudinal", reinf.barString)
                        ResultRow("Steel Ratio", "${"%.3f".format(reinf.ratio)} (${"%.2f".format(reinf.ratio * 100)}%)")
                        ResultRow("Status",
                            if (reinf.isSafe) "✓ Adequate" else "✗ Increase bars",
                            if (reinf.isSafe) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }

                // ── Export Buttons ──
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.exportToPdf(context) { }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isExporting,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.PictureAsPdf, null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isExporting) "Exporting..." else "PDF Report")
                        }
                    }
                }

                // ─── DRAWING ────────────────────────────────────
                item {
                    InteractiveDrawingScreen(
                        title = "Pile Foundation Detail",
                        subtitle = "Plan, Section, Elevation & Reinforcement",
                        viewModes = listOf("All", "Plan", "Section", "Elevation"),
                        drawingContent = {
                            ProfessionalPileDrawing(
                                pileDiameter = res.pileDiameterMm,
                                pileLength = res.pileLengthM,
                                numberOfPiles = res.numberOfPiles,
                                pileSpacing = group.spacing,
                                pattern = group.pattern,
                                capWidth = cap.capWidth,
                                capLength = cap.capLength,
                                capThickness = cap.capThickness,
                                columnWidth = res.columnWidth, // from input, use default
                                columnLength = res.columnLength,
                                longitBars = reinf.longitudinalBars,
                                longitDia = reinf.longitudinalDiameter,
                                tiesDia = reinf.tiesDiameter,
                                tiesSpacing = reinf.tiesSpacing,
                                capRebarDia = cap.flexuralReinforcement.diameter,
                                capRebarCount = cap.flexuralReinforcement.bars,
                                soilType = res.soilType,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

// ══════════════════════════════════════════════════════════
// REUSABLE COMPOSABLE COMPONENTS
// ══════════════════════════════════════════════════════════

@Composable
private fun PileInputField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}

@Composable
private fun ResultRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = valueColor)
    }
}

@Composable
private fun ShearCheckRow(
    name: String,
    applied: Double,
    capacity: Double,
    unit: String,
    isOk: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (isOk) "✓" else "✗",
            color = if (isOk) Color(0xFF2E7D32) else Color(0xFFC62828),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(Modifier.width(8.dp))
        Text(name, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text(
            "${"%.2f".format(applied)} / ${"%.2f".format(capacity)} $unit",
            fontSize = 12.sp,
            color = if (isOk) Color(0xFF2E7D32) else Color(0xFFC62828)
        )
    }
}

@Composable
private fun ResultCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
