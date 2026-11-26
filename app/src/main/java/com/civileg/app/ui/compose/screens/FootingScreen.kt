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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.livedata.observeAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.calculations.utils.BbsGenerator
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import com.civileg.app.viewmodel.FootingViewModel
import com.civileg.app.ui.compose.components.common.BbsTableCard
import com.civileg.app.ui.compose.components.common.BoqTableCard
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.ui.compose.components.drawings.footingDrawingSummary
import com.civileg.app.ui.compose.components.common.CalculationStepsCard
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalFootingDrawing
import com.civileg.app.ui.compose.components.drawings.ProfessionalCombinedFootingDrawing
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.app.viewmodel.CombinedFootingViewModel
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.captureToAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import com.civileg.app.ui.defaultDesignCode
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.domain.validation.FieldKind
import com.civileg.app.domain.validation.FieldSpec
import com.civileg.app.domain.validation.FormValidator
import com.civileg.app.ui.compose.validation.FormErrorSummary
import com.civileg.app.ui.compose.validation.OptionalInputGroup
import com.civileg.app.ui.compose.validation.ValidatedNumberField
import com.civileg.app.ui.compose.validation.rememberFormValidationState
import com.civileg.app.ui.compose.validation.scrollItemToCenter
import androidx.compose.foundation.lazy.rememberLazyListState
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootingScreen(
    viewModel: FootingViewModel = hiltViewModel(),
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val result by viewModel.result.observeAsState()
    val solvedInputs by viewModel.solvedInputs.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)
    val projects by projectViewModel.allProjects.observeAsState(emptyList())
    val vmError by viewModel.error.observeAsState()

    // Show errors via Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(vmError) {
        vmError?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
        }
    }

    val pdfCaptureLayer = ComposeDrawingCaptureUtil.rememberDrawingCaptureLayer()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * density.density).toInt()
    val screenHeightPx = (config.screenHeightDp * density.density).toInt()

    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    val footingDefaultName = stringResource(R.string.footing_default_name)
    var designName by remember { mutableStateOf(footingDefaultName) }

    // Pre-select the first available project instead of saving against a hardcoded id.
    LaunchedEffect(showSaveDialog, projects) {
        if (showSaveDialog && projects.isNotEmpty() && projects.none { it.id == selectedProjectId }) {
            selectedProjectId = projects.first().id
        }
    }
    
    var selectedType by remember { mutableStateOf(CalculatorEngine.FootingType.ISOLATED) }
    var expandedType by remember { mutableStateOf(false) }
    var barDiameter by remember { mutableStateOf("16") }
    var barSpacing by remember { mutableStateOf("150") }
    
    var axialLoad by remember { mutableStateOf("1200") }
    var axialServiceLoad by remember { mutableStateOf("") } // required project input, no assumed value
    var soilCapacity by remember { mutableStateOf("150") }
    var colLength by remember { mutableStateOf("600") }
    var colWidth by remember { mutableStateOf("300") }
    var fcu by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("360") }
    
    // Combined Footing and Boundary Parameters
    var axialLoad2 by remember { mutableStateOf("1000") }
    var colDistance by remember { mutableStateOf("3.5") }
    var maxLeft by remember { mutableStateOf("") }
    var maxRight by remember { mutableStateOf("") }
    var selectedCode by remember { mutableStateOf(defaultDesignCode()) }

    // Site quantities (excavation / blinding)
    var excavationDepth by remember { mutableStateOf("1.5") }
    var workingSpace by remember { mutableStateOf("0.5") }
    var blindingThickness by remember { mutableStateOf("0.1") }

    // Optional boundary limits: blank used to mean "no limit". Now an explicit checkbox decides.
    var limitLeftOn by remember { mutableStateOf(false) }
    var limitRightOn by remember { mutableStateOf(false) }

    // Shared field-anchored validation (ui/compose/validation). No silent defaults.
    val form = rememberFormValidationState()
    val listState = rememberLazyListState()
    // Any input change makes the shown result stale: drop it and that field's old error.
    fun touch(id: String) {
        form.clear(id)
        viewModel.clearResult()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.screen_footing_title), fontWeight = FontWeight.Bold) },
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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SectionHeader(stringResource(R.string.footing_inputs), R.drawable.ic_footing) }

            item {
                Column {
                    AppText(stringResource(R.string.footing_type_label), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedType,
                        onExpandedChange = { expandedType = !expandedType }
                    ) {
                        OutlinedTextField(
                            value = selectedType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedType,
                            onDismissRequest = { expandedType = false }
                        ) {
                            CalculatorEngine.FootingType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { AppText(type.displayName) },
                                    onClick = {
                                        if (selectedType != type) { form.clearAll(); viewModel.clearResult() }
                                        selectedType = type
                                        expandedType = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValidatedNumberField(form, "colLength", colLength, stringResource(R.string.footing_column_length_mm), { colLength = it; touch("colLength") }, Modifier.weight(1f))
                    ValidatedNumberField(form, "colWidth", colWidth, stringResource(R.string.footing_column_width_mm), { colWidth = it; touch("colWidth") }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValidatedNumberField(form, "fcu", fcu, stringResource(R.string.footing_fcu_label), { fcu = it; touch("fcu") }, Modifier.weight(1f))
                    ValidatedNumberField(form, "fy", fy, stringResource(R.string.footing_fy_label), { fy = it; touch("fy") }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValidatedNumberField(form, "axialLoad", axialLoad, stringResource(R.string.footing_pu1_label), { axialLoad = it; touch("axialLoad") }, Modifier.weight(1f))
                    ValidatedNumberField(form, "soilCapacity", soilCapacity, stringResource(R.string.footing_soil_label), { soilCapacity = it; touch("soilCapacity") }, Modifier.weight(1f))
                }
            }

            if (selectedType != CalculatorEngine.FootingType.COMBINED) {
                item {
                    // Unfactored column load for plan size and bearing. Pu above is used for strength checks; neither is derived from the other.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ValidatedNumberField(form, "axialServiceLoad", axialServiceLoad, stringResource(R.string.footing_ps_label), { axialServiceLoad = it; touch("axialServiceLoad") }, Modifier.weight(1f))
                    }
                }
            }

            if (selectedType == CalculatorEngine.FootingType.COMBINED) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ValidatedNumberField(form, "axialLoad2", axialLoad2, stringResource(R.string.footing_pu2_label), { axialLoad2 = it; touch("axialLoad2") }, Modifier.weight(1f))
                        ValidatedNumberField(form, "colDistance", colDistance, stringResource(R.string.footing_distance_label), { colDistance = it; touch("colDistance") }, Modifier.weight(1f))
                    }
                }
            }

            item {
                AppText(stringResource(R.string.footing_boundary_label), fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionalInputGroup(
                        label = stringResource(R.string.footing_max_left_label),
                        checked = limitLeftOn,
                        onCheckedChange = { limitLeftOn = it; touch("maxLeft") },
                        testTag = "optional_maxLeft"
                    ) {
                        ValidatedNumberField(form, "maxLeft", maxLeft, stringResource(R.string.footing_max_left_label), { maxLeft = it; touch("maxLeft") }, Modifier.fillMaxWidth())
                    }
                    OptionalInputGroup(
                        label = stringResource(R.string.footing_max_right_label),
                        checked = limitRightOn,
                        onCheckedChange = { limitRightOn = it; touch("maxRight") },
                        testTag = "optional_maxRight"
                    ) {
                        ValidatedNumberField(form, "maxRight", maxRight, stringResource(R.string.footing_max_right_label), { maxRight = it; touch("maxRight") }, Modifier.fillMaxWidth())
                    }
                }
            }

            item {
                AppText(stringResource(R.string.footing_rebar_options), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValidatedNumberField(form, "barDiameter", barDiameter, stringResource(R.string.footing_bar_diameter), { barDiameter = it; touch("barDiameter") }, Modifier.weight(1f))
                    ValidatedNumberField(form, "barSpacing", barSpacing, stringResource(R.string.footing_bar_spacing), { barSpacing = it; touch("barSpacing") }, Modifier.weight(1f))
                }
            }

            item {
                DesignCodeSelectorRow(
                    selectedCode = selectedCode,
                    onCodeSelected = { if (selectedCode != it) viewModel.clearResult(); selectedCode = it }
                )
            }

            item {
                AppText(stringResource(R.string.boq_site_title), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValidatedNumberField(form, "excavationDepth", excavationDepth, stringResource(R.string.boq_excavation_depth), { excavationDepth = it; touch("excavationDepth") }, Modifier.weight(1f))
                    ValidatedNumberField(form, "workingSpace", workingSpace, stringResource(R.string.boq_working_space), { workingSpace = it; touch("workingSpace") }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValidatedNumberField(form, "blindingThickness", blindingThickness, stringResource(R.string.boq_blinding_thickness), { blindingThickness = it; touch("blindingThickness") }, Modifier.weight(1f))
                }
            }


            item {
                // LazyColumn item index per field (the combined-only row shifts later rows by one).
                val shift = 1 // the combined-only row or the service-load row always occupies item 5
                val fieldItemIndex = mapOf(
                    "colLength" to 2, "colWidth" to 2, "fcu" to 3, "fy" to 3,
                    "axialLoad" to 4, "soilCapacity" to 4, "axialServiceLoad" to 5, "axialLoad2" to 5, "colDistance" to 5,
                    "maxLeft" to 5 + shift, "maxRight" to 5 + shift,
                    "barDiameter" to 6 + shift, "barSpacing" to 6 + shift,
                    "excavationDepth" to 8 + shift, "workingSpace" to 8 + shift, "blindingThickness" to 8 + shift
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormErrorSummary(form)
                AppButton(
                    label = stringResource(R.string.footing_design_now),
                    onClick = {
                        viewModel.clearResult() // never leave a stale result on screen
                        val combined = selectedType == CalculatorEngine.FootingType.COMBINED
                        val groups = buildSet {
                            if (combined) add("combined") else add("single")
                            if (limitLeftOn) add("left")
                            if (limitRightOn) add("right")
                        }
                        // Order = on-screen order. Only physical rules (positive / non-negative / whole);
                        // no code limits are invented here.
                        val checked = FormValidator.validate(
                            listOf(
                                FieldSpec("colLength", colLength, FieldKind.POSITIVE),
                                FieldSpec("colWidth", colWidth, FieldKind.POSITIVE),
                                FieldSpec("fcu", fcu, FieldKind.POSITIVE),
                                FieldSpec("fy", fy, FieldKind.POSITIVE),
                                FieldSpec("axialLoad", axialLoad, FieldKind.POSITIVE),
                                FieldSpec("soilCapacity", soilCapacity, FieldKind.POSITIVE),
                                FieldSpec("axialServiceLoad", axialServiceLoad, FieldKind.POSITIVE, group = "single"),
                                // Engine runs the combined design only when p2 > 0 and distance > 0.
                                FieldSpec("axialLoad2", axialLoad2, FieldKind.POSITIVE, group = "combined"),
                                FieldSpec("colDistance", colDistance, FieldKind.POSITIVE, group = "combined"),
                                FieldSpec("maxLeft", maxLeft, FieldKind.POSITIVE, group = "left"),
                                FieldSpec("maxRight", maxRight, FieldKind.POSITIVE, group = "right"),
                                FieldSpec("barDiameter", barDiameter, FieldKind.POSITIVE_INT),
                                FieldSpec("barSpacing", barSpacing, FieldKind.POSITIVE),
                                FieldSpec("excavationDepth", excavationDepth, FieldKind.NON_NEGATIVE),
                                FieldSpec("workingSpace", workingSpace, FieldKind.NON_NEGATIVE),
                                FieldSpec("blindingThickness", blindingThickness, FieldKind.NON_NEGATIVE)
                            ),
                            activeGroups = groups
                        )
                        form.apply(checked)
                        if (!checked.isValid) {
                            scope.launch {
                                form.focusFirstInvalid(checked) { id ->
                                    fieldItemIndex[id]?.let { listState.scrollItemToCenter(it) }
                                }
                            }
                            return@AppButton
                        }
                        viewModel.calculateFooting(
                            type = selectedType,
                            p = checked.value("axialLoad"),
                            ps = if (combined) null else checked.value("axialServiceLoad"),
                            fcu = checked.value("fcu"),
                            fy = checked.value("fy"),
                            soil = checked.value("soilCapacity"),
                            colB = checked.value("colWidth"),
                            colT = checked.value("colLength"),
                            code = selectedCode,
                            preferredDiameter = checked.value("barDiameter").toInt(),
                            preferredSpacing = checked.value("barSpacing"),
                            // Not applicable (type != COMBINED): engine ignores them; 0.0 = "not used".
                            p2 = checked.valueOrNull("axialLoad2") ?: 0.0,
                            distance = (checked.valueOrNull("colDistance") ?: 0.0) * 1000.0,
                            maxLeft = checked.valueOrNull("maxLeft")?.let { it * 1000.0 },
                            maxRight = checked.valueOrNull("maxRight")?.let { it * 1000.0 },
                            excavationDepthM = checked.value("excavationDepth"),
                            workingSpaceM = checked.value("workingSpace"),
                            blindingThicknessM = checked.value("blindingThickness")
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    loading = isLoading,
                    icon = Icons.Default.Calculate
                )
                }
            }

            result?.let { res ->
                item { SectionHeader(stringResource(R.string.slab_results), R.drawable.ic_calculator) }
                
                item {
                    // isOptimal is an unassessed default, never a verdict; the header follows the real safety checks only.
                    val ecoColor = if (res.isSafe) Color(0xFFF57C00) else Color(0xFFC62828)
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
                                        Icon(Icons.Default.Info, 
                                            contentDescription = null, tint = ecoColor)
                                        Spacer(Modifier.width(8.dp))
                                        AppText(
                                            if (res.isSafe) stringResource(R.string.footing_safe_review)
                                            else stringResource(R.string.footing_unsafe_review),
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
                                        color = when {
                                            res.utilizationRatio > 1.0 -> Color.Red
                                            res.utilizationRatio > 0.9 -> Color(0xFFFF9800)
                                            res.utilizationRatio > 0.4 -> Color(0xFF4CAF50)
                                            else -> Color(0xFF2196F3)
                                        },
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    AppText(
                                        "${(res.utilizationRatio * 100).toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))

                            ResultRow(stringResource(R.string.footing_dims), "${res.width.toInt()} x ${res.length.toInt()} mm")
                            ResultRow(stringResource(R.string.footing_thickness_label2), "${res.thickness.toInt()} mm")
                            ResultRow(stringResource(R.string.column_concrete_vol), "${"%.2f".format(res.concreteVolume)} m³")
                            ResultRow(stringResource(R.string.column_steel_weight), "${"%.2f".format(res.steelWeight)} kg")
                            ResultRow(stringResource(R.string.footing_allowable_pressure), "${"%.1f".format(res.allowablePressure)} kN/m²")
                            ResultRow(stringResource(R.string.column_estimated_cost), "Unavailable (no price basis)")
                            if (selectedType == CalculatorEngine.FootingType.COMBINED) {
                                Spacer(Modifier.height(8.dp))
                                AppText(
                                    "Method: unified combined-footing design — centroid-balanced soil pressure, " +
                                        "longitudinal and transverse bending, one-way and punching shear. " +
                                        "ECP 203 uses the dedicated combined-footing engine; " +
                                        "ACI 318 / SBC 304 route through the shared footing design interface.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (!res.isCombined && res.serviceLoadKn == null) {
                    item {
                        AppText(
                            "Service-load basis unknown: this result has no entered service load Ps (saved before that input existed). Bearing and plan size are not redesigned. Enter Ps and calculate again.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (res.calculationSteps.isNotEmpty()) {
                    item { CalculationStepsCard(res.calculationSteps, title = stringResource(R.string.footing_calc_steps)) }
                }

                // BBS/BOQ are heavy pure functions: remember them so recomposition
                // (typing, scrolling, snackbars) does not regenerate the tables.
                item {
                    // Bar bending schedule NOT ISSUED: bend-up/hook/anchorage/lap lengths have no verified code basis.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppText(
                            "Bar bending schedule: NOT ISSUED. Cutting lengths (bends, hooks, anchorage, laps) have no verified code basis.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.error
                        )
                        AppText(
                            BbsGenerator.footingNetLengthNote(res),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    val solvedIn = solvedInputs
                    val boqRows = remember(res, solvedIn) {
                        if (solvedIn == null) emptyList() else runCatching {
                            BoqEstimator.footingRows(
                                res, "F1",
                                solvedIn.excavationDepthM, solvedIn.workingSpaceM, solvedIn.blindingThicknessM
                            )
                        }.getOrDefault(emptyList())
                    }
                    if (boqRows.isNotEmpty()) {
                        BoqTableCard(boqRows, title = stringResource(R.string.footing_boq_title))
                    }
                }

                if (res.safetyChecks.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                AppText(
                                    stringResource(R.string.safety_checks),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                res.safetyChecks.forEach { check ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppText(
                                            if (check.isSafe) "✓" else "✗",
                                            color = if (check.isSafe) Color(0xFF2E7D32) else Color(0xFFC62828),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                            AppText(check.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            AppText(
                                                "${String.format("%.2f", check.value)} / ${String.format("%.2f", check.limit)} ${check.unit}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            label = stringResource(R.string.pdf_report),
                            onClick = {
                                scope.launch {
                                    val captureBitmap = try {
                                        pdfCaptureLayer.captureToAndroidBitmap()
                                    } catch (_: Exception) { null }
                                    viewModel.pendingDrawingBitmap = captureBitmap
                                    viewModel.exportToPdf(context) { file -> }
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
                    var selectedViewMode by remember { mutableStateOf(0) }
                    val configuration = LocalConfiguration.current
                    val screenW = configuration.screenWidthDp.dp
                    // Responsive height: scale proportionally to screen width
                    val wRatio = screenW.value / 360f  // baseline 360dp
                    val drawingHeight = when (selectedViewMode) {
                        1 -> (350 * wRatio).toInt().coerceIn(250, 450)
                        2 -> (320 * wRatio).toInt().coerceIn(220, 420)
                        3 -> (280 * wRatio).toInt().coerceIn(200, 380)
                        else -> (700 * wRatio).toInt().coerceIn(500, 900)
                    }
                    // Map footing type to English name expected by the drawing
                    val footingTypeEnglish = footingDrawingTypeName(res.type)
                    // For combined footings, compute column positions relative to footing left edge
                    val (col1XPos, col2XPos) = if (res.type == CalculatorEngine.FootingType.COMBINED) {
                        combinedColumnPositions(
                            columnWidthMm = res.column1Size.first,
                            distanceM = res.distanceBetweenColumns / 1000.0
                        )
                    } else {
                        Pair(0.0, 0.0)
                    }
                    InteractiveDrawingScreen(
                        onExportDxf = { viewModel.exportToDxf(context) },
                        title = stringResource(R.string.footing_drawing_title),
                        subtitle = stringResource(R.string.footing_reinforcement_detail),
                        drawingHeightDp = drawingHeight,
                        viewModes = listOf(stringResource(R.string.view_all), stringResource(R.string.slab_view_plan), stringResource(R.string.view_section), stringResource(R.string.view_reinforcement)),
                        selectedViewMode = selectedViewMode,
                        onViewModeChanged = { selectedViewMode = it },
                        drawingContent = {
                            if (res.type == CalculatorEngine.FootingType.COMBINED) {
                                ProfessionalCombinedFootingDrawing(
                                    footingLength = res.length.toDouble(),
                                    footingWidth = res.width.toDouble(),
                                    footingThickness = res.thickness.toDouble(),
                                    col1X = col1XPos,
                                    col1Width = res.column1Size.first,
                                    col1Depth = res.column1Size.second,
                                    col2X = col2XPos,
                                    col2Width = res.column1Size.first,
                                    col2Depth = res.column1Size.second,
                                    longBottomDia = res.barDiameter.toDouble(),
                                    longBottomCount = res.barsX,
                                    longTopDia = res.barDiameter.toDouble(),
                                    longTopCount = res.reinforcementTopX,
                                    transBottomDia = res.barDiameter.toDouble(),
                                    transBottomCount = res.barsY,
                                    cover = res.coverMm,
                                    soilPressureMax = res.soilPressure,
                                    soilPressureMin = res.soilPressure,
                                    viewMode = selectedViewMode,
                                    resultSummary = footingDrawingSummary(res),
                                    designCode = selectedCode,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                ProfessionalFootingDrawing(
                                    footingType = footingTypeEnglish,
                                    footingLengthX = res.length.toDouble(),
                                    footingLengthY = res.width.toDouble(),
                                    footingThickness = res.thickness.toDouble(),
                                    columnWidth = res.column1Size.first,
                                    columnDepth = res.column1Size.second,
                                    rebarXDia = res.barDiameter.toDouble(),
                                    rebarXCount = res.barsX,
                                    rebarYDia = res.barDiameter.toDouble(),
                                    rebarYCount = res.barsY,
                                    cover = res.coverMm,
                                    rebarXSpacingMm = res.reinforcementBottom.spacing.toDouble(),
                                    rebarYSpacingMm = res.reinforcementBottom.spacing.toDouble(),
                                    col1X = col1XPos,
                                    col2X = col2XPos,
                                    soilPressureMax = res.soilPressure,
                                    soilPressureMin = res.soilPressure,
                                    viewMode = selectedViewMode,
                                    resultSummary = footingDrawingSummary(res),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
        // PDF drawing capture area (invisible, renders at viewMode=0)
        result?.let { res ->
            val footingTypeEnglish = footingDrawingTypeName(res.type)
            val (col1XPos, col2XPos) = if (res.type == CalculatorEngine.FootingType.COMBINED) {
                combinedColumnPositions(
                    columnWidthMm = res.column1Size.first,
                    distanceM = res.distanceBetweenColumns / 1000.0
                )
            } else {
                Pair(0.0, 0.0)
            }
            ComposeDrawingCaptureUtil.DrawingCaptureArea(
                captureLayer = pdfCaptureLayer,
                widthPx = screenWidthPx,
                heightPx = screenHeightPx
            ) {
                Box(modifier = Modifier.background(Color(0xFF1A1A2E))) {
                    if (res.type == CalculatorEngine.FootingType.COMBINED) {
                        ProfessionalCombinedFootingDrawing(
                            footingLength = res.length.toDouble(),
                            footingWidth = res.width.toDouble(),
                            footingThickness = res.thickness.toDouble(),
                            col1X = col1XPos,
                            col1Width = res.column1Size.first,
                            col1Depth = res.column1Size.second,
                            col2X = col2XPos,
                            col2Width = res.column1Size.first,
                            col2Depth = res.column1Size.second,
                            longBottomDia = res.barDiameter.toDouble(),
                            longBottomCount = res.barsX,
                            longTopDia = res.barDiameter.toDouble(),
                            longTopCount = res.reinforcementTopX,
                            transBottomDia = res.barDiameter.toDouble(),
                            transBottomCount = res.barsY,
                            cover = res.coverMm,
                            soilPressureMax = res.soilPressure,
                            soilPressureMin = res.soilPressure,
                            viewMode = 0,
                            resultSummary = footingDrawingSummary(res),
                            designCode = selectedCode,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        ProfessionalFootingDrawing(
                            footingType = footingTypeEnglish,
                            footingLengthX = res.length.toDouble(),
                            footingLengthY = res.width.toDouble(),
                            footingThickness = res.thickness.toDouble(),
                            columnWidth = res.column1Size.first,
                            columnDepth = res.column1Size.second,
                            rebarXDia = res.barDiameter.toDouble(),
                            rebarXCount = res.barsX,
                            rebarYDia = res.barDiameter.toDouble(),
                            rebarYCount = res.barsY,
                            cover = res.coverMm,
                            rebarXSpacingMm = res.reinforcementBottom.spacing.toDouble(),
                            rebarYSpacingMm = res.reinforcementBottom.spacing.toDouble(),
                            col1X = col1XPos,
                            col2X = col2XPos,
                            soilPressureMax = res.soilPressure,
                            soilPressureMin = res.soilPressure,
                            viewMode = 0,
                            resultSummary = footingDrawingSummary(res),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        // Loading overlay (single instance — the duplicate below was removed)
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AppText(stringResource(R.string.loading), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        label = { AppText(stringResource(R.string.footing_name_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    AppText(stringResource(R.string.select_project), style = MaterialTheme.typography.labelMedium)
                    if (projects.isEmpty()) {
                        AppText(stringResource(R.string.no_projects_available), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                    enabled = selectedProjectId != -1L,
                    onClick = {
                        result?.let { viewModel.saveFooting(selectedProjectId, designName, it) }
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
private fun SectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label)
        AppText(value, fontWeight = FontWeight.Bold)
    }
}

/**
 * Footing type name understood by [ProfessionalFootingDrawing].
 *
 * The drawing now implements every footing type, so each [CalculatorEngine.FootingType]
 * maps to its own plan; this used to send STRAP/STRIP/PILE_CAP to the "Isolated"
 * drawing, which produced wrong plans and reinforcement labels on screen and in PDF.
 *
 * `internal` so the regression test can assert one-to-one mapping.
 */
internal fun footingDrawingTypeName(type: CalculatorEngine.FootingType): String = when (type) {
    CalculatorEngine.FootingType.ISOLATED -> "Isolated"
    CalculatorEngine.FootingType.COMBINED -> "Combined"
    CalculatorEngine.FootingType.STRAP -> "Strap"
    CalculatorEngine.FootingType.RAFT -> "Raft"
    CalculatorEngine.FootingType.STRIP -> "Strip"
    CalculatorEngine.FootingType.PILE_CAP -> "Pile Cap"
}

/**
 * Column centres (mm, measured from the footing left edge) used by the combined
 * footing drawing: boundary column at the edge, second column one [distanceM] away.
 */
internal fun combinedColumnPositions(
    columnWidthMm: Double,
    distanceM: Double
): Pair<Double, Double> {
    val firstColCenter = columnWidthMm / 2.0 + 50.0
    return Pair(firstColCenter, firstColCenter + distanceM * 1000.0)
}


