package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeismicScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val engine = remember { CalculatorEngine(settingsManager) }
    
    var zone by remember { mutableStateOf("0.15") }
    var importance by remember { mutableStateOf("1.0") }
    var height by remember { mutableStateOf("12.0") }
    var totalWeight by remember { mutableStateOf("5000") }
    
    var result by remember { mutableStateOf<CalculatorEngine.SeismicResult?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تحليل الأحمال الزلزالية", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SectionHeader("🌍 بيانات الموقع والمنشأ", R.drawable.ic_design) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SeismicInputField(zone, "عامل المنطقة (Z)", { zone = it }, Modifier.weight(1f))
                    SeismicInputField(importance, "عامل الأهمية (I)", { importance = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SeismicInputField(height, "ارتفاع المبنى (m)", { height = it }, Modifier.weight(1f))
                    SeismicInputField(totalWeight, "الوزن الكلي (kN)", { totalWeight = it }, Modifier.weight(1f))
                }
            }

            item {
                Button(
                    onClick = {
                        result = engine.calculateSeismicLoads(
                            CalculatorEngine.SeismicInput(
                                zone = zone.toDoubleOrNull() ?: 0.15,
                                importance = importance.toDoubleOrNull() ?: 1.0,
                                soilType = "C",
                                height = height.toDoubleOrNull() ?: 12.0,
                                totalWeight = totalWeight.toDoubleOrNull() ?: 5000.0
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Calculate, null)
                    Spacer(Modifier.width(8.dp))
                    Text("حساب قوى القص القاعدي")
                }
            }

            result?.let { res ->
                item { SectionHeader("📊 نتائج التحليل", R.drawable.ic_calculator) }
                
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ResultRow("قوة القص القاعدي (Vb)", "${"%.2f".format(res.baseShear)} kN")
                            ResultRow("الزمن الدوري (T)", "${"%.3f".format(res.timePeriod)} sec")
                            ResultRow("عجلة التصميم (Sd)", "${"%.3f".format(res.spectralAcceleration)} g")
                        }
                    }
                }

                item {
                    Text("🏢 توزيع القوى على الأدوار", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    res.forcesPerFloor.forEach { (floor, force) ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الدور $floor")
                                Text("${"%.2f".format(force)} kN", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
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
private fun SeismicInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
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
