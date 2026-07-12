package com.civileg.app.ui.compose.components.drawings

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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// SHARED COLOR PALETTE — all professional drawings use these
// ============================================================================

object DrawingColors {
    val RebarBlue = Color(0xFF4A90D9)
    val TopRebarBlue = Color(0xFF7EC8E3)
    val StirrupPurple = Color(0xFF9B59B6)
    val SecondaryRed = Color(0xFFE74C3C)
    val ConcreteGray = Color(0xFF6B6B6B)
    val ConcreteTopGray = Color(0xFF8A8A8A)
    val ConcreteSideGray = Color(0xFF505050)
    val StressPink = Color(0xFFE91E8C)
    val DimensionWhite = Color(0xFFFFFFFF)
    val ExtensionGray = Color(0xFFAAAAAA)
    val TableHeaderBg = Color(0x33FFFFFF)
    val TableRowAlt = Color(0x1AFFFFFF)
    val DevLengthColor = Color(0xFF4A90D9)
    val LapSpliceColor = Color(0xFFE74C3C)
    val SupportColor = Color(0xFFCCCCCC)
    val HatchColor = Color(0x99AAAAAA)
    val SoilBrown = Color(0xFF8B6914)
    val WaterBlue = Color(0xFF3498DB)
    val SafeGreen = Color(0xFF2ECC71)
    val UnsafeRed = Color(0xFFE74C3C)
    val WarningOrange = Color(0xFFF39C12)
    val AccentCyan = Color(0xFF00BCD4)
    val ConcreteFill = Color(0xFF3D3D3D)
    val GridLine = Color(0x33FFFFFF)
    val SectionLine = Color(0xFFE74C3C)
    val CenterLine = Color(0x4488FF88)
}

// ============================================================================
// SHARED DRAWSCOPE EXTENSIONS
// ============================================================================

/**
 * Draw a horizontal dimension line with extension lines and centered text.
 * Extension lines extend 8px above/below, arrows at both ends, text centered.
 */
fun DrawScope.drawHorizontalDimension(
    x1: Float, x2: Float, y: Float,
    text: String,
    color: Color = DrawingColors.DimensionWhite,
    textSize: Float = 22f,
    offset: Float = 20f  // how far below the measured points the dimension line sits
) {
    val dimY = y + offset
    val extLen = 8f
    val arrowSize = 4f

    // Extension lines
    drawLine(color, Offset(x1, y + 2f), Offset(x1, dimY + extLen), 1f)
    drawLine(color, Offset(x2, y + 2f), Offset(x2, dimY + extLen), 1f)

    // Dimension line
    drawLine(color, Offset(x1 + arrowSize, dimY), Offset(x2 - arrowSize, dimY), 1.5f)

    // Arrows (filled triangles)
    // Left arrow pointing left
    drawPath(
        path = Path().apply {
            moveTo(x1, dimY)
            lineTo(x1 + arrowSize * 2, dimY - arrowSize)
            lineTo(x1 + arrowSize * 2, dimY + arrowSize)
            close()
        },
        color = color
    )
    // Right arrow pointing right
    drawPath(
        path = Path().apply {
            moveTo(x2, dimY)
            lineTo(x2 - arrowSize * 2, dimY - arrowSize)
            lineTo(x2 - arrowSize * 2, dimY + arrowSize)
            close()
        },
        color = color
    )

    // Text centered
    val textX = (x1 + x2) / 2f
    drawTextAnnotated(text, textX, dimY + extLen + 2f, color, textSize, center = true)
}

/**
 * Draw a vertical dimension line with extension lines and centered text.
 */
