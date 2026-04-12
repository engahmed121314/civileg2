package com.civileg.app.ui.compose.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.civileg.app.ui.theme.ThemeColors
import kotlin.math.abs

/**
 * مخططات العزوم والجهود للكمرات (Moment & Shear Diagrams)
 */
@Composable
fun MomentShearDiagramChart(
    span: Double,                 // m
    momentValues: List<Pair<Double, Double>>,  // (position, moment)
    shearValues: List<Pair<Double, Double>>,   // (position, shear)
    modifier: Modifier = Modifier
) {
    val chartColors = ThemeColors.chartColors()
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Moment & Shear Diagrams", 
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // مخطط العزوم
            Text("Bending Moment Diagram (kN.m)", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                drawBeamDiagram(
                    values = momentValues,
                    span = span,
                    diagramColor = chartColors.getOrElse(0) { Color.Blue },
                    label = "Moment"
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // مخطط القص
            Text("Shear Force Diagram (kN)", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                drawBeamDiagram(
                    values = shearValues,
                    span = span,
                    diagramColor = chartColors.getOrElse(1) { Color.Red },
                    label = "Shear"
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBeamDiagram(
    values: List<Pair<Double, Double>>,
    span: Double,
    diagramColor: Color,
    label: String
) {
    val canvasWidth = size.width
    val canvasHeight = size.height
    val padding = 50f
    
    if (values.isEmpty()) return

    // نطاقات البيانات
    val maxX = span
    val maxY = values.maxOfOrNull { abs(it.second) }?.coerceAtLeast(1.0) ?: 100.0
    
    fun toCanvasX(position: Double): Float = 
        padding + (position / maxX * (canvasWidth - 2 * padding)).toFloat()
    
    fun toCanvasY(value: Double): Float = 
        canvasHeight / 2 - (value / maxY * (canvasHeight / 2 - padding)).toFloat()
    
    // خط الصفر (المحور الأفقي)
    drawLine(
        color = Color.Gray.copy(alpha = 0.5f),
        start = Offset(padding, canvasHeight / 2),
        end = Offset(canvasWidth - padding, canvasHeight / 2),
        strokeWidth = 2f
    )
    
    // رسم المنحنى
    val path = Path()
    values.forEachIndexed { index, (position, value) ->
        val x = toCanvasX(position)
        val y = toCanvasY(value)
        
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    
    drawPath(
        path = path,
        color = diagramColor,
        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    
    // تظليل المنطقة تحت المنحنى
    val fillPath = Path()
    fillPath.addPath(path)
    fillPath.lineTo(toCanvasX(values.last().first), canvasHeight / 2)
    fillPath.lineTo(toCanvasX(values.first().first), canvasHeight / 2)
    fillPath.close()
    
    drawPath(
        path = fillPath,
        color = diagramColor.copy(alpha = 0.15f),
        style = Fill
    )
    
    // رسم تسميات المحاور والقيم القصوى
    drawContext.canvas.nativeCanvas.apply {
        val textPaint = android.graphics.Paint().apply {
            textSize = 24f
            this.color = android.graphics.Color.GRAY
            textAlign = android.graphics.Paint.Align.CENTER
        }
        
        // بداية ونهاية الكمرة
        drawText("0m", padding, canvasHeight / 2 + 35f, textPaint)
        drawText("${span}m", canvasWidth - padding, canvasHeight / 2 + 35f, textPaint)
        
        // القيمة العظمى (تقريبية)
        val maxVal = values.maxByOrNull { abs(it.second) }
        maxVal?.let {
            val maxTextPaint = android.graphics.Paint().apply {
                textSize = 24f
                this.color = android.graphics.Color.parseColor("#1976D2")
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true 
            }
            drawText(
                "Max: ${"%.1f".format(it.second)}",
                toCanvasX(it.first),
                toCanvasY(it.second) - 15f,
                maxTextPaint
            )
        }
    }
}
