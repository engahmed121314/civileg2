package com.civileg.app.ui.compose.screens

import com.civileg.app.ui.compose.components.localizedCheckName
import com.civileg.app.ui.responsive.dimens

import com.civileg.app.domain.entities.DesignCode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.civileg.app.domain.validation.FieldKind
import com.civileg.app.domain.validation.FieldSpec
import com.civileg.app.domain.validation.FormValidator
import com.civileg.app.ui.compose.validation.FormErrorSummary
import com.civileg.app.ui.compose.validation.ValidatedNumberField
import com.civileg.app.ui.compose.validation.rememberFormValidationState
import com.civileg.app.ui.compose.validation.scrollItemToCenter
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
import com.civileg.app.domain.calculations.utils.BbsGenerator
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.ui.compose.components.common.BbsTableCard
import com.civileg.app.ui.compose.components.common.BoqTableCard
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.ui.compose.components.common.CalculationStepsCard
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalSlabDrawing
import com.civileg.app.ui.compose.components.drawings.slabDrawingSummary
import com.civileg.app.ui.compose.components.drawings.ProfessionalWaffleSlabDrawing
import com.civileg.app.domain.calculations.base.HordiSlabDesign
import com.civileg.app.ui.compose.components.drawings.ProfessionalHordiSlabDrawing
import com.civileg.app.viewmodel.SlabViewModel
import com.civileg.app.viewmodel.WaffleSlabViewModel
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.captureToAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import com.civileg.app.ui.defaultDesignCode
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor

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
    
    var openingWidth by remember { mutableStateOf("0.0") }
    var openingLength by remember { mutableStateOf("0.0") }

    var ribWidth by remember { mutableStateOf("100") }
    var ribSpacing by remember { mutableStateOf("500") }
    var ribSpacingY by remember { mutableStateOf("500") }

    val form = rememberFormValidationState()
    val listState = rememberLazyListState()
    // Item index of each field row, recorded by the list builder below (rows shift with the slab type).
    val fieldItemIndex = remember { mutableMapOf<String, Int>() }
    var selectedType by remember { mutableStateOf(CalculatorEngine.SlabType.SOLID) }
    var selectedCode by remember { mutableStateOf(defaultDesignCode()) }
    var expandedType by remember { mutableStateOf(false) }
    var expandedCode by remember { mutableStateOf(false) }
    
    val result by viewModel.result.observeAsState()
    val solved by viewModel.solvedInputs.observeAsState()
    // Any edit makes the shown result stale: drop it and that field's old error.
    fun touch(id: String) { form.clear(id); viewModel.clearResult() }
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
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * density.density).toInt()
    val screenHeightPx = (config.screenHeightDp * density.density).toInt()

    var showSaveDialog by remember { mutableStateOf(false) }
    var pdfError by remember { mutableStateOf<String?>(null) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    val defaultSlabName = stringResource(R.string.slab_default_name)
    var designName by remember { mutableStateOf(defaultSlabName) }
    val configuration = LocalConfiguration.current
    val screenW = configuration.screenWidthDp.dp


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.screen_slab_title), fontWeight = FontWeight.Bold) },
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
        Box(modifier = Modifier.padding(padding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            var itemCounter = 0
            fun reg(vararg ids: String) { ids.forEach { fieldItemIndex[it] = itemCounter }; itemCounter++ }
            fieldItemIndex.clear()
            reg()
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
                            label = { AppText(stringResource(R.string.slab_type_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                            CalculatorEngine.SlabType.values().forEach { type ->
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

                    ExposedDropdownMenuBox(
                        expanded = expandedCode,
                        onExpandedChange = { expandedCode = !expandedCode },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedCode.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { AppText(stringResource(R.string.slab_code_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCode) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(expanded = expandedCode, onDismissRequest = { expandedCode = false }) {
                            DesignCode.values().forEach { code ->
                                DropdownMenuItem(
                                    text = { AppText(code.displayName) },
                                    onClick = {
                                        if (selectedCode != code) viewModel.clearResult()
                                        selectedCode = code
                                        expandedCode = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // --- Advanced Inputs Section ---
            if (selectedType == CalculatorEngine.SlabType.FLAT || selectedType == CalculatorEngine.SlabType.POST_TENSION) {
                reg("openingWidth", "openingLength")
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppText(stringResource(R.string.slab_advanced_detailing_pro), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            // Drop panel, column size and prestress are entered once, in the main inputs below.

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            AppText(stringResource(R.string.slab_openings_shafts), style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ValidatedNumberField(form, "openingWidth", openingWidth, stringResource(R.string.slab_open_width_mm), { openingWidth = it; touch("openingWidth") }, Modifier.weight(1f))
                                ValidatedNumberField(form, "openingLength", openingLength, stringResource(R.string.slab_open_length_mm), { openingLength = it; touch("openingLength") }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            reg()
            item { SectionHeader(stringResource(R.string.slab_dimensions_loading), R.drawable.ic_slab) }

            reg("shortSpan", "longSpan")
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValidatedNumberField(form, "shortSpan", shortSpan, stringResource(R.string.span_x_label), { shortSpan = it; touch("shortSpan") }, Modifier.weight(1f))
                    ValidatedNumberField(form, "longSpan", longSpan, stringResource(R.string.span_y_label), { longSpan = it; touch("longSpan") }, Modifier.weight(1f))
                }
            }

            reg("deadLoad", "liveLoad")
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    ValidatedNumberField(form, "deadLoad", deadLoad, stringResource(R.string.slab_dl_label), { deadLoad = it; touch("deadLoad") }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    ValidatedNumberField(form, "liveLoad", liveLoad, stringResource(R.string.slab_ll_label), { liveLoad = it; touch("liveLoad") }, Modifier.weight(1f))
                }
            }

            reg()
            item { SectionHeader(stringResource(R.string.beam_material_props), R.drawable.ic_calculator) }

            reg("fcu", "fy")
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    ValidatedNumberField(form, "fcu", fcu, stringResource(R.string.fcu_label), { fcu = it; touch("fcu") }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    ValidatedNumberField(form, "fy", fy, stringResource(R.string.fy_label), { fy = it; touch("fy") }, Modifier.weight(1f))
                }
            }
            
            reg("thickness", "preferredDiameter")
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    ValidatedNumberField(form, "thickness", thickness, stringResource(R.string.slab_thickness_mm), { thickness = it; touch("thickness") }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    ValidatedNumberField(form, "preferredDiameter", preferredDiameter, stringResource(R.string.slab_bar_diameter_mm), { preferredDiameter = it; touch("preferredDiameter") }, Modifier.weight(1f))
                }
            }

            if (selectedType == CalculatorEngine.SlabType.FLAT) {
                reg("columnSize", "dropPanelThickness")
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValidatedNumberField(form, "columnSize", columnSize, stringResource(R.string.slab_column_width_mm), { columnSize = it; touch("columnSize") }, Modifier.weight(1f))
                        ValidatedNumberField(form, "dropPanelThickness", dropPanelThickness, stringResource(R.string.slab_drop_thickness_mm), { dropPanelThickness = it; touch("dropPanelThickness") }, Modifier.weight(1f))
                    }
                }
            }

            if (selectedType == CalculatorEngine.SlabType.POST_TENSION) {
                reg("prestressForce")
                item {
                    ValidatedNumberField(form, "prestressForce", prestressForce, stringResource(R.string.slab_compressive_force), { prestressForce = it; touch("prestressForce") }, Modifier.fillMaxWidth())
                }
            }

            if (selectedType == CalculatorEngine.SlabType.HOLLOW_BLOCK) {
                reg()
                item {
                    SectionHeader(stringResource(R.string.slab_ribbed_dims), R.drawable.ic_slab)
                }
                reg("ribWidth", "ribSpacing")
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ValidatedNumberField(form, "ribWidth", ribWidth, stringResource(R.string.slab_rib_width_mm), { ribWidth = it; touch("ribWidth") }, Modifier.weight(1f))
                        ValidatedNumberField(form, "ribSpacing", ribSpacing, stringResource(R.string.slab_rib_spacing_mm), { ribSpacing = it; touch("ribSpacing") }, Modifier.weight(1f))
                    }
                }
            }

            if (selectedType == CalculatorEngine.SlabType.WAFFLE) {
                reg()
                item {
                    SectionHeader(stringResource(R.string.slab_waffle_dims), R.drawable.ic_slab)
                }
                reg("ribWidth", "ribSpacing")
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ValidatedNumberField(form, "ribWidth", ribWidth, stringResource(R.string.slab_rib_width_mm), { ribWidth = it; touch("ribWidth") }, Modifier.weight(1f))
                        ValidatedNumberField(form, "ribSpacing", ribSpacing, stringResource(R.string.slab_waffle_spacing_x_mm), { ribSpacing = it; touch("ribSpacing") }, Modifier.weight(1f))
                    }
                }
                reg("ribSpacingY")
                item {
                    ValidatedNumberField(form, "ribSpacingY", ribSpacingY, stringResource(R.string.slab_waffle_spacing_y_mm), { ribSpacingY = it; touch("ribSpacingY") }, Modifier.fillMaxWidth())
                }
                reg("columnSize")
                item {
                    // WAFFLE solid head uses the column size; it used to be an unseen 400 mm default.
                    ValidatedNumberField(form, "columnSize", columnSize, stringResource(R.string.slab_column_width_mm), { columnSize = it; touch("columnSize") }, Modifier.fillMaxWidth())
                }
            }

            reg()
            item {
                FormErrorSummary(form)
                AppButton(
                    label = stringResource(R.string.slab_design_now),
                    onClick = {
                        viewModel.clearResult() // never leave a stale result on screen
                        val groups = buildSet {
                            when (selectedType) {
                                CalculatorEngine.SlabType.FLAT -> { add("flat"); add("openings") }
                                CalculatorEngine.SlabType.POST_TENSION -> { add("pt"); add("openings") }
                                CalculatorEngine.SlabType.HOLLOW_BLOCK -> add("rib")
                                CalculatorEngine.SlabType.WAFFLE -> { add("rib"); add("waffle") }
                                else -> {}
                            }
                        }
                        // Physical rules only (positive / non-negative / whole). The old fcu 15-60, fy 200-700
                        // and thickness 50-500 cut-offs had no recorded code source and are not enforced.
                        val checked = FormValidator.validate(
                            listOf(
                                FieldSpec("openingWidth", openingWidth, FieldKind.NON_NEGATIVE, group = "openings"),
                                FieldSpec("openingLength", openingLength, FieldKind.NON_NEGATIVE, group = "openings"),
                                FieldSpec("shortSpan", shortSpan, FieldKind.POSITIVE),
                                FieldSpec("longSpan", longSpan, FieldKind.POSITIVE),
                                FieldSpec("deadLoad", deadLoad, FieldKind.NON_NEGATIVE),
                                FieldSpec("liveLoad", liveLoad, FieldKind.NON_NEGATIVE),
                                FieldSpec("fcu", fcu, FieldKind.POSITIVE),
                                FieldSpec("fy", fy, FieldKind.POSITIVE),
                                FieldSpec("thickness", thickness, FieldKind.POSITIVE),
                                FieldSpec("preferredDiameter", preferredDiameter, FieldKind.POSITIVE_INT),
                                FieldSpec("columnSize", columnSize, FieldKind.POSITIVE, group = "flat"),
                                FieldSpec("dropPanelThickness", dropPanelThickness, FieldKind.NON_NEGATIVE, group = "flat"),
                                FieldSpec("prestressForce", prestressForce, FieldKind.NON_NEGATIVE, group = "pt"),
                                FieldSpec("ribWidth", ribWidth, FieldKind.POSITIVE, group = "rib"),
                                FieldSpec("ribSpacing", ribSpacing, FieldKind.POSITIVE, group = "rib"),
                                FieldSpec("ribSpacingY", ribSpacingY, FieldKind.POSITIVE, group = "waffle"),
                                FieldSpec("columnSize", columnSize, FieldKind.POSITIVE, group = "waffle")
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
                        viewModel.calculateSlabPro(
                            lx = checked.value("shortSpan"),
                            ly = checked.value("longSpan"),
                            deadLoad = checked.value("deadLoad"),
                            liveLoad = checked.value("liveLoad"),
                            fcu = checked.value("fcu"),
                            fy = checked.value("fy"),
                            ts = checked.value("thickness"),
                            preferredDiameter = checked.value("preferredDiameter").toInt(),
                            type = selectedType,
                            code = selectedCode,
                            // Not applicable for this type: the engine ignores them; 0.0 means "not used".
                            prestressForce = checked.valueOrNull("prestressForce") ?: 0.0,
                            dropPanelThickness = checked.valueOrNull("dropPanelThickness") ?: 0.0,
                            columnSize = checked.valueOrNull("columnSize") ?: 0.0,
                            openingWidth = checked.valueOrNull("openingWidth") ?: 0.0,
                            openingLength = checked.valueOrNull("openingLength") ?: 0.0,
                            ribWidth = checked.valueOrNull("ribWidth") ?: 0.0,
                            ribSpacing = checked.valueOrNull("ribSpacing") ?: 0.0
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    loading = isLoading,
                    icon = Icons.Default.Calculate
                )
            }

            result?.let { res ->
                val sol = solved ?: return@let
                item { SectionHeader(stringResource(R.string.slab_results), R.drawable.ic_calculator) }
                
                item { SlabResultCard(res) }

                if (res.calculationSteps.isNotEmpty()) {
                    item { CalculationStepsCard(res.calculationSteps, title = "Slab Calculation Steps") }

                    val bbsEntries = runCatching {
                        BbsGenerator.generateSlabBbs(
                            mark = "S1",
                            result = res,
                            lxMm = (sol.lx) * 1000.0,
                            lyMm = (sol.ly) * 1000.0
                        )
                    }.getOrDefault(emptyList())
                    if (bbsEntries.isNotEmpty()) {
                        item { BbsTableCard(bbsEntries, title = stringResource(R.string.slab_bbs_title)) }
                    }

                    val boqRows = runCatching {
                        BoqEstimator.slabRows(
                            res,
                            sol.lx,
                            sol.ly,
                            "S1"
                        )
                    }.getOrDefault(emptyList())
                    if (boqRows.isNotEmpty()) {
                        item { BoqTableCard(boqRows, title = stringResource(R.string.slab_boq_title)) }
                    }
                }

                if (res.safetyChecks.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                AppText(
                                    stringResource(R.string.safety_checks),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (res.type == CalculatorEngine.SlabType.HOLLOW_BLOCK) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (res.bondClassAssessed) Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (res.bondClassAssessed) Color(0xFF2E7D32) else Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        AppText("Material assessment (bar surface/bond)", modifier = Modifier.weight(1f), fontSize = 13.sp)
                                        AppText(
                                            if (res.bondClassAssessed) "declared" else "UNASSESSED - estimate only",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (res.bondClassAssessed) Color(0xFF2E7D32) else Color.Red
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (res.serviceabilityAssessed) Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (res.serviceabilityAssessed) Color(0xFF2E7D32) else Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        AppText("Serviceability (deflection)", modifier = Modifier.weight(1f), fontSize = 13.sp)
                                        AppText(
                                            if (res.serviceabilityAssessed) "assessed" else "NOT ASSESSED - no deflection check",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (res.serviceabilityAssessed) Color(0xFF2E7D32) else Color.Red
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (res.engineeringAcceptance) Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (res.engineeringAcceptance) Color(0xFF2E7D32) else Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        AppText("Engineering acceptance", modifier = Modifier.weight(1f), fontSize = 13.sp)
                                        AppText(
                                            if (res.engineeringAcceptance) "ACCEPTED" else "NO - qualification incomplete",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (res.engineeringAcceptance) Color(0xFF2E7D32) else Color.Red
                                        )
                                    }
                                }
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
                                        AppText(localizedCheckName(check.name), modifier = Modifier.weight(1f).padding(end = 12.dp), fontSize = 13.sp)
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

                item {
                    AppText(stringResource(R.string.slab_equations_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    SlabFormulasCard()
                }

                item {
                    val pdfErrorMsg = stringResource(R.string.beam_pdf_error)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                            pdfError = pdfErrorMsg
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
                    var selectedViewMode by remember { mutableStateOf(0) }
                    val drawingHeight = if (configuration.smallestScreenWidthDp >= 600) 580 else 480
                    InteractiveDrawingScreen(
                        onExportDxf = { viewModel.exportToDxf(context) },
                        title = stringResource(R.string.slab_drawing_title),
                        subtitle = stringResource(R.string.slab_reinforcement_subtitle),
                        viewModes = when (sol.type) {
                            CalculatorEngine.SlabType.WAFFLE, CalculatorEngine.SlabType.HOLLOW_BLOCK -> listOf("All", "Plan", "Section", "Detail")
                            else -> listOf(stringResource(R.string.view_all), stringResource(R.string.slab_view_plan), stringResource(R.string.view_section), stringResource(R.string.view_reinforcement))
                        },
                        selectedViewMode = selectedViewMode,
                        onViewModeChanged = { selectedViewMode = it },
                        drawingHeightDp = drawingHeight,
                        drawingContent = {
                            when (sol.type) {
                                CalculatorEngine.SlabType.WAFFLE -> {
                                    ProfessionalWaffleSlabDrawing(
                                        lx = (sol.lx) * 1000.0,
                                        ly = (sol.ly) * 1000.0,
                                        ribSpacing = sol.ribSpacing,
                                        ribWidth = sol.ribWidth,
                                        ribHeight = res.thickness.toDouble() - 50.0,
                                        toppingThickness = 50.0,
                                        solidHeadSize = 800.0,
                                        columnWidth = 400.0,
                                        ribBottomDia = res.reinforcementMain.diameter.toDouble(),
                                        ribBottomCount = res.reinforcementMain.numBars.coerceAtLeast(2),
                                        ribTopDia = res.reinforcementSecondary.diameter.toDouble(),
                                        ribTopCount = res.reinforcementSecondary.numBars.coerceAtLeast(2),
                                        headBottomDia = res.reinforcementMain.diameter.toDouble(),
                                        headBottomCount = 6,
                                        cover = 25.0,
                                        viewMode = selectedViewMode,
                                        designCode = sol.code,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                CalculatorEngine.SlabType.HOLLOW_BLOCK -> {
                                    ProfessionalHordiSlabDrawing(
                                        span = (sol.lx) * 1000.0,
                                        ribWidth = sol.ribWidth,
                                        ribSpacing = sol.ribSpacing,
                                        totalThickness = res.thickness.toDouble(),
                                        toppingThickness = 50.0,
                                        ribBottomDia = res.reinforcementMain.diameter.toDouble(),
                                        ribBottomCount = res.reinforcementMain.numBars.coerceAtLeast(2),
                                        stirrupDia = HordiSlabDesign.DEFAULT_LINK_DIA_MM,
                                        stirrupSpacing = 200.0,
                                        cover = HordiSlabDesign.DEFAULT_COVER_MM,
                                        viewMode = selectedViewMode,
                                        designCode = sol.code,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                else -> {
                                    ProfessionalSlabDrawing(
                                        slabType = sol.type.displayName,
                                        slabThickness = res.thickness.toDouble(),
                                        spanX = sol.lx,
                                        spanY = sol.ly,
                                        mainRebarDia = res.reinforcementMain.diameter.toDouble(),
                                        mainRebarSpacing = res.reinforcementMain.spacing.toDouble(),
                                        distRebarDia = res.reinforcementSecondary.diameter.toDouble(),
                                        distRebarSpacing = res.reinforcementSecondary.spacing.toDouble(),
                                        cover = 25.0,
                                        dropPanelSize = 0.0, // thickness is not a plan size; the slab screen has no plan-size input
                                        ribWidth = if (sol.type == CalculatorEngine.SlabType.HOLLOW_BLOCK || sol.type == CalculatorEngine.SlabType.WAFFLE) sol.ribWidth else 0.0,
                                        ribSpacing = if (sol.type == CalculatorEngine.SlabType.HOLLOW_BLOCK || sol.type == CalculatorEngine.SlabType.WAFFLE) sol.ribSpacing else 0.0,
                                        viewMode = selectedViewMode,
                                        resultSummary = slabDrawingSummary(res),
                                        modifier = Modifier.fillMaxWidth(),
                                        momentX = res.momentX,
                                        momentY = res.momentY,
                                        factoredLoad = res.totalLoad,
                                        fcu = sol.fcu,
                                        fy = sol.fy,
                                        isSafe = res.isSafe,
                                        utilizationRatio = res.utilizationRatio
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
        // PDF drawing capture area (invisible, renders at viewMode=0)
        result?.let { res ->
            val sol = solved ?: return@let
            ComposeDrawingCaptureUtil.DrawingCaptureArea(
                captureLayer = pdfCaptureLayer,
                widthPx = screenWidthPx,
                heightPx = screenHeightPx
            ) {
                Box(modifier = Modifier.background(Color(0xFF1A1A2E))) {
                    when (sol.type) {
                        CalculatorEngine.SlabType.WAFFLE -> {
                            ProfessionalWaffleSlabDrawing(
                                lx = (sol.lx) * 1000.0,
                                ly = (sol.ly) * 1000.0,
                                ribSpacing = sol.ribSpacing,
                                ribWidth = sol.ribWidth,
                                ribHeight = res.thickness.toDouble() - 50.0,
                                toppingThickness = 50.0,
                                solidHeadSize = 800.0,
                                columnWidth = 400.0,
                                ribBottomDia = res.reinforcementMain.diameter.toDouble(),
                                ribBottomCount = res.reinforcementMain.numBars.coerceAtLeast(2),
                                ribTopDia = res.reinforcementSecondary.diameter.toDouble(),
                                ribTopCount = res.reinforcementSecondary.numBars.coerceAtLeast(2),
                                headBottomDia = res.reinforcementMain.diameter.toDouble(),
                                headBottomCount = 6,
                                cover = 25.0,
                                viewMode = 0,
                                designCode = sol.code,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        CalculatorEngine.SlabType.HOLLOW_BLOCK -> {
                            ProfessionalHordiSlabDrawing(
                                span = (sol.lx) * 1000.0,
                                ribWidth = sol.ribWidth,
                                ribSpacing = sol.ribSpacing,
                                totalThickness = res.thickness.toDouble(),
                                toppingThickness = 50.0,
                                ribBottomDia = res.reinforcementMain.diameter.toDouble(),
                                ribBottomCount = res.reinforcementMain.numBars.coerceAtLeast(2),
                                stirrupDia = HordiSlabDesign.DEFAULT_LINK_DIA_MM,
                                stirrupSpacing = 200.0,
                                cover = HordiSlabDesign.DEFAULT_COVER_MM,
                                viewMode = 0,
                                designCode = sol.code,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> {
                            ProfessionalSlabDrawing(
                                slabType = sol.type.displayName,
                                slabThickness = res.thickness.toDouble(),
                                spanX = sol.lx,
                                spanY = sol.ly,
                                mainRebarDia = res.reinforcementMain.diameter.toDouble(),
                                mainRebarSpacing = res.reinforcementMain.spacing.toDouble(),
                                distRebarDia = res.reinforcementSecondary.diameter.toDouble(),
                                distRebarSpacing = res.reinforcementSecondary.spacing.toDouble(),
                                cover = 25.0,
                                dropPanelSize = 0.0, // thickness is not a plan size; the slab screen has no plan-size input
                                ribWidth = if (sol.type == CalculatorEngine.SlabType.HOLLOW_BLOCK || sol.type == CalculatorEngine.SlabType.WAFFLE) sol.ribWidth else 0.0,
                                ribSpacing = if (sol.type == CalculatorEngine.SlabType.HOLLOW_BLOCK || sol.type == CalculatorEngine.SlabType.WAFFLE) sol.ribSpacing else 0.0,
                                viewMode = 0,
                                resultSummary = slabDrawingSummary(res),
                                modifier = Modifier.fillMaxWidth(),
                                momentX = res.momentX,
                                momentY = res.momentY,
                                factoredLoad = res.totalLoad,
                                fcu = sol.fcu,
                                fy = sol.fy,
                                isSafe = res.isSafe,
                                utilizationRatio = res.utilizationRatio
                            )
                        }
                    }
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
                        label = { AppText(stringResource(R.string.slab_name_hint)) },
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
                    onClick = {
                        val pId = if (selectedProjectId == -1L) 1L else selectedProjectId
                        result?.let { viewModel.saveSlab(pId, designName, it) }
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
                    AppText(
                        stringResource(R.string.consultant_ratio),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (res.type != CalculatorEngine.SlabType.HOLLOW_BLOCK && res.isSafe || res.type == CalculatorEngine.SlabType.HOLLOW_BLOCK && res.engineeringAcceptance) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (if(res.type == CalculatorEngine.SlabType.HOLLOW_BLOCK) res.engineeringAcceptance else res.isSafe) Color(0xFF2E7D32) else Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        AppText(
                            if(res.type == CalculatorEngine.SlabType.HOLLOW_BLOCK && !res.engineeringAcceptance) "INCOMPLETE - not accepted" else if (res.isSafe) stringResource(R.string.slab_safe) else stringResource(R.string.slab_unsafe),
                            fontWeight = FontWeight.Bold,
                            color = if (if(res.type == CalculatorEngine.SlabType.HOLLOW_BLOCK) res.engineeringAcceptance else res.isSafe) Color(0xFF2E7D32) else Color.Red
                        )
                    }
                }

                Box(contentAlignment = Alignment.Center) {
                    val animatedRatio by animateFloatAsState(
                        targetValue = com.civileg.app.utils.DesignNumberFormat.progress(res.utilizationRatio),
                        animationSpec = tween(1000), label = ""
                    )
                    CircularProgressIndicator(
                        progress = { animatedRatio },
                        modifier = Modifier.size(54.dp),
                        strokeWidth = 5.dp,
                        color = utilizationColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    AppText(
                        com.civileg.app.utils.DesignNumberFormat.percent(res.utilizationRatio),
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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ResultItem("Moment My", "${"%.1f".format(res.momentY)} kN.m")
                ResultItem("Punching", if (res.punchingSafe) "SAFE ✓" else "UNSAFE ✗")
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ResultItem("Concrete Volume", "${"%.3f".format(res.concreteVolume)} m³")
                ResultItem("Steel Weight", "${"%.1f".format(res.steelWeight)} kg")
            }

            ResultItem("Cost", "${"%.2f".format(res.cost)}")

            if (res.trimmerReinforcement.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AppText("Slab Opening Support", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                AppText(res.trimmerReinforcement, fontWeight = FontWeight.Bold)
            }
            
            if (res.type == CalculatorEngine.SlabType.HOLLOW_BLOCK) {
                Spacer(Modifier.height(8.dp))
                AppText("Rib Reinforcement", style = MaterialTheme.typography.labelMedium)
                AppText("${res.reinforcementMain.numBars} Ø ${res.reinforcementMain.diameter} per rib", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            } else {
                Spacer(Modifier.height(8.dp))
                AppText(stringResource(R.string.slab_main_steel_lx), style = MaterialTheme.typography.labelMedium)
                AppText(res.reinforcementMain.barString, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                
                AppText(stringResource(R.string.slab_secondary_steel_ly), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                AppText(res.reinforcementSecondary.barString, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ResultItem(label: String, value: String) {
    com.civileg.app.ui.components.ContentDirection(label) {
    Column {
        AppText(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppText(value, fontWeight = FontWeight.Bold)
    }
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
        AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
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