fun DrawScope.drawVerticalDimension(
    y1: Float, y2: Float, x: Float,
    text: String,
    color: Color = DrawingColors.DimensionWhite,
    textSize: Float = 22f,
    offset: Float = 20f
) {
    val dimX = x + offset
    val extLen = 8f
    val arrowSize = 4f

    // Extension lines
    drawLine(color, Offset(x + 2f, y1), Offset(dimX + extLen, y1), 1f)
    drawLine(color, Offset(x + 2f, y2), Offset(dimX + extLen, y2), 1f)

    // Dimension line
    drawLine(color, Offset(dimX, y1 + arrowSize), Offset(dimX, y2 - arrowSize), 1.5f)

    // Top arrow pointing up
    drawPath(
        path = Path().apply {
            moveTo(dimX, y1)
            lineTo(dimX - arrowSize, y1 + arrowSize * 2)
            lineTo(dimX + arrowSize, y1 + arrowSize * 2)
            close()
        },
        color = color
    )
    // Bottom arrow pointing down
    drawPath(
        path = Path().apply {
            moveTo(dimX, y2)
            lineTo(dimX - arrowSize, y2 - arrowSize * 2)
            lineTo(dimX + arrowSize, y2 - arrowSize * 2)
            close()
        },
        color = color
    )

    // Text centered (rotated 90°)
    val textY = (y1 + y2) / 2f
    drawTextAnnotated(
        text, dimX + extLen + 2f, textY, color, textSize,
        center = true, rotation = -90f
    )
}

/**
 * Draw engineering hatch pattern (diagonal lines for cut concrete/soil).
 * Uses manual bounds-coercion instead of clipPath for compatibility.
 */
fun DrawScope.drawHatchPattern(
    x: Float, y: Float, w: Float, h: Float,
    spacing: Float = 12f,
    angleDeg: Float = 45f,
    color: Color = DrawingColors.HatchColor
) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()
    val maxDiag = w + h
    var i = 0
    while (i * spacing < maxDiag) {
        val startX = x + i * spacing * cosA
        val startY = y + i * spacing * sinA
        val endX = startX + h * sinA
        val endY = y + h - i * spacing * cosA
        // Clip to bounds manually
        drawLine(
            color,
            Offset(startX.coerceIn(x, x + w), startY.coerceIn(y, y + h)),
            Offset(endX.coerceIn(x, x + w), endY.coerceIn(y, y + h)),
            0.8f
        )
        i++
    }
}

/**
 * Draw a rebar circle at the given position with optional label.
 */
fun DrawScope.drawRebarCircle(
    x: Float, y: Float, diameter: Float, scale: Float = 1f,
    color: Color = DrawingColors.RebarBlue,
    label: String? = null,
    labelColor: Color = DrawingColors.DimensionWhite,
    textSize: Float = 18f
) {
    val r = (diameter / 2f) * scale
    val actualR = maxOf(r, 3f) // minimum visible radius

    drawCircle(
        color = color,
        radius = actualR,
        center = Offset(x, y)
    )
    // Inner dot
    drawCircle(
        color = color.copy(alpha = 0.5f),
        radius = actualR * 0.35f,
        center = Offset(x, y)
    )

    if (label != null) {
        drawTextAnnotated(label, x + actualR + 3f, y - textSize / 3f, labelColor, textSize)
    }
}

/**
 * Draw a stirrup/tie rectangle with given cover offset.
 */
fun DrawScope.drawStirrupRect(
    x: Float, y: Float, w: Float, h: Float,
    color: Color = DrawingColors.StirrupPurple,
    strokeWidth: Float = 1.5f,
    cornerRadius: Float = 6f
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(cornerRadius),
        style = Stroke(width = strokeWidth)
    )
}

/**
 * Draw a pin support triangle.
 */
fun DrawScope.drawPinSupport(
    x: Float, y: Float, size: Float = 20f,
    color: Color = DrawingColors.SupportColor
) {
    drawPath(
        path = Path().apply {
            moveTo(x - size / 2, y)
            lineTo(x + size / 2, y)
            lineTo(x, y + size)
            close()
        },
        color = color
    )
    // Ground line
    drawLine(color, Offset(x - size, y + size), Offset(x + size, y + size), 1.5f)
    // Hatch below ground
    for (i in 0..4) {
        val hx = x - size + i * size / 2f
        drawLine(color, Offset(hx, y + size), Offset(hx - 4f, y + size + 6f), 1f)
    }
}

/**
 * Draw a roller support triangle with circle.
 */
