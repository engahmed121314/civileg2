package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.entities.StirrupZone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ============================================================================
// COLOR PALETTE — strictly follows engineering workshop standards
// ============================================================================

private val RebarBlue = Color(0xFF4A90D9)
private val TopRebarBlue = Color(0xFF7EC8E3)
private val StirrupPurple = Color(0xFF9B59B6)
private val ConcreteGray = Color(0xFF6B6B6B)
private val ConcreteTopGray = Color(0xFF8A8A8A)
private val ConcreteSideGray = Color(0xFF505050)
private val DimensionWhite = Color(0xFFFFFFFF)
private val ExtensionGray = Color(0xFFAAAAAA)
private val TableHeaderBg = Color(0x33FFFFFF)
private val TableRowAlt = Color(0x1AFFFFFF)
private val DevLengthColor = Color(0xFF4A90D9)
private val LapSpliceColor = Color(0xFFE74C3C)
private val SupportColor = Color(0xFFCCCCCC)
private val HatchColor = Color(0x99AAAAAA)

// ============================================================================
// COMPOSABLE ENTRY POINT
// ============================================================================

/**
 * Professional Reinforced Concrete Beam Workshop Drawing.
 * Strictly uses passed results to ensure UI matches calculations and CAD.
 */
@Composable
fun ProfessionalBeamDrawing(
    beamWidth: Double,         // mm
    beamDepth: Double,         // mm
    span: Double,              // mm
    mainRebarDia: Double,      // mm
    mainRebarCount: Int,       // n
    stirrupDia: Double,        // mm
    stirrupSpacing: Double,    // mm
    cover: Double,             // mm
    developmentLength: Double, // mm
    lapLength: Double,         // mm
    isContinuous: Boolean = false,
    hasTopSteel: Boolean = false,
    topRebarDia: Double = 0.0,
    topRebarCount: Int = 0,
    zones: List<StirrupZone> = emptyList(),
    modifier: Modifier = Modifier,
    viewMode: Int = 0
) {
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val cw = size.width
        val ch = size.height

        val angleX = 0.30f
        val angleY = 0.20f

        val elevationFrac = when (viewMode) {
            1 -> 0.88f
            0 -> 0.40f
            else -> 0.08f
        }
        val sectionFrac = when (viewMode) {
            2 -> 0.88f
            0 -> 0.16f
            else -> 0.08f
        }
        val mainBottom = ch * elevationFrac
        val sectionZoneTop = ch * (elevationFrac + 0.04f)
        val sectionZoneBottom = ch * (elevationFrac + 0.04f + sectionFrac)

        val sideMargin = 60f
        val mainLeft = sideMargin
        val mainRight = cw - sideMargin
        val mainTop = 50f

        val availableW = mainRight - mainLeft
        val availableH = mainBottom - mainTop - 60f
        val scaleW = availableW / span.toFloat()
        val scaleH = availableH / beamDepth.toFloat()
        val scale = min(scaleW, scaleH) * 0.90f

        val beamDrawW = span.toFloat() * scale
        val beamDrawH = beamDepth.toFloat() * scale
        val beamDrawD = beamWidth.toFloat() * scale * 0.35f

        val beamLeft = mainLeft + (availableW - beamDrawW) / 2f
        val beamTop = mainTop + 60f + beamDrawD * angleY
        val beamRight = beamLeft + beamDrawW
        val beamBottom = beamTop + beamDrawH
        val coverPx = cover.toFloat() * scale

        // ── Drawing Backdrop ──
        drawRect(color = Color(0xFF1A1A2E), size = size)

        // 1. COLUMN SUPPORTS (Schematic)
        val supW = 400.0f * scale
        val supH = 1200.0f * scale
        drawRect(color = ConcreteGray, topLeft = Offset(beamLeft - supW, beamBottom), size = Size(supW, supH))
        drawRect(color = ConcreteGray, topLeft = Offset(beamRight, beamBottom), size = Size(supW, supH))
        drawRect(color = DimensionWhite.copy(alpha = 0.3f), topLeft = Offset(beamLeft - supW, beamBottom), size = Size(supW, supH), style = Stroke(1f))
        drawRect(color = DimensionWhite.copy(alpha = 0.3f), topLeft = Offset(beamRight, beamBottom), size = Size(supW, supH), style = Stroke(1f))

        // ═══ ELEVATION VIEW ═══
        if (viewMode == 0 || viewMode == 1) {
            draw3DBeamBody(
                left = beamLeft, top = beamTop,
                w = beamDrawW, h = beamDrawH, d = beamDrawD,
                angleX = angleX, angleY = angleY
            )
            
            // Main Bottom Reinforcement strictly matching result
            val mbY = beamBottom - coverPx
            val hook = 150f * scale
            val anch = 300f * scale
            drawLine(color = RebarBlue, start = Offset(beamLeft - anch, mbY), end = Offset(beamRight + anch, mbY), strokeWidth = 3f)
            drawLine(color = RebarBlue, start = Offset(beamLeft - anch, mbY), end = Offset(beamLeft - anch, mbY + hook), strokeWidth = 3f)
            drawLine(color = RebarBlue, start = Offset(beamRight + anch, mbY), end = Offset(beamRight + anch, mbY + hook), strokeWidth = 3f)
            
            // Labels
            drawTextWithBackground("${mainRebarCount}Ø${mainRebarDia.toInt()}", beamLeft + beamDrawW/2, mbY - 20f, RebarBlue, Color(0xCC000000), 16f)

            // Confinement zones strictly from zones result
            zones.forEach { zone ->
                val zStart = beamLeft + zone.startLocation.toFloat() * scale
                val zEnd = beamLeft + zone.endLocation.toFloat() * scale
                val zSp = (zone.spacing.toFloat() * scale).coerceAtLeast(10f)
                var sx = zStart
                while (sx < zEnd - 1f) {
                    drawLine(color = StirrupPurple, start = Offset(sx, beamTop + coverPx), end = Offset(sx, beamBottom - coverPx), strokeWidth = 1.5f)
                    sx += zSp
                }
                val mz = (zStart + zEnd) / 2.0f
                drawCircle(color = StirrupPurple.copy(alpha = 0.4f), radius = 15f, center = Offset(mz, beamTop - 30f))
                drawTextAnnotated("Ø${zone.diameter}@${zone.spacing.toInt()}", mz - 40f, beamTop - 30f, StirrupPurple, 14f)
            }

            drawDimensionLines(
                beamLeft = beamLeft, beamTop = beamTop,
                beamRight = beamRight, beamBottom = beamBottom,
                beamDrawH = beamDrawH, scale = scale,
                beamWidth = beamWidth, beamDepth = beamDepth,
                span = span, cover = cover, stirrupSpacing = stirrupSpacing
            )
        }

        // ═══ CROSS-SECTION VIEW ═══
        if (viewMode == 0 || viewMode == 2) {
            drawSectionInset(
                cw = cw, ch = ch, zoneTop = sectionZoneTop, zoneBottom = sectionZoneBottom,
                beamWidth = beamWidth, beamDepth = beamDepth,
                mainRebarDia = mainRebarDia, mainRebarCount = mainRebarCount,
                stirrupDia = stirrupDia, cover = cover,
                hasTopSteel = hasTopSteel, topRebarDia = topRebarDia, topRebarCount = topRebarCount
            )
        }

        // ═══ REINFORCEMENT TABLE ═══
        if (viewMode == 0 || viewMode == 3) {
            drawReinforcementSchedule(
                cw = cw, ch = ch, beamWidth = beamWidth, beamDepth = beamDepth, span = span,
                mainRebarDia = mainRebarDia, mainRebarCount = mainRebarCount,
                stirrupDia = stirrupDia, stirrupSpacing = stirrupSpacing,
                hasTopSteel = hasTopSteel, topRebarDia = topRebarDia, topRebarCount = topRebarCount,
                developmentLength = developmentLength, cover = cover,
                tableZoneTop = if (viewMode == 3) ch * 0.06f else 0f
            )
        }
    }
}

