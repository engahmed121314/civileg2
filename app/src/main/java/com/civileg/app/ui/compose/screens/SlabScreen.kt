package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlin.math.ceil

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

    // Support conditions for edges
    var edgeA by remember { mutableStateOf(0) } // Short left
    var edgeB by remember { mutableStateOf(0) } // Short right
    var edgeC by remember { mutableStateOf(0) } // Long left
    var edgeD by remember { mutableStateOf(0) } // Long right

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
    var showCalcSteps by remember { mutableStateOf(false) }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ===== SLAB TYPE & CODE SELECTORS =====
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    onClick = { selectedType = type; expandedType = false }
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
                                    onClick = { selectedCode = code; expandedCode = false }
                                )
                            }
                        }
                    }
                }
            }

            // ===== DIMENSIONS & LOADING =====
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

            // ===== MATERIAL PROPERTIES =====
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

            // ===== SUPPORT CONDITIONS (for Solid, Waffle slabs) =====
            if (selectedType == CalculatorEngine.SlabType.SOLID || selectedType == CalculatorEngine.SlabType.WAFFLE) {
                item { SectionHeader("Support Conditions / شروط التثبيت", R.drawable.ic_slab) }
                item {
                    val edgeLabels = listOf("Edge A (Lx left)", "Edge B (Lx right)", "Edge C (Ly left)", "Edge D (Ly right)")
                    val edgeStates = listOf(edgeA, edgeB, edgeC, edgeD)
                    val edgeSetters = listOf({ v: Int -> edgeA = v }, { v: Int -> edgeB = v }, { v: Int -> edgeC = v }, { v: Int -> edgeD = v })
                    val supportOptions = listOf("Simply Supported", "Fixed", "Free")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        edgeLabels.forEachIndexed { i, label ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, fontSize = 11.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                supportOptions.forEachIndexed { j, opt ->
                                    val selected = edgeStates[i] == j
                                    FilterChip(
                                        selected = selected,
                                        onClick = { edgeSetters[i](j) },
                                        label = { Text(opt, fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ===== FLAT SLAB INPUTS =====
            if (selectedType == CalculatorEngine.SlabType.FLAT) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SlabInputField(columnSize, stringResource(R.string.slab_column_width_mm), { columnSize = it }, Modifier.weight(1f))
                        SlabInputField(dropPanelThickness, stringResource(R.string.slab_drop_thickness_mm), { dropPanelThickness = it }, Modifier.weight(1f))
                    }
                }
            }

            // ===== POST TENSION INPUT =====
            if (selectedType == CalculatorEngine.SlabType.POST_TENSION) {
                item {
                    SlabInputField(prestressForce, stringResource(R.string.slab_compressive_force), { prestressForce = it }, Modifier.fillMaxWidth())
                }
            }

            // ===== HOLLOW BLOCK INPUTS =====
            if (selectedType == CalculatorEngine.SlabType.HOLLOW_BLOCK) {
                item { SectionHeader(stringResource(R.string.slab_ribbed_dims), R.drawable.ic_slab) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SlabInputField(ribWidth, stringResource(R.string.slab_rib_width_mm), { ribWidth = it }, Modifier.weight(1f))
                        SlabInputField(ribSpacing, stringResource(R.string.slab_rib_spacing_mm), { ribSpacing = it }, Modifier.weight(1f))
                    }
                }
            }

            // ===== WAFFLE INPUTS =====
            if (selectedType == CalculatorEngine.SlabType.WAFFLE) {
                item { SectionHeader(stringResource(R.string.slab_waffle_dims), R.drawable.ic_slab) }
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

            // ===== DESIGN BUTTON =====
            item {
                inputError?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 2.dp))
                }
                Button(
                    onClick = {
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
                            spanX = lxVal, spanY = lyVal, deadLoad = dlVal, liveLoad = llVal,
                            fcu = fcuVal, fy = fyVal, thickness = tVal, preferredDiameter = preferredDiameter.toIntOrNull() ?: 12,
                            type = selectedType, code = selectedCode,
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

            // ===== RESULTS SECTION =====
            result?.let { res ->
                // Classification Banner
                item {
                    ClassificationBanner(res)
                }

                // Main Results Card
                item { SectionHeader(stringResource(R.string.slab_results), R.drawable.ic_calculator) }
                item { SlabResultCard(res) }

                // Moments Detail Card
                item { MomentsDetailCard(res) }

                // Reinforcement Detail Card (top & bottom)
                item { ReinforcementDetailCard(res) }

                // Safety Checks
                if (res.safetyChecks.isNotEmpty()) {
                    item { SafetyChecksCard(res.safetyChecks) }
                }

                // Shear Detail Card
                item { ShearDetailCard(res) }

                // Deflection & Thickness Card
                item { DeflectionCard(res) }

                // Hollow Block Rib Detail
                if (res.type == CalculatorEngine.SlabType.HOLLOW_BLOCK && res.ribReinforcement.diameter > 0) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Rib Reinforcement / تسليح الكمرات", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                Text("Ø${res.ribReinforcement.diameter} bars in ribs", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Suggestions
                if (res.suggestions.isNotEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFFF8F00), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Suggestions / ملاحظات", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFE65100))
                                }
                                Spacer(Modifier.height(4.dp))
                                res.suggestions.forEach { s ->
                                    Text("• $s", fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
                                }
                            }
                        }
                    }
                }

                // Calculation Steps (Expandable)
                if (res.calculationSteps.isNotEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { showCalcSteps = !showCalcSteps },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Functions, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Calculation Steps / خطوات الحساب", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Icon(
                                        if (showCalcSteps) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                if (showCalcSteps) {
                                    Spacer(Modifier.height(8.dp))
                                    res.calculationSteps.forEach { step ->
                                        Text(step, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(vertical = 1.dp, horizontal = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // Code-Specific Formulas
                item {
                    Text(stringResource(R.string.slab_equations_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    CodeFormulasCard(res.code, res.isOneWay, res.type)
                }

                // Drawing
                item {
                    var selectedViewMode by remember { mutableStateOf(0) }
                    val wRatio = screenW.value / 360f
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
                                isArabic = viewModel.isArabic,
                                modifier = Modifier.fillMaxWidth(),
                                momentX = res.momentX,
                                momentY = res.momentY,
                                factoredLoad = res.totalLoad,
                                fcu = fcu.toDoubleOrNull() ?: 25.0,
                                fy = fy.toDoubleOrNull() ?: 360.0,
                                isSafe = res.isSafe,
                                utilizationRatio = res.utilizationRatio,
                                requiredAsX = res.requiredAsX,
                                providedAsX = res.providedAsX,
                                requiredAsY = res.requiredAsY,
                                providedAsY = res.providedAsY,
                                effectiveDepthX = res.effectiveDepthX,
                                effectiveDepthY = res.effectiveDepthY,
                                shearCheck = res.shearCheck
                            )
                        }
                    )
                }

                // Export Buttons
                item {
                    val pdfErrorMsg = stringResource(R.string.beam_pdf_error)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { viewModel.exportToPdf(context) { file -> if (file == null) pdfError = pdfErrorMsg else pdfError = null } },
                            modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(12.dp), enabled = !isExporting
                        ) {
                            if (isExporting) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            else { Icon(Icons.Default.PictureAsPdf, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.pdf_report), maxLines = 1, fontSize = 12.sp) }
                        }
                        OutlinedButton(
                            onClick = { viewModel.exportToDxf(context) { _ -> } },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), enabled = !isExporting
                        ) {
                            Icon(Icons.Default.Draw, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("DXF", maxLines = 1, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.save), maxLines = 1, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // ===== Save Dialog =====
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.save_design_in_project)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = designName, onValueChange = { designName = it }, label = { Text(stringResource(R.string.slab_name_hint)) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.select_project), style = MaterialTheme.typography.labelMedium)
                    if (projects.isEmpty()) {
                        Text(stringResource(R.string.no_projects_available), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    } else {
                        projects.forEach { project ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedProjectId == project.id, onClick = { selectedProjectId = project.id })
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
                }) { Text(stringResource(R.string.save), maxLines = 1, fontSize = 12.sp) }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// ===== CLASSIFICATION BANNER =====
@Composable
private fun ClassificationBanner(res: CalculatorEngine.SlabResult) {
    val bgColor = when {
        res.isOneWay -> Color(0xFFE3F2FD)
        else -> Color(0xFFF3E5F5)
    }
    val classText = if (res.isOneWay) "One-Way Slab / بلاطة اتجاه واحد (Ly/Lx = ${"%.2f".format(res.aspectRatio)} > 2)"
                     else "Two-Way Slab / بلاطة اتجاهين (Ly/Lx = ${"%.2f".format(res.aspectRatio)})"
    }
    Card(colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(classText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Self-weight = ${"%.2f".format(res.selfWeight)} kN/m² | Total DL = ${"%.2f".format(res.totalDeadLoad)} kN/m² | Wu = ${"%.2f".format(res.totalLoad)} kN/m²", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ===== MAIN RESULT CARD =====
@Composable
private fun SlabResultCard(res: CalculatorEngine.SlabResult) {
    val utilizationColor = when {
        res.utilizationRatio > 1.0 -> Color.Red
        res.utilizationRatio > 0.9 -> Color(0xFFFF9800)
        res.utilizationRatio > 0.4 -> Color(0xFF4CAF50)
        else -> Color(0xFF2196F3)
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.consultant_ratio), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (res.isSafe) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = if (res.isSafe) Color(0xFF2E7D32) else Color.Red, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (res.isSafe) stringResource(R.string.slab_safe) else stringResource(R.string.slab_unsafe), fontWeight = FontWeight.Bold, color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red)
                    }
                }
                Box(contentAlignment = Alignment.Center) {
                    val animatedRatio by animateFloatAsState(targetValue = res.utilizationRatio.toFloat(), animationSpec = tween(1000), label = "")
                    CircularProgressIndicator(progress = { animatedRatio }, modifier = Modifier.size(54.dp), strokeWidth = 5.dp, color = utilizationColor, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Text("${(res.utilizationRatio * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ResultItem("Thickness / السمك", "${res.thickness} mm")
                ResultItem("Min h / أدنى سمك", "${"%.0f".format(res.minThickness)} mm")
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ResultItem("Mx (governing)", "${"%.2f".format(res.momentX)} kN.m/m")
                ResultItem("My (governing)", "${"%.2f".format(res.momentY)} kN.m/m")
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ResultItem("d (short)", "${"%.0f".format(res.effectiveDepthShort)} mm")
                ResultItem("d (long)", "${"%.0f".format(res.effectiveDepthLong)} mm")
            }
        }
    }
}

// ===== MOMENTS DETAIL CARD =====
@Composable
private fun MomentsDetailCard(res: CalculatorEngine.SlabResult) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Bending Moments / عزوم الانحناء", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            // Moment coefficients
            Text("Coefficients: α+ = ${"%.4f".format(res.momentCoeffPosX)}, α- = ${"%.4f".format(res.momentCoeffNegX)}, β+ = ${"%.4f".format(res.momentCoeffPosY)}, β- = ${"%.4f".format(res.momentCoeffNegY)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Mx+ (bottom)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%.2f".format(res.momentPositiveX)} kN.m/m", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column {
                    Text("Mx- (top)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%.2f".format(res.momentNegativeX)} kN.m/m", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column {
                    Text("My+ (bottom)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%.2f".format(res.momentPositiveY)} kN.m/m", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column {
                    Text("My- (top)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%.2f".format(res.momentNegativeY)} kN.m/m", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

// ===== REINFORCEMENT DETAIL CARD =====
@Composable
private fun ReinforcementDetailCard(res: CalculatorEngine.SlabResult) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GridOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reinforcement / التسليح", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))

            // Bottom X
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Bot X (short dir):", modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text(res.reinforcementBottomX.barString, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Text("As=${"%.0f".format(res.providedAsX)} mm²/m", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Bottom Y
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Bot Y (long dir):", modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text(res.reinforcementBottomY.barString, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Text("As=${"%.0f".format(res.providedAsY)} mm²/m", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Top X (only if there's negative moment)
            if (res.reinforcementTopX.diameter > 0) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("Top Steel (Negative Moment) / حديد علوي", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Top X:", modifier = Modifier.weight(1f), fontSize = 12.sp)
                    Text(res.reinforcementTopX.barString, fontWeight = FontWeight.Bold, color = Color(0xFFE65100), fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Top Y:", modifier = Modifier.weight(1f), fontSize = 12.sp)
                    Text(res.reinforcementTopY.barString, fontWeight = FontWeight.Bold, color = Color(0xFFE65100), fontSize = 13.sp)
                }
            }

            // Required vs Provided
            Spacer(Modifier.height(4.dp))
            Text("As_req(X) = ${"%.0f".format(res.requiredAsX)} mm²/m → Provided = ${"%.0f".format(res.providedAsX)} mm²/m", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("As_req(Y) = ${"%.0f".format(res.requiredAsY)} mm²/m → Provided = ${"%.0f".format(res.providedAsY)} mm²/m", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ===== SHEAR DETAIL CARD =====
@Composable
private fun ShearDetailCard(res: CalculatorEngine.SlabResult) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Shear Check / فحص القص", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Vu(X) = ${"%.1f".format(res.shearVuX)} kN/m", fontSize = 12.sp)
                    Text("Vc(X) = ${"%.1f".format(res.shearVcX)} kN/m", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (res.shearVuX <= res.shearVcX) Color(0xFF2E7D32) else Color.Red)
                }
                if (!res.isOneWay) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vu(Y) = ${"%.1f".format(res.shearVuY)} kN/m", fontSize = 12.sp)
                        Text("Vc(Y) = ${"%.1f".format(res.shearVcY)} kN/m", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (res.shearVuY <= res.shearVcY) Color(0xFF2E7D32) else Color.Red)
                    }
                }
            }
            if (res.type == CalculatorEngine.SlabType.FLAT) {
                Spacer(Modifier.height(4.dp))
                Text("Punching Shear: vu = ${"%.2f".format(res.shearCheck)} MPa  →  ${if (res.punchingSafe) "OK" else "FAIL"}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (res.punchingSafe) Color(0xFF2E7D32) else Color.Red)
            }
        }
    }
}

