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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.civileg.app.R
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.ColumnViewModel
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScreen(
    viewModel: ColumnViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصميم الأعمدة الخرسانية Pro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                    }
                }
            }
            
            item { SectionHeader("📐 أبعاد المقطع والخصائص", R.drawable.ic_column) }
            
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColumnInputField(uiState.width.toString(), "العرض b (mm)", { viewModel.updateInputs(width = it.toDoubleOrNull() ?: 0.0) }, Modifier.weight(1f))
                    ColumnInputField(uiState.depth.toString(), "العمق t (mm)", { viewModel.updateInputs(depth = it.toDoubleOrNull() ?: 0.0) }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColumnInputField(uiState.fcu.toString(), "fcu (MPa)", { viewModel.updateInputs(fcu = it.toDoubleOrNull() ?: 0.0) }, Modifier.weight(1f))
                    ColumnInputField(uiState.fy.toString(), "fy (MPa)", { viewModel.updateInputs(fy = it.toDoubleOrNull() ?: 0.0) }, Modifier.weight(1f))
                }
            }

            item { SectionHeader("⚡ الأحمال المطبقة", R.drawable.ic_design) }
            
            item {
                ColumnInputField(uiState.axialLoad.toString(), "الحمل المحوري Pu (kN)", { viewModel.updateInputs(axialLoad = it.toDoubleOrNull() ?: 0.0) }, Modifier.fillMaxWidth())
            }

            uiState.result?.let { result ->
                item { SectionHeader("📊 نتائج التصميم والتحقق", R.drawable.ic_calculator) }
                
                item { ColumnResultCard(result) }

                item {
                    Text("📝 المعادلات الهندسية (بدون نتائج)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    EngineeringFormulasCard()
                }

                item {
                    Text("🎨 الرسم الهندسي للقطاع", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ColumnSectionCanvas(result)
                }
            }
            
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
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
            
            result.safetyChecks.forEach { check ->
                ResultCheckRow(check)
            }
        }
    }
}

@Composable
private fun ResultCheckRow(check: CalculatorEngine.DesignSafetyCheck) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(check.name, fontSize = 14.sp)
        Text(
            if (check.isSafe) "آمن ✅" else "غير آمن ❌",
            color = if (check.isSafe) Color(0xFF2E7D32) else Color(0xFFC62828),
            fontWeight = FontWeight.Bold
        )
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
private fun ColumnSectionCanvas(result: CalculatorEngine.ColumnResult) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(300.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().background(Color.White)) {
            val padding = 60f
            val scale = minOf((size.width - 2*padding)/result.width, (size.height - 2*padding)/result.depth).toFloat()
            
            val w = (result.width * scale).toFloat()
            val h = (result.depth * scale).toFloat()
            val left = (size.width - w) / 2
            val top = (size.height - h) / 2
            
            // الخرسانة
            drawRect(Color.LightGray, Offset(left, top), Size(w, h))
            drawRect(Color.DarkGray, Offset(left, top), Size(w, h), style = Stroke(4f))
            
            // الكانات
            val cover = 25f * scale
            drawRect(Color.Blue.copy(alpha = 0.7f), Offset(left + cover, top + cover), Size(w - 2*cover, h - 2*cover), style = Stroke(3f))
            
            // حديد التسليح
            val barRadius = 8f
            val nBars = result.reinforcement.numBars
            val barsSideX = max(2, (result.width / (result.width + result.depth) * nBars / 2).toInt() + 1)
            val barsSideY = max(2, (result.depth / (result.width + result.depth) * nBars / 2).toInt() + 1)
            
            for (i in 0 until barsSideX) {
                val x = left + cover + i * (w - 2*cover) / (barsSideX - 1)
                drawCircle(Color.Black, barRadius, Offset(x, top + cover))
                drawCircle(Color.Black, barRadius, Offset(x, top + h - cover))
            }
            for (i in 1 until barsSideY - 1) {
                val y = top + cover + i * (h - 2*cover) / (barsSideY - 1)
                drawCircle(Color.Black, barRadius, Offset(left + cover, y))
                drawCircle(Color.Black, barRadius, Offset(left + w - cover, y))
            }
            
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 35f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText("${result.width.toInt()} mm", size.width/2, top - 20f, paint)
                
                val midY = size.height/2
                val paintVert = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 35f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                save()
                rotate(-90f, left - 30f, midY)
                drawText("${result.depth.toInt()} mm", left - 30f, midY, paintVert)
                restore()
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
private fun ColumnInputField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier) {
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
    Text("اختر كود التصميم", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DesignCode.values().forEach { code ->
            FilterChip(
                selected = code == selectedCode,
                onClick = { onCodeSelected(code) },
                label = { Text(code.displayName) }
            )
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
