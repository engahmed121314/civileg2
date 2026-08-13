package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Professional Staircase Engineering Drawing — Genius Edition.
 */
@Composable
fun ProfessionalStairDrawing(
    stairWidth: Double,
    totalHeight: Double,
    totalLength: Double,
    riserHeight: Double,
    treadWidth: Double,
    slabThickness: Double,
    landingLength: Double = 0.0,
    landingThickness: Double = 0.0,
    mainRebarDia: Double,
    mainRebarSpacing: Double,
    topRebarDia: Double = 0.0,
    topRebarSpacing: Double = 0.0,
    distributionDia: Double = 0.0,
    distributionSpacing: Double = 0.0,
    cover: Double = 25.0,
    numberOfRisers: Int = 0,
    modifier: Modifier = Modifier,
    viewMode: Int = 0
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val density = this.density

        drawRect(color = Color(0xFF1A1A2E), size = size)

        val nRisers = if (numberOfRisers > 0) numberOfRisers else (totalHeight / riserHeight).toInt()
        val scale = min(w * 0.5f / totalLength.toFloat(), h * 0.5f / totalHeight.toFloat())
        val rw = treadWidth.toFloat() * scale
        val rh = riserHeight.toFloat() * scale
        val ts = slabThickness.toFloat() * scale
        val cov = cover.toFloat() * scale

        val ox = w * 0.25f - (totalLength.toFloat() * scale) / 2f
        val oy = h * 0.45f - (totalHeight.toFloat() * scale) / 2f

        // 1. ELEVATION VIEW
        if (viewMode == 0 || viewMode == 1) {
            var curX = ox; var curY = oy
            for (stepIdx in 0 until nRisers) {
                drawLine(Color(0xFF6B6B6B), Offset(curX, curY), Offset(curX, curY + rh), 3f)
                drawLine(Color(0xFF6B6B6B), Offset(curX, curY + rh), Offset(curX + rw, curY + rh), 3f)
                if (stepIdx % 5 == 0) drawTextAnnotated("${stepIdx + 1}", curX + 5f, curY + rh - 5f, Color.White, 8f * density)
                curX += rw; curY += rh
            }
            // Soffit
            val angle = atan2(riserHeight, treadWidth)
            val dx = ts * sin(angle).toFloat(); val dy = ts * cos(angle).toFloat()
            drawLine(Color(0xFF8A8A8A), Offset(ox + dx, oy - dy), Offset(curX + dx, curY - dy), 2.5f)
            
            // Rebar detail from result
            val mbX1 = ox + dx - cov * sin(angle).toFloat(); val mbY1 = oy - dy + cov * cos(angle).toFloat()
            val mbX2 = curX + dx - cov * sin(angle).toFloat(); val mbY2 = curY - dy + cov * cos(angle).toFloat()
            drawLine(Color(0xFF4A90D9), Offset(mbX1, mbY1), Offset(mbX2, mbY2), 3f)
            
            drawTextAnnotated("STAIR ELEVATION (B=%dmm)".format(stairWidth.toInt()), ox, oy - 40f, Color.White, 14f * density)
        }

        // 2. DATA TABLE
        if (viewMode == 0 || viewMode == 3) {
            val headers = listOf("Mark", "Dia.", "Spacing", "Comment")
            val rows = listOf(
                listOf("B1", "Ø${mainRebarDia.toInt()}", "@${mainRebarSpacing.toInt()} mm", "Main Steel"),
                listOf("D1", "Ø${distributionDia.toInt()}", "@${distributionSpacing.toInt()} mm", "Dist Steel"),
                listOf("ts", "${slabThickness.toInt()} mm", "-", "Waist (L_land=%d)".format(landingLength.toInt()))
            )
            drawReinforcementTable(20f, h * 0.72f, listOf(w*0.15f, w*0.15f, w*0.30f, w*0.30f), headers, rows, 30f, 35f, Color(0xFF1565C0), Color(0xFF222222), Color.White, 15f)
        }
    }
}

private fun DrawScope.drawTextAnnotated(text: String, x: Float, y: Float, color: Color, size: Float) {
    val p = android.graphics.Paint().apply { this.color = color.toArgb(); this.textSize = size; isFakeBoldText = true }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, p)
}
