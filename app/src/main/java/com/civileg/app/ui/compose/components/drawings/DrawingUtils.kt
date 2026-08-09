package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
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
// SHARED COLOR PALETTE (defaults — not theme-aware)
// ============================================================================

/** Static color defaults used by non-theme-aware drawings (Beam, Column, etc.). */
object DrawingColorDefaults {
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
// THEME-AWARE DRAWING COLORS (data class with light/dark variants)
// ============================================================================

/**
 * Theme-aware color palette for professional engineering drawings.
 * Provides light and dark variants for all colors so drawings adapt
 * to the system theme at runtime.
 *
 * Obtain the correct palette via the [drawingColors] composable function.
 */
data class DrawingColors(
    // ── Structural element colors (same in both themes for clarity) ──
    val rebarBlue: Color = Color(0xFF4A90D9),
    val topRebarBlue: Color = Color(0xFF7EC8E3),
    val stirrupPurple: Color = Color(0xFF9B59B6),
    val secondaryRed: Color = Color(0xFFE74C3C),
    val distBarGreen: Color = Color(0xFF2E7D32),
    // ── Concrete fills (theme-dependent) ──
    val concreteFill: Color = Color(0xFFE0E0E0),
    val concreteStroke: Color = Color(0xFF424242),
    val concreteGray: Color = Color(0xFF6B6B6B),
    val concreteTopGray: Color = Color(0xFF8A8A8A),
    val concreteSideGray: Color = Color(0xFF505050),
    val ribFill: Color = Color(0xFFBDBDBD),
    val dropFill: Color = Color(0xFFB0BEC5),
    val columnFill: Color = Color(0xFF616161),
    val planBackground: Color = Color(0xFF37474F),
    // ── Text colors (theme-dependent) ──
    val textPrimary: Color = Color(0xFF212121),
    val textSecondary: Color = Color(0xFF757575),
    val textOnHeader: Color = Color(0xFFFFFFFF),
    // ── Dimension lines (theme-dependent) ──
    val dimColor: Color = Color(0xFF6A1B9A),
    val dimLineColor: Color = Color(0xFF757575),
    val extensionGray: Color = Color(0xFFAAAAAA),
    // ── Hatch / pattern colors (theme-dependent) ──
    val hatchColor: Color = Color(0x60BDBDBD),
    // ── Table colors (theme-dependent) ──
    val tableHeaderBg: Color = Color(0xFF1A237E),
    val tableHeaderText: Color = Color(0xFFFFFFFF),
    val tableCellText: Color = Color(0xFF212121),
    val tableRowColor: Color = Color(0xFFFAFAFA),
    val tableRowAltColor: Color = Color(0xFFF5F5F5),
    val tableTitleBg: Color = Color(0xFF263238),
    val tableBorder: Color = Color(0xFF37474F),
    val tableRowBorder: Color = Color(0xFFBDBDBD),
    val tableColumnBorder: Color = Color(0xFF90A4AE),
    // ── Status colors (same in both themes) ──
    val safeGreen: Color = Color(0xFF2E7D32),
    val unsafeRed: Color = Color(0xFFC62828),
    val warningOrange: Color = Color(0xFFF39C12),
    // ── Utility colors (theme-dependent) ──
    val stripColor: Color = Color(0xFF757575),
    val shearPerimColor: Color = Color(0xFFE65100),
    val coverColor: Color = Color(0xFF2E7D32),
    val labelGray: Color = Color(0xFF757575),
    val sectionLineColor: Color = Color(0xFFC62828),
    val totalsGold: Color = Color(0xFFFFD700),
    // ── Force diagram / misc (same in both themes) ──
    val stressPink: Color = Color(0xFFE91E8C),
    val soilBrown: Color = Color(0xFF8B6914),
    val waterBlue: Color = Color(0xFF3498DB),
    val accentCyan: Color = Color(0xFF00BCD4),
    val centerLine: Color = Color(0x4488FF88),
    val gridLine: Color = Color(0x33FFFFFF),
    val sectionLine: Color = Color(0xFFE74C3C),
    val safeCheckGreen: Color = Color(0x33FFFFFF)
) {
    companion object {
        /** Light theme drawing palette */
        val Light = DrawingColors(
            concreteFill = Color(0xFFE0E0E0),
            concreteStroke = Color(0xFF424242),
            textPrimary = Color(0xFF212121),
            textSecondary = Color(0xFF757575),
            dimLineColor = Color(0xFF6A6A6A),
            extensionGray = Color(0xFF888888),
            hatchColor = Color(0x60BDBDBD),
            tableHeaderBg = Color(0xFF1A237E),
            tableHeaderText = Color(0xFFFFFFFF),
            tableCellText = Color(0xFF212121),
            tableRowColor = Color(0xFFFAFAFA),
            tableRowAltColor = Color(0xFFF5F5F5),
            tableTitleBg = Color(0xFF263238),
            tableBorder = Color(0xFF37474F),
            tableRowBorder = Color(0xFFBDBDBD),
            tableColumnBorder = Color(0xFF90A4AE),
            stripColor = Color(0xFF757575),
            labelGray = Color(0xFF757575),
            gridLine = Color(0x22000000),
            safeCheckGreen = Color(0x22000000),
            ribFill = Color(0xFFBDBDBD),
            dropFill = Color(0xFFB0BEC5),
            columnFill = Color(0xFF616161),
            planBackground = Color(0xFF37474F)
        )

        /** Dark theme drawing palette */
        val Dark = DrawingColors(
            concreteFill = Color(0xFF2A2A2A),
            concreteStroke = Color(0xFF9E9E9E),
            textPrimary = Color(0xFFE0E0E0),
            textSecondary = Color(0xFFB0B0B0),
            dimLineColor = Color(0xFFBDBDBD),
            extensionGray = Color(0xFFAAAAAA),
            hatchColor = Color(0x50888888),
            tableHeaderBg = Color(0xFF283593),
            tableHeaderText = Color(0xFFFFFFFF),
            tableCellText = Color(0xFFE0E0E0),
            tableRowColor = Color(0xFF2C2C2C),
            tableRowAltColor = Color(0xFF333333),
            tableTitleBg = Color(0xFF37474F),
            tableBorder = Color(0xFF546E7A),
            tableRowBorder = Color(0xFF424242),
            tableColumnBorder = Color(0xFF546E7A),
            stripColor = Color(0xFF9E9E9E),
            labelGray = Color(0xFFB0B0B0),
            gridLine = Color(0x33FFFFFF),
            safeCheckGreen = Color(0x33FFFFFF),
            ribFill = Color(0xFF4A4A4A),
            dropFill = Color(0xFF455A64),
            columnFill = Color(0xFF424242),
            planBackground = Color(0xFF1E1E1E)
        )
    }
}

/**
 * Returns the theme-appropriate [DrawingColors] palette.
 * Must be called from a @Composable context so it reacts to theme changes.
 */
@Composable
fun drawingColors(): DrawingColors =
    if (isSystemInDarkTheme()) DrawingColors.Dark else DrawingColors.Light

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
    color: Color = DrawingColorDefaults.DimensionWhite,
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
    color: Color = DrawingColorDefaults.DimensionWhite,
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
    color: Color = DrawingColorDefaults.HatchColor
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
    color: Color = DrawingColorDefaults.RebarBlue,
    label: String? = null,
    labelColor: Color = DrawingColorDefaults.DimensionWhite,
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
    color: Color = DrawingColorDefaults.StirrupPurple,
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
    color: Color = DrawingColorDefaults.SupportColor
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
    color: Color = DrawingColorDefaults.SupportColor
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
    color: Color = DrawingColorDefaults.SupportColor
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
    color: Color = DrawingColorDefaults.CenterLine,
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
    color: Color = DrawingColorDefaults.SectionLine,
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
    headerBg: Color = DrawingColorDefaults.TableHeaderBg,
    altRowBg: Color = DrawingColorDefaults.TableRowAlt,
    textColor: Color = DrawingColorDefaults.DimensionWhite,
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
            drawLine(DrawingColorDefaults.ExtensionGray, Offset(cx, y), Offset(cx, y + totalH), 1f)
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
                textColor, textSize, center = true, maxWidth = colWidths[colIdx].toInt()
            )
            cx += colWidths[colIdx]
        }
        // Bottom divider
        drawLine(
            DrawingColorDefaults.ExtensionGray, Offset(x, ry + rowHeight),
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
    fillColor: Color = DrawingColorDefaults.StressPink.copy(alpha = 0.25f),
    lineColor: Color = DrawingColorDefaults.StressPink,
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
        DrawingColorDefaults.ExtensionGray,
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
 * For Arabic text, uses StaticLayout for proper BIDI reordering + HarfBuzz shaping.
 */
fun DrawScope.drawTextAnnotated(
    text: String,
    x: Float,
    y: Float,
    color: Color = DrawingColorDefaults.DimensionWhite,
    size: Float = 22f,
    center: Boolean = false,
    bold: Boolean = false,
    rotation: Float = 0f,
    maxWidth: Int? = null // [NEW] Optional constraint
) {
    if (text.isBlank()) return
    drawContext.canvas.nativeCanvas.apply {
        val hasArabic = text.any { it.code in 0x0600..0x06FF || it.code in 0xFB50..0xFEFF }

        if (!hasArabic || rotation != 0f) {
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
                if (hasArabic) {
                    this.typeface = try {
                        com.civileg.app.utils.ArabicFontProvider.getArabicTypeface(
                            com.civileg.app.CivilEGApplication.instance.applicationContext,
                            bold = bold
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
        } else {
            // Arabic text: use StaticLayout for proper BIDI + shaping
            val tf = try {
                com.civileg.app.utils.ArabicFontProvider.getArabicTypeface(
                    com.civileg.app.CivilEGApplication.instance.applicationContext,
                    bold = bold
                )
            } catch (_: Exception) { null }
            val tp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
                this.textSize = size
                this.isAntiAlias = true
                this.textAlign = if (center) android.graphics.Paint.Align.CENTER
                                 else android.graphics.Paint.Align.LEFT
                this.typeface = tf ?: android.graphics.Typeface.SANS_SERIF
            }
            
            // FIX: If centered, we use maxWidth or a large buffer to prevent truncation/wrapping
            val lWidth = maxWidth ?: (if (center) 800 else (this.width - x).toInt()).coerceAtLeast(1)
            
            val layoutAlign = when {
                center -> android.text.Layout.Alignment.ALIGN_CENTER
                else -> android.text.Layout.Alignment.ALIGN_NORMAL
            }
            val sl = android.text.StaticLayout.Builder
                .obtain(text, 0, text.length, tp, lWidth)
                .setAlignment(layoutAlign)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
            val drawX = when {
                center -> x - sl.width / 2f
                else -> x
            }
            save()
            translate(drawX, y - tp.ascent() - tp.descent() / 2f)
            sl.draw(this)
            restore()
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
    color: Color = DrawingColorDefaults.DimensionWhite,
    borderColor: Color = DrawingColorDefaults.ExtensionGray
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
    color: Color = DrawingColorDefaults.DimensionWhite
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