package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedInput
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedResult
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val PtBackground = Color(0xFF15161F)
private val PtConcrete = Color(0xFF5A5F6B)
private val PtConcreteEdge = Color(0xFFB9C0CC)
private val PtDuct = Color(0xFF00C2A8)
private val PtTendon = Color(0xFF00E0C0)
private val PtMildSteel = Color(0xFF4A90D9)
private val PtStirrup = Color(0xFF9B59B6)
private val PtDimension = Color(0xFFD7DCE5)
private val PtCenterline = Color(0xFF7A8291)

/**
 * مقطع عرضي لعنصر مسبق الإجهاد: الخرسانة، مجاري الكابلات (ducts)،
 * حديد التسليح العادي، والأبعاد.
 */
@Composable
fun PostTensionedSectionDrawing(
    input: PostTensionedInput,
    result: PostTensionedResult? = null,
    modifier: Modifier = Modifier,
    height: Int = 300
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PtBackground)
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height.dp)) {
            drawPostTensionedSection(input, result)
        }
    }
}

/**
 * منظر رأسي (Elevation) للعنصر مع مسار الكابل المكافئ (Parabolic tendon).
 */
@Composable
fun PostTensionedElevationDrawing(
    input: PostTensionedInput,
    result: PostTensionedResult? = null,
    modifier: Modifier = Modifier,
    height: Int = 280
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PtBackground)
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height.dp)) {
            drawPostTensionedElevation(input, result)
        }
    }
}

private fun DrawScope.drawPostTensionedSection(
    input: PostTensionedInput,
    result: PostTensionedResult?
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val padX = w * 0.18f
    val padY = h * 0.14f
    val availW = w - 2 * padX
    val availH = h - 2 * padY
    if (availW <= 0f || availH <= 0f) return

    val scale = min(availW / input.width.toFloat(), availH / input.depth.toFloat())
    val beamW = input.width.toFloat() * scale
    val beamH = input.depth.toFloat() * scale
    val left = (w - beamW) / 2f
    val top = (h - beamH) / 2f
    val bottom = top + beamH
    val right = left + beamW
    val centerY = top + beamH / 2f

    // Concrete body
    drawRect(color = PtConcrete, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(beamW, beamH))
    drawRect(
        color = PtConcreteEdge,
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(beamW, beamH),
        style = Stroke(width = 2.dp.toPx())
    )

    // Flange for T-sections
    if (input.flangeWidth > input.width && input.flangeThickness > 0) {
        val flangeW = input.flangeWidth.toFloat() * scale
        val flangeH = input.flangeThickness.toFloat() * scale
        val flangeLeft = (w - flangeW) / 2f
        drawRect(
            color = PtConcrete,
            topLeft = Offset(flangeLeft, top),
            size = androidx.compose.ui.geometry.Size(flangeW, flangeH)
        )
        drawRect(
            color = PtConcreteEdge,
            topLeft = Offset(flangeLeft, top),
            size = androidx.compose.ui.geometry.Size(flangeW, flangeH),
            style = Stroke(width = 2.dp.toPx())
        )
    }

    // Mild steel (top and bottom) — 3 bars each face
    val coverPx = input.cover.toFloat() * scale
    val barR = max(3f, 8f * scale)
    for (i in 0 until 3) {
        val fx = left + beamW * (i + 1) / 4f
        drawCircle(color = PtMildSteel, radius = barR, center = Offset(fx, top + coverPx))
        drawCircle(color = PtMildSteel, radius = barR, center = Offset(fx, bottom - coverPx))
    }

    // Stirrups (2 vertical lines + hooks simplified)
    drawRect(
        color = PtStirrup,
        topLeft = Offset(left + coverPx, top + coverPx),
        size = androidx.compose.ui.geometry.Size(beamW - 2 * coverPx, beamH - 2 * coverPx),
        style = Stroke(width = 1.4.dp.toPx())
    )

    // Tendon ducts — place at the provided eccentricity below centroid
    val eccentricity = result?.prestressDesign?.providedEccentricity
        ?: (input.depth / 2.0 - input.cover - 35.0)
    val ductY = centerY + (eccentricity * scale).toFloat()
    val count = input.numTendons.coerceIn(1, 6)
    val ductR = max(4f, min(beamW / (count * 3f), 12f))
    val spacing = if (count > 1) (beamW - 2 * coverPx) / (count - 1) else 0f
    val startX = if (count > 1) left + coverPx else left + beamW / 2f
    for (i in 0 until count) {
        val cx = if (count > 1) startX + i * spacing else startX
        drawCircle(color = PtDuct, radius = ductR, center = Offset(cx, ductY))
        drawCircle(
            color = PtTendon,
            radius = ductR * 0.55f,
            center = Offset(cx, ductY)
        )
    }

    // Centerline
    drawLine(
        color = PtCenterline,
        start = Offset(left - 10f, centerY),
        end = Offset(right + 10f, centerY),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    )

    // Width dimension
    val dimY = bottom + 28f
    drawDimensionLine(left, dimY, right, dimY)
    drawLabel("b = ${input.width.roundToInt()} mm", (left + right) / 2f, dimY + 16f, PtDimension, 13f, android.graphics.Paint.Align.CENTER)

    // Depth dimension (right side)
    val dimX = right + 34f
    drawDimensionLine(dimX, top, dimX, bottom)
    drawLabel("h = ${input.depth.roundToInt()} mm", dimX + 8f, (top + bottom) / 2f, PtDimension, 13f)

    // Eccentricity annotation
    drawLine(
        color = PtTendon,
        start = Offset(right + 8f, centerY),
        end = Offset(right + 8f, ductY),
        strokeWidth = 1.2.dp.toPx()
    )
    drawLabel("e = ${eccentricity.roundToInt()} mm", right + 12f, (centerY + ductY) / 2f, PtTendon, 12f)

    // Tendon count label
    drawLabel("${input.numTendons} × ${input.tendonType.description}", left, top - 8f, PtTendon, 12f)
}

