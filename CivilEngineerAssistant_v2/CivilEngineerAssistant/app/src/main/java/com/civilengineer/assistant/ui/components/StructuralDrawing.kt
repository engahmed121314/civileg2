package com.civilengineer.assistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civilengineer.assistant.ui.theme.*
import kotlin.math.abs
import kotlin.math.max

// ═══════════════════════════════════════════════════════════
// رسم مقطع العمود مع التسليح
// Column Cross Section Drawing
// ═══════════════════════════════════════════════════════════

@Composable
fun ColumnCrossSectionDrawing(
    width: Double,          // mm
    height: Double,         // mm
    numberOfBars: Int,
    barDiameter: Double,    // mm
    stirrupDiameter: Double,// mm
    cover: Double = 25.0,   // mm
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, PrimaryBlueVeryLight, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("مقطع العمود", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PrimaryBlueDark)
        Text("${width.toInt()} × ${height.toInt()} mm", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val canvasW = size.width
            val canvasH = size.height
            val padding = 30f

            // نسبة الرسم
            val maxDim = max(width, height)
            val scale = (minOf(canvasW, canvasH) - 2 * padding) / maxDim.toFloat()
            val drawW = (width * scale).toFloat()
            val drawH = (height * scale).toFloat()
            val offsetX = (canvasW - drawW) / 2
            val offsetY = (canvasH - drawH) / 2

            // رسم الخرسانة (المستطيل الخارجي)
            drawRect(
                color = Color(0xFFE0E0E0),
                topLeft = Offset(offsetX, offsetY),
                size = Size(drawW, drawH)
            )
            drawRect(
                color = PrimaryBlueDark,
                topLeft = Offset(offsetX, offsetY),
                size = Size(drawW, drawH),
                style = Stroke(width = 3f)
            )

            // رسم الكانة
            val coverScaled = (cover * scale).toFloat()
            val stirrupInset = coverScaled
            drawRect(
                color = SafeGreen,
                topLeft = Offset(offsetX + stirrupInset, offsetY + stirrupInset),
                size = Size(drawW - 2 * stirrupInset, drawH - 2 * stirrupInset),
                style = Stroke(width = 2f)
            )

            // رسم أسياخ التسليح
            val barRadius = ((barDiameter / 2) * scale).toFloat().coerceAtLeast(4f)
            val barInset = stirrupInset + (stirrupDiameter * scale).toFloat() + barRadius

            // توزيع الأسياخ
            if (numberOfBars >= 4) {
                val cornersAndSides = distributeBarPositions(
                    numberOfBars, drawW, drawH, offsetX, offsetY, barInset
                )
                cornersAndSides.forEach { (bx, by) ->
                    drawCircle(
                        color = UnsafeRed,
                        radius = barRadius,
                        center = Offset(bx, by)
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = barRadius,
                        center = Offset(bx, by),
                        style = Stroke(width = 1f)
                    )
                }
            }

            // كتابة الأبعاد
            val textPaint = android.graphics.Paint().apply {
                textSize = 24f
                color = android.graphics.Color.parseColor("#0D47A1")
                textAlign = android.graphics.Paint.Align.CENTER
            }

            // عرض
            drawContext.canvas.nativeCanvas.drawText(
                "${width.toInt()}",
                canvasW / 2,
                offsetY + drawH + 25f,
                textPaint
            )

            // ارتفاع
            val vTextPaint = android.graphics.Paint(textPaint).apply {
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(-90f, offsetX - 15f, canvasH / 2)
            drawContext.canvas.nativeCanvas.drawText(
                "${height.toInt()}",
                offsetX - 15f,
                canvasH / 2,
                vTextPaint
            )
            drawContext.canvas.nativeCanvas.restore()
        }

        Text(
            "${numberOfBars}φ${barDiameter.toInt()} + كانات φ${stirrupDiameter.toInt()}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = PrimaryBlue
        )
    }
}

