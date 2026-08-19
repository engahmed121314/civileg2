package com.civileg.app.ui.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp
import com.civileg.app.domain.*
import kotlin.math.*

// ─── Color Palette ───────────────────────────────────────────────

private object C {
    val Concrete = DrawingColors.ConcreteTopGray
    val ConcreteDark = DrawingColors.ConcreteSideGray
    val ConcreteFill = Color(0xFF2A2A3A)
    val Bar = DrawingColors.RebarBlue
    val BarLight = Color(0xFF7EC8E3)
    val Tie = DrawingColors.StirrupPurple
    val TieLight = Color(0xFFB07CC8)
    val HorzBar = Color(0xFFE67E22)  // orange for horizontal
    val White = DrawingColors.DimensionWhite
    val DimLine = DrawingColors.ExtensionGray
    val Safe = DrawingColors.SafeGreen
    val Unsafe = DrawingColors.UnsafeRed
    val MomentArrow = Color(0xFFE91E63)
    val ShearArrow = Color(0xFF9C27B0)
    val AxialArrow = Color(0xFF2196F3)
    val BeZone = Color(0x44FF6600)
    val Hatch = DrawingColors.HatchColor
    val Ground = Color(0xFF8B6914)
    val Coupling = Color(0xFFFF9800)
}

// ─── Main Composable ────────────────────────────────────────────

@Composable
fun ProfessionalShearWallDrawing(
    wallLength: Double,
    wallThickness: Double,
    totalHeight: Double,
    storyHeight: Double,
    verticalRebar: RebarResult,
    horizontalRebar: RebarResult,
    boundaryElementType: BoundaryElementType,
    boundaryRebar: RebarResult?,
    couplingBeamResult: CouplingBeamResult?,
    wallType: WallType,
    wallShape: String,
    axialLoad: Double,
    shearForce: Double,
    bendingMoment: Double,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
    ) {
        val w = size.width
        val h = size.height
        val pad = 40f
        val ip = 16f  // inner padding

        // ── Layout: Left half = elevation, Right half = section ──
        val halfW = (w - 3 * pad) / 2f
        val elevationLeft = pad
        val sectionLeft = 2 * pad + halfW

        drawWallElevation(
            left = elevationLeft, top = pad + 30f,
            width = halfW, height = h - 2 * pad - 50f,
            wallLength = wallLength, totalHeight = totalHeight,
            storyHeight = storyHeight, wallThickness = wallThickness,
            axialLoad = axialLoad, shearForce = shearForce,
            bendingMoment = bendingMoment, wallType = wallType
        )

        drawWallSection(
            left = sectionLeft, top = pad + 30f,
            width = halfW, height = h - 2 * pad - 50f,
            wallLength = wallLength, wallThickness = wallThickness,
            verticalRebar = verticalRebar, horizontalRebar = horizontalRebar,
            boundaryElementType = boundaryElementType, boundaryRebar = boundaryRebar,
            wallShape = wallShape
        )

        // Labels
        drawTextAnnotated("ELEVATION", elevationLeft + halfW / 2f, pad + 16f,
            C.White, 18f, center = true, bold = true)
        drawTextAnnotated("SECTION A-A", sectionLeft + halfW / 2f, pad + 16f,
            C.White, 18f, center = true, bold = true)
    }
}

// ═══════════════════════════════════════════════════════════════
// 1. WALL ELEVATION
// ═══════════════════════════════════════════════════════════════

