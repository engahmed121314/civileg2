package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.StairViewModel
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.app.ui.compose.components.drawings.ProfessionalStairDrawing
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StairScreen(
    viewModel: StairViewModel = hiltViewModel(),
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
    var designName by remember { mutableStateOf(stringResource(R.string.stair_default_name)) }
    
    var selectedType by remember { mutableStateOf(CalculatorEngine.StairType.SINGLE_FLIGHT) }
    var span by remember { mutableStateOf("4.0") }
    var riser by remember { mutableStateOf("150") }
    var tread by remember { mutableStateOf("300") }
    var liveLoad by remember { mutableStateOf("4.0") }
    var deadLoad by remember { mutableStateOf("5.0") }
    var expandedType by remember { mutableStateOf(false) }
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_stair_title), fontWeight = FontWeight.Bold) },
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
                        if (result != null) {
                            IconButton(onClick = { viewModel.exportToPdf(context) {} }) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (result != null) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
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
            item { SectionHeader(stringResource(R.string.stair_dimensions), R.drawable.ic_stairs) }

            item {
                ExposedDropdownMenuBox(
                    expanded = expandedType,
                    onExpandedChange = { expandedType = !expandedType },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.stair_type_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                        CalculatorEngine.StairType.values().forEach { type ->
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
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StairInputField(span, stringResource(R.string.stair_horizontal_length), { span = it }, Modifier.weight(1f))
                    StairInputField(riser, stringResource(R.string.stair_riser), { riser = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StairInputField(deadLoad, "D.L (kN/m²)", { deadLoad = it }, Modifier.weight(1f))
                    StairInputField(liveLoad, "L.L (kN/m²)", { liveLoad = it }, Modifier.weight(1f))
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
                        viewModel.calculateStairPro(
                            type = selectedType,
                            span = span.toDoubleOrNull() ?: 4.0,
                            riser = riser.toDoubleOrNull() ?: 150.0,
                            tread = tread.toDoubleOrNull() ?: 300.0,
                            deadLoad = deadLoad.toDoubleOrNull() ?: 5.0,
                            liveLoad = liveLoad.toDoubleOrNull() ?: 4.0,
                            fcu = 25.0,
                            fy = 360.0,
                            preferredDiameter = 12,
                            code = selectedCode
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stair_design_now))
                }
            }

            result?.let { res ->
                item { SectionHeader(stringResource(R.string.stair_results), R.drawable.ic_calculator) }
                
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
                                            else if (res.utilizationRatio > 0.4) stringResource(R.string.beam_section_ideal)
                                            else stringResource(R.string.tank_section_large),
                                            fontWeight = FontWeight.Bold,
                                            color = ecoColor
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
                                        progress = { animatedRatio },
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
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ResultRow(stringResource(R.string.stair_slab_thickness), "${res.thickness.toInt()} mm")
                            ResultRow(stringResource(R.string.stair_main_reinforcement), res.reinforcement.barString)
                            ResultRow(stringResource(R.string.stair_distribution_reinforcement), res.distributionReinforcement.barString)
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.exportToPdf(context) { /* Handle complete */ } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            enabled = !isExporting
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.PictureAsPdf, null)
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
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.save))
                        }
                    }
                }

                item {
                    Text(stringResource(R.string.stair_reinforcement_drawing), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    StairReinforcementDrawing(res, modifier = Modifier.fillMaxWidth().height(400.dp))
                }

                item {
                    InteractiveDrawingScreen(
                        title = stringResource(R.string.stair_drawing_title),
                        subtitle = "Staircase Reinforcement Detail",
                        drawingContent = {
                            ProfessionalStairDrawing(
                                stairWidth = 1200.0,
                                totalHeight = ((res.span * 1000.0) / res.tread).toInt() * res.riser,
                                totalLength = res.span * 1000.0,
                                riserHeight = res.riser,
                                treadWidth = res.tread,
                                slabThickness = res.thickness,
                                mainRebarDia = res.reinforcement.diameter.toDouble(),
                                mainRebarSpacing = res.reinforcement.spacing,
                                distributionDia = res.distributionReinforcement.diameter.toDouble(),
                                distributionSpacing = res.distributionReinforcement.spacing,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }

                item {
                    Text(stringResource(R.string.stair_equations_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    StairFormulasCard()
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
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
                        label = { Text(stringResource(R.string.stair_name_hint)) },
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
                    result?.let { viewModel.saveStair(pId, designName, it) }
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
private fun StairReinforcementDrawing(res: CalculatorEngine.StairResult, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(12.dp)
        ) {
            // ===== DATA EXTRACTION FROM RESULT =====
            val totalSpanMM = res.span.toFloat() * 1000f
            val riserMM = res.riser.toFloat()
            val treadMM = res.tread.toFloat()
            val thicknessMM = res.thickness.toFloat()
            val numSteps = (totalSpanMM / treadMM).toInt().coerceIn(1, 30)
            val totalHeightMM = numSteps * riserMM
            val coverMM = 25f

            // ===== LAYOUT & SCALING =====
            val border = 2.5f
            val marginL = 55f
            val marginR = 50f
            val marginT = 45f
            val marginB = 55f
            val drawW = size.width - marginL - marginR - border * 2
            val drawH = size.height - marginT - marginB - border * 2
            if (drawW <= 0f || drawH <= 0f) return@Canvas

            val fitSpan = totalSpanMM
            val fitHeight = totalHeightMM + thicknessMM + 30f
            val scX = drawW / fitSpan
            val scY = drawH / fitHeight
            val sc = minOf(scX.toFloat(), scY) * 0.88f

            // Origin: bottom-left corner of stair (floor level, start of first riser)
            val ox = marginL + border + (drawW - totalSpanMM * sc) / 2f
            val oy = size.height - marginB - border - (drawH - (totalHeightMM + thicknessMM) * sc) / 2f
            // End point: top of last riser
            val ex = ox + numSteps * treadMM * sc
            val ey = oy - numSteps * riserMM * sc

            // ===== COLORS =====
            val concreteFill = Color(0xFFE6E6E6)
            val concreteStroke = Color(0xFF2C2C2C)
            val mainSteel = Color(0xFFC62828)
            val distSteel = Color(0xFF0D47A1)
            val dimClr = Color(0xFF4A4A4A)
            val borderClr = Color(0xFF1A1A1A)
            val supportClr = Color(0xFF444444)

            // ===== GEOMETRY HELPERS =====
            val slopeAngle = atan2(riserMM.toDouble(), treadMM.toDouble())
            val cosA = cos(slopeAngle).toFloat()
            val sinA = sin(slopeAngle).toFloat()
            // Perpendicular offset for slab thickness (below slope line, into the slab)
            val perpX = thicknessMM * sinA * sc
            val perpY = thicknessMM * cosA * sc
            // Cover offset (from slab soffit toward top surface, perpendicular to slope)
            val covX = coverMM * sinA * sc
            val covY = coverMM * cosA * sc

            // ===== 1. PROFESSIONAL BORDER =====
            drawRect(
                color = borderClr,
                topLeft = Offset(border / 2f, border / 2f),
                size = Size(size.width - border, size.height - border),
                style = Stroke(width = border)
            )
            // Inner border for double-line effect
            drawRect(
                color = borderClr.copy(alpha = 0.3f),
                topLeft = Offset(border + 2f, border + 2f),
                size = Size(size.width - border * 2 - 4f, size.height - border * 2 - 4f),
                style = Stroke(width = 0.5f)
            )

            // ===== 2. STAIR PROFILE (CONCRETE SECTION) =====
            val soffitStartX = ox + perpX
            val soffitStartY = oy + perpY
            val soffitEndX = ex + perpX
            val soffitEndY = ey + perpY

            val profilePath = Path()
            // Start at soffit bottom-left
            profilePath.moveTo(soffitStartX, soffitStartY)
            // Up to floor level at stair start
            profilePath.lineTo(ox, oy)
            // Draw each step: riser up, then tread across
            for (i in 0 until numSteps) {
                val stepX = ox + i * treadMM * sc
                val stepTopY = oy - (i + 1) * riserMM * sc
                val nextX = ox + (i + 1) * treadMM * sc
                // Riser (vertical up)
                profilePath.lineTo(stepX, stepTopY)
                // Tread (horizontal across)
                profilePath.lineTo(nextX, stepTopY)
            }
            // Down to soffit at top end
            profilePath.lineTo(soffitEndX, soffitEndY)
            // Soffit straight back to start
            profilePath.lineTo(soffitStartX, soffitStartY)
            profilePath.close()

            // Fill concrete section
            drawPath(profilePath, concreteFill)
            // Outline concrete section
            drawPath(profilePath, concreteStroke, style = Stroke(width = 2.2f))

            // ===== 3. STEP DIVISION LINES (risers for clarity) =====
            for (i in 0 until numSteps) {
                val x = ox + i * treadMM * sc
                val yBot = oy - i * riserMM * sc
                val yTop = oy - (i + 1) * riserMM * sc
                drawLine(concreteStroke, Offset(x, yBot), Offset(x, yTop), strokeWidth = 1.2f)
            }
            // Last vertical line at the top riser
            if (numSteps > 0) {
                val lastX = ox + numSteps * treadMM * sc
                val lastYTop = ey
                val lastYBot = oy - (numSteps - 1) * riserMM * sc
                drawLine(concreteStroke, Offset(lastX, lastYBot), Offset(lastX, lastYTop), strokeWidth = 1.2f)
            }

            // ===== 4. SOFFIT LINE EMPHASIS (waist slab bottom) =====
            drawLine(
                concreteStroke,
                Offset(soffitStartX, soffitStartY),
                Offset(soffitEndX, soffitEndY),
                strokeWidth = 2.5f
            )

            // ===== 5. SUPPORT CONDITIONS =====
            val supSize = 14f

            // --- Bottom support: pinned (triangle + hatching) ---
            val bsCx = ox
            val bsCy = oy
            drawLine(supportClr, Offset(bsCx, bsCy), Offset(bsCx - supSize, bsCy + supSize), 2f)
            drawLine(supportClr, Offset(bsCx - supSize, bsCy + supSize), Offset(bsCx + supSize, bsCy + supSize), 2f)
            drawLine(supportClr, Offset(bsCx + supSize, bsCy + supSize), Offset(bsCx, bsCy), 2f)
            // Hatching below bottom support
            for (h in 0..5) {
                val hx = bsCx - supSize + h * (supSize * 2f / 5f)
                drawLine(supportClr, Offset(hx, bsCy + supSize), Offset(hx - 5f, bsCy + supSize + 7f), 1f)
            }
            // Ground line
            drawLine(supportClr, Offset(bsCx - supSize - 5f, bsCy + supSize), Offset(bsCx + supSize + 5f, bsCy + supSize), 1.5f)

            // --- Top support: roller (triangle + circle + hatching) ---
            val tsCx = ex
            val tsCy = ey
            drawLine(supportClr, Offset(tsCx, tsCy), Offset(tsCx, tsCy - supSize), 2f)
            drawLine(supportClr, Offset(tsCx - supSize, tsCy - supSize), Offset(tsCx + supSize, tsCy - supSize), 2f)
            // Roller circle
            drawCircle(supportClr, radius = 5f, center = Offset(tsCx, tsCy - supSize - 5f), style = Stroke(1.8f))
            drawCircle(supportClr, radius = 1.5f, center = Offset(tsCx, tsCy - supSize - 5f))
            // Hatching above top support
            for (h in 0..5) {
                val hx = tsCx - supSize + h * (supSize * 2f / 5f)
                drawLine(supportClr, Offset(hx, tsCy - supSize), Offset(hx - 5f, tsCy - supSize - 7f), 1f)
            }
            drawLine(supportClr, Offset(tsCx - supSize - 5f, tsCy - supSize), Offset(tsCx + supSize + 5f, tsCy - supSize), 1.5f)

            // ===== 6. MAIN REINFORCEMENT (diagonal line following slope) =====
            val mbStartX = soffitStartX - covX
            val mbStartY = soffitStartY - covY
            val mbEndX = soffitEndX - covX
            val mbEndY = soffitEndY - covY

            // Extend bars into supports (anchorage)
            val anchLen = 25f
            val mbFullStartX = mbStartX - anchLen * cosA
            val mbFullStartY = mbStartY + anchLen * sinA
            val mbFullEndX = mbEndX + anchLen * cosA
            val mbFullEndY = mbEndY - anchLen * sinA

            // Main bar line
            drawLine(mainSteel, Offset(mbFullStartX, mbFullStartY), Offset(mbFullEndX, mbFullEndY), strokeWidth = 3.5f)

            // 90-degree hooks at both ends
            val hookLen = 14f
            drawLine(mainSteel, Offset(mbFullStartX, mbFullStartY),
                Offset(mbFullStartX - hookLen * sinA, mbFullStartY - hookLen * cosA), 3.5f)
            drawLine(mainSteel, Offset(mbFullEndX, mbFullEndY),
                Offset(mbFullEndX - hookLen * sinA, mbFullEndY - hookLen * cosA), 3.5f)

            // ===== 7. DISTRIBUTION REINFORCEMENT (small circles along span) =====
            val numDistBars = maxOf(4, numSteps)
            for (i in 1..numDistBars) {
                val t = i.toFloat() / (numDistBars + 1f)
                val cx = mbStartX + t * (mbEndX - mbStartX)
                val cy = mbStartY + t * (mbEndY - mbStartY)
                // Open circle (typical rebar cross-section symbol)
                drawCircle(distSteel, radius = 4.5f, center = Offset(cx, cy), style = Stroke(1.8f))
                drawCircle(Color.White, radius = 2.5f, center = Offset(cx, cy))
            }

            // ===== 8. TOP COVER INDICATION =====
            val cvFrac = 0.35f
            val cvTopX = ox + cvFrac * (ex - ox)
            val cvTopY = oy + cvFrac * (ey - oy)
            val cvSoffX = cvTopX + perpX
            val cvSoffY = cvTopY + perpY
            val cvBarX = cvSoffX - covX
            val cvBarY = cvSoffY - covY
            val cvOff = 18f
            val cvD1X = cvSoffX + cvOff * cosA
            val cvD1Y = cvSoffY + cvOff * sinA
            val cvD2X = cvBarX + cvOff * cosA
            val cvD2Y = cvBarY + cvOff * sinA
            drawLine(dimClr, Offset(cvD1X, cvD1Y), Offset(cvD2X, cvD2Y), strokeWidth = 0.8f)
            // Tick marks at soffit and bar
            drawLine(dimClr,
                Offset(cvSoffX - 2f * sinA, cvSoffY - 2f * cosA),
                Offset(cvSoffX + 4f * sinA, cvSoffY + 4f * cosA), 0.8f)
            drawLine(dimClr,
                Offset(cvBarX - 2f * sinA, cvBarY - 2f * cosA),
                Offset(cvBarX + 4f * sinA, cvBarY + 4f * cosA), 0.8f)

            // ===== 9. DIMENSION LINES =====
            val dimPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#4A4A4A")
                textSize = 17f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
            val dimSmPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#4A4A4A")
                textSize = 13f
                textAlign = android.graphics.Paint.Align.CENTER
            }

            fun extLine(x1: Float, y1: Float, x2: Float, y2: Float) {
                drawLine(dimClr, Offset(x1, y1), Offset(x2, y2), strokeWidth = 0.7f)
            }

            fun drawArrow(x: Float, y: Float, angle: Float, sz: Float = 5f) {
                val a1 = angle + 2.6f
                val a2 = angle - 2.6f
                drawLine(dimClr, Offset(x, y),
                    Offset(x + sz * cos(a1.toDouble()).toFloat(), y + sz * sin(a1.toDouble()).toFloat()), 1f)
                drawLine(dimClr, Offset(x, y),
                    Offset(x + sz * cos(a2.toDouble()).toFloat(), y + sz * sin(a2.toDouble()).toFloat()), 1f)
            }

            // --- (a) Total Horizontal Span ---
            val spanDimY = oy + 35f
            extLine(ox, oy + 6f, ox, spanDimY + 4f)
            extLine(ex, oy + 6f, ex, spanDimY + 4f)
            drawLine(dimClr, Offset(ox, spanDimY), Offset(ex, spanDimY), strokeWidth = 1f)
            drawArrow(ox, spanDimY, 0f)
            drawArrow(ex, spanDimY, PI.toFloat())
            drawContext.canvas.nativeCanvas.drawText(
                "L = ${res.span} m", (ox + ex) / 2f, spanDimY + 15f, dimPaint
            )

            // --- (b) Total Vertical Height ---
            val hDimX = ox - 38f
            extLine(ox - 5f, oy, hDimX - 4f, oy)
            extLine(ox - 5f, ey, hDimX - 4f, ey)
            drawLine(dimClr, Offset(hDimX, oy), Offset(hDimX, ey), strokeWidth = 1f)
            drawArrow(hDimX, oy, -(PI.toFloat() / 2f))
            drawArrow(hDimX, ey, PI.toFloat() / 2f)
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(-90f, hDimX - 14f, (oy + ey) / 2f)
            drawContext.canvas.nativeCanvas.drawText(
                "H = ${totalHeightMM.toInt()} mm", hDimX - 14f, (oy + ey) / 2f + 5f, dimPaint
            )
            drawContext.canvas.nativeCanvas.restore()

            // --- (c) Riser Dimension (first step) ---
            if (numSteps >= 1) {
                val rDimX = ox - 14f
                val rTop = oy - riserMM * sc
                extLine(ox - 2f, oy, rDimX - 2f, oy)
                extLine(ox - 2f, rTop, rDimX - 2f, rTop)
                drawLine(dimClr, Offset(rDimX, oy), Offset(rDimX, rTop), strokeWidth = 0.8f)
                drawContext.canvas.nativeCanvas.save()
                drawContext.canvas.nativeCanvas.rotate(-90f, rDimX - 8f, (oy + rTop) / 2f)
                drawContext.canvas.nativeCanvas.drawText(
                    "R=${riserMM.toInt()}", rDimX - 8f, (oy + rTop) / 2f + 4f, dimSmPaint
                )
                drawContext.canvas.nativeCanvas.restore()
            }

            // --- (d) Tread Dimension (first step) ---
            if (numSteps >= 1) {
                val tDimY = oy + 17f
                val tEndX = ox + treadMM * sc
                extLine(ox, oy + 2f, ox, tDimY + 2f)
                extLine(tEndX, oy + 2f, tEndX, tDimY + 2f)
                drawLine(dimClr, Offset(ox, tDimY), Offset(tEndX, tDimY), strokeWidth = 0.8f)
                drawArrow(ox, tDimY, 0f, 4f)
                drawArrow(tEndX, tDimY, PI.toFloat(), 4f)
                drawContext.canvas.nativeCanvas.drawText(
                    "T=${treadMM.toInt()}", (ox + tEndX) / 2f, tDimY + 11f, dimSmPaint
                )
            }

            // --- (e) Waist Slab Thickness (rotated along slope) ---
            val wtFrac = 0.65f
            val wtPx = ox + wtFrac * (ex - ox)
            val wtPy = oy + wtFrac * (ey - oy)
            val wtSx = wtPx + perpX
            val wtSy = wtPy + perpY
            val wtOff = 22f
            val wtD1x = wtPx + wtOff * cosA
            val wtD1y = wtPy + wtOff * sinA
            val wtD2x = wtSx + wtOff * cosA
            val wtD2y = wtSy + wtOff * sinA
            drawLine(dimClr, Offset(wtD1x, wtD1y), Offset(wtD2x, wtD2y), strokeWidth = 0.8f)
            drawLine(dimClr,
                Offset(wtPx - 3f * sinA, wtPy - 3f * cosA),
                Offset(wtPx + 5f * sinA, wtPy + 5f * cosA), 0.8f)
            drawLine(dimClr,
                Offset(wtSx - 3f * sinA, wtSy - 3f * cosA),
                Offset(wtSx + 5f * sinA, wtSy + 5f * cosA), 0.8f)
            drawContext.canvas.nativeCanvas.save()
            val wtMidX = (wtD1x + wtD2x) / 2f
            val wtMidY = (wtD1y + wtD2y) / 2f
            val wtAngleDeg = -Math.toDegrees(slopeAngle).toFloat()
            drawContext.canvas.nativeCanvas.rotate(wtAngleDeg, wtMidX, wtMidY)
            drawContext.canvas.nativeCanvas.drawText(
                "ts=${thicknessMM.toInt()}", wtMidX, wtMidY - 6f, dimSmPaint
            )
            drawContext.canvas.nativeCanvas.restore()

            // ===== 10. TITLE & SCALE TEXT =====
            val titlePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#1A1A1A")
                textSize = 15f
                textAlign = android.graphics.Paint.Align.RIGHT
                isFakeBoldText = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                "STAIR REINFORCEMENT DETAIL - S1", size.width - 10f, marginT - 5f, titlePaint
            )

            val stepsPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#333333")
                textSize = 13f
                textAlign = android.graphics.Paint.Align.LEFT
                isFakeBoldText = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                "Steps: $numSteps  |  R x T = ${riserMM.toInt()} x ${treadMM.toInt()} mm",
                marginL + 5f, marginT - 5f, stepsPaint
            )

            val scalePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#888888")
                textSize = 12f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                "Scale: Not to Scale", size.width / 2f, size.height - 3f, scalePaint
            )

            // Safety status indicator
            val safePaint = android.graphics.Paint().apply {
                textSize = 12f
                textAlign = android.graphics.Paint.Align.RIGHT
                isFakeBoldText = true
            }
            if (res.isSafe) {
                safePaint.color = android.graphics.Color.parseColor("#2E7D32")
                drawContext.canvas.nativeCanvas.drawText(
                    "SAFE", size.width - 10f, size.height - 3f, safePaint
                )
            } else {
                safePaint.color = android.graphics.Color.parseColor("#C62828")
                drawContext.canvas.nativeCanvas.drawText(
                    "UNSAFE", size.width - 10f, size.height - 3f, safePaint
                )
            }
        }
    }
}

@Composable
private fun StairFormulasCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            FormulaItem("1. Wu = 1.4 * DL + 1.6 * LL")
            FormulaItem("2. ts = L / 25 (Simple) or L / 20 (Cantilever)")
            FormulaItem("3. Mu = Wu * L² / 8")
            FormulaItem("4. As = Mu / (fy/gamma_s * j * d)")
        }
    }
}

@Composable
private fun StairInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
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
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
    }
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

@Composable
private fun SectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}
