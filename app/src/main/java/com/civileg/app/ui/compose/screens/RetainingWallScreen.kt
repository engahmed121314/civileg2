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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.RetainingWallViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetainingWallScreen(
    viewModel: RetainingWallViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val result by viewModel.result.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    
    var height by remember { mutableStateOf("4.0") }
    var soilDensity by remember { mutableStateOf("18.0") }
    var frictionAngle by remember { mutableStateOf("30.0") }
    var surcharge by remember { mutableStateOf("0.0") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم حوائط السند Pro", fontWeight = FontWeight.Bold) },
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
            item { SectionHeader("📐 أبعاد الحائط وخصائص التربة", R.drawable.ic_wall) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RetainingInputField(height, "ارتفاع الحائط (m)", { height = it }, Modifier.weight(1f))
                    RetainingInputField(soilDensity, "كثافة التربة (kN/m³)", { soilDensity = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RetainingInputField(frictionAngle, "زاوية الاحتكاك (deg)", { frictionAngle = it }, Modifier.weight(1f))
                    RetainingInputField(surcharge, "الحمل الإضافي (kN/m²)", { surcharge = it }, Modifier.weight(1f))
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.calculateRetainingWallPro(
                            height = height.toDoubleOrNull() ?: 4.0,
                            soilDensity = soilDensity.toDoubleOrNull() ?: 18.0,
                            frictionAngle = frictionAngle.toDoubleOrNull() ?: 30.0,
                            surcharge = surcharge.toDoubleOrNull() ?: 0.0,
                            fcu = 25.0,
                            fy = 360.0,
                            preferredDiameter = 16,
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
                        Text("تحليل وتصميم")
                    }
                }
            }

            result?.let { res ->
                item { SectionHeader("📊 نتائج التصميم", R.drawable.ic_calculator) }
                
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ResultRow("عرض القاعدة (Base)", "${res.baseWidth.toInt()} mm")
                            ResultRow("سمك الحائط (Stem)", "${res.stemThickness.toInt()} mm")
                            ResultRow("تسليح الظهر (Stem Steel)", res.stemSteel.barString)
                            ResultRow("تسليح القاعدة (Base Steel)", res.baseSteel.barString)
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                            res.safetyChecks.forEach { check ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(check.name, fontSize = 12.sp)
                                    Text(if (check.isSafe) "آمن ✅" else "غير آمن ❌", color = if (check.isSafe) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Text("🎨 مخطط الحائط وتوزيع التسليح", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    WallReinforcementDrawing(res, modifier = Modifier.fillMaxWidth().height(300.dp))
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun WallReinforcementDrawing(res: CalculatorEngine.RetainingWallResult, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().background(Color.White)) {
            val padding = 50f
            val canvasW = size.width - 2*padding
            val canvasH = size.height - 2*padding
            
            // Scaling factors
            val wallH = res.height * 1000.0 // in mm
            val baseW = res.baseWidth // in mm
            val scale = minOf(canvasH / wallH, canvasW / baseW).toFloat()
            
            val drawWallH = (wallH * scale).toFloat()
            val drawBaseW = (baseW * scale).toFloat()
            val drawStemW = (res.stemThickness * scale).toFloat()
            val drawBaseH = (500.0 * scale).toFloat() // Assume 500mm base height for drawing
            
            val startX = (size.width - drawBaseW) / 2
            val startY = size.height - padding - drawBaseH
            
            // Draw Base
            drawRect(Color.LightGray, Offset(startX, startY), Size(drawBaseW, drawBaseH))
            drawRect(Color.DarkGray, Offset(startX, startY), Size(drawBaseW, drawBaseH), style = Stroke(3f))
            
            // Draw Stem
            val stemX = startX + drawBaseW * 0.3f
            val stemY = startY - drawWallH
            drawRect(Color.LightGray, Offset(stemX, stemY), Size(drawStemW, drawWallH))
            drawRect(Color.DarkGray, Offset(stemX, stemY), Size(drawStemW, drawWallH), style = Stroke(3f))
            
            // Draw Main Reinforcement (Stem) - Red Line
            drawLine(Color.Red, Offset(stemX + drawStemW - 10f, stemY + 10f), Offset(stemX + drawStemW - 10f, startY + drawBaseH - 10f), strokeWidth = 4f)
            
            // Draw Base Reinforcement - Red Line
            drawLine(Color.Red, Offset(startX + 10f, startY + drawBaseH - 15f), Offset(startX + drawBaseW - 10f, startY + drawBaseH - 15f), strokeWidth = 3f)
            
            // Dimensions
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; textSize = 25f; textAlign = android.graphics.Paint.Align.CENTER }
                drawText("H=${res.height}m", stemX + drawStemW/2, stemY - 10f, paint)
                drawText("B=${res.baseWidth.toInt()}mm", startX + drawBaseW/2, startY + drawBaseH + 30f, paint)
            }
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
private fun RetainingInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
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
