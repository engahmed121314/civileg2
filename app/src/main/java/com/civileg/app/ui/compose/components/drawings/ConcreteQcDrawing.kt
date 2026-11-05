package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.QcLimits
import kotlin.math.max

// ============================================================================
// CONCRETE QC DRAWING
// ============================================================================
//
// Two acceptance tests are visualised, both driven by the values entered in
// the Concrete QC tab:
//   1. Slump test (ASTM C143) - cone 300 mm high, measured slump annotated.
//   2. Strength specimens use cubes for ECP and cylinders for ACI/SBC.
// The footer repeats the remaining acceptance parameters (air content,
// temperature and strength ratio) using the same displayed QC limits.
// ============================================================================

private val QcConcreteFill = Color(0xFF4A4A4A)
private val QcConcreteEdge = Color(0xFFBDBDBD)
private val QcHatch = Color(0x55FFFFFF)
private val QcPass = Color(0xFF2ECC71)
private val QcFail = Color(0xFFE74C3C)
private val QcTarget = Color(0xFF00BCD4)
private val QcLabel = Color(0xFFB0BEC5)
private val QcDim = Color(0xFFAAAAAA)

@Composable
fun ConcreteQcDrawing(
    elementId: String,
    volumeM3: Double,
    slumpMm: Double,
    fcuDesignMpa: Double,
    strength7DaysMpa: Double,
    strength28DaysMpa: Double,
    airContent: Double,
    concreteTemp: Double,
    designCode: DesignCode = DesignCode.ECP,
    modifier: Modifier = Modifier
) {
    val cfg = drawingDimensionsConfig()
    val slumpRange = QcLimits.slumpRange(designCode)
    val airRange = QcLimits.airContent(designCode)

    Canvas(modifier = modifier.fillMaxSize()) {
        val cw = size.width
        val ch = size.height
        val margin = cfg.margin
        val headerH = cfg.headerTextSize * 2.2f
        val footerH = cfg.annotationTextSmall * 3.6f
        val bandTop = margin + headerH
        val bandBottom = ch - footerH - margin

        // ── Header ────────────────────────────────────────────────────
        drawTextAnnotated(
            "CONCRETE QC - ${elementId.ifBlank { "ELEMENT" }}",
            margin, margin + cfg.headerTextSize,
            DrawingColorDefaults.DimensionWhite, cfg.headerTextSize, bold = true
        )
        drawTextAnnotated(
            "${designCode.version}  |  Volume ${"%.2f".format(volumeM3)} m3",
            margin, margin + headerH - cfg.annotationTextSmall * 0.3f,
            QcTarget, cfg.annotationTextSmall
        )

        // ── Split the drawing band ────────────────────────────────────
        val splitX = margin + (cw - 2 * margin) * 0.42f

        drawSlumpTest(
            left = margin,
            right = splitX - margin * 0.5f,
            top = bandTop,
            bottom = bandBottom,
            slumpMm = slumpMm,
            slumpRange = slumpRange,
            cfg = cfg
        )
        drawStrengthSpecimens(
            left = splitX + margin * 0.5f,
            right = cw - margin,
            top = bandTop,
            bottom = bandBottom,
            fcuDesignMpa = fcuDesignMpa,
            strength7DaysMpa = strength7DaysMpa,
            strength28DaysMpa = strength28DaysMpa,
            designCode = designCode,
            cfg = cfg
        )

        // ── Footer: remaining acceptance parameters ───────────────────
        val footerTop = ch - footerH - margin * 0.5f
        drawLine(
            color = QcHatch,
            start = Offset(margin, footerTop - cfg.annotationTextSmall * 0.6f),
            end = Offset(cw - margin, footerTop - cfg.annotationTextSmall * 0.6f),
            strokeWidth = 1f
        )

        val airOk = airContent in airRange
        val tempOk = concreteTemp in 5.0..40.0
        val ratioOk = strength28DaysMpa > 0.0 &&
            strength7DaysMpa / strength28DaysMpa >= 0.65
        val strengthOk = fcuDesignMpa > 0.0 && strength28DaysMpa >= fcuDesignMpa

        val footerItems = listOf(
            Triple("Air ${airRange.start.toInt()}-${airRange.endInclusive.toInt()}%", "%.1f %%".format(airContent), if (airOk) QcPass else QcFail),
            Triple("Temp 5-40 C", "%.1f C".format(concreteTemp), if (tempOk) QcPass else QcFail),
            Triple("7/28 ratio", if (strength28DaysMpa > 0) "%.0f %%".format(100.0 * strength7DaysMpa / strength28DaysMpa) else "-",
                if (ratioOk) QcPass else QcFail),
            Triple("28-day / target", "%.1f / %.1f MPa".format(strength28DaysMpa, fcuDesignMpa),
                if (strengthOk) QcPass else QcFail)
        )
        val cellW = (cw - 2 * margin) / footerItems.size
        footerItems.forEachIndexed { i, (label, value, color) ->
            val x = margin + cellW * i
            drawTextAnnotated(label, x, footerTop + cfg.annotationTextSmall, QcLabel,
                cfg.annotationTextSmall)
            drawTextAnnotated(value, x, footerTop + cfg.annotationTextSmall * 2.5f, color,
                cfg.annotationTextSmall * 1.15f, bold = true)
        }
    }
}

