package com.civileg.app.ui.compose.screens

import com.civileg.app.ui.responsive.dimens

import com.civileg.app.domain.entities.DesignCode

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.app.db.Project
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.calculations.utils.BbsGenerator
import com.civileg.app.viewmodel.BeamViewModel
import com.civileg.app.ui.compose.components.common.BbsTableCard
import com.civileg.app.ui.compose.components.common.BoqTableCard
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.ui.compose.components.drawings.beamDrawingSummary
import com.civileg.app.ui.compose.components.drawings.ProfessionalBeamDrawing
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.MomentShearForceDiagram
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.captureToAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.launch
import com.civileg.app.ui.defaultDesignCode
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.domain.validation.FieldSpec
import com.civileg.app.domain.validation.FieldKind
import com.civileg.app.domain.validation.FormValidator
import com.civileg.app.ui.compose.validation.*
import androidx.compose.foundation.lazy.rememberLazyListState

private data class BeamDiagramInputSnapshot(
    val span: Double,
    val deadLoad: Double,
    val liveLoad: Double,
    val code: DesignCode,
    val support: CalculatorEngine.SupportType
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeamScreen(
    viewModel: BeamViewModel = hiltViewModel(),
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)
    val error by viewModel.error.observeAsState()
    val projects by projectViewModel.allProjects.observeAsState(emptyList())

    // Show errors via Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
        }
    }
    val pdfCaptureLayer = ComposeDrawingCaptureUtil.rememberDrawingCaptureLayer()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * density.density).toInt()
    val screenHeightPx = (config.screenHeightDp * density.density).toInt()

    var pdfError by remember { mutableStateOf<String?>(null) }
    // ... existing state vars ...
    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    val beamDefaultName = stringResource(R.string.beam_default_name)
    var designName by remember { mutableStateOf(beamDefaultName) }
    
    var width by remember { mutableStateOf("250") }
    var height by remember { mutableStateOf("600") }
    var span by remember { mutableStateOf("5.0") }
    var deadLoad by remember { mutableStateOf("15.0") }
    var liveLoad by remember { mutableStateOf("10.0") }
    var fcu by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("360") }
    var barDiameter by remember { mutableStateOf("16") }
    var selectedSupport by remember { mutableStateOf(CalculatorEngine.SupportType.HINGED_HINGED) }
    var expandedSupport by remember { mutableStateOf(false) }
    var selectedCode by remember { mutableStateOf(defaultDesignCode()) }
    var calculatedDiagramInputs by remember { mutableStateOf<BeamDiagramInputSnapshot?>(null) }

    val form = rememberFormValidationState()
    val listState = rememberLazyListState()
    val fieldRows = mapOf("width" to 2,"height" to 2,"span" to 3,"deadLoad" to 4,"liveLoad" to 4,"fcu" to 6,"fy" to 6,"barDiameter" to 7)
    fun touch(id: String) {
        form.clear(id)
        calculatedDiagramInputs = null
        viewModel.clearResult()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.screen_beam_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SectionHeader(stringResource(R.string.beam_section_support), R.drawable.ic_beam) }

            item {
                ExposedDropdownMenuBox(
                    expanded = expandedSupport,
                    onExpandedChange = { expandedSupport = !expandedSupport },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedSupport.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { AppText(stringResource(R.string.beam_support_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSupport) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expandedSupport, onDismissRequest = { expandedSupport = false }) {
                        CalculatorEngine.SupportType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { AppText(type.displayName) },
                                onClick = {
                                    selectedSupport = type
                                    touch("support")
                                    expandedSupport = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValidatedNumberField(form, "width", width, stringResource(R.string.column_width_t_mm), { width = it; touch("width") }, Modifier.weight(1f))
                    ValidatedNumberField(form, "height", height, stringResource(R.string.beam_depth_h_mm), { height = it; touch("height") }, Modifier.weight(1f))
                }
            }

            item {
                ValidatedNumberField(form, "span", span, stringResource(R.string.beam_effective_length), { span = it; touch("span") }, Modifier.fillMaxWidth())
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValidatedNumberField(form, "deadLoad", deadLoad, stringResource(R.string.beam_dead_load_kn_m), { deadLoad = it; touch("deadLoad") }, Modifier.weight(1f))
                    ValidatedNumberField(form, "liveLoad", liveLoad, stringResource(R.string.beam_live_load_kn_m), { liveLoad = it; touch("liveLoad") }, Modifier.weight(1f))
                }
            }

            item {
                DesignCodeSelectorRow(
                    selectedCode = selectedCode,
                    onCodeSelected = { selectedCode = it; touch("code") }
                )
            }

            item {
                AppText(stringResource(R.string.beam_material_props), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValidatedNumberField(form, "fcu", fcu, stringResource(R.string.beam_fcu_mpa), { fcu = it; touch("fcu") }, Modifier.weight(1f))
                    ValidatedNumberField(form, "fy", fy, stringResource(R.string.beam_fy_mpa), { fy = it; touch("fy") }, Modifier.weight(1f))
                }
            }

            item {
                AppText(stringResource(R.string.beam_rebar_selection), fontWeight = FontWeight.Bold)
                ValidatedNumberField(form, "barDiameter", barDiameter, stringResource(R.string.column_bar_diameter_label), { barDiameter = it; touch("barDiameter") }, Modifier.fillMaxWidth())
            }

            item { FormErrorSummary(form) }
            item {
                AppButton(
                    label = stringResource(R.string.beam_design_now),
                    onClick = {
                        viewModel.clearResult()
                        calculatedDiagramInputs = null
                        val checked = FormValidator.validate(listOf(
                            FieldSpec("width",width,FieldKind.POSITIVE),FieldSpec("height",height,FieldKind.POSITIVE),
                            FieldSpec("span",span,FieldKind.POSITIVE),FieldSpec("deadLoad",deadLoad,FieldKind.NON_NEGATIVE),
                            FieldSpec("liveLoad",liveLoad,FieldKind.NON_NEGATIVE),FieldSpec("fcu",fcu,FieldKind.POSITIVE),
                            FieldSpec("fy",fy,FieldKind.POSITIVE),FieldSpec("barDiameter",barDiameter,FieldKind.POSITIVE_INT)
                        ))
                        form.apply(checked)
                        if (!checked.isValid) {
                            scope.launch { form.focusFirstInvalid(checked) { id -> fieldRows[id]?.let { listState.scrollItemToCenter(it) } } }
                            return@AppButton
                        }
                        val spanValue = checked.value("span")
                        val deadLoadValue = checked.value("deadLoad")
                        val liveLoadValue = checked.value("liveLoad")
                        calculatedDiagramInputs = BeamDiagramInputSnapshot(
                            spanValue,
                            deadLoadValue,
                            liveLoadValue,
                            selectedCode,
                            selectedSupport
                        )
                        viewModel.calculateBeamPro(
                            checked.value("width"),
                            checked.value("height"),
                            spanValue,
                            deadLoadValue,
                            liveLoadValue,
                            checked.value("fcu"),
                            checked.value("fy"),
                            checked.value("barDiameter").toInt(),
                            selectedCode,
                            selectedSupport
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    loading = isLoading,
                    icon = Icons.Default.Calculate
                )
            }

            result?.let { res ->
                item { SectionHeader(stringResource(R.string.beam_results_economy), R.drawable.ic_calculator) }

                // No bar bending schedule: cutting lengths have no verified code basis (see BbsGenerator).
                if (res.codeAcceptanceComplete) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AppText(stringResource(R.string.beam_bbs_unavailable_title), fontWeight = FontWeight.Bold)
                                AppText(
                                    stringResource(R.string.beam_bbs_unavailable_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                val boqRows = runCatching {
                    BoqEstimator.beamRows(res, "B1")
                }.getOrDefault(emptyList())
                if (res.codeAcceptanceComplete && boqRows.isNotEmpty()) {
                    item { BoqTableCard(boqRows, title = "Beam Bill of Quantities") }
                }
                
                item {
                    val ecoColor = when {
                        !res.isSafe -> Color.Red
                        !res.codeAcceptanceComplete -> Color(0xFFFF9800)
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
                                            if (res.isSafe && res.codeAcceptanceComplete) Icons.Default.Verified
                                            else Icons.Default.Dangerous, 
                                            contentDescription = null, 
                                            tint = ecoColor
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        AppText(
                                            if (!res.isSafe) stringResource(R.string.beam_section_unsafe)
                                            else if (!res.codeAcceptanceComplete) "نتيجة أولية: مراجعة اشتراطات الكود غير مكتملة"
                                            else if (res.utilizationRatio > 0.9) stringResource(R.string.design_caution)
                                            else if (res.utilizationRatio > 0.4) stringResource(R.string.beam_section_ideal)
                                            else stringResource(R.string.beam_section_large),
                                            fontWeight = FontWeight.Bold,
                                            color = ecoColor
                                        )
                                    }
                                    AppText(
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
                                    AppText(
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val beamPdfErrorMsg = stringResource(R.string.beam_pdf_error)
                        AppButton(
                            label = stringResource(R.string.pdf_report),
                            onClick = {
                                scope.launch {
                                    val captureBitmap = try {
                                        pdfCaptureLayer.captureToAndroidBitmap()
                                    } catch (_: Exception) { null }
                                    viewModel.pendingDrawingBitmap = captureBitmap
                                    viewModel.exportToPdf(context) { file ->
                                        if (file == null) {
                                            pdfError = beamPdfErrorMsg
                                        } else {
                                            pdfError = null
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isExporting,
                            loading = isExporting,
                            icon = Icons.Default.PictureAsPdf
                        )

                        AppButton(
                            label = stringResource(R.string.save),
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Save,
                            containerOverride = MaterialTheme.colorScheme.secondary,
                            contentOverride = onColorFor(MaterialTheme.colorScheme.secondary)
                        )
                    }
                }

                item {
                    AppText(stringResource(R.string.column_equations_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    BeamFormulasCard()
                }

                item {
                    var selectedViewMode by remember { mutableStateOf(0) }
                    val drawingHeightDp = when (selectedViewMode) {
                        1 -> 400
                        2 -> 350
                        3 -> 300
                        else -> 780
                    }
                    InteractiveDrawingScreen(
                        onExportDxf = { viewModel.exportToDxf(context) },
                        title = stringResource(R.string.column_drawing_title),
                        subtitle = stringResource(R.string.beam_drawing_subtitle),
                        viewModes = listOf(stringResource(R.string.view_all), stringResource(R.string.view_elevation), stringResource(R.string.view_section), stringResource(R.string.view_reinforcement)),
                        selectedViewMode = selectedViewMode,
                        onViewModeChanged = { selectedViewMode = it },
                        drawingHeightDp = drawingHeightDp,
                        drawingContent = {
                            ProfessionalBeamDrawing(
                                beamWidth = res.width.toDouble(),
                                beamDepth = res.depth.toDouble(),
                                span = res.span * 1000.0,
                                mainRebarDia = res.reinforcementBottom.diameter.toDouble(),
                                mainRebarCount = res.reinforcementBottom.numBars,
                                stirrupDia = res.stirrups.diameter.toDouble(),
                                stirrupSpacing = res.stirrups.spacing.toDouble(),
                                cover = 50.0,
                                developmentLength = {
                                    val fcuVal = viewModel.solvedFcu
                                    val fyVal = viewModel.solvedFy
                                    val db = res.reinforcementBottom.diameter.toDouble()
                                    (0.25 * fyVal * db) / (sqrt(fcuVal) * 1.25)
                                }(),
                                lapLength = {
                                    val fcuVal = viewModel.solvedFcu
                                    val fyVal = viewModel.solvedFy
                                    val db = res.reinforcementBottom.diameter.toDouble()
                                    val ld = (0.25 * fyVal * db) / (sqrt(fcuVal) * 1.25)
                                    1.3 * ld
                                }(),
                                isContinuous = res.supportType == CalculatorEngine.SupportType.FIXED_FIXED || res.supportType == CalculatorEngine.SupportType.FIXED_HINGED,
                                supportType = res.supportType,
                                hasTopSteel = res.reinforcementTop.numBars > 0,
                                topRebarDia = res.reinforcementTop.diameter.toDouble(),
                                topRebarCount = res.reinforcementTop.numBars,
                                zones = res.stirrups.zones.map { com.civileg.app.domain.entities.StirrupZone(it.name, it.startLocation, it.endLocation, it.spacing, it.numLegs, it.diameter, it.description) },
                                viewMode = selectedViewMode,
                                resultSummary = beamDrawingSummary(res),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }

                item {
                    val currentInputs = BeamDiagramInputSnapshot(
                        span = FormValidator.parseNumber(span) ?: Double.NaN,
                        deadLoad = FormValidator.parseNumber(deadLoad) ?: Double.NaN,
                        liveLoad = FormValidator.parseNumber(liveLoad) ?: Double.NaN,
                        code = selectedCode,
                        support = selectedSupport
                    )
                    if (!isLoading &&
                        calculatedDiagramInputs == currentInputs &&
                        res.span == currentInputs.span &&
                        res.code == currentInputs.code &&
                        res.supportType == currentInputs.support
                    ) {
                        val diagram = com.civileg.app.domain.calculations.utils.BeamUdlDiagram.samples(res.span,res.appliedShear,res.supportType)
                        MomentShearForceDiagram(
                            span = currentInputs.span,
                            supportType = res.supportType.name,
                            deadLoad = currentInputs.deadLoad,
                            liveLoad = currentInputs.liveLoad,
                            momentValues = diagram.map { (it.xM/res.span).toFloat() to it.momentKnM.toFloat() },
                            shearValues = diagram.map { (it.xM/res.span).toFloat() to it.shearKn.toFloat() },
                            appliedMoment = res.appliedMoment,
                            appliedShear = res.appliedShear,
                            designCode = currentInputs.code,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        // PDF drawing capture area (invisible, renders at viewMode=0)
        result?.let { res ->
            ComposeDrawingCaptureUtil.DrawingCaptureArea(
                captureLayer = pdfCaptureLayer,
                widthPx = screenWidthPx,
                heightPx = screenHeightPx
            ) {
                Box(modifier = Modifier.background(Color(0xFF1A1A2E))) {
                    ProfessionalBeamDrawing(
                        beamWidth = res.width.toDouble(),
                        beamDepth = res.depth.toDouble(),
                        span = res.span * 1000.0,
                        mainRebarDia = res.reinforcementBottom.diameter.toDouble(),
                        mainRebarCount = res.reinforcementBottom.numBars,
                        stirrupDia = res.stirrups.diameter.toDouble(),
                        stirrupSpacing = res.stirrups.spacing.toDouble(),
                        cover = 50.0,
                        developmentLength = {
                            val fcuVal = viewModel.solvedFcu
                            val fyVal = viewModel.solvedFy
                            val db = res.reinforcementBottom.diameter.toDouble()
                            (0.25 * fyVal * db) / (sqrt(fcuVal) * 1.25)
                        }(),
                        lapLength = {
                            val fcuVal = viewModel.solvedFcu
                            val fyVal = viewModel.solvedFy
                            val db = res.reinforcementBottom.diameter.toDouble()
                            val ld = (0.25 * fyVal * db) / (sqrt(fcuVal) * 1.25)
                            1.3 * ld
                        }(),
                        isContinuous = res.supportType == CalculatorEngine.SupportType.FIXED_FIXED || res.supportType == CalculatorEngine.SupportType.FIXED_HINGED,
                        supportType = res.supportType,
                        hasTopSteel = res.reinforcementTop.numBars > 0,
                        topRebarDia = res.reinforcementTop.diameter.toDouble(),
                        topRebarCount = res.reinforcementTop.numBars,
                        zones = res.stirrups.zones.map { com.civileg.app.domain.entities.StirrupZone(it.name, it.startLocation, it.endLocation, it.spacing, it.numLegs, it.diameter, it.description) },
                        viewMode = 0,
                        resultSummary = beamDrawingSummary(res),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        // Loading overlay
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
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
                        AppText("Calculating...", fontSize = 14.sp, color = Color.Gray)
                    }
                }
            }
        }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { AppText(stringResource(R.string.save_design_in_project)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = designName,
                        onValueChange = { designName = it },
                        label = { AppText(stringResource(R.string.beam_name_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    AppText(stringResource(R.string.select_project), style = MaterialTheme.typography.labelMedium)
                    if (projects.isEmpty()) {
                        AppText(stringResource(R.string.no_projects_available), color = Color.Gray, fontSize = 12.sp)
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
                                AppText(project.name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    label = stringResource(R.string.save),
                    fillMaxWidth = false,
                    onClick = {
                        val pId = if (selectedProjectId == -1L) 1L else selectedProjectId
                        result?.let { viewModel.saveBeam(pId, designName, it) }
                        showSaveDialog = false
                    }
                )
            },
            dismissButton = {
                AppButton(
                    label = stringResource(R.string.cancel),
                    onClick = { showSaveDialog = false },
                    style = AppButtonStyle.Text,
                    fillMaxWidth = false
                )
            }
        )
    }
}

@Composable
private fun BeamResultCard(result: CalculatorEngine.BeamResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText(stringResource(R.string.beam_final_results), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            ResultRow(stringResource(R.string.beam_bottom_reinforcement), result.reinforcementBottom.barString)
            ResultRow(stringResource(R.string.beam_top_reinforcement), result.reinforcementTop.barString)
            
            if (result.stirrups.zones.isNotEmpty()) {
                AppText(stringResource(R.string.stirrups_distribution), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                result.stirrups.zones.forEach { zone ->
                    ResultRow("${zone.name} (${String.format(Locale.US, "%.1f", (zone.endLocation - zone.startLocation)/1000.0)}m)", zone.description)
                }
                // [NEW] Display Multi-leg info
                ResultRow(stringResource(R.string.stirrups) + " Legs", "${result.stirrups.numLegs} Branches")
            } else {
                ResultRow(stringResource(R.string.stirrups), result.stirrups.description)
            }
            ResultRow(stringResource(R.string.beam_max_moment), "${"%.1f".format(result.appliedMoment)} kN.m")
            ResultRow(stringResource(R.string.beam_max_shear), "${"%.1f".format(result.appliedShear)} kN")
            ResultRow("Moment Capacity", "${"%.1f".format(result.momentCapacity)} kN.m")
            ResultRow("Shear Capacity", "${"%.1f".format(result.shearCapacity)} kN")
            ResultRow("Deflection", "${"%.2f".format(result.deflection)} mm")
            ResultRow("Allowable Deflection", "${"%.2f".format(result.allowableDeflection)} mm")
            ResultRow("Concrete Volume", "${"%.3f".format(result.concreteVolume)} m³")
            ResultRow("Steel Weight", "${"%.1f".format(result.steelWeight)} kg")
            ResultRow("Cost", "${"%.2f".format(result.cost)}")
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            result.safetyChecks.forEach { check ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AppText(check.name, fontSize = 12.sp)
                    AppText(if (check.isSafe) stringResource(R.string.safe_check) else stringResource(R.string.unsafe_check), color = if (check.isSafe) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            result.shearCodeReview?.let { review ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                AppText("فحوص القص - ${review.basis}", fontWeight = FontWeight.Bold)
                review.checks.forEach { check ->
                    AppText("${if(check.passed) "PASS" else "FAIL"}: ${check.name} [${check.clause}]", fontSize = 12.sp,
                        color = if(check.passed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                    if (!check.passed) AppText(check.redesign, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                if (!result.codeAcceptanceComplete) AppText("هذه النتيجة ليست اعتماداً تنفيذياً. استكمل بيانات الحالة والفحوص الموضحة أدناه.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }

            if (result.warnings.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                AppText("Warnings", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                result.warnings.forEach { warning ->
                    AppText("• $warning", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            if (result.codeNotes.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                AppText("Code Notes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                result.codeNotes.forEach { note ->
                    AppText("• $note", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BeamFormulasCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            FormulaItem("1. Mu = (w_ult * L²) / Factor (Structural Analysis)")
            FormulaItem("2. d = h - Concrete Cover")
            FormulaItem("3. R = Mu / (fcu/gamma_c * b * d²)")
            FormulaItem("4. As = Mu / (fy/gamma_s * j * d)")
            FormulaItem("5. Check Shear: q_u = Vu / (b * d) < q_cu_limit")
        }
    }
}

@Composable
private fun SectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun BeamInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { AppText(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label, fontSize = 14.sp)
        AppText(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun FormulaItem(formula: String) {
    AppText(
        text = formula,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.secondary
    )
}
