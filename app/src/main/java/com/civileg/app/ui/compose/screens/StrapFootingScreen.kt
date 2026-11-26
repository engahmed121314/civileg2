package com.civileg.app.ui.compose.screens

import com.civileg.app.ui.responsive.dimens

import com.civileg.app.domain.entities.DesignCode

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.ui.compose.components.common.BbsTableCard
import com.civileg.app.ui.compose.components.common.BoqTableCard
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.ui.compose.components.common.CalculationStepsCard
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.ui.compose.components.drawings.ProfessionalStrapFootingDrawing
import com.civileg.app.ui.compose.components.drawings.strapFootingDrawingSummary
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.domain.calculations.utils.BbsGenerator
import com.civileg.app.viewmodel.StrapFootingViewModel
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.captureToAndroidBitmap
import kotlinx.coroutines.launch
import kotlin.math.max
import com.civileg.app.ui.defaultDesignCode
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextBlock
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrapFootingScreen(
    viewModel: StrapFootingViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()
    val errorMessage by viewModel.errorMessage.observeAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
        }
    }
    val context = LocalContext.current

    val pdfCaptureLayer = ComposeDrawingCaptureUtil.rememberDrawingCaptureLayer()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * density.density).toInt()
    val screenHeightPx = (config.screenHeightDp * density.density).toInt()

    var selectedViewMode by remember { mutableStateOf(0) }

    var col1Load by remember { mutableStateOf("1200") }
    var col2Load by remember { mutableStateOf("1800") }
    var distance by remember { mutableStateOf("5.0") }
    var col1W by remember { mutableStateOf("300") }
    var col1D by remember { mutableStateOf("600") }
    var col2W by remember { mutableStateOf("300") }
    var col2D by remember { mutableStateOf("700") }
    var soil by remember { mutableStateOf("150") }
    var fcu by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("360") }
    var strapWidth by remember { mutableStateOf("400") }
    var selectedCode by remember { mutableStateOf(defaultDesignCode()) }
    var barDia by remember { mutableStateOf("18") }
    var cover by remember { mutableStateOf("75") }
    // An edited input makes the shown result stale: drop it so it cannot be read, exported or saved as the new case.
    LaunchedEffect(col1Load, col2Load, distance, col1W, col1D, col2W, col2D, soil, fcu, fy, strapWidth, selectedCode, barDia, cover) { viewModel.clearResult() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.home_strap_footing), fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AppText(
                    stringResource(R.string.footing_inputs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StrapInputField(col1Load, "P1 (Edge) kN", { col1Load = it }, Modifier.weight(1f))
                    StrapInputField(col2Load, "P2 (Int) kN", { col2Load = it }, Modifier.weight(1f))
                }
            }

            item {
                StrapInputField(distance, "Distance C/C (m)", { distance = it }, Modifier.fillMaxWidth())
            }

            item {
                AppText("Column 1 (Edge) Dimensions (mm)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StrapInputField(col1W, "Width (b1)", { col1W = it }, Modifier.weight(1f))
                    StrapInputField(col1D, "Depth (a1)", { col1D = it }, Modifier.weight(1f))
                }
            }

            item {
                AppText("Column 2 (Internal) Dimensions (mm)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StrapInputField(col2W, "Width (b2)", { col2W = it }, Modifier.weight(1f))
                    StrapInputField(col2D, "Depth (a2)", { col2D = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StrapInputField(soil, "Soil Capacity (kPa)", { soil = it }, Modifier.weight(1f))
                    StrapInputField(strapWidth, "Strap Beam Width (mm)", { strapWidth = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StrapInputField(fcu, "fcu (MPa)", { fcu = it }, Modifier.weight(1f))
                    StrapInputField(fy, "fy (MPa)", { fy = it }, Modifier.weight(1f))
                }
            }

            item {
                StrapInputField(barDia, "Main Bar Diameter (mm)", { barDia = it }, Modifier.fillMaxWidth())
            }

            item {
                StrapInputField(cover, "Cover (mm)", { cover = it }, Modifier.fillMaxWidth())
            }

            item {
                DesignCodeSelectorRow(
                    selectedCode = selectedCode,
                    onCodeSelected = { selectedCode = it }
                )
            }

            item {
                AppButton(
                    label = stringResource(R.string.calculate),
                    onClick = {
                        viewModel.clearResult()
                        // Blank or non-numeric fields are rejected, never replaced by a default value.
                        fun num(v: String): Double? = v.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
                        val nC1 = num(col1Load); val nC2 = num(col2Load); val nDist = num(distance)
                        val nW1 = num(col1W); val nD1 = num(col1D); val nW2 = num(col2W); val nD2 = num(col2D)
                        val nSoil = num(soil); val nFcu = num(fcu); val nFy = num(fy)
                        val nStrap = num(strapWidth); val nCover = num(cover)
                        val nBar = barDia.trim().toIntOrNull()?.takeIf { it > 0 }
                        if (listOf(nC1, nC2, nDist, nW1, nD1, nW2, nD2, nSoil, nFcu, nFy, nStrap, nCover).any { it == null } || nBar == null) {
                            viewModel.reportInputError("Check the inputs: every field must be a number greater than zero / تأكد من إدخال أرقام أكبر من الصفر في كل الحقول")
                            return@AppButton
                        }
                        viewModel.calculate(
                            nC1!!, nC2!!, nDist!! * 1000.0,
                            nW1!!, nD1!!, nW2!!, nD2!!,
                            nSoil!!, nFcu!!, nFy!!,
                            selectedCode, nBar, nStrap!!
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    loading = isLoading,
                    icon = Icons.Default.Calculate
                )
            }

            error?.let {
                item {
                    AppTextBlock(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            }

            result?.let { res ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                    ) {
                        Column {
                            // View mode selector
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("All", "Plan", "Section", "Detail").forEachIndexed { idx, label ->
                                    FilterChip(
                                        selected = selectedViewMode == idx,
                                        onClick = { selectedViewMode = idx },
                                        label = { AppText(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            ProfessionalStrapFootingDrawing(
                                footing1Length = res.footing1.length,
                                footing1Width = res.footing1.width,
                                footing1Thickness = res.footing1.thickness,
                                footing2Length = res.footing2.length,
                                footing2Width = res.footing2.width,
                                footing2Thickness = res.footing2.thickness,
                                strapWidth = res.strapBeamWidth,
                                strapThickness = res.strapBeamDepth,
                                distanceBetweenColumns = (distance.toDoubleOrNull() ?: 5.0) * 1000.0,
                                col1Width = col1W.toDoubleOrNull() ?: 300.0,
                                col2Width = col2W.toDoubleOrNull() ?: 300.0,
                                rebar1Dia = res.footing1.reinforcementBottom.diameter.toDouble(),
                                rebar1Count = res.footing1.reinforcementBottom.numBars,
                                rebar2Dia = res.footing2.reinforcementBottom.diameter.toDouble(),
                                rebar2Count = res.footing2.reinforcementBottom.numBars,
                                strapDia = res.strapBottomReinforcement.diameter.toDouble(),
                                strapCount = res.strapBottomReinforcement.numBars,
                                cover = cover.toDoubleOrNull() ?: 75.0,
                                viewMode = selectedViewMode,
                                resultSummary = strapFootingDrawingSummary(res),
                                designCode = selectedCode,
                                modifier = Modifier.fillMaxWidth().height(400.dp)
                            )
                        }
                    }
                }

                // Invisible capture layer for PDF export
                item {
                    ComposeDrawingCaptureUtil.DrawingCaptureArea(
                        captureLayer = pdfCaptureLayer,
                        widthPx = screenWidthPx,
                        heightPx = screenHeightPx
                    ) {
                        Box(modifier = Modifier.background(Color(0xFF1A1A2E))) {
                            ProfessionalStrapFootingDrawing(
                                footing1Length = res.footing1.length,
                                footing1Width = res.footing1.width,
                                footing1Thickness = res.footing1.thickness,
                                footing2Length = res.footing2.length,
                                footing2Width = res.footing2.width,
                                footing2Thickness = res.footing2.thickness,
                                strapWidth = res.strapBeamWidth,
                                strapThickness = res.strapBeamDepth,
                                distanceBetweenColumns = (distance.toDoubleOrNull() ?: 5.0) * 1000.0,
                                col1Width = col1W.toDoubleOrNull() ?: 300.0,
                                col2Width = col2W.toDoubleOrNull() ?: 300.0,
                                rebar1Dia = res.footing1.reinforcementBottom.diameter.toDouble(),
                                rebar1Count = res.footing1.reinforcementBottom.numBars,
                                rebar2Dia = res.footing2.reinforcementBottom.diameter.toDouble(),
                                rebar2Count = res.footing2.reinforcementBottom.numBars,
                                strapDia = res.strapBottomReinforcement.diameter.toDouble(),
                                strapCount = res.strapBottomReinforcement.numBars,
                                cover = 75.0,
                                viewMode = selectedViewMode,
                                resultSummary = strapFootingDrawingSummary(res),
                                designCode = selectedCode,
                                modifier = Modifier.fillMaxWidth().height(400.dp)
                            )
                        }
                    }
                }
                
                item {
                    StrapFootingResultCard(res)
                }

                if (res.calculationSteps.isNotEmpty()) {
                    item { CalculationStepsCard(res.calculationSteps, title = "Strap Footing Calculation Steps") }

                    val bbsEntries = runCatching {
                        BbsGenerator.generateStrapFootingBbs(
                            "SF1", res, (distance.toDoubleOrNull() ?: 5.0) * 1000.0
                        )
                    }.getOrDefault(emptyList())
                    if (bbsEntries.isNotEmpty()) {
                        item { BbsTableCard(bbsEntries, title = "Strap Footing Bar Bending Schedule") }
                    }

                    val boqRows = runCatching {
                        BoqEstimator.strapFootingRows(
                            res, distance.toDoubleOrNull() ?: 5.0, "SF1"
                        )
                    }.getOrDefault(emptyList())
                    if (boqRows.isNotEmpty()) {
                        item { BoqTableCard(boqRows, title = "Strap Footing Bill of Quantities") }
                    }
                }
                
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            label = stringResource(R.string.export_pdf),
                            onClick = {
                                scope.launch {
                                    val captureBitmap = try {
                                        pdfCaptureLayer.captureToAndroidBitmap()
                                    } catch (_: Exception) { null }
                                    viewModel.pendingDrawingBitmap = captureBitmap
                                    viewModel.exportToPdf(context) { file ->
                                        if (file != null) ExportUtils.openPdf(context, file)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.PictureAsPdf,
                            containerOverride = MaterialTheme.colorScheme.secondary,
                            contentOverride = onColorFor(MaterialTheme.colorScheme.secondary)
                        )
                        AppButton(
                            label = "DXF",
                            onClick = { viewModel.exportToDxf(context) },
                            modifier = Modifier.weight(1f),
                            style = AppButtonStyle.Outline,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp,
                        color = Color(0xFF1565C0)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AppText(
                        "Calculating... / جاري الحساب...",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun StrapFootingVisualizer(res: CalculatorEngine.StrapFootingResult) {
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val w = size.width
            val h = size.height
            
            val totalL = res.footing1.length + res.footing2.length + 2000.0
            val scale = (w * 0.8f) / totalL.toFloat()
            
            val f1W = res.footing1.width.toFloat() * scale
            val f1L = res.footing1.length.toFloat() * scale
            val f2W = res.footing2.width.toFloat() * scale
            val f2L = res.footing2.length.toFloat() * scale
            val strapW = res.strapBeamWidth.toFloat() * scale
            
            val cy = h / 2
            
            drawRect(Color.Gray, Offset(0f, cy - f1W / 2), Size(f1L, f1W))
            val f2Pos = w - f2L
            drawRect(Color.Gray, Offset(f2Pos, cy - f2W / 2), Size(f2L, f2W))
            drawRect(Color.DarkGray, Offset(f1L, cy - strapW / 2), Size(f2Pos - f1L, strapW))
        }
    }
}

@Composable
fun StrapFootingResultCard(res: CalculatorEngine.StrapFootingResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText("Design Results", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            
            HorizontalDivider()
            
            ResultRow("Footing 1 (Edge)", "${res.footing1.width.toInt()} x ${res.footing1.length.toInt()} x ${res.footing1.thickness.toInt()} mm")
            ResultRow("Reinforcement 1", res.footing1.reinforcementBottom.barString)
            
            Spacer(Modifier.height(8.dp))
            
            ResultRow("Footing 2 (Internal)", "${res.footing2.width.toInt()} x ${res.footing2.length.toInt()} x ${res.footing2.thickness.toInt()} mm")
            ResultRow("Reinforcement 2", res.footing2.reinforcementBottom.barString)
            
            Spacer(Modifier.height(8.dp))
            
            ResultRow("Strap Beam", "${res.strapBeamWidth.toInt()} x ${res.strapBeamDepth.toInt()} mm")
            ResultRow("Top Rebar", res.strapTopReinforcement.barString)
            ResultRow("Bottom Rebar", res.strapBottomReinforcement.barString)
            
            Spacer(Modifier.height(8.dp))
            
            ResultRow("Reactions (R1, R2)", "${res.reactions.first.toInt()}, ${res.reactions.second.toInt()} kN")
            
            Spacer(Modifier.height(8.dp))
            
            ResultRow("Utilization Ratio", "${"%.1f".format(res.utilizationRatio * 100)}%")
            ResultRow("Concrete Volume", "${"%.3f".format(res.concreteVolume)} m³")
            ResultRow("Steel Weight", "${"%.1f".format(res.steelWeight)} kg")
            
            if (!res.isSafe) {
                AppText("⚠ Design Unsafe - Check dimensions", color = Color.Red, fontWeight = FontWeight.Bold)
            }
        }
    }
    
    if (res.safetyChecks.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AppText(
                    "Safety Checks",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                res.safetyChecks.forEach { check ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(
                            if (check.isSafe) "✓" else "✗",
                            color = if (check.isSafe) Color(0xFF2E7D32) else Color(0xFFC62828),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        AppText(check.name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        AppText(
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

@Composable
fun StrapInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { AppText(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label, style = MaterialTheme.typography.bodyMedium)
        AppText(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LoadingIndicator(size: Dp) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        strokeWidth = 2.dp
    )
}
