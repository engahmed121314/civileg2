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
import com.civileg.app.domain.entities.StirrupZone
import kotlin.math.min

// ============================================================================
// COLOR PALETTE — strictly follows engineering workshop standards
// ============================================================================

private object C {
    val Concrete = Color(0xFF6B6B6B)
    val ConcreteDark = Color(0xFF4A4A4A)
    val Bar = Color(0xFF4A90D9)
    val Tie = Color(0xFF9B59B6)
    val White = Color(0xFFFFFFFF)
    val Dim = Color(0xFFAAAAAA)
}

@Composable
fun ProfessionalColumnDrawing(
    columnWidth: Double,
    columnDepth: Double,
    columnHeight: Double,
    longitudinalBars: List<BarInfo>,
    tieDia: Double,
    tieSpacing: Double,
    cover: Double,
    isSpiral: Boolean = false,
    sectionType: String = "Rectangular",
    zones: List<StirrupZone> = emptyList(),
    viewMode: Int = 0,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val W = size.width
        val H = size.height
        val density = this.density

        drawRect(color = Color(0xFF1A1A2E), size = size)

        val scale = min(W * 0.4f / columnWidth.toFloat(), H * 0.6f / columnHeight.toFloat())
        val cw = columnWidth.toFloat() * scale
        val ch = columnHeight.toFloat() * scale
        val cd = columnDepth.toFloat() * scale * 0.4f
        val coverPx = cover.toFloat() * scale

        val ox = W * 0.25f - cw / 2f
        val oy = H * 0.45f - ch / 2f

        // 1. ELEVATION VIEW
        if (viewMode == 0 || viewMode == 1) {
            // Floor slabs
            drawRect(color = C.ConcreteDark, topLeft = Offset(ox - 100f, oy - 150f), size = Size(cw + 200f, 150f))
            drawRect(color = C.ConcreteDark, topLeft = Offset(ox - 100f, oy + ch), size = Size(cw + 200f, 150f))

            drawRect(color = C.Concrete, topLeft = Offset(ox, oy), size = Size(cw, ch))
            
            // Vertical bars strictly from longitudinalBars
            val frontBars = longitudinalBars.filter { it.y <= columnDepth * 0.2 }
            frontBars.forEach { bar ->
                val bx = ox + (bar.x.toFloat() / columnWidth.toFloat()) * cw
                drawLine(color = C.Bar, start = Offset(bx, oy - 100f), end = Offset(bx, oy + ch + 100f), strokeWidth = 3f)
                // Crank
                drawLine(color = C.Bar, start = Offset(bx, oy + ch), end = Offset(bx - 15f, oy + ch + 150f), strokeWidth = 3f)
            }

            // Ties and Zones strictly from zones result
            zones.forEach { zone ->
                val zStart = oy + ch - (zone.endLocation.toFloat() / columnHeight.toFloat()) * ch
                val zEnd = oy + ch - (zone.startLocation.toFloat() / columnHeight.toFloat()) * ch
                val zSp = (zone.spacing.toFloat() / columnHeight.toFloat()) * ch
                var curY = zEnd
                while (curY > zStart) {
                    drawLine(color = C.Tie, start = Offset(ox + coverPx, curY), end = Offset(ox + cw - coverPx, curY), strokeWidth = 1.5f)
                    curY -= zSp
                }
                drawTextAnnotated("lo: Ø${zone.diameter}@${zone.spacing.toInt()}", ox + cw + 20f, (zStart+zEnd)/2, C.White, 12f * density)
            }

            drawVerticalDimension(oy, oy + ch, ox - 50f, "Hn=${columnHeight.toInt()}", C.White, 12f * density)
        }

        // 2. CROSS SECTION VIEW
        if (viewMode == 0 || viewMode == 2) {
            val secX = W * 0.75f - cw / 2f
            val secY = H * 0.35f - cw / 2f
            drawRect(color = C.Concrete, topLeft = Offset(secX, secY), size = Size(cw, cw)) // simplified depth for UI
            drawRect(color = C.Tie, topLeft = Offset(secX + coverPx, secY + coverPx), size = Size(cw - 2*coverPx, cw - 2*coverPx), style = Stroke(2f))
            
            longitudinalBars.forEach { bar ->
                val bx = secX + (bar.x.toFloat() / columnWidth.toFloat()) * cw
                val by = secY + (bar.y.toFloat() / columnDepth.toFloat()) * cw
                drawCircle(color = C.Bar, radius = 5f, center = Offset(bx, by))
            }
            drawTextAnnotated("SECTION A-A", secX, secY - 20f, C.White, 16f * density)
        }
        
        // 3. TABLE
        if (viewMode == 0 || viewMode == 3) {
            val headers = listOf("Parameter", "Design Value")
            val rows = listOf(
                listOf("Load Pu", "${longitudinalBars.size} Bars"),
                listOf("Main Steel", "Ø${longitudinalBars.firstOrNull()?.diameter?.toInt()}"),
                listOf("Confinement", "lo = ${zones.firstOrNull()?.endLocation?.toInt()} mm")
            )
            drawReinforcementTable(20f, H * 0.78f, listOf(W * 0.4f, W * 0.4f), headers, rows, 30f, 35f, C.ConcreteDark, Color(0xFF222222), C.White, 16f)
        }
    }
}

private fun DrawScope.drawTextAnnotated(text: String, x: Float, y: Float, color: Color, size: Float) {
    val p = android.graphics.Paint().apply { this.color = color.toArgb(); this.textSize = size; isFakeBoldText = true }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, p)
}

data class BarInfo(val x: Double, val y: Double, val diameter: Double, val isCorner: Boolean = false)
