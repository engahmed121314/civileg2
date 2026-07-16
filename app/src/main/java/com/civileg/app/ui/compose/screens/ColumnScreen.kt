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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import com.civileg.app.viewmodel.ProjectViewModel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.civileg.app.db.Project
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.ColumnViewModel
import com.civileg.app.ui.compose.components.drawings.ProfessionalColumnDrawing
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.BarInfo
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScreen(
    viewModel: ColumnViewModel = hiltViewModel(),
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val projects by projectViewModel.allProjects.observeAsState(emptyList())

    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    var designName by remember { mutableStateOf("عمود الدور الأرضي") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_column_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.result != null) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(end = 8.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            IconButton(onClick = { viewModel.exportToPdf(context) {} }) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DesignCodeSelector(
                            selectedCode = uiState.designCode,
                            onCodeSelected = { viewModel.updateDesignCode(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LoadCombinationSelector(
                            selected = uiState.loadCombination,
                            onSelected = { viewModel.updateLoadCombination(it) }
                        )
                        
                        Text(
                            text = if (uiState.loadCombination == LoadCombination.DEAD_ONLY) 
                                "💡 Dead Load Only: يستخدم عادة في المنشآت المؤقتة أو تحت ظروف خاصة حيث لا يوجد أحمال حية مؤثرة."
                                else "💡 Dead + Live: المزيج التصميمي الأكثر شيوعاً وأماناً، يشمل وزن المنشأ والأثاث والأشخاص.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            
            item { SectionHeader("📐 أبعاد المقطع والخصائص", R.drawable.ic_column) }
            
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColumnInputField(uiState.width, "العرض b (mm)", { viewModel.updateInputs(width = it) }, Modifier.weight(1f))
                    ColumnInputField(uiState.depth, "العمق t (mm)", { viewModel.updateInputs(depth = it) }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColumnInputField(uiState.fcu, "fcu (MPa)", { viewModel.updateInputs(fcu = it) }, Modifier.weight(1f))
                    ColumnInputField(uiState.fy, "fy (MPa)", { viewModel.updateInputs(fy = it) }, Modifier.weight(1f))
                }
            }

            item { SectionHeader("⚡ الأحمال المطبقة", R.drawable.ic_design) }
            
            item {
                ColumnInputField(uiState.axialLoad, "الحمل المحوري Pu (kN)", { viewModel.updateInputs(axialLoad = it) }, Modifier.fillMaxWidth())
            }

            item {
                Button(
                    onClick = { viewModel.calculateManual() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حساب المقطع (Design Now)", fontWeight = FontWeight.Bold)
                }
            }

            uiState.result?.let { result ->
                item { SectionHeader("📊 نتائج التصميم ومؤشر التوفير", R.drawable.ic_calculator) }
                
                item {
                    val ecoColor = when {
                        result.utilizationRatio > 1.0 -> Color.Red
                        result.utilizationRatio > 0.9 -> Color(0xFFFF9800)
                        result.utilizationRatio > 0.4 -> Color(0xFF4CAF50)
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
                                            if (result.utilizationRatio <= 1.0) Icons.Default.Verified 
                                            else Icons.Default.Dangerous, 
                                            contentDescription = null, 
                                            tint = ecoColor
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (result.utilizationRatio > 1.0) "القطاع غير آمن إنشائياً! ❌"
                                            else if (result.utilizationRatio > 0.9) "تحميل عالي (حذر) ⚠️"
                                            else if (result.utilizationRatio > 0.4) "قطاع مثالي واقتصادي ✅"
                                            else "قطاع كبير جداً (غير اقتصادي) 🔵",
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
                                        targetValue = result.utilizationRatio.toFloat(),
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
                                        "${(result.utilizationRatio * 100).toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                item { ColumnResultCard(result) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.applyEconomicalDesign() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("خيار موفر 💰", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { viewModel.applySafetyDesign() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("الأكثر أماناً 🛡️", fontSize = 12.sp)
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("تخصيص التسليح (لتحقيق التوفير)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = uiState.autoOptimize,
                                    onCheckedChange = { viewModel.updateAutoOptimize(it) },
                                    thumbContent = { if (uiState.autoOptimize) Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp)) }
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ColumnInputField(
                                    value = if (uiState.autoOptimize && uiState.manualNumBars.isEmpty()) (result.reinforcement.numBars.toString()) else uiState.manualNumBars,
                                    label = "عدد الأسياخ",
                                    onValueChange = { viewModel.updateInputs(manualNumBars = it) },
                                    modifier = Modifier.weight(1f),
                                    enabled = true
                                )
                                ColumnInputField(
                                    value = uiState.preferredDiameter,
                                    label = "القطر Ø (mm)",
                                    onValueChange = { viewModel.updateInputs(preferredDiameter = it) },
                                    modifier = Modifier.weight(1f),
                                    enabled = true
                                )
                            }
                            if (!uiState.autoOptimize) {
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.calculateManual() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("تحديث التسليح المخصص")
                                }
                            }
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.exportToPdf(context) { /* Handle completion */ } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(painterResource(id = R.drawable.ic_pdf), contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تقرير PDF")
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
                    Text("📝 المعادلات الهندسية (بدون نتائج)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    EngineeringFormulasCard()
                }

                item {
                    InteractiveDrawingScreen(
                        title = "📐 الرسم الهندسي التفصيلي",
                        subtitle = "Column Reinforcement Detail",
                        viewModes = listOf("الكل", "المنظور", "المقطع العرضي", "جدول التسليح"),
                        drawingContent = {
                            ProfessionalColumnDrawing(
                                columnWidth = result.width.toDouble(),
                                columnDepth = result.depth.toDouble(),
                                columnHeight = 3000.0,
                                longitudinalBars = generateBarPositions(result.width.toDouble(), result.depth.toDouble(), result.reinforcement.numBars, result.reinforcement.diameter.toDouble()),
                                tieDia = result.stirrups.diameter.toDouble(),
                                tieSpacing = result.stirrups.spacing.toDouble(),
                                cover = 40.0,
                                isSpiral = false,
                                sectionType = if (result.columnType.contains("CIRCULAR", ignoreCase = true)) "Circular" else "Rectangular",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
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
                        label = { Text("اسم العمود (مثلاً: C1)") },
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
                    uiState.result?.let { viewModel.saveColumn(pId, designName, it) }
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
private fun ColumnResultCard(result: CalculatorEngine.ColumnResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (result.isSafe) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (result.isSafe) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (result.isSafe) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (result.isSafe) "المقطع آمن (SAFE)" else "المقطع غير آمن (UNSAFE)",
                    fontWeight = FontWeight.Bold,
                    color = if (result.isSafe) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.2f))
            
            ResultDataRow("التسليح الموفر", result.reinforcement.barString)
            ResultDataRow("الكانات", result.stirrups.description)
            ResultDataRow("نسبة التسليح (μ)", String.format("%.2f %%", result.reinforcementRatio))
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.1f))
            
            Text("الكميات والتقديرات:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            ResultDataRow("حجم الخرسانة", String.format("%.3f m³", result.concreteVolume))
            ResultDataRow("وزن الحديد", String.format("%.2f kg", result.steelWeight))
            ResultDataRow("التكلفة التقديرية", String.format("%.2f", result.cost))

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.1f))
            
            Text("التحقق من الكود (Safety Checks):", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            result.safetyChecks.forEach { check ->
                ResultCheckRow(check)
            }
        }
    }
}

@Composable
private fun ResultCheckRow(check: CalculatorEngine.DesignSafetyCheck) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (check.isSafe) "آمن ✅" else "غير آمن ❌",
            color = if (check.isSafe) Color(0xFF2E7D32) else Color(0xFFC62828),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(check.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "${String.format("%.2f", check.value)} / ${String.format("%.2f", check.limit)} ${check.unit}",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun EngineeringFormulasCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            FormulaItem("Pu_limit = 0.35 * fcu * Ac + 0.67 * fy * As")
            FormulaItem("Shear Check: q_u = Pu / (b * d) < q_cu")
            FormulaItem("Punching Check: q_p = Pu / (perimeter * d) < q_p_limit")
            FormulaItem("As_min = 0.008 * Ac")
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
private fun ColumnInputField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
    )
}

@Composable
private fun ResultDataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FormulaItem(formula: String) {
    Text(
        text = formula,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
private fun DesignCodeSelector(selectedCode: DesignCode, onCodeSelected: (DesignCode) -> Unit) {
    Text("اختر كود التصميم", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DesignCode.values().forEach { code ->
            val isSelected = code == selectedCode
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(45.dp)
                    .clickable { onCodeSelected(code) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (code == DesignCode.SBC) "SAUDI Code" else if (code == DesignCode.ECP) "ECP Code" else "ACI Code",
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadCombinationSelector(selected: LoadCombination, onSelected: (LoadCombination) -> Unit) {
    Text("تركيبة الأحمال", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LoadCombination.values().take(2).forEach { combo ->
            FilterChip(
                selected = combo == selected,
                onClick = { onSelected(combo) },
                label = { Text(combo.description) }
            )
        }
    }
}

private fun generateBarPositions(width: Double, depth: Double, numBars: Int, diameter: Double): List<BarInfo> {
    val cover = 40.0
    val cx = width / 2.0
    val cy = depth / 2.0
    val effectiveW = width - 2 * cover
    val effectiveD = depth - 2 * cover
    val positions = mutableListOf<BarInfo>()
    val barsPerSide = maxOf(2, numBars / 4)
    val corners = 4

    // Corner bars
    listOf(
        BarInfo(cover + diameter/2, cover + diameter/2, diameter, isCorner = true),
        BarInfo(width - cover - diameter/2, cover + diameter/2, diameter, isCorner = true),
        BarInfo(cover + diameter/2, depth - cover - diameter/2, diameter, isCorner = true),
        BarInfo(width - cover - diameter/2, depth - cover - diameter/2, diameter, isCorner = true)
    ).forEach { positions.add(it) }

    // Distribute remaining bars along sides
    val remaining = numBars - 4
    if (remaining > 0) {
        val perSide = remaining / 4
        for (side in 0 until 4) {
            val count = if (side < remaining % 4) perSide + 1 else perSide
            for (i in 1..count) {
                val t = i.toDouble() / (count + 1)
                val pos = when (side) {
                    0 -> BarInfo(cover + diameter/2 + t * effectiveW, cover + diameter/2, diameter)
                    1 -> BarInfo(width - cover - diameter/2, cover + diameter/2 + t * effectiveD, diameter)
                    2 -> BarInfo(width - cover - diameter/2 - t * effectiveW, depth - cover - diameter/2, diameter)
                    else -> BarInfo(cover + diameter/2, depth - cover - diameter/2 - t * effectiveD, diameter)
                }
                positions.add(pos)
            }
        }
    }
    return positions
}
