package com.civileg.app.ui.compose.screens

import com.civileg.app.domain.entities.DesignCode

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.ui.responsive.dimens
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.SlabDesignResult
import com.civileg.app.ui.compose.components.*
import com.civileg.app.ui.compose.components.common.BbsTableCard
import com.civileg.app.ui.compose.components.common.BoqTableCard
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.domain.calculations.utils.BbsGenerator
import com.civileg.app.viewmodel.HordiSlabViewModel
import com.civileg.app.ui.defaultDesignCode
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor

/**
 * شاشة تصميم بلاطة الهوردي — Hordi (Ribbed/Hollow-Block) Slab Design Screen
 * One-way ribbed slab with hollow blocks between ribs.
 * Supports ECP 203 / ACI 318 / SBC 304 design codes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HordiSlabScreen(
    viewModel: HordiSlabViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    // ── Input state ────────────────────────────────────────────────
    var designCode by remember { mutableStateOf(defaultDesignCode().name) }
    var fcu by remember { mutableStateOf("30") }
    var fy by remember { mutableStateOf("400") }
    var ribWidth by remember { mutableStateOf("120") }
    var ribSpacing by remember { mutableStateOf("500") }
    var totalThickness by remember { mutableStateOf("350") }
    var toppingThickness by remember { mutableStateOf("50") }
    var span by remember { mutableStateOf("6000") }
    var designMoment by remember { mutableStateOf("80") }
    var designShear by remember { mutableStateOf("30") }
    var loadCombination by remember { mutableStateOf(LoadCombination.DEAD_LIVE) }
    var loadCombinationExpanded by remember { mutableStateOf(false) }
    var cover by remember { mutableStateOf("") }
    var barBondClass by remember { mutableStateOf(com.civileg.app.domain.entities.BarBondClass.UNKNOWN) }
    var bondClassExpanded by remember { mutableStateOf(false) }
    // UNSPECIFIED: no support system presumed (simply-supported would be
    // UNconservative against a cantilever); the deemed deflection check does
    // not run until the user selects the actual condition.
    var supportCondition by rememberSaveable { mutableStateOf(com.civileg.app.domain.calculations.base.SlabSupportCondition.UNSPECIFIED) }
    var supportExpanded by remember { mutableStateOf(false) }
    // UNDECLARED: the deemed check also requires the user's declaration that
    // loading is ordinary and no deflection-sensitive elements are supported.
    var loadingApplicability by rememberSaveable { mutableStateOf(com.civileg.app.domain.calculations.base.DeflectionLoadingApplicability.UNDECLARED) }
    var loadingExpanded by remember { mutableStateOf(false) }
    var detailDraft by remember { mutableStateOf(com.civileg.app.domain.calculations.utils.JoistDetailDraft()) }

    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)
    val errorMsg by viewModel.error.observeAsState()

    // Show errors via Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.hordi_slab_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.dimens.screenHorizontalPadding,
                    vertical = MaterialTheme.dimens.screenVerticalPadding
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.cardSpacing)
            ) {
                // ── Configuration Section ─────────────────────────────
                item {
                    PremiumSectionHeader(
                        title = stringResource(R.string.hordi_slab_config),
                        icon = Icons.Default.ViewModule
                    )
                }

                // Design Code Selector
                item {
                    CodeSelectorChips(
                        selectedCode = designCode,
                        codes = com.civileg.app.domain.calculations.utils.RcReferenceBasis.entries.map { it.code to it.edition },
                        onCodeSelected = { newCode ->
                            viewModel.clearResult()
                            designCode = newCode
                            val combos = LoadCombination.getByCode(
                                runCatching { DesignCode.valueOf(newCode) }.getOrDefault(DesignCode.ECP)
                            )
                            if (loadCombination !in combos) loadCombination = combos.first()
                        }
                    )
                }

                // ── Material Properties ───────────────────────────────
                item { PremiumSectionHeader(stringResource(R.string.hordi_slab_material_properties), icon = Icons.Default.Science) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(fcu, "fcu (MPa)", { viewModel.clearResult(); fcu = it }, modifier = Modifier.weight(1f))
                        PremiumInputField(fy, "fy (MPa)", { viewModel.clearResult(); fy = it }, modifier = Modifier.weight(1f))
                    }
                }

                // ── Geometry ──────────────────────────────────────────
                item { PremiumSectionHeader(stringResource(R.string.hordi_slab_geometry), icon = Icons.Default.SquareFoot) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(
                            ribWidth, "Rib Width (mm)", { viewModel.clearResult(); ribWidth = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            ribSpacing, "Rib Spacing (mm)", { viewModel.clearResult(); ribSpacing = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(
                            totalThickness, "Total Thickness (mm)", { viewModel.clearResult(); totalThickness = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            toppingThickness, "Topping Thk (mm)", { viewModel.clearResult(); toppingThickness = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    PremiumInputField(
                        span, "البحر الصافي mm / Clear span between support faces", { viewModel.clearResult(); span = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Design Forces ─────────────────────────────────────
                item { PremiumSectionHeader(stringResource(R.string.hordi_slab_design_forces), icon = Icons.Default.MoneyOff) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumInputField(
                            designMoment, "Moment (kN.m/rib)", { viewModel.clearResult(); designMoment = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            designShear, "Shear (kN/rib)", { viewModel.clearResult(); designShear = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    PremiumInputField(
                        cover, "Actual cover to outermost reinforcement (mm)", { viewModel.clearResult(); cover = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Bar surface/bond classification. UNKNOWN keeps the result a
                // conservative estimate (isSafe=false); a declared surface is
                // required for case-qualified acceptance.
                item {
                    ExposedDropdownMenuBox(
                        expanded = bondClassExpanded,
                        onExpandedChange = { bondClassExpanded = !bondClassExpanded }
                    ) {
                        OutlinedTextField(
                            value = when (barBondClass) {
                                com.civileg.app.domain.entities.BarBondClass.DEFORMED -> "Deformed (ribbed) bars"
                                com.civileg.app.domain.entities.BarBondClass.PLAIN -> "Plain (smooth) bars"
                                com.civileg.app.domain.entities.BarBondClass.UNKNOWN -> "Unknown — estimate only"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { AppText("Bar Surface / Bond Class", fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bondClassExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = PremiumDesignSystem.InputShape,
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = bondClassExpanded,
                            onDismissRequest = { bondClassExpanded = false }
                        ) {
                            com.civileg.app.domain.entities.BarBondClass.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        AppText(when (option) {
                                            com.civileg.app.domain.entities.BarBondClass.DEFORMED -> "Deformed (ribbed) bars"
                                            com.civileg.app.domain.entities.BarBondClass.PLAIN -> "Plain (smooth) bars"
                                            com.civileg.app.domain.entities.BarBondClass.UNKNOWN -> "Unknown — estimate only"
                                        }, fontSize = 13.sp)
                                    },
                                    onClick = {
                                        viewModel.clearResult()
                                        barBondClass = option
                                        bondClassExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Support/continuity condition — drives the deemed-to-satisfy
                // deflection check (ECP Table 4-10 / SBC Table 9.3.1.1).
                item {
                    ExposedDropdownMenuBox(
                        expanded = supportExpanded,
                        onExpandedChange = { supportExpanded = !supportExpanded }
                    ) {
                        OutlinedTextField(
                            value = when (supportCondition) {
                                com.civileg.app.domain.calculations.base.SlabSupportCondition.UNSPECIFIED -> "Not specified - deflection NOT assessed"
                                com.civileg.app.domain.calculations.base.SlabSupportCondition.SIMPLY_SUPPORTED -> "Simply supported"
                                com.civileg.app.domain.calculations.base.SlabSupportCondition.ONE_END_CONTINUOUS -> "One end continuous"
                                com.civileg.app.domain.calculations.base.SlabSupportCondition.BOTH_ENDS_CONTINUOUS -> "Both ends continuous"
                                com.civileg.app.domain.calculations.base.SlabSupportCondition.CANTILEVER -> "Cantilever"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { AppText("Support Condition (deflection check)", fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supportExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = PremiumDesignSystem.InputShape,
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = supportExpanded,
                            onDismissRequest = { supportExpanded = false }
                        ) {
                            com.civileg.app.domain.calculations.base.SlabSupportCondition.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        AppText(when (option) {
                                            com.civileg.app.domain.calculations.base.SlabSupportCondition.UNSPECIFIED -> "Not specified - deflection NOT assessed"
                                            com.civileg.app.domain.calculations.base.SlabSupportCondition.SIMPLY_SUPPORTED -> "Simply supported"
                                            com.civileg.app.domain.calculations.base.SlabSupportCondition.ONE_END_CONTINUOUS -> "One end continuous"
                                            com.civileg.app.domain.calculations.base.SlabSupportCondition.BOTH_ENDS_CONTINUOUS -> "Both ends continuous"
                                            com.civileg.app.domain.calculations.base.SlabSupportCondition.CANTILEVER -> "Cantilever"
                                        }, fontSize = 13.sp)
                                    },
                                    onClick = {
                                        viewModel.clearResult()
                                        supportCondition = option
                                        supportExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Loading/deflection-sensitivity declaration — Table 4-10 notes
                // (ج/د) / SBC 9.3.1.1 scope gate the deemed deflection check.
                item {
                    ExposedDropdownMenuBox(
                        expanded = loadingExpanded,
                        onExpandedChange = { loadingExpanded = !loadingExpanded }
                    ) {
                        OutlinedTextField(
                            value = when (loadingApplicability) {
                                com.civileg.app.domain.calculations.base.DeflectionLoadingApplicability.UNDECLARED -> "Not declared - deflection NOT assessed"
                                com.civileg.app.domain.calculations.base.DeflectionLoadingApplicability.ORDINARY_NO_SENSITIVE_ELEMENTS -> "Ordinary loading, no damage-sensitive elements"
                                com.civileg.app.domain.calculations.base.DeflectionLoadingApplicability.SENSITIVE_OR_IRREGULAR -> "Sensitive/irregular - detailed calc required"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { AppText("Loading / Supported Elements (deflection check)", fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = loadingExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = PremiumDesignSystem.InputShape,
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = loadingExpanded,
                            onDismissRequest = { loadingExpanded = false }
                        ) {
                            com.civileg.app.domain.calculations.base.DeflectionLoadingApplicability.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        AppText(when (option) {
                                            com.civileg.app.domain.calculations.base.DeflectionLoadingApplicability.UNDECLARED -> "Not declared - deflection NOT assessed"
                                            com.civileg.app.domain.calculations.base.DeflectionLoadingApplicability.ORDINARY_NO_SENSITIVE_ELEMENTS -> "Ordinary loading, no damage-sensitive elements"
                                            com.civileg.app.domain.calculations.base.DeflectionLoadingApplicability.SENSITIVE_OR_IRREGULAR -> "Sensitive/irregular - detailed calc required"
                                        }, fontSize = 13.sp)
                                    },
                                    onClick = {
                                        viewModel.clearResult()
                                        loadingApplicability = option
                                        loadingExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Load Combination Dropdown
                item {
                    ExposedDropdownMenuBox(
                        expanded = loadCombinationExpanded,
                        onExpandedChange = { loadCombinationExpanded = !loadCombinationExpanded }
                    ) {
                        OutlinedTextField(
                            value = loadCombination.description,
                            onValueChange = {},
                            readOnly = true,
                            label = { AppText("Load Combination", fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = loadCombinationExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = PremiumDesignSystem.InputShape,
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = loadCombinationExpanded,
                            onDismissRequest = { loadCombinationExpanded = false }
                        ) {
                            LoadCombination.getByCode(
                                runCatching { DesignCode.valueOf(designCode) }.getOrDefault(DesignCode.ECP)
                            ).forEach { combo ->
                                DropdownMenuItem(
                                    text = { AppText(combo.description, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.clearResult()
                                        loadCombination = combo
                                        loadCombinationExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (designCode == "ECP") {
                    item { JoistDetailInputCard(detailDraft) { viewModel.clearResult(); detailDraft = it } }
                }

                // ── Calculate Button ──────────────────────────────────
                item {
                    AppButton(
                        label = stringResource(R.string.hordi_slab_design_button),
                        onClick = {
                            viewModel.clearResult()
                            viewModel.calculateHordiSlab(
                                fcu = fcu.toDoubleOrNull() ?: Double.NaN,
                                fy = fy.toDoubleOrNull() ?: Double.NaN,
                                ribWidth = ribWidth.toDoubleOrNull() ?: Double.NaN,
                                ribSpacing = ribSpacing.toDoubleOrNull() ?: Double.NaN,
                                totalThickness = totalThickness.toDoubleOrNull() ?: Double.NaN,
                                toppingThickness = toppingThickness.toDoubleOrNull() ?: Double.NaN,
                                span = span.toDoubleOrNull() ?: Double.NaN,
                                designMoment = designMoment.toDoubleOrNull() ?: Double.NaN,
                                designShear = designShear.toDoubleOrNull() ?: Double.NaN,
                                loadCombination = loadCombination,
                                designCode = designCode,
                                coverMm = cover.toDoubleOrNull() ?: Double.NaN,
                                barBondClass = barBondClass,
                                supportCondition = supportCondition,
                                loadingApplicability = loadingApplicability,
                                detailCase = detailDraft.toCase(
                                    span.toDoubleOrNull() ?: Double.NaN,
                                    designMoment.toDoubleOrNull() ?: Double.NaN,
                                    designShear.toDoubleOrNull() ?: Double.NaN,
                                    cover.toDoubleOrNull() ?: Double.NaN
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !isLoading,
                        loading = isLoading,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        icon = Icons.Default.Engineering
                    )
                }

                // ── Error Message ─────────────────────────────────────
                errorMsg?.let { err ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x1AE53935)),
                            shape = PremiumDesignSystem.CardShape
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, "Error", tint = Color(0xFFE53935))
                                Spacer(modifier = Modifier.width(10.dp))
                                AppText(err, fontSize = 13.sp, color = Color(0xFFE53935))
                            }
                        }
                    }
                }

                // ── Results ───────────────────────────────────────────
                result?.let { res ->
                    val params = viewModel.calculatedInput ?: return@let
                    val bbsEntries = viewModel.reviewBbsEntries()
                    item {
                        ResultDataCard(
                            "غير مكتمل للتنفيذ / INCOMPLETE FOR CONSTRUCTION",
                            listOf("Status" to "${params.referenceBasis.edition}: method rebuild and complete adopted detailing pending",
                                "Takeoff basis" to com.civileg.app.domain.calculations.utils.HordiReviewTakeoff.LIMITATION),
                            icon = Icons.Default.Warning, accentColor = Color(0xFFE53935)
                        )
                    }
                    val codeReview = viewModel.codeSpecificReview()
                    codeReview?.let { review ->
                        item {
                            val rows = buildList {
                                add("Engineering status" to "NOT ISSUED. Bounded section review only")
                                add("Unverified requirements" to review.limitations)
                                if (review.missingInputs.isNotEmpty()) add("Missing inputs" to review.missingInputs.joinToString("; "))
                                if (review.unsupported.isNotEmpty()) add("Unsupported" to review.unsupported.joinToString("; "))
                                review.section?.let { check ->
                                    add("Effective depth" to "${check.effectiveDepthMm} mm")
                                    add("Required area incl minimum" to (check.requiredAreaWithMinimumMm2?.let { "$it mm2" } ?: "No singly reinforced solution"))
                                    add("Provided area" to "${check.providedAreaMm2} mm2")
                                    add("Section check only" to if (check.sectionPasses) "PASS, not full design" else "FAIL")
                                }
                                review.concreteOnlyShearKn?.let { add("Concrete-only shear threshold" to "$it kN") }
                                review.shearDemandWithinConcreteThreshold?.let { add("Shear threshold check" to if (it) "Within threshold, detailing still unchecked" else "Exceeds threshold") }
                                review.bottomStraightDevelopmentMm?.let { add("Straight bottom development" to "$it mm") }
                                review.leftAnchorageFits?.let { add("Left straight anchorage fits" to it.toString()) }
                                review.rightAnchorageFits?.let { add("Right straight anchorage fits" to it.toString()) }
                                review.effectiveSpanMm?.let { add("Effective span, demand NOT reanalysed" to "${String.format(java.util.Locale.US, "%.2f", it)} mm, 6-2-1-1-1 via 6-2-2-4") }
                                review.crossRibsRequired?.let { add("Cross ribs required, not designed" to it.toString()) }
                                review.crossRibMinimums?.let { minimum ->
                                    add("Cross-rib minima ONLY, 6-2-2-2" to "Width >= ${minimum.minimumWebWidthMm} mm; total depth >= ${minimum.minimumTotalDepthMm} mm; bottom As >= ${minimum.minimumBottomAreaMm2} mm2")
                                    add("Cross-rib top steel rule" to "As top >= 0.5 x ACTUAL cross-rib bottom As. Cross-rib demand, bars and anchorage NOT designed or counted")
                                }
                            }
                            ResultDataCard("ECP 203-2020 partial case review", rows,
                                icon = Icons.Default.Warning, accentColor = Color(0xFFE53935))
                        }
                    }
                    val panelBottom = viewModel.panelBottomTakeoff()
                    val panelEntries = viewModel.panelBottomEntries()
                    if (panelEntries.isNotEmpty()) {
                        item { ResultDataCard("Partial panel bottom steel only",
                            listOf("Limitations" to com.civileg.app.domain.calculations.ecp.ECP2020JoistBottomTakeoff.LIMITATION),
                            icon = Icons.Default.Warning, accentColor = Color(0xFFE53935)) }
                        item { BbsTableCard(panelEntries, title = "Partial panel bottom bars incl straight development, NOT complete BBS") }
                    } else if (panelBottom != null && panelBottom.reasons.isNotEmpty()) {
                        item { ResultDataCard("Partial panel takeoff not available", listOf("Reason" to panelBottom.reasons.joinToString("; ")),
                            icon = Icons.Default.Warning, accentColor = Color(0xFFE53935)) }
                    }
                    if (bbsEntries.isNotEmpty() && panelEntries.isEmpty()) {
                        item { BbsTableCard(bbsEntries, title = "Review only: main bar bodies for ONE RIB, no end anchorage") }
                    }
                    val boqRows = runCatching { viewModel.reviewBoqRows() }.getOrDefault(emptyList())
                    if (boqRows.isNotEmpty()) {
                        item { BoqTableCard(boqRows, title = "Partial review quantities, not total member takeoff") }
                    }

                    // Reinforcement Result
                    item {
                        ResultDataCard(
                            "Legacy engine reinforcement - NOT selected-edition design",
                            listOf(
                                stringResource(R.string.hordi_slab_required_as) to String.format("%.1f mm²", res.requiredReinforcement),
                                stringResource(R.string.hordi_slab_provided_as) to String.format("%.1f mm²", res.providedReinforcement),
                                "Bar Diameter" to String.format("φ%.0f mm", res.barDiameter),
                                "Bars per rib (review)" to (com.civileg.app.domain.calculations.utils.HordiBarAdoption.adoptedCount(res.providedReinforcement, res.barDiameter)?.toString() ?: "Not resolved"),
                                "Rib pitch (not bar spacing)" to String.format("%.0f mm", params.ribSpacing)
                            ),
                            icon = Icons.Default.Build,
                            accentColor = Color(0xFF1565C0)
                        )
                    }

                    // Shear Check
                    item {
                        ResultDataCard(
                            "Legacy engine shear - NOT selected-edition design",
                            listOf(
                                "Shear Capacity" to String.format("%.1f kN", res.shearCapacity),
                                "Min Thickness" to String.format("%.0f mm", res.minThickness),
                                "Utilization" to String.format("%.1f%%", res.utilizationRatio * 100),
                                "Engine Check (unverified)" to when {
                                    !res.bondClassAssessed -> "ESTIMATE ONLY - bar surface undeclared, cannot be accepted"
                                    res.isSafe -> "PASS - method not verified"
                                    else -> "FAIL"
                                },
                                "Serviceability (deflection)" to if (res.serviceabilityAssessed) "assessed" else "NOT ASSESSED - no deflection check",
                                "Engineering acceptance" to if (res.engineeringAcceptance) "ACCEPTED" else "NO - qualification incomplete"
                            ),
                            icon = Icons.Default.Shield,
                            accentColor = Color(0xFFE53935)
                        )
                    }

                    // Warnings
                    if (res.warnings.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x1AFF9800)),
                                shape = PremiumDesignSystem.CardShape
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, "", tint = Color(0xFFFF9800))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        AppText(stringResource(R.string.hordi_slab_warnings), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFF9800))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    res.warnings.forEach { w ->
                                        AppText("• $w", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }

                    // No default mesh, stirrups or support bars are presented
                    // as adopted design. The shared drawing awaits a real layout.
                    item {
                        ResultDataCard("Reinforcement drawing not issued",
                            listOf("Reason" to "Anchorage, topping steel, stirrups, supports and cross ribs are not adopted by this engine"),
                            icon = Icons.Default.Warning, accentColor = Color(0xFFE53935))
                    }

                    // Code Notes
                    if (res.codeNotes.isNotEmpty()) {
                        item { FormulaCard(res.codeNotes, title = stringResource(R.string.hordi_slab_design_calculations)) }
                    }

                    // Export & Save
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton(
                                label = "Export PDF",
                                onClick = { viewModel.exportToPdf(context) { } },
                                modifier = Modifier.weight(1f),
                                enabled = !isExporting,
                                loading = isExporting,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                icon = Icons.Default.PictureAsPdf
                            )
                            AppButton(
                                label = "Save",
                                onClick = { /* save */ },
                                modifier = Modifier.weight(1f),
                                enabled = false,
                                style = AppButtonStyle.Outline,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                icon = Icons.Default.Save
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
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
                            AppText(stringResource(R.string.hordi_slab_calculating), fontSize = 14.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