private fun distributeBarPositions(
    n: Int, w: Float, h: Float, ox: Float, oy: Float, inset: Float
): List<Pair<Float, Float>> {
    val positions = mutableListOf<Pair<Float, Float>>()

    if (n <= 4) {
        // 4 أركان فقط
        positions.add(Pair(ox + inset, oy + inset))
        positions.add(Pair(ox + w - inset, oy + inset))
        positions.add(Pair(ox + inset, oy + h - inset))
        positions.add(Pair(ox + w - inset, oy + h - inset))
        return positions.take(n)
    }

    // الأركان الأربعة
    positions.add(Pair(ox + inset, oy + inset))
    positions.add(Pair(ox + w - inset, oy + inset))
    positions.add(Pair(ox + w - inset, oy + h - inset))
    positions.add(Pair(ox + inset, oy + h - inset))

    val remaining = n - 4
    // توزيع على الأضلاع (أعلى وأسفل أولاً)
    val topBottom = remaining / 2
    val leftRight = remaining - topBottom

    val topCount = (topBottom + 1) / 2
    val bottomCount = topBottom - topCount
    val leftCount = (leftRight + 1) / 2
    val rightCount = leftRight - leftCount

    // أعلى
    for (i in 1..topCount) {
        val x = ox + inset + (w - 2 * inset) * i / (topCount + 1)
        positions.add(Pair(x, oy + inset))
    }
    // أسفل
    for (i in 1..bottomCount) {
        val x = ox + inset + (w - 2 * inset) * i / (bottomCount + 1)
        positions.add(Pair(x, oy + h - inset))
    }
    // يسار
    for (i in 1..leftCount) {
        val y = oy + inset + (h - 2 * inset) * i / (leftCount + 1)
        positions.add(Pair(ox + inset, y))
    }
    // يمين
    for (i in 1..rightCount) {
        val y = oy + inset + (h - 2 * inset) * i / (rightCount + 1)
        positions.add(Pair(ox + w - inset, y))
    }

    return positions
}

// ═══════════════════════════════════════════════════════════
// رسم مخطط القص والعزم (SFD & BMD)
// Shear Force & Bending Moment Diagrams
// ═══════════════════════════════════════════════════════════

@Composable
fun ShearForceDiagram(
    span: Double,              // m
    maxShear: Double,          // kN
    loadType: String = "UDL",  // UDL, Point
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, PrimaryBlueVeryLight, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("مخطط قوة القص (SFD)", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = PrimaryBlueDark)

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val canvasW = size.width
            val canvasH = size.height
            val padding = 40f
            val plotW = canvasW - 2 * padding
            val plotH = canvasH - 2 * padding
            val midY = padding + plotH / 2

            // محور X (الكمرة)
            drawLine(Color.Black, Offset(padding, midY), Offset(padding + plotW, midY), strokeWidth = 2f)

            // مسند يسار
            drawTriangleSupport(padding, midY, 10f)
            // مسند يمين
            drawTriangleSupport(padding + plotW, midY, 10f)

            when (loadType) {
                "UDL" -> {
                    // SFD لحمل موزع: خط مائل من +V إلى -V
                    val topY = midY - (plotH / 2 * 0.8f)
                    val botY = midY + (plotH / 2 * 0.8f)

                    val path = Path().apply {
                        moveTo(padding, midY)
                        lineTo(padding, topY)
                        lineTo(padding + plotW, botY)
                        lineTo(padding + plotW, midY)
                        close()
                    }
                    drawPath(path, Color(0xFF1565C0).copy(alpha = 0.2f))
                    drawLine(PrimaryBlue, Offset(padding, topY), Offset(padding + plotW, botY), strokeWidth = 2.5f)

                    // قيم
                    val textPaint = android.graphics.Paint().apply {
                        textSize = 20f
                        color = android.graphics.Color.parseColor("#1565C0")
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "+${String.format("%.1f", maxShear)} kN",
                        padding + 5, topY - 5, textPaint
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "-${String.format("%.1f", maxShear)} kN",
                        padding + plotW - 120, botY + 20, textPaint
                    )
                }
            }

            // الطول
            val spanPaint = android.graphics.Paint().apply {
                textSize = 20f
                color = android.graphics.Color.BLACK
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                "L = ${String.format("%.1f", span)} m",
                canvasW / 2, canvasH - 5, spanPaint
            )
        }
    }
}