private fun DrawScope.draw3DBeamBody(left: Float, top: Float, w: Float, h: Float, d: Float, angleX: Float, angleY: Float) {
    val dx = d * angleX; val dy = d * angleY
    val frontPath = Path().apply { moveTo(left, top); lineTo(left + w, top); lineTo(left + w, top + h); lineTo(left, top + h); close() }
    drawPath(path = frontPath, color = ConcreteGray)
    drawPath(path = frontPath, brush = Brush.verticalGradient(listOf(ConcreteGray, ConcreteSideGray), startY = top, endY = top + h))
    
    val topPath = Path().apply { moveTo(left, top); lineTo(left + dx, top - dy); lineTo(left + w + dx, top - dy); lineTo(left + w, top); close() }
    drawPath(path = topPath, color = ConcreteTopGray)
    
    val rightPath = Path().apply { moveTo(left + w, top); lineTo(left + w + dx, top - dy); lineTo(left + w + dx, top + h - dy); lineTo(left + w, top + h); close() }
    drawPath(path = rightPath, color = ConcreteSideGray)
    
    drawHatchPattern(left, top, w, h, spacing = 20f, angleDeg = 45f, color = HatchColor.copy(alpha = 0.3f))
}

private fun DrawScope.drawDimensionLines(beamLeft: Float, beamTop: Float, beamRight: Float, beamBottom: Float, beamDrawH: Float, scale: Float, beamWidth: Double, beamDepth: Double, span: Double, cover: Double, stirrupSpacing: Double) {
    drawHorizontalDimension(beamLeft, beamRight, beamBottom + 60f, "Span L=${span.toInt()}mm", DimensionWhite, 14f * density)
    drawVerticalDimension(beamBottom, beamTop, beamRight + 60f, "h=${beamDepth.toInt()}mm", DimensionWhite, 14f * density)
}

