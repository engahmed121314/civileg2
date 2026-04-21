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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.livedata.observeAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.R
import com.civileg.app.viewmodel.FootingViewModel
import com.civileg.app.viewmodel.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootingScreen(
    viewModel: FootingViewModel = hiltViewModel(),
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
    var designName by remember { mutableStateOf("قاعدة منفصلة F1") }
    
    var selectedType by remember { mutableStateOf(CalculatorEngine.FootingType.ISOLATED) }
    var expandedType by remember { mutableStateOf(false) }
    var barDiameter by remember { mutableStateOf("16") }
    var barSpacing by remember { mutableStateOf("150") }
    
    var axialLoad by remember { mutableStateOf("1200") }
    var soilCapacity by remember { mutableStateOf("150") }
    var colLength by remember { mutableStateOf("600") }
    var colWidth by remember { mutableStateOf("300") }
    
    // Combined Footing and Boundary Parameters
    var axialLoad2 by remember { mutableStateOf("1000") }
    var colDistance by remember { mutableStateOf("3.5") }
    var maxLeft by remember { mutableStateOf("") }
    var maxRight by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم القواعد الاحترافي Pro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
            item { SectionHeader("🏗️ نوع القاعدة والمدخلات", R.drawable.ic_footing) }

            item {
                Column {
                    Text("نوع القاعدة", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedType,
                        onExpandedChange = { expandedType = !expandedType }
                    ) {
                        OutlinedTextField(
                            value = selectedType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedType,
                            onDismissRequest = { expandedType = false }
                        ) {
                            CalculatorEngine.FootingType.entries.forEach { type ->
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
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FootingInputField(colLength, "طول العمود (mm)", { colLength = it }, Modifier.weight(1f))
                    FootingInputField(colWidth, "عرض العمود (mm)", { colWidth = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FootingInputField(axialLoad, "Pu1 (kN)", { axialLoad = it }, Modifier.weight(1f))
                    FootingInputField(soilCapacity, "Soil (kPa)", { soilCapacity = it }, Modifier.weight(1f))
                }
            }

            if (selectedType == CalculatorEngine.FootingType.COMBINED) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FootingInputField(axialLoad2, "Pu2 (kN)", { axialLoad2 = it }, Modifier.weight(1f))
                        FootingInputField(colDistance, "Distance (m)", { colDistance = it }, Modifier.weight(1f))
                    }
                }
            }

            item {
                Text("Boundary Constraints (Optional)", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FootingInputField(maxLeft, "Max Left (m)", { maxLeft = it }, Modifier.weight(1f))
                    FootingInputField(maxRight, "Max Right (m)", { maxRight = it }, Modifier.weight(1f))
                }
            }

            item {
                Text("خيارات التسليح (لتقييم التوفير)", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FootingInputField(barDiameter, "قطر السيخ Ø", { barDiameter = it }, Modifier.weight(1f))
                    FootingInputField(barSpacing, "المسافة S (mm)", { barSpacing = it }, Modifier.weight(1f))
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.calculateFooting(
                            type = selectedType,
                            p = axialLoad.toDoubleOrNull() ?: 1200.0,
                            fcu = 25.0,
                            fy = 360.0,
                            soil = soilCapacity.toDoubleOrNull() ?: 150.0,
                            colB = colWidth.toDoubleOrNull() ?: 300.0,
                            colT = colLength.toDoubleOrNull() ?: 600.0,
                            code = CalculatorEngine.DesignCode.EGYPTIAN,
                            preferredDiameter = barDiameter.toIntOrNull() ?: 16,
                            preferredSpacing = barSpacing.toDoubleOrNull() ?: 150.0,
                            p2 = axialLoad2.toDoubleOrNull() ?: 0.0,
                            distance = (colDistance.toDoubleOrNull() ?: 3.5) * 1000.0,
                            maxLeft = maxLeft.toDoubleOrNull()?.let { it * 1000.0 },
                            maxRight = maxRight.toDoubleOrNull()?.let { it * 1000.0 }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Calculate, null)
                        Spacer(Modifier.width(8.dp))
                        Text("تحليل وتصميم القاعدة")
                    }
                }
            }

            result?.let { res ->
                item { SectionHeader("📊 نتائج التصميم", R.drawable.ic_calculator) }
                
                item {
                    val ecoColor = if (res.isOptimal) Color(0xFF2E7D32) else Color(0xFFF57C00)
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
                                        Icon(if (res.isOptimal) Icons.Default.Verified else Icons.Default.Info, 
                                            contentDescription = null, tint = ecoColor)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (res.isOptimal) "تصميم آمن واقتصادي" 
                                            else "آمن (تحت المراجعة)",
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
                                        color = when {
                                            res.utilizationRatio > 1.0 -> Color.Red
                                            res.utilizationRatio > 0.9 -> Color(0xFFFF9800)
                                            res.utilizationRatio > 0.4 -> Color(0xFF4CAF50)
                                            else -> Color(0xFF2196F3)
                                        },
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    Text(
                                        "${(res.utilizationRatio * 100).toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))

                            ResultRow("أبعاد القاعدة", "${res.width.toInt()} x ${res.length.toInt()} mm")
                            ResultRow("سمك القاعدة", "${res.thickness.toInt()} mm")
                            ResultRow("حجم الخرسانة", "${"%.2f".format(res.concreteVolume)} m³")
                            ResultRow("التكلفة التقديرية", "${"%.2f".format(res.cost)}")
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { 
                                viewModel.exportToPdf(context) { file -> }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isExporting,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.PictureAsPdf, null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isExporting) "جاري التصدير..." else "تقرير PDF")
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
                    Text("🎨 مخطط القاعدة والعمود", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FootingProfessionalDrawing(
                        fL = res.length,
                        fW = res.width,
                        thickness = res.thickness,
                        cL = colLength.toDoubleOrNull() ?: 600.0,
                        cW = colWidth.toDoubleOrNull() ?: 300.0,
                        modifier = Modifier.fillMaxWidth().height(350.dp)
                    )
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
                        label = { Text("اسم القاعدة (مثلاً: F1)") },
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
                    result?.let { viewModel.saveFooting(pId, designName, it) }
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
private fun SectionHeader(title: String, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun FootingInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
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
private fun FootingProfessionalDrawing(fL: Double, fW: Double, thickness: Double, cL: Double, cW: Double, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
            val scale = minOf((size.width / 2.5f) / fL.toFloat(), (size.height / 2.5f) / fW.toFloat())
            
            // 1. Plan View (Top Left)
            val pW = (fL * scale).toFloat()
            val pH = (fW * scale).toFloat()
            val pOffset = Offset(50f, 50f)
            drawRect(Color(0xFFE0E0E0), pOffset, Size(pW, pH))
            drawRect(Color.DarkGray, pOffset, Size(pW, pH), style = Stroke(2f))
            
            val pcW = (cL * scale).toFloat()
            val pcH = (cW * scale).toFloat()
            drawRect(Color.Gray, Offset(pOffset.x + (pW-pcW)/2, pOffset.y + (pH-pcH)/2), Size(pcW, pcH))
            
            // 2. Section View (Bottom/Right)
            val sW = pW
            val sH = (thickness * scale).toFloat()
            val sOffset = Offset(50f, pH + 100f)
            
            // Draw Concrete
            drawRect(Color(0xFFE0E0E0), sOffset, Size(sW, sH))
            drawRect(Color.DarkGray, sOffset, Size(sW, sH), style = Stroke(2f))
            
            // Draw Column Neck
            drawRect(Color.Gray, Offset(sOffset.x + (sW-pcW)/2, sOffset.y - 60f), Size(pcW, 60f))
            
            // Draw Reinforcement (Professional Look)
            val cover = 50f * scale
            // Bottom Main Bars
            drawLine(Color.Red, Offset(sOffset.x + cover, sOffset.y + sH - cover), Offset(sOffset.x + sW - cover, sOffset.y + sH - cover), strokeWidth = 3f)
            // Hooks
            drawLine(Color.Red, Offset(sOffset.x + cover, sOffset.y + sH - cover), Offset(sOffset.x + cover, sOffset.y + sH - cover - 20f), strokeWidth = 3f)
            drawLine(Color.Red, Offset(sOffset.x + sW - cover, sOffset.y + sH - cover), Offset(sOffset.x + sW - cover, sOffset.y + sH - cover - 20f), strokeWidth = 3f)
            
            // Distribution dots
            var dx = sOffset.x + cover + 20f
            while(dx < sOffset.x + sW - cover) {
                drawCircle(Color.Red, radius = 3f, center = Offset(dx, sOffset.y + sH - cover - 8f))
                dx += 30f * scale
            }
        }
    }
}
