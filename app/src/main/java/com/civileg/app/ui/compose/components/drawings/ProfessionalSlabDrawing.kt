package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max
import kotlin.math.min

/**
 * Professional Slab Engineering Drawing — Data-Driven Edition.
 * Strictly uses passed results to ensure UI matches calculations and CAD.
 */
@Composable
fun ProfessionalSlabDrawing(
    slabType: String,
    slabThickness: Double,
    spanX: Double,
    spanY: Double,
    mainRebarDia: Double,
    mainRebarSpacing: Double,
    distRebarDia: Double,
    distRebarSpacing: Double,
    cover: Double,
    dropPanelSize: Double = 0.0,
    ribWidth: Double = 0.0,
    ribSpacing: Double = 0.0,
    viewMode: Int = 0,
    modifier: Modifier = Modifier,
    momentX: Double = 0.0,
    momentY: Double = 0.0,
    factoredLoad: Double = 0.0,
    fcu: Double = 25.0,
    fy: Double = 360.0,
    isSafe: Boolean = true,
    utilizationRatio: Double = 0.0
) {
    val palette = drawingColors()

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val density = this.density

        fun dt(text: String, x: Float, y: Float, color: Color = palette.textOnHeader, size: Float = 11f, bold: Boolean = false, center: Boolean = true) = 
            drawTextAnnotated(text, x, y, color, size * density, center = center, bold = bold)

        // ── Layout zones ─────────────────────────────────────────────
        val margin = 28f
        val planTop = h * 0.05f
        val planLeft = margin + 50f
        val planRight = w - margin
        val planBottom = h * 0.50f
        val planW = planRight - planLeft
        val planDrawH = planBottom - planTop

        val isHordi = slabType.contains("Hordi", ignoreCase = true) || slabType.contains("هردي", ignoreCase = true) || slabType.contains("Hollow", ignoreCase = true)
        val isWaffle = slabType.contains("Waffle", ignoreCase = true) || slabType.contains("وافل", ignoreCase = true)
        val isFlat = slabType.contains("Flat", ignoreCase = true) || slabType.contains("مسطحة", ignoreCase = true)
        val spanRatio = if (spanY > 0) spanX / spanY else 1.0

        val scaleX = planW / spanX.toFloat()
        val scaleY = planDrawH / spanY.toFloat()
        val scale = min(scaleX, scaleY) * 0.85f
        val drawSpanX = spanX.toFloat() * scale
        val drawSpanY = spanY.toFloat() * scale
        val slabLeft = planLeft + (planW - drawSpanX) / 2f
        val slabTop = planTop + (planDrawH - drawSpanY) / 2f
        val slabRight = slabLeft + drawSpanX
        val slabBottom = slabTop + drawSpanY

        // HEADER
        val headerColor = if (isSafe) palette.safeGreen else palette.unsafeRed
        drawRect(color = headerColor, topLeft = Offset(0f, 0f), size = Size(w, 36f * density))
        dt("${"SLAB DETAIL"} — ${slabType.uppercase()} • U=${(utilizationRatio * 100).toInt()}%", w / 2f, 24f * density, palette.textOnHeader, 11f, bold = true)

        if (viewMode == 0 || viewMode == 1) {
            drawRect(color = palette.planBackground, topLeft = Offset(slabLeft - 6f, slabTop - 6f), size = Size(drawSpanX + 12f, drawSpanY + 12f))
            drawRect(color = palette.concreteFill, topLeft = Offset(slabLeft, slabTop), size = Size(drawSpanX, drawSpanY))
            drawHatchPattern(slabLeft, slabTop, drawSpanX, drawSpanY, 22f, 45f, palette.hatchColor)

            // Reinforcement lines
            val mainStepPx = (mainRebarSpacing.toFloat() / 1000f * scale).coerceAtLeast(15f)
            var curY = slabTop + mainStepPx / 2
            while (curY < slabBottom) {
                drawLine(palette.rebarBlue, Offset(slabLeft + 10f, curY), Offset(slabRight - 10f, curY), 2f)
                curY += mainStepPx
            }
            
            val distStepPx = (distRebarSpacing.toFloat() / 1000f * scale).coerceAtLeast(15f)
            var curX = slabLeft + distStepPx / 2
            while (curX < slabRight) {
                drawLine(palette.distBarGreen, Offset(curX, slabTop + 10f), Offset(curX, slabBottom - 10f), 1.2f)
                curX += distStepPx
            }

            drawRect(palette.concreteStroke, Offset(slabLeft, slabTop), Size(drawSpanX, drawSpanY), style = Stroke(2.5f))
            drawHorizontalDimension(slabLeft, slabRight, slabTop - 20f, "${(spanX * 1000).toInt()} mm", palette.dimColor, 10f * density)
            drawVerticalDimension(slabTop, slabBottom, slabLeft - 20f, "${(spanY * 1000).toInt()} mm", palette.dimColor, 10f * density)
        }

        if (viewMode == 0 || viewMode == 3) {
            val tblTop = if (viewMode == 0) h * 0.65f else 50f * density
            val tblWidth = w - 2 * margin
            val effD = (slabThickness - cover).coerceAtLeast(50.0)
            val z_arm = 0.87 * effD
            
            val asReqX = momentX * 1e6 / (fy * z_arm)
            val asReqY = momentY * 1e6 / (fy * z_arm)
            val asProvX = (Math.PI * mainRebarDia * mainRebarDia / 4.0) * (1000.0 / mainRebarSpacing)
            val asProvY = (Math.PI * distRebarDia * distRebarDia / 4.0) * (1000.0 / distRebarSpacing)

            val rows = mutableListOf<List<String>>()
            rows.add(listOf("①", "Main Bottom (X)", mainRebarDia.toInt().toString(), mainRebarSpacing.toInt().toString(), "${(spanX * 1000).toInt()}", "%.0f".format(asReqX), "%.0f".format(asProvX), "%.1f".format(asProvX * spanX * 0.00785)))
            rows.add(listOf("②", "Dist. Bottom (Y)", distRebarDia.toInt().toString(), distRebarSpacing.toInt().toString(), "${(spanY * 1000).toInt()}", "%.0f".format(asReqY), "%.0f".format(asProvY), "%.1f".format(asProvY * spanY * 0.00785)))
            
            val colWidths = listOf(tblWidth * 0.05f, tblWidth * 0.25f, tblWidth * 0.08f, tblWidth * 0.12f, tblWidth * 0.12f, tblWidth * 0.12f, tblWidth * 0.12f, tblWidth * 0.14f)
            val headers = listOf("Mark", "Direction", "Dia", "Spacing", "Length", "As Req", "As Prov", "Wt(kg)")
            
            drawReinforcementTable(margin, tblTop, colWidths, headers, rows, 30f * density, 35f * density, palette.tableHeaderBg, palette.tableRowAltColor, palette.tableCellText, 10f * density)
        }
    }
}
