package com.civileg.app.features.steel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.ui.responsive.dimens
import com.civileg.app.domain.calculations.aci.SteelFlexuralResult
import com.civileg.app.domain.calculations.ecp.BoltDesignResult
import com.civileg.app.domain.calculations.ecp.BlockShearResult
import com.civileg.app.domain.calculations.ecp.SteelConnectionDesign
import com.civileg.app.domain.calculations.ecp.SteelBasePlateDesign
import com.civileg.app.domain.calculations.ecp.WeldDesignResult
import com.civileg.app.domain.entities.*
import com.civileg.app.ui.compose.components.PremiumDesignSystem
import com.civileg.app.ui.compose.components.PremiumSectionHeader
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen as AppInteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalSteelDrawing
import com.civileg.app.ui.compose.components.drawings.ProfessionalWarehouseDrawing
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.utils.ComposeDrawingCaptureUtil
import com.civileg.app.utils.captureToAndroidBitmap
import com.civileg.app.features.steel.SteelViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import java.io.File
import java.util.*
import kotlin.math.*
import kotlinx.coroutines.launch
import com.civileg.app.ui.defaultDesignCode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelDesignScreen(
    viewModel: SteelViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val result by viewModel.result.observeAsState()
    val warehouseResult by viewModel.warehouseResult.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.steel_warehouse_tab),
        stringResource(R.string.steel_sections_tab),
        stringResource(R.string.steel_connections_tab),
        stringResource(R.string.steel_baseplate_tab),
        stringResource(R.string.steel_structure_tab)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.home_steel), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { AppText(stringResource(R.string.reset)) },
                                onClick = { showMenu = false; viewModel.resetResult() },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { AppText(title) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> SteelWarehouseTab(viewModel, warehouseResult, isLoading)
                1 -> SteelSectionTab(viewModel, result, isLoading)
                2 -> ConnectionDesignTab(viewModel)
                3 -> BasePlateDesignTab(viewModel)
                4 -> SteelStructureAndBOMTab(viewModel, warehouseResult, isLoading)
            }


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
                        stringResource(R.string.steel_calculating),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SteelSectionTab(viewModel: SteelViewModel, result: SteelMemberResult?, isLoading: Boolean) {
    val context = LocalContext.current
    var selectedCategory by rememberSaveable { mutableStateOf("IPE (European I-Beams)") }
    var selectedSection by remember { mutableStateOf<SteelSectionType?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var expandedCategory by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf(false) }

    var axialLoad by rememberSaveable { mutableStateOf("500") }
    var moment by rememberSaveable { mutableStateOf("100") }
    var shear by rememberSaveable { mutableStateOf("50") }
    var length by rememberSaveable { mutableStateOf("3") }
    var selectedMemberType by rememberSaveable { mutableStateOf(SteelMemberType.COLUMN) }
    var selectedCode by rememberSaveable { mutableStateOf(defaultDesignCode()) }

    val searchResults by viewModel.searchResults.observeAsState(emptyList())
    val library = viewModel.sectionLibrary
    val categories = library.keys.toList()

    val steelGrades = SteelGrade.entries
    var selectedGrade by rememberSaveable { mutableStateOf(SteelGrade.ST37) }
    var expandedGrade by remember { mutableStateOf(false) }
    val isExporting by viewModel.isExporting.observeAsState(false)

    // PDF drawing capture: the export embeds the view that is on screen right now.
    val pdfCaptureLayer = ComposeDrawingCaptureUtil.rememberDrawingCaptureLayer()
    val captureScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * density.density).toInt().coerceAtLeast(800)
    val screenHeightPx = (config.screenHeightDp * density.density).toInt().coerceAtLeast(600)
    // Hoisted out of the drawing item so the invisible capture area (below the list) can
    // render the same tab the user has selected.
    var selectedViewMode by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.sectionSpacing)
    ) {
        item { SteelModuleSectionHeader(stringResource(R.string.steel_section_dictionary), R.drawable.ic_steel) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DesignCodeSelectorRow(
                            selectedCode = selectedCode,
                            onCodeSelected = { selectedCode = it },
                            modifier = Modifier.weight(1f)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            AppText(stringResource(R.string.steel_grade_label), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            ExposedDropdownMenuBox(
                                expanded = expandedGrade,
                                onExpandedChange = { expandedGrade = !expandedGrade }
                            ) {
                                OutlinedTextField(
                                    value = selectedGrade.displayName,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGrade) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedGrade,
                                    onDismissRequest = { expandedGrade = false }
                                ) {
                                    steelGrades.forEach { grade ->
                                        DropdownMenuItem(
                                            text = { AppText(grade.displayName) },
                                            onClick = { selectedGrade = grade; expandedGrade = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    viewModel.searchSections(it)
                },
                label = { AppText(stringResource(R.string.steel_search_hint)) },
                placeholder = { AppText(stringResource(R.string.steel_section_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = ""; viewModel.searchSections("") }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (searchQuery.isNotEmpty()) {
            item {
                AppText(stringResource(R.string.steel_search_results), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    searchResults.take(8).forEach { section ->
                        Surface(
                            onClick = { 
                                searchQuery = ""
                                viewModel.searchSections("")
                                categories.find { cat -> library[cat]?.any { s -> s.sectionName == section.sectionName } == true }?.let { cat ->
                                    selectedCategory = cat
                                    selectedSection = section
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Architecture, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                AppText(section.displayName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(stringResource(R.string.steel_section_category), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    ExposedDropdownMenuBox(
                        expanded = expandedCategory,
                        onExpandedChange = { expandedCategory = !expandedCategory }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCategory,
                            onDismissRequest = { expandedCategory = false }
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { AppText(category) },
                                    onClick = {
                                        selectedCategory = category
                                        selectedSection = library[category]?.firstOrNull()
                                        expandedCategory = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    AppText(stringResource(R.string.steel_selected_section), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    ExposedDropdownMenuBox(
                        expanded = expandedSection,
                        onExpandedChange = { expandedSection = !expandedSection }
                    ) {
                        OutlinedTextField(
                            value = selectedSection?.sectionName ?: stringResource(R.string.steel_select_section),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSection) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSection,
                            onDismissRequest = { expandedSection = false }
                        ) {
                            library[selectedCategory]?.forEach { section ->
                                DropdownMenuItem(
                                    text = { AppText(section.sectionName) },
                                    onClick = {
                                        selectedSection = section
                                        expandedSection = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        selectedSection?.let { section ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            AppText(stringResource(R.string.steel_section_properties), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                PropertyLine("h", "${section.depth.toInt()} mm")
                                PropertyLine("b", "${section.width.toInt()} mm")
                                PropertyLine("tw", "${section.webThickness} mm")
                                PropertyLine("tf", "${section.flangeThickness} mm")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                PropertyLine(stringResource(R.string.steel_section_area), "${String.format("%.1f", section.area / 100.0)} cm\u00B2")
                                PropertyLine(stringResource(R.string.steel_section_weight), "${String.format("%.1f", section.weight)} kg/m")
                                PropertyLine("Ix", "${String.format("%.0f", section.ix / 10000.0)} cm\u2074")
                                PropertyLine("Zx", "${String.format("%.0f", section.zx / 1000.0)} cm\u00B3")
                            }
                        }
                    }
                }
            }

            item {
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        AppText(
                            stringResource(R.string.steel_section_drawing),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        ProfessionalSteelDrawing(
                            sectionName = section.sectionName,
                            sectionType = section.displayName,
                            depth = section.depth,
                            flangeWidth = section.width,
                            webThickness = section.webThickness,
                            flangeThickness = section.flangeThickness,
                            memberLength = (length.toDoubleOrNull() ?: 3.0) * 1000.0,
                            radius = section.rootRadius,
                            area = section.area,
                            ix = section.ix,
                            sx = section.sx,
                            zx = section.zx,
                            weightPerMeter = section.weight,
                            isColumn = selectedMemberType == SteelMemberType.COLUMN,
                            viewMode = 0,
                            modifier = Modifier.fillMaxWidth().height(320.dp)
                        )
                    }
                }
            }
        }

        item { SteelModuleSectionHeader(stringResource(R.string.steel_applied_loads), R.drawable.ic_frame) }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(axialLoad, stringResource(R.string.steel_axial_pu_kn), { axialLoad = it }, Modifier.weight(1f))
                        SteelInputField(moment, stringResource(R.string.steel_moment_mu_knm), { moment = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteelInputField(shear, stringResource(R.string.steel_shear_vu_kn), { shear = it }, Modifier.weight(1f))
                        SteelInputField(length, stringResource(R.string.steel_length_m_label), { length = it }, Modifier.weight(1f))
                    }
                    
                    AppText(stringResource(R.string.steel_member_type_label), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        SteelMemberType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedMemberType == type,
                                onClick = { selectedMemberType = type },
                                label = { AppText(type.name, fontSize = 10.sp) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            val sectionInputsValid = listOf(axialLoad, moment, shear, length).all { it.trim().toDoubleOrNull() != null } &&
                (length.trim().toDoubleOrNull() ?: 0.0) > 0.0
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!sectionInputsValid) {
                    AppText(
                        text = stringResource(R.string.input_invalid_numbers),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        label = stringResource(R.string.steel_analyze_section),
                        onClick = {
                            val sec = selectedSection
                            val pu = axialLoad.trim().toDoubleOrNull()
                            val mu = moment.trim().toDoubleOrNull()
                            val vu = shear.trim().toDoubleOrNull()
                            val lb = length.trim().toDoubleOrNull()
                            if (sec != null && pu != null && mu != null && vu != null && lb != null) {
                                viewModel.calculateSteelMember(
                                    section = sec,
                                    memberType = selectedMemberType,
                                    inputs = SteelInputs(
                                        axialLoad = pu,
                                        moment = mu,
                                        shear = vu,
                                        unbracedLength = lb * 1000.0,
                                        length = lb * 1000.0,
                                        grade = selectedGrade
                                    ),
                                    code = selectedCode
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && selectedSection != null && sectionInputsValid,
                        loading = isLoading
                    )

                    if (result != null) {
                        AppButton(
                            label = stringResource(R.string.pdf_report),
                            onClick = {
                                captureScope.launch {
                                    val captureBitmap = try {
                                        pdfCaptureLayer.captureToAndroidBitmap()
                                    } catch (_: Exception) { null }
                                    viewModel.pendingDrawingBitmap = captureBitmap
                                    viewModel.exportToPdf(context) { }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.PictureAsPdf,
                            containerOverride = MaterialTheme.colorScheme.secondary,
                            contentOverride = onColorFor(MaterialTheme.colorScheme.secondary),
                            enabled = !isExporting,
                            loading = isExporting
                        )
                    }
                }
            }
        }

        result?.let { res ->
            item {
                val animatedRatio by animateFloatAsState(targetValue = res.utilizationRatio.toFloat(), animationSpec = tween(1000), label = "ur")
                val urColor = when {
                    res.utilizationRatio > 1.0 -> Color.Red
                    res.utilizationRatio > 0.9 -> Color(0xFFF57C00)
                    else -> Color(0xFF2E7D32)
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = urColor.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, urColor.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                AppText(stringResource(R.string.steel_utilization_ratio), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                AppText(
                                    if (res.isSafe) stringResource(R.string.steel_section_safe) else stringResource(R.string.steel_section_unsafe),
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red,
                                    fontSize = 13.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    progress = { animatedRatio.coerceAtMost(1f) },
                                    modifier = Modifier.size(64.dp),
                                    strokeWidth = 7.dp,
                                    color = urColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                AppText(
                                    "${(res.utilizationRatio * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = urColor
                                )
                            }
                        }
                    }
                }
            }
            item { SteelResultCard(res) }

            val flexRes = res.detailedResults["flexRes"] as? SteelFlexuralResult
            if (flexRes != null && flexRes.calculationTrace.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            AppText(stringResource(R.string.steel_calculation_trace), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            flexRes.calculationTrace.forEach { step ->
                                AppText("• $step", fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            item {
                AppInteractiveDrawingScreen(
                    title = stringResource(R.string.steel_section_drawing),
                    subtitle = stringResource(R.string.steel_member_detail),
                    viewModes = listOf(
                        stringResource(R.string.steel_view_all),
                        stringResource(R.string.steel_view_longitudinal),
                        stringResource(R.string.steel_view_cross),
                        stringResource(R.string.steel_view_connections)
                    ),
                    selectedViewMode = selectedViewMode,
                    onViewModeChanged = { selectedViewMode = it },
                    drawingContent = {
                        val connParams = com.civileg.app.utils.SteelConnectionDrawing.from(res.connectionDesign)
                        val endPlateThk = 12.0
                        val hasStiff = false

                        ProfessionalSteelDrawing(
                            sectionName = res.sectionType.sectionName,
                            depth = res.sectionType.depth,
                            flangeWidth = res.sectionType.width,
                            webThickness = res.sectionType.webThickness,
                            flangeThickness = res.sectionType.flangeThickness,
                            memberLength = (length.toDoubleOrNull() ?: 3.0) * 1000.0,
                            isSafe = res.isSafe,
                            utilizationRatio = res.utilizationRatio,
                            viewMode = selectedViewMode,
                            modifier = Modifier.fillMaxSize(),
                            sectionType = res.sectionType.displayName,
                            radius = res.sectionType.rootRadius,
                            area = res.sectionType.area,
                            ix = res.sectionType.ix,
                            sx = res.sectionType.sx,
                            zx = res.sectionType.zx,
                            weightPerMeter = res.sectionType.weight,
                            boltDia = connParams.boltDia,
                            boltCount = connParams.boltCount,
                            boltGauge = connParams.boltGauge,
                            boltPitch = connParams.boltPitch,
                            endPlateThickness = endPlateThk,
                            hasStiffener = hasStiff,
                            weldSize = connParams.weldSize,
                            isColumn = selectedMemberType == SteelMemberType.COLUMN
                        )
                    }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // Invisible duplicate of the on-screen drawing, rendered at the selected view mode, so the
    // exported PDF shows exactly what the user is looking at. The ViewModel still falls back to
    // PdfDrawingGenerator.generateSteelDrawing when no capture is available.
    result?.let { res ->
        val connParams = com.civileg.app.utils.SteelConnectionDrawing.from(res.connectionDesign)
        ComposeDrawingCaptureUtil.DrawingCaptureArea(
            captureLayer = pdfCaptureLayer,
            widthPx = screenWidthPx,
            heightPx = screenHeightPx
        ) {
            Box(modifier = Modifier.background(Color(0xFF1E1E2C))) {
                ProfessionalSteelDrawing(
                    sectionName = res.sectionType.sectionName,
                    depth = res.sectionType.depth,
                    flangeWidth = res.sectionType.width,
                    webThickness = res.sectionType.webThickness,
                    flangeThickness = res.sectionType.flangeThickness,
                    memberLength = (length.toDoubleOrNull() ?: 3.0) * 1000.0,
                    isSafe = res.isSafe,
                    utilizationRatio = res.utilizationRatio,
                    viewMode = selectedViewMode,
                    modifier = Modifier.fillMaxSize(),
                    sectionType = res.sectionType.displayName,
                    radius = res.sectionType.rootRadius,
                    area = res.sectionType.area,
                    ix = res.sectionType.ix,
                    sx = res.sectionType.sx,
                    zx = res.sectionType.zx,
                    weightPerMeter = res.sectionType.weight,
                    boltDia = connParams.boltDia,
                    boltCount = connParams.boltCount,
                    boltGauge = connParams.boltGauge,
                    boltPitch = connParams.boltPitch,
                    // Same fixed values the on-screen drawing uses.
                    endPlateThickness = 12.0,
                    hasStiffener = false,
                    weldSize = connParams.weldSize,
                    isColumn = selectedMemberType == SteelMemberType.COLUMN
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
