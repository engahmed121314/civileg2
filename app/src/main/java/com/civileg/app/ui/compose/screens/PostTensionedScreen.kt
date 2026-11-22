package com.civileg.app.ui.compose.screens

import com.civileg.app.ui.responsive.dimens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedElementType
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedInput
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedResult
import com.civileg.app.domain.calculations.base.posttensioned.PTSupportType
import com.civileg.app.domain.calculations.base.posttensioned.PTTendonType
import com.civileg.app.domain.calculations.base.posttensioned.PTExposureClass
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.ui.compose.components.drawings.PostTensionedElevationDrawing
import com.civileg.app.ui.compose.components.drawings.PostTensionedSectionDrawing
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.captureToAndroidBitmap
import com.civileg.app.viewmodel.PostTensionedViewModel
import kotlinx.coroutines.launch
import com.civileg.app.ui.defaultDesignCode
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor

/**
 * شاشة تصميم العناصر مسبقة الإجهاد (Post-Tensioned) وفق ECP / ACI / SBC.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostTensionedScreen(
    viewModel: PostTensionedViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val result by viewModel.result.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureLayer = rememberGraphicsLayer()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it) }
    }

    var elementType by remember { mutableStateOf(PostTensionedElementType.Beam) }
    var supportType by remember { mutableStateOf(PTSupportType.SimplySupported) }
    var tendonType by remember { mutableStateOf(PTTendonType.Strand_7Wire_12_7mm) }
    var exposureClass by remember { mutableStateOf(PTExposureClass.Moderate) }
    var designCode by remember { mutableStateOf(defaultDesignCode()) }

    var span by remember { mutableStateOf("10.0") }
    var width by remember { mutableStateOf("300") }
    var depth by remember { mutableStateOf("800") }
    var fcu by remember { mutableStateOf("40") }
    var fy by remember { mutableStateOf("400") }
    var fpk by remember { mutableStateOf("1860") }
    var fp01k by remember { mutableStateOf("1610") }
    var numTendons by remember { mutableStateOf("6") }
    var deadLoad by remember { mutableStateOf("15.0") }
    var liveLoad by remember { mutableStateOf("10.0") }
    var cover by remember { mutableStateOf("40") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    AppText(
                        stringResource(R.string.screen_post_tensioned_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            item {
                AppText(
                    stringResource(R.string.pt_element_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                EnumDropdown(
                    label = stringResource(R.string.pt_element_type),
                    options = PostTensionedElementType.entries,
                    selected = elementType,
                    display = { it.localizedName() },
                    onSelected = { elementType = it }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(stringResource(R.string.pt_span), span, Modifier.weight(1f)) { span = it }
                    NumberField(stringResource(R.string.pt_width), width, Modifier.weight(1f)) { width = it }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(stringResource(R.string.pt_depth), depth, Modifier.weight(1f)) { depth = it }
                    NumberField(stringResource(R.string.pt_cover), cover, Modifier.weight(1f)) { cover = it }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("fcu (MPa)", fcu, Modifier.weight(1f)) { fcu = it }
                    NumberField("fy (MPa)", fy, Modifier.weight(1f)) { fy = it }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("fpk (MPa)", fpk, Modifier.weight(1f)) { fpk = it }
                    NumberField("fp0.1k (MPa)", fp01k, Modifier.weight(1f)) { fp01k = it }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(stringResource(R.string.pt_tendons), numTendons, Modifier.weight(1f)) { numTendons = it }
                    NumberField(stringResource(R.string.pt_dead_load), deadLoad, Modifier.weight(1f)) { deadLoad = it }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(stringResource(R.string.pt_live_load), liveLoad, Modifier.weight(1f)) { liveLoad = it }
                    Spacer(Modifier.weight(1f))
                }
            }
            item {
                EnumDropdown(
                    label = stringResource(R.string.pt_tendon_type),
                    options = PTTendonType.entries,
                    selected = tendonType,
                    display = { it.localizedName() },
                    onSelected = { tendonType = it }
                )
            }
            item {
                EnumDropdown(
                    label = stringResource(R.string.pt_support),
                    options = PTSupportType.entries,
                    selected = supportType,
                    display = { it.localizedName() },
                    onSelected = { supportType = it }
                )
            }
            item {
                EnumDropdown(
                    label = stringResource(R.string.pt_exposure_class),
                    options = PTExposureClass.entries,
                    selected = exposureClass,
                    display = { it.localizedName() },
                    onSelected = { exposureClass = it }
                )
            }
            item {
                DesignCodeSelectorRow(
                    selectedCode = designCode,
                    onCodeSelected = { designCode = it }
                )
            }
            item {
                AppButton(
                    label = stringResource(R.string.pt_calculate),
                    onClick = {
                        val input = PostTensionedInput(
                            elementType = elementType,
                            span = span.toDoubleOrNull() ?: 0.0,
                            width = width.toDoubleOrNull() ?: 0.0,
                            depth = depth.toDoubleOrNull() ?: 0.0,
                            fcu = fcu.toDoubleOrNull() ?: 0.0,
                            fy = fy.toDoubleOrNull() ?: 0.0,
                            fpk = fpk.toDoubleOrNull() ?: 0.0,
                            fp01k = fp01k.toDoubleOrNull() ?: 0.0,
                            tendonType = tendonType,
                            numTendons = numTendons.toIntOrNull() ?: 1,
                            tendonArea = tendonType.areaMm2,
                            deadLoad = deadLoad.toDoubleOrNull() ?: 0.0,
                            liveLoad = liveLoad.toDoubleOrNull() ?: 0.0,
                            supportType = supportType,
                            cover = cover.toDoubleOrNull() ?: 40.0,
                            designCode = designCode,
                            exposureClass = exposureClass
                        )
                        viewModel.calculate(input)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    loading = isLoading,
                    fontWeight = FontWeight.Bold,
                    icon = Icons.Default.Calculate
                )
            }

            result?.let { r ->
                item {
                    PostTensionedResultContent(
                        result = r,
                        isExporting = isExporting,
                        onExportPdf = {
                            scope.launch {
                                val bitmap = try {
                                    captureLayer.captureToAndroidBitmap()
                                } catch (_: Exception) {
                                    null
                                }
                                viewModel.pendingDrawingBitmap = bitmap
                                viewModel.exportToPdf(context) { file ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (file != null) "PDF saved: ${file.name}"
                                            else "PDF export failed"
                                        )
                                    }
                                }
                            }
                        },
                        onExportDxf = {
                            viewModel.exportDxf(context) { file ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (file != null) "DXF saved: ${file.name}"
                                        else "DXF export failed"
                                    )
                                }
                            }
                        },
                        onExportCsv = {
                            viewModel.exportCsv(context) { file ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (file != null) "CSV saved: ${file.name}"
                                        else "CSV export failed"
                                    )
                                }
                            }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
            }

            result?.let { r ->
                ComposeDrawingCaptureUtil.DrawingCaptureArea(
                    captureLayer = captureLayer,
                    widthPx = 1200,
                    heightPx = 1000
                ) {
                    Column(modifier = Modifier.background(Color.White)) {
                        PostTensionedSectionDrawing(input = r.input, result = r)
                        PostTensionedElevationDrawing(input = r.input, result = r)
                    }
                }
            }
        }
    }
}

@Composable
private fun PostTensionedResultContent(
    result: PostTensionedResult,
    isExporting: Boolean = false,
    onExportPdf: () -> Unit = {},
    onExportDxf: () -> Unit = {},
    onExportCsv: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StatusBanner(result.isSafe)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExportButton(
                label = "PDF",
                icon = Icons.Default.PictureAsPdf,
                enabled = !isExporting,
                modifier = Modifier.weight(1f),
                onClick = onExportPdf
            )
            ExportButton(
                label = "DXF",
                icon = Icons.Default.Download,
                enabled = !isExporting,
                modifier = Modifier.weight(1f),
                onClick = onExportDxf
            )
            ExportButton(
                label = "CSV",
                icon = Icons.Default.GridOn,
                enabled = !isExporting,
                modifier = Modifier.weight(1f),
                onClick = onExportCsv
            )
        }

        PostTensionedSectionDrawing(input = result.input, result = result)
        PostTensionedElevationDrawing(input = result.input, result = result)

        ResultCard(stringResource(R.string.pt_prestress_design)) {
            ResultRow(stringResource(R.string.pt_r_required_force), "${fmt(result.prestressDesign.requiredForce)} kN")
            ResultRow(stringResource(R.string.pt_r_jacking_force), "${fmt(result.prestressDesign.jackingForce)} kN")
            ResultRow(stringResource(R.string.pt_r_initial_stress), "${fmt(result.prestressDesign.initialStress)} MPa")
            ResultRow(stringResource(R.string.pt_r_effective_stress), "${fmt(result.prestressDesign.effectiveStress)} MPa")
            ResultRow(stringResource(R.string.pt_r_eccentricity), "${fmt(result.prestressDesign.providedEccentricity)} mm")
            ResultRow(stringResource(R.string.pt_r_tendons), "${result.prestressDesign.numTendons} × ${result.input.tendonType.description}")
        }

        ResultCard(stringResource(R.string.pt_losses)) {
            ResultRow(stringResource(R.string.pt_r_elastic_shortening), "${fmt(result.losses.elasticShortening)} MPa")
            ResultRow(stringResource(R.string.pt_r_creep), "${fmt(result.losses.creep)} MPa")
            ResultRow(stringResource(R.string.pt_r_shrinkage), "${fmt(result.losses.shrinkage)} MPa")
            ResultRow(stringResource(R.string.pt_r_relaxation), "${fmt(result.losses.steelRelaxation)} MPa")
            ResultRow(stringResource(R.string.pt_r_friction), "${fmt(result.losses.friction)} MPa")
            ResultRow(stringResource(R.string.pt_r_anchorage_slip), "${fmt(result.losses.anchorageSlip)} MPa")
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ResultRow(stringResource(R.string.pt_r_total_losses), "${fmt(result.losses.total)} MPa (${fmt(result.losses.totalPercent)}%)", bold = true)
        }

        ResultCard(stringResource(R.string.pt_stress_check)) {
            ResultRow(stringResource(R.string.pt_r_transfer_top), stressText(result.stressCheck.transferTopCompression, result.stressCheck.transferTopTension))
            ResultRow(stringResource(R.string.pt_r_transfer_bottom), stressText(result.stressCheck.transferBottomCompression, result.stressCheck.transferBottomTension))
            ResultRow(stringResource(R.string.pt_r_service_top), stressText(result.stressCheck.serviceTopCompression, result.stressCheck.serviceTopTension))
            ResultRow(stringResource(R.string.pt_r_service_bottom), stressText(result.stressCheck.serviceBottomCompression, result.stressCheck.serviceBottomTension))
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ResultRow(stringResource(R.string.pt_r_transfer_safe), yesNo(result.stressCheck.isTransferSafe))
            ResultRow(stringResource(R.string.pt_r_service_safe), yesNo(result.stressCheck.isServiceSafe))
        }

        ResultCard(stringResource(R.string.pt_ultimate_strength)) {
            ResultRow("φMn", "${fmt(result.ultimateStrength.flexuralCapacity)} kN.m")
            ResultRow(stringResource(R.string.pt_r_mu_required), "${fmt(result.ultimateStrength.requiredMoment)} kN.m")
            ResultRow(stringResource(R.string.pt_r_flexure_util), "${fmt(result.ultimateStrength.flexuralUtilization * 100)}%")
            ResultRow(stringResource(R.string.pt_r_shear_capacity), "${fmt(result.ultimateStrength.shearCapacity)} kN")
            ResultRow(stringResource(R.string.pt_r_shear_required), "${fmt(result.ultimateStrength.requiredShear)} kN")
            result.ultimateStrength.requiredStirrups?.let { s ->
                ResultRow(stringResource(R.string.pt_r_stirrups), "${s.legs}Ø${s.diameter.toInt()} @ ${fmt(s.spacing)} mm")
            }
        }

        ResultCard(stringResource(R.string.pt_anchorage_zone)) {
            ResultRow(stringResource(R.string.pt_r_bursting_force), "${fmt(result.anchorageZone.burstingForce)} kN")
            ResultRow(stringResource(R.string.pt_r_spalling_force), "${fmt(result.anchorageZone.spallingForce)} kN")
            ResultRow(stringResource(R.string.pt_r_bursting_steel), "${fmt(result.anchorageZone.burstingReinforcement)} mm²")
            ResultRow(stringResource(R.string.pt_r_provide), "${result.anchorageZone.stirrupLegs}Ø${result.anchorageZone.stirrupDiameter.toInt()} @ ${fmt(result.anchorageZone.stirrupSpacing)} mm")
        }

        ResultCard(stringResource(R.string.pt_deflection)) {
            ResultRow(stringResource(R.string.pt_r_dead_load), "${fmt(result.deflection.deadLoadDeflection)} mm")
            ResultRow(stringResource(R.string.pt_r_live_load), "${fmt(result.deflection.liveLoadDeflection)} mm")
            ResultRow(stringResource(R.string.pt_r_prestress_up), "${fmt(result.deflection.prestressDeflection)} mm")
            ResultRow(stringResource(R.string.pt_r_long_term), "${fmt(result.deflection.longTermDeflection)} mm")
            ResultRow(stringResource(R.string.pt_r_total), "${fmt(result.deflection.totalDeflection)} mm", bold = true)
            ResultRow(stringResource(R.string.pt_r_allowable), "${fmt(result.deflection.allowableDeflection)} mm")
            ResultRow(stringResource(R.string.pt_r_safe), yesNo(result.deflection.isSafe))
        }

        ResultCard(stringResource(R.string.pt_quantities)) {
            ResultRow(stringResource(R.string.pt_r_concrete), "${fmt(result.quantity.concreteVolume)} m³")
            ResultRow(stringResource(R.string.pt_r_mild_steel), "${fmt(result.quantity.rebarWeight)} kg")
            ResultRow(stringResource(R.string.pt_r_pt_tendons), "${fmt(result.quantity.ptTendonWeight)} kg")
            ResultRow(stringResource(R.string.pt_r_ducts), "${fmt(result.quantity.ductWeight)} kg")
            ResultRow(stringResource(R.string.pt_r_anchorages), "${fmt(result.quantity.anchorageWeight)} kg")
            ResultRow(stringResource(R.string.pt_r_grout), "${fmt(result.quantity.groutVolume)} m³")
        }

        if (result.warnings.isNotEmpty()) {
            ResultCard(stringResource(R.string.pt_warnings)) {
                result.warnings.forEach { w ->
                    AppText(
                        "• $w",
                        color = Color(0xFFE65100),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        if (result.codeNotes.isNotEmpty()) {
            ResultCard(stringResource(R.string.pt_code_notes)) {
                result.codeNotes.forEach { n ->
                    AppText(
                        "• $n",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AppButton(
        label = label,
        onClick = onClick,
        enabled = enabled,
        loading = !enabled,
        modifier = modifier,
        style = AppButtonStyle.Outline,
        icon = icon,
        fontSize = 12.sp
    )
}

@Composable
private fun StatusBanner(isSafe: Boolean) {
    val color = if (isSafe) Color(0xFF2E7D32) else Color(0xFFC62828)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (isSafe) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = color
            )
            AppText(
                stringResource(if (isSafe) R.string.pt_design_safe else R.string.pt_design_unsafe),
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ResultCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            AppText(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AppText(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        AppText(
            value,
            fontSize = 12.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { AppText(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth()
    )
}

/** Display labels localize; the enum value itself stays the selection model. */
@Composable
private fun PostTensionedElementType.localizedName(): String = stringResource(when (this) {
    PostTensionedElementType.Beam -> R.string.pt_elem_beam
    PostTensionedElementType.Slab -> R.string.pt_elem_slab_one_way
    PostTensionedElementType.SlabTwoWay -> R.string.pt_elem_slab_two_way
    PostTensionedElementType.BandBeam -> R.string.pt_elem_band_beam
    PostTensionedElementType.TransferBeam -> R.string.pt_elem_transfer_beam
    PostTensionedElementType.Girder -> R.string.pt_elem_girder
    PostTensionedElementType.BridgeGirder -> R.string.pt_elem_bridge_girder
    PostTensionedElementType.TankWall -> R.string.pt_elem_tank_wall
    PostTensionedElementType.SiloWall -> R.string.pt_elem_silo_wall
})