@Composable
fun BendingMomentDiagram(
    span: Double,              // m
    maxMoment: Double,         // kN.m
    loadType: String = "UDL",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, PrimaryBlueVeryLight, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("مخطط العزم (BMD)", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = PrimaryBlueDark)

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val canvasW = size.width
            val canvasH = size.height
            val padding = 40f
            val plotW = canvasW - 2 * padding
            val plotH = canvasH - 2 * padding
            val baseY = padding + 20f

            // محور
            drawLine(Color.Black, Offset(padding, baseY), Offset(padding + plotW, baseY), strokeWidth = 2f)

            // مسندين
            drawTriangleSupport(padding, baseY, 10f)
            drawTriangleSupport(padding + plotW, baseY, 10f)

            when (loadType) {
                "UDL" -> {
                    // BMD لحمل موزع: قطع مكافئ (parabola)
                    val maxY = baseY + plotH * 0.7f
                    val path = Path().apply {
                        moveTo(padding, baseY)
                        quadraticBezierTo(
                            padding + plotW / 2, maxY + 20f,
                            padding + plotW, baseY
                        )
                        close()
                    }
                    drawPath(path, Color(0xFFC62828).copy(alpha = 0.15f))

                    // الخط
                    val curvePath = Path().apply {
                        moveTo(padding, baseY)
                        quadraticBezierTo(
                            padding + plotW / 2, maxY + 20f,
                            padding + plotW, baseY
                        )
                    }
                    drawPath(curvePath, UnsafeRed, style = Stroke(width = 2.5f))

                    // قيمة العزم الأقصى
                    val textPaint = android.graphics.Paint().apply {
                        textSize = 20f
                        color = android.graphics.Color.parseColor("#C62828")
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "M_max = ${String.format("%.1f", maxMoment)} kN.m",
                        canvasW / 2, maxY + 15, textPaint
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawTriangleSupport(x: Float, y: Float, size: Float) {
    val path = Path().apply {
        moveTo(x, y)
        lineTo(x - size, y + size * 1.5f)
        lineTo(x + size, y + size * 1.5f)
        close()
    }
    drawPath(path, Color.Black, style = Stroke(width = 2f))
}

// ═══════════════════════════════════════════════════════════
// رسم مقطع الكمرة مع التسليح
// Beam Cross Section
// ═══════════════════════════════════════════════════════════

@Composable
fun BeamCrossSectionDrawing(
    width: Double,              // mm
    height: Double,             // mm
    topBars: Int,
    topBarDia: Double,
    bottomBars: Int,
    bottomBarDia: Double,
    stirrupDia: Double,
    cover: Double = 25.0,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, PrimaryBlueVeryLight, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("مقطع الكمرة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PrimaryBlueDark)
        Text("${width.toInt()} × ${height.toInt()} mm", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            val canvasW = size.width
            val canvasH = size.height
            val padding = 40f

            val scale = (minOf(canvasW, canvasH) - 2 * padding) / max(width, height).toFloat()
            val drawW = (width * scale).toFloat()
            val drawH = (height * scale).toFloat()
            val ox = (canvasW - drawW) / 2
            val oy = (canvasH - drawH) / 2

            // خرسانة
            drawRect(Color(0xFFE8EAF6), topLeft = Offset(ox, oy), size = Size(drawW, drawH))
            drawRect(PrimaryBlueDark, topLeft = Offset(ox, oy), size = Size(drawW, drawH), style = Stroke(3f))

            // كانة
            val ci = (cover * scale).toFloat()
            drawRect(SafeGreen, topLeft = Offset(ox + ci, oy + ci),
                size = Size(drawW - 2 * ci, drawH - 2 * ci), style = Stroke(2f))

            val barInset = ci + (stirrupDia * scale).toFloat()
            val botBarR = ((bottomBarDia / 2) * scale).toFloat().coerceAtLeast(5f)
            val topBarR = ((topBarDia / 2) * scale).toFloat().coerceAtLeast(4f)

            // حديد سفلي (شد)
            for (i in 0 until bottomBars) {
                val x = ox + barInset + botBarR + (drawW - 2 * barInset - 2 * botBarR) * i / (bottomBars - 1).coerceAtLeast(1)
                val y = oy + drawH - barInset - botBarR
                drawCircle(UnsafeRed, botBarR, Offset(x, y))
                drawCircle(Color.Black, botBarR, Offset(x, y), style = Stroke(1f))
            }

            // حديد علوي (ضغط أو إنشائي)
            for (i in 0 until topBars) {
                val x = ox + barInset + topBarR + (drawW - 2 * barInset - 2 * topBarR) * i / (topBars - 1).coerceAtLeast(1)
                val y = oy + barInset + topBarR
                drawCircle(PrimaryBlue, topBarR, Offset(x, y))
                drawCircle(Color.Black, topBarR, Offset(x, y), style = Stroke(1f))
            }

            // أبعاد
            val textPaint = android.graphics.Paint().apply {
                textSize = 22f; color = android.graphics.Color.parseColor("#0D47A1")
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText("${width.toInt()}", canvasW / 2, oy + drawH + 25f, textPaint)
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(-90f, ox - 15f, canvasH / 2)
            drawContext.canvas.nativeCanvas.drawText("${height.toInt()}", ox - 15f, canvasH / 2, textPaint)
            drawContext.canvas.nativeCanvas.restore()
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("شد: ${bottomBars}φ${bottomBarDia.toInt()}", fontSize = 11.sp, color = UnsafeRed, fontWeight = FontWeight.Medium)
            Text("ضغط: ${topBars}φ${topBarDia.toInt()}", fontSize = 11.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium)
            Text("كانات: φ${stirrupDia.toInt()}", fontSize = 11.sp, color = SafeGreen, fontWeight = FontWeight.Medium)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// رسم قطاع القاعدة
// Footing Section Drawing
// ═══════════════════════════════════════════════════════════

@Composable
fun FootingSectionDrawing(
    footingWidth: Double,       // m
    footingDepth: Double,       // m
    columnWidth: Double,        // mm
    columnHeight: Double = 3000.0, // mm - ارتفاع العمود المرسوم
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, PrimaryBlueVeryLight, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("قطاع القاعدة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PrimaryBlueDark)

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val canvasW = size.width
            val canvasH = size.height
            val padding = 30f

            val footingWmm = footingWidth * 1000
            val footingDmm = footingDepth * 1000
            val colW = columnWidth

            val totalH = footingDmm + 1500  // column stub
            val scale = minOf(
                (canvasW - 2 * padding) / footingWmm.toFloat(),
                (canvasH - 2 * padding) / totalH.toFloat()
            )

            val fW = (footingWmm * scale).toFloat()
            val fD = (footingDmm * scale).toFloat()
            val cW = (colW * scale).toFloat()
            val cH = (1500 * scale).toFloat()

            val baseY = canvasH - padding - 20f
            val ox = (canvasW - fW) / 2

            // رسم التربة (خطوط مائلة)
            for (i in 0..20) {
                val x = ox + i * fW / 20
                drawLine(Color(0xFF8D6E63).copy(alpha = 0.3f),
                    Offset(x, baseY), Offset(x - 10f, baseY + 15f), strokeWidth = 1f)
            }

            // القاعدة
            drawRect(Color(0xFFBBDEFB), topLeft = Offset(ox, baseY - fD), size = Size(fW, fD))
            drawRect(PrimaryBlueDark, topLeft = Offset(ox, baseY - fD), size = Size(fW, fD), style = Stroke(2.5f))

            // العمود
            val colOx = (canvasW - cW) / 2
            drawRect(Color(0xFFC8E6C9), topLeft = Offset(colOx, baseY - fD - cH), size = Size(cW, cH))
            drawRect(SafeGreen.copy(alpha = 0.8f), topLeft = Offset(colOx, baseY - fD - cH), size = Size(cW, cH), style = Stroke(2f))

            // أبعاد
            val tp = android.graphics.Paint().apply {
                textSize = 20f; color = android.graphics.Color.parseColor("#0D47A1")
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                "${String.format("%.2f", footingWidth)} m",
                canvasW / 2, baseY + 18f, tp
            )
        }
    }
}
