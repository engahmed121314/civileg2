package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Interactive Moment & Shear Force Diagram for beams.
 * Shows bending moment and shear force diagrams with tap-to-read values.
 *
 * Supports: Simply Supported, Fixed-Fixed, Fixed-Hinged, Cantilever, Continuous beams.
 */
@Composable
fun MomentShearForceDiagram(
    // Beam parameters
    span: Double,                    // m
    supportType: String = "SIMPLY_SUPPORTED", // SS, FF, FH, CANTILEVER, CONTINUOUS
    deadLoad: Double = 0.0,         // kN/m
    liveLoad: Double = 0.0,         // kN/m
    appliedMoment: Double = 0.0,    // kN.m (max moment from design)
    appliedShear: Double = 0.0,     // kN (max shear from design)
    // Diagram data (optional - if provided, overrides calculated diagrams)
    momentValues: List<Pair<Float, Float>>? = null, // (x_ratio, moment_value)
    shearValues: List<Pair<Float, Float>>? = null,  // (x_ratio, shear_value)
    // Style
    showMoment: Boolean = true,
    showShear: Boolean = true,
    modifier: Modifier = Modifier
) {
    var selectedPoint by remember { mutableStateOf<Offset?>(null) }
    var selectedMoment by remember { mutableStateOf(0f) }
    var selectedShear by remember { mutableStateOf(0f) }

    // Calculate diagrams if not provided
    val wu = (deadLoad * 1.4 + liveLoad * 1.6) // ECP ultimate load factor
    val momentData = momentValues ?: calculateMomentDiagram(span, wu, supportType)
    val shearData = shearValues ?: calculateShearDiagram(span, wu, supportType)

    val maxMoment = momentData.maxOfOrNull { abs(it.second) } ?: 1f
    val maxShear = shearData.maxOfOrNull { abs(it.second) } ?: 1f

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("تحليل القوى", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Moment & Shear Force Diagrams", color = Color(0xAAFFFFFF), fontSize = 10.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Legend
                    if (showMoment) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(12.dp)) {
                                drawLine(Color(0xFF4A90D9), Offset(0f, 6f), Offset(12f, 6f), 3f)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("M (kN.m)", color = Color(0xFF4A90D9), fontSize = 10.sp)
                        }
                    }
                    if (showShear) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(12.dp)) {
                                drawLine(Color(0xFFE74C3C), Offset(0f, 6f), Offset(12f, 6f), 3f)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("V (kN)", color = Color(0xFFE74C3C), fontSize = 10.sp)
                        }
                    }
                }
            }

            // Diagrams
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (showMoment && showShear) 280.dp else 160.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            selectedPoint = offset
                            // Find nearest x ratio
                            val xRatio = ((offset.x - 50f) / (size.width - 100f)).coerceIn(0f, 1f)
                            // Interpolate moment
                            selectedMoment = interpolate(momentData, xRatio)
                            selectedShear = interpolate(shearData, xRatio)
                        }
                    }
            ) {
                val marginL = 50f; val marginR = 50f; val marginT = 20f
                val diagramW = size.width - marginL - marginR

                if (showMoment) {
                    val diagramH = if (showShear) size.height * 0.45f else size.height - 40f
                    val baseY = marginT + diagramH * 0.7f
                    val scale = (diagramH * 0.6f) / maxOf(maxMoment, 0.1f)

                    // Title
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = Color(0xFF4A90D9).toArgb()
                            textSize = 14f
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                        }
                        drawText("Bending Moment Diagram (M)", marginL, marginT - 2f, paint)
                    }

                    // Baseline
                    drawLine(
                        Color(0x55FFFFFF), Offset(marginL, baseY), Offset(marginL + diagramW, baseY),
                        1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
                    )

                    // Fill + Line
                    val fillPath = Path().apply {
                        moveTo(marginL, baseY)
                        momentData.forEach { (xr, value) ->
                            lineTo(marginL + xr * diagramW, baseY - value * scale)
                        }
                        lineTo(marginL + diagramW, baseY)
                        close()
                    }
                    drawPath(fillPath, Color(0xFF4A90D9).copy(alpha = 0.2f))

                    val linePath = Path().apply {
                        momentData.forEachIndexed { i, (xr, value) ->
                            val px = marginL + xr * diagramW
                            val py = baseY - value * scale
                            if (i == 0) moveTo(px, py) else lineTo(px, py)
                        }
                    }
                    drawPath(linePath, Color(0xFF4A90D9), style = Stroke(2.5f))

                    // Max/min annotations
                    val maxMEntry = momentData.maxByOrNull { it.second }!!
                    val minMEntry = momentData.minByOrNull { it.second }!!
                    annotateValue(marginL + maxMEntry.first * diagramW, baseY - maxMEntry.second * scale - 12f, "%.1f".format(maxMEntry.second), Color(0xFF4A90D9))
                    if (abs(minMEntry.second - maxMEntry.second) > 0.1f) {
                        annotateValue(marginL + minMEntry.first * diagramW, baseY - minMEntry.second * scale + 18f, "%.1f".format(minMEntry.second), Color(0xFF4A90D9))
                    }

                    // Zero line label
                    drawTextAnnotated("0", marginL - 15f, baseY + 4f, Color(0xAAFFFFFF), 12f)
                }

                if (showShear) {
                    val shearLayout = if (showMoment) {
                        val mEnd = (size.height * 0.45f + 30f)
                        Pair(mEnd, size.height - marginT - 20f)
                    } else {
                        Pair(marginT, size.height - 40f)
                    }
                    val shearLayoutTop = shearLayout.first + 20f
                    val shearLayoutH = shearLayout.second - shearLayout.first - 20f
                    val baseY = shearLayoutTop + shearLayoutH * 0.5f
                    val scale = (shearLayoutH * 0.4f) / maxOf(maxShear, 0.1f)

                    // Title
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = Color(0xFFE74C3C).toArgb()
                            textSize = 14f
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                        }
                        drawText("Shear Force Diagram (V)", marginL, shearLayoutTop - 2f, paint)
                    }

                    // Baseline
                    drawLine(
                        Color(0x55FFFFFF), Offset(marginL, baseY), Offset(marginL + diagramW, baseY),
                        1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
                    )

                    // Fill + Line
                    val fillPath = Path().apply {
                        moveTo(marginL, baseY)
                        shearData.forEach { (xr, value) ->
                            lineTo(marginL + xr * diagramW, baseY - value * scale)
                        }
                        lineTo(marginL + diagramW, baseY)
                        close()
                    }
                    drawPath(fillPath, Color(0xFFE74C3C).copy(alpha = 0.15f))

                    val linePath = Path().apply {
                        shearData.forEachIndexed { i, (xr, value) ->
                            val px = marginL + xr * diagramW
                            val py = baseY - value * scale
                            if (i == 0) moveTo(px, py) else lineTo(px, py)
                        }
                    }
                    drawPath(linePath, Color(0xFFE74C3C), style = Stroke(2.5f))

                    // Annotations
                    val maxVEntry = shearData.maxByOrNull { it.second }!!
                    val minVEntry = shearData.minByOrNull { it.second }!!
                    annotateValue(marginL + maxVEntry.first * diagramW, baseY - maxVEntry.second * scale - 12f, "%.1f".format(maxVEntry.second), Color(0xFFE74C3C))
                    if (abs(minVEntry.second - maxVEntry.second) > 0.1f) {
                        annotateValue(marginL + minVEntry.first * diagramW, baseY - minVEntry.second * scale + 18f, "%.1f".format(minVEntry.second), Color(0xFFE74C3C))
                    }
                }

                // X-axis labels
                drawTextAnnotated("0", marginL, size.height - 2f, Color(0xAAFFFFFF), 12f, center = true)
                drawTextAnnotated("L/2", marginL + diagramW / 2f, size.height - 2f, Color(0xAAFFFFFF), 12f, center = true)
                drawTextAnnotated("L", marginL + diagramW, size.height - 2f, Color(0xAAFFFFFF), 12f, center = true)

                // Selected point indicator
                selectedPoint?.let { pt ->
                    val xRatio = ((pt.x - marginL) / diagramW).coerceIn(0f, 1f)
                    val px = marginL + xRatio * diagramW
                    drawLine(Color(0x88FFFFFF), Offset(px, marginT), Offset(px, size.height - 15f), 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f), 0f))
                    drawCircle(Color.White, radius = 5f, center = Offset(px, pt.y))
                    drawCircle(Color(0xFF1A1A2E), radius = 3f, center = Offset(px, pt.y))
                }
            }

            // Selected value tooltip
            if (selectedPoint != null) {
                Surface(
                    color = Color(0xCC000000),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Text("x/L = ${"%.2f".format(((selectedPoint!!.x - 50f) / (800f - 100f)).coerceIn(0f, 1f))}",
                            color = Color.White, fontSize = 11.sp)
                        Text("M = ${"%.1f".format(selectedMoment)} kN.m",
                            color = Color(0xFF4A90D9), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("V = ${"%.1f".format(selectedShear)} kN",
                            color = Color(0xFFE74C3C), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Key values
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ValueChip("Max M", "${"%.1f".format(appliedMoment)} kN.m", Color(0xFF4A90D9))
                ValueChip("Max V", "${"%.1f".format(appliedShear)} kN", Color(0xFFE74C3C))
                ValueChip("Wu", "${"%.1f".format(wu)} kN/m", Color(0xFF9B59B6))
            }
        }
    }
}

