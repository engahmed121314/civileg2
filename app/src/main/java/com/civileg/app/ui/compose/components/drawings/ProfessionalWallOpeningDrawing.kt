package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*

/**
 * Professional Wall Opening Engineering Drawing
 * Renders wall section with opening, header beam reinforcement, sill reinforcement,
 * vertical reinforcement on each side, and stress distribution diagram.
 *
 * ECP 203-2020: Wall opening reinforcement around openings
 * ACI 318-19: Chapter 14 — walls, opening reinforcement
 */
@Composable
fun ProfessionalWallOpeningDrawing(
    wallHeightMm: Double,
    wallThicknessMm: Double,
    openingWidthMm: Double,
    openingHeightMm: Double,
    openingFromBaseMm: Double,
    wallLengthMm: Double,
    headerBarDia: Double,
    headerBarCount: Int,
    sillBarDia: Double,
    sillBarCount: Int,
    verticalBarDia: Double,
    verticalBarCount: Int,
    cover: Double,
    viewMode: Int = 0,
    designCode: DesignCode = DesignCode.ECP,
    resultSummary: DrawingResultSummary? = null,
    modifier: Modifier = Modifier
) {
    // ── InputGuard ─────────────────────────────────────────────
    InputGuard.positive("wallHeightMm", wallHeightMm)
    InputGuard.positive("wallThicknessMm", wallThicknessMm)
    InputGuard.positive("openingWidthMm", openingWidthMm)
    InputGuard.positive("openingHeightMm", openingHeightMm)
    InputGuard.positive("wallLengthMm", wallLengthMm)
    InputGuard.positive("cover", cover)

    val codeLabel = designCode.version
    val cfg = drawingDimensionsConfig()

    Canvas(
        modifier = modifier.fillMaxWidth().fillMaxSize()
    ) {
        val w = size.width
        val h = size.height

        // Safe values
        val safeWH = wallHeightMm.coerceAtLeast(500.0)
        val safeWT = wallThicknessMm.coerceAtLeast(100.0)
        val safeOW = openingWidthMm.coerceAtLeast(200.0)
        val safeOH = openingHeightMm.coerceAtLeast(200.0)
        val safeWL = wallLengthMm.coerceAtLeast(1000.0)

        // Scale to fit canvas (mm → pixels, approx 1:20 scale for typical wall)
        val scale = min(w / (safeWL * 0.0025), h / (safeWH * 0.0035))
        val offsetX = w * 0.1f
        val offsetY = h * 0.1f

        fun mmX(mm: Double): Float = (offsetX + mm * 0.001f * scale).toFloat()
        fun mmY(mm: Double): Float = (offsetY + mm * 0.001f * scale).toFloat()

        val wallColor = Color(0xFF9E9E9E)
        val openingColor = Color(0xFF424242)
        val steelColor = Color(0xFF1976D2)
        val dimColor = Color(0xFF37474F)
        val textColor = Color(0xFF212121)

        // ── Draw Wall Outline ──────────────────────────────────
        drawRect(
            color = wallColor,
            topLeft = Offset(mmX(0.0), mmY(0.0)),
            size = Size(mmX(safeWL) - mmX(0.0), mmY(safeWH) - mmY(0.0)),
            style = Stroke(width = 2f)
        )

        // ── Draw Opening ───────────────────────────────────────
        val openingLeft = safeWL * 0.35 // opening centered at 35% from left
        val openingTop = openingFromBaseMm

        drawRect(
            color = openingColor,
            topLeft = Offset(mmX(openingLeft), mmY(openingTop)),
            size = Size(mmX(openingLeft + safeOW) - mmX(openingLeft), mmY(openingTop + safeOH) - mmY(openingTop)),
            style = Stroke(width = 2f)
        )

        // ── Draw Header Beam Reinforcement (top of opening) ────
        val headerY = openingTop
        val headerBarSpacing = safeOW / (headerBarCount + 1)
        for (i in 1..headerBarCount) {
            val barX = openingLeft + headerBarSpacing * i
            drawCircle(
                color = steelColor,
                center = Offset(mmX(barX), mmY(headerY - cover * 0.5)),
                radius = (headerBarDia * 0.001 * scale * 2.0).toFloat()
            )
        }

        // ── Draw Sill Reinforcement (bottom of opening) ────────
        val sillY = openingTop + safeOH
        val sillBarSpacing = safeOW / (sillBarCount + 1)
        for (i in 1..sillBarCount) {
            val barX = openingLeft + sillBarSpacing * i
            drawCircle(
                color = steelColor,
                center = Offset(mmX(barX), mmY(sillY + cover * 0.5)),
                radius = (sillBarDia * 0.001 * scale * 2.0).toFloat()
            )
        }

        // ── Draw Vertical Reinforcement (each side of opening) ────
        val verticalBarSpacing = safeOH / (verticalBarCount + 1)
        // Left side
        for (i in 1..verticalBarCount) {
            val barY = openingTop + verticalBarSpacing * i
            drawCircle(
                color = steelColor,
                center = Offset(mmX(openingLeft - cover), mmY(barY)),
                radius = (verticalBarDia * 0.001 * scale * 2.0).toFloat()
            )
        }
        // Right side
        for (i in 1..verticalBarCount) {
            val barY = openingTop + verticalBarSpacing * i
            drawCircle(
                color = steelColor,
                center = Offset(mmX(openingLeft + safeOW + cover), mmY(barY)),
                radius = (verticalBarDia * 0.001 * scale * 2.0).toFloat()
            )
        }

        // ── Dimensions ─────────────────────────────────────────
        // Wall height
        drawVerticalDimension(mmY(0.0), mmY(safeWH), mmX(safeWL) + 10f, " mm", dimColor, cfg.valueLabelTextSize)
        // Wall thickness
        drawHorizontalDimension(mmX(0.0), mmX(safeWT), mmY(0.0) - 10f, " mm", dimColor, cfg.valueLabelTextSize, offset = -10f)
        // Opening width
        drawHorizontalDimension(mmX(openingLeft), mmX(openingLeft + safeOW), mmY(safeWH) + 10f, " mm", dimColor, cfg.valueLabelTextSize)
        // Opening height
        drawVerticalDimension(mmY(openingTop), mmY(openingTop + safeOH), mmX(openingLeft) - 10f, " mm", dimColor, cfg.valueLabelTextSize, offset = -10f)

        // ── Section Separator & Reinforcement Table ────────────
        if (viewMode == 0) {
            drawSectionSeparator(h * 0.85f, w, "REINFORCEMENT SCHEDULE", cfg)
            drawReinforcementTable(
                x = w * 0.05f,
                y = h * 0.88f,
                colWidths = listOf(w * 0.15f, w * 0.25f, w * 0.15f, w * 0.15f, w * 0.15f),
                headers = listOf("Mark", "Location", "Dia (mm)", "Count", "Spacing"),
                rows = listOf(
                    listOf("①", "Header Beam", headerBarDia.toInt().toString(), headerBarCount.toString(), " mm"),
                    listOf("②", "Sill", sillBarDia.toInt().toString(), sillBarCount.toString(), " mm"),
                    listOf("③", "Vertical (each side)", verticalBarDia.toInt().toString(), verticalBarCount.toString(), " mm")
                ),
                rowHeight = cfg.tableRowHeight,
                headerHeight = cfg.tableHeaderHeight,
                headerBg = Color(0xFF37474F),
                altRowBg = Color(0x1AFFFFFF),
                textColor = textColor,
                textSize = cfg.dimTextSize
            )
        }

        // ── Title Block & Annotations ──────────────────────────
        drawSheetAnnotations(
            x = w - cfg.titleBlockWidth - cfg.margin * 0.3f,
            y = h - cfg.titleBlockHeight - cfg.margin * 0.3f,
            cfg = cfg,
            summary = resultSummary
        )
        drawResponsiveTitleBlock(
            x = w - cfg.titleBlockWidth - cfg.margin * 0.3f,
            y = h - cfg.titleBlockHeight - cfg.margin * 0.3f,
            cfg = cfg,
            drawingTitle = "Wall Opening Detail",
            designCode = codeLabel
        )
    }
}
