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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.app.db.Project
import kotlin.math.pow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
import com.civileg.app.viewmodel.BeamViewModel
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeamScreen(
    viewModel: BeamViewModel = hiltViewModel(),
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)
    val error by viewModel.error.observeAsState()
    val projects by projectViewModel.allProjects.observeAsState(emptyList())

    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    var designName by remember { mutableStateOf("كمرة B1") }
    
    var width by remember { mutableStateOf("250") }
    var height by remember { mutableStateOf("600") }
    var span by remember { mutableStateOf("5.0") }
    var deadLoad by remember { mutableStateOf("15.0") }
    var liveLoad by remember { mutableStateOf("10.0") }
    var fcu by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("360") }
    var barDiameter by remember { mutableStateOf("16") }
    var numBars by remember { mutableStateOf("4") }
    var selectedSupport by remember { mutableStateOf(CalculatorEngine.SupportType.HINGED_HINGED) }
    var expandedSupport by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم الكمرات الخرسانية Pro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item { SectionHeader("📐 أبعاد الكمرة ونوع الارتكاز", R.drawable.ic_beam) }

            item {
                ExposedDropdownMenuBox(
                    expanded = expandedSupport,
                    onExpandedChange = { expandedSupport = !expandedSupport },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedSupport.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("نوع نقاط الارتكاز") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSupport) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expandedSupport, onDismissRequest = { expandedSupport = false }) {
                        CalculatorEngine.SupportType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selectedSupport = type
                                    expandedSupport = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BeamInputField(width, "العرض b (mm)", { width = it }, Modifier.weight(1f))
                    BeamInputField(height, "العمق h (mm)", { height = it }, Modifier.weight(1f))
                }
            }

            item {
                BeamInputField(span, "الطول الفعال L (m)", { span = it }, Modifier.fillMaxWidth())
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BeamInputField(deadLoad, "Dead Load (kN/m)", { deadLoad = it }, Modifier.weight(1f))
                    BeamInputField(liveLoad, "Live Load (kN/m)", { liveLoad = it }, Modifier.weight(1f))
                }
            }

            item {
                Text("اختيار حديد التسليح (لتقييم التوفير)", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BeamInputField(numBars, "عدد الأسياخ", { numBars = it }, Modifier.weight(1f))
                    BeamInputField(barDiameter, "القطر Ø (mm)", { barDiameter = it }, Modifier.weight(1f))
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.calculateBeamPro(
                            width.toDoubleOrNull() ?: 250.0,
                            height.toDoubleOrNull() ?: 600.0,
                            span.toDoubleOrNull() ?: 5.0,
                            deadLoad.toDoubleOrNull() ?: 0.0,
                            liveLoad.toDoubleOrNull() ?: 0.0,
                            fcu.toDoubleOrNull() ?: 25.0,
                            fy.toDoubleOrNull() ?: 360.0,
                            barDiameter.toIntOrNull() ?: 16,
                            CalculatorEngine.DesignCode.EGYPTIAN,
                            selectedSupport
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
                        Text("تحليل وتصميم المقطع")
                    }
                }
            }

            result?.let { res ->
                item { SectionHeader("📊 نتائج التحليل والإقتصاد", R.drawable.ic_calculator) }
                
                item {
                    val ecoColor = when {
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (res.isSafe) Icons.Default.Verified 
                                            else Icons.Default.Dangerous, 
                                            contentDescription = null, 
                                            tint = ecoColor
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (res.utilizationRatio > 1.0) "المقطع غير آمن إنشائياً! ❌"
                                            else if (res.utilizationRatio > 0.9) "تحميل عالي (حذر) ⚠️"
                                            else if (res.utilizationRatio > 0.4) "تصميم مثالي واقتصادي ✅"
                                            else "القطاع كبير (غير اقتصادي) 🔵",
                                            fontWeight = FontWeight.Bold,
                                            color = ecoColor
                                        )
                                    }
                                    Text(
                                        "المستشار الإنشائي: نسبة الاستخدام",
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
                                        color = ecoColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    Text(
                                        "${(res.utilizationRatio * 100).toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.exportToPdf(context) { file ->
                                    if (file == null) {
                                        // Handle error
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            enabled = !isExporting
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تقرير PDF")
                            }
                        }

                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حفظ")
                        }
                    }
                }

                item {
                    Text("📉 مخطط العزوم (B.M.D)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    BeamDiagramCanvas(res, modifier = Modifier.fillMaxWidth().height(150.dp))
                }

                item {
                    Text("📝 المعادلات الهندسية (بدون نتائج)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    BeamFormulasCard()
                }

                item {
                    Text("🎨 رسم المقطع العرضي والتسليح", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    BeamSectionDrawing(res, modifier = Modifier.fillMaxWidth().height(280.dp))
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
                        label = { Text("اسم الكمرة (مثلاً: B1)") },
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
                    result?.let { viewModel.saveBeam(pId, designName, it) }
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
private fun BeamResultCard(result: CalculatorEngine.BeamResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("النتائج النهائية:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            ResultRow("التسليح السفلي", result.reinforcementBottom.barString)
            ResultRow("التسليح العلوي", result.reinforcementTop.barString)
            ResultRow("الكانات", result.stirrups.description)
            ResultRow("أقصى عزم", "${"%.1f".format(result.appliedMoment)} kN.m")
            ResultRow("أقصى قص", "${"%.1f".format(result.appliedShear)} kN")
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            result.safetyChecks.forEach { check ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(check.name, fontSize = 12.sp)
                    Text(if (check.isSafe) "آمن ✅" else "غير آمن ❌", color = if (check.isSafe) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BeamFormulasCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            FormulaItem("1. Mu = (w_ult * L²) / Factor (Structural Analysis)")
            FormulaItem("2. d = h - Concrete Cover")
            FormulaItem("3. R = Mu / (fcu/gamma_c * b * d²)")
            FormulaItem("4. As = Mu / (fy/gamma_s * j * d)")
            FormulaItem("5. Check Shear: q_u = Vu / (b * d) < q_cu_limit")
        }
    }
}

@Composable
private fun BeamDiagramCanvas(result: CalculatorEngine.BeamResult, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
            val w = size.width
            val h = size.height
            val baseline = h * 0.4f
            
            // Draw baseline
            drawLine(Color.DarkGray, Offset(0f, baseline), Offset(w, baseline), strokeWidth = 3f)
            
            // Draw Supports based on type
            drawSupport(result.supportType, 0f, baseline)
            drawSupport(result.supportType, w, baseline, isEnd = true)

            // Draw BMD Path
            val path = Path()
            path.moveTo(0f, baseline)
            val segments = 50
            for (i in 0..segments) {
                val x = (i.toFloat() / segments) * w
                val normX = i.toFloat() / segments
                val y = when(result.supportType) {
                    CalculatorEngine.SupportType.CANTILEVER -> baseline - (h * 0.5f * normX * normX)
                    else -> baseline + (h * 0.5f * 4 * normX * (1 - normX))
                }
                path.lineTo(x, y)
            }
            drawPath(path, Color.Red, style = Stroke(4f))
            
            // Text for Max Moment
            drawContext.canvas.nativeCanvas.drawText(
                "M max = ${"%.1f".format(result.appliedMoment)} kN.m",
                w/2, h - 5f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.RED
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSupport(type: CalculatorEngine.SupportType, x: Float, y: Float, isEnd: Boolean = false) {
    val size = 20f
    when (type) {
        CalculatorEngine.SupportType.HINGED_HINGED, CalculatorEngine.SupportType.ROLLER_HINGED -> {
            val path = Path()
            path.moveTo(x, y)
            path.lineTo(x - size, y + size)
            path.lineTo(x + size, y + size)
            path.close()
            drawPath(path, Color.Black)
            if (type == CalculatorEngine.SupportType.ROLLER_HINGED && isEnd) {
                drawLine(Color.Black, Offset(x - size, y + size + 5f), Offset(x + size, y + size + 5f), strokeWidth = 2f)
            }
        }
        CalculatorEngine.SupportType.FIXED_HINGED, CalculatorEngine.SupportType.FIXED_FIXED -> {
            if ((!isEnd && (type == CalculatorEngine.SupportType.FIXED_HINGED || type == CalculatorEngine.SupportType.FIXED_FIXED)) || 
                (isEnd && type == CalculatorEngine.SupportType.FIXED_FIXED)) {
                drawLine(Color.Black, Offset(x, y - size), Offset(x, y + size), strokeWidth = 6f)
                for (i in -2..2) {
                    val offset = i * 8f
                    drawLine(Color.Black, Offset(x, y + offset), Offset(x + (if(isEnd) -10f else 10f), y + offset - 10f), strokeWidth = 2f)
                }
            } else {
                // Hinged part
                val path = Path()
                path.moveTo(x, y)
                path.lineTo(x - size, y + size)
                path.lineTo(x + size, y + size)
                path.close()
                drawPath(path, Color.Black)
            }
        }
        CalculatorEngine.SupportType.CANTILEVER -> {
            if (!isEnd) {
                drawLine(Color.Black, Offset(x, y - size*2), Offset(x, y + size*2), strokeWidth = 8f)
            }
        }
        else -> {}
    }
}

@Composable
private fun BeamSectionDrawing(result: CalculatorEngine.BeamResult, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().background(Color.White)) {
            val scale = minOf((size.width - 120f)/result.width, (size.height - 120f)/result.depth).toFloat()
            val w = (result.width * scale).toFloat()
            val h = (result.depth * scale).toFloat()
            val left = (size.width - w) / 2
            val top = (size.height - h) / 2
            
            // Concrete
            drawRect(Color.LightGray, Offset(left, top), Size(w, h))
            drawRect(Color.DarkGray, Offset(left, top), Size(w, h), style = Stroke(4f))
            
            // Stirrup (Ties)
            val cover = 25f * scale
            drawRect(Color.Blue.copy(alpha = 0.8f), Offset(left + cover, top + cover), Size(w - 2*cover, h - 2*cover), style = Stroke(3f))
            
            // Main Reinforcement (Bottom)
            val barR = 8f
            val nBarsBottom = result.reinforcementBottom.numBars
            for (i in 0 until nBarsBottom) {
                val x = left + cover + 20f + i * (w - 2*cover - 40f) / (if(nBarsBottom > 1) nBarsBottom - 1 else 1)
                drawCircle(Color.Black, barR, Offset(x, top + h - cover - 20f))
            }
            
            // Top Reinforcement (Hangers)
            val nBarsTop = result.reinforcementTop.numBars
            for (i in 0 until nBarsTop) {
                val x = left + cover + 20f + i * (w - 2*cover - 40f) / (if(nBarsTop > 1) nBarsTop - 1 else 1)
                drawCircle(Color.DarkGray, barR * 0.8f, Offset(x, top + cover + 20f))
            }
            
            // Dimensions
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply { 
                    color = android.graphics.Color.BLACK
                    textSize = 30f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText("${result.width.toInt()} mm", size.width/2, top - 15f, paint)
                save()
                rotate(-90f, left - 20f, size.height/2)
                drawText("${result.depth.toInt()} mm", left - 20f, size.height/2, paint)
                restore()
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
private fun BeamInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
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
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
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