private fun DrawScope.drawWallElevation(
    left: Float, top: Float, width: Float, height: Float,
    wallLength: Double, totalHeight: Double,
    storyHeight: Double, wallThickness: Double,
    axialLoad: Double, shearForce: Double,
    bendingMoment: Double, wallType: WallType
) {
    val wallW = width * 0.35f  // wall width in elevation (visual thickness)
    val wallH = height * 0.8f
    val wallLeft = left + (width - wallW) / 2f
    val wallTop = top + 30f
    val wallBottom = wallTop + wallH
    val numStories = (totalHeight / storyHeight).toInt().coerceAtLeast(1)
    val storyH = wallH / numStories

    // ── Concrete fill ────────────────────────────────────────────
    drawRect(
        color = C.ConcreteFill,
        topLeft = Offset(wallLeft, wallTop),
        size = Size(wallW, wallH)
    )

    // ── Story lines ──────────────────────────────────────────────
    for (i in 1 until numStories) {
        val y = wallTop + i * storyH
        drawLine(C.DimLine, Offset(wallLeft, y), Offset(wallLeft + wallW, y), 0.8f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
    }

    // ── Wall outline ─────────────────────────────────────────────
    drawRect(
        color = C.Concrete,
        topLeft = Offset(wallLeft, wallTop),
        size = Size(wallW, wallH),
        style = Stroke(2f)
    )

    // ── Boundary element zones (hatched) at base ─────────────────
    val beH = min(storyH * 1.5f, wallH * 0.3f)
    if (wallType == WallType.SPECIAL || wallType == WallType.COUPLED) {
        // Left BE
        drawRect(
            color = C.BeZone,
            topLeft = Offset(wallLeft, wallBottom - beH),
            size = Size(wallW * 0.2f, beH)
        )
        // Right BE
        drawRect(
            color = C.BeZone,
            topLeft = Offset(wallLeft + wallW * 0.8f, wallBottom - beH),
            size = Size(wallW * 0.2f, beH)
        )
    }

    // ── Vertical rebar lines ─────────────────────────────────────
    val numBars = 5
    for (i in 0 until numBars) {
        val x = wallLeft + wallW * (i + 1) / (numBars + 1)
        drawLine(C.Bar, Offset(x, wallTop + 10f), Offset(x, wallBottom - 2f), 1.5f)
    }

    // ── Horizontal rebar lines (distributed) ────────────────────
    val hSpacing = storyH / 5f
    var y = wallTop + hSpacing
    while (y < wallBottom - hSpacing) {
        drawLine(C.HorzBar, Offset(wallLeft + 3f, y), Offset(wallLeft + wallW - 3f, y), 1.2f)
        y += hSpacing
    }

    // ── Ground / Fixed support ───────────────────────────────────
    drawFixedSupport(wallLeft - 8f, wallBottom, 12f, 30f, C.Ground)
    drawFixedSupport(wallLeft + wallW - 4f, wallBottom, 12f, 30f, C.Ground)
    drawLine(C.Ground, Offset(wallLeft - 20f, wallBottom + 30f),
        Offset(wallLeft + wallW + 20f, wallBottom + 30f), 2f)
    for (i in 0 until 8) {
        val hx = wallLeft - 15f + i * (wallW + 30f) / 7f
        drawLine(C.Ground, Offset(hx, wallBottom + 30f), Offset(hx - 5f, wallBottom + 38f), 1f)
    }

    // ── Axial load arrow (downward at top) ───────────────────────
    if (axialLoad > 0) {
        val cx = wallLeft + wallW / 2f
        val arrowTop = wallTop - 25f
        drawLine(C.AxialArrow, Offset(cx, arrowTop), Offset(cx, wallTop - 2f), 2f, cap = StrokeCap.Round)
        drawPath(path = Path().apply {
            moveTo(cx, wallTop - 2f)
            lineTo(cx - 4f, wallTop - 10f)
            lineTo(cx + 4f, wallTop - 10f)
            close()
        }, color = C.AxialArrow)
        drawTextAnnotated("Pu=${"%.0f".format(axialLoad)}kN", cx, arrowTop - 10f,
            C.AxialArrow, 16f, center = true)
    }

    // ── Shear arrow (at base, horizontal) ────────────────────────
    if (shearForce > 0) {
        val arrowY = wallBottom - 10f
        val arrowStart = wallLeft + wallW + 5f
        val arrowEnd = arrowStart + 30f
        drawLine(C.ShearArrow, Offset(arrowEnd, arrowY), Offset(arrowStart + 5f, arrowY), 2f, cap = StrokeCap.Round)
        drawPath(path = Path().apply {
            moveTo(arrowStart + 5f, arrowY)
            lineTo(arrowStart + 12f, arrowY - 4f)
            lineTo(arrowStart + 12f, arrowY + 4f)
            close()
        }, color = C.ShearArrow)
        drawTextAnnotated("Vu", arrowEnd + 4f, arrowY - 6f, C.ShearArrow, 16f)
    }

    // ── Moment arc (at base) ────────────────────────────────────
    if (bendingMoment > 0) {
        val cx = wallLeft - 15f
        val cy = wallBottom - 20f
        val radius = 15f
        val path = Path()
        // Draw arc from ~0° to ~270°
        for (i in 0..20) {
            val angle = -PI * i / 15f
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = C.MomentArrow, style = Stroke(1.5f))
        // Arrowhead at end of arc
        val endAngle = -PI * 20f / 15f
        val ax = cx + radius * cos(endAngle).toFloat()
        val ay = cy + radius * sin(endAngle).toFloat()
        drawCircle(C.MomentArrow, radius = 2.5f, center = Offset(ax, ay))
        drawTextAnnotated("Mu", cx - 8f, cy + radius + 8f, C.MomentArrow, 16f)
    }

    // ── Dimensions ───────────────────────────────────────────────
    // Total height
    drawVerticalDimension(wallTop, wallBottom, wallLeft + wallW + 10f,
        "${"%.1f".format(totalHeight / 1000.0)}m", C.White, 16f, 18f)

    // Story height
    drawVerticalDimension(wallTop, wallTop + storyH, wallLeft - 10f,
        "${"%.1f".format(storyHeight / 1000.0)}m", C.DimLine, 14f, 18f)

    // Wall type label
    drawTextAnnotated(wallType.name, wallLeft + wallW / 2f, wallBottom + 45f,
        C.White, 14f, center = true)
}

// ═══════════════════════════════════════════════════════════════
// 2. WALL CROSS-SECTION
// ═══════════════════════════════════════════════════════════════

private fun DrawScope.drawWallSection(
    left: Float, top: Float, width: Float, height: Float,
    wallLength: Double, wallThickness: Double,
    verticalRebar: RebarResult,
    horizontalRebar: RebarResult,
    boundaryElementType: BoundaryElementType,
    boundaryRebar: RebarResult?,
    wallShape: String
) {
    val pad = 40f
    val availW = width - 2 * pad
    val availH = height - 2 * pad - 10f

    // Scale wall section to fit (wall is long and thin)
    val scaleH = availW / wallLength
    val scaleV = availH / wallThickness
    val scale = min(scaleH, scaleV) * 0.85f

    val drawLw = wallLength * scale
    val drawTw = wallThickness * scale
    val ox = left + pad + (availW - drawLw) / 2f
    val oy = top + pad + 30f + (availH - drawTw) / 2f
    val cover = 25f * scale

    // ── Concrete section ─────────────────────────────────────────
    drawRect(
        color = C.ConcreteFill,
        topLeft = Offset(ox, oy),
        size = Size(drawLw, drawTw)
    )

    // ── Flange for L/T shapes ────────────────────────────────────
    if (wallShape == "L-shaped" || wallShape == "T-shaped") {
        val flangeLen = wallThickness * 3 * scale
        val flangeThk = wallThickness * 0.8 * scale
        if (wallShape == "L-shaped") {
            // Flange extends to the left
            drawRect(
                color = C.ConcreteFill,
                topLeft = Offset(ox - flangeLen, oy),
                size = Size(flangeLen, flangeThk)
            )
            drawRect(
                color = C.Concrete,
                topLeft = Offset(ox - flangeLen, oy),
                size = Size(flangeLen, flangeThk),
                style = Stroke(2f)
            )
        } else {
            // T-shape: flange at top
            val flangeW = wallLength * 0.8 * scale
            val flangeH = wallThickness * 0.8 * scale
            drawRect(
                color = C.ConcreteFill,
                topLeft = Offset(ox + (drawLw - flangeW) / 2f, oy - flangeH),
                size = Size(flangeW, flangeH)
            )
            drawRect(
                color = C.Concrete,
                topLeft = Offset(ox + (drawLw - flangeW) / 2f, oy - flangeH),
                size = Size(flangeW, flangeH),
                style = Stroke(2f)
            )
        }
    }

    // ── Boundary element zones ───────────────────────────────────
    val beLen = if (boundaryElementType != BoundaryElementType.NONE) {
        max(drawLw * 0.12f, 30f)
    } else 0f

    if (beLen > 0) {
        // Left BE zone
        drawRect(
            color = C.BeZone,
            topLeft = Offset(ox, oy),
            size = Size(beLen, drawTw)
        )
        // Right BE zone
        drawRect(
            color = C.BeZone,
            topLeft = Offset(ox + drawLw - beLen, oy),
            size = Size(beLen, drawTw)
        )

        // BE tie rectangles
        val tieSpacing = boundaryRebar?.spacing?.toFloat()?.let { it * scale } ?: 25f
        val numTies = max(2, (beLen / max(tieSpacing, 10f)).toInt())
        for (i in 0 until numTies) {
            val tx = ox + 4f + i * (beLen - 8f) / max(1, numTies - 1)
            drawRoundRect(
                color = C.Tie,
                topLeft = Offset(tx, oy + 3f),
                size = Size(max(6f, beLen / numTies - 4f), drawTw - 6f),
                cornerRadius = CornerRadius(3f),
                style = Stroke(1.2f)
            )
        }
    }

    // ── Main outline ─────────────────────────────────────────────
    drawRect(
        color = C.Concrete,
        topLeft = Offset(ox, oy),
        size = Size(drawLw, drawTw),
        style = Stroke(2.5f)
    )

    // ── Vertical rebar circles ───────────────────────────────────
    val numBars = verticalRebar.bars.coerceIn(2, 20)
    val barR = max(3f, (verticalRebar.diameter / 2f) * scale * 0.8f)
    for (i in 0 until numBars) {
        val x = if (numBars > 1) {
            ox + cover + i * (drawLw - 2 * cover) / (numBars - 1)
        } else ox + drawLw / 2f
        drawCircle(color = C.Bar, radius = barR, center = Offset(x, oy + drawTw / 2f))
        // Inner dot
        drawCircle(color = C.BarLight, radius = barR * 0.35f, center = Offset(x, oy + drawTw / 2f))
    }

    // ── Horizontal rebar marks (top/bottom edges) ────────────────
    val hBarR = max(2f, (horizontalRebar.diameter / 2f) * scale * 0.6f)
    val numHBars = max(3, (drawLw / 50f).toInt())
    for (i in 0 until numHBars) {
        val x = ox + (i + 1) * drawLw / (numHBars + 1)
        // Top edge
        drawCircle(color = C.HorzBar, radius = hBarR, center = Offset(x, oy + hBarR + 2f))
        // Bottom edge
        drawCircle(color = C.HorzBar, radius = hBarR, center = Offset(x, oy + drawTw - hBarR - 2f))
    }

    // ── Stirrup/tie around web (between BE zones) ────────────────
    val webLeft = ox + beLen
    val webRight = ox + drawLw - beLen
    if (webRight > webLeft + 10f) {
        drawRoundRect(
            color = C.Tie,
            topLeft = Offset(webLeft + 2f, oy + 2f),
            size = Size(webRight - webLeft - 4f, drawTw - 4f),
            cornerRadius = CornerRadius(4f),
            style = Stroke(1.5f)
        )
    }

    // ── Dimensions ───────────────────────────────────────────────
    // Wall length (bottom)
    drawHorizontalDimension(ox, ox + drawLw, oy + drawTw + 5f,
        "${wallLength.toInt()} mm", C.White, 16f, 15f)
    // Wall thickness (right)
    drawVerticalDimension(oy, oy + drawTw, ox + drawLw + 5f,
        "${wallThickness.toInt()} mm", C.White, 16f, 15f)

    // ── Rebar legend ─────────────────────────────────────────────
    val legY = oy + drawTw + 35f
    drawCircle(C.Bar, radius = 4f, center = Offset(ox + 6f, legY))
    drawTextAnnotated("Vert: ${verticalRebar.bars}Φ${verticalRebar.diameter}",
        ox + 16f, legY - 5f, C.Bar, 14f)

    drawCircle(C.HorzBar, radius = 3f, center = Offset(ox + drawLw / 2f - 80f, legY))
    drawTextAnnotated("Horz: Φ${horizontalRebar.diameter}@${horizontalRebar.spacing}mm",
        ox + drawLw / 2f - 70f, legY - 5f, C.HorzBar, 14f)

    if (boundaryElementType != BoundaryElementType.NONE) {
        drawCircle(C.Tie, radius = 3f, center = Offset(ox + drawLw - 110f, legY))
        drawTextAnnotated("BE: ${boundaryElementType.name}",
            ox + drawLw - 100f, legY - 5f, C.Tie, 14f)
    }
}

// ═══════════════════════════════════════════════════════════════
// 3. FIXED SUPPORT DRAWING
// ═══════════════════════════════════════════════════════════════

private fun DrawScope.drawFixedSupport(
    x: Float, y: Float, width: Float, height: Float,
    color: Color
) {
    drawRect(
        color = color.copy(alpha = 0.7f),
        topLeft = Offset(x, y),
        size = Size(width, height)
    )
    val lines = (height / 6f).toInt()
    for (i in 0 until lines) {
        val hy = y + i * 6f
        drawLine(color, Offset(x, hy + 6f), Offset(x + width, hy), 1f)
    }
}

// ═══════════════════════════════════════════════════════════════
// 4. DIMENSION LINES
// ═══════════════════════════════════════════════════════════════

private fun DrawScope.drawHorizontalDimension(
    x1: Float, x2: Float, y: Float,
    text: String, color: Color = C.White,
    textSize: Float = 18f, offset: Float = 18f
) {
    val dimY = y + offset
    val extLen = 6f
    val arrowSize = 3.5f
    drawLine(color, Offset(x1, y + 2f), Offset(x1, dimY + extLen), 0.8f)
    drawLine(color, Offset(x2, y + 2f), Offset(x2, dimY + extLen), 0.8f)
    drawLine(color, Offset(x1 + arrowSize, dimY), Offset(x2 - arrowSize, dimY), 1.2f)
    drawPath(path = Path().apply {
        moveTo(x1, dimY); lineTo(x1 + arrowSize * 2, dimY - arrowSize)
        lineTo(x1 + arrowSize * 2, dimY + arrowSize); close()
    }, color = color)
    drawPath(path = Path().apply {
        moveTo(x2, dimY); lineTo(x2 - arrowSize * 2, dimY - arrowSize)
        lineTo(x2 - arrowSize * 2, dimY + arrowSize); close()
    }, color = color)
    drawTextAnnotated(text, (x1 + x2) / 2f, dimY + extLen + 1f, color, textSize, center = true)
}

private fun DrawScope.drawVerticalDimension(
    y1: Float, y2: Float, x: Float,
    text: String, color: Color = C.White,
    textSize: Float = 18f, offset: Float = 18f
) {
    val dimX = x + offset
    val extLen = 6f
    val arrowSize = 3.5f
    drawLine(color, Offset(x + 2f, y1), Offset(dimX + extLen, y1), 0.8f)
    drawLine(color, Offset(x + 2f, y2), Offset(dimX + extLen, y2), 0.8f)
    drawLine(color, Offset(dimX, y1 + arrowSize), Offset(dimX, y2 - arrowSize), 1.2f)
    drawTextAnnotated(text, dimX + extLen + 1f, (y1 + y2) / 2f, color, textSize,
        center = true, rotation = -90f)
}

// ═══════════════════════════════════════════════════════════════
// 5. TEXT HELPER (uses nativeCanvas)
// ═══════════════════════════════════════════════════════════════

private fun DrawScope.drawTextAnnotated(
    text: String, x: Float, y: Float,
    color: Color, textSize: Float,
    center: Boolean = false, bold: Boolean = false,
    rotation: Float = 0f
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            this.color = color.toArgb()
            this.textSize = textSize * density
            this.isFakeBoldText = bold
            this.antiAlias = true
        }
        if (rotation != 0f) {
            save()
            rotate(rotation, x * density, y * density)
        }
        if (center) {
            val tw = paint.measureText(text)
            drawText(text, (x - tw / (2 * density)) * density, (y + textSize * 0.35f) * density, paint)
        } else {
            drawText(text, x * density, (y + textSize * 0.35f) * density, paint)
        }
        if (rotation != 0f) restore()
    }
}
