package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.domain.entities.*
import com.civileg.app.viewmodel.DiagramType
import kotlin.math.*

/**
 * لوحة الرسم التفاعلية للإطار والمخططات
 */
@Composable
fun FrameDrawingCanvas(
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    memberLoads: List<MemberLoad>,
    nodalLoads: List<NodalLoad>,
    result: FrameAnalysisResult?,
    diagramType: DiagramType,
    selectedMemberId: Int?,
    onMemberTap: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val showDiagrams = result?.hasResults == true
    val colorScheme = MaterialTheme.colorScheme

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasW = maxWidth
        val canvasH = maxHeight
        val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

        // Calculate scale to fit frame in canvas with padding
        val padding = 60.dp
        val drawW = canvasW - padding * 2
        val drawH = canvasH - padding * 2

        val xRange = if (nodes.isNotEmpty()) (nodes.minOf { it.x }..nodes.maxOf { it.x }) else 0.0..1.0
        val yRange = if (nodes.isNotEmpty()) (nodes.minOf { it.y }..nodes.maxOf { it.y }) else 0.0..1.0
        val xSpan = max(xRange.endInclusive - xRange.start, 1.0)
        val ySpan = max(yRange.endInclusive - xRange.start, 1.0)

        val scale = min(
            drawW.value / xSpan,
            drawH.value / ySpan
        ).toFloat() * 0.85f

        val offsetX = (padding.value + (drawW.value - xSpan * scale) / 2 - xRange.start * scale).toFloat()
        val offsetY = (padding.value + (drawH.value - ySpan * scale) / 2 + yRange.endInclusive * scale).toFloat()

        val toScreen: (Double, Double) -> Offset = { x, y ->
            Offset(
                (x * scale + offsetX).toFloat(),
                (-y * scale + offsetY).toFloat()
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedMemberId) {
                    detectTapGestures { tapOffset ->
                        if (onMemberTap != null) {
                            // Find closest member to tap
                            var closestMember: Int? = null
                            var minDist = 30f // pixels threshold
                            for (member in members) {
                                val ni = nodes.find { it.id == member.nodeI } ?: continue
                                val nj = nodes.find { it.id == member.nodeJ } ?: continue
                                val p1 = toScreen(ni.x, ni.y)
                                val p2 = toScreen(nj.x, nj.y)
                                val dist = pointToSegmentDist(tapOffset, p1, p2)
                                if (dist < minDist) {
                                    minDist = dist
                                    closestMember = member.id
                                }
                            }
                            closestMember?.let { onMemberTap(it) }
                        }
                    }
                }
        ) {
            if (nodes.isEmpty()) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = "أضف عقد وأعضاء لبدء التحليل",
                    topLeft = Offset(size.width / 2 - 120f, size.height / 2),
                    style = TextStyle(color = Color.Gray, fontSize = 16.sp)
                )
                return@Canvas
            }

            // === Draw Grid (light) ===
            drawGrid(toScreen, xRange, yRange, scale, offsetX, offsetY)

            // === Draw BMD/SFD/AFD Diagrams (behind members if active) ===
            if (showDiagrams) {
                drawDiagrams(
                    members, result?.memberDiagrams ?: emptyList(),
                    nodes, toScreen, scale, diagramType, textMeasurer
                )
            }

            // === Draw Members ===
            for (member in members) {
                val ni = nodes.find { it.id == member.nodeI } ?: continue
                val nj = nodes.find { it.id == member.nodeJ } ?: continue
                val p1 = toScreen(ni.x, ni.y)
                val p2 = toScreen(nj.x, nj.y)

                val isSelected = member.id == selectedMemberId
                val memberColor = when (member.materialType) {
                    FrameMaterialType.Concrete -> if (isSelected) Color(0xFF1565C0) else Color(0xFF42A5F5)
                    FrameMaterialType.Steel -> if (isSelected) Color(0xFFE65100) else Color(0xFFFF9800)
                }

                drawLine(
                    color = memberColor,
                    start = p1,
                    end = p2,
                    strokeWidth = if (isSelected) 5.dp.toPx() else 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Member label
                val midX = (p1.x + p2.x) / 2
                val midY = (p1.y + p2.y) / 2
                val angle = atan2(p2.y - p1.y, p2.x - p1.x)
                val labelOffset = 15.dp.toPx()
                val labelX = midX + cos(angle + PI / 2) * labelOffset
                val labelY = midY + sin(angle + PI / 2) * labelOffset

                if (member.name.isNotEmpty()) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = member.name,
                        topLeft = Offset((labelX - 20).toFloat(), (labelY - 8).toFloat()),
                        style = TextStyle(
                            color = if (isSelected) Color(0xFF1565C0) else Color.DarkGray,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }

                // Member ID
                drawText(
                    textMeasurer = textMeasurer,
                    text = "#${member.id}",
                    topLeft = Offset((labelX - 8).toFloat(), (labelY + 4).toFloat()),
                    style = TextStyle(color = Color.Gray, fontSize = 8.sp)
                )
            }

            // === Draw Member Loads (UDL arrows) ===
            for (mLoad in memberLoads) {
                val member = members.find { it.id == mLoad.memberId } ?: continue
                val ni = nodes.find { it.id == member.nodeI } ?: continue
                val nj = nodes.find { it.id == member.nodeJ } ?: continue
                drawMemberLoadArrows(ni, nj, mLoad, toScreen, scale, textMeasurer)
            }

            // === Draw Nodal Loads ===
            for (nLoad in nodalLoads) {
                val node = nodes.find { it.id == nLoad.nodeId } ?: continue
                val p = toScreen(node.x, node.y)
                drawNodalLoadArrow(p, nLoad, textMeasurer)
            }

            // === Draw Supports ===
            for (node in nodes) {
                if (node.support != SupportType.Free) {
                    val p = toScreen(node.x, node.y)
                    drawSupport(p, node.support)
                }
            }

            // === Draw Nodes ===
            for (node in nodes) {
                val p = toScreen(node.x, node.y)
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = p
                )
                drawCircle(
                    color = Color(0xFF333333),
                    radius = 6f,
                    center = p,
                    style = Stroke(width = 2f)
                )

                // Node ID label
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${node.id}",
                    topLeft = Offset(p.x - 4, p.y - 20),
                    style = TextStyle(color = Color(0xFF1565C0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
            }

            // === Draw Deformed Shape (if solved) ===
            if (showDiagrams) {
                drawDeformedShape(
                    members, nodes, result?.nodeResults ?: emptyList(),
                    toScreen, scale
                )
            }

            // === Scale Bar ===
            drawScaleBar(size, scale, textMeasurer)
        }
    }
}

