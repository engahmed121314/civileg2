package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.SteelMemberDesignResult
import com.civileg.app.utils.SteelTables
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private object SteelSectionColors {
    val Fill = Color(0xFFCFD8DC)
    val Outline = Color(0xFF263238)
    val Accent = Color(0xFF1565C0)
    val Secondary = Color(0xFFE65100)
    val Dimension = Color(0xFF546E7A)
    val Caption = Color(0xFF455A64)
    val Centerline = Color(0xFF90A4AE)
}

private enum class FrameSteelShape(val label: String) {
    I("I / H Section"),
    Channel("Channel"),
    Angle("Angle"),
    HollowRect("RHS / SHS"),
    HollowRound("CHS"),
    Pipe("Pipe"),
    Tee("T Section"),
    PlateGirder("Plate Girder")
}

private fun steelShapeForSection(name: String): FrameSteelShape {
    val n = name.trim().uppercase(Locale.ROOT)
    return when {
        n.startsWith("PG ") || n.contains("PLATE GIRDER") -> FrameSteelShape.PlateGirder
        n.startsWith("PIPE") -> FrameSteelShape.Pipe
        n.startsWith("T ") || n.startsWith("TEE") || n.contains("T SECTION") -> FrameSteelShape.Tee
        n.startsWith("UPN") || n.startsWith("CHANNEL") || n.startsWith("C ") -> FrameSteelShape.Channel
        n.startsWith("L ") || n.startsWith("L-") || n.startsWith("ANGLE") -> FrameSteelShape.Angle
        n.startsWith("RHS") || n.startsWith("SHS") -> FrameSteelShape.HollowRect
        n.startsWith("CHS") -> FrameSteelShape.HollowRound
        else -> FrameSteelShape.I
    }
}

/**
 * Steel section drawing with three views (elevation, cross section and plan),
 * used both for the selected member preview and for the design results cards.
 */
@Composable
fun SteelSectionDrawing(
    sectionName: String,
    memberLengthMm: Double,
    memberType: FrameMemberType,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        drawSteelSectionViews(
            sectionName = sectionName,
            memberLengthMm = memberLengthMm,
            memberType = memberType,
            textMeasurer = textMeasurer
        )
    }
}

@Composable
fun FrameSteelSectionDrawing(
    result: SteelMemberDesignResult,
    memberLengthMm: Double,
    modifier: Modifier = Modifier
) {
    SteelSectionDrawing(
        sectionName = result.selectedSection,
        memberLengthMm = memberLengthMm,
        memberType = result.memberType,
        modifier = modifier
    )
}

