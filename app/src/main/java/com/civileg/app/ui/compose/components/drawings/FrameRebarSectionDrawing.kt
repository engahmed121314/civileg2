package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.civileg.app.R
import com.civileg.app.domain.entities.ConcreteMemberDesignResult
import com.civileg.app.domain.entities.FrameMemberType
import kotlin.math.min

private object RebarSectionColors {
    val ConcreteFill = Color(0xFFF2F4F5)
    val ConcreteOutline = Color(0xFF37474F)
    val Stirrup = Color(0xFF2E7D32)
    val BottomBar = Color(0xFF1565C0)
    val BottomBarOutline = Color(0xFF0D47A1)
    val TopBar = Color(0xFFE53935)
    val TopBarOutline = Color(0xFFB71C1C)
    val Dimension = Color(0xFF546E7A)
    val Caption = Color(0xFF455A64)
}

@Composable
fun FrameRebarSectionDrawing(
    result: ConcreteMemberDesignResult,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val isColumn = result.memberType == FrameMemberType.Column
    val barDia = result.barDia.takeIf { it > 0.0 }?.toInt() ?: 16
    val tieDia = result.stirrupDia.takeIf { it > 0.0 }?.toInt() ?: 8
    val tieSpacing = result.stirrupSpacing.takeIf { it > 0.0 }?.toInt() ?: 150
    val totalBars = if (isColumn) {
        maxOf(result.numBarsTop, result.numBarsBot)
    } else {
        result.numBarsTop + result.numBarsBot
    }
    val callout = if (isColumn) {
        stringResource(
            R.string.frame_rebar_column_format,
            totalBars,
            barDia,
            tieDia,
            tieSpacing
        )
    } else {
        val mainBars = stringResource(
            R.string.frame_rebar_beam_bot_top_format,
            result.numBarsBot,
            barDia,
            result.numBarsTop,
            barDia
        )
        val stirrups = stringResource(
            R.string.frame_rebar_beam_stirrup_format,
            tieDia,
            tieSpacing
        )
        "$mainBars $stirrups"
    }

    Canvas(modifier = modifier) {
        drawRebarSection(
            result = result,
            isColumn = isColumn,
            barDia = barDia,
            totalBars = totalBars,
            callout = callout,
            textMeasurer = textMeasurer
        )
    }
}

private fun DrawScope.drawRebarSection(
    result: ConcreteMemberDesignResult,
    isColumn: Boolean,
    barDia: Int,
    totalBars: Int,
    callout: String,
    textMeasurer: TextMeasurer
) {
    if (size.width <= 0f || size.height <= 0f) return

    val widthMm = result.section.width.coerceAtLeast(1.0)
    val depthMm = result.section.depth.coerceAtLeast(1.0)
    val coverMm = result.section.cover.coerceAtLeast(0.0)

    val calloutHeight = 30f
    val dimSpace = 58f
    val padding = 12f
    val availableWidth = (size.width - padding * 2f - dimSpace).coerceAtLeast(24f)
    val availableHeight = (size.height - padding * 2f - calloutHeight).coerceAtLeast(24f)
    val scale = min(availableWidth / widthMm.toFloat(), availableHeight / depthMm.toFloat())
    val drawWidth = widthMm.toFloat() * scale
    val drawHeight = depthMm.toFloat() * scale
    val left = padding + (availableWidth - drawWidth) / 2f
    val top = padding + (availableHeight - drawHeight) / 2f
    val outlineSize = Size(drawWidth, drawHeight)

    drawRect(RebarSectionColors.ConcreteFill, Offset(left, top), outlineSize)
    drawRect(
        RebarSectionColors.ConcreteOutline,
        Offset(left, top),
        outlineSize,
        style = Stroke(width = 2f)
    )

    val coverPx = (coverMm.toFloat() * scale)
        .coerceIn(2f, min(drawWidth, drawHeight) * 0.25f)
    val tieInset = coverPx
    drawRect(
        RebarSectionColors.Stirrup,
        Offset(left + tieInset, top + tieInset),
        Size(drawWidth - 2f * tieInset, drawHeight - 2f * tieInset),
        style = Stroke(width = 1.5f)
    )

    val barRadius = (barDia.toFloat() / 2f * scale).coerceIn(2.5f, 7f)
    val barInset = coverPx + barRadius + 2f
    val barLeft = left + barInset
    val barRight = left + drawWidth - barInset
    val barTop = top + barInset
    val barBottom = top + drawHeight - barInset

    if (isColumn) {
        val positions = mutableListOf(
            Offset(barLeft, barTop),
            Offset(barRight, barTop),
            Offset(barLeft, barBottom),
            Offset(barRight, barBottom)
        )
        val remaining = (totalBars - 4).coerceAtLeast(0)
        if (remaining > 0) {
            val alongTop = (remaining + 1) / 2
            val alongBottom = remaining - alongTop
            for (index in 1..alongTop) {
                positions.add(
                    Offset(barLeft + (barRight - barLeft) * index / (alongTop + 1), barTop)
                )
            }
            for (index in 1..alongBottom) {
                positions.add(
                    Offset(barLeft + (barRight - barLeft) * index / (alongBottom + 1), barBottom)
                )
            }
        }
        positions.take(totalBars).forEach { center ->
            drawCircle(RebarSectionColors.BottomBarOutline, barRadius + 1.5f, center)
            drawCircle(RebarSectionColors.BottomBar, barRadius, center)
        }
    } else {
        drawBarRow(
            count = result.numBarsBot,
            xStart = barLeft,
            xEnd = barRight,
            y = barBottom,
            radius = barRadius,
            fill = RebarSectionColors.BottomBar,
            outline = RebarSectionColors.BottomBarOutline
        )
        drawBarRow(
            count = result.numBarsTop,
            xStart = barLeft,
            xEnd = barRight,
            y = barTop,
            radius = barRadius,
            fill = RebarSectionColors.TopBar,
            outline = RebarSectionColors.TopBarOutline
        )
    }

    drawWidthDimension(left, left + drawWidth, top + drawHeight, widthMm, textMeasurer)
    drawDepthDimension(left + drawWidth, top, top + drawHeight, depthMm, textMeasurer)

    val calloutStyle = TextStyle(
        color = RebarSectionColors.Caption,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium
    )
    val calloutLayout = textMeasurer.measure(callout, calloutStyle)
    drawText(
        textLayoutResult = calloutLayout,
        topLeft = Offset(
            x = ((size.width - calloutLayout.size.width) / 2f).coerceAtLeast(padding),
            y = size.height - calloutHeight
        )
    )
}