// ============================================================================
// Drawing Helpers
// ============================================================================

private fun DrawScope.drawGrid(
    toScreen: (Double, Double) -> Offset,
    xRange: ClosedFloatingPointRange<Double>,
    yRange: ClosedFloatingPointRange<Double>,
    scale: Float,
    offsetX: Float,
    offsetY: Float
) {
    val gridSpacing = calculateGridSpacing(scale)
    val xMin = xRange.start - 1
    val xMax = xRange.endInclusive + 1
    val yMin = yRange.start - 1
    val yMax = yRange.endInclusive + 1

    val gridColor = Color(0xFFE0E0E0)
    val axisColor = Color(0xFFBDBDBD)

    // Vertical grid lines
    var x = floor(xMin / gridSpacing) * gridSpacing
    while (x <= xMax) {
        val p1 = toScreen(x, yMin)
        val p2 = toScreen(x, yMax)
        drawLine(
            color = if (abs(x) < 0.01) axisColor else gridColor,
            start = p1, end = p2,
            strokeWidth = if (abs(x) < 0.01) 1.5f else 0.5f
        )
        x += gridSpacing
    }

    // Horizontal grid lines
    var y = floor(yMin / gridSpacing) * gridSpacing
    while (y <= yMax) {
        val p1 = toScreen(xMin, y)
        val p2 = toScreen(xMax, y)
        drawLine(
            color = if (abs(y) < 0.01) axisColor else gridColor,
            start = p1, end = p2,
            strokeWidth = if (abs(y) < 0.01) 1.5f else 0.5f
        )
        y += gridSpacing
    }
}

