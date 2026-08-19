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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
    val tabTitles = listOf("Weight", "Dev. Length", "Lap Length", "Schedule", "Crack Width")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rebar Calculator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            Text(
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
                Text("Standard Bar Diameters", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    STANDARD_DIAMETERS.forEach { d ->
                        SuggestionChip(
                            label = { Text("$d") },
                            onClick = { diameter = "$d" }
                        )
                    }
                }
            }
        }

        // Inputs
        Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Input Parameters", fontWeight = FontWeight.Bold)
                InputField("Diameter (mm)", diameter, { diameter = it })
                InputField("Bar Length (m)", length, { length = it })
                InputField("Quantity", quantity, { quantity = it }, KeyboardType.Number)

                Button(
                    onClick = {
                        val d = diameter.toDoubleOrNull() ?: return@Button
                        val l = length.toDoubleOrNull() ?: return@Button
                        val q = quantity.toIntOrNull() ?: return@Button
                        viewModel.calculateWeight(d, l, q)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Calculate")
                }
            }
        }

        // Results
        result?.let { r ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Results", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    ResultRow("Weight/m", "${formatWeight(r.weightPerMeter)} kg/m")
                    ResultRow("Total Weight", "${formatWeight(r.totalWeight)} kg")
                    ResultRow("Total Length", "${formatLength(r.totalLength)} m")
                    ResultRow("Quantity", "${r.quantity} bars")
                    ResultRow("Bundle Wt (20m)", "${formatWeight(weightPerMeter(r.diameter) * 20)} kg")
                }
            }
        }

        // Full table
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Bar Reference Table", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ø (mm)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Wt (kg/m)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Area (mm²)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()
                Column {
                    STANDARD_DIAMETERS.forEach { d ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("$d", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("${"%.3f".format(weightPerMeter(d.toDouble()))}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("${"%.1f".format(barArea(d.toDouble()))}", fontSize = 12.sp, modifier = Modifier.weight(1f))
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
                Text("Development Length", fontWeight = FontWeight.Bold)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code, onValueChange = {}, readOnly = true,
                        label = { Text("Code") }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownSelector(
                        options = listOf("ECP 203", "ACI 318"), selected = code,
                        onSelect = { code = it }
                    )
                }

                InputField("Bar Diameter (mm)", diameter, { diameter = it })
                InputField("fy (N/mm²)", fy, { fy = it })
                InputField("fcu / f'c (N/mm²)", fcu, { fcu = it })
                InputField("Excess Reinf. Ratio (As/As,req)", excessRatio, { excessRatio = it })

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isTopBar, onCheckedChange = { isTopBar = it })
                    Text("Top bar (fresh concrete > 300mm below)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isConfined, onCheckedChange = { isConfined = it })
                    Text("Confined (tied/stirrups)")
                }

                Button(
                    onClick = {
                        val d = diameter.toDoubleOrNull() ?: return@Button
                        val f1 = fy.toDoubleOrNull() ?: return@Button
                        val f2 = fcu.toDoubleOrNull() ?: return@Button
                        val er = excessRatio.toDoubleOrNull() ?: return@Button
                        val c = if (code == "ECP 203") DesignCode.ECP_203 else DesignCode.ACI_318
                        viewModel.calculateDevelopmentLength(d, f1, f2, isTopBar, isConfined, er, c)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Calculate")
                }
            }
        }

        result?.let { r ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Development Length Result", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    ResultRow("Code", r.code.name)
                    ResultRow("Basic Ld", "${formatLength(r.basicLd)} mm")
                    ResultRow("Modified Ld", "${formatLength(r.modifiedLd)} mm")
                    ResultRow("Top Bar Modifier", "×${r.topBarModifier}")
                    ResultRow("Confinement Modifier", "×${r.confinementModifier}")
                    ResultRow("Excess Reinf. Modifier", "×${"%.2f".format(r.excessRebarModifier)}")
                    Spacer(Modifier.height(4.dp))
                    r.notes.forEach { note ->
                        Text(text = "• $note", fontSize = 12.sp, style = MaterialTheme.typography.bodySmall)
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
                Text("Lap Splice Length", fontWeight = FontWeight.Bold)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code, onValueChange = {}, readOnly = true,
                        label = { Text("Code") }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownSelector(
                        options = listOf("ECP 203", "ACI 318"), selected = code,
                        onSelect = { code = it }
                    )
                }

                InputField("Bar Diameter (mm)", diameter, { diameter = it })
                InputField("fy (N/mm²)", fy, { fy = it })
                InputField("fcu / f'c (N/mm²)", fcu, { fcu = it })

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lap Type:", modifier = Modifier.align(Alignment.CenterVertically))
                    listOf("Tension", "Compression").forEach { t ->
                        FilterChip(
                            selected = lapType == t, onClick = { lapType = t },
                            label = { Text(t) }
                        )
                    }
                }

                if (lapType == "Tension") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Class:", modifier = Modifier.align(Alignment.CenterVertically))
                        listOf("A", "B").forEach { c ->
                            FilterChip(
                                selected = spliceClass == c, onClick = { spliceClass = c },
                                label = { Text("Class $c") }
                            )
                        }
                    }
                    Text("Class A: 1.0×Ld | Class B: 1.3×Ld", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isTopBar, onCheckedChange = { isTopBar = it })
                    Text("Top bar")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isConfined, onCheckedChange = { isConfined = it })
                    Text("Confined")
                }

                Button(
                    onClick = {
                        val d = diameter.toDoubleOrNull() ?: return@Button
                        val f1 = fy.toDoubleOrNull() ?: return@Button
                        val f2 = fcu.toDoubleOrNull() ?: return@Button
                        val lt = if (lapType == "Tension") LapType.TENSION else LapType.COMPRESSION
                        val c = if (code == "ECP 203") DesignCode.ECP_203 else DesignCode.ACI_318
                        viewModel.calculateLapSplice(d, f1, f2, lt, c, isTopBar, isConfined, spliceClass)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Calculate")
                }
            }
        }

        result?.let { r ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lap Splice Result", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    ResultRow("Code", r.code.name)
                    ResultRow("Lap Type", r.lapType.name)
                    ResultRow("Based on Ld", "${formatLength(r.basicLd)} mm")
                    ResultRow("Modifier", "×${r.modifier}")
                    ResultRow("Lap Length", "${formatLength(r.lapLength)} mm", bold = true)
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
    var botDia by remember { mutableStateOf("20") }
    var botCnt by remember { mutableStateOf("3") }
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
                    label = { Text(t) }
                )
            }
        }

        Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${elementType} Schedule Inputs", fontWeight = FontWeight.Bold)

                when (elementType) {
                    "Beam" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Width (mm)", beamW, { beamW = it }, modifier = Modifier.weight(1f))
                            InputField("Depth (mm)", beamD, { beamD = it }, modifier = Modifier.weight(1f))
                        }
                        InputField("Span Length (mm)", beamL, { beamL = it })
                        InputField("No. of Spans", spans, { spans = it }, KeyboardType.Number)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Top Ø (mm)", topDia, { topDia = it }, modifier = Modifier.weight(1f))
                            InputField("Top Count", topCnt, { topCnt = it }, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Bot Ø (mm)", botDia, { botDia = it }, modifier = Modifier.weight(1f))
                            InputField("Bot Count", botCnt, { botCnt = it }, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Stirrup Ø (mm)", stirDia, { stirDia = it }, modifier = Modifier.weight(1f))
                            InputField("Stirrup Sp. (mm)", stirSp, { stirSp = it }, modifier = Modifier.weight(1f))
                        }
                        InputField("Cover (mm)", cover, { cover = it })

                        Button(
                            onClick = {
                                viewModel.generateBeamSchedule(
                                    beamW.toDoubleOrNull() ?: return@Button,
                                    beamD.toDoubleOrNull() ?: return@Button,
                                    beamL.toDoubleOrNull() ?: return@Button,
                                    spans.toIntOrNull() ?: return@Button,
                                    topDia.toIntOrNull() ?: return@Button,
                                    topCnt.toIntOrNull() ?: return@Button,
                                    botDia.toIntOrNull() ?: return@Button,
                                    botCnt.toIntOrNull() ?: return@Button,
                                    stirDia.toIntOrNull() ?: return@Button,
                                    stirSp.toDoubleOrNull() ?: return@Button,
                                    cover.toDoubleOrNull() ?: return@Button
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Generate Schedule") }
                    }
                    "Slab" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Length (m)", slabL, { slabL = it }, modifier = Modifier.weight(1f))
                            InputField("Width (m)", slabW, { slabW = it }, modifier = Modifier.weight(1f))
                        }
                        InputField("Thickness (mm)", slabT, { slabT = it })
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
                        InputField("Cover (mm)", cover, { cover = it })

                        Button(
                            onClick = {
                                viewModel.generateSlabSchedule(
                                    slabL.toDoubleOrNull() ?: return@Button,
                                    slabW.toDoubleOrNull() ?: return@Button,
                                    slabT.toDoubleOrNull() ?: return@Button,
                                    bsDia.toIntOrNull() ?: return@Button,
                                    bsSp.toDoubleOrNull() ?: return@Button,
                                    blDia.toIntOrNull() ?: return@Button,
                                    blSp.toDoubleOrNull() ?: return@Button,
                                    tsDia.toIntOrNull() ?: return@Button,
                                    tsSp.toDoubleOrNull() ?: return@Button,
                                    cover.toDoubleOrNull() ?: return@Button
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Generate Schedule") }
                    }
                    "Column" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Width (mm)", colW, { colW = it }, modifier = Modifier.weight(1f))
                            InputField("Depth (mm)", colD, { colD = it }, modifier = Modifier.weight(1f))
                        }
                        InputField("Height (m)", colH, { colH = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Main Ø (mm)", mainDia, { mainDia = it }, modifier = Modifier.weight(1f))
                            InputField("Main Count", mainCnt, { mainCnt = it }, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InputField("Tie Ø (mm)", tieDia, { tieDia = it }, modifier = Modifier.weight(1f))
                            InputField("Tie Sp. (mm)", tieSp, { tieSp = it }, modifier = Modifier.weight(1f))
                        }
                        InputField("Cover (mm)", cover, { cover = it })

                        Button(
                            onClick = {
                                viewModel.generateColumnSchedule(
                                    colW.toDoubleOrNull() ?: return@Button,
                                    colD.toDoubleOrNull() ?: return@Button,
                                    colH.toDoubleOrNull() ?: return@Button,
                                    mainDia.toIntOrNull() ?: return@Button,
                                    mainCnt.toIntOrNull() ?: return@Button,
                                    tieDia.toIntOrNull() ?: return@Button,
                                    tieSp.toDoubleOrNull() ?: return@Button,
                                    cover.toDoubleOrNull() ?: return@Button
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Generate Schedule") }
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
                        Text("${r.elementType} Rebar Schedule", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Total: ${"%.1f".format(r.totalWeight)} kg", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Total bars: ${r.totalBars}", fontSize = 12.sp)
                    HorizontalDivider()
                    // Table header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Mark", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                        Text("Ø", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                        Text("No.", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                        Text("L (m)", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                        Text("Wt (kg)", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                    }
                    HorizontalDivider()
                    r.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.mark, fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                            Text("${item.diameter}", fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                            Text("${item.numberOfBars}", fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                            Text("${"%.2f".format(item.barLength)}", fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                            Text("${"%.2f".format(item.totalWeight)}", fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                        }
                        Text(item.description, fontSize = 10.sp,
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
                Text("Crack Width Calculation", fontWeight = FontWeight.Bold)
                Text("wk = 3.4 × εm × acr", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))

                InputField("Steel Stress fs (N/mm²)", steelStress, { steelStress = it })
                InputField("Bar Diameter (mm)", barDiameter, { barDiameter = it })
                InputField("Bar Spacing c/c (mm)", barSpacing, { barSpacing = it })
                InputField("Cover to Bar Center (mm)", coverToCenter, { coverToCenter = it })

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Limiting wk:", modifier = Modifier.align(Alignment.CenterVertically))
                    listOf("0.2", "0.3", "0.4").forEach { w ->
                        FilterChip(
                            selected = limitingWidth == w, onClick = { limitingWidth = w },
                            label = { Text("$w mm") }
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.calculateCrackWidth(
                            steelStress.toDoubleOrNull() ?: return@Button,
                            barDiameter.toDoubleOrNull() ?: return@Button,
                            barSpacing.toDoubleOrNull() ?: return@Button,
                            coverToCenter.toDoubleOrNull() ?: return@Button,
                            limitingWidth.toDoubleOrNull() ?: return@Button
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Calculate Crack Width")
                }
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
                        Text(
                            "Crack Width: ${"%.3f".format(r.crackWidth)} mm",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (r.isAcceptable) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                        )
                    }
                    ResultRow("Status", if (r.isAcceptable) "ACCEPTABLE" else "EXCEEDS LIMIT")
                    ResultRow("Limiting Width", "${r.limitingCrackWidth} mm")
                    ResultRow("Mean Strain εm", "${"%.6f".format(r.meanStrain)}")
                    ResultRow("Steel Stress", "${"%.1f".format(r.steelStress)} N/mm²")
                    ResultRow("Bar Spacing", "${"%.0f".format(r.barSpacing)} mm")
                    ResultRow("Cover to Bar Center", "${"%.0f".format(r.coverToBarCenter)} mm")
                    Spacer(Modifier.height(4.dp))
                    Text("Calculation Notes:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    r.notes.forEach { note ->
                        Text(text = "• $note", fontSize = 11.sp,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  SHARED COMPONENTS
// ══════════════════════════════════════════════════════════════
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
        label = { Text(label, fontSize = 13.sp) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun ResultRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
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
        OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(12.dp)) {
            Text(selected, fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}