private fun DrawScope.drawPostTensionedElevation(
    input: PostTensionedInput,
    result: PostTensionedResult?
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val left = w * 0.10f
    val right = w * 0.90f
    val beamTop = h * 0.28f
    val beamBottom = h * 0.62f
    val beamH = beamBottom - beamTop
    val centerY = (beamTop + beamBottom) / 2f
    val spanPx = right - left

    // Beam outline
    drawRect(
        color = PtConcrete.copy(alpha = 0.35f),
        topLeft = Offset(left, beamTop),
        size = androidx.compose.ui.geometry.Size(spanPx, beamH)
    )
    drawLine(PtConcreteEdge, Offset(left, beamTop), Offset(right, beamTop), 2.dp.toPx())
    drawLine(PtConcreteEdge, Offset(left, beamBottom), Offset(right, beamBottom), 2.dp.toPx())
    drawLine(PtConcreteEdge, Offset(left, beamTop), Offset(left, beamBottom), 2.dp.toPx())
    drawLine(PtConcreteEdge, Offset(right, beamTop), Offset(right, beamBottom), 2.dp.toPx())

    // Supports
    val supportH = h * 0.09f
    drawSupport(left, beamBottom, supportH)
    drawSupport(right, beamBottom, supportH)

    // Centerline
    drawLine(
        color = PtCenterline,
        start = Offset(left - 12f, centerY),
        end = Offset(right + 12f, centerY),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    )

    // Parabolic tendon profile
    val eccentricity = result?.prestressDesign?.providedEccentricity
        ?: (input.depth / 2.0 - input.cover - 35.0)
    val ePx = (eccentricity / input.depth * beamH).toFloat().coerceIn(0f, beamH * 0.42f)
    val endY = centerY - beamH * 0.22f
    val midY = centerY + ePx

    val path = Path().apply {
        moveTo(left, endY)
        quadraticBezierTo((left + right) / 2f, midY + (midY - endY), right, endY)
    }
    drawPath(path, color = PtTendon, style = Stroke(width = 2.2.dp.toPx()))

    // Anchorage points
    drawCircle(PtDuct, 5f, Offset(left, endY))
    drawCircle(PtDuct, 5f, Offset(right, endY))

    // Eccentricity dimension at midspan
    val midX = (left + right) / 2f
    drawLine(
        color = PtDimension,
        start = Offset(midX, centerY),
        end = Offset(midX, midY),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
    )
    drawLabel("e = ${eccentricity.roundToInt()} mm", midX + 6f, (centerY + midY) / 2f, PtDimension, 12f)

    // Span dimension
    val dimY = beamBottom + supportH + 24f
    drawDimensionLine(left, dimY, right, dimY)
    drawLabel("L = ${input.span.roundToInt()} m", midX, dimY + 16f, PtDimension, 13f, android.graphics.Paint.Align.CENTER)

    // Tendon profile label
    drawLabel("Parabolic tendon (drape)", left, beamTop - 10f, PtTendon, 12f)
}

private fun DrawScope.drawSupport(x: Float, y: Float, height: Float) {
    val half = 12f
    val path = Path().apply {
        moveTo(x, y)
        lineTo(x - half, y + height)
        lineTo(x + half, y + height)
        close()
    }
    drawPath(path, color = PtConcreteEdge.copy(alpha = 0.7f))
}

private fun DrawScope.drawDimensionLine(x1: Float, y1: Float, x2: Float, y2: Float) {
    drawLine(PtDimension, Offset(x1, y1), Offset(x2, y2), 1.2.dp.toPx())
    if (x1 == x2) {
        drawLine(PtDimension, Offset(x1 - 5f, y1), Offset(x1 + 5f, y1), 1.2.dp.toPx())
        drawLine(PtDimension, Offset(x2 - 5f, y2), Offset(x2 + 5f, y2), 1.2.dp.toPx())
    } else {
        drawLine(PtDimension, Offset(x1, y1 - 5f), Offset(x1, y1 + 5f), 1.2.dp.toPx())
        drawLine(PtDimension, Offset(x2, y2 - 5f), Offset(x2, y2 + 5f), 1.2.dp.toPx())
    }
}

private fun DrawScope.drawLabel(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    textSize: Float,
    align: android.graphics.Paint.Align = android.graphics.Paint.Align.LEFT
) {
    drawContext.canvas.nativeCanvas.drawText(
        text,
        x,
        y,
        android.graphics.Paint().apply {
            this.color = color.toArgb()
            this.textSize = textSize
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = align
            isAntiAlias = true
        }
    )
}