@Composable
private fun PTTendonType.localizedName(): String = stringResource(when (this) {
    PTTendonType.Strand_7Wire_9_5mm -> R.string.pt_tendon_9_5
    PTTendonType.Strand_7Wire_11_1mm -> R.string.pt_tendon_11_1
    PTTendonType.Strand_7Wire_12_7mm -> R.string.pt_tendon_12_7
    PTTendonType.Strand_7Wire_15_2mm -> R.string.pt_tendon_15_2
    PTTendonType.Strand_7Wire_18_0mm -> R.string.pt_tendon_18_0
    PTTendonType.Wire_5mm -> R.string.pt_tendon_wire5
    PTTendonType.Wire_7mm -> R.string.pt_tendon_wire7
    PTTendonType.Bar_25mm -> R.string.pt_tendon_bar25
    PTTendonType.Bar_32mm -> R.string.pt_tendon_bar32
})

@Composable
private fun PTSupportType.localizedName(): String = stringResource(when (this) {
    PTSupportType.SimplySupported -> R.string.pt_support_simply
    PTSupportType.FixedFixed -> R.string.pt_support_fixed_fixed
    PTSupportType.FixedHinged -> R.string.pt_support_fixed_hinged
    PTSupportType.Cantilever -> R.string.pt_support_cantilever
    PTSupportType.Continuous -> R.string.pt_support_continuous
})

@Composable
private fun PTExposureClass.localizedName(): String = stringResource(when (this) {
    PTExposureClass.Mild -> R.string.pt_exposure_mild
    PTExposureClass.Moderate -> R.string.pt_exposure_moderate
    PTExposureClass.Severe -> R.string.pt_exposure_severe
    PTExposureClass.VerySevere -> R.string.pt_exposure_very_severe
    PTExposureClass.Extreme -> R.string.pt_exposure_extreme
})

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    display: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            label = { AppText(label, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { AppText(display(option), fontSize = 13.sp) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun fmt(value: Double): String = "%.2f".format(value)

@Composable
private fun yesNo(value: Boolean): String = stringResource(if (value) R.string.yes else R.string.no)

private fun stressText(compression: Double, tension: Double): String {
    return if (compression > 0.0) {
        "C ${fmt(compression)} MPa"
    } else {
        "T ${fmt(tension)} MPa"
    }
}