package com.civilengineer.assistant.ui.components.steel

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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civilengineer.assistant.models.steel.SteelSectionProperties
import com.civilengineer.assistant.models.steel.SteelSectionType
import com.civilengineer.assistant.ui.theme.*
import kotlin.math.*

// ═══════════════════════════════════════════════════════════════
// 1. رسم قطاع I / H (IPE, HEA, HEB, HEM)
//    I-Section / H-Section Cross Section Drawing
// ═══════════════════════════════════════════════════════════════

@Composable
fun ISectionDrawing(
    section: SteelSectionProperties,
    showDimensions: Boolean = true,
    showAxes: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFB0BEC5), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(section.name, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = Color(0xFF37474F))
        Text("${section.weight} kg/m | A = ${section.A} mm²",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary)

        Spacer(modifier = Modifier.height(4.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            val cW = size.width
            val cH = size.height
            val pad = if (showDimensions) 55f else 30f

            val h = section.h
            val b = section.b
            val tw = section.tw
            val tf = section.tf
            val r = section.r

            // حساب مقياس الرسم
            val scaleX = (cW - 2 * pad) / b.toFloat()
            val scaleY = (cH - 2 * pad) / h.toFloat()
            val scale = minOf(scaleX, scaleY)

            val drawH = (h * scale).toFloat()
            val drawB = (b * scale).toFloat()
            val drawTw = (tw * scale).toFloat()
            val drawTf = (tf * scale).toFloat()
            val drawR = (r * scale).toFloat().coerceAtMost(drawTf)

            val cx = cW / 2
            val cy = cH / 2

            // ─── رسم القطاع I/H ───

            val steelColor = Color(0xFF455A64)
            val fillColor = Color(0xFFB0BEC5).copy(alpha = 0.4f)
            val dimColor = Color(0xFF1565C0)
            val axisColor = Color(0xFFE53935)

            // بناء مسار القطاع I
            val path = Path().apply {
                // شفة علوية - يسار إلى يمين
                moveTo(cx - drawB / 2, cy - drawH / 2)
                lineTo(cx + drawB / 2, cy - drawH / 2)
                lineTo(cx + drawB / 2, cy - drawH / 2 + drawTf)

                // منحنى التقعر العلوي أيمن
                lineTo(cx + drawTw / 2 + drawR, cy - drawH / 2 + drawTf)
                if (drawR > 1) {
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            cx + drawTw / 2, cy - drawH / 2 + drawTf,
                            cx + drawTw / 2 + 2 * drawR, cy - drawH / 2 + drawTf + 2 * drawR
                        ),
                        startAngleDegrees = 270f, sweepAngleDegrees = -90f, forceMoveTo = false
                    )
                }

                // وتر أيمن
                lineTo(cx + drawTw / 2, cy + drawH / 2 - drawTf - drawR)

                // منحنى التقعر السفلي أيمن
                if (drawR > 1) {
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            cx + drawTw / 2, cy + drawH / 2 - drawTf - 2 * drawR,
                            cx + drawTw / 2 + 2 * drawR, cy + drawH / 2 - drawTf
                        ),
                        startAngleDegrees = 180f, sweepAngleDegrees = -90f, forceMoveTo = false
                    )
                }

                // شفة سفلية
                lineTo(cx + drawB / 2, cy + drawH / 2 - drawTf)
                lineTo(cx + drawB / 2, cy + drawH / 2)
                lineTo(cx - drawB / 2, cy + drawH / 2)
                lineTo(cx - drawB / 2, cy + drawH / 2 - drawTf)

                // منحنى التقعر السفلي أيسر
                lineTo(cx - drawTw / 2 - drawR, cy + drawH / 2 - drawTf)
                if (drawR > 1) {
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            cx - drawTw / 2 - 2 * drawR, cy + drawH / 2 - drawTf - 2 * drawR,
                            cx - drawTw / 2, cy + drawH / 2 - drawTf
                        ),
                        startAngleDegrees = 90f, sweepAngleDegrees = -90f, forceMoveTo = false
                    )
                }

                // وتر أيسر
                lineTo(cx - drawTw / 2, cy - drawH / 2 + drawTf + drawR)

                // منحنى التقعر العلوي أيسر
                if (drawR > 1) {
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            cx - drawTw / 2 - 2 * drawR, cy - drawH / 2 + drawTf,
                            cx - drawTw / 2, cy - drawH / 2 + drawTf + 2 * drawR
                        ),
                        startAngleDegrees = 0f, sweepAngleDegrees = -90f, forceMoveTo = false
                    )
                }

                lineTo(cx - drawB / 2, cy - drawH / 2 + drawTf)
                close()
            }

            // رسم التعبئة والحدود
            drawPath(path, fillColor)
            drawPath(path, steelColor, style = Stroke(width = 2.5f))

            // ─── تهشير القطاع ───
            val hatchSpacing = 8f
            for (i in 0..((drawH / hatchSpacing).toInt())) {
                val y = cy - drawH / 2 + i * hatchSpacing
                val xStart: Float
                val xEnd: Float
                if (y < cy - drawH / 2 + drawTf || y > cy + drawH / 2 - drawTf) {
                    xStart = cx - drawB / 2
                    xEnd = cx + drawB / 2
                } else {
                    xStart = cx - drawTw / 2
                    xEnd = cx + drawTw / 2
                }
                drawLine(
                    Color(0xFF78909C).copy(alpha = 0.25f),
                    Offset(xStart + 2, y), Offset(xEnd - 2, y),
                    strokeWidth = 0.5f
                )
            }

            // ─── محاور X-X و Y-Y ───
            if (showAxes) {
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                // X-X محور
                drawLine(axisColor, Offset(pad / 2, cy), Offset(cW - pad / 2, cy),
                    strokeWidth = 1f, pathEffect = dashEffect)
                // Y-Y محور
                drawLine(axisColor, Offset(cx, pad / 2), Offset(cx, cH - pad / 2),
                    strokeWidth = 1f, pathEffect = dashEffect)

                // تسميات المحاور
                val axisPaint = android.graphics.Paint().apply {
                    textSize = 22f; color = android.graphics.Color.parseColor("#E53935")
                    isFakeBoldText = true
                }
                drawContext.canvas.nativeCanvas.drawText("X", cW - pad / 2 + 5, cy + 5, axisPaint)
                drawContext.canvas.nativeCanvas.drawText("X", pad / 2 - 20, cy + 5, axisPaint)
                drawContext.canvas.nativeCanvas.drawText("Y", cx - 3, pad / 2 - 5, axisPaint)
                drawContext.canvas.nativeCanvas.drawText("Y", cx - 3, cH - pad / 2 + 18, axisPaint)
            }

            // ─── الأبعاد ───
            if (showDimensions) {
                val dimPaint = android.graphics.Paint().apply {
                    textSize = 20f; color = android.graphics.Color.parseColor("#1565C0")
                    textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true
                }
                val dimPaintSmall = android.graphics.Paint().apply {
                    textSize = 16f; color = android.graphics.Color.parseColor("#1565C0")
                    textAlign = android.graphics.Paint.Align.LEFT
                }

                // h - الارتفاع (يمين)
                val dimX = cx + drawB / 2 + 25
                drawLine(dimColor, Offset(dimX, cy - drawH / 2), Offset(dimX, cy + drawH / 2), strokeWidth = 1.5f)
                drawLine(dimColor, Offset(dimX - 5, cy - drawH / 2), Offset(dimX + 5, cy - drawH / 2), strokeWidth = 1.5f)
                drawLine(dimColor, Offset(dimX - 5, cy + drawH / 2), Offset(dimX + 5, cy + drawH / 2), strokeWidth = 1.5f)
                drawContext.canvas.nativeCanvas.drawText("h=${h.toInt()}", dimX + 8, cy + 6, dimPaintSmall)

                // b - العرض (أسفل)
                val dimY = cy + drawH / 2 + 25
                drawLine(dimColor, Offset(cx - drawB / 2, dimY), Offset(cx + drawB / 2, dimY), strokeWidth = 1.5f)
                drawLine(dimColor, Offset(cx - drawB / 2, dimY - 5), Offset(cx - drawB / 2, dimY + 5), strokeWidth = 1.5f)
                drawLine(dimColor, Offset(cx + drawB / 2, dimY - 5), Offset(cx + drawB / 2, dimY + 5), strokeWidth = 1.5f)
                drawContext.canvas.nativeCanvas.drawText("b=${b.toInt()}", cx, dimY + 18, dimPaint)

                // tf - سمك الشفة (يسار أعلى)
                val tfDimX = cx - drawB / 2 - 15
                drawLine(dimColor, Offset(tfDimX, cy - drawH / 2), Offset(tfDimX, cy - drawH / 2 + drawTf), strokeWidth = 1f)
                drawContext.canvas.nativeCanvas.drawText("tf=${tf}", tfDimX - 35, cy - drawH / 2 + drawTf / 2 + 5,
                    android.graphics.Paint().apply { textSize = 14f; color = android.graphics.Color.parseColor("#1565C0") })

                // tw - سمك الوتر (وسط)
                drawContext.canvas.nativeCanvas.drawText("tw=${tw}", cx + drawTw / 2 + 5, cy,
                    android.graphics.Paint().apply { textSize = 14f; color = android.graphics.Color.parseColor("#1565C0") })
            }
        }

        // خصائص مختصرة
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            SectionPropChip("Ix", "${(section.Ix / 1e4).toInt()} cm⁴")
            SectionPropChip("Wx", "${(section.Wx / 1e3).toInt()} cm³")
            SectionPropChip("Zx", "${(section.Zx / 1e3).toInt()} cm³")
            SectionPropChip("rx", "${section.rx.toInt()} mm")
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 2. رسم قطاع مجرى C (UPN / Channel)
// ═══════════════════════════════════════════════════════════════