fun DrawScope.drawRollerSupport(
    x: Float, y: Float, size: Float = 20f,
    color: Color = DrawingColors.SupportColor
) {
    // Triangle
    drawPath(
        path = Path().apply {
            moveTo(x - size / 2, y)
            lineTo(x + size / 2, y)
            lineTo(x, y + size * 0.7f)
            close()
        },
        color = color
    )
    // Rollers
    val rollerY = y + size * 0.7f + 4f
    drawCircle(color = color, radius = 3f, center = Offset(x - 6f, rollerY))
    drawCircle(color = color, radius = 3f, center = Offset(x + 6f, rollerY))
    // Ground
    drawLine(color, Offset(x - size, rollerY + 4f), Offset(x + size, rollerY + 4f), 1.5f)
}

/**
 * Draw a fixed support (hatched wall).
 */
fun DrawScope.drawFixedSupport(
    x: Float, y: Float, width: Float = 15f, height: Float = 40f,
    color: Color = DrawingColors.SupportColor
) {
    drawRect(
        color = color.copy(alpha = 0.6f),
        topLeft = Offset(x, y),
        size = Size(width, height)
    )
    // Hatch lines
    for (i in 0 until (height / 6f).toInt()) {
        val hy = y + i * 6f
        drawLine(color, Offset(x, hy + 6f), Offset(x + width, hy), 1f)
    }
}

/**
 * Draw a dashed center line.
 */
fun DrawScope.drawCenterLine(
    x1: Float, y1: Float, x2: Float, y2: Float,
    color: Color = DrawingColors.CenterLine,
    strokeWidth: Float = 1f
) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 4f, 3f, 4f), 0f)
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth, pathEffect = dashEffect)
}

/**
 * Draw a section cut line with label (A-A, B-B, etc.).
 */
fun DrawScope.drawSectionCutLine(
    x1: Float, y1: Float, x2: Float, y2: Float,
    label: String = "A",
    color: Color = DrawingColors.SectionLine,
    circleRadius: Float = 12f,
    textSize: Float = 20f
) {
    drawLine(
        color = color,
        start = Offset(x1, y1),
        end = Offset(x2, y2),
        strokeWidth = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
    )
    // Start circle with label
    drawCircle(color = color, radius = circleRadius, center = Offset(x1, y1))
    drawCircle(color = Color(0xFF1A1A1A), radius = circleRadius - 2f, center = Offset(x1, y1))
    drawTextAnnotated(label, x1, y1 - textSize / 3f, Color.White, textSize, center = true)
    // End circle with label
    drawCircle(color = color, radius = circleRadius, center = Offset(x2, y2))
    drawCircle(color = Color(0xFF1A1A1A), radius = circleRadius - 2f, center = Offset(x2, y2))
    drawTextAnnotated(label, x2, y2 - textSize / 3f, Color.White, textSize, center = true)
}

/**
 * Draw a simple table for reinforcement schedule.
 * @param x, y: top-left corner
 * @param colWidths: list of column widths
 * @param headers: list of header texts
 * @param rows: list of row string lists
 * @param rowHeight: height per row
 * @param headerHeight: header row height
 */
fun DrawScope.drawReinforcementTable(
    x: Float, y: Float,
    colWidths: List<Float>,
    headers: List<String>,
    rows: List<List<String>>,
    rowHeight: Float = 28f,
    headerHeight: Float = 32f,
    headerBg: Color = DrawingColors.TableHeaderBg,
    altRowBg: Color = DrawingColors.TableRowAlt,
    textColor: Color = DrawingColors.DimensionWhite,
    textSize: Float = 18f
) {
    val totalW = colWidths.sum()
    val totalH = headerHeight + rows.size * rowHeight

    // Background
    drawRect(Color(0x22FFFFFF), Offset(x, y), Size(totalW, totalH))

    // Header background
    drawRect(headerBg, Offset(x, y), Size(totalW, headerHeight))

    // Header text and dividers
    var cx = x
    headers.forEachIndexed { i, header ->
        if (i > 0) {
            drawLine(DrawingColors.ExtensionGray, Offset(cx, y), Offset(cx, y + totalH), 1f)
        }
        drawTextAnnotated(
            header, cx + colWidths[i] / 2f, y + headerHeight / 2f - textSize / 3f,
            textColor, textSize, center = true, bold = true
        )
        cx += colWidths[i]
    }

    // Rows
    rows.forEachIndexed { rowIdx, row ->
        val ry = y + headerHeight + rowIdx * rowHeight
        // Alternating row background
        if (rowIdx % 2 == 1) {
            drawRect(altRowBg, Offset(x, ry), Size(totalW, rowHeight))
        }
        // Row text
        cx = x
        row.forEachIndexed { colIdx, cell ->
            drawTextAnnotated(
                cell, cx + colWidths[colIdx] / 2f, ry + rowHeight / 2f - textSize / 3f,
                textColor, textSize, center = true
            )
            cx += colWidths[colIdx]
        }
        // Bottom divider
        drawLine(
            DrawingColors.ExtensionGray, Offset(x, ry + rowHeight),
            Offset(x + totalW, ry + rowHeight), 0.5f
        )
    }

    // Border
    drawRect(
        Color(0x66FFFFFF), Offset(x, y), Size(totalW, totalH),
        style = Stroke(1.5f)
    )
}