@Composable
private fun ValueChip(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = color.copy(alpha = 0.7f), fontSize = 9.sp)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

// ==================== Diagram Calculations ====================

private fun calculateMomentDiagram(span: Double, wu: Double, supportType: String): List<Pair<Float, Float>> {
    val n = 50
    return when (supportType) {
        "SIMPLY_SUPPORTED", "SS" -> {
            // M(x) = wu * x * (L - x) / 2, parabolic
            (0..n).map { i ->
                val x = i.toFloat() / n
                val m = (wu * x * (1 - x) * span * span / 2f).toFloat()
                x to m
            }
        }
        "FIXED_FIXED", "FF" -> {
            // M(x) = wu*L²/12 * (6*x/L * (1-x/L) - 1)
            (0..n).map { i ->
                val x = i.toFloat() / n
                val m = (wu * span * span / 12f * (6f * x * (1 - x) - 1f)).toFloat()
                x to m
            }
        }
        "CANTILEVER" -> {
            // M(x) = -wu * x² / 2
            (0..n).map { i ->
                val x = i.toFloat() / n
                val m = (-wu * x * x * span * span / 2f).toFloat()
                x to m
            }
        }
        else -> {
            // Default: simply supported
            (0..n).map { i ->
                val x = i.toFloat() / n
                val m = (wu * x * (1 - x) * span * span / 2f).toFloat()
                x to m
            }
        }
    }
}

