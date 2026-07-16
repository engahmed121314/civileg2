package com.civileg.app.ui.compose.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.ui.compose.components.drawings.InteractiveDrawingScreen
import com.civileg.app.ui.compose.components.drawings.ProfessionalTankDrawing
import com.civileg.app.ui.compose.components.DesignCodeSelectorRow
import com.civileg.app.viewmodel.TankViewModel
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.app.db.Project

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TankScreen(
    viewModel: TankViewModel = hiltViewModel(),
    projectViewModel: ProjectViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val projects by projectViewModel.allProjects.observeAsState(emptyList())

    var showSaveDialog by remember { mutableStateOf(false) }
    var selectedProjectId by remember { mutableLongStateOf(-1L) }
    var designName by remember { mutableStateOf("خزان مياه T1") }
    
    // تصحيح: تحديد النوع صراحة وحل مشكلة المسميات المفقودة
    var selectedType by remember { mutableStateOf<CalculatorEngine.TankType>(CalculatorEngine.TankType.RECTANGULAR_GROUND) }
    var capacity by remember { mutableStateOf("50.0") }
    var height by remember { mutableStateOf("3.5") }
    var fcu by remember { mutableStateOf("30") }
    var fy by remember { mutableStateOf("400") }
    var selectedCode by remember { mutableStateOf(CalculatorEngine.DesignCode.EGYPTIAN) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_tank_title), fontWeight = FontWeight.Bold) },
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
            item { SectionHeader("📐 نوع الخزان والبيانات", R.drawable.ic_water) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("موقع الخزان:", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val isGround = selectedType == CalculatorEngine.TankType.RECTANGULAR_GROUND || selectedType == CalculatorEngine.TankType.CIRCULAR_GROUND
                        val isElevated = selectedType == CalculatorEngine.TankType.RECTANGULAR_ELEVATED || selectedType == CalculatorEngine.TankType.CIRCULAR_ELEVATED
                        val isUnderground = selectedType == CalculatorEngine.TankType.UNDERGROUND || selectedType == CalculatorEngine.TankType.CIRCULAR_UNDERGROUND

                        FilterChip(selected = isGround, onClick = { selectedType = CalculatorEngine.TankType.RECTANGULAR_GROUND }, label = { Text("أرضي") })
                        FilterChip(selected = isElevated, onClick = { selectedType = CalculatorEngine.TankType.RECTANGULAR_ELEVATED }, label = { Text("علوي") })
                        FilterChip(selected = isUnderground, onClick = { selectedType = CalculatorEngine.TankType.UNDERGROUND }, label = { Text("تحت الأرض") })
                    }
                    
                    Text("شكل القطاع (هندسي):", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isRect = selectedType == CalculatorEngine.TankType.RECTANGULAR_GROUND || selectedType == CalculatorEngine.TankType.RECTANGULAR_ELEVATED || selectedType == CalculatorEngine.TankType.UNDERGROUND
                        
                        FilterChip(
                            selected = isRect, 
                            onClick = { 
                                selectedType = when(selectedType) {
                                    CalculatorEngine.TankType.CIRCULAR_GROUND -> CalculatorEngine.TankType.RECTANGULAR_GROUND
                                    CalculatorEngine.TankType.CIRCULAR_ELEVATED -> CalculatorEngine.TankType.RECTANGULAR_ELEVATED
                                    CalculatorEngine.TankType.CIRCULAR_UNDERGROUND -> CalculatorEngine.TankType.UNDERGROUND
                                    else -> selectedType
                                }
                            }, 
                            label = { Text("مستطيل (Bending)") }
                        )
                        FilterChip(
                            selected = !isRect, 
                            onClick = { 
                                selectedType = when(selectedType) {
                                    CalculatorEngine.TankType.RECTANGULAR_GROUND -> CalculatorEngine.TankType.CIRCULAR_GROUND
                                    CalculatorEngine.TankType.RECTANGULAR_ELEVATED -> CalculatorEngine.TankType.CIRCULAR_ELEVATED
                                    CalculatorEngine.TankType.UNDERGROUND -> CalculatorEngine.TankType.CIRCULAR_UNDERGROUND
                                    else -> selectedType
                                }
                            }, 
                            label = { Text("دائري (Hoop Tension)") }
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TankInputField(capacity, "السعة (m³)", { capacity = it }, Modifier.weight(1f))
                    TankInputField(height, "الارتفاع (m)", { height = it }, Modifier.weight(1f))
                }
            }

            item {
                DesignCodeSelectorRow(
                    selectedCode = selectedCode,
                    onCodeSelected = { selectedCode = it }
                )
            }

            item {
                Button(
                    onClick = {
                        viewModel.calculateTankPro(
                            type = selectedType,
                            capacity = capacity.toDoubleOrNull() ?: 50.0,
                            height = height.toDoubleOrNull() ?: 3.0,
                            fcu = fcu.toDoubleOrNull() ?: 30.0,
                            fy = fy.toDoubleOrNull() ?: 400.0,
                            preferredDiameter = 12,
                            code = selectedCode
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
                        Text("بدء التصميم الإنشائي")
                    }
                }
            }

            result?.let { res ->
                item { SectionHeader("📊 نتائج التحليل المائي", R.drawable.ic_calculator) }

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
                                            else if (res.utilizationRatio > 0.4) "خزان مثالي واقتصادي ✅"
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ResultRow("سمك الحوائط", "${res.wallThickness.toInt()} mm")
                            ResultRow("سمك اللبشة (Base)", "${res.baseThickness.toInt()} mm")
                            ResultRow("ضغط الماء الأقصى", "${"%.1f".format(res.waterPressure)} kN/m²")
                            ResultRow("تسليح الحوائط", res.wallReinforcement.barString)
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.exportToPdf(context) { /* Handle complete */ } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null)
                            Spacer(Modifier.width(8.dp))
                            Text("تقرير PDF")
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
                    InteractiveDrawingScreen(
                        title = "📐 رسم الخزان التفصيلي",
                        subtitle = "Water Tank Detail",
                        viewModes = listOf("الكل", "المنظور", "المقطع", "جدول التسليح"),
                        drawingContent = {
                            ProfessionalTankDrawing(
                                tankType = selectedType.displayName,
                                length = res.length,
                                width = res.width,
                                height = res.height,
                                wallThickness = res.wallThickness,
                                baseThickness = res.baseThickness,
                                waterLevel = res.height * 0.85,
                                verticalRebarDia = res.wallReinforcement.diameter.toDouble(),
                                verticalRebarSpacing = res.wallReinforcement.spacing.toDouble(),
                                horizontalRebarDia = res.baseReinforcement.diameter.toDouble(),
                                horizontalRebarSpacing = res.baseReinforcement.spacing.toDouble(),
                                foundationDepth = if (selectedType == CalculatorEngine.TankType.UNDERGROUND || selectedType == CalculatorEngine.TankType.CIRCULAR_UNDERGROUND) res.height * 0.3 else 0.0,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
                        label = { Text("اسم الخزان (مثلاً: T1)") },
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
                    result?.let { viewModel.saveTank(pId, designName, it) }
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
private fun TankTypeChip(type: CalculatorEngine.TankType, label: String, selected: CalculatorEngine.TankType, onSelect: (CalculatorEngine.TankType) -> Unit) {
    FilterChip(
        selected = type == selected,
        onClick = { onSelect(type) },
        label = { Text(label) },
        modifier = Modifier.padding(horizontal = 4.dp)
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

@Composable
private fun TankInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
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
