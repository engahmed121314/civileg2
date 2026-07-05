package com.civileg.app.ui.compose.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.calculations.base.SpectrumValue
import com.civileg.app.ui.theme.ThemeColors
import kotlin.math.abs

/**
 * مخطط طيف الاستجابة الزلزالي (Seismic Response Spectrum Chart)
 */
@Composable
fun SeismicResponseChart(
    spectrumValues: List<SpectrumValue>,
    designPeriod: Double,
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
                "Response Spectrum Analysis", 
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val padding = 60f
                
                if (spectrumValues.isEmpty()) return@Canvas

                val maxPeriod = spectrumValues.maxOfOrNull { it.period } ?: 3.0
                val maxSa = spectrumValues.maxOfOrNull { it.spectralAcceleration } ?: 1.0
                
                fun toCanvasX(period: Double): Float = 
                    padding + (period / maxPeriod * (canvasWidth - 2 * padding)).toFloat()
                
                fun toCanvasY(sa: Double): Float = 
                    canvasHeight - padding - (sa / maxSa * (canvasHeight - 2 * padding)).toFloat()
                
                // 1. رسم شبكة المحاور (Grid)
                val gridPaint = Color.Gray.copy(alpha = 0.2f)
                for (i in 1..5) {
                    val x = toCanvasX(maxPeriod * i / 5.0)
                    drawLine(gridPaint, Offset(x, padding), Offset(x, canvasHeight - padding), 1f)
                    
                    val y = toCanvasY(maxSa * i / 5.0)
                    drawLine(gridPaint, Offset(padding, y), Offset(canvasWidth - padding, y), 1f)
                }

                // 2. رسم المحاور الرئيسية
                drawLine(
                    color = Color.Gray,
                    start = Offset(padding, canvasHeight - padding),
                    end = Offset(canvasWidth - padding, canvasHeight - padding),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color.Gray,
                    start = Offset(padding, canvasHeight - padding),
                    end = Offset(padding, padding),
                    strokeWidth = 2f
                )
                
                // 3. رسم منحنى الطيف (Spectrum Curve)
                val path = Path()
                spectrumValues.forEachIndexed { index, value ->
                    val x = toCanvasX(value.period)
                    val y = toCanvasY(value.spectralAcceleration)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                
                drawPath(
                    path = path,
                    color = chartColors.getOrElse(0) { Color.Blue },
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                
                // 4. تحديد نقطة الدور التصميمي (Design Period Point)
                val designSa = spectrumValues.minByOrNull { abs(it.period - designPeriod) }?.spectralAcceleration ?: 0.0
                val designX = toCanvasX(designPeriod)
                val designY = toCanvasY(designSa)
                
                // خطوط إسقاط متقطعة
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                drawLine(
                    color = Color.Red,
                    start = Offset(designX, canvasHeight - padding),
                    end = Offset(designX, designY),
                    strokeWidth = 2f,
                    pathEffect = dashEffect
                )
                drawLine(
                    color = Color.Red,
                    start = Offset(padding, designY),
                    end = Offset(designX, designY),
                    strokeWidth = 2f,
                    pathEffect = dashEffect
                )
                
                // رسم النقطة
                drawCircle(
                    color = Color.Red,
                    radius = 8f,
                    center = Offset(designX, designY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(designX, designY)
                )
                
                // 5. النصوص والتسميات
                drawContext.canvas.nativeCanvas.apply {
                    val textPaint = android.graphics.Paint().apply {
                        textSize = 24f
                        color = android.graphics.Color.GRAY
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    
                    drawText("Time Period T (sec)", canvasWidth / 2, canvasHeight - 10f, textPaint)
                    
                    save()
                    rotate(-90f, 20f, canvasHeight / 2)
                    drawText("Spectral Accel. Sa/g", 20f, canvasHeight / 2, textPaint)
                    restore()
                }
            }
            
            // لوحة المعلومات السريعة
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoBox(label = "Design Period", value = "${"%.2f".format(designPeriod)} s")
                InfoBox(label = "Design Sa/g", value = "${"%.3f".format(spectrumValues.minByOrNull { abs(it.period - designPeriod) }?.spectralAcceleration ?: 0.0)}")
            }
        }
    }
}

@Composable
private fun InfoBox(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
