package com.civileg.app.ui.compose.screens


import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.civileg.app.R
import com.civileg.app.ui.responsive.dimens
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import com.civileg.app.ui.theme.onColorFor
import com.civileg.app.utils.RebarCalculator
import com.civileg.app.utils.RebarCalculator.STANDARD_DIAMETERS
import com.civileg.app.utils.RebarCalculator.DesignCode
import com.civileg.app.utils.RebarCalculator.LapType
import com.civileg.app.utils.RebarCalculator.ElementType
import com.civileg.app.utils.RebarCalculator.weightPerMeter
import com.civileg.app.utils.RebarCalculator.barArea
import com.civileg.app.utils.RebarCalculator.formatWeight
import com.civileg.app.utils.RebarCalculator.formatLength
import com.civileg.app.viewmodel.RebarToolViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RebarToolScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: RebarToolViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
        }
    }
    val tabTitles = listOf(
        stringResource(R.string.rebar_tab_weight),
        stringResource(R.string.rebar_tab_dev),
        stringResource(R.string.rebar_tab_lap),
        stringResource(R.string.rebar_tab_schedule),
        stringResource(R.string.rebar_tab_crack)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.rebar_calculator_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = {
                            AppText(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> WeightCalculatorTab(viewModel)
                1 -> DevelopmentLengthTab(viewModel)
                2 -> LapSpliceTab(viewModel)
                3 -> RebarScheduleTab(viewModel)
                4 -> CrackWidthTab(viewModel)
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
                        "Calculating... / جاري الحساب...",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  TAB 0: WEIGHT CALCULATOR
// ══════════════════════════════════════════════════════════════
@Composable
private fun WeightCalculatorTab(viewModel: RebarToolViewModel) {
    var diameter by remember { mutableStateOf("16") }
    var length by remember { mutableStateOf("12.0") }
    var quantity by remember { mutableStateOf("10") }
    val result by viewModel.weightResult.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Bar diameter table
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                AppText(stringResource(R.string.rebar_std_dia), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    STANDARD_DIAMETERS.forEach { d ->
                        SuggestionChip(
                            label = { AppText("$d") },
                            onClick = { diameter = "$d" }
                        )
                    }
                }
            }
        }

        // Inputs
        Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(stringResource(R.string.rebar_input_params), fontWeight = FontWeight.Bold)
                InputField(stringResource(R.string.rebar_dia_label), diameter, { diameter = it })
                InputField(stringResource(R.string.rebar_bar_len_label), length, { length = it })
                InputField(stringResource(R.string.rebar_qty_label), quantity, { quantity = it }, KeyboardType.Number)

                AppButton(
                    label = stringResource(R.string.rebar_calc_button),
                    onClick = {
                        val d = diameter.toDoubleOrNull() ?: return@AppButton
                        val l = length.toDoubleOrNull() ?: return@AppButton
                        val q = quantity.toIntOrNull() ?: return@AppButton
                        viewModel.calculateWeight(d, l, q)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Results
        result?.let { r ->
            val context = androidx.compose.ui.platform.LocalContext.current

            AppButton(
                label = "تصدير تقرير PDF برو (3 قطاعات)",
                onClick = { viewModel.exportToPdf(context, barDiameter = r.diameter) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                icon = Icons.Default.PictureAsPdf,
                containerOverride = MaterialTheme.colorScheme.tertiary,
                contentOverride = onColorFor(MaterialTheme.colorScheme.tertiary),
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(R.string.results), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    ResultRow(stringResource(R.string.rebar_weight_m), "${formatWeight(r.weightPerMeter)} kg/m")
                    ResultRow(stringResource(R.string.rebar_total_weight_label), "${formatWeight(r.totalWeight)} kg")
                    ResultRow(stringResource(R.string.rebar_total_len), "${formatLength(r.totalLength)} m")
                    ResultRow(stringResource(R.string.rebar_qty_label), "${r.quantity} bars")
                    ResultRow(stringResource(R.string.rebar_bundle_wt), "${formatWeight(weightPerMeter(r.diameter) * 20)} kg")
                }
            }
        }

        // Full table
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                AppText(stringResource(R.string.rebar_ref_table), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AppText("Ø (mm)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    AppText("Wt (kg/m)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    AppText("Area (mm²)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()
                Column {
                    STANDARD_DIAMETERS.forEach { d ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AppText("$d", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            AppText("${"%.3f".format(weightPerMeter(d.toDouble()))}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            AppText("${"%.1f".format(barArea(d.toDouble()))}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  TAB 1: DEVELOPMENT LENGTH
// ══════════════════════════════════════════════════════════════
@Composable
private fun DevelopmentLengthTab(viewModel: RebarToolViewModel) {
    var diameter by remember { mutableStateOf("16") }
    var fy by remember { mutableStateOf("360") }
    var fcu by remember { mutableStateOf("25") }
    var isTopBar by remember { mutableStateOf(false) }
    var isConfined by remember { mutableStateOf(false) }
    var excessRatio by remember { mutableStateOf("1.0") }
    var code by remember { mutableStateOf("ECP 203") }
    val result by viewModel.devLengthResult.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(stringResource(R.string.rebar_dev_len_title), fontWeight = FontWeight.Bold)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code, onValueChange = {}, readOnly = true,
                        label = { AppText(stringResource(R.string.rebar_code_label)) }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownSelector(
                        options = listOf("ECP 203", "ACI 318", "SBC 304"), selected = code,
                        onSelect = { code = it }
                    )
                }

                InputField(stringResource(R.string.rebar_dia_label), diameter, { diameter = it })
                InputField(stringResource(R.string.rebar_fy_label), fy, { fy = it })
                InputField(stringResource(R.string.rebar_fcu_label), fcu, { fcu = it })
                InputField(stringResource(R.string.rebar_excess_ratio), excessRatio, { excessRatio = it })

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isTopBar, onCheckedChange = { isTopBar = it })
                    AppText(stringResource(R.string.rebar_top_bar))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isConfined, onCheckedChange = { isConfined = it })
                    AppText(stringResource(R.string.rebar_confined))
                }

                AppButton(
                    label = stringResource(R.string.rebar_calc_button),
                    onClick = {
                        val d = diameter.toDoubleOrNull() ?: return@AppButton
                        val f1 = fy.toDoubleOrNull() ?: return@AppButton
                        val f2 = fcu.toDoubleOrNull() ?: return@AppButton
                        val er = excessRatio.toDoubleOrNull() ?: return@AppButton
                        val c = when (code) {
                            "ECP 203" -> DesignCode.ECP_203
                            "SBC 304" -> DesignCode.SBC_304
                            else -> DesignCode.ACI_318
                        }
                        viewModel.calculateDevelopmentLength(d, f1, f2, isTopBar, isConfined, er, c)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        result?.let { r ->
            val context = androidx.compose.ui.platform.LocalContext.current

            AppButton(
                label = "تصدير تقرير PDF برو (3 قطاعات)",
                onClick = { viewModel.exportToPdf(context, barDiameter = diameter.toDoubleOrNull() ?: 16.0) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                icon = Icons.Default.PictureAsPdf,
                containerOverride = MaterialTheme.colorScheme.tertiary,
                contentOverride = onColorFor(MaterialTheme.colorScheme.tertiary),
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(R.string.rebar_dev_len_res), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    ResultRow(stringResource(R.string.rebar_code_label), r.code.name)
                    ResultRow(stringResource(R.string.rebar_basic_ld), "${formatLength(r.basicLd)} mm")
                    ResultRow(stringResource(R.string.rebar_modified_ld), "${formatLength(r.modifiedLd)} mm")
                    ResultRow(stringResource(R.string.rebar_top_mod), "×${r.topBarModifier}")
                    ResultRow(stringResource(R.string.rebar_conf_mod), "×${r.confinementModifier}")
                    ResultRow(stringResource(R.string.rebar_excess_mod), "×${"%.2f".format(r.excessRebarModifier)}")
                    Spacer(Modifier.height(4.dp))
                    r.notes.forEach { note ->
                        AppText(text = "• $note", fontSize = 12.sp, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  TAB 2: LAP SPLICE LENGTH
// ══════════════════════════════════════════════════════════════
@Composable
private fun LapSpliceTab(viewModel: RebarToolViewModel) {
    var diameter by remember { mutableStateOf("16") }
    var fy by remember { mutableStateOf("360") }
    var fcu by remember { mutableStateOf("25") }
    var lapType by remember { mutableStateOf("Tension") }
    var code by remember { mutableStateOf("ECP 203") }
    var spliceClass by remember { mutableStateOf("B") }
    var isTopBar by remember { mutableStateOf(false) }
    var isConfined by remember { mutableStateOf(false) }
    val result by viewModel.lapResult.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(stringResource(R.string.rebar_lap_splice_title), fontWeight = FontWeight.Bold)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code, onValueChange = {}, readOnly = true,
                        label = { AppText(stringResource(R.string.rebar_code_label)) }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownSelector(
                        options = listOf("ECP 203", "ACI 318", "SBC 304"), selected = code,
                        onSelect = { code = it }
                    )
                }

                InputField(stringResource(R.string.rebar_dia_label), diameter, { diameter = it })
                InputField(stringResource(R.string.rebar_fy_label), fy, { fy = it })
                InputField(stringResource(R.string.rebar_fcu_label), fcu, { fcu = it })

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(R.string.rebar_lap_type), modifier = Modifier.align(Alignment.CenterVertically))
                    listOf("Tension", "Compression").forEach { t ->
                        FilterChip(
                            selected = lapType == t, onClick = { lapType = t },
                            label = { AppText(t) }
                        )
                    }
                }

                if (lapType == "Tension") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(stringResource(R.string.rebar_class), modifier = Modifier.align(Alignment.CenterVertically))
                        listOf("A", "B").forEach { c ->
                            FilterChip(
                                selected = spliceClass == c, onClick = { spliceClass = c },
                                label = { AppText("Class $c") }
                            )
                        }
                    }
                    AppText("Class A: 1.0×Ld | Class B: 1.3×Ld", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isTopBar, onCheckedChange = { isTopBar = it })
                    AppText(stringResource(R.string.rebar_top_bar))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isConfined, onCheckedChange = { isConfined = it })
                    AppText(stringResource(R.string.rebar_confined))
                }

                AppButton(
                    label = stringResource(R.string.rebar_calc_button),
                    onClick = {
                        val d = diameter.toDoubleOrNull() ?: return@AppButton
                        val f1 = fy.toDoubleOrNull() ?: return@AppButton
                        val f2 = fcu.toDoubleOrNull() ?: return@AppButton
                        val lt = if (lapType == "Tension") LapType.TENSION else LapType.COMPRESSION
                        val c = when (code) {
                            "ECP 203" -> DesignCode.ECP_203
                            "SBC 304" -> DesignCode.SBC_304
                            else -> DesignCode.ACI_318
                        }
                        viewModel.calculateLapSplice(d, f1, f2, lt, c, isTopBar, isConfined, spliceClass)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        result?.let { r ->
            val context = androidx.compose.ui.platform.LocalContext.current

            AppButton(
                label = "تصدير تقرير PDF برو (3 قطاعات)",
                onClick = { viewModel.exportToPdf(context, barDiameter = diameter.toDoubleOrNull() ?: 16.0) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                icon = Icons.Default.PictureAsPdf,
                containerOverride = MaterialTheme.colorScheme.tertiary,
                contentOverride = onColorFor(MaterialTheme.colorScheme.tertiary),
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(R.string.rebar_lap_res), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    ResultRow(stringResource(R.string.rebar_code_label), r.code.name)
                    ResultRow(stringResource(R.string.rebar_lap_type), r.lapType.name)
                    ResultRow(stringResource(R.string.rebar_based_ld), "${formatLength(r.basicLd)} mm")
                    ResultRow(stringResource(R.string.rebar_excess_mod), "×${r.modifier}")
                    ResultRow(stringResource(R.string.rebar_lap_len), "${formatLength(r.lapLength)} mm", bold = true)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  TAB 3: REBAR SCHEDULE
// ══════════════════════════════════════════════════════════════
@Composable
private fun RebarScheduleTab(viewModel: RebarToolViewModel) {
    var elementType by remember { mutableStateOf("Beam") }
    val result by viewModel.scheduleResult.collectAsState()

    // Beam inputs
    var beamW by remember { mutableStateOf("250") }
    var beamD by remember { mutableStateOf("600") }
    var beamL by remember { mutableStateOf("5000") }
    var spans by remember { mutableStateOf("3") }
    var topDia by remember { mutableStateOf("16") }
    var topCnt by remember { mutableStateOf("2") }
    var beamBotDia by remember { mutableStateOf("20") }
    var beamBotCnt by remember { mutableStateOf("3") }
    var stirDia by remember { mutableStateOf("10") }
    var stirSp by remember { mutableStateOf("200") }
    var cover by remember { mutableStateOf("40") }

    // Slab inputs
    var slabL by remember { mutableStateOf("6.0") }
    var slabW by remember { mutableStateOf("4.0") }
    var slabT by remember { mutableStateOf("150") }
    var bsDia by remember { mutableStateOf("12") }
    var bsSp by remember { mutableStateOf("150") }
    var blDia by remember { mutableStateOf("10") }
    var blSp by remember { mutableStateOf("200") }
    var tsDia by remember { mutableStateOf("12") }
    var tsSp by remember { mutableStateOf("200") }

    // Column inputs
    var colW by remember { mutableStateOf("300") }
    var colD by remember { mutableStateOf("300") }
    var colH by remember { mutableStateOf("3.5") }
    var mainDia by remember { mutableStateOf("20") }
    var mainCnt by remember { mutableStateOf("8") }
    var tieDia by remember { mutableStateOf("10") }
    var tieSp by remember { mutableStateOf("200") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Element type selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Beam", "Slab", "Column").forEach { t ->
                FilterChip(
                    selected = elementType == t, onClick = { elementType = t },
                    label = { AppText(t) }
                )
            }
        }

        Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppText("${elementType} ${stringResource(R.string.rebar_schedule_inputs)}", fontWeight = FontWeight.Bold)

                when (elementType) {
                    "Beam" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField(stringResource(R.string.beam_width_hint), beamW, { beamW = it }, modifier = Modifier.weight(1f))
                            InputField(stringResource(R.string.beam_height_hint), beamD, { beamD = it }, modifier = Modifier.weight(1f))
                        }
                        InputField(stringResource(R.string.beam_length_hint), beamL, { beamL = it })
                        InputField("No. of Spans", spans, { spans = it }, KeyboardType.Number)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Top Ø (mm)", topDia, { topDia = it }, modifier = Modifier.weight(1f))
                            InputField("Top Count", topCnt, { topCnt = it }, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Bot Ø (mm)", beamBotDia, { beamBotDia = it }, modifier = Modifier.weight(1f))
                            InputField("Bot Count", beamBotCnt, { beamBotCnt = it }, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Stirrup Ø (mm)", stirDia, { stirDia = it }, modifier = Modifier.weight(1f))
                            InputField("Stirrup Sp. (mm)", stirSp, { stirSp = it }, modifier = Modifier.weight(1f))
                        }
                        InputField(stringResource(R.string.cover_label), cover, { cover = it })

                        AppButton(
                            label = stringResource(R.string.rebar_gen_schedule),
                            onClick = {
                                viewModel.generateBeamSchedule(
                                    beamW.toDoubleOrNull() ?: return@AppButton,
                                    beamD.toDoubleOrNull() ?: return@AppButton,
                                    beamL.toDoubleOrNull() ?: return@AppButton,
                                    spans.toIntOrNull() ?: return@AppButton,
                                    topDia.toIntOrNull() ?: return@AppButton,
                                    topCnt.toIntOrNull() ?: return@AppButton,
                                    beamBotDia.toIntOrNull() ?: return@AppButton,
                                    beamBotCnt.toIntOrNull() ?: return@AppButton,
                                    stirDia.toIntOrNull() ?: return@AppButton,
                                    stirSp.toDoubleOrNull() ?: return@AppButton,
                                    cover.toDoubleOrNull() ?: return@AppButton
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "Slab" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField(stringResource(R.string.span_x_label), slabL, { slabL = it }, modifier = Modifier.weight(1f))
                            InputField(stringResource(R.string.width_label), slabW, { slabW = it }, modifier = Modifier.weight(1f))
                        }
                        InputField(stringResource(R.string.thickness_label), slabT, { slabT = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Bot Short Ø", bsDia, { bsDia = it }, modifier = Modifier.weight(1f))
                            InputField("Bot Short Sp.", bsSp, { bsSp = it }, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Bot Long Ø", blDia, { blDia = it }, modifier = Modifier.weight(1f))
                            InputField("Bot Long Sp.", blSp, { blSp = it }, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Top Short Ø", tsDia, { tsDia = it }, modifier = Modifier.weight(1f))
                            InputField("Top Short Sp.", tsSp, { tsSp = it }, modifier = Modifier.weight(1f))
                        }
                        InputField(stringResource(R.string.cover_label), cover, { cover = it })

                        AppButton(
                            label = stringResource(R.string.rebar_gen_schedule),
                            onClick = {
                                viewModel.generateSlabSchedule(
                                    slabL.toDoubleOrNull() ?: return@AppButton,
                                    slabW.toDoubleOrNull() ?: return@AppButton,
                                    slabT.toDoubleOrNull() ?: return@AppButton,
                                    bsDia.toIntOrNull() ?: return@AppButton,
                                    bsSp.toDoubleOrNull() ?: return@AppButton,
                                    blDia.toIntOrNull() ?: return@AppButton,
                                    blSp.toDoubleOrNull() ?: return@AppButton,
                                    tsDia.toIntOrNull() ?: return@AppButton,
                                    tsSp.toDoubleOrNull() ?: return@AppButton,
                                    cover.toDoubleOrNull() ?: return@AppButton
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "Column" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField(stringResource(R.string.column_width_hint), colW, { colW = it }, modifier = Modifier.weight(1f))
                            InputField(stringResource(R.string.column_height_hint), colD, { colD = it }, modifier = Modifier.weight(1f))
                        }
                        InputField(stringResource(R.string.height_m), colH, { colH = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Main Ø (mm)", mainDia, { mainDia = it }, modifier = Modifier.weight(1f))
                            InputField("Main Count", mainCnt, { mainCnt = it }, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Tie Ø (mm)", tieDia, { tieDia = it }, modifier = Modifier.weight(1f))
                            InputField("Tie Sp. (mm)", tieSp, { tieSp = it }, modifier = Modifier.weight(1f))
                        }
                        InputField(stringResource(R.string.cover_label), cover, { cover = it })

                        AppButton(
                            label = stringResource(R.string.rebar_gen_schedule),
                            onClick = {
                                viewModel.generateColumnSchedule(
                                    colW.toDoubleOrNull() ?: return@AppButton,
                                    colD.toDoubleOrNull() ?: return@AppButton,
                                    colH.toDoubleOrNull() ?: return@AppButton,
                                    mainDia.toIntOrNull() ?: return@AppButton,
                                    mainCnt.toIntOrNull() ?: return@AppButton,
                                    tieDia.toIntOrNull() ?: return@AppButton,
                                    tieSp.toDoubleOrNull() ?: return@AppButton,
                                    cover.toDoubleOrNull() ?: return@AppButton
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Schedule result
        result?.let { r ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AppText("${r.elementType} Rebar Schedule", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        AppText("Total: ${"%.1f".format(r.totalWeight)} kg", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    AppText("Total bars: ${r.totalBars}", fontSize = 12.sp)
                    HorizontalDivider()
                    // Table header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AppText(stringResource(R.string.rebar_mark_label), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                        AppText("Ø", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                        AppText("No.", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                        AppText("L (m)", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                        AppText("Wt (kg)", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                    }
                    HorizontalDivider()
                    r.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AppText(item.mark, fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                            AppText("${item.diameter}", fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                            AppText("${item.numberOfBars}", fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                            AppText("${"%.2f".format(item.barLength)}", fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                            AppText("${"%.2f".format(item.totalWeight)}", fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                        }
                        AppText(item.description, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  TAB 4: CRACK WIDTH
// ══════════════════════════════════════════════════════════════
@Composable
private fun CrackWidthTab(viewModel: RebarToolViewModel) {
    var steelStress by remember { mutableStateOf("200") }
    var barDiameter by remember { mutableStateOf("16") }
    var barSpacing by remember { mutableStateOf("150") }
    var coverToCenter by remember { mutableStateOf("50") }
    var limitingWidth by remember { mutableStateOf("0.3") }
    val result by viewModel.crackResult.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(stringResource(R.string.rebar_crack_title), fontWeight = FontWeight.Bold)
                AppText("wk = 3.4 × εm × acr", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))

                InputField(stringResource(R.string.rebar_steel_stress), steelStress, { steelStress = it })
                InputField(stringResource(R.string.rebar_dia_label), barDiameter, { barDiameter = it })
                InputField(stringResource(R.string.rebar_bar_spacing_label), barSpacing, { barSpacing = it })
                InputField(stringResource(R.string.rebar_cover_center), coverToCenter, { coverToCenter = it })

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(R.string.rebar_limiting_wk), modifier = Modifier.align(Alignment.CenterVertically))
                    listOf("0.2", "0.3", "0.4").forEach { w ->
                        FilterChip(
                            selected = limitingWidth == w, onClick = { limitingWidth = w },
                            label = { AppText("$w mm") }
                        )
                    }
                }

                AppButton(
                    label = stringResource(R.string.rebar_calc_crack),
                    onClick = {
                        viewModel.calculateCrackWidth(
                            steelStress.toDoubleOrNull() ?: return@AppButton,
                            barDiameter.toDoubleOrNull() ?: return@AppButton,
                            barSpacing.toDoubleOrNull() ?: return@AppButton,
                            coverToCenter.toDoubleOrNull() ?: return@AppButton,
                            limitingWidth.toDoubleOrNull() ?: return@AppButton
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        result?.let { r ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (r.isAcceptable)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (r.isAcceptable) Icons.Default.CheckCircle else Icons.Default.Warning,
                            null,
                            tint = if (r.isAcceptable) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                        )
                        Spacer(Modifier.width(8.dp))
                        AppText(
                            "Crack Width: ${"%.3f".format(r.crackWidth)} mm",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (r.isAcceptable) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                        )
                    }
                    ResultRow(stringResource(R.string.status), if (r.isAcceptable) stringResource(R.string.pile_ok) else "EXCEEDS LIMIT")
                    ResultRow(stringResource(R.string.rebar_limiting_wk), "${r.limitingCrackWidth} mm")
                    ResultRow(stringResource(R.string.rebar_mean_strain), "${"%.6f".format(r.meanStrain)}")
                    ResultRow(stringResource(R.string.rebar_steel_stress), "${"%.1f".format(r.steelStress)} N/mm²")
                    ResultRow(stringResource(R.string.rebar_bar_spacing_label), "${"%.0f".format(r.barSpacing)} mm")
                    ResultRow(stringResource(R.string.rebar_cover_center), "${"%.0f".format(r.coverToBarCenter)} mm")
                    Spacer(Modifier.height(4.dp))
                    AppText(stringResource(R.string.notes), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    r.notes.forEach { note ->
                        AppText(text = "• $note", fontSize = 11.sp,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════
@Composable
private fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { AppText(label, fontSize = 13.sp) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun ResultRow(label: String, value: String, bold: Boolean = false) {
    com.civileg.app.ui.components.ContentDirection(label) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AppText(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppText(
            value, fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
}

@Composable
private fun DropdownSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AppButton(
            label = selected,
            onClick = { expanded = true },
            style = AppButtonStyle.Outline,
            fillMaxWidth = false
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { AppText(opt) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}