// ===== DEFLECTION CARD =====
@Composable
private fun DeflectionCard(res: CalculatorEngine.SlabResult) {
    val deflOk = res.deflectionRatio <= 1.0
    Card(colors = CardDefaults.cardColors(containerColor = if (deflOk) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (deflOk) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = if (deflOk) Color(0xFF2E7D32) else Color.Red, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Deflection & Thickness / الانحراف والسمك", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text("Min thickness (code) = ${"%.0f".format(res.minThickness)} mm", fontSize = 12.sp)
            Text("Provided thickness = ${res.thickness} mm", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Concrete = ${"%.3f".format(res.concreteVolume)} m³ | Steel = ${"%.1f".format(res.steelWeight)} kg", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ===== SAFETY CHECKS CARD =====
@Composable
private fun SafetyChecksCard(checks: List<CalculatorEngine.DesignSafetyCheck>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.safety_checks), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            checks.forEach { check ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (check.isSafe) Icons.Default.CheckCircle else Icons.Default.Error, contentDescription = null, tint = if (check.isSafe) Color(0xFF2E7D32) else Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(check.name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Text("${"%.2f".format(check.value)} / ${"%.2f".format(check.limit)} ${check.unit}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (check.isSafe) Color(0xFF2E7D32) else Color.Red)
                }
            }
        }
    }
}

// ===== CODE-SPECIFIC FORMULAS =====
@Composable
private fun CodeFormulasCard(code: CalculatorEngine.DesignCode, isOneWay: Boolean, type: CalculatorEngine.SlabType) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            val codeName = when(code) {
                CalculatorEngine.DesignCode.ACI -> "ACI 318"
                CalculatorEngine.DesignCode.SAUDI -> "SBC 304"
                else -> "ECP 203"
            }
            Text("$codeName Design Equations", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            when {
                isOneWay -> {
                    FormulaItem("1. Wu = " + if (code == CalculatorEngine.DesignCode.EGYPTIAN) "1.4×DL + 1.6×LL" else "1.2×DL + 1.6×LL")
                    FormulaItem("2. M+ = Wu × L² / 8  (SS)  |  M+ = Wu × L² / 10 (Cont.)")
                    FormulaItem("3. d = h - cover - Ø/2")
                    if (code == CalculatorEngine.DesignCode.EGYPTIAN) {
                        FormulaItem("4. K = Mu / (fcu/γc × b × d²)")
                        FormulaItem("5. ω = 1.25(1 - √(1 - 2.25K))")
                        FormulaItem("6. As = ω × (fcu/γc) / (fy/γs) × b × d")
                        FormulaItem("7. As_min = max(0.6/fy × b × d, 0.0015 × b × h)")
                        FormulaItem("8. Vc = 0.16√(fcu/γc) × b × d")
                    } else {
                        FormulaItem("4. Rn = Mu / (φ × fc' × b × d²)  [fc'=0.8×fcu]")
                        FormulaItem("5. ρ = (0.85×fc'/fy)[1 - √(1 - 2Rn/(0.85×fc'))]")
                        FormulaItem("6. As = ρ × b × d")
                        FormulaItem("7. As_min = max(0.0018×b×h, 0.25√fc'/fy × b×d)")
                        FormulaItem("8. Vc = 0.17√fc' × b × d × φ  [φ=0.75]")
                    }
                }
                type == CalculatorEngine.SlabType.FLAT -> {
                    FormulaItem("1. Mo = Wu × Ly × Lx² / 8  (Total static moment)")
                    FormulaItem("2. Col strip M- = 0.50Mo, M+ = 0.20Mo")
                    FormulaItem("3. Mid strip M- = 0.15Mo, M+ = 0.05Mo")
                    FormulaItem("4. Punching: vu = Vu / (bo × d) ≤ φ×0.33√fc'")
                }
                type == CalculatorEngine.SlabType.HOLLOW_BLOCK -> {
                    FormulaItem("1. Wu = γ_D×DL + γ_L×LL  (includes block weight)")
                    FormulaItem("2. M = Wu × L² / 8  (one-way ribs)")
                    FormulaItem("3. Design rib as rectangular beam (b = rib width)")
                    FormulaItem("4. Topping: min mesh = Ø6@200 or code minimum")
                }
                else -> {
                    // Two-way
                    FormulaItem("1. Wu = " + if (code == CalculatorEngine.DesignCode.EGYPTIAN) "1.4×DL + 1.6×LL" else "1.2×DL + 1.6×LL")
                    FormulaItem("2. Mx = α × Wu × Lx²  (Marcus coefficients)")
                    FormulaItem("3. My = β × Wu × Lx²  (β depends on Ly/Lx ratio)")
                    FormulaItem("4. For SS: α≈0.062-0.117, β≈0.062-0.008 (Ly/Lx=1-2)")
                    FormulaItem("5. For Fixed: α+≈0.024-0.048, α-≈0.051-0.081")
                    if (code == CalculatorEngine.DesignCode.EGYPTIAN) {
                        FormulaItem("6. ECP: K-method for As calculation")
                    } else {
                        FormulaItem("6. ACI/SBC: Rn-ρ method for As calculation")
                    }
                    FormulaItem("7. Max spacing: ECP=min(200,2h,3d), ACI/SBC=min(3h,450)")
                }
            }
        }
    }
}

// ===== UTILITY COMPOSABLES =====
@Composable
private fun ResultItem(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SlabInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier, shape = RoundedCornerShape(12.dp))
}

@Composable
private fun FormulaItem(formula: String) {
    Text(text = formula, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(vertical = 1.dp), color = MaterialTheme.colorScheme.secondary)
}