@Composable
fun ChannelSectionDrawing(
    section: SteelSectionProperties,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFB0BEC5), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(section.name, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = Color(0xFF37474F))

        Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            val cW = size.width; val cH = size.height; val pad = 50f
            val h = section.h; val b = section.b; val tw = section.tw; val tf = section.tf

            val scaleX = (cW - 2 * pad) / b.toFloat()
            val scaleY = (cH - 2 * pad) / h.toFloat()
            val scale = minOf(scaleX, scaleY)

            val dH = (h * scale).toFloat(); val dB = (b * scale).toFloat()
            val dTw = (tw * scale).toFloat(); val dTf = (tf * scale).toFloat()
            val cx = cW / 2; val cy = cH / 2

            val steelColor = Color(0xFF455A64)
            val fillColor = Color(0xFF90A4AE).copy(alpha = 0.35f)

            // مسار القطاع C
            val path = Path().apply {
                moveTo(cx - dB / 2, cy - dH / 2)
                lineTo(cx + dB / 2, cy - dH / 2)
                lineTo(cx + dB / 2, cy - dH / 2 + dTf)
                lineTo(cx - dB / 2 + dTw, cy - dH / 2 + dTf)
                lineTo(cx - dB / 2 + dTw, cy + dH / 2 - dTf)
                lineTo(cx + dB / 2, cy + dH / 2 - dTf)
                lineTo(cx + dB / 2, cy + dH / 2)
                lineTo(cx - dB / 2, cy + dH / 2)
                close()
            }

            drawPath(path, fillColor)
            drawPath(path, steelColor, style = Stroke(width = 2.5f))

            // تهشير
            for (i in 0..((dH / 7f).toInt())) {
                val y = cy - dH / 2 + i * 7f
                val xs = cx - dB / 2; val xe: Float
                xe = if (y < cy - dH / 2 + dTf || y > cy + dH / 2 - dTf) cx + dB / 2 else cx - dB / 2 + dTw
                drawLine(Color(0xFF78909C).copy(0.2f), Offset(xs + 2, y), Offset(xe - 2, y), 0.5f)
            }

            // أبعاد
            val dp = android.graphics.Paint().apply { textSize = 18f; color = android.graphics.Color.parseColor("#1565C0"); textAlign = android.graphics.Paint.Align.CENTER }
            drawContext.canvas.nativeCanvas.drawText("h=${h.toInt()}", cx + dB / 2 + 30, cy + 5, dp)
            drawContext.canvas.nativeCanvas.drawText("b=${b.toInt()}", cx, cy + dH / 2 + 20, dp)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            SectionPropChip("Ix", "${(section.Ix / 1e4).toInt()} cm⁴")
            SectionPropChip("Wx", "${(section.Wx / 1e3).toInt()} cm³")
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 3. رسم قطاع مربع/مستطيل مجوف (SHS/RHS)
// ═══════════════════════════════════════════════════════════════

