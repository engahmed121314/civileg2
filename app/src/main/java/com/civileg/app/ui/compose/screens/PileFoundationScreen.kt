package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.livedata.observeAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.domain.*
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalPileDrawing
import com.civileg.app.viewmodel.PileFoundationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PileFoundationScreen(
    viewModel: PileFoundationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var designCode by remember { mutableStateOf("ECP") }

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
                title = { Text(stringResource(R.string.pile_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    Text(stringResource(R.string.pile_header_ecp),
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
                        Text(stringResource(R.string.pile_config_section), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))

                        // Pile type selector
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.pile_type), style = MaterialTheme.typography.labelMedium,
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
                                Text(stringResource(R.string.pile_soil_type), style = MaterialTheme.typography.labelMedium,
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
                        Text(stringResource(R.string.pile_geometry_section), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PileInputField(pileDiameter, stringResource(R.string.pile_diameter), { pileDiameter = it }, Modifier.weight(1f))
                            PileInputField(pileLength, stringResource(R.string.pile_length), { pileLength = it }, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PileInputField(numberOfPiles, stringResource(R.string.pile_num_piles), { numberOfPiles = it }, Modifier.weight(1f), KeyboardType.Number)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.pile_pattern), style = MaterialTheme.typography.labelMedium,
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
                        PileInputField(spacing, stringResource(R.string.pile_spacing_dia), { spacing = it }, Modifier.fillMaxWidth())
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
                            Text(stringResource(R.string.pile_loads_section), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PileInputField(axialLoad, stringResource(R.string.pile_axial_load), { axialLoad = it }, Modifier.weight(1f))
                            PileInputField(lateralLoad, stringResource(R.string.pile_lateral_load), { lateralLoad = it }, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        PileInputField(momentLoad, stringResource(R.string.pile_moment_load), { momentLoad = it }, Modifier.fillMaxWidth())
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
                            Text(stringResource(R.string.pile_materials_section), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PileInputField(fcu, stringResource(R.string.pile_fcu), { fcu = it }, Modifier.weight(1f))
                            PileInputField(fy, stringResource(R.string.pile_fy_cap), { fy = it }, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        PileInputField(fyp, stringResource(R.string.pile_fy_pile), { fyp = it }, Modifier.fillMaxWidth())
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
                                Text(stringResource(R.string.pile_soil_params_section), fontWeight = FontWeight.Bold)
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
                                PileInputField(cu, stringResource(R.string.pile_cu), { cu = it }, Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                            }
                            if (selectedSoilType == SoilType.SAND || selectedSoilType == SoilType.MIXED) {
                                PileInputField(phi, stringResource(R.string.pile_phi), { phi = it }, Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PileInputField(gammaSoil, stringResource(R.string.pile_gamma), { gammaSoil = it }, Modifier.weight(1f))
                                PileInputField(waterTableDepth, stringResource(R.string.pile_water_table), { waterTableDepth = it }, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PileInputField(embedmentDepth, stringResource(R.string.pile_embedment), { embedmentDepth = it }, Modifier.weight(1f))
                                PileInputField(safetyFactor, stringResource(R.string.pile_fos), { safetyFactor = it }, Modifier.weight(1f))
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
                                Text(stringResource(R.string.pile_cap_col_section), fontWeight = FontWeight.Bold)
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
                                PileInputField(columnWidth, stringResource(R.string.pile_col_width), { columnWidth = it }, Modifier.weight(1f))
                                PileInputField(columnLength, stringResource(R.string.pile_col_length), { columnLength = it }, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            PileInputField(capCover, stringResource(R.string.pile_conc_cover), { capCover = it }, Modifier.fillMaxWidth())
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
                                Text(stringResource(R.string.pile_advanced_section), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                            Text(stringResource(R.string.pile_eccentricities), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PileInputField(eccentricityX, stringResource(R.string.pile_ex), { eccentricityX = it }, Modifier.weight(1f))
                                PileInputField(eccentricityY, stringResource(R.string.pile_ey), { eccentricityY = it }, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            PileInputField(scourDepth, stringResource(R.string.pile_scour), { scourDepth = it }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // ─── ERROR DISPLAY (§34 Engineering Error UX) ─────────
            error?.let { err ->
                item {
                    com.civileg.app.ui.designsystem.components.EngineeringErrorState(
                        reason = err,
                        fix = stringResource(R.string.eg_error_fix_hint),
                        actionLabel = stringResource(R.string.eg_error_action_review_inputs)
                    )
                }
            }

            // ─── DESIGN CODE SELECTOR (ECP / ACI / SBC) ───────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("ECP", "ACI", "SBC").forEach { code ->
                        FilterChip(
                            selected = designCode == code,
                            onClick = { designCode = code },
                            label = { Text(code, fontSize = 13.sp,
                                fontWeight = if (designCode == code) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
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
                            designCode = designCode,
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
                        Text(stringResource(R.string.pile_design_button))
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
                                            if (res.isSafe) stringResource(R.string.pile_safe) else stringResource(R.string.pile_unsafe),
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
                        title = stringResource(R.string.pile_capacity_card),
                        icon = Icons.Default.Speed
                    ) {
                        ResultRow(stringResource(R.string.pile_ultimate_cap), "${"%.1f".format(capacity.ultimateCapacity)} kN")
                        ResultRow(stringResource(R.string.pile_allowable_cap), "${"%.1f".format(capacity.allowableCapacity)} kN")
                        ResultRow(stringResource(R.string.pile_shaft_res), "${"%.1f".format(capacity.shaftResistance)} kN")
                        ResultRow(stringResource(R.string.pile_end_bearing), "${"%.1f".format(capacity.endBearingResistance)} kN")
                        ResultRow(stringResource(R.string.pile_fos), "${"%.1f".format(capacity.fs)}")
                        ResultRow(stringResource(R.string.pile_load_per_pile), "${"%.1f".format(res.axialLoad / res.numberOfPiles)} kN")
                    }
                }

                // ── Group Efficiency Card ──
                item {
                    ResultCard(
                        title = stringResource(R.string.pile_group_eff_card),
                        icon = Icons.Default.GridView
                    ) {
                        ResultRow(stringResource(R.string.pile_pattern), "${group.pattern} (${group.numberOfPiles} piles)")
                        ResultRow(stringResource(R.string.rebar_excess_mod), "${"%.3f".format(group.efficiencyFactor)}")
                        ResultRow(stringResource(R.string.pile_indiv_cap), "${"%.1f".format(group.individualCapacity)} kN")
                        ResultRow(stringResource(R.string.pile_group_cap), "${"%.1f".format(group.groupCapacity)} kN")
                        ResultRow(stringResource(R.string.rebar_bar_spacing_label), "${group.spacing.toInt()} mm")
                    }
                }

                // ── Settlement Card ──
                item {
                    ResultCard(
                        title = stringResource(R.string.pile_settlement_card),
                        icon = Icons.Default.Height
                    ) {
                        ResultRow(stringResource(R.string.pile_immediate_set), "${"%.2f".format(settlement.immediateSettlement)} mm")
                        if (settlement.consolidationSettlement > 0.01) {
                            ResultRow(stringResource(R.string.pile_consolidation), "${"%.2f".format(settlement.consolidationSettlement)} mm")
                        }
                        ResultRow(stringResource(R.string.pile_total_set), "${"%.2f".format(settlement.totalSettlement)} mm")
                        ResultRow(stringResource(R.string.pile_allowable_set), "${"%.1f".format(settlement.allowableSettlement)} mm")
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.pile_status))
                            Text(
                                if (settlement.isOk) stringResource(R.string.pile_ok) else stringResource(R.string.pile_exceeds),
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
                            title = stringResource(R.string.pile_lateral_cap_card),
                            icon = Icons.Default.SwapHoriz
                        ) {
                            ResultRow(stringResource(R.string.pile_allow_lateral), "${"%.1f".format(res.lateralCapacity)} kN")
                            ResultRow(stringResource(R.string.pile_applied_lateral), "${"%.1f".format(res.lateralLoad / res.numberOfPiles)} kN/pile")
                            ResultRow(stringResource(R.string.steel_util_ratio), "${"%.0f".format(res.lateralUtilizationRatio * 100)}%")
                            if (res.negativeSkinFriction > 0) {
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                ResultRow(stringResource(R.string.pile_neg_friction), "${"%.1f".format(res.negativeSkinFriction)} kN")
                            }
                        }
                    }
                }

                // ── Pile Cap Design Card ──
                item {
                    ResultCard(
                        title = stringResource(R.string.pile_cap_design_card),
                        icon = Icons.Default.Domain
                    ) {
                        ResultRow(stringResource(R.string.pile_cap_size), "${cap.capWidth.toInt()} × ${cap.capLength.toInt()} × ${cap.capThickness.toInt()} mm")
                        ResultRow(stringResource(R.string.fs_concrete), "${"%.3f".format(cap.concreteVolume)} m³")
                        ResultRow(stringResource(R.string.fs_steel), "${"%.1f".format(cap.steelWeight)} kg")

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(stringResource(R.string.pile_shear_checks), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)

                        ShearCheckRow(
                            stringResource(R.string.pile_punching_shear),
                            cap.punchingShearStress, cap.punchingShearCapacity, "MPa",
                            cap.punchingShearOk
                        )
                        ShearCheckRow(
                            stringResource(R.string.pile_beam_shear),
                            cap.beamShearStress, cap.beamShearCapacity, "MPa",
                            cap.beamShearOk
                        )

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(stringResource(R.string.pile_reinforcement), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ResultRow(stringResource(R.string.pile_flexural), cap.flexuralReinforcement.barString)
                        if (cap.punchingReinforcement != null) {
                            ResultRow(stringResource(R.string.pile_punching_reinf), cap.punchingReinforcement!!.barString)
                        }
                    }
                }

                // ── Pile Reinforcement Card ──
                item {
                    ResultCard(
                        title = stringResource(R.string.pile_struct_reinf_card),
                        icon = Icons.Default.BuildCircle
                    ) {
                        ResultRow(stringResource(R.string.pile_longitudinal), reinf.barString)
                        ResultRow(stringResource(R.string.pile_steel_ratio), "${"%.3f".format(reinf.ratio)} (${"%.2f".format(reinf.ratio * 100)}%)")
                        ResultRow(stringResource(R.string.pile_status),
                            if (reinf.isSafe) stringResource(R.string.pile_adequate) else stringResource(R.string.pile_increase_bars),
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
                            Text(if (isExporting) stringResource(R.string.pile_exporting) else stringResource(R.string.pile_pdf_report))
                        }

                        // ADR-004: canonical P043 model-derived single-sheet DXF (cap section)
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val outcome = withContext(Dispatchers.IO) {
                                        com.civileg.app.utils.CadDxfExporter.exportPileFoundation(
                                            context, res, designCode
                                        )
                                    }
                                    com.civileg.app.utils.ExportUtils.handleDxfOutcome(context, outcome)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isExporting
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("DXF")
                        }
                    }
                }

                // ─── DRAWING ────────────────────────────────────
                item {
                    InteractiveDrawingScreen(
                        title = stringResource(R.string.pile_detail_drawing),
                        subtitle = stringResource(R.string.pile_drawing_sub),
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
                                isSafe = res.isSafe,
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
