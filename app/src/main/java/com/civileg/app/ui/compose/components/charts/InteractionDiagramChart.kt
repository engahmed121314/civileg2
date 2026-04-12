package com.civileg.app.ui.compose.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.civileg.app.ui.theme.ThemeColors

/**
 * بيانات نقطة على منحنى التفاعل
 */
data class ChartInteractionPoint(
    val axialLoad: Double,
    val moment: Double,
    val isSafe: Boolean = true,
    val utilizationRatio: Double = 0.0,
    val failureMode: String = "Normal"
)

/**
 * مخطط التفاعل للأعمدة (P-M Interaction Diagram)
 * يظهر العلاقة بين الحمل المحوري والعزم
 */
@Composable
fun InteractionDiagramChart(
    axialCapacities: List<Double>,     // kN
    momentCapacities: List<Double>,    // kN.m
    currentLoad: Pair<Double, Double>, // (Pu, Mu)
    modifier: Modifier = Modifier,
    onPointSelected: (ChartInteractionPoint) -> Unit = {}
) {
    var selectedPoint by remember { mutableStateOf<ChartInteractionPoint?>(null) }
    
    val chartColors = ThemeColors.chartColors()
    val safeColor = ThemeColors.safeColor()
    val unsafeColor = ThemeColors.unsafeColor()
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "P-M Interaction Diagram",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .pointerInput(axialCapacities, momentCapacities) {
                        detectTapGestures { offset ->
                            val point = getPointAtPosition(
                                offset, axialCapacities, momentCapacities,
                                size.width.toFloat(), size.height.toFloat()
                            )
                            selectedPoint = point
                            point?.let { onPointSelected(it) }
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val padding = 60f
                
                // نطاقات البيانات
                val maxAxial = (axialCapacities.maxOrNull() ?: 1000.0) * 1.1
                val maxMoment = (momentCapacities.maxOrNull() ?: 100.0) * 1.1
                
                // دالة تحويل الإحداثيات
                fun toCanvasX(moment: Double): Float = 
                    padding + (moment / maxMoment * (canvasWidth - 2 * padding)).toFloat()
                
                fun toCanvasY(axial: Double): Float = 
                    canvasHeight - padding - (axial / maxAxial * (canvasHeight - 2 * padding)).toFloat()
                
                // رسم الشبكة الخلفية
                drawGridLines(padding, canvasWidth, canvasHeight, maxAxial, maxMoment, ::toCanvasX, ::toCanvasY)
                
                // رسم المحاور
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
                
                // تسميات المحاور
                drawContext.canvas.nativeCanvas.apply {
                    val textPaint = android.graphics.Paint().apply {
                        textSize = 24f
                        color = android.graphics.Color.GRAY
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    
                    drawText("Moment (kN.m)", canvasWidth / 2, canvasHeight - 10f, textPaint)
                    
                    save()
                    rotate(-90f, 20f, canvasHeight / 2)
                    drawText("Axial Load (kN)", 20f, canvasHeight / 2, textPaint)
                    restore()
                }
                
                // رسم منحنى التفاعل
                if (axialCapacities.isNotEmpty()) {
                    val path = Path()
                    axialCapacities.forEachIndexed { index, axial ->
                        val moment = momentCapacities.getOrNull(index) ?: 0.0
                        val x = toCanvasX(moment)
                        val y = toCanvasY(axial)
                        
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    
                    // إغلاق المنحنى مع المحاور لتظليل المنطقة الآمنة
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(toCanvasX(0.0), toCanvasY(0.0))
                        close()
                    }
                    
                    drawPath(
                        path = fillPath,
                        color = safeColor.copy(alpha = 0.15f),
                        style = Fill
                    )
                    
                    drawPath(
                        path = path,
                        color = chartColors.firstOrNull() ?: Color.Blue,
                        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                
                // رسم نقطة الحمل الحالي
                val currentX = toCanvasX(currentLoad.second)
                val currentY = toCanvasY(currentLoad.first)
                
                // التأكد من أن النقطة داخل حدود الكانفاس
                if (currentX in 0f..canvasWidth && currentY in 0f..canvasHeight) {
                    drawCircle(
                        color = if (currentLoad.first <= (axialCapacities.maxOrNull() ?: 0.0)) safeColor else unsafeColor,
                        radius = 10f,
                        center = Offset(currentX, currentY)
                    )
                    
                    // دائرة بيضاء صغيرة في المنتصف للجمالية
                    drawCircle(
                        color = Color.White,
                        radius = 4f,
                        center = Offset(currentX, currentY)
                    )
                }
            }
            
            // وسيلة الإيضاح
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                LegendItem(color = chartColors.firstOrNull() ?: Color.Blue, label = "Capacity")
                LegendItem(color = safeColor, label = "Safe Zone")
                LegendItem(color = Color.Red, label = "Current Load")
            }
        }
    }
    
    selectedPoint?.let { point ->
        InteractionPointDialog(point = point, onDismiss = { selectedPoint = null })
    }
}

private fun getPointAtPosition(
    offset: Offset,
    axialCapacities: List<Double>,
    momentCapacities: List<Double>,
    canvasWidth: Float,
    canvasHeight: Float
): ChartInteractionPoint? {
    // منطق تقريبي لاكتشاف النقر القريب من المنحنى
    return null 
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGridLines(
    padding: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    maxAxial: Double,
    maxMoment: Double,
    toCanvasX: (Double) -> Float,
    toCanvasY: (Double) -> Float
) {
    val gridPaint = Color.LightGray.copy(alpha = 0.2f)
    
    // خطوط أفقية
    for (i in 1..4) {
        val y = toCanvasY(maxAxial * i / 5.0)
        drawLine(gridPaint, Offset(padding, y), Offset(canvasWidth - padding, y), 1f)
    }
    
    // خطوط رأسية
    for (i in 1..4) {
        val x = toCanvasX(maxMoment * i / 5.0)
        drawLine(gridPaint, Offset(x, padding), Offset(x, canvasHeight - padding), 1f)
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(10.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = color
        ) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InteractionPointDialog(
    point: ChartInteractionPoint,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تفاصيل نقطة التحميل") },
        text = {
            Column {
                Text("الحمل المحوري: ${"%.1f".format(point.axialLoad)} kN")
                Text("العزم: ${"%.1f".format(point.moment)} kN.m")
                Text("الحالة: ${if (point.isSafe) "آمن" else "غير آمن"}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}
