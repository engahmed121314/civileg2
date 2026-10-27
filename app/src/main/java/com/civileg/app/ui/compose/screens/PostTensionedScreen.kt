package com.civileg.app.ui.compose.screens

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
    var designCode by remember { mutableStateOf(DesignCode.ECP) }

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
                    Text(
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            item {
                Text(
                    "Post-Tensioned Element / عنصر مسبق الإجهاد",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                EnumDropdown(
                    label = "Element Type / نوع العنصر",
                    options = PostTensionedElementType.entries,
                    selected = elementType,
                    display = { it.displayName },
                    onSelected = { elementType = it }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("Span (m) / البحر", span, Modifier.weight(1f)) { span = it }
                    NumberField("Width (mm) / العرض", width, Modifier.weight(1f)) { width = it }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("Depth (mm) / العمق", depth, Modifier.weight(1f)) { depth = it }
                    NumberField("Cover (mm) / الغطاء", cover, Modifier.weight(1f)) { cover = it }
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
                    NumberField("Tendons / عدد الكابلات", numTendons, Modifier.weight(1f)) { numTendons = it }
                    NumberField("Dead (kN/m)", deadLoad, Modifier.weight(1f)) { deadLoad = it }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("Live (kN/m)", liveLoad, Modifier.weight(1f)) { liveLoad = it }
                    Spacer(Modifier.weight(1f))
                }
            }
            item {
                EnumDropdown(
                    label = "Tendon Type / نوع الكابل",
                    options = PTTendonType.entries,
                    selected = tendonType,
                    display = { it.description },
                    onSelected = { tendonType = it }
                )
            }
            item {
                EnumDropdown(
                    label = "Support / الاستناد",
                    options = PTSupportType.entries,
                    selected = supportType,
                    display = { it.displayName },
                    onSelected = { supportType = it }
                )
            }
            item {
                EnumDropdown(
                    label = "Exposure Class / فئة التعرض",
                    options = PTExposureClass.entries,
                    selected = exposureClass,
                    display = { it.displayName },
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
                Button(
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
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).width(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Calculate, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Calculate / احسب", fontWeight = FontWeight.Bold)
                    }
                }
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

        ResultCard("Prestress Design / تصميم الإجهاد المسبق") {
            ResultRow("Required force", "${fmt(result.prestressDesign.requiredForce)} kN")
            ResultRow("Jacking force", "${fmt(result.prestressDesign.jackingForce)} kN")
            ResultRow("Initial stress", "${fmt(result.prestressDesign.initialStress)} MPa")
            ResultRow("Effective stress", "${fmt(result.prestressDesign.effectiveStress)} MPa")
            ResultRow("Eccentricity", "${fmt(result.prestressDesign.providedEccentricity)} mm")
            ResultRow("Tendons", "${result.prestressDesign.numTendons} × ${result.input.tendonType.description}")
        }

        ResultCard("Prestress Losses / فقد الإجهاد") {
            ResultRow("Elastic shortening", "${fmt(result.losses.elasticShortening)} MPa")
            ResultRow("Creep", "${fmt(result.losses.creep)} MPa")
            ResultRow("Shrinkage", "${fmt(result.losses.shrinkage)} MPa")
            ResultRow("Relaxation", "${fmt(result.losses.steelRelaxation)} MPa")
            ResultRow("Friction", "${fmt(result.losses.friction)} MPa")
            ResultRow("Anchorage slip", "${fmt(result.losses.anchorageSlip)} MPa")
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ResultRow("Total losses", "${fmt(result.losses.total)} MPa (${fmt(result.losses.totalPercent)}%)", bold = true)
        }

        ResultCard("Stress Check / فحص الإجهادات") {
            ResultRow("Transfer top", stressText(result.stressCheck.transferTopCompression, result.stressCheck.transferTopTension))
            ResultRow("Transfer bottom", stressText(result.stressCheck.transferBottomCompression, result.stressCheck.transferBottomTension))
            ResultRow("Service top", stressText(result.stressCheck.serviceTopCompression, result.stressCheck.serviceTopTension))
            ResultRow("Service bottom", stressText(result.stressCheck.serviceBottomCompression, result.stressCheck.serviceBottomTension))
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ResultRow("Transfer safe", yesNo(result.stressCheck.isTransferSafe))
            ResultRow("Service safe", yesNo(result.stressCheck.isServiceSafe))
        }

        ResultCard("Ultimate Strength / المقاومة القصوى") {
            ResultRow("φMn", "${fmt(result.ultimateStrength.flexuralCapacity)} kN.m")
            ResultRow("Mu required", "${fmt(result.ultimateStrength.requiredMoment)} kN.m")
            ResultRow("Flexure utilization", "${fmt(result.ultimateStrength.flexuralUtilization * 100)}%")
            ResultRow("Shear capacity", "${fmt(result.ultimateStrength.shearCapacity)} kN")
            ResultRow("Shear required", "${fmt(result.ultimateStrength.requiredShear)} kN")
            result.ultimateStrength.requiredStirrups?.let { s ->
                ResultRow("Stirrups", "${s.legs}Ø${s.diameter.toInt()} @ ${fmt(s.spacing)} mm")
            }
        }

        ResultCard("Anchorage Zone / منطقة التثبيت") {
            ResultRow("Bursting force", "${fmt(result.anchorageZone.burstingForce)} kN")
            ResultRow("Spalling force", "${fmt(result.anchorageZone.spallingForce)} kN")
            ResultRow("Bursting steel", "${fmt(result.anchorageZone.burstingReinforcement)} mm²")
            ResultRow("Provide", "${result.anchorageZone.stirrupLegs}Ø${result.anchorageZone.stirrupDiameter.toInt()} @ ${fmt(result.anchorageZone.stirrupSpacing)} mm")
        }

        ResultCard("Deflection / الترخيم") {
            ResultRow("Dead load", "${fmt(result.deflection.deadLoadDeflection)} mm")
            ResultRow("Live load", "${fmt(result.deflection.liveLoadDeflection)} mm")
            ResultRow("Prestress (upward)", "${fmt(result.deflection.prestressDeflection)} mm")
            ResultRow("Long-term", "${fmt(result.deflection.longTermDeflection)} mm")
            ResultRow("Total", "${fmt(result.deflection.totalDeflection)} mm", bold = true)
            ResultRow("Allowable", "${fmt(result.deflection.allowableDeflection)} mm")
            ResultRow("Safe", yesNo(result.deflection.isSafe))
        }

        ResultCard("Quantities / الكميات") {
            ResultRow("Concrete", "${fmt(result.quantity.concreteVolume)} m³")
            ResultRow("Mild steel", "${fmt(result.quantity.rebarWeight)} kg")
            ResultRow("PT tendons", "${fmt(result.quantity.ptTendonWeight)} kg")
            ResultRow("Ducts", "${fmt(result.quantity.ductWeight)} kg")
            ResultRow("Anchorages", "${fmt(result.quantity.anchorageWeight)} kg")
            ResultRow("Grout", "${fmt(result.quantity.groutVolume)} m³")
        }

        if (result.warnings.isNotEmpty()) {
            ResultCard("Warnings / تحذيرات") {
                result.warnings.forEach { w ->
                    Text(
                        "• $w",
                        color = Color(0xFFE65100),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        if (result.codeNotes.isNotEmpty()) {
            ResultCard("Code Notes / ملاحظات الكود") {
                result.codeNotes.forEach { n ->
                    Text(
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
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        if (!enabled) {
            CircularProgressIndicator(
                modifier = Modifier.height(16.dp).width(16.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(icon, contentDescription = null)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp)
    }
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
            Text(
                if (isSafe) "Design is SAFE / التصميم آمن" else "Design is UNSAFE / التصميم غير آمن",
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
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
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
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth()
    )
}

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
            label = { Text(label, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option), fontSize = 13.sp) },
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

private fun yesNo(value: Boolean): String = if (value) "Yes / نعم" else "No / لا"

private fun stressText(compression: Double, tension: Double): String {
    return if (compression > 0.0) {
        "C ${fmt(compression)} MPa"
    } else {
        "T ${fmt(tension)} MPa"
    }
}