// ============================================================================
// SLUMP TEST
// ============================================================================

private fun DrawScope.drawSlumpTest(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    slumpMm: Double,
    slumpRange: ClosedFloatingPointRange<Double>,
    cfg: DrawingDimensionsConfig
) {
    val bandW = right - left
    val bandH = bottom - top
    if (bandW <= 0f || bandH <= 0f) return

    drawZoneLabel(left, top, "SLUMP TEST (ASTM C143)", cfg)

    val coneH = bandH * 0.55f
    val coneTopW = coneH * 0.33f   // 100 mm top / 300 mm height
    val coneBaseW = coneH * 0.66f  // 200 mm base / 300 mm height
    val centerX = left + bandW * 0.46f
    val baseY = top + bandH * 0.86f
    val coneTopY = baseY - coneH

    // Base plate
    drawLine(
        color = QcDim,
        start = Offset(centerX - coneBaseW, baseY),
        end = Offset(centerX + coneBaseW, baseY),
        strokeWidth = cfg.strokeDefault * 2.5f
    )

    // Cone outline (frustum)
    val cone = Path().apply {
        moveTo(centerX - coneTopW / 2f, coneTopY)
        lineTo(centerX + coneTopW / 2f, coneTopY)
        lineTo(centerX + coneBaseW / 2f, baseY)
        lineTo(centerX - coneBaseW / 2f, baseY)
        close()
    }
    drawPath(cone, color = QcConcreteFill)
    drawPath(cone, color = QcConcreteEdge, style = Stroke(cfg.strokeDefault))

    // Concrete hatching inside the cone
    drawConcreteHatch(
        left = centerX - coneBaseW / 2f,
        top = coneTopY,
        w = coneBaseW,
        h = coneH
    )

    // Measured slump: level of the slumped concrete measured from the cone top
    val slump = slumpMm.coerceIn(0.0, 300.0).toFloat()
    val slumpY = coneTopY + coneH * (slump / 300f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
    drawLine(
        color = QcTarget,
        start = Offset(centerX - coneBaseW * 0.75f, slumpY),
        end = Offset(centerX + coneBaseW * 0.75f, slumpY),
        strokeWidth = cfg.strokeDefault,
        pathEffect = dash
    )
    // Small arrow head at the level line
    drawLine(
        color = QcTarget,
        start = Offset(centerX + coneBaseW * 0.75f, slumpY),
        end = Offset(centerX + coneBaseW * 0.75f - 8f, slumpY - 5f),
        strokeWidth = cfg.strokeDefault
    )
    drawLine(
        color = QcTarget,
        start = Offset(centerX + coneBaseW * 0.75f, slumpY),
        end = Offset(centerX + coneBaseW * 0.75f - 8f, slumpY + 5f),
        strokeWidth = cfg.strokeDefault
    )

    // Dimensions: 300 mm height, 200 mm base
    drawDimLine(
        x1 = centerX - coneBaseW / 2f - 18f,
        y1 = coneTopY,
        x2 = centerX - coneBaseW / 2f - 18f,
        y2 = baseY,
        text = "300",
        vertical = true
    )
    drawDimLine(
        x1 = centerX - coneBaseW / 2f,
        y1 = baseY + 16f,
        x2 = centerX + coneBaseW / 2f,
        y2 = baseY + 16f,
        text = "200",
        vertical = false
    )

    // Slump annotation
    val slumpColor = if (slumpMm in slumpRange) QcPass else QcFail
    drawTextAnnotated(
        "Slump = ${"%.0f".format(slumpMm)} mm",
        left, top + cfg.annotationTextSmall * 2.6f, slumpColor,
        cfg.annotationTextSmall * 1.05f, bold = true
    )
    drawTextAnnotated(
        "Project QC range %.0f - %.0f mm".format(slumpRange.start, slumpRange.endInclusive),
        left, top + cfg.annotationTextSmall * 4.0f, QcLabel, cfg.annotationTextSmall
    )
}

// ============================================================================
// STRENGTH CYLINDERS
// ============================================================================

private fun DrawScope.drawStrengthSpecimens(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    fcuDesignMpa: Double,
    strength7DaysMpa: Double,
    strength28DaysMpa: Double,
    designCode: DesignCode,
    cfg: DrawingDimensionsConfig
) {
    val bandW = right - left
    val bandH = bottom - top
    if (bandW <= 0f || bandH <= 0f) return

    val cubeSpecimens = designCode == DesignCode.ECP
    val specimenLabel = if (cubeSpecimens) "COMPRESSIVE STRENGTH (ECP CUBES)" else "COMPRESSIVE STRENGTH (ASTM C39)"
    drawZoneLabel(left, top, specimenLabel, cfg)

    val fcu = fcuDesignMpa.coerceAtLeast(0.0)
    val specs = listOf(
        Triple("7-day", strength7DaysMpa, strength28DaysMpa > 0.0 &&
            strength7DaysMpa / strength28DaysMpa >= 0.65),
        Triple("28-day", strength28DaysMpa, fcu > 0.0 && strength28DaysMpa >= fcu),
        Triple("f'c", fcu, true)
    )

    val specimenH = bandH * 0.50f
    val specimenW = if (cubeSpecimens) specimenH * 0.62f else specimenH * 0.5f
    val baseY = top + bandH * 0.80f
    val specimenTopY = baseY - specimenH
    val gap = (bandW - specs.size * specimenW) / (specs.size + 1)
    val maxValue = (max(fcu, max(strength7DaysMpa, strength28DaysMpa)) * 1.1).coerceAtLeast(1.0)

    specs.forEachIndexed { i, (label, value, passed) ->
        val cx = left + gap + specimenW / 2f + i * (specimenW + gap)
        val color = when (i) {
            2 -> QcTarget
            else -> if (passed) QcPass else QcFail
        }

        if (cubeSpecimens) {
            drawRect(
                color = QcConcreteFill,
                topLeft = Offset(cx - specimenW / 2f, specimenTopY),
                size = Size(specimenW, specimenH)
            )
        } else {
            drawRoundRect(
                color = QcConcreteFill,
                topLeft = Offset(cx - specimenW / 2f, specimenTopY),
                size = Size(specimenW, specimenH),
                cornerRadius = CornerRadius(specimenW * 0.12f)
            )
        }
        // Filled gauge proportional to measured strength
        val fillH = (specimenH * (value / maxValue).coerceIn(0.0, 1.0)).toFloat()
        if (cubeSpecimens) {
            drawRect(
                color = color.copy(alpha = 0.30f),
                topLeft = Offset(cx - specimenW / 2f, baseY - fillH),
                size = Size(specimenW, fillH)
            )
            drawRect(
                color = color,
                topLeft = Offset(cx - specimenW / 2f, specimenTopY),
                size = Size(specimenW, specimenH),
                style = Stroke(cfg.strokeDefault * 1.5f)
            )
        } else {
            drawRoundRect(
                color = color.copy(alpha = 0.30f),
                topLeft = Offset(cx - specimenW / 2f, baseY - fillH),
                size = Size(specimenW, fillH),
                cornerRadius = CornerRadius(specimenW * 0.12f)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(cx - specimenW / 2f, specimenTopY),
                size = Size(specimenW, specimenH),
                cornerRadius = CornerRadius(specimenW * 0.12f),
                style = Stroke(cfg.strokeDefault * 1.5f)
            )
            drawOval(
                color = QcConcreteEdge,
                topLeft = Offset(cx - specimenW / 2f, specimenTopY - specimenW * 0.12f),
                size = Size(specimenW, specimenW * 0.24f),
                style = Stroke(cfg.strokeDefault)
            )
        }

        // Value + label under the cylinder
        drawTextAnnotated(
            "${"%.1f".format(value)} MPa",
            cx, baseY + cfg.annotationTextSmall * 1.6f, color,
            cfg.annotationTextSmall * 1.05f, center = true, bold = true
        )
        drawTextAnnotated(
            label,
            cx, baseY + cfg.annotationTextSmall * 3.1f, QcLabel,
            cfg.annotationTextSmall, center = true
        )
    }

    // Design target line
    val targetY = baseY - specimenH * (fcu / maxValue).toFloat()
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
    drawLine(
        color = QcTarget.copy(alpha = 0.7f),
        start = Offset(left, targetY),
        end = Offset(right, targetY),
        strokeWidth = cfg.strokeDefault,
        pathEffect = dash
    )
    drawTextAnnotated(
        "Target f'c = ${"%.1f".format(fcu)} MPa",
        left, targetY - cfg.annotationTextSmall * 0.6f, QcTarget,
        cfg.annotationTextSmall
    )
}

// ============================================================================
// HELPERS
// ============================================================================

private fun DrawScope.drawDimLine(
    x1: Float, y1: Float, x2: Float, y2: Float,
    text: String, vertical: Boolean
) {
    val tick = 8f
    if (vertical) {
        drawLine(QcDim, Offset(x1, y1), Offset(x1, y2), 1f)
        drawLine(QcDim, Offset(x1 - tick, y1), Offset(x1 + tick, y1), 1f)
        drawLine(QcDim, Offset(x1 - tick, y2), Offset(x1 + tick, y2), 1f)
        drawTextAnnotated(text, x1 - 26f, (y1 + y2) / 2f + 4f, QcLabel, 12f)
    } else {
        drawLine(QcDim, Offset(x1, y1), Offset(x2, y1), 1f)
        drawLine(QcDim, Offset(x1, y1 - tick), Offset(x1, y1 + tick), 1f)
        drawLine(QcDim, Offset(x2, y1 - tick), Offset(x2, y1 + tick), 1f)
        drawTextAnnotated(text, (x1 + x2) / 2f - 16f, y1 - 8f, QcLabel, 12f)
    }
}

private fun DrawScope.drawConcreteHatch(left: Float, top: Float, w: Float, h: Float) {
    if (w <= 0f || h <= 0f) return
    val nc = drawContext.canvas.nativeCanvas
    nc.save()
    nc.clipRect(left, top, left + w, top + h)
    var x = left - h
    while (x < left + w + h) {
        nc.drawLine(
            x, top, x + h, top + h,
            android.graphics.Paint().apply {
                color = QcHatch.toArgb()
                strokeWidth = 0.8f
                isAntiAlias = true
            }
        )
        x += 14f
    }
    nc.restore()
}
