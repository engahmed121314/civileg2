package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.civileg.app.ui.compose.components.drawings.DrawingColorDefaults
import kotlin.math.*

/**
 * Professional Pile Foundation Engineering Drawing.
 * Renders:
 *  - Pile group plan view with spacing and cap outline
 *  - Pile cross-section with reinforcement
 *  - Side elevation showing pile embedded in soil with soil layers
 *  - Pile cap section with rebar
 *  - Reinforcement schedule table
 *
 * Uses shared [DrawingColors] and [DrawingUtils] extensions.
 */
@Composable
fun ProfessionalPileDrawing(
    pileDiameter: Double,          // mm
    pileLength: Double,            // m
    numberOfPiles: Int,
    pileSpacing: Double,           // mm
    pattern: String,               // "2x2", "3x3", etc.
    capWidth: Double,              // mm
    capLength: Double,             // mm
    capThickness: Double,          // mm
    columnWidth: Double,           // mm
    columnLength: Double,          // mm
    longitBars: Int,
    longitDia: Int,                // mm
    tiesDia: Int,                  // mm
    tiesSpacing: Int,              // mm
    capRebarDia: Int,              // mm
    capRebarCount: Int,
    soilType: String,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
    ) {
        val w = size.width
        val h = size.height
        val C = DrawingColorDefaults

        // Color palette
        val concreteFill = Color(0xFF3D3D3D)
        val concreteStroke = Color(0xFF6B6B6B)
        val pileStroke = Color(0xFF9E9E9E)
        val capStroke = Color(0xFFBDBDBD)
        val rebarColor = C.RebarBlue
        val stirrupColor = C.StirrupPurple
        val dimColor = C.ExtensionGray
        val textColor = C.DimensionWhite
        val soilColor = C.SoilBrown
        val soilHatchColor = C.SoilBrown.copy(alpha = 0.7f)
        val waterColor = C.WaterBlue.copy(alpha = 0.3f)
        val bearingLayerColor = Color(0xFF5D4037)
        val safeColor = C.SafeGreen
        val unsafeColor = C.UnsafeRed
        val headerBg = Color(0x55333333)
        val columnFill = Color(0xFF555555)
        val columnStroke = Color(0xFF333333)
        val tableHeaderBg = Color(0x55333333)

        // ── Safety coercions ──────────────────────────────
        val safeDia = pileDiameter.coerceAtLeast(100.0)
        val safeSpacing = pileSpacing.coerceAtLeast(200.0)
        val safeCapW = capWidth.coerceAtLeast(300.0)
        val safeCapL = capLength.coerceAtLeast(300.0)
        val safeCapT = capThickness.coerceAtLeast(200.0)
        val safeColW = columnWidth.coerceAtLeast(100.0)
        val safeColL = columnLength.coerceAtLeast(100.0)

        // ── Parse pattern ─────────────────────────────────
        val (nCols, nRows) = try {
            val parts = pattern.lowercase().split("x")
            if (parts.size == 2) Pair(parts[0].trim().toInt().coerceAtLeast(1), parts[1].trim().toInt().coerceAtLeast(1))
            else when {
                numberOfPiles <= 1 -> Pair(1, 1)
                numberOfPiles <= 4 -> Pair(2, 2)
                numberOfPiles <= 6 -> Pair(3, 2)
                numberOfPiles <= 9 -> Pair(3, 3)
                else -> Pair(4, 4)
            }
        } catch (_: Exception) { Pair(2, 2) }

        val margin = 24f
        val scale = 0.3f  // drawing scale factor for mm to px

        // ══════════════════════════════════════════════════
        // HEADER
        // ══════════════════════════════════════════════════
        drawRect(color = headerBg, topLeft = Offset(0f, 0f), size = Size(w, 36f))
        drawTextAnnotated(
            "PILE FOUNDATION DETAIL — $pattern (${numberOfPiles} piles)",
            w / 2f, 24f, textColor, 12f * density, center = true, bold = true
        )

        // ══════════════════════════════════════════════════
        // PLAN VIEW (top-left)
        // ══════════════════════════════════════════════════
        val planLeft = margin + 40f
        val planTop = 46f
        val planMaxW = w * 0.48f
        val planMaxH = h * 0.38f

        drawTextAnnotated("PLAN", planLeft, planTop - 4f, C.ExtensionGray, 9f * density, bold = true)

        // Scale plan to fit
        val planScaleX = planMaxW / safeCapL
        val planScaleY = planMaxH / safeCapW
        val pScale = min(planScaleX, planScaleY) * 0.8f

        val capDrawL = (safeCapL * pScale).toFloat()
        val capDrawW = (safeCapW * pScale).toFloat()
        val pCenterX = (planLeft + planMaxW / 2f).toFloat()
        val pCenterY = (planTop + planMaxH / 2f + 4f).toFloat()
        val capLeft = pCenterX - capDrawL / 2f
        val capTop = pCenterY - capDrawW / 2f

        // Cap outline
        drawRect(
            color = concreteFill,
            topLeft = Offset(capLeft, capTop),
            size = Size(capDrawL, capDrawW)
        )
        drawHatchPattern(
            capLeft, capTop, capDrawL, capDrawW,
            spacing = 14f, angleDeg = 45f, color = Color(0x55AAAAAA)
        )
        drawRect(
            color = capStroke,
            topLeft = Offset(capLeft, capTop),
            size = Size(capDrawL, capDrawW),
            style = Stroke(width = 2.5f)
        )

        // Pile circles in plan
        val pileR = (safeDia / 2.0 * pScale).toFloat().coerceAtLeast(4f)
        for (r in 0 until nRows) {
            for (c in 0 until nCols) {
                val px = capLeft + (if (nCols > 1) capDrawL * (c + 0.5f) / nCols else capDrawL / 2f)
                val py = capTop + (if (nRows > 1) capDrawW * (r + 0.5f) / nRows else capDrawW / 2f)
                drawCircle(color = Color(0xFF555555), radius = pileR, center = Offset(px, py))
                drawCircle(color = pileStroke, radius = pileR, center = Offset(px, py), style = Stroke(width = 1.5f))
                // Cross mark for pile
                drawLine(Color(0xFF777777), Offset(px - pileR * 0.6f, py - pileR * 0.6f), Offset(px + pileR * 0.6f, py + pileR * 0.6f), 0.8f)
                drawLine(Color(0xFF777777), Offset(px - pileR * 0.6f, py + pileR * 0.6f), Offset(px + pileR * 0.6f, py - pileR * 0.6f), 0.8f)
            }
        }

        // Column outline in plan
        val colDrawW = (safeColL * pScale).toFloat()
        val colDrawD = (safeColW * pScale).toFloat()
        drawRect(
            color = columnFill,
            topLeft = Offset(pCenterX - colDrawW / 2f, pCenterY - colDrawD / 2f),
            size = Size(colDrawW, colDrawD)
        )
        drawRect(
            color = columnStroke,
            topLeft = Offset(pCenterX - colDrawW / 2f, pCenterY - colDrawD / 2f),
            size = Size(colDrawW, colDrawD),
            style = Stroke(width = 1.5f)
        )
        drawHatchPattern(
            pCenterX - colDrawW / 2f, pCenterY - colDrawD / 2f,
            colDrawW, colDrawD, spacing = 5f, angleDeg = -45f, color = Color(0x66666666)
        )

        // Cap dimension lines
        drawHorizontalDimension(capLeft, capLeft + capDrawL, capTop - 2f, "L=${safeCapL.toInt()}", dimColor, 8f * density, offset = -12f)
        drawVerticalDimension(capTop, capTop + capDrawW, capLeft - 2f, "W=${safeCapW.toInt()}", dimColor, 8f * density, offset = -14f)

        // Spacing dimension between two adjacent piles
        if (nCols > 1) {
            val p1x = capLeft + capDrawL * 0.5f / nCols
            val p2x = capLeft + capDrawL * 1.5f / nCols
            drawHorizontalDimension(p1x, p2x, capTop + capDrawW + 2f, "s=${safeSpacing.toInt()}", dimColor, 7f * density, offset = 10f)
        }

        // Section cut line
        drawSectionCutLine(
            x1 = pCenterX, y1 = capTop - 10f,
            x2 = pCenterX, y2 = capTop + capDrawW + 2f,
            label = "A", color = C.SectionLine
        )

        // ══════════════════════════════════════════════════
        // CROSS-SECTION VIEW (top-right)
        // ══════════════════════════════════════════════════
        val secLeft = w * 0.52f
        val secTop = 46f
        val secMaxSize = min(w * 0.44f, h * 0.38f)

        drawTextAnnotated("PILE SECTION", secLeft, secTop - 4f, C.ExtensionGray, 9f * density, bold = true)

        // Scale pile section to fit
        val secScale = ((secMaxSize * 0.6f) / safeDia).toFloat()
        val pileDrawR = (safeDia / 2.0 * secScale).toFloat().coerceAtLeast(20f)
        val pileCX = (secLeft + secMaxSize / 2f).toFloat()
        val pileCY = (secTop + secMaxSize / 2f + 8f).toFloat()

        // Pile circle (concrete)
        drawCircle(color = concreteFill, radius = pileDrawR, center = Offset(pileCX, pileCY))
        drawCircle(color = pileStroke, radius = pileDrawR, center = Offset(pileCX, pileCY), style = Stroke(width = 2f))

        // Concrete hatching (concentric circles)
        for (i in 1..3) {
            val hr = pileDrawR * (i / 4f)
            drawCircle(color = Color(0x33AAAAAA), radius = hr, center = Offset(pileCX, pileCY), style = Stroke(width = 0.5f))
        }

        // Longitudinal reinforcement bars
        val barDrawR = (longitDia / 2.0 * secScale).toFloat().coerceIn(2f, 6f)
        val coverPx = (75.0 * secScale).toFloat()
        val barCircleR = pileDrawR - coverPx
        for (i in 0 until longitBars) {
            val angle = 2.0 * PI * i / longitBars
            val bx = (pileCX + (barCircleR * cos(angle))).toFloat()
            val by = (pileCY + (barCircleR * sin(angle))).toFloat()
            drawRebarCircle(bx, by, longitDia.toFloat(), secScale * 0.5f, rebarColor)
        }

        // Ties (stirrup)
        val tieR = barCircleR + barDrawR + 2f
        drawCircle(color = stirrupColor, radius = tieR, center = Offset(pileCX, pileCY), style = Stroke(width = 1.2f))

        // Diameter dimension
        drawHorizontalDimension(
            pileCX - pileDrawR, pileCX + pileDrawR, pileCY + pileDrawR + 2f,
            "Ø${safeDia.toInt()}", dimColor, 9f * density, offset = 10f
        )

        // Rebar label
        drawTextAnnotated(
            "${longitBars}Ø$longitDia",
            pileCX + pileDrawR + 14f, pileCY - 6f, rebarColor, 9f * density, bold = true
        )
        drawTextAnnotated(
            "ties Ø$tiesDia @$tiesSpacing",
            pileCX + pileDrawR + 14f, pileCY + 8f, stirrupColor, 8f * density
        )

        // ══════════════════════════════════════════════════
        // ELEVATION / SIDE VIEW (bottom-left)
        // ══════════════════════════════════════════════════
        val elevTop = planTop + planMaxH + 30f
        val elevLeft = margin + 40f
        val elevW = w * 0.48f
        val elevH = h * 0.45f

        drawTextAnnotated("SECTION A-A (ELEVATION)", elevLeft, elevTop - 4f, C.ExtensionGray, 9f * density, bold = true)

        // Pile length to drawing height
        val pileDrawLength = (elevH - 50f) * 0.75f
        val elevScale = (pileDrawLength / pileLength).toFloat()

        val eCenterX = (elevLeft + elevW / 2f).toFloat()
        val groundY = (elevTop + 20f).toFloat()
        val pileTopY = groundY + (safeCapT * elevScale * 0.3f).toFloat()
        val pileBottomY = pileTopY + pileDrawLength
        val pileDrawW = (safeDia * elevScale).toFloat().coerceIn(10f, 30f)

        // Ground surface line
        drawLine(dimColor, Offset(elevLeft, groundY), Offset(elevLeft + elevW, groundY), 1.5f)
        drawTextAnnotated("GL ±0.00", elevLeft + elevW - 60f, groundY - 4f, dimColor, 8f * density)

        // Soil layers
        val soilLayerH = (pileBottomY - groundY) * 0.6f
        drawRect(
            color = soilColor.copy(alpha = 0.3f),
            topLeft = Offset(elevLeft, groundY),
            size = Size(elevW, soilLayerH)
        )
        drawHatchPattern(
            elevLeft, groundY, elevW, soilLayerH,
            spacing = 10f, angleDeg = -45f, color = soilHatchColor
        )
        drawTextAnnotated(soilType.uppercase(), elevLeft + 6f, groundY + soilLayerH / 2f - 4f, soilHatchColor, 9f * density, bold = true)

        // Bearing layer
        drawRect(
            color = bearingLayerColor.copy(alpha = 0.4f),
            topLeft = Offset(elevLeft, groundY + soilLayerH),
            size = Size(elevW, pileBottomY - groundY - soilLayerH + 10f)
        )
        drawHatchPattern(
            elevLeft, groundY + soilLayerH, elevW, pileBottomY - groundY - soilLayerH + 10f,
            spacing = 6f, angleDeg = 30f, color = Color(0x885D4037)
        )
        drawTextAnnotated("BEARING STRATUM", elevLeft + 6f, groundY + soilLayerH + 14f, bearingLayerColor, 9f * density, bold = true)

        // Pile body
        drawRect(
            color = concreteFill,
            topLeft = Offset(eCenterX - pileDrawW / 2f, pileTopY),
            size = Size(pileDrawW, pileBottomY - pileTopY)
        )
        drawRect(
            color = pileStroke,
            topLeft = Offset(eCenterX - pileDrawW / 2f, pileTopY),
            size = Size(pileDrawW, pileBottomY - pileTopY),
            style = Stroke(width = 2f)
        )

        // Pile tip (triangle)
        drawPath(
            path = Path().apply {
                moveTo(eCenterX - pileDrawW / 2f, pileBottomY)
                lineTo(eCenterX, pileBottomY + 8f)
                lineTo(eCenterX + pileDrawW / 2f, pileBottomY)
                close()
            },
            color = concreteFill
        )

        // Pile cap
        val capDrawW_elev = (safeCapL * elevScale * 0.25f).toFloat()
        val capDrawH = (safeCapT * elevScale * 0.3f).toFloat()
        drawRect(
            color = concreteFill,
            topLeft = Offset(eCenterX - capDrawW_elev / 2f, pileTopY - capDrawH),
            size = Size(capDrawW_elev, capDrawH)
        )
        drawRect(
            color = capStroke,
            topLeft = Offset(eCenterX - capDrawW / 2f, pileTopY - capDrawH),
            size = Size(capDrawW, capDrawH),
            style = Stroke(width = 2.5f)
        )

        // Column on top of cap
        val colDrawW2 = (safeColL * elevScale * 0.25f).toFloat().coerceAtLeast(12f)
        val colH = 28f
        drawRect(
            color = columnFill,
            topLeft = Offset(eCenterX - colDrawW2 / 2f, pileTopY - capDrawH - colH),
            size = Size(colDrawW2, colH)
        )
        drawRect(
            color = columnStroke,
            topLeft = Offset(eCenterX - colDrawW2 / 2f, pileTopY - capDrawH - colH),
            size = Size(colDrawW2, colH),
            style = Stroke(width = 1.5f)
        )
        drawHatchPattern(
            eCenterX - colDrawW2 / 2f, pileTopY - capDrawH - colH,
            colDrawW2, colH, spacing = 4f, angleDeg = -45f, color = Color(0x66666666)
        )

        // Rebar in pile (two lines)
        val rebarOffset = pileDrawW * 0.25f
        drawLine(rebarColor, Offset(eCenterX - rebarOffset, pileTopY), Offset(eCenterX - rebarOffset, pileBottomY - 4f), 1.2f)
        drawLine(rebarColor, Offset(eCenterX + rebarOffset, pileTopY), Offset(eCenterX + rebarOffset, pileBottomY - 4f), 1.2f)

        // Pile length dimension
        drawVerticalDimension(
            pileTopY, pileBottomY, eCenterX + pileDrawW / 2f + 4f,
            "L=${pileLength.toInt()}m", dimColor, 8f * density, offset = 14f
        )

        // Cap thickness dimension
        drawVerticalDimension(
            pileTopY - capDrawH, pileTopY, eCenterX + capDrawW / 2f + 4f,
            "${safeCapT.toInt()}", dimColor, 7f * density, offset = -12f
        )

        // Load arrow at top
        val arrowX = eCenterX
        val arrowTop = pileTopY - capDrawH - colH - 16f
        drawLine(unsafeColor, Offset(arrowX, arrowTop), Offset(arrowX, arrowTop + 14f), 2f)
        drawPath(
            path = Path().apply {
                moveTo(arrowX, arrowTop + 14f)
                lineTo(arrowX - 4f, arrowTop + 8f)
                lineTo(arrowX + 4f, arrowTop + 8f)
                close()
            },
            color = unsafeColor
        )
        drawTextAnnotated("P", arrowX, arrowTop - 4f, unsafeColor, 10f * density, center = true, bold = true)

        // ══════════════════════════════════════════════════
        // REINFORCEMENT SCHEDULE TABLE (bottom-right)
        // ══════════════════════════════════════════════════
        val tblLeft = w * 0.52f
        val tblTop = elevTop
        val tblW = w * 0.44f

        drawTextAnnotated("REINFORCEMENT SCHEDULE", tblLeft, tblTop - 4f, C.ExtensionGray, 9f * density, bold = true)

        val headers = listOf("Mark", "Description", "Dia", "Qty", "Spacing")
        val colWidths = listOf(
            tblW * 0.08f, tblW * 0.34f, tblW * 0.14f,
            tblW * 0.16f, tblW * 0.28f
        )
        val rows = listOf(
            listOf("\u2460", "Pile Longitudinal", "Ø$longitDia", "$longitBars", "-"),
            listOf("\u2461", "Pile Ties", "Ø$tiesDia", "-", "@$tiesSpacing"),
            listOf("\u2462", "Cap Bottom X", "Ø$capRebarDia", "$capRebarCount", "@200"),
            listOf("\u2463", "Cap Bottom Y", "Ø$capRebarDia", "$capRebarCount", "@200")
        )

        drawReinforcementTable(
            x = tblLeft, y = tblTop + 4f,
            colWidths = colWidths,
            headers = headers,
            rows = rows,
            rowHeight = 22f,
            headerHeight = 26f,
            headerBg = tableHeaderBg,
            altRowBg = Color(0x1AFFFFFF),
            textColor = textColor,
            textSize = 9f * density
        )

        // Title block
        drawTitleBlock(
            x = w - 160f, y = h - 44f,
            width = 160f, height = 44f,
            drawingTitle = "Pile Foundation",
            scale = "NTS",
            drawingNo = "PF-001"
        )
    }
}
