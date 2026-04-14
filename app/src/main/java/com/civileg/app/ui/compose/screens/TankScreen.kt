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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.TankViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TankScreen(
    viewModel: TankViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    
    // تصحيح: تحديد النوع صراحة وحل مشكلة المسميات المفقودة
    var selectedType by remember { mutableStateOf<CalculatorEngine.TankType>(CalculatorEngine.TankType.RECTANGULAR_GROUND) }
    var capacity by remember { mutableStateOf("50.0") }
    var height by remember { mutableStateOf("3.5") }
    var fcu by remember { mutableStateOf("30") }
    var fy by remember { mutableStateOf("400") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم خزانات المياه Pro", fontWeight = FontWeight.Bold) },
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
            item { SectionHeader("📐 نوع الخزان والبيانات", R.drawable.ic_water) }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TankTypeChip(CalculatorEngine.TankType.RECTANGULAR_GROUND, "أرضي مستطيل", selectedType) { selectedType = it }
                    TankTypeChip(CalculatorEngine.TankType.CIRCULAR_GROUND, "أرضي دائري", selectedType) { selectedType = it }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TankInputField(capacity, "السعة (m³)", { capacity = it }, Modifier.weight(1f))
                    TankInputField(height, "الارتفاع (m)", { height = it }, Modifier.weight(1f))
                }
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
                            code = CalculatorEngine.DesignCode.EGYPTIAN
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
                    Text("🎨 مخطط الخزان", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TankDrawingCanvas(type = selectedType, modifier = Modifier.fillMaxWidth().height(200.dp))
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
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

@Composable
private fun TankDrawingCanvas(type: CalculatorEngine.TankType, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().background(Color.White)) {
            val padding = 50f
            val w = size.width - 2*padding
            val h = size.height - 2*padding
            
            // تصحيح مسميات النوع في الرسم
            if (type == CalculatorEngine.TankType.CIRCULAR_GROUND || type == CalculatorEngine.TankType.CIRCULAR_ELEVATED || type == CalculatorEngine.TankType.CIRCULAR_UNDERGROUND) {
                drawCircle(Color.LightGray, radius = h/2, center = Offset(size.width/2, size.height/2))
                drawCircle(Color.DarkGray, radius = h/2, center = Offset(size.width/2, size.height/2), style = Stroke(4f))
                drawCircle(Color.Blue.copy(alpha = 0.2f), radius = h/2 - 10f, center = Offset(size.width/2, size.height/2))
            } else {
                drawRect(Color.LightGray, Offset(padding, padding), Size(w, h))
                drawRect(Color.DarkGray, Offset(padding, padding), Size(w, h), style = Stroke(4f))
                drawRect(Color.Blue.copy(alpha = 0.2f), Offset(padding + 10f, padding + 10f), Size(w - 20f, h - 20f))
            }
        }
    }
}
