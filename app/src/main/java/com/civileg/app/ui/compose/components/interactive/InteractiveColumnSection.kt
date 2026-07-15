package com.civileg.app.ui.compose.components.interactive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.civileg.app.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.ui.theme.ThemeColors
import kotlin.math.PI

/**
 * بيانات السيخ المختار
 */
data class RebarInfo(
    val index: Int,
    val diameter: Double,
    val area: Double,
    val developmentLength: Double
)

/**
 * رسم تفاعلي لمقطع العمود مع إمكانية التكبير والحركة والنقر لعرض التفاصيل.
 */
@Composable
fun InteractiveColumnSection(
    width: Double,              // mm
    depth: Double,              // mm
    reinforcementResult: ReinforcementResult,
    showDimensions: Boolean = true,
    showRebarDetails: Boolean = true,
    modifier: Modifier = Modifier,
    onRebarSelected: (RebarInfo) -> Unit = {}
) {
    var scale by remember { mutableStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selectedRebar by remember { mutableStateOf<RebarInfo?>(null) }
    
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
                        onTap = { tapOffset ->
                            val rebar = getRebarAtPosition(
                                tapOffset, offset, scale, 
                                width, depth, reinforcementResult,
                                size.width.toFloat(), size.height.toFloat()
                            )
                            selectedRebar = rebar
                            rebar?.let { onRebarSelected(it) }
                        },
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
            
            // حساب مقياس الرسم الأساسي ليناسب الشاشة
            val baseScale = minOf(
                canvasWidth / (width.toFloat() * 1.5f),
                canvasHeight / (depth.toFloat() * 1.5f)
            )
            val currentScale = baseScale * scale
            
            // نقطة المركز مع الإزاحة
            val centerX = canvasWidth / 2 + offset.x
            val centerY = canvasHeight / 2 + offset.y
            
            val rectWidth = (width * currentScale).toFloat()
            val rectHeight = (depth * currentScale).toFloat()
            val left = centerX - rectWidth / 2
            val top = centerY - rectHeight / 2
            
            // 1. رسم المقطع الخرساني
            drawRect(
                color = concreteColor,
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                style = Stroke(width = 4f * scale)
            )
            
            // تظليل داخلي بسيط للخرسانة
            drawConcreteHatching(left, top, rectWidth, rectHeight, concreteColor.copy(alpha = 0.1f))
            
            // 2. الغطاء الخرساني (Concrete Cover) - افتراضي 40 مم
            val cover = 40f * currentScale
            
            // 3. توزيع أسياخ التسليح
            val rebarPositions = calculateRebarPositions(
                width, depth, reinforcementResult, 
                left, top, rectWidth, rectHeight, cover
            )
            
            rebarPositions.forEachIndexed { index, pos ->
                val barRadius = (reinforcementResult.barDiameter * currentScale / 2.0).coerceAtLeast(4.0).toFloat()
                val isSelected = selectedRebar?.index == index
                
                // تحديد السيخ المختار باللون الأصفر
                if (isSelected) {
                    drawCircle(
                        color = Color.Yellow,
                        radius = barRadius + 4f,
                        center = pos,
                        style = Stroke(width = 2f)
                    )
                }
                
                // رسم السيخ (أحمر إذا كان غير آمن، ولون الحديد إذا كان آمناً)
                drawCircle(
                    color = if (reinforcementResult.isSafe) steelColor else Color.Red,
                    radius = barRadius,
                    center = pos
                )
                
                // عرض رقم السيخ عند التكبير
                if (showRebarDetails && scale > 0.8f) {
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(
                            "${index + 1}",
                            pos.x,
                            pos.y + barRadius + 25f,
                            android.graphics.Paint().apply {
                                textSize = 24f
                                color = android.graphics.Color.GRAY
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                        )
                    }
                }
            }
            
            // 4. رسم الأبعاد (Dimensions)
            if (showDimensions) {
                drawDimensions(left, top, rectWidth, rectHeight, width, depth, dimensionColor)
            }
        }
    }
    
    // عرض نافذة التفاصيل عند النقر
    selectedRebar?.let { rebar ->
        RebarDetailDialog(rebar, onDismiss = { selectedRebar = null })
    }
}

private fun calculateRebarPositions(
    width: Double, depth: Double,
    result: ReinforcementResult,
    left: Float, top: Float,
    rectWidth: Float, rectHeight: Float,
    cover: Float
): List<Offset> {
    val positions = mutableListOf<Offset>()
    val bars = result.numberOfBars
    
    // توزيع الأسياخ في الأركان الأربعة كبداية
    positions.add(Offset(left + cover, top + cover))
    positions.add(Offset(left + rectWidth - cover, top + cover))
    positions.add(Offset(left + cover, top + rectHeight - cover))
    positions.add(Offset(left + rectWidth - cover, top + rectHeight - cover))
    
    // توزيع باقي الأسياخ على الجوانب
    if (bars > 4) {
        val sideBars = (bars - 4) / 2
        val spacing = (rectHeight - 2 * cover) / (sideBars + 1)
        for (i in 1..sideBars) {
            positions.add(Offset(left + cover, top + cover + i * spacing))
            positions.add(Offset(left + rectWidth - cover, top + cover + i * spacing))
        }
    }
    return positions.take(bars)
}

private fun getRebarAtPosition(
    tapOffset: Offset, offset: Offset, scale: Float,
    width: Double, depth: Double, result: ReinforcementResult,
    cW: Float, cH: Float
): RebarInfo? {
    val baseScale = minOf(cW / (width.toFloat() * 1.5f), cH / (depth.toFloat() * 1.5f))
    val currentScale = baseScale * scale
    val centerX = cW / 2 + offset.x
    val centerY = cH / 2 + offset.y
    val rectW = (width * currentScale).toFloat()
    val rectH = (depth * currentScale).toFloat()
    val left = centerX - rectW / 2
    val top = centerY - rectH / 2
    val cover = 40f * currentScale

    val positions = calculateRebarPositions(width, depth, result, left, top, rectW, rectH, cover)
    positions.forEachIndexed { index, pos ->
        if ((tapOffset - pos).getDistance() <= 40f) {
            return RebarInfo(
                index = index,
                diameter = result.barDiameter,
                area = PI * result.barDiameter * result.barDiameter / 4.0,
                developmentLength = result.barDiameter * 50.0
            )
        }
    }
    return null
}

private fun DrawScope.drawConcreteHatching(l: Float, t: Float, w: Float, h: Float, color: Color) {
    val step = 30f
    for (i in 0..(w / step).toInt()) {
        drawLine(color, Offset(l + i * step, t), Offset(l + i * step, t + h), 1f)
    }
}

private fun DrawScope.drawDimensions(l: Float, t: Float, w: Float, h: Float, aW: Double, aD: Double, color: Color) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            textSize = 32f
            this.color = android.graphics.Color.parseColor("#1976D2")
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        // البعد العرضي
        drawText("${aW.toInt()} mm", l + w/2, t - 25f, paint)
        
        // البعد الرأسي (مع التدوير)
        save()
        rotate(-90f, l - 40f, t + h/2)
        drawText("${aD.toInt()} mm", l - 40f, t + h/2, paint)
        restore()
    }
}

@Composable
private fun RebarDetailDialog(info: RebarInfo, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("تفاصيل السيخ #${info.index + 1}") },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text("القطر: Ø${info.diameter.toInt()} مم")
                androidx.compose.material3.Text("المساحة: ${"%.1f".format(info.area)} مم²")
                androidx.compose.material3.Text("طول الرباط التقديري: ${info.developmentLength.toInt()} مم")
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("Close")
            }
        }
    )
}