fun DrawScope.drawSteelSectionViews(
    sectionName: String,
    memberLengthMm: Double,
    memberType: FrameMemberType,
    textMeasurer: TextMeasurer,
    topLeft: Offset = Offset.Zero,
    sheetSize: Size = size
) {
    val w = sheetSize.width
    val h = sheetSize.height
    if (w < 20f || h < 20f) return

    val pad = min(w, h) * 0.03f
    val headerH = h * 0.13f
    val footerH = h * 0.08f
    val left = topLeft.x + pad
    val contentTop = topLeft.y + headerH
    val contentH = (h - headerH - footerH).coerceAtLeast(10f)
    val gap = w * 0.015f
    val panelW = (w - 2f * pad - 2f * gap) / 3f
    if (panelW < 10f) return

    val isColumn = memberType == FrameMemberType.Column
    val lengthMm = if (memberLengthMm.isFinite() && memberLengthMm > 1.0) memberLengthMm else 6000.0
    val displayName = sectionName.trim().ifEmpty { "Steel Section" }
    val section = SteelTables.getSectionByName(displayName)
    val shape = steelShapeForSection(displayName)

    drawText(
        textMeasurer = textMeasurer,
        text = displayName,
        topLeft = Offset(left, topLeft.y + pad * 0.4f),
        style = TextStyle(
            color = SteelSectionColors.Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    )
    val shapeLabel = "${shape.label}  |  ${if (isColumn) "COLUMN" else "BEAM"}"
    val shapeLayout = textMeasurer.measure(
        text = shapeLabel,
        style = TextStyle(color = SteelSectionColors.Secondary, fontSize = 8.5.sp)
    )
    drawText(
        textMeasurer = textMeasurer,
        text = shapeLabel,
        topLeft = Offset(topLeft.x + w - pad - shapeLayout.size.width, topLeft.y + pad * 0.5f),
        style = TextStyle(color = SteelSectionColors.Secondary, fontSize = 8.5.sp)
    )

    val titleStyle = TextStyle(
        color = SteelSectionColors.Caption,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold
    )
    val panelTops = contentTop
    val panelBottom = contentTop + contentH
    val panels = listOf(
        Rect(left, panelTops, left + panelW, panelBottom),
        Rect(left + panelW + gap, panelTops, left + 2f * panelW + gap, panelBottom),
        Rect(left + 2f * (panelW + gap), panelTops, left + 3f * panelW + 2f * gap, panelBottom)
    )
    val titles = listOf("ELEVATION", "SECTION A-A", "PLAN")
    for ((index, panel) in panels.withIndex()) {
        val titleLayout = textMeasurer.measure(titles[index], titleStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = titles[index],
            topLeft = Offset(panel.center.x - titleLayout.size.width / 2f, panel.top),
            style = titleStyle
        )
    }

    val bodyTop = contentTop + contentH * 0.14f
    val contentAreas = panels.map {
        Rect(it.left + 6f, bodyTop, it.right - 6f, it.bottom - 4f)
    }

    if (section == null) {
        val message = "Section not found"
        val style = TextStyle(color = SteelSectionColors.Dimension, fontSize = 9.sp)
        val layout = textMeasurer.measure(message, style)
        drawText(
            textMeasurer = textMeasurer,
            text = message,
            topLeft = Offset(topLeft.x + (w - layout.size.width) / 2f, topLeft.y + h / 2f),
            style = style
        )
        return
    }

    drawElevation(contentAreas[0], section, lengthMm, isColumn, textMeasurer)
    drawCrossSectionView(contentAreas[1], section, shape, textMeasurer)
    drawPlanView(contentAreas[2], section, lengthMm, isColumn, shape, textMeasurer)

    val dims = if (shape == FrameSteelShape.HollowRound || shape == FrameSteelShape.Pipe) {
        String.format(
            Locale.ROOT,
            "\u00D8%.0f  t=%.1f mm   |   A=%.1f cm\u00B2   %.1f kg/m",
            section.depth, section.tw, section.area, section.weight
        )
    } else {
        String.format(
            Locale.ROOT,
            "h=%.0f  b=%.0f  tw=%.1f  tf=%.1f mm   |   A=%.1f cm\u00B2   %.1f kg/m",
            section.depth, section.width, section.tw, section.tf, section.area, section.weight
        )
    }
    val footerStyle = TextStyle(color = SteelSectionColors.Dimension, fontSize = 8.sp)
    val footerLayout = textMeasurer.measure(dims, footerStyle)
    drawText(
        textMeasurer = textMeasurer,
        text = dims,
        topLeft = Offset(
            topLeft.x + (w - footerLayout.size.width) / 2f,
            topLeft.y + h - footerH + footerH * 0.25f
        ),
        style = footerStyle
    )
}

private fun DrawScope.drawElevation(
    area: Rect,
    section: SteelTables.SectionProperties,
    lengthMm: Double,
    isColumn: Boolean,
    textMeasurer: TextMeasurer
) {
    if (area.width < 8f || area.height < 8f) return
    val lineWidth = max(1f, area.width * 0.007f)
    val outline = Stroke(lineWidth)
    val flangeRatio = (section.tf / section.depth.coerceAtLeast(1.0)).toFloat().coerceIn(0.08f, 0.30f)
    val labelStyle = TextStyle(color = SteelSectionColors.Dimension, fontSize = 7.5.sp)
    val label = "L = ${lengthMm.toInt()} mm"
    val labelLayout = textMeasurer.measure(label, labelStyle)
    val labelTop = area.bottom - labelLayout.size.height

    if (isColumn) {
        val lengthPx = area.height * 0.78f
        val depthPx = area.width * 0.38f
        val cx = area.center.x
        val cy = area.top + lengthPx * 0.5f + area.height * 0.01f
        val top = cy - lengthPx / 2f
        val bottom = cy + lengthPx / 2f
        val leftX = cx - depthPx / 2f
        val rightX = cx + depthPx / 2f
        val flangePx = max(2f, depthPx * flangeRatio)

        val body = Path().apply {
            addRect(Rect(leftX, top, rightX, bottom))
        }
        drawPath(body, SteelSectionColors.Fill)
        drawPath(body, SteelSectionColors.Outline, style = outline)
        drawLine(
            color = SteelSectionColors.Outline,
            start = Offset(leftX + flangePx, top),
            end = Offset(leftX + flangePx, bottom),
            strokeWidth = lineWidth
        )
        drawLine(
            color = SteelSectionColors.Outline,
            start = Offset(rightX - flangePx, top),
            end = Offset(rightX - flangePx, bottom),
            strokeWidth = lineWidth
        )
        drawLine(
            color = SteelSectionColors.Secondary,
            start = Offset(leftX - 2f, top),
            end = Offset(rightX + 2f, top),
            strokeWidth = lineWidth * 2f
        )
        drawLine(
            color = SteelSectionColors.Secondary,
            start = Offset(leftX - 2f, bottom),
            end = Offset(rightX + 2f, bottom),
            strokeWidth = lineWidth * 2f
        )
        drawLine(
            color = SteelSectionColors.Centerline,
            start = Offset(cx, top),
            end = Offset(cx, bottom),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )
    } else {
        val lengthPx = area.width * 0.88f
        val depthPx = area.height * 0.30f
        val cx = area.center.x
        val cy = area.top + area.height * 0.42f
        val leftX = cx - lengthPx / 2f
        val rightX = cx + lengthPx / 2f
        val top = cy - depthPx / 2f
        val bottom = cy + depthPx / 2f
        val flangePx = max(2f, depthPx * flangeRatio)

        drawRect(
            color = SteelSectionColors.Fill,
            topLeft = Offset(leftX, top),
            size = Size(lengthPx, depthPx)
        )
        drawRect(
            color = SteelSectionColors.Outline,
            topLeft = Offset(leftX, top),
            size = Size(lengthPx, depthPx),
            style = outline
        )
        drawLine(
            color = SteelSectionColors.Outline,
            start = Offset(leftX, top + flangePx),
            end = Offset(rightX, top + flangePx),
            strokeWidth = lineWidth
        )
        drawLine(
            color = SteelSectionColors.Outline,
            start = Offset(leftX, bottom - flangePx),
            end = Offset(rightX, bottom - flangePx),
            strokeWidth = lineWidth
        )
        drawLine(
            color = SteelSectionColors.Secondary,
            start = Offset(leftX, top - 2f),
            end = Offset(leftX, bottom + 2f),
            strokeWidth = lineWidth * 2f
        )
        drawLine(
            color = SteelSectionColors.Secondary,
            start = Offset(rightX, top - 2f),
            end = Offset(rightX, bottom + 2f),
            strokeWidth = lineWidth * 2f
        )
        drawLine(
            color = SteelSectionColors.Centerline,
            start = Offset(leftX, cy),
            end = Offset(rightX, cy),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )
    }

    drawText(
        textMeasurer = textMeasurer,
        text = label,
        topLeft = Offset(area.center.x - labelLayout.size.width / 2f, labelTop),
        style = labelStyle
    )
}

private fun buildFrameShapePath(
    shape: FrameSteelShape,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    tw: Float,
    tf: Float
): Path {
    val right = left + width
    val bottom = top + height
    val t = max(tw, tf)
    return Path().apply {
        when (shape) {
            FrameSteelShape.I, FrameSteelShape.PlateGirder -> {
                moveTo(left, top)
                lineTo(right, top)
                lineTo(right, top + tf)
                lineTo(left + width / 2f + tw / 2f, top + tf)
                lineTo(left + width / 2f + tw / 2f, bottom - tf)
                lineTo(right, bottom - tf)
                lineTo(right, bottom)
                lineTo(left, bottom)
                lineTo(left, bottom - tf)
                lineTo(left + width / 2f - tw / 2f, bottom - tf)
                lineTo(left + width / 2f - tw / 2f, top + tf)
                lineTo(left, top + tf)
                close()
            }

            FrameSteelShape.Channel -> {
                moveTo(left, top)
                lineTo(right, top)
                lineTo(right, top + tf)
                lineTo(left + tw, top + tf)
                lineTo(left + tw, bottom - tf)
                lineTo(right, bottom - tf)
                lineTo(right, bottom)
                lineTo(left, bottom)
                close()
            }

            FrameSteelShape.Angle -> {
                moveTo(left, top)
                lineTo(left + t, top)
                lineTo(left + t, bottom - t)
                lineTo(right, bottom - t)
                lineTo(right, bottom)
                lineTo(left, bottom)
                close()
            }

            FrameSteelShape.HollowRect -> {
                fillType = PathFillType.EvenOdd
                addRect(Rect(left, top, right, bottom))
                addRect(Rect(left + tw, top + tf, right - tw, bottom - tf))
            }

            FrameSteelShape.HollowRound, FrameSteelShape.Pipe -> {
                fillType = PathFillType.EvenOdd
                addOval(Rect(left, top, right, bottom))
                addOval(Rect(left + t, top + t, right - t, bottom - t))
            }

            FrameSteelShape.Tee -> {
                moveTo(left, top)
                lineTo(right, top)
                lineTo(right, top + tf)
                lineTo(left + width / 2f + tw / 2f, top + tf)
                lineTo(left + width / 2f + tw / 2f, bottom)
                lineTo(left + width / 2f - tw / 2f, bottom)
                lineTo(left + width / 2f - tw / 2f, top + tf)
                lineTo(left, top + tf)
                close()
            }
        }
    }
}

private fun DrawScope.drawCrossSectionView(
    area: Rect,
    section: SteelTables.SectionProperties,
    shape: FrameSteelShape,
    textMeasurer: TextMeasurer
) {
    if (area.width < 8f || area.height < 8f) return
    val b = section.width.coerceAtLeast(1.0)
    val h = section.depth.coerceAtLeast(1.0)
    val scale = min(area.width * 0.58f / b, area.height * 0.60f / h)
    val w = (b * scale).toFloat()
    val hh = (h * scale).toFloat()
    val left = area.center.x - w / 2f
    val top = area.center.y - hh / 2f - area.height * 0.02f
    val right = left + w
    val bottom = top + hh
    val tw = max(1.2f, (section.tw * scale).toFloat())
    val tf = max(1.2f, (section.tf * scale).toFloat())
    val lineWidth = max(1f, area.width * 0.006f)
    val outline = Stroke(lineWidth)

    val path = buildFrameShapePath(shape, left, top, w, hh, tw, tf)
    drawPath(path, SteelSectionColors.Fill)
    drawPath(path, SteelSectionColors.Outline, style = outline)

    val dimStyle = TextStyle(color = SteelSectionColors.Dimension, fontSize = 7.5.sp)
    val bLabel = "b=${section.width.toInt()}"
    val bLayout = textMeasurer.measure(bLabel, dimStyle)
    drawText(
        textMeasurer = textMeasurer,
        text = bLabel,
        topLeft = Offset(area.center.x - bLayout.size.width / 2f, bottom + 2f),
        style = dimStyle
    )
    val hLabel = "h=${section.depth.toInt()}"
    val hLayout = textMeasurer.measure(hLabel, dimStyle)
    val hLeft = right + 4f
    val hTop = (area.center.y - hLayout.size.height / 2f - area.height * 0.02f)
        .coerceIn(area.top, area.bottom - hLayout.size.height)
    drawText(
        textMeasurer = textMeasurer,
        text = hLabel,
        topLeft = Offset(hLeft.coerceAtMost(area.right - hLayout.size.width), hTop),
        style = dimStyle
    )
}

private fun DrawScope.drawPlanView(
    area: Rect,
    section: SteelTables.SectionProperties,
    lengthMm: Double,
    isColumn: Boolean,
    shape: FrameSteelShape,
    textMeasurer: TextMeasurer
) {
    if (area.width < 8f || area.height < 8f) return
    val lineWidth = max(1f, area.width * 0.006f)
    val outline = Stroke(lineWidth)
    val dimStyle = TextStyle(color = SteelSectionColors.Dimension, fontSize = 7.5.sp)

    if (isColumn) {
        val b = section.width.coerceAtLeast(1.0)
        val h = section.depth.coerceAtLeast(1.0)
        val scale = min(area.width * 0.52f / b, area.height * 0.58f / h)
        val w = (b * scale).toFloat()
        val hh = (h * scale).toFloat()
        val left = area.center.x - w / 2f
        val top = area.center.y - hh / 2f - area.height * 0.02f
        val twPx = max(1.2f, (section.tw * scale).toFloat())
        val tfPx = max(1.2f, (section.tf * scale).toFloat())
        val path = buildFrameShapePath(shape, left, top, w, hh, twPx, tfPx)
        drawPath(path, SteelSectionColors.Fill)
        drawPath(path, SteelSectionColors.Outline, style = outline)
        drawLine(
            color = SteelSectionColors.Centerline,
            start = Offset(left - 4f, top + hh / 2f),
            end = Offset(left + w + 4f, top + hh / 2f),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )
        drawLine(
            color = SteelSectionColors.Centerline,
            start = Offset(left + w / 2f, top - 4f),
            end = Offset(left + w / 2f, top + hh + 4f),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )
        val label = if (shape == FrameSteelShape.HollowRound || shape == FrameSteelShape.Pipe) {
            "\u00D8${section.depth.toInt()}  t=${section.tw}"
        } else {
            "b=${section.width.toInt()}  h=${section.depth.toInt()}"
        }
        val layout = textMeasurer.measure(label, dimStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = label,
            topLeft = Offset(
                area.center.x - layout.size.width / 2f,
                (top + hh + 2f).coerceAtMost(area.bottom - layout.size.height)
            ),
            style = dimStyle
        )
    } else {
        val lengthPx = area.width * 0.88f
        val widthPx = (lengthPx * section.width / lengthMm).toFloat().coerceIn(4f, area.height * 0.45f)
        val cx = area.center.x
        val cy = area.center.y - area.height * 0.04f
        val left = cx - lengthPx / 2f
        val right = cx + lengthPx / 2f
        val top = cy - widthPx / 2f
        val bottom = cy + widthPx / 2f

        drawRect(
            color = SteelSectionColors.Fill,
            topLeft = Offset(left, top),
            size = Size(lengthPx, widthPx)
        )
        drawRect(
            color = SteelSectionColors.Outline,
            topLeft = Offset(left, top),
            size = Size(lengthPx, widthPx),
            style = outline
        )
        drawLine(
            color = SteelSectionColors.Centerline,
            start = Offset(left - 4f, cy),
            end = Offset(right + 4f, cy),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )
        val boltRadius = max(1.5f, widthPx * 0.07f)
        val boltX = listOf(left + lengthPx * 0.10f, left + lengthPx * 0.90f)
        val boltY = listOf(top + widthPx * 0.25f, top + widthPx * 0.75f)
        for (x in boltX) {
            for (y in boltY) {
                drawCircle(SteelSectionColors.Secondary, boltRadius, Offset(x, y))
            }
        }
        val label = "b=${section.width.toInt()}  L=${lengthMm.toInt()}"
        val layout = textMeasurer.measure(label, dimStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = label,
            topLeft = Offset(
                area.center.x - layout.size.width / 2f,
                (bottom + 2f).coerceAtMost(area.bottom - layout.size.height)
            ),
            style = dimStyle
        )
    }
}
