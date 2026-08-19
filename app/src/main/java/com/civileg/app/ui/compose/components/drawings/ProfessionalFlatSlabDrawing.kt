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
import com.civileg.app.domain.PanelType
import com.civileg.app.domain.RebarResult
import kotlin.math.min

/**
 * Professional Flat Slab Plan Drawing
 *
 * Shows:
 *  - Panel outline with dimensions
 *  - Column locations
 *  - Column strip / middle strip boundaries (dashed lines)
 *  - Drop panel outline (if any)
 *  - Reinforcement annotation in each strip region
 *  - Punching shear critical perimeter
 *  - Status indicator (safe/unsafe)
 *  - Legend / title block
 */
@Composable
fun ProfessionalFlatSlabDrawing(
    lx: Double,            // m
    ly: Double,            // m
    slabThickness: Double, // mm
    columnWidth: Double,   // mm
    columnDepth: Double,   // mm
    dropSizeX: Double = 0.0,
    dropSizeY: Double = 0.0,
    colStripWidthX: Double = 0.0,
    colStripWidthY: Double = 0.0,
    colTopRebar: RebarResult,
    colBotRebar: RebarResult,
    midTopRebar: RebarResult,
    midBotRebar: RebarResult,
    isSafe: Boolean = true,
    panelType: PanelType = PanelType.INTERIOR,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val density = this.density

        // ── Color Palette ────────────────────────────────────────
        val bgDark = Color(0xFF1A1A2E)
        val panelFill = Color(0xFF2A2A40)
        val panelStroke = Color(0xFF607D8B)
        val columnFill = Color(0xFF78909C)
        val columnStroke = Color(0xFFB0BEC5)
        val dropFill = Color(0xFF37474F)
        val dropStroke = Color(0xFF546E7A)
        val colStripColor = Color(0xFF1B5E20)
        val midStripColor = Color(0xFF0D47A1)
        val topBarColor = Color(0xFFE53935)
        val botBarColor = Color(0xFF1565C0)
        val dimColor = Color(0xFFB0BEC5)
        val textColor = Color(0xFFECEFF1)
        val headerBg = Color(0xFF0D47A1)
        val safeColor = Color(0xFF2E7D32)
        val unsafeColor = Color(0xFFC62828)
        val shearPerimColor = Color(0xFFFF9800)
        val gridColor = Color(0x1AFFFFFF)

        // ── Background ───────────────────────────────────────────
        drawRect(bgDark, Offset.Zero, size)

        // ── Layout calculations ──────────────────────────────────
        val margin = 40f * density
        val titleH = 32f * density
        val legendH = 90f * density
        val drawAreaW = w - 2 * margin
        val drawAreaH = h - 2 * margin - titleH - legendH
        val drawTop = margin + titleH

        // Scale: fit lx × ly into drawArea (maintain aspect ratio)
        val panelAspect = lx / ly
        val areaAspect = drawAreaW / drawAreaH
        var panelW: Float
        var panelH: Float
        if (panelAspect > areaAspect) {
            panelW = drawAreaW
            panelH = drawAreaW / panelAspect.toFloat()
        } else {
            panelH = drawAreaH
            panelW = drawAreaH * panelAspect.toFloat()
        }
        val panelX = margin + (drawAreaW - panelW) / 2f
        val panelY = drawTop + (drawAreaH - panelH) / 2f

        // Scale factor: pixels per meter
        val scale = panelW / lx.toFloat()
        val colWPx = (columnWidth / 1000.0 * scale)
        val colDPx = (columnDepth / 1000.0 * scale)
        val dropXPx = (dropSizeX / 1000.0 * scale)
        val dropYPx = (dropSizeY / 1000.0 * scale)

        // Column strip widths in pixels (convert from mm to m then to px)
        val colStripXPx = (colStripWidthX / 1000.0 * scale)
        val colStripYPx = (colStripWidthY / 1000.0 * scale)

        // ── Grid lines ───────────────────────────────────────────
        for (i in 1 until 4) {
            val gx = panelX + panelW * i / 4f
            drawLine(gridColor, Offset(gx, panelY), Offset(gx, panelY + panelH), 0.5f)
        }
        for (i in 1 until 4) {
            val gy = panelY + panelH * i / 4f
            drawLine(gridColor, Offset(panelX, gy), Offset(panelX + panelW, gy), 0.5f)
        }

        // ── Drop panel (if any) ──────────────────────────────────
        if (dropXPx > 0 && dropYPx > 0) {
            val dx = panelX + panelW / 2f - dropXPx / 2f
            val dy = panelY + panelH / 2f - dropYPx / 2f
            drawRect(dropFill, Offset(dx, dy), Size(dropXPx, dropYPx))
            drawRect(dropStroke, Offset(dx, dy), Size(dropXPx, dropYPx), style = Stroke(1.5f))
            drawTextAnnotated(
                "Drop Panel",
                dx + dropXPx / 2f, dy + dropYPx / 2f - 8f,
                textColor, 14f, center = true, bold = true
            )
            drawTextAnnotated(
                String.format("%.0f × %.0f mm", dropSizeX, dropSizeY),
                dx + dropXPx / 2f, dy + dropYPx / 2f + 8f,
                dimColor, 12f, center = true
            )
        }

        // ── Column strip / middle strip dashed lines ─────────────
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)

        // X-direction column strip boundaries (vertical lines)
        val colStripEdgeLeftX = panelX + panelW / 2f - colStripXPx / 2f
        val colStripEdgeRightX = panelX + panelW / 2f + colStripXPx / 2f
        drawLine(colStripColor, Offset(colStripEdgeLeftX, panelY),
            Offset(colStripEdgeLeftX, panelY + panelH), 1.5f, pathEffect = dashEffect)
        drawLine(colStripColor, Offset(colStripEdgeRightX, panelY),
            Offset(colStripEdgeRightX, panelY + panelH), 1.5f, pathEffect = dashEffect)

        // Y-direction column strip boundaries (horizontal lines)
        val colStripEdgeTopY = panelY + panelH / 2f - colStripYPx / 2f
        val colStripEdgeBotY = panelY + panelH / 2f + colStripYPx / 2f
        drawLine(colStripColor, Offset(panelX, colStripEdgeTopY),
            Offset(panelX + panelW, colStripEdgeTopY), 1.5f, pathEffect = dashEffect)
        drawLine(colStripColor, Offset(panelX, colStripEdgeBotY),
            Offset(panelX + panelW, colStripEdgeBotY), 1.5f, pathEffect = dashEffect)

        // ── Strip labels ─────────────────────────────────────────
        // Column strip label
        val csLabelX = panelX + panelW / 2f
        val csLabelY = panelY + 16f
        drawRect(colStripColor.copy(alpha = 0.3f),
            Offset(csLabelX - 40f, csLabelY - 10f), Size(80f, 20f),
            cornerRadius = CornerRadius(4f))
        drawTextAnnotated("Column Strip", csLabelX, csLabelY - 4f, Color.White, 11f, center = true, bold = true)

        // Middle strip labels
        val msLabelX = panelX + panelW / 4f
        drawTextAnnotated("Mid", msLabelX, panelY + panelH / 2f - 4f, midStripColor, 11f, center = true)
        val msLabelX2 = panelX + 3f * panelW / 4f
        drawTextAnnotated("Mid", msLabelX2, panelY + panelH / 2f - 4f, midStripColor, 11f, center = true)

        // ── Panel outline ─────────────────────────────────────────
        drawRect(panelStroke, Offset(panelX, panelY), Size(panelW, panelH), style = Stroke(2f))

        // ── Punching shear critical perimeter (dashed rectangle) ──
        val punchOffset = 15f  // approximate d/2 in pixels
        val px1 = panelX + panelW / 2f - colWPx / 2f - punchOffset
        val py1 = panelY + panelH / 2f - colDPx / 2f - punchOffset
        val pW = colWPx + 2 * punchOffset
        val pH = colDPx + 2 * punchOffset
        drawRect(shearPerimColor, Offset(px1, py1), Size(pW, pH), style = Stroke(1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
        ))
        drawTextAnnotated("bo", px1 + pW + 4f, py1 + pH / 2f - 6f, shearPerimColor, 11f)

        // ── Columns (4 corners of center) ────────────────────────
        val colPositions = listOf(
            Offset(panelX, panelY),                          // top-left
            Offset(panelX + panelW - colWPx, panelY),         // top-right
            Offset(panelX, panelY + panelH - colDPx),         // bottom-left
            Offset(panelX + panelW - colWPx, panelY + panelH - colDPx)  // bottom-right
        )
        colPositions.forEach { pos ->
            drawRect(columnFill, pos, Size(colWPx, colDPx))
            drawRect(columnStroke, pos, Size(colWPx, colDPx), style = Stroke(1.5f))
            // Cross-hatch for column
            for (i in 1..3) {
                val hx = pos.x + colWPx * i / 4f
                drawLine(columnStroke, Offset(hx, pos.y + 2f), Offset(hx, pos.y + colDPx - 2f), 0.5f)
            }
            for (i in 1..3) {
                val hy = pos.y + colDPx * i / 4f
                drawLine(columnStroke, Offset(pos.x + 2f, hy), Offset(pos.x + colWPx - 2f, hy), 0.5f)
            }
        }

        // ── Rebar annotations ────────────────────────────────────
        // Column strip: top rebar (red) and bottom rebar (blue)
        val rebarY = panelY + panelH / 2f
        drawTextAnnotated(
            "T: ${colTopRebar.barString}",
            panelX + panelW / 2f, rebarY - 16f,
            topBarColor, 12f, center = true, bold = true
        )
        drawTextAnnotated(
            "B: ${colBotRebar.barString}",
            panelX + panelW / 2f, rebarY + 2f,
            botBarColor, 12f, center = true, bold = true
        )

        // Middle strip rebar
        if (midBotRebar.spacing > 0) {
            val msRebarY = panelY + panelH / 2f + 18f
            drawTextAnnotated(
                "B: ${midBotRebar.barString}",
                panelX + panelW / 4f, msRebarY,
                botBarColor.copy(alpha = 0.7f), 10f, center = true
            )
            drawTextAnnotated(
                "B: ${midBotRebar.barString}",
                panelX + 3f * panelW / 4f, msRebarY,
                botBarColor.copy(alpha = 0.7f), 10f, center = true
            )
        }

        // ── Dimension lines ─────────────────────────────────────
        drawHorizontalDimension(
            panelX, panelX + panelW, panelY + panelH + 8f,
            String.format("%.1f m", lx), dimColor, 16f, offset = 18f
        )
        drawVerticalDimension(
            panelX + panelW + 8f, panelY, panelY + panelH,
            String.format("%.1f m", ly), dimColor, 16f, offset = 18f
        )

        // Column dimension
        if (colWPx > 20f) {
            drawTextAnnotated(
                String.format("%.0f", columnWidth),
                panelX + colWPx / 2f, panelY - 6f,
                dimColor, 10f, center = true
            )
        }

        // ── Title bar ────────────────────────────────────────────
        drawRect(headerBg, Offset(panelX, margin), Size(panelW, titleH),
            cornerRadius = CornerRadius(6f))
        drawTextAnnotated(
            "FLAT SLAB — ${panelType.displayName} PANEL",
            panelX + panelW / 2f, margin + titleH / 2f - 4f,
            Color.White, 14f, center = true, bold = true
        )

        // ── Status badge ─────────────────────────────────────────
        val statusText = if (isSafe) "DESIGN SAFE ✓" else "DESIGN FAIL ✗"
        val statusColor = if (isSafe) safeColor else unsafeColor
        val badgeW = 100f
        val badgeH = 22f
        val badgeX = panelX + panelW - badgeW
        val badgeY = margin + titleH + 6f
        drawRect(statusColor, Offset(badgeX, badgeY), Size(badgeW, badgeH),
            cornerRadius = CornerRadius(4f))
        drawTextAnnotated(
            statusText, badgeX + badgeW / 2f, badgeY + badgeH / 2f - 4f,
            Color.White, 11f, center = true, bold = true
        )

        // ── Legend ───────────────────────────────────────────────
        val legY = panelY + panelH + 50f
        val legX = panelX
        val legRowH = 16f

        drawTextAnnotated("LEGEND:", legX, legY, textColor, 12f, bold = true)
        drawRect(columnFill, Offset(legX, legY + legRowH + 2f), Size(14f, 10f))
        drawTextAnnotated("Column", legX + 20f, legY + legRowH + 6f, textColor, 11f)
        drawLine(colStripColor, Offset(legX + 80f, legY + legRowH + 7f),
            Offset(legX + 94f, legY + legRowH + 7f), 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f))
        drawTextAnnotated("Column Strip", legX + 100f, legY + legRowH + 6f, textColor, 11f)
        drawLine(shearPerimColor, Offset(legX + 200f, legY + legRowH + 7f),
            Offset(legX + 214f, legY + legRowH + 7f), 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f))
        drawTextAnnotated("Punching Perim.", legX + 220f, legY + legRowH + 6f, textColor, 11f)

        // Rebar legend
        val legY2 = legY + 2 * legRowH + 4f
        drawTextAnnotated("T:", legX, legY2 + 4f, topBarColor, 11f, bold = true)
        drawTextAnnotated("Top Reinforcement (negative moment)", legX + 16f, legY2 + 4f, textColor, 11f)
        drawTextAnnotated("B:", legX + 240f, legY2 + 4f, botBarColor, 11f, bold = true)
        drawTextAnnotated("Bottom Reinforcement (positive moment)", legX + 256f, legY2 + 4f, textColor, 11f)

        // ── Title block (bottom-right) ───────────────────────────
        val tbW = 180f
        val tbH = 44f
        val tbX = panelX + panelW - tbW
        val tbY = legY + 3 * legRowH + 4f
        drawRect(Color(0x11FFFFFF), Offset(tbX, tbY), Size(tbW, tbH))
        drawRect(Color(0x55FFFFFF), Offset(tbX, tbY), Size(tbW, tbH), style = Stroke(1f))
        drawLine(Color(0x55FFFFFF), Offset(tbX, tbY + tbH * 0.5f),
            Offset(tbX + tbW, tbY + tbH * 0.5f), 0.5f)
        drawTextAnnotated("Project: Flat Slab Module", tbX + 6f, tbY + tbH * 0.3f, textColor, 11f)
        drawTextAnnotated(
            "Scale: NTS  |  h = ${slabThickness.toInt()} mm",
            tbX + 6f, tbY + tbH * 0.75f, textColor, 10f
        )
    }
}
