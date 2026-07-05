package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import com.civileg.app.viewmodel.ProjectViewModel
import androidx.compose.material3.rememberModalBottomSheetState
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfExportHelper
import com.civileg.app.viewmodel.SlabViewModel

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

    var selectedType by remember { mutableStateOf(CalculatorEngine.SlabType.SOLID) }
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }
    var expandedType by remember { mutableStateOf(false) }
    var expandedCode by remember { mutableStateOf(false) }
    
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)
    val projects by projectViewModel.allProjects.observeAsState(emptyList())

    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    var designName by remember { mutableStateOf("بلاطة سقف الدور") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم البلاطات (Slabs) Pro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (result != null) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "Save to Project", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                            label = { Text("نوع البلاطة") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                            CalculatorEngine.SlabType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.displayName) },
                                    onClick = {
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
                            label = { Text("الكود") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCode) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(expanded = expandedCode, onDismissRequest = { expandedCode = false }) {
                            CalculatorEngine.DesignCode.values().forEach { code ->
                                DropdownMenuItem(
                                    text = { Text(code.displayName) },
                                    onClick = {
                                        selectedCode = code
                                        expandedCode = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader("📐 أبعاد البلاطة والتحميل", R.drawable.ic_slab) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SlabInputField(shortSpan, "Lx (m)", { shortSpan = it }, Modifier.weight(1f))
                    SlabInputField(longSpan, "Ly (m)", { longSpan = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SlabInputField(deadLoad, "DL (kN/m²)", { deadLoad = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    SlabInputField(liveLoad, "LL (kN/m²)", { liveLoad = it }, Modifier.weight(1f))
                }
            }
            
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SlabInputField(thickness, "السماكة (mm)", { thickness = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    SlabInputField(preferredDiameter, "قطر السيخ (mm)", { preferredDiameter = it }, Modifier.weight(1f))
                }
            }

            if (selectedType == CalculatorEngine.SlabType.FLAT) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SlabInputField(columnSize, "عرض العمود (mm)", { columnSize = it }, Modifier.weight(1f))
                        SlabInputField(dropPanelThickness, "سمك السقوط (mm)", { dropPanelThickness = it }, Modifier.weight(1f))
                    }
                }
            }

            if (selectedType == CalculatorEngine.SlabType.POST_TENSION) {
                item {
                    SlabInputField(prestressForce, "قوة الضغط P (kN)", { prestressForce = it }, Modifier.fillMaxWidth())
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.calculateSlab(
                            spanX = shortSpan.toDoubleOrNull() ?: 4.0,
                            spanY = longSpan.toDoubleOrNull() ?: 5.0,
                            deadLoad = deadLoad.toDoubleOrNull() ?: 2.5,
                            liveLoad = liveLoad.toDoubleOrNull() ?: 3.0,
                            fcu = fcu.toDoubleOrNull() ?: 25.0,
                            fy = fy.toDoubleOrNull() ?: 360.0,
                            thickness = thickness.toDoubleOrNull() ?: 150.0,
                            preferredDiameter = preferredDiameter.toIntOrNull() ?: 12,
                            type = selectedType,
                            code = selectedCode,
                            prestressForce = prestressForce.toDoubleOrNull() ?: 0.0,
                            dropPanelThickness = dropPanelThickness.toDoubleOrNull() ?: 0.0,
                            columnSize = columnSize.toDoubleOrNull() ?: 400.0
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Calculate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تحليل وتصميم")
                    }
                }
            }

            result?.let { res ->
                item { SectionHeader("📊 نتائج التصميم", R.drawable.ic_calculator) }
                
                item { SlabResultCard(res) }

                item {
                    Text("📝 المعادلات (بدون نتائج)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    SlabFormulasCard()
                }

                item {
                    Text("🎨 مخطط التحليل والتسليح", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    SlabLayoutDrawing(lx = shortSpan.toDoubleOrNull() ?: 4.0, ly = longSpan.toDoubleOrNull() ?: 5.0, res = res)
                }
                
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.exportToPdf(context) { file ->
                                    if (file == null) {
                                        // Handle error if needed
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isExporting
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("تقرير PDF")
                            }
                        }

                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("حفظ في مشروع")
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("حفظ التصميم في مشروع") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = designName,
                        onValueChange = { designName = it },
                        label = { Text("اسم العنصر (مثلاً: سقف المتكرر)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text("اختر المشروع:", style = MaterialTheme.typography.labelMedium)
                    if (projects.isEmpty()) {
                        Text("لا توجد مشاريع حالية. سيتم إنشاء مشروع افتراضي.", color = Color.Gray, fontSize = 12.sp)
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
                                Text(project.name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pId = if (selectedProjectId == -1L) 1L else selectedProjectId
                    result?.let { viewModel.saveSlab(pId, designName, it) }
                    showSaveDialog = false
                }) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("إلغاء") }
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
                    Text(
                        "المستشار الإنشائي: نسبة الاستخدام",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (res.isSafe) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (res.isSafe) Color(0xFF2E7D32) else Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (res.isSafe) "البلاطة آمنة إنشائياً" else "البلاطة غير آمنة!",
                            fontWeight = FontWeight.Bold,
                            color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red
                        )
                    }
                }

                Box(contentAlignment = Alignment.Center) {
                    val animatedRatio by animateFloatAsState(
                        targetValue = res.utilizationRatio.toFloat(),
                        animationSpec = tween(1000), label = ""
                    )
                    CircularProgressIndicator(
                        progress = { animatedRatio },
                        modifier = Modifier.size(54.dp),
                        strokeWidth = 5.dp,
                        color = utilizationColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        "${(res.utilizationRatio * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ResultItem("سمك البلاطة", "${res.thickness} mm")
                ResultItem("عزم Mx", "${"%.1f".format(res.momentX)} kN.m")
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text("الحديد الرئيسي (Lx):", style = MaterialTheme.typography.labelMedium)
            Text(res.reinforcementMain.barString, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            
            Text("الحديد الثانوي (Ly):", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            Text(res.reinforcementSecondary.barString, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold)
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
private fun SlabLayoutDrawing(lx: Double, ly: Double, res: CalculatorEngine.SlabResult) {
    Card(modifier = Modifier.fillMaxWidth().height(300.dp), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().background(Color.White)) {
            val scale = minOf((size.width - 120f)/lx, (size.height - 120f)/ly).toFloat()
            val w = (lx * scale).toFloat()
            val h = (ly * scale).toFloat()
            val left = (size.width - w) / 2
            val top = (size.height - h) / 2
            
            // Slab Boundary
            drawRect(Color(0xFFF0F0F0), Offset(left, top), Size(w, h))
            
            // Draw Drop Panel if Flat Slab
            if (res.type == CalculatorEngine.SlabType.FLAT) {
                val panelSize = minOf(w, h) * 0.35f
                drawRect(
                    Color.LightGray.copy(alpha = 0.5f),
                    Offset(left + w/2 - panelSize/2, top + h/2 - panelSize/2),
                    Size(panelSize, panelSize)
                )
                drawRect(
                    Color.Gray,
                    Offset(left + w/2 - panelSize/2, top + h/2 - panelSize/2),
                    Size(panelSize, panelSize),
                    style = Stroke(2f)
                )
                
                // Draw Column
                val colSize = ((w / (lx * 1000f)) * 400f).toFloat() // Scale 400mm column
                drawRect(
                    Color.DarkGray,
                    Offset((left + w/2 - colSize/2).toFloat(), (top + h/2 - colSize/2).toFloat()),
                    Size(colSize, colSize)
                )
            }

            // Draw PT Tendons if PT Slab
            if (res.type == CalculatorEngine.SlabType.POST_TENSION) {
                for (i in 1..4) {
                    val y = top + i * (h / 5)
                    drawLine(Color(0xFFFFA000), Offset(left, y), Offset(left + w, y), strokeWidth = 4f)
                    // Anchor points
                    drawCircle(Color.Black, 6f, Offset(left, y))
                    drawCircle(Color.Black, 6f, Offset(left + w, y))
                }
            }

            drawRect(Color.DarkGray, Offset(left, top), Size(w, h), style = Stroke(4f))
            
            // Draw Main Reinforcement Bars (X-Direction)
            for (i in 1..8) {
                val y = top + i * (h / 9)
                drawLine(Color.Blue.copy(alpha = 0.5f), Offset(left + 20f, y), Offset(left + w - 20f, y), strokeWidth = 2f)
            }
            
            // Draw Secondary Reinforcement Bars (Y-Direction)
            for (i in 1..6) {
                val x = left + i * (w / 7)
                drawLine(Color.Red.copy(alpha = 0.5f), Offset(x, top + 20f), Offset(x, top + h - 20f), strokeWidth = 2f)
            }
            
            // Analysis Labels
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply { 
                    color = android.graphics.Color.BLACK
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText("Lx = ${lx}m (Main: ${res.reinforcementMain.barString})", size.width/2, top - 20f, paint)
                save(); rotate(-90f, left - 35f, size.height/2); drawText("Ly = ${ly}m (Sec: ${res.reinforcementSecondary.barString})", left - 35f, size.height/2, paint); restore()
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SlabInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun FormulaItem(formula: String) {
    Text(
        text = formula,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.secondary
    )
}
