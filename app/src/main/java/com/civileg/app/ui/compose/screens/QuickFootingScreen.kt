package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.FootingViewModel

/**
 * Professional Quick Footing Tool
 * A streamlined, single-screen calculator for isolated footings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickFootingScreen(
    viewModel: FootingViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    var load by remember { mutableStateOf("1200") }
    var soil by remember { mutableStateOf("150") }
    var fcu by remember { mutableStateOf("25") }
    var fy by remember { mutableStateOf("360") }
    var colB by remember { mutableStateOf("300") }
    var colD by remember { mutableStateOf("600") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Isolated Footing", fontWeight = FontWeight.Bold) },
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
            item {
                Text("Design Inputs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickInputField(load, "Axial Load (kN)", { load = it }, Modifier.weight(1f))
                    QuickInputField(soil, "Soil Capacity (kPa)", { soil = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickInputField(colB, "Col Width (mm)", { colB = it }, Modifier.weight(1f))
                    QuickInputField(colD, "Col Length (mm)", { colD = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickInputField(fcu, "fcu (MPa)", { fcu = it }, Modifier.weight(1f))
                    QuickInputField(fy, "fy (MPa)", { fy = it }, Modifier.weight(1f))
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.calculateFooting(
                            type = CalculatorEngine.FootingType.ISOLATED,
                            p = load.toDoubleOrNull() ?: 1200.0,
                            fcu = fcu.toDoubleOrNull() ?: 25.0,
                            fy = fy.toDoubleOrNull() ?: 360.0,
                            soil = soil.toDoubleOrNull() ?: 150.0,
                            colB = colB.toDoubleOrNull() ?: 300.0,
                            colT = colD.toDoubleOrNull() ?: 600.0,
                            code = CalculatorEngine.DesignCode.EGYPTIAN,
                            preferredDiameter = 16,
                            preferredSpacing = 150.0
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.White)
                    else {
                        Icon(Icons.Default.Calculate, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Calculate Professional Result")
                    }
                }
            }

            result?.let { res ->
                item {
                    Text("Design Results", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ResultItem("Footing Dimensions", "${res.width.toInt()} x ${res.length.toInt()} mm")
                            ResultItem("Thickness", "${res.thickness.toInt()} mm")
                            ResultItem("Reinforcement", res.reinforcementBottom.barString)
                            ResultItem("Concrete Volume", "${"%.2f".format(res.concreteVolume)} m³")
                            ResultItem("Steel Weight", "${"%.1f".format(res.steelWeight)} kg")
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (res.isSafe) Icons.Default.Layers else Icons.Default.Layers, // placeholder
                                    null, tint = if (res.isSafe) Color(0xFF2E7D32) else Color.Red
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (res.isSafe) "SAFE DESIGN" else "UNSAFE - REVIEW", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun ResultItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