private fun DrawScope.drawBarRow(
    count: Int,
    xStart: Float,
    xEnd: Float,
    y: Float,
    radius: Float,
    fill: Color,
    outline: Color
) {
    if (count <= 0) return
    if (count == 1) {
        val center = Offset((xStart + xEnd) / 2f, y)
        drawCircle(outline, radius + 1.5f, center)
        drawCircle(fill, radius, center)
        return
    }
    val spacing = (xEnd - xStart) / (count - 1)
    for (index in 0 until count) {
        val center = Offset(xStart + spacing * index, y)
        drawCircle(outline, radius + 1.5f, center)
        drawCircle(fill, radius, center)
    }
}

private fun DrawScope.drawWidthDimension(
    left: Float,
    right: Float,
    sectionBottom: Float,
    widthMm: Double,
    textMeasurer: TextMeasurer
) {
    val dimensionY = sectionBottom + 14f
    drawLine(
        RebarSectionColors.Dimension,
        Offset(left, sectionBottom),
        Offset(left, dimensionY),
        strokeWidth = 1f
    )
    drawLine(
        RebarSectionColors.Dimension,
        Offset(right, sectionBottom),
        Offset(right, dimensionY),
        strokeWidth = 1f
    )
    drawLine(
        RebarSectionColors.Dimension,
        Offset(left, dimensionY),
        Offset(right, dimensionY),
        strokeWidth = 1.2f
    )
    drawLine(
        RebarSectionColors.Dimension,
        Offset(left, dimensionY - 4f),
        Offset(left, dimensionY + 4f),
        strokeWidth = 1.2f
    )
    drawLine(
        RebarSectionColors.Dimension,
        Offset(right, dimensionY - 4f),
        Offset(right, dimensionY + 4f),
        strokeWidth = 1.2f
    )
    val style = TextStyle(
        color = RebarSectionColors.Dimension,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium
    )
    val label = textMeasurer.measure("${widthMm.toInt()}", style)
    drawText(
        textLayoutResult = label,
        topLeft = Offset(
            (left + right) / 2f - label.size.width / 2f,
            dimensionY + 2f
        )
    )
}

private fun DrawScope.drawDepthDimension(
    right: Float,
    top: Float,
    bottom: Float,
    depthMm: Double,
    textMeasurer: TextMeasurer
) {
    val dimensionX = right + 16f
    drawLine(
        RebarSectionColors.Dimension,
        Offset(right, top),
        Offset(dimensionX, top),
        strokeWidth = 1f
    )
    drawLine(
        RebarSectionColors.Dimension,
        Offset(right, bottom),
        Offset(dimensionX, bottom),
        strokeWidth = 1f
    )
    drawLine(
        RebarSectionColors.Dimension,
        Offset(dimensionX, top),
        Offset(dimensionX, bottom),
        strokeWidth = 1.2f
    )
    val style = TextStyle(
        color = RebarSectionColors.Dimension,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium
    )
    val label = textMeasurer.measure("h=${depthMm.toInt()}", style)
    drawText(
        textLayoutResult = label,
        topLeft = Offset(
            dimensionX + 3f,
            (top + bottom) / 2f - label.size.height / 2f
        )
    )
}
