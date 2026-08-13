package com.civileg.app.ui.compose.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
                        Text(stringResource(R.string.water_level_tool), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(stringResource(R.string.water_level_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Step 1: Benchmark & Back Sight ──
            SectionLabel(stringResource(R.string.water_level_step1))

            OutlinedTextField(
                value = benchmarkRL,
                onValueChange = { benchmarkRL = it },
                label = { Text(stringResource(R.string.water_level_bm)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.GpsFixed, null, tint = Color(0xFF2E7D32)) }
            )

            OutlinedTextField(
                value = backSight,
                onValueChange = { backSight = it },
                label = { Text(stringResource(R.string.water_level_bs)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Visibility, null, tint = Color(0xFF1565C0)) }
            )

            // ── Step 2: Number of Points ──
            SectionLabel(stringResource(R.string.water_level_step2))

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
                label = { Text(stringResource(R.string.water_level_num_points)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.FormatListNumbered, null) }
            )

            // ── Step 3: Staff Readings ──
            SectionLabel(stringResource(R.string.water_level_step3))

            pointReadings.forEachIndexed { index, reading ->
                OutlinedTextField(
                    value = reading,
                    onValueChange = { newValue ->
                        pointReadings = pointReadings.toMutableList().also { it[index] = newValue }
                    },
                    label = { Text(stringResource(R.string.water_level_fs_n, index + 1)) },
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
                            Text(stringResource(R.string.water_level_hi), style = MaterialTheme.typography.labelMedium, color = Color(0xFF757575))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.water_level_value_m, hi),
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
                Text(stringResource(R.string.water_level_results_table), fontWeight = FontWeight.Bold, fontSize = 16.sp)

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
                        TableHeaderCell(stringResource(R.string.water_level_point), Modifier.weight(1.2f))
                        TableHeaderCell(stringResource(R.string.water_level_type), Modifier.weight(1f))
                        TableHeaderCell(stringResource(R.string.water_level_reading), Modifier.weight(1f))
                        TableHeaderCell(stringResource(R.string.water_level_rl), Modifier.weight(1.2f))
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
                    typeLabel = stringResource(R.string.water_level_backsight),
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
                        typeLabel = stringResource(R.string.water_level_foresight),
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
                            stringResource(R.string.water_level_hi_summary, hi, results.size),
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
                        Text(stringResource(R.string.water_level_summary), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                        Spacer(modifier = Modifier.height(8.dp))

                        val validResults = results.filter { pointReadings[results.indexOf(it)].toDoubleOrNull() != null }
                        if (validResults.isNotEmpty()) {
                            val maxRL = validResults.maxOf { it.rl }
                            val minRL = validResults.minOf { it.rl }
                            val diff = maxRL - minRL

                            SummaryRow(stringResource(R.string.water_level_max_rl), stringResource(R.string.water_level_value_m, maxRL))
                            SummaryRow(stringResource(R.string.water_level_min_rl), stringResource(R.string.water_level_value_m, minRL))
                            SummaryRow(stringResource(R.string.water_level_diff), stringResource(R.string.water_level_value_m, diff))
                        }
                    }
                }

                // ── Export to PDF ──
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            exportWaterLevelPdf(
                                context = context,
                                benchmarkRL = bmValue,
                                backSight = bsValue,
                                hi = hi,
                                pointReadings = results
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.water_level_export_pdf))
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
                        Text(stringResource(R.string.water_level_enter_data), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.water_level_formula_hint),
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

// ========== PDF Export ==========

/**
 * Export water-level survey data to a simple PDF using Android's native PdfDocument.
 * Draws a title, summary info (BM, BS, HI), and a table of all reduced levels.
 */
private suspend fun exportWaterLevelPdf(
    context: Context,
    benchmarkRL: Double,
    backSight: Double,
    hi: Double,
    pointReadings: List<LevelPoint>
) {
    withContext(Dispatchers.IO) {
        try {
            val fileName = "Water_Level_Survey_${System.currentTimeMillis()}.pdf"
            val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                ?: context.cacheDir
            directory.mkdirs()
            val file = File(directory, fileName)

            val pageWidth = 595  // A4 width in points
            val pageHeight = 842 // A4 height in points
            val margin = 36f

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // ── Paints ──
            val titlePaint = Paint().apply {
                textSize = 18f
                isFakeBoldText = true
                color = AndroidColor.rgb(21, 101, 192)
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }
            val headerPaint = Paint().apply {
                textSize = 11f
                isFakeBoldText = true
                color = AndroidColor.WHITE
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }
            val cellPaint = Paint().apply {
                textSize = 10f
                color = AndroidColor.rgb(33, 33, 33)
                textAlign = Paint.Align.CENTER
            }
            val labelPaint = Paint().apply {
                textSize = 12f
                isFakeBoldText = true
                color = AndroidColor.rgb(33, 33, 33)
            }
            val valuePaint = Paint().apply {
                textSize = 12f
                color = AndroidColor.rgb(33, 33, 33)
            }
            val linePaint = Paint().apply {
                strokeWidth = 1f
                color = AndroidColor.rgb(150, 150, 150)
            }

            var y = margin + 20f

            // ── Title ──
            canvas.drawText("Water Level Survey Report", pageWidth / 2f, y, titlePaint)
            y += 30f

            // ── Summary ──
            canvas.drawText("Benchmark RL: ", margin, y, labelPaint)
            canvas.drawText("%.3f m".format(benchmarkRL), margin + 120f, y, valuePaint)
            y += 20f
            canvas.drawText("Back Sight: ", margin, y, labelPaint)
            canvas.drawText("%.3f m".format(backSight), margin + 120f, y, valuePaint)
            y += 20f
            canvas.drawText("Height of Instrument (HI): ", margin, y, labelPaint)
            canvas.drawText("%.3f m".format(hi), margin + 200f, y, valuePaint)
            y += 30f

            // ── Table ──
            val colX = floatArrayOf(
                margin,                           // Point
                margin + 100f,                    // Type
                margin + 220f,                    // Staff Reading
                margin + 340f,                    // RL
                margin + 440f                     // right edge
            )
            val rowHeight = 24f

            // Header row
            val headerBg = Paint().apply { color = AndroidColor.rgb(21, 101, 192) }
            canvas.drawRect(colX[0], y, colX[4], y + rowHeight, headerBg)
            canvas.drawText("Point", (colX[0] + colX[1]) / 2f, y + 16f, headerPaint)
            canvas.drawText("Type", (colX[1] + colX[2]) / 2f, y + 16f, headerPaint)
            canvas.drawText("Reading (m)", (colX[2] + colX[3]) / 2f, y + 16f, headerPaint)
            canvas.drawText("RL (m)", (colX[3] + colX[4]) / 2f, y + 16f, headerPaint)
            y += rowHeight

            // Row helper
            fun drawRow(point: String, type: String, reading: String, rl: String, bgColor: Int) {
                if (y + rowHeight > pageHeight - margin) return
                val bgPaint = Paint().apply { color = bgColor }
                canvas.drawRect(colX[0], y, colX[4], y + rowHeight, bgPaint)
                canvas.drawText(point, (colX[0] + colX[1]) / 2f, y + 16f, cellPaint)
                canvas.drawText(type, (colX[1] + colX[2]) / 2f, y + 16f, cellPaint)
                canvas.drawText(reading, (colX[2] + colX[3]) / 2f, y + 16f, cellPaint)
                canvas.drawText(rl, (colX[3] + colX[4]) / 2f, y + 16f, cellPaint)
                // Cell borders
                for (x in colX) {
                    canvas.drawLine(x, y, x, y + rowHeight, linePaint)
                }
                canvas.drawLine(colX[0], y, colX[4], y, linePaint)
                y += rowHeight
            }

            // Benchmark row
            drawRow("BM", "Benchmark", "—", "%.3f".format(benchmarkRL), AndroidColor.rgb(232, 245, 253))

            // Back Sight row
            drawRow("BS", "Back Sight", "%.3f".format(backSight), "—", AndroidColor.rgb(227, 242, 253))

            // Forward Sight rows
            pointReadings.forEachIndexed { index, point ->
                val hasReading = point.staffReading != 0.0 || pointReadings.getOrNull(index)?.staffReading != 0.0
                val bgColor = if (index % 2 == 0) AndroidColor.WHITE else AndroidColor.rgb(245, 245, 245)
                drawRow(
                    point = point.pointName,
                    type = "Fore Sight",
                    reading = if (hasReading) "%.3f".format(point.staffReading) else "—",
                    rl = if (hasReading) "%.3f".format(point.rl) else "—",
                    bgColor = bgColor
                )
            }

            // ── Footer ──
            y += 10f
            if (y < pageHeight - margin) {
                val validResults = pointReadings.filter { it.staffReading != 0.0 }
                if (validResults.isNotEmpty()) {
                    val maxRL = validResults.maxOf { it.rl }
                    val minRL = validResults.minOf { it.rl }
                    canvas.drawText("Max RL: %.3f m  |  Min RL: %.3f m  |  Difference: %.3f m".format(maxRL, minRL, maxRL - minRL), margin, y, labelPaint)
                }
            }

            pdfDocument.finishPage(page)
            pdfDocument.close()

            // Open the PDF
            com.civileg.app.utils.ExportUtils.openPdf(context, file)
        } catch (e: Exception) {
            android.util.Log.e("WaterLevelScreen", "PDF export failed", e)
        }
    }
}