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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootingScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val engine = remember { CalculatorEngine(settingsManager) }
    
    var axialLoad by remember { mutableStateOf("1200") }
    var soilCapacity by remember { mutableStateOf("150") }
    var colLength by remember { mutableStateOf("600") }
    var colWidth by remember { mutableStateOf("300") }
    var result by remember { mutableStateOf<CalculatorEngine.FootingResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم القواعد المنفصلة Pro", fontWeight = FontWeight.Bold) },
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
            item { SectionHeader("📐 أبعاد العمود والأحمال", R.drawable.ic_footing) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FootingInputField(colLength, "طول العمود (mm)", { colLength = it }, Modifier.weight(1f))
                    FootingInputField(colWidth, "عرض العمود (mm)", { colWidth = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FootingInputField(axialLoad, "Pu (kN)", { axialLoad = it }, Modifier.weight(1f))
                    FootingInputField(soilCapacity, "Soil (kPa)", { soilCapacity = it }, Modifier.weight(1f))
                }
            }

            item {
                Button(
                    onClick = {
                        result = engine.calculateFooting(
                            p = axialLoad.toDoubleOrNull() ?: 1200.0,
                            fcu = 25.0,
                            fy = 360.0,
                            soil = soilCapacity.toDoubleOrNull() ?: 150.0,
                            colB = colWidth.toDoubleOrNull() ?: 300.0,
                            colT = colLength.toDoubleOrNull() ?: 600.0,
                            code = CalculatorEngine.DesignCode.EGYPTIAN
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Calculate, null)
                    Spacer(Modifier.width(8.dp))
                    Text("تحليل وتصميم القاعدة")
                }
            }

            result?.let { res ->
                item { SectionHeader("📊 نتائج التصميم", R.drawable.ic_calculator) }
                
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ResultRow("أبعاد القاعدة", "${res.width.toInt()} x ${res.length.toInt()} mm")
                            ResultRow("سمك القاعدة", "${res.thickness.toInt()} mm")
                            ResultRow("التسليح (X & Y)", "${res.barsX}Ø${res.barDiameter}")
                            ResultRow("حجم الخرسانة", "${"%.2f".format(res.concreteVolume)} m³")
                            ResultRow("التكلفة التقديرية", "${"%.2f".format(res.cost)} ${settingsManager.currency}")
                        }
                    }
                }

                item {
                    Text("🎨 مخطط القاعدة والعمود", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FootingDrawingCanvas(
                        fL = res.length,
                        fW = res.width,
                        cL = colLength.toDoubleOrNull() ?: 600.0,
                        cW = colWidth.toDoubleOrNull() ?: 300.0,
                        modifier = Modifier.fillMaxWidth().height(250.dp)
                    )
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
private fun FootingDrawingCanvas(fL: Double, fW: Double, cL: Double, cW: Double, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().background(Color.White)) {
            val scale = minOf((size.width - 60f)/fL, (size.height - 60f)/fW).toFloat()
            val w = (fL * scale).toFloat()
            val h = (fW * scale).toFloat()
            val left = (size.width - w) / 2
            val top = (size.height - h) / 2
            
            drawRect(Color.LightGray, Offset(left, top), Size(w, h))
            drawRect(Color.DarkGray, Offset(left, top), Size(w, h), style = Stroke(3f))
            
            val cw = (cL * scale).toFloat()
            val ch = (cW * scale).toFloat()
            drawRect(Color.DarkGray, Offset(left + (w-cw)/2, top + (h-ch)/2), Size(cw, ch))
            
            drawLine(Color.Red.copy(alpha = 0.3f), Offset(size.width/2, top - 10f), Offset(size.width/2, top + h + 10f), strokeWidth = 1f)
            drawLine(Color.Red.copy(alpha = 0.3f), Offset(left - 10f, size.height/2), Offset(left + w + 10f, size.height/2), strokeWidth = 1f)
        }
    }
}