/**
 * Draw a bending moment or shear force diagram.
 * @param values: list of (x_ratio, value) pairs where x_ratio is 0..1 along span
 * @param spanStart, spanEnd: x coordinates in drawing space
 * @param baselineY: y coordinate for zero line
 * @param maxAbsValue: for scaling (maximum absolute value across all points)
 * @param positiveUp: true = positive values go up (moment), false = positive go down (shear convention)
 */
fun DrawScope.drawForceDiagram(
    values: List<Pair<Float, Float>>,
    spanStart: Float, spanEnd: Float,
    baselineY: Float,
    maxAbsValue: Float,
    positiveUp: Boolean = true,
    fillColor: Color = DrawingColors.StressPink.copy(alpha = 0.25f),
    lineColor: Color = DrawingColors.StressPink,
    strokeWidth: Float = 2f,
    label: String = "M (kN.m)"
) {
    if (values.isEmpty() || maxAbsValue < 0.001f) return

    val spanW = spanEnd - spanStart
    val maxH = 50f // max diagram height in px
    val scale = maxH / maxAbsValue

    // Build path
    val path = Path()
    values.forEachIndexed { i, (xr, value) ->
        val px = spanStart + xr * spanW
        val py = if (positiveUp) baselineY - value * scale else baselineY + value * scale
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }

    // Fill
    val fillPath = Path().apply {
        moveTo(spanStart, baselineY)
        values.forEach { (xr, value) ->
            val px = spanStart + xr * spanW
            val py = if (positiveUp) baselineY - value * scale else baselineY + value * scale
            lineTo(px, py)
        }
        lineTo(spanEnd, baselineY)
        close()
    }
    drawPath(fillPath, fillColor)
    drawPath(path, lineColor, style = Stroke(width = strokeWidth))

    // Baseline
    drawLine(
        DrawingColors.ExtensionGray,
        Offset(spanStart, baselineY),
        Offset(spanEnd, baselineY),
        1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
    )

    // Label
    drawTextAnnotated(
        label, spanStart + spanW / 2f, baselineY - maxH - 14f,
        lineColor, 20f, center = true
    )

    // Max/min value annotations
    val maxEntry = values.maxByOrNull { it.second } ?: return
    val minEntry = values.minByOrNull { it.second } ?: return
    if (abs(maxEntry.second) > 0.01f) {
        val mx = spanStart + maxEntry.first * spanW
        val my = if (positiveUp) baselineY - maxEntry.second * scale
                 else baselineY + maxEntry.second * scale
        drawTextAnnotated(
            "%.1f".format(maxEntry.second), mx, my - 14f,
            lineColor, 16f, center = true
        )
    }
    if (abs(minEntry.second) > 0.01f && abs(minEntry.second - maxEntry.second) > 0.01f) {
        val mx = spanStart + minEntry.first * spanW
        val my = if (positiveUp) baselineY - minEntry.second * scale
                 else baselineY + minEntry.second * scale
        drawTextAnnotated(
            "%.1f".format(minEntry.second), mx, my + 4f,
            lineColor, 16f, center = true
        )
    }
}

