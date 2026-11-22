package com.civileg.app.ui.compose.screens

import com.civileg.app.domain.entities.DesignCode
import com.civileg.core.engine.rc.ecp.Ecp203BarLayout
import com.civileg.core.engine.rc.ecp.Ecp203RectangularFlexure
import com.civileg.core.engine.rc.ecp.Ecp203RibbedSection
import com.civileg.core.engine.rc.ecp.Ecp203WebShear
import com.civileg.core.engine.rc.ecp.Ecp203LongTermDeflection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.ui.responsive.dimens
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.SlabDesignResult
import com.civileg.app.ui.compose.components.*
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalHordiSlabDrawing
import com.civileg.app.ui.compose.components.drawings.hordiSlabDrawingSummary
import com.civileg.app.ui.compose.components.common.BbsTableCard
import com.civileg.app.ui.compose.components.common.BoqTableCard
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.domain.calculations.utils.BbsGenerator
import com.civileg.app.viewmodel.HordiSlabViewModel
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.app.domain.entities.HordiSlabDesignSnapshot
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.captureToAndroidBitmap
import kotlinx.coroutines.launch
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
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    val pdfCaptureLayer = ComposeDrawingCaptureUtil.rememberDrawingCaptureLayer()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * density.density).toInt()
    val screenHeightPx = (config.screenHeightDp * density.density).toInt()

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
    var cover by remember { mutableStateOf("25") }
    var aggregate by remember { mutableStateOf("20") }
    var mainDiameter by remember { mutableStateOf("16") }
    var barsPerLayer by remember { mutableStateOf("2") }
    var layerCount by remember { mutableStateOf("1") }
    var linksDiameter by remember { mutableStateOf("8") }
    var linksSpacing by remember { mutableStateOf("200") }
    var linksFy by remember { mutableStateOf("240") }
    var linksLegs by remember { mutableStateOf("2") }
    var characteristicLiveLoad by remember { mutableStateOf("3") }
    var contraflexureDistance by remember { mutableStateOf("") }
    var flangeInCompression by remember { mutableStateOf(true) }
    var mainBarsDeformed by remember { mutableStateOf(true) }
    var immediateDeflection by remember { mutableStateOf(false) }
    var serviceDeadLoad by remember { mutableStateOf("") }
    var serviceAnalysisSpan by remember { mutableStateOf("") }
    var longTermDeflection by remember { mutableStateOf(false) }
    var serviceClearSpan by remember { mutableStateOf("") }
    var sustainedLiveFraction by remember { mutableStateOf("") }
    var endMonths by remember { mutableStateOf("") }
    var installationMonths by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<Ecp203LongTermDeflection.Attachment?>(null) }
    var commonSustainedAge by remember { mutableStateOf(false) }
    var thermalTotal by remember { mutableStateOf("") }
    var thermalAfterInstallation by remember { mutableStateOf("") }
    fun supportedMonths(text: String, allowZero: Boolean): Boolean = text.toIntOrNull()?.let {
        it in listOf(3, 6, 12) || it >= 60 || allowZero && it == 0
    } == true
    fun optionalAdverseThermal(text: String) = text.isBlank() ||
        text.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true
    val longTermInputsValid = !longTermDeflection || (
        commonSustainedAge && attachment != null &&
        serviceClearSpan.toDoubleOrNull()?.let { clear -> clear.isFinite() && clear > 0.0 &&
            serviceAnalysisSpan.toDoubleOrNull()?.let { clear <= it + 1e-6 } == true } == true &&
        sustainedLiveFraction.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..1.0 } == true &&
        supportedMonths(endMonths, false) && optionalAdverseThermal(thermalTotal) &&
        (attachment == Ecp203LongTermDeflection.Attachment.NONE ||
            supportedMonths(installationMonths, true) && installationMonths.toInt() <= endMonths.toInt() &&
                optionalAdverseThermal(thermalAfterInstallation)))
    val ecpSection = designCode == "ECP"
    val sectionInputsValid = !ecpSection || (
        listOf(fcu, fy, ribWidth, ribSpacing, totalThickness, toppingThickness, span,
            aggregate, mainDiameter, linksDiameter, linksSpacing, linksFy).all {
            it.toDoubleOrNull()?.let { value -> value.isFinite() && value > 0.0 } == true
        } && listOf(designMoment, designShear, cover, characteristicLiveLoad).all {
            it.toDoubleOrNull()?.let { value -> value.isFinite() && value >= 0.0 } == true
        } && barsPerLayer.toIntOrNull() in 1..100 && layerCount.toIntOrNull() in 1..20 &&
        linksLegs.toIntOrNull() in 2..20 && (!flangeInCompression ||
        contraflexureDistance.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true) &&
        (!immediateDeflection || (flangeInCompression &&
            serviceDeadLoad.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true &&
            serviceAnalysisSpan.toDoubleOrNull()?.let { analysis ->
                analysis.isFinite() && analysis > 0.0 && contraflexureDistance.toDoubleOrNull()?.let {
                    kotlin.math.abs(analysis - it) <= 1e-6
                } == true
            } == true && longTermInputsValid)))
    // Results belong to an immutable calculation input snapshot, not edited form fields.
    LaunchedEffect(listOf(designCode, fcu, fy, ribWidth, ribSpacing, totalThickness,
        toppingThickness, span, designMoment, designShear, loadCombination, cover,
        aggregate, mainDiameter, barsPerLayer, layerCount, linksDiameter, linksSpacing,
        linksFy, linksLegs, characteristicLiveLoad, contraflexureDistance,
        flangeInCompression, mainBarsDeformed, immediateDeflection, serviceDeadLoad,
        serviceAnalysisSpan, longTermDeflection, serviceClearSpan, sustainedLiveFraction,
        endMonths, installationMonths, attachment, commonSustainedAge, thermalTotal,
        thermalAfterInstallation)) { viewModel.clearResult() }


    // Drawing view mode: 0=All, 1=Plan, 2=Section, 3=Detail
    var viewMode by remember { mutableStateOf(0) }
    val viewModeLabels = listOf("All", "Plan", "Section", "Detail")

    val result by viewModel.result.observeAsState()
    val projects by projectViewModel.allProjects.observeAsState(emptyList())
    val isSaving by viewModel.isSaving.observeAsState(false)
    var saveSnapshot by remember { mutableStateOf<HordiSlabDesignSnapshot?>(null) }
    var showSourceNotes by remember(result) { mutableStateOf(false) }
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
                        codes = listOf("ECP" to "ECP 203", "ACI" to "ACI 318", "SBC" to "SBC 304"),
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
                        PremiumInputField(fcu, "fcu (MPa)", { fcu = it }, modifier = Modifier.weight(1f))
                        PremiumInputField(fy, "fy (MPa)", { fy = it }, modifier = Modifier.weight(1f))
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
                            ribWidth, "Rib Width (mm)", { ribWidth = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            ribSpacing, "Rib Spacing (mm)", { ribSpacing = it },
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
                            totalThickness, "Total Thickness (mm)", { totalThickness = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            toppingThickness, "Topping Thk (mm)", { toppingThickness = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    PremiumInputField(
                        span, "Span (mm)", { span = it },
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
                            designMoment, "Moment (kN.m/rib)", { designMoment = it },
                            modifier = Modifier.weight(1f)
                        )
                        PremiumInputField(
                            designShear, "Shear (kN/rib)", { designShear = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    PremiumInputField(
                        cover, if (ecpSection) stringResource(R.string.ecp_ribbed_cover_links) else "Clear Cover (mm)", { cover = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (ecpSection) {
                    item {
                        AppText(stringResource(R.string.ecp_ribbed_section_scope),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    item { PremiumInputField(aggregate, stringResource(R.string.ecp_ribbed_aggregate), { aggregate = it }, modifier = Modifier.fillMaxWidth()) }
                    item { PremiumInputField(mainDiameter, stringResource(R.string.ecp_ribbed_main_diameter), { mainDiameter = it }, modifier = Modifier.fillMaxWidth()) }
                    item { PremiumInputField(barsPerLayer, stringResource(R.string.ecp_ribbed_bars_per_layer), { barsPerLayer = it }, modifier = Modifier.fillMaxWidth()) }
                    item { PremiumInputField(layerCount, stringResource(R.string.ecp_ribbed_layers), { layerCount = it }, modifier = Modifier.fillMaxWidth()) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = mainBarsDeformed, onCheckedChange = { mainBarsDeformed = it })
                            AppText(stringResource(R.string.ecp_ribbed_deformed), modifier = Modifier.weight(1f))
                        }
                    }
                    item { PremiumInputField(linksDiameter, stringResource(R.string.ecp_ribbed_links_diameter), { linksDiameter = it }, modifier = Modifier.fillMaxWidth()) }
                    item { PremiumInputField(linksSpacing, stringResource(R.string.ecp_ribbed_links_spacing), { linksSpacing = it }, modifier = Modifier.fillMaxWidth()) }
                    item { PremiumInputField(linksFy, stringResource(R.string.ecp_ribbed_links_fy), { linksFy = it }, modifier = Modifier.fillMaxWidth()) }
                    item { PremiumInputField(linksLegs, stringResource(R.string.ecp_ribbed_links_legs), { linksLegs = it }, modifier = Modifier.fillMaxWidth()) }
                    item { PremiumInputField(characteristicLiveLoad, stringResource(R.string.ecp_ribbed_live_load), { characteristicLiveLoad = it }, modifier = Modifier.fillMaxWidth()) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = flangeInCompression, onCheckedChange = { flangeInCompression = it })
                            AppText(stringResource(R.string.ecp_ribbed_compression_flange), modifier = Modifier.weight(1f))
                        }
                    }
                    if (flangeInCompression) {
                        item { PremiumInputField(contraflexureDistance, stringResource(R.string.ecp_ribbed_contraflexure), { contraflexureDistance = it }, modifier = Modifier.fillMaxWidth()) }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = immediateDeflection,
                                onCheckedChange = { immediateDeflection = it; if (!it) longTermDeflection = false },
                                modifier = Modifier.testTag("ecp-immediate-deflection-toggle"))
                            AppText(stringResource(R.string.ecp_ribbed_immediate_udl), modifier = Modifier.weight(1f))
                        }
                    }
                    if (immediateDeflection) {
                        item { AppText(stringResource(R.string.ecp_ribbed_immediate_scope), style = MaterialTheme.typography.bodySmall) }
                        item { PremiumInputField(serviceDeadLoad, stringResource(R.string.ecp_ribbed_service_dead), { serviceDeadLoad = it }, modifier = Modifier.fillMaxWidth()) }
                        item { PremiumInputField(serviceAnalysisSpan, stringResource(R.string.ecp_ribbed_analysis_span), { serviceAnalysisSpan = it }, modifier = Modifier.fillMaxWidth()) }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(longTermDeflection, { longTermDeflection = it },
                                    modifier = Modifier.testTag("ecp-long-term-toggle"))
                                AppText(stringResource(R.string.ecp_ribbed_long_term), modifier = Modifier.weight(1f))
                            }
                        }
                        if (longTermDeflection) {
                            item { AppText(stringResource(R.string.ecp_ribbed_long_term_scope), style = MaterialTheme.typography.bodySmall) }
                            item { PremiumInputField(serviceClearSpan, stringResource(R.string.ecp_ribbed_clear_span), { serviceClearSpan = it }, modifier = Modifier.fillMaxWidth()) }
                            item { PremiumInputField(sustainedLiveFraction, stringResource(R.string.ecp_ribbed_sustained_fraction), { sustainedLiveFraction = it }, modifier = Modifier.fillMaxWidth()) }
                            item { PremiumInputField(endMonths, stringResource(R.string.ecp_ribbed_end_months), { endMonths = it }, modifier = Modifier.fillMaxWidth()) }
                            item { AppText(stringResource(R.string.ecp_ribbed_attachment), fontWeight = FontWeight.Bold) }
                            Ecp203LongTermDeflection.Attachment.entries.forEach { kind ->
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { attachment = kind }) {
                                        RadioButton(attachment == kind, { attachment = kind },
                                            modifier = Modifier.testTag("ecp-attachment-${kind.name}"))
                                        AppText(stringResource(when (kind) {
                                            Ecp203LongTermDeflection.Attachment.NONE -> R.string.ecp_ribbed_attachment_none
                                            Ecp203LongTermDeflection.Attachment.DEFLECTION_INSENSITIVE -> R.string.ecp_ribbed_attachment_insensitive
                                            Ecp203LongTermDeflection.Attachment.DEFLECTION_SENSITIVE -> R.string.ecp_ribbed_attachment_sensitive
                                        }), modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            if (attachment != null && attachment != Ecp203LongTermDeflection.Attachment.NONE) {
                                item { PremiumInputField(installationMonths, stringResource(R.string.ecp_ribbed_install_months), { installationMonths = it }, modifier = Modifier.fillMaxWidth()) }
                                item { AppText(stringResource(R.string.ecp_ribbed_install_zero_scope), style = MaterialTheme.typography.bodySmall) }
                            }
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(commonSustainedAge, { commonSustainedAge = it },
                                        modifier = Modifier.testTag("ecp-common-sustained-age"))
                                    AppText(stringResource(R.string.ecp_ribbed_common_age), modifier = Modifier.weight(1f))
                                }
                            }
                            item { AppText(stringResource(R.string.ecp_ribbed_thermal_scope), style = MaterialTheme.typography.bodySmall) }
                            item { PremiumInputField(thermalTotal, stringResource(R.string.ecp_ribbed_thermal_total), { thermalTotal = it }, modifier = Modifier.fillMaxWidth()) }
                            if (attachment != null && attachment != Ecp203LongTermDeflection.Attachment.NONE) {
                                item { PremiumInputField(thermalAfterInstallation, stringResource(R.string.ecp_ribbed_thermal_after), { thermalAfterInstallation = it }, modifier = Modifier.fillMaxWidth()) }
                            }
                        }
                    }
                    if (!sectionInputsValid) item {
                        AppText(stringResource(R.string.ecp_ribbed_invalid_inputs), color = MaterialTheme.colorScheme.error)
                    }
                }

                // Source ECP forces are already factored, so a second-factor selector is misleading.
                if (!ecpSection) item {
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
                                        loadCombination = combo
                                        loadCombinationExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Calculate Button ──────────────────────────────────
                item {
                    AppButton(
                        label = stringResource(R.string.hordi_slab_design_button),
                        onClick = {
                            viewModel.clearResult()
                            viewModel.calculateHordiSlab(
                                fcu = fcu.toDoubleOrNull() ?: 30.0,
                                fy = fy.toDoubleOrNull() ?: 400.0,
                                ribWidth = ribWidth.toDoubleOrNull() ?: 120.0,
                                ribSpacing = ribSpacing.toDoubleOrNull() ?: 500.0,
                                totalThickness = totalThickness.toDoubleOrNull() ?: 350.0,
                                toppingThickness = toppingThickness.toDoubleOrNull() ?: 50.0,
                                span = span.toDoubleOrNull() ?: 6000.0,
                                designMoment = designMoment.toDoubleOrNull() ?: 80.0,
                                designShear = designShear.toDoubleOrNull() ?: 30.0,
                                loadCombination = loadCombination,
                                designCode = designCode,
                                sectionContext = if (!ecpSection) null else Ecp203RibbedSection.Context(
                                    cover.toDouble(), aggregate.toDouble(),
                                    List(layerCount.toInt()) { Ecp203BarLayout.Layer(barsPerLayer.toInt(), mainDiameter.toDouble()) },
                                    if (mainBarsDeformed) Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED else Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD,
                                    if (flangeInCompression) Ecp203RibbedSection.Region.FLANGE_IN_COMPRESSION else Ecp203RibbedSection.Region.FLANGE_IN_TENSION,
                                    if (flangeInCompression) contraflexureDistance.toDouble() else null,
                                    characteristicLiveLoad.toDouble(),
                                    Ecp203WebShear.VerticalLinks(linksFy.toDouble(),
                                        if (linksFy.toDouble() == 240.0) Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD else Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED,
                                        linksDiameter.toDouble(), linksLegs.toInt(), linksSpacing.toDouble()),
                                    if (!immediateDeflection) null else Ecp203RibbedSection.ImmediateDeflectionInput(
                                        serviceAnalysisSpan.toDouble(), serviceDeadLoad.toDouble(),
                                        if (!longTermDeflection) null else Ecp203LongTermDeflection.Input(
                                            serviceClearSpan.toDouble(), sustainedLiveFraction.toDouble(),
                                            Ecp203LongTermDeflection.Duration.ofMonths(endMonths.toInt()), attachment!!,
                                            if (attachment == Ecp203LongTermDeflection.Attachment.NONE) null
                                            else Ecp203LongTermDeflection.Duration.ofMonths(installationMonths.toInt()),
                                            commonSustainedAge,
                                            thermalTotal.takeIf { it.isNotBlank() }?.toDouble(),
                                            if (attachment == Ecp203LongTermDeflection.Attachment.NONE) null
                                            else thermalAfterInstallation.takeIf { it.isNotBlank() }?.toDouble()))
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !isLoading && sectionInputsValid,
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
                    val bbsEntries = if (res.safetyAssessmentBlocked) emptyList() else runCatching {
                        BbsGenerator.generateHordiSlabBbs(
                            mark = "H1",
                            result = res,
                            spanMm = span.toDoubleOrNull() ?: 6000.0,
                            ribSpacingMm = ribSpacing.toDoubleOrNull() ?: 500.0,
                            coverMm = cover.toDoubleOrNull() ?: 25.0
                        )
                    }.getOrDefault(emptyList())
                    if (bbsEntries.isNotEmpty()) {
                        item { BbsTableCard(bbsEntries, title = "Hordi Slab Bar Bending Schedule") }
                    }

                    val boqRows = if (res.safetyAssessmentBlocked) emptyList() else runCatching {
                        BoqEstimator.hordiSlabRows(
                            (span.toDoubleOrNull() ?: 6000.0) / 1000.0,
                            (totalThickness.toDoubleOrNull() ?: 350.0) / 1000.0,
                            bbsEntries.sumOf { it.totalWeightKg },
                            "H1"
                        )
                    }.getOrDefault(emptyList())
                    if (boqRows.isNotEmpty()) {
                        item { BoqTableCard(boqRows, title = "Hordi Slab Bill of Quantities") }
                    }

                    // Safety Status Gauge
                    item {
                        HordiSafetyAssessment(res, designCode)
                    }

                    // Reinforcement Result
                    item {
                        ResultDataCard(
                            stringResource(R.string.hordi_slab_reinforcement),
                            listOf(
                                stringResource(R.string.hordi_slab_required_as) to String.format("%.1f mm²", res.requiredReinforcement),
                                stringResource(R.string.hordi_slab_provided_as) to String.format("%.1f mm²", res.providedReinforcement),
                                "Bar Diameter" to String.format("φ%.0f mm", res.barDiameter),
                                (if (ecpSection) stringResource(R.string.ecp_ribbed_center_spacing) else "Bar Spacing") to String.format("%.0f mm", res.barSpacing)
                            ),
                            icon = Icons.Default.Build,
                            accentColor = Color(0xFF1565C0)
                        )
                    }

                    // Shear Check
                    item {
                        ResultDataCard(
                            stringResource(R.string.hordi_slab_shear_check),
                            listOf(
                                "Shear Capacity" to String.format("%.1f kN", res.shearCapacity),
                                stringResource(R.string.ecp_ribbed_entered_depth) to String.format("%.0f mm", res.minThickness),
                                "Utilization" to String.format("%.1f%%", res.utilizationRatio * 100),
                                "Status" to if (res.safetyAssessmentBlocked) stringResource(R.string.design_status_not_verified)
                                    else if (res.isSafe) "PASS ✓" else "FAIL ✗"
                            ),
                            icon = Icons.Default.Shield,
                            accentColor = if (res.isSafe) Color(0xFF2E7D32) else Color(0xFFE53935)
                        )
                    }

                    if (ecpSection && res.codeNotes.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AppText(stringResource(R.string.ecp_ribbed_section_trace), fontWeight = FontWeight.Bold)
                                AppText(stringResource(R.string.ecp_stage_precision_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // Each stage is a separate lazy item, not a paragraph dump.
                        // Nullable stages preserve decoding of earlier frozen results.
                        val stages = res.calculationStages.orEmpty()
                        items(stages.size) { index ->
                            HordiCalculationStageCard(stages[index], index + 1)
                        }
                        if (stages.isNotEmpty()) item {
                            TextButton(onClick = { showSourceNotes = !showSourceNotes }) {
                                AppText(stringResource(if (showSourceNotes) R.string.ecp_stage_hide_notes
                                    else R.string.ecp_stage_show_notes))
                            }
                        }
                        if (stages.isEmpty() || showSourceNotes) items(res.codeNotes) { note ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                AppText(note, modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    textAlign = TextAlign.Left,
                                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
                            }
                        }
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

                    // The legacy renderer cannot represent source-section layers/regions yet.
                    if (!res.safetyAssessmentBlocked) item {
                        val drawingHeight = if (config.smallestScreenWidthDp >= 600) 580 else 480
                        InteractiveDrawingScreen(
                            onExportDxf = { viewModel.exportToDxf(context) },
                            title = stringResource(R.string.hordi_slab_title),
                            subtitle = "Ribbed Slab Detail",
                            viewModes = viewModeLabels,
                            selectedViewMode = viewMode,
                            onViewModeChanged = { viewMode = it },
                            drawingHeightDp = drawingHeight,
                            onExportPdf = {
                                scope.launch {
                                    val captureBitmap = try {
                                        pdfCaptureLayer.captureToAndroidBitmap()
                                    } catch (_: Exception) { null }
                                    viewModel.pendingDrawingBitmap = captureBitmap
                                    viewModel.exportToPdf(context) { file -> }
                                }
                            },
                            designCode = com.civileg.app.domain.entities.DesignCode.valueOf(designCode),
                            drawingContent = {
                                ProfessionalHordiSlabDrawing(
                                    span = span.toDoubleOrNull() ?: 6000.0,
                                    ribWidth = ribWidth.toDoubleOrNull() ?: 120.0,
                                    ribSpacing = ribSpacing.toDoubleOrNull() ?: 500.0,
                                    totalThickness = totalThickness.toDoubleOrNull() ?: 350.0,
                                    toppingThickness = toppingThickness.toDoubleOrNull() ?: 50.0,
                                    ribBottomDia = res.barDiameter,
                                    ribBottomCount = if (res.barDiameter > 0) maxOf(2, (res.providedReinforcement / (kotlin.math.PI / 4.0 * res.barDiameter * res.barDiameter)).toInt()) else 2,
                                    stirrupDia = 8.0,
                                    stirrupSpacing = 200.0,
                                    cover = cover.toDoubleOrNull() ?: 25.0,
                                    viewMode = viewMode,
                                    resultSummary = hordiSlabDrawingSummary(res),
                                    designCode = com.civileg.app.domain.entities.DesignCode.valueOf(designCode),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        )
                    }

                    // Invisible capture layer for PDF export
                    item {
                        ComposeDrawingCaptureUtil.DrawingCaptureArea(
                            captureLayer = pdfCaptureLayer,
                            widthPx = screenWidthPx,
                            heightPx = screenHeightPx
                        ) {
                            Box(modifier = Modifier.background(Color(0xFF1A1A2E))) {
                                ProfessionalHordiSlabDrawing(
                                    span = span.toDoubleOrNull() ?: 6000.0,
                                    ribWidth = ribWidth.toDoubleOrNull() ?: 120.0,
                                    ribSpacing = ribSpacing.toDoubleOrNull() ?: 500.0,
                                    totalThickness = totalThickness.toDoubleOrNull() ?: 350.0,
                                    toppingThickness = toppingThickness.toDoubleOrNull() ?: 50.0,
                                    ribBottomDia = res.barDiameter,
                                    ribBottomCount = 2,
                                    stirrupDia = 8.0,
                                    stirrupSpacing = 200.0,
                                    cover = 25.0,
                                    viewMode = viewMode,
                                    resultSummary = hordiSlabDrawingSummary(res),
                                    designCode = com.civileg.app.domain.entities.DesignCode.valueOf(designCode),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Code notes for legacy paths. The source trace is already shown above.
                    if (!ecpSection && res.codeNotes.isNotEmpty()) {
                        item { FormulaCard(res.codeNotes, title = stringResource(R.string.hordi_slab_design_calculations)) }
                    }

                    // Export & Save
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton(
                                label = "Export PDF",
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
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                icon = Icons.Default.PictureAsPdf
                            )
                            AppButton(
                                label = stringResource(R.string.save),
                                onClick = { saveSnapshot = viewModel.currentDesignSnapshot() },
                                modifier = Modifier.weight(1f).testTag("hordi-save-open"),
                                enabled = !isSaving && !isLoading && result != null,
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
    val dialogSnapshot = saveSnapshot
    if (dialogSnapshot != null) {
        val savedMessage = stringResource(R.string.save_success)
        HordiSnapshotSaveDialog(
            snapshot = dialogSnapshot,
            projects = projects,
            isSaving = isSaving,
            errorMessage = errorMsg,
            onDismiss = { saveSnapshot = null },
            onSave = { projectId, name ->
                viewModel.saveDesign(projectId, name, dialogSnapshot) {
                    saveSnapshot = null
                    scope.launch { snackbarHostState.showSnackbar(savedMessage) }
                }
            }
        )
    }
}


/** A method hold is distinct from a failed numerical check. */
@Composable
internal fun HordiSafetyAssessment(result: SlabDesignResult, designCode: String) {
    if (result.safetyAssessmentBlocked) {
        HordiMethodHoldNotice()
    } else {
        SafetyStatusCard(
            utilizationRatio = result.utilizationRatio,
            isSafe = result.isSafe,
            title = stringResource(R.string.hordi_slab_safety_title, designCode)
        )
    }
}

/** Shared by dedicated and generic slab entry points while the ECP method is held. */
@Composable
internal fun HordiMethodHoldNotice() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        AppText(
            stringResource(R.string.hordi_method_hold),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