private fun calculateGridSpacing(scale: Float): Double {
    val targetPixels = 60f
    val rawSpacing = (targetPixels / scale).toDouble()
    val magnitude = 10.0.pow(floor(log10(rawSpacing)))
    return when {
        rawSpacing / magnitude < 2.0 -> 2 * magnitude
        rawSpacing / magnitude < 5.0 -> 5 * magnitude
        else -> 10 * magnitude
    }
}

private fun DrawScope.drawSupport(p: Offset, supportType: SupportType) {
    val size = 18f
    when (supportType) {
        SupportType.Pin -> {
            // Triangle
            val path = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x - size, p.y + size * 1.2f)
                lineTo(p.x + size, p.y + size * 1.2f)
                close()
            }
            drawPath(path, color = Color(0xFF1565C0), style = Stroke(width = 2f))
            // Ground line
            drawLine(
                color = Color(0xFF1565C0),
                start = Offset(p.x - size - 5, p.y + size * 1.2f),
                end = Offset(p.x + size + 5, p.y + size * 1.2f),
                strokeWidth = 2f
            )
            // Hatching
            for (i in -2..2) {
                val hx = p.x + i * 8f
                drawLine(
                    color = Color(0xFF1565C0),
                    start = Offset(hx, p.y + size * 1.2f),
                    end = Offset(hx - 5, p.y + size * 1.2f + 6),
                    strokeWidth = 1f
                )
            }
        }
        SupportType.Roller -> {
            // Triangle (smaller)
            val path = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x - size * 0.7f, p.y + size * 0.8f)
                lineTo(p.x + size * 0.7f, p.y + size * 0.8f)
                close()
            }
            drawPath(path, color = Color(0xFF1565C0), style = Stroke(width = 2f))
            // Circle (roller)
            drawCircle(color = Color(0xFF1565C0), radius = 4f, center = Offset(p.x, p.y + size * 0.8f + 5f), style = Stroke(2f))
            // Ground line
            drawLine(
                color = Color(0xFF1565C0),
                start = Offset(p.x - size, p.y + size * 0.8f + 12f),
                end = Offset(p.x + size, p.y + size * 0.8f + 12f),
                strokeWidth = 2f
            )
        }
        SupportType.Fixed -> {
            // Hatched wall at the support
            drawLine(
                color = Color(0xFF1565C0),
                start = Offset(p.x - 14, p.y + 14),
                end = Offset(p.x + 14, p.y - 14),
                strokeWidth = 3f
            )
            for (i in -2..2) {
                val hx = p.x - 14 + i * 7f
                drawLine(
                    color = Color(0xFF1565C0),
                    start = Offset(hx, p.y + 14 + (i - 2) * 3f),
                    end = Offset(hx - 8, p.y + 6 + (i - 2) * 3f),
                    strokeWidth = 1.5f
                )
            }
        }
        SupportType.VerticalRoller -> {
            // Similar to pin but rotated
            val path = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x + size * 1.2f, p.y - size * 0.7f)
                lineTo(p.x + size * 1.2f, p.y + size * 0.7f)
                close()
            }
            drawPath(path, color = Color(0xFF1565C0), style = Stroke(width = 2f))
            drawCircle(color = Color(0xFF1565C0), radius = 4f, center = Offset(p.x + size * 1.2f + 5f, p.y), style = Stroke(2f))
        }
        SupportType.Free -> { /* nothing */ }
    }
}

