package com.civileg.app.ui.compose.components.interactive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.civileg.app.ui.theme.ThemeColors

/**
 * رسم تفاعلي لمقطع الخزان (Water Tank Section)
 */
@Composable
fun InteractiveTankSection(
    width: Double,              // m
    height: Double,             // m
    wallThickness: Double,      // mm
    baseThickness: Double,      // mm
    isSafe: Boolean = true,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val concreteColor = ThemeColors.concreteColor()
    val steelColor = ThemeColors.steelColor()
    val waterColor = Color(0xFF2196F3).copy(alpha = 0.3f)
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { scale = 1.0f; offset = Offset.Zero })
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            // تحويل الأبعاد لمقياس الرسم (نستخدم ملم للكل للتبسيط)
            val wMm = width.toFloat() * 1000f
            val hMm = height.toFloat() * 1000f
            val wallTMm = wallThickness.toFloat()
            val baseTMm = baseThickness.toFloat()

            val baseScale = minOf(
                canvasWidth / (wMm + 2 * wallTMm) / 1.2f,
                canvasHeight / (hMm + baseTMm) / 1.2f
            )
            val currentScale = baseScale * scale
            
            val drawW = wMm * currentScale
            val drawH = hMm * currentScale
            val drawWallT = wallTMm * currentScale
            val drawBaseT = baseTMm * currentScale
            
            val centerX = canvasWidth / 2 + offset.x
            val centerY = canvasHeight / 2 + offset.y
            
            val left = centerX - (drawW + 2 * drawWallT) / 2
            val bottom = centerY + (drawH + drawBaseT) / 2
            
            // 1. رسم الماء (Water)
            drawRect(
                color = waterColor,
                topLeft = Offset(left + drawWallT, bottom - drawBaseT - drawH * 0.9f),
                size = Size(drawW, drawH * 0.9f),
                style = Fill
            )
            
            // 2. رسم جدران الخزان (Walls)
            val tankPath = Path().apply {
                // الجدار الأيسر
                moveTo(left, bottom - drawBaseT - drawH)
                lineTo(left + drawWallT, bottom - drawBaseT - drawH)
                lineTo(left + drawWallT, bottom - drawBaseT)
                // القاعدة
                lineTo(left + drawWallT + drawW, bottom - drawBaseT)
                // الجدار الأيمن
                lineTo(left + drawWallT + drawW, bottom - drawBaseT - drawH)
                lineTo(left + 2 * drawWallT + drawW, bottom - drawBaseT - drawH)
                lineTo(left + 2 * drawWallT + drawW, bottom)
                lineTo(left, bottom)
                close()
            }
            
            drawPath(
                path = tankPath,
                color = concreteColor,
                style = Stroke(width = 3f * scale)
            )
            
            // 3. رسم تسليح بسيط (Reinforcement)
            val reinfPadding = 30f * currentScale
            // تسليح القاعدة
            drawLine(
                color = steelColor,
                start = Offset(left + reinfPadding, bottom - reinfPadding),
                end = Offset(left + 2 * drawWallT + drawW - reinfPadding, bottom - reinfPadding),
                strokeWidth = 2f * scale
            )
            
            // 4. الأبعاد
            drawTankDimensions(left, bottom, drawW, drawH, drawWallT, drawBaseT, width, height)
        }
    }
}

private fun DrawScope.drawTankDimensions(
    l: Float, b: Float, dw: Float, dh: Float, dwt: Float, dbt: Float,
    aw: Double, ah: Double
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            textSize = 24f
            color = android.graphics.Color.GRAY
            textAlign = android.graphics.Paint.Align.CENTER
        }
        // العرض
        drawText("${aw} m", l + dwt + dw/2, b + 30f, paint)
        // الارتفاع
        save()
        rotate(-90f, l - 30f, b - dbt - dh/2)
        drawText("${ah} m", l - 30f, b - dbt - dh/2, paint)
        restore()
    }
}
