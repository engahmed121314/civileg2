package com.civileg.app.ui.compose.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalRetainingWallDrawing
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.app.viewmodel.RetainingWallViewModel
import com.civileg.app.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetainingWallScreen(
    viewModel: RetainingWallViewModel = hiltViewModel(),
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)
    val projects by projectViewModel.allProjects.observeAsState(emptyList())

    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    val rwDefaultName = stringResource(R.string.rw_default_name)
    var designName by remember { mutableStateOf(rwDefaultName) }

    var height by remember { mutableStateOf("4.0") }
    var soilDensity by remember { mutableStateOf("18.0") }
    var frictionAngle by remember { mutableStateOf("30.0") }
    var surcharge by remember { mutableStateOf("10.0") }
    var fcu by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("400") }
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }
    
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_retaining_wall_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(onClick = {
                            viewModel.exportToPdf(context) { /* Handle completion if needed */ }
                        }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export")
                        }
                    }
                    IconButton(onClick = { showSaveDialog = true }, enabled = result != null) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Text(stringResource(R.string.rw_data_input), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text(stringResource(R.string.rw_wall_height)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = soilDensity, onValueChange = { soilDensity = it }, label = { Text(stringResource(R.string.rw_soil_density)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = frictionAngle, onValueChange = { frictionAngle = it }, label = { Text(stringResource(R.string.rw_friction_angle)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = surcharge, onValueChange = { surcharge = it }, label = { Text(stringResource(R.string.rw_surcharge)) }, modifier = Modifier.fillMaxWidth())
            
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = fcu, onValueChange = { fcu = it }, label = { Text("fcu") }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(value = fy, onValueChange = { fy = it }, label = { Text("fy") }, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            DesignCodeSelectorRow(
                selectedCode = selectedCode,
                onCodeSelected = { selectedCode = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.calculateRetainingWallPro(
                        height = height.toDoubleOrNull() ?: 4.0,
                        soilDensity = soilDensity.toDoubleOrNull() ?: 18.0,
                        frictionAngle = frictionAngle.toDoubleOrNull() ?: 30.0,
                        surcharge = surcharge.toDoubleOrNull() ?: 10.0,
                        fcu = fcu.toDoubleOrNull() ?: 25.0,
                        fy = fy.toDoubleOrNull() ?: 400.0,
                        preferredDiameter = 16,
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
                    Icon(Icons.Default.Calculate, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.rw_analyze))
                }
            }

            result?.let { res ->
                Spacer(modifier = Modifier.height(24.dp))
                val ecoColor = when {
                    res.utilizationRatio > 1.0 -> Color.Red
                    res.utilizationRatio > 0.9 -> Color(0xFFFF9800)
                    res.utilizationRatio > 0.4 -> Color(0xFF4CAF50)
                    else -> Color(0xFF2196F3)
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (res.isSafe) Icons.Default.Verified else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = ecoColor
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (res.isSafe) stringResource(R.string.rw_safe) else stringResource(R.string.rw_unsafe),
                                        fontWeight = FontWeight.Bold,
                                        color = ecoColor,
                                        fontSize = 18.sp
                                    )
                                }
                                Text(
                                    stringResource(R.string.consultant_ratio),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box(contentAlignment = Alignment.Center) {
                                val animatedRatio by animateFloatAsState(
                                    targetValue = res.utilizationRatio.toFloat(),
                                    animationSpec = tween(1000), label = ""
                                )
                                CircularProgressIndicator(
                                    progress = { animatedRatio.coerceIn(0f, 1.2f) },
                                    modifier = Modifier.size(60.dp),
                                    strokeWidth = 6.dp,
                                    color = ecoColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Text(
                                    "${(res.utilizationRatio * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        ResultRow(stringResource(R.string.rw_stem_thickness), "${res.stemThickness * 100} cm")
                        ResultRow(stringResource(R.string.rw_base_width), "${res.baseWidth} m")
                        ResultRow(stringResource(R.string.rw_stem_reinforcement), res.stemReinforcement.barString)
                        ResultRow(stringResource(R.string.rw_base_reinforcement), res.baseReinforcement.barString)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                InteractiveDrawingScreen(
                    title = stringResource(R.string.rw_drawing_title),
                    subtitle = "Retaining Wall Detail",
                    viewModes = listOf(stringResource(R.string.view_all), stringResource(R.string.view_section), stringResource(R.string.rw_view_soil_pressure), stringResource(R.string.view_reinforcement)),
                    drawingContent = {
                        ProfessionalRetainingWallDrawing(
                            wallHeight = res.height,
                            wallTopThickness = res.stemThickness * 0.6,
                            wallBottomThickness = res.stemThickness,
                            baseWidth = res.baseWidth,
                            baseThickness = res.stemThickness * 1.2,
                            toeLength = res.baseWidth * 0.25,
                            heelLength = res.baseWidth * 0.6,
                            mainRebarDia = res.stemReinforcement.diameter.toDouble(),
                            mainRebarSpacing = res.stemReinforcement.spacing.toDouble(),
                            distRebarDia = res.stemReinforcement.diameter.toDouble() * 0.7,
                            distRebarSpacing = res.stemReinforcement.spacing.toDouble() * 1.5,
                            baseRebarDia = res.baseReinforcement.diameter.toDouble(),
                            baseRebarSpacing = res.baseReinforcement.spacing.toDouble(),
                            cover = 50.0,
                            backfillAngle = res.backfillAngle,
                            hasKey = true,
                            keyDepth = 150.0,
                            fsOverturning = res.factorOfSafetyOverturning,
                            fsSliding = res.factorOfSafetySliding,
                            maxBearingPressure = res.maxBearingPressure,
                            allowableBearingPressure = 200.0,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
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
                        label = { Text(stringResource(R.string.rw_name_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(stringResource(R.string.select_project), style = MaterialTheme.typography.labelMedium)
                    if (projects.isEmpty()) {
                        Text(stringResource(R.string.rw_no_projects), color = Color.Gray, fontSize = 12.sp)
                    } else {
                        Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                            projects.forEach { project ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedProjectId = project.id }
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
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pId = if (selectedProjectId == -1L) (projects.firstOrNull()?.id ?: 1L) else selectedProjectId
                    result?.let { viewModel.saveRetainingWall(pId, designName, it) }
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
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
