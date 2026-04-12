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
    onNavigateBack: () -> Unit = {}
) {
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()
    
    var width by remember { mutableStateOf("250") }
    var height by remember { mutableStateOf("600") }
    var span by remember { mutableStateOf("5.0") }
    var deadLoad by remember { mutableStateOf("15.0") }
    var liveLoad by remember { mutableStateOf("10.0") }
    var fcu by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("360") }
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
                            16,
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
                item { SectionHeader("📊 نتائج التحليل الإنشائي", R.drawable.ic_calculator) }
                
                item { BeamResultCard(res) }

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
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
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
