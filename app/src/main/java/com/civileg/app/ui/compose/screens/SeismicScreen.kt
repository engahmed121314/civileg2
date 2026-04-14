package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Info
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
    var reductionFactor by remember { mutableStateOf("5.0") }
    var soilType by remember { mutableStateOf("C") }
    
    var result by remember { mutableStateOf<CalculatorEngine.SeismicResult?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تحليل الأحمال الزلزالية", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SeismicInputField(reductionFactor, "معامل التعديل (R)", { reductionFactor = it }, Modifier.weight(1f))
                    SeismicInputField(soilType, "نوع التربة (Soil)", { soilType = it }, Modifier.weight(1f))
                }
            }

            item {
                Button(
                    onClick = {
                        result = engine.calculateSeismicLoads(
                            CalculatorEngine.SeismicInput(
                                zone = zone.toDoubleOrNull() ?: 0.15,
                                importance = importance.toDoubleOrNull() ?: 1.0,
                                soilType = soilType,
                                height = height.toDoubleOrNull() ?: 12.0,
                                totalWeight = totalWeight.toDoubleOrNull() ?: 5000.0,
                                reductionFactor = reductionFactor.toDoubleOrNull() ?: 5.0
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.Calculate, null)
                    Spacer(Modifier.width(8.dp))
                    Text("حساب قوى القص القاعدي", style = MaterialTheme.typography.titleMedium)
                }
            }

            result?.let { res ->
                item { SectionHeader("📊 نتائج التحليل", R.drawable.ic_calculator) }
                
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ResultRow("قوة القص القاعدي (Vb)", "${"%.2f".format(res.baseShear)} kN")
                            ResultRow("الزمن الدوري (T)", "${"%.3f".format(res.timePeriod)} sec")
                            ResultRow("عجلة التصميم (Sd)", "${"%.3f".format(res.spectralAcceleration)} g")
                            ResultRow("إزاحة الدور (Drift)", "${"%.4f".format(res.storyDrift)} m")
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("🏢 توزيع القوى على الأدوار", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }

                res.forcesPerFloor.forEach { (floor, force) ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("الدور $floor", style = MaterialTheme.typography.bodyMedium)
                                Text("${"%.2f".format(force)} kN", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(
            painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}
