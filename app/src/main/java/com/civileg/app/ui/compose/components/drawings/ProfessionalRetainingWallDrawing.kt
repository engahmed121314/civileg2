package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.min
import kotlin.math.tan

/**
 * Professional Retaining Wall Engineering Drawing — Genius Edition.
 * matching inputs and stability calculations strictly.
 */
@Composable
fun ProfessionalRetainingWallDrawing(
    wallHeight: Double,
    wallTopThickness: Double,
    wallBottomThickness: Double,
    baseWidth: Double,
    baseThickness: Double,
    toeLength: Double,
    heelLength: Double,
    mainRebarDia: Double,
    mainRebarSpacing: Double,
    distRebarDia: Double,
    distRebarSpacing: Double,
    baseRebarDia: Double,
    baseRebarSpacing: Double,
    cover: Double,
    backfillAngle: Double = 30.0,
    hasKey: Boolean = false,
    keyDepth: Double = 0.0,
    fsOverturning: Double = 2.0,
    fsSliding: Double = 1.5,
    maxBearingPressure: Double = 0.0,
    allowableBearingPressure: Double = 200.0,
    viewMode: Int = 0,
    modifier: Modifier = Modifier
) {
    val palette = drawingColors()

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val density = this.density

        drawRect(color = Color(0xFF1A1A2E), size = size)

        val totalH = wallHeight + baseThickness
        val scale = min(w * 0.45f / baseWidth.toFloat(), h * 0.6f / totalH.toFloat())
        val bh = baseThickness.toFloat() * scale
        val wh = wallHeight.toFloat() * scale
        val bw = baseWidth.toFloat() * scale
        val wt = wallBottomThickness.toFloat() * scale
        val toe = toeLength.toFloat() * scale
        val cov = cover.toFloat() * scale

        val ox = w * 0.35f - bw / 2f
        val oy = h * 0.40f - totalH.toFloat() * scale / 2f

        // 1. SECTION VIEW
        if (viewMode == 0 || viewMode == 1) {
            // Base
            drawRect(color = Color(0xFF6B6B6B), topLeft = Offset(ox, oy + wh), size = Size(bw, bh))
            // Stem
            val stemPath = Path().apply {
                moveTo(ox + toe, oy + wh)
                lineTo(ox + toe + (wallBottomThickness.toFloat() - wallTopThickness.toFloat())*0.5f*scale, oy)
                lineTo(ox + toe + (wallBottomThickness.toFloat() + wallTopThickness.toFloat())*0.5f*scale, oy)
                lineTo(ox + toe + wt, oy + wh)
                close()
            }
            drawPath(path = stemPath, color = Color(0xFF8A8A8A))
            
            // Rebar
            drawLine(palette.rebarBlue, Offset(ox + toe + wt - cov, oy + wh - cov), Offset(ox + toe + wt*0.8f - cov, oy + cov), 3f)
            drawTextWithBackground(mainRebarDia.toInt().toString(), ox + toe + wt - cov, oy + wh/2, palette.rebarBlue, Color.Black, 12f)

            // Pressure diagram
            val px = ox + bw + 150f
            drawLine(Color(0xFFFF5722), Offset(px, oy), Offset(px + 80f, oy + wh), 2f)
            drawLine(Color(0xFFFF5722), Offset(px, oy), Offset(px, oy + wh), 2f)
            drawTextAnnotated("Pa resultant", px + 90f, oy + wh * 0.66f, Color(0xFFFF5722), 12f * density)

            drawVerticalDimension(oy, oy + wh + bh, ox - 35f, "H_tot=${(totalH*1000).toInt()}mm", Color.White, 12f * density)
        }

        // 2. DATA TABLE
        if (viewMode == 0 || viewMode == 3) {
            val headers = listOf("Check", "Factor of Safety", "Status")
            val rows = listOf(
                listOf("Overturning", "%.2f".format(fsOverturning), if(fsOverturning >= 1.5) "PASS" else "FAIL"),
                listOf("Sliding", "%.2f".format(fsSliding), if(fsSliding >= 1.5) "PASS" else "FAIL"),
                listOf("Soil Pressure", "%.1f kPa".format(maxBearingPressure), if(maxBearingPressure <= allowableBearingPressure) "PASS" else "FAIL")
            )
            drawReinforcementTable(20f, h * 0.72f, listOf(w*0.35f, w*0.3f, w*0.25f), headers, rows, 30f, 35f, Color(0xFF1565C0), Color(0xFF222222), Color.White, 14f)
        }
    }
}

private fun DrawScope.drawTextAnnotated(text: String, x: Float, y: Float, color: Color, size: Float) {
    val p = android.graphics.Paint().apply { this.color = color.toArgb(); this.textSize = size; isFakeBoldText = true }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, p)
}

private fun DrawScope.drawTextWithBackground(text: String, x: Float, y: Float, textColor: Color, bgColor: Color, textSize: Float) {
    val p = android.graphics.Paint().apply { this.textSize = textSize * density; color = textColor.toArgb(); isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER }
    val metrics = p.fontMetrics; val tw = p.measureText(text); val th = metrics.descent - metrics.ascent
    drawRect(color = bgColor, topLeft = Offset(x - tw/2 - 4f, y + metrics.ascent), size = Size(tw + 8f, th))
    drawContext.canvas.nativeCanvas.drawText(text, x, y, p)
}