@Composable
fun HollowSectionDrawing(
    h: Double, b: Double, t: Double,
    name: String = "SHS ${h.toInt()}×${b.toInt()}×${t}",
    isCircular: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFB0BEC5), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF37474F))

        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val cW = size.width; val cH = size.height; val pad = 50f
            val maxDim = maxOf(h, b)
            val scale = (minOf(cW, cH) - 2 * pad) / maxDim.toFloat()
            val dH = (h * scale).toFloat(); val dB = (b * scale).toFloat(); val dT = (t * scale).toFloat().coerceAtLeast(3f)
            val cx = cW / 2; val cy = cH / 2
            val cornerR = dT * 2

            if (isCircular) {
                // CHS - دائري مجوف
                val radius = dH / 2
                drawCircle(Color(0xFF90A4AE).copy(0.3f), radius, Offset(cx, cy))
                drawCircle(Color(0xFF455A64), radius, Offset(cx, cy), style = Stroke(2.5f))
                drawCircle(Color.White, radius - dT, Offset(cx, cy))
                drawCircle(Color(0xFF455A64), radius - dT, Offset(cx, cy), style = Stroke(1.5f))
                // تهشير بين الدائرتين
                for (angle in 0..360 step 15) {
                    val rad = Math.toRadians(angle.toDouble())
                    val r1 = radius - dT + 1
                    val r2 = radius - 1
                    drawLine(Color(0xFF78909C).copy(0.2f),
                        Offset(cx + (r1 * cos(rad)).toFloat(), cy + (r1 * sin(rad)).toFloat()),
                        Offset(cx + (r2 * cos(rad)).toFloat(), cy + (r2 * sin(rad)).toFloat()), 0.5f)
                }
            } else {
                // SHS / RHS
                // خارجي
                drawRoundRect(Color(0xFF90A4AE).copy(0.3f), Offset(cx - dB / 2, cy - dH / 2), Size(dB, dH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR))
                drawRoundRect(Color(0xFF455A64), Offset(cx - dB / 2, cy - dH / 2), Size(dB, dH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR), style = Stroke(2.5f))
                // داخلي
                drawRoundRect(Color.White, Offset(cx - dB / 2 + dT, cy - dH / 2 + dT), Size(dB - 2 * dT, dH - 2 * dT), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR / 2))
                drawRoundRect(Color(0xFF455A64), Offset(cx - dB / 2 + dT, cy - dH / 2 + dT), Size(dB - 2 * dT, dH - 2 * dT), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR / 2), style = Stroke(1.5f))
                // تهشير
                for (i in 0..((dH / 6f).toInt())) {
                    val y = cy - dH / 2 + i * 6f
                    if (y >= cy - dH / 2 && y <= cy + dH / 2) {
                        val isInFlange = y < cy - dH / 2 + dT || y > cy + dH / 2 - dT
                        if (isInFlange) {
                            drawLine(Color(0xFF78909C).copy(0.2f), Offset(cx - dB / 2 + 2, y), Offset(cx + dB / 2 - 2, y), 0.5f)
                        } else {
                            drawLine(Color(0xFF78909C).copy(0.2f), Offset(cx - dB / 2 + 2, y), Offset(cx - dB / 2 + dT - 1, y), 0.5f)
                            drawLine(Color(0xFF78909C).copy(0.2f), Offset(cx + dB / 2 - dT + 1, y), Offset(cx + dB / 2 - 2, y), 0.5f)
                        }
                    }
                }
            }

            // أبعاد
            val dp = android.graphics.Paint().apply { textSize = 18f; color = android.graphics.Color.parseColor("#1565C0"); textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
            if (isCircular) {
                drawContext.canvas.nativeCanvas.drawText("D=${h.toInt()}", cx, cy + dH / 2 + 22, dp)
                drawContext.canvas.nativeCanvas.drawText("t=${t}", cx + dH / 2 + 15, cy, dp.apply { textAlign = android.graphics.Paint.Align.LEFT; textSize = 14f })
            } else {
                drawContext.canvas.nativeCanvas.drawText("${b.toInt()}×${h.toInt()}×$t", cx, cy + dH / 2 + 22, dp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 4. رسم زاوية (Equal / Unequal Angle)
// ═══════════════════════════════════════════════════════════════

@Composable
fun AngleSectionDrawing(
    legA: Double, legB: Double, t: Double,
    name: String = "L ${legA.toInt()}×${legB.toInt()}×${t}",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFB0BEC5), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF37474F))

        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val cW = size.width; val cH = size.height; val pad = 50f
            val maxDim = maxOf(legA, legB)
            val scale = (minOf(cW, cH) - 2 * pad) / maxDim.toFloat()
            val dA = (legA * scale).toFloat(); val dB = (legB * scale).toFloat(); val dT = (t * scale).toFloat().coerceAtLeast(4f)
            val cx = cW / 2 - dB / 4; val cy = cH / 2 - dA / 4

            val steelColor = Color(0xFF455A64)
            val fillColor = Color(0xFF90A4AE).copy(0.35f)

            val path = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + dB, cy)
                lineTo(cx + dB, cy + dT)
                lineTo(cx + dT, cy + dT)
                lineTo(cx + dT, cy + dA)
                lineTo(cx, cy + dA)
                close()
            }

            drawPath(path, fillColor)
            drawPath(path, steelColor, style = Stroke(2.5f))

            // تهشير
            for (i in 0..((dA / 6f).toInt())) {
                val y = cy + i * 6f
                if (y <= cy + dA) {
                    val xe = if (y <= cy + dT) cx + dB else cx + dT
                    drawLine(Color(0xFF78909C).copy(0.2f), Offset(cx + 2, y), Offset(xe - 2, y), 0.5f)
                }
            }

            val dp = android.graphics.Paint().apply { textSize = 18f; color = android.graphics.Color.parseColor("#1565C0"); textAlign = android.graphics.Paint.Align.CENTER }
            drawContext.canvas.nativeCanvas.drawText("${legB.toInt()}", cx + dB / 2, cy - 8, dp)
            drawContext.canvas.nativeCanvas.drawText("${legA.toInt()}", cx - 18, cy + dA / 2, dp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 5. رسم وصلة براغي (Bolted Connection)
// ═══════════════════════════════════════════════════════════════

@Composable
fun BoltedConnectionDrawing(
    rows: Int,
    columns: Int,
    boltDiameter: Double,
    pitch: Double,
    gauge: Double,
    edgeDistance: Double,
    plateWidth: Double = 0.0,
    plateHeight: Double = 0.0,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFB0BEC5), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("وصلة براغي - Bolted Connection", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = Color(0xFF37474F))
        Text("${rows}×${columns} M${boltDiameter.toInt()}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

        Canvas(modifier = Modifier.fillMaxWidth().height(250.dp)) {
            val cW = size.width; val cH = size.height; val pad = 40f
            val boltR = 10f
            val holeR = 12f

            // حساب أبعاد اللوح
            val pWidth = if (plateWidth > 0) plateWidth else (2 * edgeDistance + (columns - 1) * gauge)
            val pHeight = if (plateHeight > 0) plateHeight else (2 * edgeDistance + (rows - 1) * pitch)
            val scale = minOf((cW - 2 * pad) / pWidth.toFloat(), (cH - 2 * pad) / pHeight.toFloat()) * 0.85f

            val dPW = (pWidth * scale).toFloat(); val dPH = (pHeight * scale).toFloat()
            val cx = cW / 2; val cy = cH / 2

            // اللوح
            drawRect(Color(0xFFECEFF1), Offset(cx - dPW / 2, cy - dPH / 2), Size(dPW, dPH))
            drawRect(Color(0xFF455A64), Offset(cx - dPW / 2, cy - dPH / 2), Size(dPW, dPH), style = Stroke(2f))

            // خطوط التهشير للوح
            for (i in 0..(dPH / 5f).toInt()) {
                val y = cy - dPH / 2 + i * 5f
                drawLine(Color(0xFFB0BEC5).copy(0.3f), Offset(cx - dPW / 2 + 2, y), Offset(cx + dPW / 2 - 2, y), 0.3f)
            }

            // رسم البراغي
            val startX = cx - ((columns - 1) * gauge.toFloat() * scale) / 2
            val startY = cy - ((rows - 1) * pitch.toFloat() * scale) / 2

            for (r in 0 until rows) {
                for (c in 0 until columns) {
                    val bx = startX + c * gauge.toFloat() * scale
                    val by = startY + r * pitch.toFloat() * scale

                    // ثقب
                    drawCircle(Color.White, holeR, Offset(bx, by))
                    drawCircle(Color(0xFF455A64), holeR, Offset(bx, by), style = Stroke(1.5f))

                    // رأس البرغي
                    drawCircle(Color(0xFF37474F), boltR, Offset(bx, by))
                    drawCircle(Color(0xFF263238), boltR, Offset(bx, by), style = Stroke(1f))

                    // علامة + على البرغي
                    drawLine(Color.White, Offset(bx - 4, by), Offset(bx + 4, by), 1.5f)
                    drawLine(Color.White, Offset(bx, by - 4), Offset(bx, by + 4), 1.5f)
                }
            }

            // أبعاد
            val dp = android.graphics.Paint().apply { textSize = 16f; color = android.graphics.Color.parseColor("#1565C0"); isFakeBoldText = true }

            // Pitch
            if (rows > 1) {
                val pX = startX - 25
                drawLine(Color(0xFF1565C0), Offset(pX, startY), Offset(pX, startY + pitch.toFloat() * scale), 1f)
                drawContext.canvas.nativeCanvas.drawText("p=${pitch.toInt()}", pX - 40, startY + pitch.toFloat() * scale / 2 + 5, dp)
            }

            // Gauge
            if (columns > 1) {
                val gY = startY - 22
                drawLine(Color(0xFF1565C0), Offset(startX, gY), Offset(startX + gauge.toFloat() * scale, gY), 1f)
                drawContext.canvas.nativeCanvas.drawText("g=${gauge.toInt()}", startX + gauge.toFloat() * scale / 2 - 15, gY - 5,
                    dp.apply { textAlign = android.graphics.Paint.Align.CENTER })
            }

            // Edge distance
            drawContext.canvas.nativeCanvas.drawText("e=${edgeDistance.toInt()}", cx + dPW / 2 + 5, cy,
                dp.apply { textSize = 14f; textAlign = android.graphics.Paint.Align.LEFT })
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 6. رسم وصلة لحام (Welded Connection)
// ═══════════════════════════════════════════════════════════════

@Composable
fun WeldedConnectionDrawing(
    weldSize: Double,
    weldLength: Double,
    isFilletWeld: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFB0BEC5), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (isFilletWeld) "لحام زاوي - Fillet Weld" else "لحام تجويفي - Groove Weld",
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF37474F))
        Text("a = ${weldSize}mm, L = ${weldLength}mm", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val cW = size.width; val cH = size.height
            val cx = cW / 2; val cy = cH / 2

            val plateH = 80f; val plateW = 200f; val gussetH = 100f; val gussetW = 12f

            // اللوح الأفقي
            drawRect(Color(0xFFB0BEC5).copy(0.4f), Offset(cx - plateW / 2, cy), Size(plateW, plateH))
            drawRect(Color(0xFF455A64), Offset(cx - plateW / 2, cy), Size(plateW, plateH), style = Stroke(2f))

            // اللوح الرأسي (gusset)
            drawRect(Color(0xFFA5D6A7).copy(0.5f), Offset(cx - gussetW / 2, cy - gussetH), Size(gussetW, gussetH))
            drawRect(Color(0xFF2E7D32), Offset(cx - gussetW / 2, cy - gussetH), Size(gussetW, gussetH), style = Stroke(2f))

            // رمز اللحام
            if (isFilletWeld) {
                val weldDraw = 15f
                // لحام يسار
                val leftPath = Path().apply {
                    moveTo(cx - gussetW / 2, cy)
                    lineTo(cx - gussetW / 2 - weldDraw, cy)
                    lineTo(cx - gussetW / 2, cy - weldDraw)
                    close()
                }
                drawPath(leftPath, Color(0xFFE53935).copy(0.6f))
                drawPath(leftPath, Color(0xFFC62828), style = Stroke(1.5f))

                // لحام يمين
                val rightPath = Path().apply {
                    moveTo(cx + gussetW / 2, cy)
                    lineTo(cx + gussetW / 2 + weldDraw, cy)
                    lineTo(cx + gussetW / 2, cy - weldDraw)
                    close()
                }
                drawPath(rightPath, Color(0xFFE53935).copy(0.6f))
                drawPath(rightPath, Color(0xFFC62828), style = Stroke(1.5f))
            }

            // رمز اللحام القياسي (مثلث)
            val symY = cy - gussetH / 2
            drawLine(Color(0xFF455A64), Offset(cx + gussetW / 2 + 20, symY), Offset(cx + gussetW / 2 + 60, symY), 1f)

            // مثلث رمز اللحام الزاوي
            val triPath = Path().apply {
                moveTo(cx + gussetW / 2 + 30, symY)
                lineTo(cx + gussetW / 2 + 42, symY)
                lineTo(cx + gussetW / 2 + 30, symY - 10)
                close()
            }
            drawPath(triPath, Color(0xFF455A64), style = Stroke(1.5f))

            val dp = android.graphics.Paint().apply { textSize = 16f; color = android.graphics.Color.parseColor("#1565C0"); isFakeBoldText = true }
            drawContext.canvas.nativeCanvas.drawText("a=${weldSize.toInt()}", cx + gussetW / 2 + 50, symY - 5, dp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 7. رسم لوح القاعدة (Base Plate)
// ═══════════════════════════════════════════════════════════════

@Composable
fun BasePlateDrawing(
    plateN: Double, plateB: Double, plateT: Double,
    columnH: Double, columnBf: Double,
    nAnchorBolts: Int, anchorDia: Double,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFB0BEC5), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("لوح القاعدة - Base Plate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF37474F))
        Text("${plateN.toInt()}×${plateB.toInt()}×${plateT.toInt()} mm", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

        Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            val cW = size.width; val cH = size.height; val pad = 40f
            val maxDim = maxOf(plateN, plateB)
            val scale = (minOf(cW, cH) - 2 * pad) / maxDim.toFloat() * 0.8f
            val dN = (plateN * scale).toFloat(); val dB = (plateB * scale).toFloat()
            val dColH = (columnH * scale).toFloat(); val dColBf = (columnBf * scale).toFloat()
            val cx = cW / 2; val cy = cH / 2

            // الخرسانة
            drawRect(Color(0xFFE0E0E0), Offset(cx - dN / 2 - 15, cy + 5), Size(dN + 30, 40f))
            drawRect(Color(0xFF9E9E9E), Offset(cx - dN / 2 - 15, cy + 5), Size(dN + 30, 40f), style = Stroke(1.5f))
            // تهشير الخرسانة
            for (i in 0..10) {
                val x = cx - dN / 2 - 15 + i * (dN + 30) / 10
                drawLine(Color(0xFF9E9E9E).copy(0.4f), Offset(x, cy + 8), Offset(x - 8, cy + 42), 0.8f)
            }

            // لوح القاعدة (منظر جانبي)
            drawRect(Color(0xFF90A4AE).copy(0.5f), Offset(cx - dN / 2, cy - 3), Size(dN, 8f))
            drawRect(Color(0xFF455A64), Offset(cx - dN / 2, cy - 3), Size(dN, 8f), style = Stroke(2.5f))

            // العمود المعدني (I-section مبسط)
            val colTop = cy - 3 - 80
            // وتر
            drawRect(Color(0xFFB0BEC5).copy(0.4f), Offset(cx - dColBf * 0.1f, colTop), Size(dColBf * 0.2f, 80f))
            drawRect(Color(0xFF455A64), Offset(cx - dColBf * 0.1f, colTop), Size(dColBf * 0.2f, 80f), style = Stroke(1.5f))
            // شفة علوية
            drawRect(Color(0xFFB0BEC5).copy(0.4f), Offset(cx - dColBf / 2, colTop), Size(dColBf, 8f))
            drawRect(Color(0xFF455A64), Offset(cx - dColBf / 2, colTop), Size(dColBf, 8f), style = Stroke(1.5f))
            // شفة سفلية
            drawRect(Color(0xFFB0BEC5).copy(0.4f), Offset(cx - dColBf / 2, cy - 11), Size(dColBf, 8f))
            drawRect(Color(0xFF455A64), Offset(cx - dColBf / 2, cy - 11), Size(dColBf, 8f), style = Stroke(1.5f))

            // براغي التثبيت
            val boltR = 5f
            val boltPositions = when (nAnchorBolts) {
                2 -> listOf(Pair(cx - dN / 3, cy + 2f), Pair(cx + dN / 3, cy + 2f))
                4 -> listOf(
                    Pair(cx - dN / 3, cy + 2f), Pair(cx + dN / 3, cy + 2f),
                    Pair(cx - dN / 3, cy - 6f), Pair(cx + dN / 3, cy - 6f)
                )
                6 -> listOf(
                    Pair(cx - dN / 3, cy + 2f), Pair(cx, cy + 2f), Pair(cx + dN / 3, cy + 2f),
                    Pair(cx - dN / 3, cy - 6f), Pair(cx, cy - 6f), Pair(cx + dN / 3, cy - 6f)
                )
                else -> listOf(Pair(cx - dN / 3, cy + 2f), Pair(cx + dN / 3, cy + 2f))
            }

            boltPositions.forEach { (bx, by) ->
                // ساق البرغي
                drawLine(Color(0xFF37474F), Offset(bx, by), Offset(bx, by + 30), 3f)
                // رأس
                drawCircle(Color(0xFF37474F), boltR, Offset(bx, by - 2))
                drawCircle(Color(0xFF455A64), boltR, Offset(bx, by - 2), style = Stroke(1f))
                // صامولة أسفل
                drawRect(Color(0xFF37474F), Offset(bx - boltR, by + 28), Size(boltR * 2, 4f))
            }

            // أبعاد
            val dp = android.graphics.Paint().apply { textSize = 16f; color = android.graphics.Color.parseColor("#1565C0"); isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER }
            drawContext.canvas.nativeCanvas.drawText("N=${plateN.toInt()}", cx, cy + 60, dp)
            drawContext.canvas.nativeCanvas.drawText("tp=${plateT.toInt()}", cx + dN / 2 + 20, cy + 2, dp.apply { textSize = 14f; textAlign = android.graphics.Paint.Align.LEFT })
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 8. رسم جمالون (Truss)
// ═══════════════════════════════════════════════════════════════

@Composable
fun TrussDrawing(
    span: Double,          // m
    height: Double,        // m
    nPanels: Int = 6,
    trussType: String = "Pratt",  // Pratt, Warren, Howe
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFB0BEC5), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("جمالون $trussType - $trussType Truss", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF37474F))
        Text("L=${span}m, H=${height}m, ${nPanels} بانل", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val cW = size.width; val cH = size.height; val pad = 40f
            val scale = (cW - 2 * pad) / span.toFloat()
            val dSpan = (span * scale).toFloat(); val dH = (height * scale).toFloat()
            val panelW = dSpan / nPanels
            val startX = pad; val baseY = cH - pad - 20

            val chordColor = Color(0xFF37474F)
            val diagColor = Color(0xFF1565C0)
            val vertColor = Color(0xFF2E7D32)
            val nodeColor = Color(0xFFE53935)

            // الحزام السفلي (bottom chord)
            drawLine(chordColor, Offset(startX, baseY), Offset(startX + dSpan, baseY), 3f)

            // الحزام العلوي (top chord)
            drawLine(chordColor, Offset(startX, baseY - dH), Offset(startX + dSpan, baseY - dH), 3f)

            // العناصر الرأسية والقطرية
            for (i in 0..nPanels) {
                val x = startX + i * panelW
                // رأسي
                drawLine(vertColor, Offset(x, baseY), Offset(x, baseY - dH), 2f)

                // قطريات حسب نوع الجمالون
                if (i < nPanels) {
                    when (trussType) {
                        "Pratt" -> {
                            if (i < nPanels / 2) {
                                drawLine(diagColor, Offset(x, baseY), Offset(x + panelW, baseY - dH), 2f)
                            } else {
                                drawLine(diagColor, Offset(x + panelW, baseY), Offset(x, baseY - dH), 2f)
                            }
                        }
                        "Howe" -> {
                            if (i < nPanels / 2) {
                                drawLine(diagColor, Offset(x + panelW, baseY), Offset(x, baseY - dH), 2f)
                            } else {
                                drawLine(diagColor, Offset(x, baseY), Offset(x + panelW, baseY - dH), 2f)
                            }
                        }
                        "Warren" -> {
                            if (i % 2 == 0) {
                                drawLine(diagColor, Offset(x, baseY), Offset(x + panelW, baseY - dH), 2f)
                            } else {
                                drawLine(diagColor, Offset(x, baseY - dH), Offset(x + panelW, baseY), 2f)
                            }
                        }
                    }
                }

                // عقد
                drawCircle(nodeColor, 5f, Offset(x, baseY))
                drawCircle(nodeColor, 5f, Offset(x, baseY - dH))
            }

            // مساند
            // مفصلي يسار
            val triSize = 12f
            val triPath = Path().apply {
                moveTo(startX, baseY)
                lineTo(startX - triSize, baseY + triSize * 1.5f)
                lineTo(startX + triSize, baseY + triSize * 1.5f)
                close()
            }
            drawPath(triPath, Color.Transparent, style = Stroke(2f))
            drawPath(triPath, chordColor, style = Stroke(2f))

            // أسطواني يمين
            val rollerPath = Path().apply {
                moveTo(startX + dSpan, baseY)
                lineTo(startX + dSpan - triSize, baseY + triSize * 1.5f)
                lineTo(startX + dSpan + triSize, baseY + triSize * 1.5f)
                close()
            }
            drawPath(rollerPath, Color.Transparent, style = Stroke(2f))
            drawPath(rollerPath, chordColor, style = Stroke(2f))
            drawCircle(chordColor, 4f, Offset(startX + dSpan, baseY + triSize * 1.5f + 5), style = Stroke(1.5f))

            // أبعاد
            val dp = android.graphics.Paint().apply { textSize = 16f; color = android.graphics.Color.parseColor("#1565C0"); textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
            drawContext.canvas.nativeCanvas.drawText("L = ${span}m", cW / 2, cH - 5, dp)
            drawContext.canvas.nativeCanvas.drawText("H = ${height}m", startX - 35, baseY - dH / 2, dp.apply { textSize = 14f })
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// مكون مساعد - خصائص مختصرة
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SectionPropChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 11.sp, color = PrimaryBlueDark, fontWeight = FontWeight.Bold)
    }
}
