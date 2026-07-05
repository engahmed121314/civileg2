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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.StairViewModel
import com.civileg.app.viewmodel.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StairScreen(
    viewModel: StairViewModel = hiltViewModel(),
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)
    val projects by projectViewModel.allProjects.observeAsState(emptyList())

    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    var designName by remember { mutableStateOf("سلم S1") }
    
    var selectedType by remember { mutableStateOf(CalculatorEngine.StairType.SINGLE_FLIGHT) }
    var span by remember { mutableStateOf("4.0") }
    var riser by remember { mutableStateOf("150") }
    var tread by remember { mutableStateOf("300") }
    var liveLoad by remember { mutableStateOf("4.0") }
    var deadLoad by remember { mutableStateOf("5.0") }
    var expandedType by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم وتسليح السلالم Pro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        if (result != null) {
                            IconButton(onClick = { viewModel.exportToPdf(context) {} }) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (result != null) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
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
            item { SectionHeader("📐 نوع وأبعاد السلم", R.drawable.ic_stairs) }

            item {
                ExposedDropdownMenuBox(
                    expanded = expandedType,
                    onExpandedChange = { expandedType = !expandedType },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("نوع السلم") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                        CalculatorEngine.StairType.values().forEach { type ->
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
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StairInputField(span, "الطول الأفقي L (m)", { span = it }, Modifier.weight(1f))
                    StairInputField(riser, "القائمة R (mm)", { riser = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StairInputField(deadLoad, "D.L (kN/m²)", { deadLoad = it }, Modifier.weight(1f))
                    StairInputField(liveLoad, "L.L (kN/m²)", { liveLoad = it }, Modifier.weight(1f))
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.calculateStairPro(
                            type = selectedType,
                            span = span.toDoubleOrNull() ?: 4.0,
                            riser = riser.toDoubleOrNull() ?: 150.0,
                            tread = tread.toDoubleOrNull() ?: 300.0,
                            deadLoad = deadLoad.toDoubleOrNull() ?: 5.0,
                            liveLoad = liveLoad.toDoubleOrNull() ?: 4.0,
                            fcu = 25.0,
                            fy = 360.0,
                            preferredDiameter = 12,
                            code = CalculatorEngine.DesignCode.EGYPTIAN
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("إجراء الحسابات والرسم")
                }
            }

            result?.let { res ->
                item { SectionHeader("📊 نتائج التصميم الإنشائي", R.drawable.ic_calculator) }
                
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
                                            if (res.utilizationRatio <= 1.0) Icons.Default.Verified
                                            else Icons.Default.Dangerous,
                                            contentDescription = null,
                                            tint = ecoColor
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (res.utilizationRatio > 1.0) "تصميم غير آمن! ❌"
                                            else if (res.utilizationRatio > 0.9) "تحميل عالي (حذر) ⚠️"
                                            else if (res.utilizationRatio > 0.4) "تصميم مثالي واقتصادي ✅"
                                            else "قطاع كبير (غير اقتصادي) 🔵",
                                            fontWeight = FontWeight.Bold,
                                            color = ecoColor
                                        )
                                    }
                                    Text(
                                        "المستشار الإنشائي: نسبة الاستهلاك",
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
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ResultRow("سمك البلاطة ts", "${res.thickness.toInt()} mm")
                            ResultRow("التسليح الرئيسي", res.reinforcement.barString)
                            ResultRow("تسليح التوزيع", res.distributionReinforcement.barString)
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.exportToPdf(context) { /* Handle complete */ } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            enabled = !isExporting
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.PictureAsPdf, null)
                                Spacer(Modifier.width(8.dp))
                                Text("تقرير PDF")
                            }
                        }

                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("حفظ")
                        }
                    }
                }

                item {
                    Text("🎨 رسم تفصيلي للتسليح (Full Reinforcement)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    StairReinforcementDrawing(res, modifier = Modifier.fillMaxWidth().height(300.dp))
                }

                item {
                    Text("📝 المعادلات الهندسية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    StairFormulasCard()
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
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
                        label = { Text("اسم السلم (مثلاً: S1)") },
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
                    result?.let { viewModel.saveStair(pId, designName, it) }
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
private fun StairReinforcementDrawing(res: CalculatorEngine.StairResult, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
            val steps = 6
            val stepW = size.width / (steps + 2)
            val stepH = size.height / (steps + 2)
            
            val path = Path()
            path.moveTo(0f, size.height)
            path.lineTo(stepW, size.height)
            
            for (i in 0 until steps) {
                path.lineTo((i + 1) * stepW, size.height - (i + 1) * stepH)
                path.lineTo((i + 2) * stepW, size.height - (i + 1) * stepH)
            }
            path.lineTo(size.width, stepH)
            
            // Slab Bottom Path
            val bottomPath = Path()
            bottomPath.moveTo(0f, size.height)
            bottomPath.lineTo(size.width - stepW, size.height - (steps * stepH)) // Rough slab line
            
            drawPath(path, Color.DarkGray, style = Stroke(5f))
            
            // Main Reinforcement (Cranking / Scissor if needed, simplified for drawing)
            val rebarPath = Path()
            rebarPath.moveTo(10f, size.height - 25f)
            rebarPath.lineTo(size.width - 10f, stepH + 25f)
            drawPath(rebarPath, Color.Red, style = Stroke(4f))
            
            // Secondary bars (points)
            for (i in 0 until steps) {
                drawCircle(Color.Blue, radius = 5f, center = Offset((i + 1.5f) * stepW, size.height - (i + 1) * stepH + 20f))
            }
            
            drawContext.canvas.nativeCanvas.drawText(
                "T12 @ 150mm",
                size.width / 2, size.height / 2,
                android.graphics.Paint().apply { color = android.graphics.Color.RED; textSize = 30f; textAlign = android.graphics.Paint.Align.CENTER }
            )
        }
    }
}

@Composable
private fun StairFormulasCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            FormulaItem("1. Wu = 1.4 * DL + 1.6 * LL")
            FormulaItem("2. ts = L / 25 (Simple) or L / 20 (Cantilever)")
            FormulaItem("3. Mu = Wu * L² / 8")
            FormulaItem("4. As = Mu / (fy/gamma_s * j * d)")
        }
    }
}

@Composable
private fun StairInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
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
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
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

@Composable
private fun SectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}
