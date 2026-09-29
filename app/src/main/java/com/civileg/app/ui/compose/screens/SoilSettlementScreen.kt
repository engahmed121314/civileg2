package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.core.engineering.SettlementAnalysisEngine
import com.civileg.app.viewmodel.SoilSettlementViewModel
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoilSettlementScreen(
    viewModel: SoilSettlementViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isExporting by viewModel.isExporting.observeAsState(false)

    var pressure by remember { mutableStateOf("150") }
    var width by remember { mutableStateOf("2.0") }
    var length by remember { mutableStateOf("2.0") }
    var limit by remember { mutableStateOf("50") }

    val layers = remember { mutableStateListOf(
        SettlementAnalysisEngine.SoilLayer("Clay Layer", 5.0, 18.0, 15000.0, 0.3, 0.25, 0.05, 0.8)
    ) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_settlement), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportToPdf(context) { } }, enabled = result != null && !isExporting) {
                        if (isExporting) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.PictureAsPdf, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Foundation & Load", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    SettlementInput(pressure, "Pressure (kPa)", { pressure = it }, Modifier.weight(1f))
                    SettlementInput(limit, "Limit (mm)", { limit = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    SettlementInput(width, "Width (m)", { width = it }, Modifier.weight(1f))
                    SettlementInput(length, "Length (m)", { length = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Soil Layers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { 
                        layers.add(SettlementAnalysisEngine.SoilLayer("New Layer ${layers.size + 1}", 3.0, 19.0, 20000.0))
                    }) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            itemsIndexed(layers) { index, layer ->
                LayerCard(index, layer, 
                    onUpdate = { updated -> layers[index] = updated },
                    onDelete = { if (layers.size > 1) layers.removeAt(index) }
                )
            }

            item {
                Button(
                    onClick = {
                        viewModel.calculate(
                            pressure.toDoubleOrNull() ?: 0.0,
                            width.toDoubleOrNull() ?: 0.0,
                            length.toDoubleOrNull() ?: 0.0,
                            layers.toList(),
                            limit.toDoubleOrNull() ?: 50.0
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Default.Calculate, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Analyze Settlement")
                    }
                }
            }
            
            item {
                SettlementVisualizer(layers)
            }

            result?.let { res ->
                item {
                    SettlementResultCard(res)
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun SettlementVisualizer(layers: List<SettlementAnalysisEngine.SoilLayer>) {
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val w = size.width
            val h = size.height
            val totalThickness = layers.sumOf { it.thickness }.coerceAtLeast(1.0)
            
            var currentY = 0f
            layers.forEachIndexed { i, layer ->
                val layerH = (layer.thickness / totalThickness * h).toFloat()
                val color = if (i % 2 == 0) Color(0xFF8B4513).copy(alpha = 0.6f) else Color(0xFFD2B48C).copy(alpha = 0.6f)
                
                drawRect(color, Offset(0f, currentY), Size(w, layerH))
                drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, currentY), Offset(w, currentY), 1f)
                
                currentY += layerH
            }
            
            val footW = w * 0.4f
            drawRect(Color.LightGray, Offset(w/2 - footW/2, 0f), Size(footW, 15f))
        }
    }
}

@Composable
private fun LayerCard(index: Int, layer: SettlementAnalysisEngine.SoilLayer, onUpdate: (SettlementAnalysisEngine.SoilLayer) -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Layer ${index + 1}: ${layer.name}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettlementInput(layer.thickness.toString(), "Thick (m)", { v -> onUpdate(layer.copy(thickness = v.toDoubleOrNull() ?: 0.0)) }, Modifier.weight(1f))
                SettlementInput(layer.unitWeight.toString(), "γ (kN/m³)", { v -> onUpdate(layer.copy(unitWeight = v.toDoubleOrNull() ?: 0.0)) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettlementInput(layer.elasticModulus.toString(), "Es (kPa)", { v -> onUpdate(layer.copy(elasticModulus = v.toDoubleOrNull() ?: 0.0)) }, Modifier.weight(1f))
                SettlementInput(layer.cc?.toString() ?: "", "Cc (Index)", { v -> onUpdate(layer.copy(cc = v.toDoubleOrNull())) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettlementResultCard(res: SettlementAnalysisEngine.SettlementResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (res.isSafe) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Analysis Results", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            HorizontalDivider()
            
            ResultRow("Immediate Settlement", "${"%.2f".format(res.immediateSettlement)} mm")
            ResultRow("Consolidation Settlement", "${"%.2f".format(res.consolidationSettlement)} mm")
            
            HorizontalDivider()
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Settlement", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${"%.2f".format(res.totalSettlement)} mm", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red)
            }
            
            Text(if (res.isSafe) "✅ Within safe limits" else "❌ Exceeds limit!", fontWeight = FontWeight.Bold, color = if (res.isSafe) Color(0xFF2E7D32) else Color.Red)
        }
    }
}

@Composable
private fun SettlementInput(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}
