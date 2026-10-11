package com.civileg.app.ui.compose.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.civileg.app.db.DailyTrend
import com.civileg.app.ui.theme.ThemeColors

/**
 * رسم بياني لاتجاه الفحوصات اليومية (آخر 30 يوم).
 *
 * يرسم أعمدة متكدسة تعكس نسبة النجاح/الرسوب لكل يوم على المحور Y بالتسبة المئوية،
 * مع خط اتجاه متقطع لنسبة النجاح. الترتيب زمني من الأقدم إلى الأحدث.
 */
@Composable
fun DailyTrendChart(
    trend: List<DailyTrend>,
    modifier: Modifier = Modifier
) {
    val safe = ThemeColors.safeColor()
    val unsafe = ThemeColors.unsafeColor()
    val trendColor = MaterialTheme.colorScheme.primary

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "الاتجاه التاريخي (آخر 30 يوم)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "نسبة النجاح اليومية مقارنة بالفارق",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            if (trend.isEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "لا توجد بيانات اتجاه بعد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                val chronological = trend.reversed()
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    val cw = size.width
                    val ch = size.height
                    val padL = 44f
                    val padR = 12f
                    val padT = 18f
                    val padB = 28f
                    val plotW = cw - padL - padR
                    val plotH = ch - padT - padB

                    val n = chronological.size
                    val slot = plotW / n
                    val barW = (slot * 0.62f).coerceAtMost(28f)

                    fun yOf(pct: Double): Float =
                        padT + (plotH - (pct / 100.0) * plotH).toFloat()

                    // 1. شبكة أفقية ونسب على المحور
                    val gridPaint = android.graphics.Paint().apply {
                        textSize = 26f
                        color = android.graphics.Color.GRAY
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    for (i in 0..4) {
                        val pct = i * 25
                        val y = yOf(pct.toDouble())
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.18f),
                            start = Offset(padL, y),
                            end = Offset(cw - padR, y),
                            strokeWidth = 1f
                        )
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "100%", 28f, yOf(100.0) + 8f, gridPaint
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "50%", 34f, yOf(50.0) + 8f, gridPaint
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "0%", 30f, yOf(0.0) + 8f, gridPaint
                    )

                    // 2. الأعمدة (نجاح في الأسفل، رسوب في الأعلى)
                    chronological.forEachIndexed { index, day ->
                        val pass = day.passRate.coerceIn(0.0, 100.0)
                        val x = padL + slot * index + (slot - barW) / 2f
                        val yPassBottom = yOf(0.0)
                        val yPassTop = yOf(pass)
                        val yFailTop = yOf(100.0)

                        drawRoundRect(
                            color = safe,
                            topLeft = Offset(x, yPassTop),
                            size = Size(barW, yPassBottom - yPassTop),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                        if (yFailTop < yPassTop) {
                            drawRoundRect(
                                color = unsafe,
                                topLeft = Offset(x, yFailTop),
                                size = Size(barW, yPassTop - yFailTop),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }
                    }

                    // 3. خط اتجاه نسبة النجاح
                    if (n >= 2) {
                        val trendPath = Path()
                        chronological.forEachIndexed { index, day ->
                            val x = padL + slot * index + slot / 2f
                            val y = yOf(day.passRate.coerceIn(0.0, 100.0))
                            if (index == 0) trendPath.moveTo(x, y) else trendPath.lineTo(x, y)
                        }
                        drawPath(
                            path = trendPath,
                            color = trendColor,
                            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // 4. تسميات المحور الأفقي (تاريخ متباعد)
                    val labelPaint = android.graphics.Paint().apply {
                        textSize = 24f
                        color = android.graphics.Color.GRAY
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val labelIndices = when {
                        n <= 3 -> (0 until n).toList()
                        n <= 8 -> listOf(0, n / 2, n - 1)
                        else -> listOf(0, n / 2, n - 1)
                    }
                    labelIndices.forEach { idx ->
                        val x = padL + slot * idx + slot / 2f
                        val label = chronological[idx].date.takeLast(5)
                        drawContext.canvas.nativeCanvas.drawText(
                            label, x, ch - 6f, labelPaint
                        )
                    }
                }

                // 5. مفتاح الألوان
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendDot(safe, "ناجح")
                    LegendDot(unsafe, "فاشل")
                    LegendDot(trendColor, "خط اتجاه النجاح")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "أعلى يوم: ${chronological.maxByOrNull { it.passRate }?.date ?: "—"}  (${String.format("%.0f%%", chronological.maxOfOrNull { it.passRate } ?: 0.0)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color = color)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}