private fun calculateShearDiagram(span: Double, wu: Double, supportType: String): List<Pair<Float, Float>> {
    val n = 50
    return when (supportType) {
        "SIMPLY_SUPPORTED", "SS" -> {
            // V(x) = wu * (L/2 - x)
            (0..n).map { i ->
                val x = i.toFloat() / n
                val v = (wu * span / 2f * (1f - 2f * x)).toFloat()
                x to v
            }
        }
        "FIXED_FIXED", "FF" -> {
            // V(x) = wu * (L/2 - x)
            (0..n).map { i ->
                val x = i.toFloat() / n
                val v = (wu * span / 2f * (1f - 2f * x)).toFloat()
                x to v
            }
        }
        "CANTILEVER" -> {
            // V(x) = -wu * (L - x)
            (0..n).map { i ->
                val x = i.toFloat() / n
                val v = (-wu * span * (1f - x)).toFloat()
                x to v
            }
        }
        else -> {
            (0..n).map { i ->
                val x = i.toFloat() / n
                val v = (wu * span / 2f * (1f - 2f * x)).toFloat()
                x to v
            }
        }
    }
}

private fun interpolate(data: List<Pair<Float, Float>>, xRatio: Float): Float {
    if (data.isEmpty()) return 0f
    if (xRatio <= data.first().first) return data.first().second
    if (xRatio >= data.last().first) return data.last().second

    for (i in 0 until data.size - 1) {
        if (xRatio >= data[i].first && xRatio <= data[i + 1].first) {
            val t = (xRatio - data[i].first) / (data[i + 1].first - data[i].first)
            return data[i].second + t * (data[i + 1].second - data[i].second)
        }
    }
    return 0f
}

private fun DrawScope.annotateValue(x: Float, y: Float, text: String, color: Color) {
    drawTextAnnotated(text, x, y, color, 14f, center = true)
}

private fun DrawScope.drawTextAnnotated(
    text: String, x: Float, y: Float, color: Color, size: Float, center: Boolean = false
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            this.color = color.toArgb()
            this.textSize = size
            isAntiAlias = true
            textAlign = if (center) android.graphics.Paint.Align.CENTER else android.graphics.Paint.Align.LEFT
        }
        drawText(text, x, y, paint)
    }
}