private fun DrawScope.drawMemberLoadArrows(
    ni: FrameNode, nj: FrameNode,
    mLoad: MemberLoad,
    toScreen: (Double, Double) -> Offset,
    scale: Float,
    textMeasurer: TextMeasurer
) {
    if (mLoad.loadType == MemberLoadType.UDL) {
        val numArrows = 8
        val p1 = toScreen(ni.x, ni.y)
        val p2 = toScreen(nj.x, nj.y)
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val L = sqrt(dx * dx + dy * dy)
        if (L < 1f) return
        val nx = -dy / L  // normal to member (perpendicular, pointing "down" in screen)
        val ny = dx / L

        val arrowLen = 25f
        val arrowColor = Color(0xFFD32F2F)

        for (i in 1..numArrows) {
            val t = i.toFloat() / (numArrows + 1)
            val baseX = p1.x + dx * t
            val baseY = p1.y + dy * t
            val tipX = baseX + nx * arrowLen
            val tipY = baseY + ny * arrowLen

            drawLine(color = arrowColor, start = Offset(baseX, baseY), end = Offset(tipX, tipY), strokeWidth = 1.5f)
            // Arrow head
            val headSize = 4f
            val angle = atan2(ny, nx)
            drawLine(color = arrowColor, start = Offset(tipX, tipY), end = Offset(tipX - headSize * cos(angle - 0.5f), tipY - headSize * sin(angle - 0.5f)), strokeWidth = 1.5f)
            drawLine(color = arrowColor, start = Offset(tipX, tipY), end = Offset(tipX - headSize * cos(angle + 0.5f), tipY - headSize * sin(angle + 0.5f)), strokeWidth = 1.5f)
        }

        // Load value label
        val midX = (p1.x + p2.x) / 2 + nx * (arrowLen + 8f)
        val midY = (p1.y + p2.y) / 2 + ny * (arrowLen + 8f)
        drawText(
            textMeasurer = textMeasurer,
            text = "${mLoad.value} kN/m",
            topLeft = Offset(midX - 20, midY - 6),
            style = TextStyle(color = Color(0xFFD32F2F), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        )
    } else if (mLoad.loadType == MemberLoadType.PointLoad) {
        val p1 = toScreen(ni.x, ni.y)
        val p2 = toScreen(nj.x, nj.y)
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val L = sqrt(dx * dx + dy * dy)
        if (L < 1f) return
        val memberLength = sqrt((nj.x - ni.x).pow(2) + (nj.y - ni.y).pow(2))
        val t = if (memberLength > 0) (mLoad.position / memberLength).toFloat() else 0.5f
        val baseX = p1.x + dx * t
        val baseY = p1.y + dy * t
        val nx = -dy / L
        val ny = dx / L
        val arrowLen = 35f

        val tipX = baseX + nx * arrowLen
        val tipY = baseY + ny * arrowLen

        drawLine(color = Color(0xFFD32F2F), start = Offset(baseX, baseY), end = Offset(tipX, tipY), strokeWidth = 2f)
        val angle = atan2(ny, nx)
        drawLine(color = Color(0xFFD32F2F), start = Offset(tipX, tipY), end = Offset(tipX - 6f * cos(angle - 0.5f), tipY - 6f * sin(angle - 0.5f)), strokeWidth = 2f)
        drawLine(color = Color(0xFFD32F2F), start = Offset(tipX, tipY), end = Offset(tipX - 6f * cos(angle + 0.5f), tipY - 6f * sin(angle + 0.5f)), strokeWidth = 2f)

        drawText(
            textMeasurer = textMeasurer,
            text = "${mLoad.value} kN",
            topLeft = Offset(tipX - 12, tipY + 4),
            style = TextStyle(color = Color(0xFFD32F2F), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        )
    }
}

private fun DrawScope.drawNodalLoadArrow(p: Offset, load: NodalLoad, textMeasurer: TextMeasurer) {
    if (abs(load.fx) < 0.01 && abs(load.fy) < 0.01 && abs(load.mz) < 0.01) return

    val arrowColor = Color(0xFF4CAF50)
    val arrowLen = 35f

    // Draw force arrow (fy is positive upward, screen y is inverted)
    if (abs(load.fy) > 0.01) {
        val dir = if (load.fy < 0) 1f else -1f // positive fy = upward force, but loads are usually downward
        val tipY = p.y + dir * arrowLen
        drawLine(color = arrowColor, start = p, end = Offset(p.x, tipY), strokeWidth = 2f)
        val headDir = if (load.fy < 0) -1f else 1f
        drawLine(color = arrowColor, start = Offset(p.x, tipY), end = Offset(p.x - 5f, tipY + headDir * 6f), strokeWidth = 2f)
        drawLine(color = arrowColor, start = Offset(p.x, tipY), end = Offset(p.x + 5f, tipY + headDir * 6f), strokeWidth = 2f)
        drawText(
            textMeasurer = textMeasurer,
            text = "${abs(load.fy)} kN",
            topLeft = Offset(p.x + 6, tipY - 4),
            style = TextStyle(color = arrowColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        )
    }
    if (abs(load.fx) > 0.01) {
        val dir = if (load.fx > 0) 1f else -1f
        val tipX = p.x + dir * arrowLen
        drawLine(color = arrowColor, start = p, end = Offset(tipX, p.y), strokeWidth = 2f)
        val headDir = if (load.fx > 0) -1f else 1f
        drawLine(color = arrowColor, start = Offset(tipX, p.y), end = Offset(tipX + headDir * 6f, p.y - 5f), strokeWidth = 2f)
        drawLine(color = arrowColor, start = Offset(tipX, p.y), end = Offset(tipX + headDir * 6f, p.y + 5f), strokeWidth = 2f)
    }
    if (abs(load.mz) > 0.01) {
        drawText(
            textMeasurer = textMeasurer,
            text = "M=${load.mz} kN.m",
            topLeft = Offset(p.x + 10, p.y - 20),
            style = TextStyle(color = Color(0xFF9C27B0), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        )
    }
}

private fun DrawScope.drawDiagrams(
    members: List<FrameMember>,
    diagrams: List<MemberDiagram>,
    nodes: List<FrameNode>,
    toScreen: (Double, Double) -> Offset,
    scale: Float,
    diagramType: DiagramType,
    textMeasurer: TextMeasurer
) {
    for (diagram in diagrams) {
        val member = members.find { it.id == diagram.memberId } ?: continue
        val ni = nodes.find { it.id == member.nodeI } ?: continue
        val nj = nodes.find { it.id == member.nodeJ } ?: continue
        val p1 = toScreen(ni.x, ni.y)
        val p2 = toScreen(nj.x, nj.y)
        val L = sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2))
        if (L < 1f) continue

        // Direction along member
        val dx = (p2.x - p1.x) / L
        val dy = (p2.y - p1.y) / L
        // Normal to member (perpendicular)
        val nx = -dy
        val ny = dx

        val points = when (diagramType) {
            DiagramType.BMD -> diagram.momentDiagram
            DiagramType.SFD -> diagram.shearDiagram
            DiagramType.AFD -> diagram.axialDiagram
        }

        if (points.size < 2) continue

        // Scale factor for diagram (adjust based on max value)
        val maxVal = points.maxOfOrNull { abs(it.value) } ?: 1.0
        if (maxVal < 0.001) continue
        val diagramScale = 40f / maxVal.toFloat() // Max diagram height = 40px

        val diagramColor = when (diagramType) {
            DiagramType.BMD -> Color(0xFF2196F3)  // Blue
            DiagramType.SFD -> Color(0xFF4CAF50)  // Green
            DiagramType.AFD -> Color(0xFFFF9800)  // Orange
        }

        // Draw filled diagram area
        val path = Path()
        val memberLength = member.getLength(nodes)
        val firstPt = points[0]
        val t0 = if (memberLength > 0) (firstPt.x / memberLength).toFloat() else 0f
        val offset0 = firstPt.value * diagramScale
        path.moveTo(p1.x + dx * L * t0 + nx * offset0.toFloat(), p1.y + dy * L * t0 + ny * offset0.toFloat())

        for (i in 1 until points.size) {
            val pt = points[i]
            val t = if (memberLength > 0) (pt.x / memberLength).toFloat() else (i.toFloat() / (points.size - 1))
            val offset = pt.value * diagramScale
            path.lineTo(p1.x + dx * L * t + nx * offset.toFloat(), p1.y + dy * L * t + ny * offset.toFloat())
        }

        // Close the path back along the member line
        path.lineTo(p2.x, p2.y)
        path.lineTo(p1.x, p1.y)
        path.close()

        // Fill with semi-transparent color
        drawPath(
            path = path,
            color = diagramColor.copy(alpha = 0.2f)
        )

        // Draw the diagram outline
        val outlinePath = Path()
        val firstPt2 = points[0]
        val t0_2 = if (memberLength > 0) (firstPt2.x / memberLength).toFloat() else 0f
        val offset0_2 = firstPt2.value * diagramScale
        outlinePath.moveTo(p1.x + dx * L * t0_2 + nx * offset0_2.toFloat(), p1.y + dy * L * t0_2 + ny * offset0_2.toFloat())

        for (i in 1 until points.size) {
            val pt = points[i]
            val t = if (memberLength > 0) (pt.x / memberLength).toFloat() else (i.toFloat() / (points.size - 1))
            val offset = pt.value * diagramScale
            outlinePath.lineTo(p1.x + dx * L * t + nx * offset.toFloat(), p1.y + dy * L * t + ny * offset.toFloat())
        }
        drawPath(outlinePath, color = diagramColor, style = Stroke(width = 2f))

        // Draw max value annotation
        val maxPt = points.maxByOrNull { abs(it.value) }
        if (maxPt != null && abs(maxPt.value) > 0.01) {
            val tMax = if (memberLength > 0) (maxPt.x / memberLength).toFloat() else 0.5f
            val offsetMax = maxPt.value * diagramScale
            val annX = p1.x + dx * L * tMax + nx * offsetMax.toFloat()
            val annY = p1.y + dy * L * tMax + ny * offsetMax.toFloat()
            val unit = when (diagramType) {
                DiagramType.BMD -> "kN.m"
                DiagramType.SFD, DiagramType.AFD -> "kN"
            }
            val label = "${abs(maxPt.value).formatValue(2)} $unit"
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(annX + 4, annY - 14),
                style = TextStyle(color = diagramColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

private fun DrawScope.drawDeformedShape(
    members: List<FrameMember>,
    nodes: List<FrameNode>,
    nodeResults: List<NodeResult>,
    toScreen: (Double, Double) -> Offset,
    scale: Float
) {
    // Scale deformations for visibility
    val maxDisp = nodeResults.maxOfOrNull { (max(abs(it.dx), abs(it.dy)) * scale).toFloat() } ?: 0f
    if (maxDisp < 0.5f) return // Too small to show
    val deformScale = 50f / maxDisp.coerceAtLeast(1f) // amplify to ~50px max

    for (member in members) {
        val ni = nodes.find { it.id == member.nodeI } ?: continue
        val nj = nodes.find { it.id == member.nodeJ } ?: continue
        val ri = nodeResults.find { it.nodeId == member.nodeI }
        val rj = nodeResults.find { it.nodeId == member.nodeJ } ?: continue

        val p1 = toScreen(ni.x + (ri?.dx ?: 0.0) * deformScale / scale,
                          ni.y + (ri?.dy ?: 0.0) * deformScale / scale)
        val p2 = toScreen(nj.x + (rj?.dx ?: 0.0) * deformScale / scale,
                          nj.y + (rj?.dy ?: 0.0) * deformScale / scale)

        drawLine(
            color = Color(0xFFFF5722).copy(alpha = 0.6f),
            start = p1, end = p2,
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
        )
    }
}

private fun DrawScope.drawScaleBar(size: Size, scale: Float, textMeasurer: TextMeasurer) {
    val barLength_m = 1.0 // 1 meter
    val barLength_px = (barLength_m * scale).toFloat()
    val x = 20f
    val y = size.height - 25f

    drawLine(color = Color.DarkGray, start = Offset(x, y), end = Offset(x + barLength_px, y), strokeWidth = 2f)
    drawLine(color = Color.DarkGray, start = Offset(x, y - 5), end = Offset(x, y + 5), strokeWidth = 2f)
    drawLine(color = Color.DarkGray, start = Offset(x + barLength_px, y - 5), end = Offset(x + barLength_px, y + 5), strokeWidth = 2f)
    drawText(
        textMeasurer = textMeasurer,
        text = "${barLength_m.toInt()} m",
        topLeft = Offset(x + barLength_px / 2 - 10, y + 6),
        style = TextStyle(color = Color.DarkGray, fontSize = 10.sp)
    )
}

// ============================================================================
// Utility Functions
// ============================================================================

private fun pointToSegmentDist(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lenSq = dx * dx + dy * dy
    if (lenSq < 1f) return (p - a).getDistance()
    var t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq
    t = t.coerceIn(0f, 1f)
    val proj = Offset(a.x + t * dx, a.y + t * dy)
    return (p - proj).getDistance()
}

private fun Offset.getDistance(): Float = sqrt(x * x + y * y)

private fun Double.formatValue(decimals: Int): String {
    return if (abs(this) < 0.001) "0"
    else if (abs(this) >= 1000) "%.0f".format(this)
    else "%.${decimals}f".format(this)
}