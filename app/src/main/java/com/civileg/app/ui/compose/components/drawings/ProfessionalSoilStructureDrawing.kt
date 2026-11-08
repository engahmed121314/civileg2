package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*

/**
 * Professional Soil-Structure Interaction Drawing
 * Renders foundation plan, soil pressure distribution diagram, and stability checks.
 *
 * ECP 203-2020: Foundation bearing pressure and stability
 * ACI 318-19 / ASCE 7-16: Soil bearing and settlement
 */
@Composable
fun ProfessionalSoilStructureDrawing(
    foundationLengthMm: Double,
    foundationWidthMm: Double,
    foundationThicknessMm: Double,
    columnLoadKN: Double,
    momentKNm: Double,
    soilBearingCapacityKpa: Double,
    maxPressureKpa: Double,
    minPressureKpa: Double,
    averagePressureKpa: Double,
    settlementMm: Double,
    overturningFS: Double,
    slidingFS: Double,
    bearingFS: Double,
    cover: Double,
    viewMode: Int = 0,
    designCode: DesignCode = DesignCode.ECP,
    resultSummary: DrawingResultSummary? = null,
    modifier: Modifier = Modifier
) {
    // ── InputGuard ─────────────────────────────────────────────
    InputGuard.positive("foundationLengthMm", foundationLengthMm)
    InputGuard.positive("foundationWidthMm", foundationWidthMm)
    InputGuard.positive("foundationThicknessMm", foundationThicknessMm)
    InputGuard.positive("columnLoadKN", columnLoadKN)
    InputGuard.positive("soilBearingCapacityKpa", soilBearingCapacityKpa)
    InputGuard.positive("cover", cover)

    val codeLabel = designCode.version
    val cfg = drawingDimensionsConfig()

    Canvas(
        modifier = modifier.fillMaxWidth().fillMaxSize()
    ) {
        val w = size.width
        val h = size.height

        // Safe values
        val safeL = foundationLengthMm.coerceAtLeast(500.0)
        val safeW = foundationWidthMm.coerceAtLeast(500.0)
        val safeT = foundationThicknessMm.coerceAtLeast(100.0)

        // Scale
        val scale = min(w / (safeL * 0.003f), h / ((safeT * 3 + safeW) * 0.002f))
        val offsetX = w * 0.1f
        val offsetY = h * 0.15f

        fun mmX(mm: Double): Float = (offsetX + mm * 0.001f * scale).toFloat()
        fun mmY(mm: Double): Float = (offsetY + mm * 0.001f * scale).toFloat()

        val soilColor = Color(0xFF8D6E63)
        val foundationColor = Color(0xFF78909C)
        val columnColor = Color(0xFF546E7A)
        val pressureColor = Color(0xFF1976D2)
        val allowableColor = Color(0xFF4CAF50)
        val dimColor = Color(0xFF37474F)
        val textColor = Color(0xFF212121)

        // ── Draw Soil Layer ────────────────────────────────────
        drawRect(
            color = soilColor,
            topLeft = Offset(mmX(0.0), mmY(safeT * 2)),
            size = Size(mmX(safeL) - mmX(0.0), mmY(safeT * 3 + safeW) - mmY(safeT * 2)),
            style = Stroke(width = 1f)
        )

        // ── Draw Foundation ────────────────────────────────────
        drawRect(
            color = foundationColor,
            topLeft = Offset(mmX(0.0), mmY(safeT * 2)),
            size = Size(mmX(safeL) - mmX(0.0), mmY(safeT * 2 + safeT) - mmY(safeT * 2)),
            style = Stroke(width = 2f)
        )

        // ── Draw Column ────────────────────────────────────────
        val colW = safeL * 0.15
        val colCenterX = safeL / 2.0
        val colLeftX = mmX(colCenterX - colW / 2)
        val colRightX = mmX(colCenterX + colW / 2)
        val colTopY = mmY(safeT * 2 - safeT * 1.5)
        val colBottomY = mmY(safeT * 2)
        drawRect(
            color = columnColor,
            topLeft = Offset(colLeftX, colTopY),
            size = Size(colRightX - colLeftX, colBottomY - colTopY),
            style = Stroke(width = 2f)
        )

        // Draw moment indicator if momentKNm != 0.0
        if (abs(momentKNm) > 0.01) {
            drawTextAnnotated("M = ${momentKNm.toInt()} kN.m", colLeftX, colTopY - 15f, columnColor, cfg.annotationTextSmall)
        }

        // ── Draw Pressure Distribution (below foundation) ──────
        val pressureTopY = mmY(safeT * 2 + safeT)
        val pressureHeight = h * 0.15f
        val pScale = (pressureHeight / max(soilBearingCapacityKpa, max(1.0, maxPressureKpa))).toFloat()

        // Allowable pressure line
        drawLine(
            color = allowableColor,
            start = Offset(mmX(0.0), pressureTopY),
            end = Offset(mmX(safeL), pressureTopY),
            strokeWidth = 1.5f
        )
        drawTextAnnotated("q_all = ${soilBearingCapacityKpa.toInt()} kPa", mmX(safeL * 0.55f), pressureTopY - 5f, allowableColor, cfg.annotationTextSmall)

        // Max/Min pressure trapezoid
        drawLine(
            color = pressureColor,
            start = Offset(mmX(0.0), pressureTopY + pScale * maxPressureKpa.toFloat()),
            end = Offset(mmX(safeL), pressureTopY + pScale * minPressureKpa.toFloat()),
            strokeWidth = 2f
        )
        drawLine(
            color = pressureColor,
            start = Offset(mmX(0.0), pressureTopY + pScale * maxPressureKpa.toFloat()),
            end = Offset(mmX(0.0), pressureTopY),
            strokeWidth = 1f
        )
        drawLine(
            color = pressureColor,
            start = Offset(mmX(safeL), pressureTopY + pScale * minPressureKpa.toFloat()),
            end = Offset(mmX(safeL), pressureTopY),
            strokeWidth = 1f
        )

        drawTextAnnotated("q_max = ${maxPressureKpa.toInt()} kPa", mmX(0.0) + 2f, pressureTopY + pScale * maxPressureKpa.toFloat() + 2f, pressureColor, cfg.annotationTextSmall)
        drawTextAnnotated("q_min = ${minPressureKpa.toInt()} kPa", max(mmX(0.0) + 10f, mmX(safeL) - 120f), pressureTopY + pScale * minPressureKpa.toFloat() + 2f, pressureColor, cfg.annotationTextSmall)
        if (averagePressureKpa > 0.0) {
            drawTextAnnotated("q_avg = ${averagePressureKpa.toInt()} kPa", mmX(safeL * 0.35f), pressureTopY + pressureHeight * 0.5f, pressureColor, cfg.annotationTextSmall)
        }

        // ── Dimensions ─────────────────────────────────────────
        drawHorizontalDimension(0.0f, safeL.toFloat(), mmY(safeT * 2) - 10f, " mm", dimColor, cfg.valueLabelTextSize)
        drawVerticalDimension(0.0f, (safeT * 2 + safeT).toFloat(), mmX(safeL) + 10f, " mm", dimColor, cfg.valueLabelTextSize)

        // ── Section Separator & Results Summary ────────────────
        if (viewMode == 0) {
            drawSectionSeparator(h * 0.75f, w, "STABILITY & BEARING SUMMARY", cfg)
            drawReinforcementTable(
                x = w * 0.05f,
                y = h * 0.78f,
                colWidths = listOf(w * 0.35f, w * 0.25f, w * 0.20f, w * 0.10f),
                headers = listOf("Parameter", "Value", "Limit", "Status"),
                rows = listOf(
                    listOf("Max Soil Pressure", "${maxPressureKpa.toInt()} kPa", "${soilBearingCapacityKpa.toInt()} kPa", if (maxPressureKpa <= soilBearingCapacityKpa * 1.05) "OK" else "FAIL"),
                    listOf("Settlement", "${settlementMm.toInt()} mm", "25 mm (ECP)", if (settlementMm <= 25.0) "OK" else "FAIL"),
                    listOf("Overturning FS", "%.2f".format(overturningFS), "2.0", if (overturningFS >= 2.0) "OK" else "FAIL"),
                    listOf("Sliding FS", "%.2f".format(slidingFS), "1.5", if (slidingFS >= 1.5) "OK" else "FAIL"),
                    listOf("Bearing FS", "%.2f".format(bearingFS), "3.0", if (bearingFS >= 3.0) "OK" else "FAIL")
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
            drawingTitle = "Soil-Structure Interaction",
            designCode = codeLabel
        )
    }
}
