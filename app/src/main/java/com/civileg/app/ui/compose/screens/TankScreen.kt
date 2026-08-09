package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalTankDrawing
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.viewmodel.TankViewModel
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.app.db.Project
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.captureToAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TankScreen(
    viewModel: TankViewModel = hiltViewModel(),
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)
    val projects by projectViewModel.allProjects.observeAsState(emptyList())
    val pdfCaptureLayer = ComposeDrawingCaptureUtil.rememberDrawingCaptureLayer()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * density.density).toInt()
    val screenHeightPx = (config.screenHeightDp * density.density).toInt()

    val configuration = LocalConfiguration.current
    val screenW = configuration.screenWidthDp.dp

    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    val defaultTankName = stringResource(R.string.tank_default_name)
    var designName by remember { mutableStateOf(defaultTankName) }
    
    // تصحيح: تحديد النوع صراحة وحل مشكلة المسميات المفقودة
    var selectedType by remember { mutableStateOf<CalculatorEngine.TankType>(CalculatorEngine.TankType.RECTANGULAR_GROUND) }
    var capacity by remember { mutableStateOf("50.0") }
    var height by remember { mutableStateOf("3.5") }
    var fcu by remember { mutableStateOf("30") }
    var fy by remember { mutableStateOf("400") }
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }

    // Input validation states
    var capacityError by remember { mutableStateOf("") }
    var heightError by remember { mutableStateOf("") }
    var fcuError by remember { mutableStateOf("") }
    var fyError by remember { mutableStateOf("") }

    // Validation messages (captured in composable scope)
    val tankErrInvalid = stringResource(R.string.tank_err_invalid_number)
    val tankErrGtZero = stringResource(R.string.tank_err_gt_zero)
    val tankErrMax10000 = stringResource(R.string.tank_err_max_10000)
    val tankErrMinH = stringResource(R.string.tank_err_min_height)
    val tankErrMaxH = stringResource(R.string.tank_err_max_height)
    val tankErrMinFcu = stringResource(R.string.tank_err_min_fcu)
    val tankErrMaxFcu = stringResource(R.string.tank_err_max_fcu)
    val tankErrMinFy = stringResource(R.string.tank_err_min_fy)
    val tankErrMaxFy = stringResource(R.string.tank_err_max_fy)

    val validateInputs: () -> Boolean = {
        var valid = true
        val cap = capacity.toDoubleOrNull()
        val h = height.toDoubleOrNull()
        val f = fcu.toDoubleOrNull()
        val y = fy.toDoubleOrNull()
        capacityError = when {
            cap == null -> tankErrInvalid
            cap <= 0 -> tankErrGtZero
            cap > 10000 -> tankErrMax10000
            else -> { valid = valid && true; "" }
        }
        if (capacityError.isNotEmpty()) valid = false
        heightError = when {
            h == null -> tankErrInvalid
            h < 1.0 -> tankErrMinH
            h > 10.0 -> tankErrMaxH
            else -> ""
        }
        if (heightError.isNotEmpty()) valid = false
        fcuError = when {
            f == null -> tankErrInvalid
            f < 20.0 -> tankErrMinFcu
            f > 60.0 -> tankErrMaxFcu
            else -> ""
        }
        if (fcuError.isNotEmpty()) valid = false
        fyError = when {
            y == null -> tankErrInvalid
            y < 240.0 -> tankErrMinFy
            y > 500.0 -> tankErrMaxFy
            else -> ""
        }
        if (fyError.isNotEmpty()) valid = false
        valid
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_tank_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SectionHeader(stringResource(R.string.tank_type_section), R.drawable.ic_water) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tank_location), style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val isGround = selectedType == CalculatorEngine.TankType.RECTANGULAR_GROUND || selectedType == CalculatorEngine.TankType.CIRCULAR_GROUND
                        val isElevated = selectedType == CalculatorEngine.TankType.RECTANGULAR_ELEVATED || selectedType == CalculatorEngine.TankType.CIRCULAR_ELEVATED
                        val isUnderground = selectedType == CalculatorEngine.TankType.UNDERGROUND || selectedType == CalculatorEngine.TankType.CIRCULAR_UNDERGROUND

                        FilterChip(selected = isGround, onClick = { selectedType = CalculatorEngine.TankType.RECTANGULAR_GROUND }, label = { Text(stringResource(R.string.tank_location_ground)) })
                        FilterChip(selected = isElevated, onClick = { selectedType = CalculatorEngine.TankType.RECTANGULAR_ELEVATED }, label = { Text(stringResource(R.string.tank_location_elevated)) })
                        FilterChip(selected = isUnderground, onClick = { selectedType = CalculatorEngine.TankType.UNDERGROUND }, label = { Text(stringResource(R.string.tank_location_underground)) })
                    }
                    
                    Text(stringResource(R.string.tank_section_shape), style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isRect = selectedType == CalculatorEngine.TankType.RECTANGULAR_GROUND || selectedType == CalculatorEngine.TankType.RECTANGULAR_ELEVATED || selectedType == CalculatorEngine.TankType.UNDERGROUND
                        
                        FilterChip(
                            selected = isRect, 
                            onClick = { 
                                selectedType = when(selectedType) {
                                    CalculatorEngine.TankType.CIRCULAR_GROUND -> CalculatorEngine.TankType.RECTANGULAR_GROUND
                                    CalculatorEngine.TankType.CIRCULAR_ELEVATED -> CalculatorEngine.TankType.RECTANGULAR_ELEVATED
                                    CalculatorEngine.TankType.CIRCULAR_UNDERGROUND -> CalculatorEngine.TankType.UNDERGROUND
                                    else -> selectedType
                                }
                            }, 
                            label = { Text(stringResource(R.string.tank_shape_rectangular)) }
                        )
                        FilterChip(
                            selected = !isRect, 
                            onClick = { 
                                selectedType = when(selectedType) {
                                    CalculatorEngine.TankType.RECTANGULAR_GROUND -> CalculatorEngine.TankType.CIRCULAR_GROUND
                                    CalculatorEngine.TankType.RECTANGULAR_ELEVATED -> CalculatorEngine.TankType.CIRCULAR_ELEVATED
                                    CalculatorEngine.TankType.UNDERGROUND -> CalculatorEngine.TankType.CIRCULAR_UNDERGROUND
                                    else -> selectedType
                                }
                            }, 
                            label = { Text(stringResource(R.string.tank_shape_circular)) }
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TankInputField(capacity, stringResource(R.string.tank_capacity_m3), { capacity = it }, Modifier.weight(1f), capacityError)
                    TankInputField(height, stringResource(R.string.tank_height_m), { height = it }, Modifier.weight(1f), heightError)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TankInputField(fcu, stringResource(R.string.tank_fcu_mpa), { fcu = it }, Modifier.weight(1f), fcuError)
                    TankInputField(fy, stringResource(R.string.tank_fy_mpa), { fy = it }, Modifier.weight(1f), fyError)
                }
            }

            item {
                DesignCodeSelectorRow(
                    selectedCode = selectedCode,
                    onCodeSelected = { selectedCode = it }
                )
            }

            item {
                Button(
                    onClick = {
                        if (!validateInputs()) return@Button
                        viewModel.calculateTankPro(
                            type = selectedType,
                            capacity = capacity.toDoubleOrNull() ?: 50.0,
                            height = height.toDoubleOrNull() ?: 3.0,
                            fcu = fcu.toDoubleOrNull() ?: 30.0,
                            fy = fy.toDoubleOrNull() ?: 400.0,
                            preferredDiameter = 12,
                            code = selectedCode
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
                        Text(stringResource(R.string.tank_start_design))
                    }
                }
            }

            result?.let { res ->
                item { SectionHeader(stringResource(R.string.tank_analysis_results), R.drawable.ic_calculator) }

                item {
                    val ecoColor = when {
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (res.utilizationRatio <= 1.0) Icons.Default.Verified
                                            else Icons.Default.Dangerous,
                                            contentDescription = null,
                                            tint = ecoColor
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (res.utilizationRatio > 1.0) stringResource(R.string.tank_design_unsafe)
                                            else if (res.utilizationRatio > 0.9) stringResource(R.string.design_caution)
                                            else if (res.utilizationRatio > 0.4) stringResource(R.string.tank_design_ideal)
                                            else stringResource(R.string.tank_section_large),
                                            fontWeight = FontWeight.Bold,
                                            color = ecoColor
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
                                        modifier = Modifier.size(60.dp),
                                        strokeWidth = 6.dp,
                                        color = ecoColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    Text("${(res.utilizationRatio * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // [NEW]: Professional Design Equations & Analysis Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                        border = BorderStroke(1.dp, Color(0xFF81C784))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Functions, contentDescription = null, tint = Color(0xFF2E7D32))
                                Spacer(Modifier.width(8.dp))
                                Text("DESIGN ANALYSIS & FORMULAS", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20), fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            res.suggestions.filter { it.contains("=") }.forEach { formula ->
                                Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF2E7D32), RoundedCornerShape(2.dp)))
                                    Spacer(Modifier.width(8.dp))
                                    Text(formula, style = MaterialTheme.typography.bodySmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }
                            }
                        }
                    }
                }
                
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ResultRow("Dimensions (Internal)", "${res.length} x ${res.width} x ${res.height} m")
                            ResultRow(stringResource(R.string.tank_wall_thickness), "${res.wallThickness.toInt()} mm")
                            ResultRow(stringResource(R.string.tank_base_thickness), "${res.baseThickness.toInt()} mm")
                            ResultRow(stringResource(R.string.tank_max_water_pressure), "${"%.1f".format(res.waterPressure)} kN/m²")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Text("REINFORCEMENT (Main/Hoop)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            ResultRow("Wall Reinforcement", res.wallReinforcement.barString)
                            ResultRow("Base Reinforcement", res.baseReinforcement.barString)
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val captureBitmap = try {
                                        pdfCaptureLayer.captureToAndroidBitmap()
                                    } catch (_: Exception) { null }
                                    viewModel.pendingDrawingBitmap = captureBitmap
                                    viewModel.exportToPdf(context) { /* Handle complete */ }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
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
                        1 -> (480 * wRatio).toInt().coerceIn(300, 600)
                        2 -> (320 * wRatio).toInt().coerceIn(220, 450)
                        3 -> (580 * wRatio).toInt().coerceIn(380, 750)
                        else -> (780 * wRatio).toInt().coerceIn(500, 1000)
                    }
                    InteractiveDrawingScreen(
                        title = stringResource(R.string.tank_drawing_title),
                        subtitle = stringResource(R.string.tank_drawing_subtitle),
                        viewModes = listOf(stringResource(R.string.view_all), stringResource(R.string.view_perspective), stringResource(R.string.view_section), stringResource(R.string.view_reinforcement)),
                        selectedViewMode = selectedViewMode,
                        onViewModeChanged = { selectedViewMode = it },
                        drawingHeightDp = drawingHeight,
                        drawingContent = {
                            ProfessionalTankDrawing(
                                tankType = selectedType.displayName,
                                length = res.length,
                                width = res.width,
                                height = res.height,
                                wallThickness = res.wallThickness,
                                baseThickness = res.baseThickness,
                                waterLevel = res.height * 0.85,
                                verticalRebarDia = res.wallReinforcement.diameter.toDouble(),
                                verticalRebarSpacing = res.wallReinforcement.spacing.toDouble(),
                                horizontalRebarDia = res.baseReinforcement.diameter.toDouble(),
                                horizontalRebarSpacing = res.baseReinforcement.spacing.toDouble(),
                                foundationDepth = if (selectedType == CalculatorEngine.TankType.UNDERGROUND || selectedType == CalculatorEngine.TankType.CIRCULAR_UNDERGROUND) res.height * 0.3 else 0.0,
                                viewMode = selectedViewMode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
        // PDF drawing capture area (invisible, renders at viewMode=0)
        result?.let { res ->
            ComposeDrawingCaptureUtil.DrawingCaptureArea(
                captureLayer = pdfCaptureLayer,
                widthPx = screenWidthPx,
                heightPx = screenHeightPx
            ) {
                Box(modifier = Modifier.background(Color(0xFF1A1A2E))) {
                    ProfessionalTankDrawing(
                        tankType = selectedType.displayName,
                        length = res.length,
                        width = res.width,
                        height = res.height,
                        wallThickness = res.wallThickness,
                        baseThickness = res.baseThickness,
                        waterLevel = res.height * 0.85,
                        verticalRebarDia = res.wallReinforcement.diameter.toDouble(),
                        verticalRebarSpacing = res.wallReinforcement.spacing.toDouble(),
                        horizontalRebarDia = res.baseReinforcement.diameter.toDouble(),
                        horizontalRebarSpacing = res.baseReinforcement.spacing.toDouble(),
                        foundationDepth = if (selectedType == CalculatorEngine.TankType.UNDERGROUND || selectedType == CalculatorEngine.TankType.CIRCULAR_UNDERGROUND) res.height * 0.3 else 0.0,
                        viewMode = 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                        label = { Text(stringResource(R.string.tank_name_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(stringResource(R.string.select_project), style = MaterialTheme.typography.labelMedium)
                    if (projects.isEmpty()) {
                        Text(stringResource(R.string.no_projects_available), color = Color.Gray, fontSize = 12.sp)
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
                    result?.let { viewModel.saveTank(pId, designName, it) }
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
private fun TankTypeChip(type: CalculatorEngine.TankType, label: String, selected: CalculatorEngine.TankType, onSelect: (CalculatorEngine.TankType) -> Unit) {
    FilterChip(
        selected = type == selected,
        onClick = { onSelect(type) },
        label = { Text(label) },
        modifier = Modifier.padding(horizontal = 4.dp)
    )
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
private fun TankInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier, errorText: String = "") {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            isError = errorText.isNotEmpty(),
            supportingText = if (errorText.isNotEmpty()) {{ Text(errorText, color = MaterialTheme.colorScheme.error) }} else null
        )
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
