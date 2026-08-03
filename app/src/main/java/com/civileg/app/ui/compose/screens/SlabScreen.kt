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
import androidx.compose.runtime.livedata.observeAsState
import com.civileg.app.viewmodel.ProjectViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalSlabDrawing
import com.civileg.app.viewmodel.SlabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlabScreen(
    viewModel: SlabViewModel = hiltViewModel(),
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shortSpan by remember { mutableStateOf("4.0") }
    var longSpan by remember { mutableStateOf("5.0") }
    var deadLoad by remember { mutableStateOf("2.5") }
    var liveLoad by remember { mutableStateOf("3.0") }
    var thickness by remember { mutableStateOf("150") }
    var fcu by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("360") }
    var preferredDiameter by remember { mutableStateOf("12") }

    var prestressForce by remember { mutableStateOf("0.0") }
    var dropPanelThickness by remember { mutableStateOf("0.0") }
    var columnSize by remember { mutableStateOf("400") }

    var ribWidth by remember { mutableStateOf("100") }
    var ribSpacing by remember { mutableStateOf("500") }
    var ribSpacingY by remember { mutableStateOf("500") }

    var selectedType by remember { mutableStateOf(CalculatorEngine.SlabType.SOLID) }
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }
    var expandedType by remember { mutableStateOf(false) }
    var expandedCode by remember { mutableStateOf(false) }
    
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)
    val projects by projectViewModel.allProjects.observeAsState(emptyList())

    var showSaveDialog by remember { mutableStateOf(false) }
    var pdfError by remember { mutableStateOf<String?>(null) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    val defaultSlabName = stringResource(R.string.slab_default_name)
    var designName by remember { mutableStateOf(defaultSlabName) }
    var inputError by remember { mutableStateOf<String?>(null) }
    val configuration = LocalConfiguration.current
    val screenW = configuration.screenWidthDp.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_slab_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (result != null) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save_to_project), tint = MaterialTheme.colorScheme.primary)
                        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expandedType,
                        onExpandedChange = { expandedType = !expandedType },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.slab_type_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                            CalculatorEngine.SlabType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.displayName) },
                                    onClick = {
                                        selectedType = type
                                        expandedType = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = expandedCode,
                        onExpandedChange = { expandedCode = !expandedCode },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedCode.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.seismic_pdf_code)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCode) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(expanded = expandedCode, onDismissRequest = { expandedCode = false }) {
                            CalculatorEngine.DesignCode.values().forEach { code ->
                                DropdownMenuItem(
                                    text = { Text(code.displayName) },
                                    onClick = {
                                        selectedCode = code
                                        expandedCode = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.slab_dimensions_loading), R.drawable.ic_slab) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SlabInputField(shortSpan, stringResource(R.string.span_x_label), { shortSpan = it }, Modifier.weight(1f))
                    SlabInputField(longSpan, stringResource(R.string.span_y_label), { longSpan = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SlabInputField(deadLoad, stringResource(R.string.slab_dl_label), { deadLoad = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    SlabInputField(liveLoad, stringResource(R.string.slab_ll_label), { liveLoad = it }, Modifier.weight(1f))
                }
            }

            item { SectionHeader(stringResource(R.string.beam_material_props), R.drawable.ic_calculator) }

            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SlabInputField(fcu, stringResource(R.string.fcu_label), { fcu = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    SlabInputField(fy, stringResource(R.string.fy_label), { fy = it }, Modifier.weight(1f))
                }
            }
            
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SlabInputField(thickness, stringResource(R.string.slab_thickness_mm), { thickness = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    SlabInputField(preferredDiameter, stringResource(R.string.slab_bar_diameter_mm), { preferredDiameter = it }, Modifier.weight(1f))
                }
            }

            if (selectedType == CalculatorEngine.SlabType.FLAT) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SlabInputField(columnSize, stringResource(R.string.slab_column_width_mm), { columnSize = it }, Modifier.weight(1f))
                        SlabInputField(dropPanelThickness, stringResource(R.string.slab_drop_thickness_mm), { dropPanelThickness = it }, Modifier.weight(1f))
                    }
                }
            }

            if (selectedType == CalculatorEngine.SlabType.POST_TENSION) {
                item {
                    SlabInputField(prestressForce, stringResource(R.string.slab_compressive_force), { prestressForce = it }, Modifier.fillMaxWidth())
                }
            }

            if (selectedType == CalculatorEngine.SlabType.HOLLOW_BLOCK) {
                item {
                    SectionHeader(stringResource(R.string.slab_ribbed_dims), R.drawable.ic_slab)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SlabInputField(ribWidth, stringResource(R.string.slab_rib_width_mm), { ribWidth = it }, Modifier.weight(1f))
                        SlabInputField(ribSpacing, stringResource(R.string.slab_rib_spacing_mm), { ribSpacing = it }, Modifier.weight(1f))
                    }
                }
            }

            if (selectedType == CalculatorEngine.SlabType.WAFFLE) {
                item {
                    SectionHeader(stringResource(R.string.slab_waffle_dims), R.drawable.ic_slab)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SlabInputField(ribWidth, stringResource(R.string.slab_rib_width_mm), { ribWidth = it }, Modifier.weight(1f))
                        SlabInputField(ribSpacing, stringResource(R.string.slab_waffle_spacing_x_mm), { ribSpacing = it }, Modifier.weight(1f))
                    }
                }
                item {
                    SlabInputField(ribSpacingY, stringResource(R.string.slab_waffle_spacing_y_mm), { ribSpacingY = it }, Modifier.fillMaxWidth())
                }
            }

            item {
                // Input validation error display
                inputError?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                Button(
                    onClick = {
                        // Validate inputs
                        val lxVal = shortSpan.toDoubleOrNull()
                        val lyVal = longSpan.toDoubleOrNull()
                        val dlVal = deadLoad.toDoubleOrNull()
                        val llVal = liveLoad.toDoubleOrNull()
                        val fcuVal = fcu.toDoubleOrNull()
                        val fyVal = fy.toDoubleOrNull()
                        val tVal = thickness.toDoubleOrNull()

                        if (lxVal == null || lyVal == null || dlVal == null || llVal == null ||
                            fcuVal == null || fyVal == null || tVal == null) {
                            inputError = "Please enter valid values for all fields"
                            return@Button
                        }
                        if (lxVal <= 0 || lyVal <= 0 || dlVal < 0 || llVal < 0 ||
                            fcuVal < 15 || fcuVal > 60 || fyVal < 200 || fyVal > 700 ||
                            tVal < 50 || tVal > 500) {
                            inputError = "Check value ranges: fcu 15-60, fy 200-700, thickness 50-500"
                            return@Button
                        }
                        inputError = null
                        viewModel.calculateSlab(
                            spanX = lxVal,
                            spanY = lyVal,
                            deadLoad = dlVal,
                            liveLoad = llVal,
                            fcu = fcuVal,
                            fy = fyVal,
                            thickness = tVal,
                            preferredDiameter = preferredDiameter.toIntOrNull() ?: 12,
                            type = selectedType,
                            code = selectedCode,
                            prestressForce = prestressForce.toDoubleOrNull() ?: 0.0,
                            dropPanelThickness = dropPanelThickness.toDoubleOrNull() ?: 0.0,
                            columnSize = columnSize.toDoubleOrNull() ?: 400.0
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Calculate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.slab_design_now))
                    }
                }
            }

            result?.let { res ->
                item { SectionHeader(stringResource(R.string.slab_results), R.drawable.ic_calculator) }
                
                item { SlabResultCard(res) }

                if (res.safetyChecks.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.safety_checks),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                res.safetyChecks.forEach { check ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (check.isSafe) Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (check.isSafe) Color(0xFF2E7D32) else Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(check.name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                        Text(
                                            "${"%.2f".format(check.value)} / ${"%.2f".format(check.limit)} ${check.unit}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (check.isSafe) Color(0xFF2E7D32) else Color.Red
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(stringResource(R.string.slab_equations_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    SlabFormulasCard()
                }

                item {
                    val pdfErrorMsg = stringResource(R.string.beam_pdf_error)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.exportToPdf(context) { file ->
                                    if (file == null) {
                                        pdfError = pdfErrorMsg
                                    } else {
                                        pdfError = null
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isExporting
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.pdf_report))
                            }
                        }

                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.save))
                        }
                    }
                }

                item {
                    var selectedViewMode by remember { mutableStateOf(0) }
                    // Responsive height: scale proportionally to screen width
                    val wRatio = screenW.value / 360f  // baseline 360dp
                    val drawingHeight = when (selectedViewMode) {
                        1 -> (380 * wRatio).toInt().coerceIn(250, 500)
                        2 -> (280 * wRatio).toInt().coerceIn(200, 400)
                        3 -> (520 * wRatio).toInt().coerceIn(350, 700)
                        else -> (1000 * wRatio).toInt().coerceIn(600, 1400)
                    }
                    InteractiveDrawingScreen(
                        title = stringResource(R.string.slab_drawing_title),
                        subtitle = stringResource(R.string.slab_reinforcement_subtitle),
                        viewModes = listOf(stringResource(R.string.view_all), stringResource(R.string.slab_view_plan), stringResource(R.string.view_section), stringResource(R.string.view_reinforcement)),
                        selectedViewMode = selectedViewMode,
                        onViewModeChanged = { selectedViewMode = it },
                        drawingHeightDp = drawingHeight,
                        drawingContent = {
                            ProfessionalSlabDrawing(
                                slabType = selectedType.displayName,
                                slabThickness = res.thickness.toDouble(),
                                spanX = shortSpan.toDoubleOrNull() ?: 4.0,
                                spanY = longSpan.toDoubleOrNull() ?: 5.0,
                                mainRebarDia = res.reinforcementMain.diameter.toDouble(),
                                mainRebarSpacing = res.reinforcementMain.spacing.toDouble(),
                                distRebarDia = res.reinforcementSecondary.diameter.toDouble(),
                                distRebarSpacing = res.reinforcementSecondary.spacing.toDouble(),
                                cover = 25.0,
                                dropPanelSize = if (selectedType == CalculatorEngine.SlabType.FLAT) (dropPanelThickness.toDoubleOrNull() ?: 0.0) else 0.0,
                                ribWidth = if (selectedType == CalculatorEngine.SlabType.HOLLOW_BLOCK || selectedType == CalculatorEngine.SlabType.WAFFLE) (ribWidth.toDoubleOrNull() ?: 100.0) else 0.0,
                                ribSpacing = if (selectedType == CalculatorEngine.SlabType.HOLLOW_BLOCK || selectedType == CalculatorEngine.SlabType.WAFFLE) (ribSpacing.toDoubleOrNull() ?: 500.0) else 0.0,
                                viewMode = selectedViewMode,
                                modifier = Modifier.fillMaxWidth(),
                                // NEW: Pass real design values from the SlabResult
                                momentX = res.momentX,
                                momentY = res.momentY,
                                factoredLoad = res.totalLoad,
                                fcu = fcu.toDoubleOrNull() ?: 25.0,
                                fy = fy.toDoubleOrNull() ?: 360.0,
                                isSafe = res.isSafe,
                                utilizationRatio = res.utilizationRatio
                            )
                        }
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.save_design_in_project)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = designName,
                        onValueChange = { designName = it },
                        label = { Text(stringResource(R.string.slab_name_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(stringResource(R.string.select_project), style = MaterialTheme.typography.labelMedium)
                    if (projects.isEmpty()) {
                        Text(stringResource(R.string.no_projects_available), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    } else {
                        projects.forEach { project ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedProjectId == project.id,
                                    onClick = { selectedProjectId = project.id }
                                )
                                Text(project.name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pId = if (selectedProjectId == -1L) 1L else selectedProjectId
                    result?.let { viewModel.saveSlab(pId, designName, it) }
                    showSaveDialog = false
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun SlabResultCard(res: CalculatorEngine.SlabResult) {
    val utilizationColor = when {
        res.utilizationRatio > 1.0 -> Color.Red
        res.utilizationRatio > 0.9 -> Color(0xFFFF9800)
        res.utilizationRatio > 0.4 -> Color(0xFF4CAF50)
        else -> Color(0xFF2196F3)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.consultant_ratio),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (res.isSafe) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (res.isSafe) Color(0xFF2E7D32) else Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (res.isSafe) stringResource(R.string.slab_safe) else stringResource(R.string.slab_unsafe),
                            fontWeight = FontWeight.Bold,
                            color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red
                        )
                    }
                }

                Box(contentAlignment = Alignment.Center) {
                    val animatedRatio by animateFloatAsState(
                        targetValue = res.utilizationRatio.toFloat(),
                        animationSpec = tween(1000), label = ""
                    )
                    CircularProgressIndicator(
                        progress = { animatedRatio },
                        modifier = Modifier.size(54.dp),
                        strokeWidth = 5.dp,
                        color = utilizationColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        "${(res.utilizationRatio * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ResultItem(stringResource(R.string.slab_thickness_label2), "${res.thickness} mm")
                ResultItem(stringResource(R.string.slab_moment_mx), "${"%.1f".format(res.momentX)} kN.m")
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(stringResource(R.string.slab_main_steel_lx), style = MaterialTheme.typography.labelMedium)
            Text(res.reinforcementMain.barString, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            
            Text(stringResource(R.string.slab_secondary_steel_ly), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            Text(res.reinforcementSecondary.barString, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SlabFormulasCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            FormulaItem("1. Wu = 1.4 * DL + 1.6 * LL")
            FormulaItem("2. Mx = Wu * Lx² / 8 (or Code Alpha/Beta Factors)")
            FormulaItem("3. ts_min = L / Factor (Deflection check)")
            FormulaItem("4. As = Mu / (fy/gamma_s * j * d)")
            FormulaItem("5. Check Punching: q_p = Wu * (Area) / (Perimeter * d) < q_p_limit")
        }
    }
}

@Composable
private fun SectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SlabInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun FormulaItem(formula: String) {
    Text(
        text = formula,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.secondary
    )
}
