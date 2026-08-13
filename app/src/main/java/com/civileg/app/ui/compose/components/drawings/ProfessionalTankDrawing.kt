package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.min

/**
 * Professional Tank Engineering Drawing — Genius Edition.
 * strictly data-driven for rectangular/circular tanks.
 */
@Composable
fun ProfessionalTankDrawing(
    tankType: String,
    length: Double,
    width: Double,
    height: Double,
    wallThickness: Double,
    baseThickness: Double,
    waterLevel: Double,
    verticalRebarDia: Double,
    verticalRebarSpacing: Double,
    horizontalRebarDia: Double,
    horizontalRebarSpacing: Double,
    foundationDepth: Double = 0.0,
    modifier: Modifier = Modifier,
    viewMode: Int = 0
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val density = this.density

        drawRect(color = Color(0xFF1A1A2E), size = size)

        val totalH = height + baseThickness/1000.0 + foundationDepth
        val scale = min(w * 0.45f / length.toFloat(), h * 0.55f / totalH.toFloat())
        val tw = (wallThickness / 1000.0 * scale).toFloat()
        val tb = (baseThickness / 1000.0 * scale).toFloat()
        val th = (height.toFloat() * scale)
        val tl = (length.toFloat() * scale)
        val cover = (50.0 / 1000.0 * scale).toFloat()

        val ox = w * 0.35f - tl / 2f
        val oy = h * 0.40f - th / 2f

        // 1. SECTION VIEW
        if (viewMode == 0 || viewMode == 1) {
            // Soil if underground
            if (foundationDepth > 0) {
                drawRect(color = Color(0xFF3E2723), topLeft = Offset(ox - 200f, oy + th), size = Size(tl + 2*tw + 400f, foundationDepth.toFloat()*scale))
            }
            // Base
            drawRect(color = Color(0xFF6B6B6B), topLeft = Offset(ox, oy + th), size = Size(tl + 2*tw, tb))
            // Walls
            drawRect(color = Color(0xFF8A8A8A), topLeft = Offset(ox, oy), size = Size(tw, th))
            drawRect(color = Color(0xFF8A8A8A), topLeft = Offset(ox + tl + tw, oy), size = Size(tw, th))
            
            val wl = (waterLevel.toFloat() * scale)
            drawLine(Color(0xFF4A90D9), Offset(ox + tw + 10f, oy + th - wl), Offset(ox + tl + tw - 10f, oy + th - wl), 2f)
            
            // Reinforcement
            drawLine(Color(0xFF2255CC), Offset(ox + tw - cover, oy + 20f), Offset(ox + tw - cover, oy + th + tb - cover), 3f)
            drawLine(Color(0xFF2255CC), Offset(ox + tl + tw + cover, oy + 20f), Offset(ox + tl + tw + cover, oy + th + tb - cover), 3f)

            drawVerticalDimension(oy, oy + th + tb, ox - 35f, "H=%dmm".format(((height + baseThickness/1000.0)*1000).toInt()), Color.White, 12f * density)
        }

        // 2. DATA TABLE
        if (viewMode == 0 || viewMode == 3) {
            val headers = listOf("Parameter", "Value", "Notes")
            val rows = listOf(
                listOf("Capacity", "%.1f m3".format(length * width * height), "Total"),
                listOf("Wall Steel", "Ø%d@%d".format(verticalRebarDia.toInt(), verticalRebarSpacing.toInt()), "Vertical"),
                listOf("Base Steel", "Ø%d@%d".format(horizontalRebarDia.toInt(), horizontalRebarSpacing.toInt()), "Hoop"),
                listOf("Type", tankType, if(foundationDepth > 0) "Buried" else "Ground")
            )
            drawReinforcementTable(20f, h * 0.72f, listOf(w*0.35f, w*0.30f, w*0.25f), headers, rows, 30f, 35f, Color(0xFF1565C0), Color(0xFF222222), Color.White, 15f)
        }
    }
}

private fun DrawScope.drawTextAnnotated(text: String, x: Float, y: Float, color: Color, size: Float) {
    val p = android.graphics.Paint().apply { this.color = color.toArgb(); this.textSize = size; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, p)
}