private fun DrawScope.drawSectionInset(cw: Float, ch: Float, zoneTop: Float, zoneBottom: Float, beamWidth: Double, beamDepth: Double, mainRebarDia: Double, mainRebarCount: Int, stirrupDia: Double, cover: Double, hasTopSteel: Boolean, topRebarDia: Double, topRebarCount: Int) {
    val insetW = min(200f, cw * 0.3f); val insetH = min(zoneBottom - zoneTop - 16f, 220f)
    val insetLeft = (cw - insetW) / 2f; val insetTop = zoneTop + 10f
    
    val padding = 20f; val secScale = min((insetW - 2*padding)/beamWidth.toFloat(), (insetH - 50f)/beamDepth.toFloat())
    val secW = beamWidth.toFloat() * secScale; val secH = beamDepth.toFloat() * secScale
    val ox = insetLeft + (insetW - secW)/2f; val oy = insetTop + 30f
    
    drawRect(color = ConcreteGray, topLeft = Offset(ox, oy), size = Size(secW, secH))
    val covPx = cover.toFloat() * secScale
    drawRect(color = StirrupPurple, topLeft = Offset(ox + covPx, oy + covPx), size = Size(secW - 2*covPx, secH - 2*covPx), style = Stroke(width = 2f))
    
    val nBot = mainRebarCount; val spBot = (secW - 2*covPx)/(nBot - 1).coerceAtLeast(1)
    for (i in 0 until nBot) {
        drawCircle(color = RebarBlue, radius = 5f, center = Offset(ox + covPx + i*spBot, oy + secH - covPx))
    }
    
    drawTextAnnotated("SECTION A-A", insetLeft, insetTop - 5f, DimensionWhite, 18f)
}

private fun DrawScope.drawReinforcementSchedule(cw: Float, ch: Float, beamWidth: Double, beamDepth: Double, span: Double, mainRebarDia: Double, mainRebarCount: Int, stirrupDia: Double, stirrupSpacing: Double, hasTopSteel: Boolean, topRebarDia: Double, topRebarCount: Int, developmentLength: Double, cover: Double, tableZoneTop: Float) {
    val tableLeft = 20f; val tableTop = if(tableZoneTop > 0f) tableZoneTop else ch * 0.72f
    val tableW = cw - 40f
    val headers = listOf("Mark", "Dia.", "No.", "Length", "Spacing")
    val rows = mutableListOf<List<String>>()
    rows.add(listOf("B1", "Ø${mainRebarDia.toInt()}", "$mainRebarCount", "${(span*1000 + 600).toInt()}", "Bottom"))
    rows.add(listOf("S1", "Ø${stirrupDia.toInt()}", "${((span*1000)/stirrupSpacing).toInt()}", "Cut L", "@${stirrupSpacing.toInt()}"))
    
    val colWidths = listOf(tableW * 0.15f, tableW * 0.15f, tableW * 0.15f, tableW * 0.25f, tableW * 0.30f)
    drawReinforcementTable(tableLeft, tableTop, colWidths, headers, rows, 30f, 35f, TableHeaderBg, TableRowAlt, DimensionWhite, 16f)
}

private fun DrawScope.drawTextWithBackground(text: String, x: Float, y: Float, textColor: Color, bgColor: Color, textSize: Float) {
    val p = android.graphics.Paint().apply { this.textSize = textSize * density; color = textColor.toArgb(); isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER }
    val metrics = p.fontMetrics; val tw = p.measureText(text); val th = metrics.descent - metrics.ascent
    drawRect(color = bgColor, topLeft = Offset(x - tw/2 - 4f, y + metrics.ascent), size = Size(tw + 8f, th))
    drawContext.canvas.nativeCanvas.drawText(text, x, y, p)
}
