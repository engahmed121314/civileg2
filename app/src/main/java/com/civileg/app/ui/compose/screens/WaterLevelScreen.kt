package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R
import androidx.compose.ui.res.stringResource

private data class LevelPoint(
    val pointName: String,
    val staffReading: Double,
    val rl: Double,
    val isBenchmark: Boolean = false,
    val isBackSight: Boolean = false,
    val isForeSight: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterLevelScreen(
    onNavigateBack: () -> Unit = {}
) {
    var benchmarkRL by remember { mutableStateOf("") }
    var backSight by remember { mutableStateOf("") }
    var numPoints by remember { mutableStateOf("3") }

    // Dynamic list of intermediate/future point readings
    var pointReadings by remember { mutableStateOf(listOf("", "", "")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_water_level_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Reset all inputs
                        benchmarkRL = ""
                        backSight = ""
                        numPoints = "3"
                        pointReadings = listOf("", "", "")
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header Card ──
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("أداة المساح engineer's level", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("حساب HI و RL للنقاط المختلفة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Step 1: Benchmark & Back Sight ──
            SectionLabel("📌 الخطوة ١: مستوى الرجوع والبصيرة الخلفية")

            OutlinedTextField(
                value = benchmarkRL,
                onValueChange = { benchmarkRL = it },
                label = { Text("مستوى الرجوم BM (م)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.GpsFixed, null, tint = Color(0xFF2E7D32)) }
            )

            OutlinedTextField(
                value = backSight,
                onValueChange = { backSight = it },
                label = { Text("قراءة البصيرة الخلفية BS (م)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Visibility, null, tint = Color(0xFF1565C0)) }
            )

            // ── Step 2: Number of Points ──
            SectionLabel("📌 الخطوة ٢: عدد النقاط")

            OutlinedTextField(
                value = numPoints,
                onValueChange = {
                    val n = it.toIntOrNull() ?: 1
                    val clamped = n.coerceIn(1, 20)
                    numPoints = clamped.toString()
                    // Adjust readings list size
                    pointReadings = if (pointReadings.size < clamped) {
                        pointReadings + List(clamped - pointReadings.size) { "" }
                    } else {
                        pointReadings.take(clamped)
                    }
                },
                label = { Text("عدد النقاط (قراءات الأمامية)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.FormatListNumbered, null) }
            )

            // ── Step 3: Staff Readings ──
            SectionLabel("📌 الخطوة ٣: قراءات المسطرة (FS)")

            pointReadings.forEachIndexed { index, reading ->
                OutlinedTextField(
                    value = reading,
                    onValueChange = { newValue ->
                        pointReadings = pointReadings.toMutableList().also { it[index] = newValue }
                    },
                    label = { Text("قراءة النقطة ${index + 1} FS (م)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Text(
                            text = "P${index + 1}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Results ──
            val bmValue = benchmarkRL.toDoubleOrNull()
            val bsValue = backSight.toDoubleOrNull()

            if (bmValue != null && bsValue != null) {
                val hi = bmValue + bsValue

                // Calculate all RLs
                val results = pointReadings.mapIndexed { index, reading ->
                    val fsValue = reading.toDoubleOrNull()
                    LevelPoint(
                        pointName = "P${index + 1}",
                        staffReading = fsValue ?: 0.0,
                        rl = if (fsValue != null) hi - fsValue else 0.0,
                        isBenchmark = false,
                        isBackSight = false,
                        isForeSight = true
                    )
                }

                // ── HI Card ──
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ارتفاع الجهاز HI", style = MaterialTheme.typography.labelMedium, color = Color(0xFF757575))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "%.3f م".format(hi),
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                color = Color(0xFF1B5E20)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("BM + BS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "%.3f + %.3f".format(bmValue, bsValue),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Icon(Icons.Default.Height, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(40.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Results Table ──
                Text("📋 جدول النتائج", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                // Table header
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TableHeaderCell("النقطة", Modifier.weight(1.2f))
                        TableHeaderCell("النوع", Modifier.weight(1f))
                        TableHeaderCell("القراءة", Modifier.weight(1f))
                        TableHeaderCell("RL (م)", Modifier.weight(1.2f))
                    }
                }

                // Benchmark row
                TableRow(
                    pointName = "BM",
                    typeLabel = "Benchmark",
                    typeColor = Color(0xFF2E7D32),
                    reading = "—",
                    rl = "%.3f".format(bmValue),
                    bgColor = Color(0xFF1B5E20).copy(alpha = 0.08f)
                )

                // Back Sight row
                TableRow(
                    pointName = "BS",
                    typeLabel = "بصيرة خلفية",
                    typeColor = Color(0xFF1565C0),
                    reading = "%.3f".format(bsValue),
                    rl = "—",
                    bgColor = Color(0xFF0D47A1).copy(alpha = 0.08f)
                )

                // Forward Sight rows
                results.forEachIndexed { index, point ->
                    val hasReading = pointReadings[index].toDoubleOrNull() != null
                    TableRow(
                        pointName = point.pointName,
                        typeLabel = "بصيرة أمامية",
                        typeColor = Color(0xFFE65100),
                        reading = if (hasReading) "%.3f".format(point.staffReading) else "—",
                        rl = if (hasReading) "%.3f".format(point.rl) else "—",
                        bgColor = if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                }

                // Table footer
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF37474F)),
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "HI = %.3f م | عدد النقاط: ${results.size}".format(hi),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Summary Card ──
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ملخص", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                        Spacer(modifier = Modifier.height(8.dp))

                        val validResults = results.filter { pointReadings[results.indexOf(it)].toDoubleOrNull() != null }
                        if (validResults.isNotEmpty()) {
                            val maxRL = validResults.maxOf { it.rl }
                            val minRL = validResults.minOf { it.rl }
                            val diff = maxRL - minRL

                            SummaryRow("أعلى RL", "%.3f م".format(maxRL))
                            SummaryRow("أدنى RL", "%.3f م".format(minRL))
                            SummaryRow("فرق المنسوب", "%.3f م".format(diff))
                        }
                    }
                }

                // ── Export Placeholder ──
                OutlinedButton(
                    onClick = { /* Future: Export to PDF */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تصدير PDF (قريباً)")
                }
            } else {
                // No results yet - show formula card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Straighten,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("أدخل البيانات لحساب المناسيب", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "HI = RL (BM) + قراءة البصيرة الخلفية\nRL (نقطة) = HI - قراءة البصيرة الأمامية",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = modifier
    )
}

@Composable
private fun TableRow(
    pointName: String,
    typeLabel: String,
    typeColor: Color,
    reading: String,
    rl: String,
    bgColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(pointName, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1.2f))
            Text(typeLabel, fontSize = 11.sp, color = typeColor, modifier = Modifier.weight(1f))
            Text(reading, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(rl, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}