/**
 * Draw text on native canvas with optional centering and rotation.
 * Uses Noto Sans Arabic font for Arabic text support.
 */
fun DrawScope.drawTextAnnotated(
    text: String,
    x: Float,
    y: Float,
    color: Color = DrawingColors.DimensionWhite,
    size: Float = 22f,
    center: Boolean = false,
    bold: Boolean = false,
    rotation: Float = 0f
) {
    if (text.isBlank()) return
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            this.color = color.toArgb()
            this.textSize = size
            this.isAntiAlias = true
            this.textAlign = if (center) android.graphics.Paint.Align.CENTER
                             else android.graphics.Paint.Align.LEFT
            this.typeface = if (bold) {
                android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            } else {
                android.graphics.Typeface.SANS_SERIF
            }
            // Try to use Arabic-supporting font for Arabic text
            if (text.any { it.code in 0x0600..0x06FF || it.code in 0xFB50..0xFEFF }) {
                this.typeface = try {
                    android.graphics.Typeface.create(
                        "Noto Sans Arabic",
                        android.graphics.Typeface.NORMAL
                    )
                } catch (e: Exception) {
                    android.graphics.Typeface.SANS_SERIF
                }
            }
        }

        if (rotation != 0f) {
            save()
            translate(x, y)
            rotate(rotation)
            drawText(text, 0f, 0f, paint)
            restore()
        } else {
            drawText(text, x, y, paint)
        }
    }
}

/**
 * Draw a title block (drawing title box at bottom-right of engineering drawings).
 */
fun DrawScope.drawTitleBlock(
    x: Float, y: Float, width: Float, height: Float,
    projectName: String = "CivilEG",
    drawingTitle: String = "Structural Drawing",
    scale: String = "NTS",
    drawingNo: String = "1",
    color: Color = DrawingColors.DimensionWhite,
    borderColor: Color = DrawingColors.ExtensionGray
) {
    // Background
    drawRect(Color(0x11FFFFFF), Offset(x, y), Size(width, height))
    // Border
    drawRect(borderColor, Offset(x, y), Size(width, height), style = Stroke(1.5f))

    // Divider lines
    drawLine(
        borderColor, Offset(x, y + height * 0.33f),
        Offset(x + width, y + height * 0.33f), 1f
    )
    drawLine(
        borderColor, Offset(x, y + height * 0.66f),
        Offset(x + width, y + height * 0.66f), 1f
    )
    drawLine(
        borderColor, Offset(x + width * 0.4f, y),
        Offset(x + width * 0.4f, y + height), 1f
    )

    // Project name (top-left)
    drawTextAnnotated("Project:", x + 6f, y + height * 0.22f, color, 16f)
    drawTextAnnotated(
        projectName, x + width * 0.4f + 6f, y + height * 0.22f,
        color, 16f, bold = true
    )
    // Drawing title (middle)
    drawTextAnnotated("Title:", x + 6f, y + height * 0.55f, color, 16f)
    drawTextAnnotated(
        drawingTitle, x + width * 0.4f + 6f, y + height * 0.55f,
        color, 16f, bold = true
    )
    // Scale and drawing number (bottom)
    drawTextAnnotated("Scale: $scale", x + 6f, y + height * 0.88f, color, 14f)
    drawTextAnnotated(
        "No: $drawingNo", x + width * 0.4f + 6f, y + height * 0.88f,
        color, 14f
    )
}

/**
 * Draw a north arrow / drawing direction indicator.
 */
fun DrawScope.drawNorthArrow(
    x: Float, y: Float, size: Float = 24f,
    color: Color = DrawingColors.DimensionWhite
) {
    // Circle
    drawCircle(
        color = color, radius = size, center = Offset(x, y),
        style = Stroke(1f)
    )
    // Arrow up (N)
    drawPath(
        path = Path().apply {
            moveTo(x, y - size + 4f)
            lineTo(x - 5f, y - 4f)
            lineTo(x + 5f, y - 4f)
            close()
        },
        color = color
    )
    drawLine(color, Offset(x, y - 4f), Offset(x, y + size - 8f), 1.5f)
    drawTextAnnotated("N", x, y - size - 4f, color, 16f, center = true, bold = true)
}