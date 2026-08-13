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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.civileg.app.ui.theme.ThemeColors

/**
 * رسم تفاعلي لمقطع الكمرة (Cross Section) مع إمكانية التكبير والحركة.
 */
@Composable
fun InteractiveBeamSection(
    width: Double,              // mm
    depth: Double,              // mm
    bottomBars: Int,
    bottomDiameter: Double,
    topBars: Int,
    topDiameter: Double,
    stirrupsDiameter: Double,
    isSafe: Boolean = true,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val concreteColor = ThemeColors.concreteColor()
    val steelColor = ThemeColors.steelColor()
    val dimensionColor = ThemeColors.dimensionColor()
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = 1.0f
                            offset = Offset.Zero
                        }
                    )
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
            
            val baseScale = minOf(
                canvasWidth / (width.toFloat() * 1.5f),
                canvasHeight / (depth.toFloat() * 1.5f)
            )
            val currentScale = baseScale * scale
            
            val centerX = canvasWidth / 2f + offset.x
            val centerY = canvasHeight / 2f + offset.y
            
            val rectWidth = (width * currentScale).toFloat()
            val rectHeight = (depth * currentScale).toFloat()
            val left = centerX - rectWidth / 2f
            val top = centerY - rectHeight / 2f
            
            // 1. رسم الخرسانة
            drawRect(
                color = concreteColor,
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                style = Stroke(width = 4f * scale)
            )
            
            // 2. الغطاء والكانات (Stirrups)
            val cover = 25f * currentScale
            drawRect(
                color = steelColor,
                topLeft = Offset(left + cover, top + cover),
                size = Size(rectWidth - 2 * cover, rectHeight - 2 * cover),
                style = Stroke(width = 2f * scale)
            )
            
            // 3. الأسياخ السفلية (Main Bottom Reinforcement)
            val bottomBarRadius = (bottomDiameter * currentScale / 2.0).coerceAtLeast(4.0).toFloat()
            val bottomSpacing = if (bottomBars > 1) (rectWidth - 2 * cover - 2 * bottomBarRadius) / (bottomBars - 1) else 0f
            
            for (i in 0 until bottomBars) {
                drawCircle(
                    color = if (isSafe) steelColor else Color.Red,
                    radius = bottomBarRadius,
                    center = Offset(left + cover + bottomBarRadius + i * bottomSpacing, top + rectHeight - cover - bottomBarRadius)
                )
            }
            
            // 4. الأسياخ العلوية (Top Hangers)
            val topBarRadius = (topDiameter * currentScale / 2.0).coerceAtLeast(4.0).toFloat()
            val topSpacing = if (topBars > 1) (rectWidth - 2 * cover - 2 * topBarRadius) / (topBars - 1) else 0f
            
            for (i in 0 until topBars) {
                drawCircle(
                    color = steelColor.copy(alpha = 0.8f),
                    radius = topBarRadius,
                    center = Offset(left + cover + topBarRadius + i * topSpacing, top + cover + topBarRadius)
                )
            }
            
            // 5. الأبعاد
            drawDimensions(left, top, rectWidth, rectHeight, width, depth, dimensionColor)
        }
    }
}

private fun DrawScope.drawDimensions(l: Float, t: Float, w: Float, h: Float, aW: Double, aD: Double, color: Color) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            textSize = 28f
            this.color = android.graphics.Color.GRAY
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawText("${aW.toInt()} mm", l + w/2f, t - 15f, paint)
        save()
        rotate(-90f, l - 35f, t + h/2f)
        drawText("${aD.toInt()} mm", l - 35f, t + h/2f, paint)
        restore()
    }
}
