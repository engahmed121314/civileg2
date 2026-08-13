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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.domain.entities.*
import com.civileg.app.viewmodel.DiagramType
import kotlin.math.*

/**
 * لوحة الرسم التفاعلية للإطار والمخططات
 *
 * Supports multiple view modes:
 * 0: Frame (default 2D structural view with BMD/SFD/AFD overlays)
 * 1: Longitudinal Section (قطاع طولي) — elevation showing all frame members along span
 * 2: Cross Section (قطاع عرضي) — typical column/beam section with reinforcement
 * 3: Plan View (مسقط أفقي) — top-down view showing column grid and beam layout
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
    viewMode: Int = 0,
    modifier: Modifier = Modifier
) {
    val showDiagrams = result?.hasResults == true && viewMode == 0
    val colorScheme = MaterialTheme.colorScheme

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val canvasW = with(density) { maxWidth.toPx() }
        val canvasH = with(density) { maxHeight.toPx() }
        val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

        // Calculate scale to fit frame in canvas with padding (all in pixels)
        // CRITICAL FIX: Reduce padding to use more screen space (was 16.dp, now 8.dp)
        val paddingPx = with(density) { 8.dp.toPx() }
        val drawW = canvasW - paddingPx * 2
        val drawH = canvasH - paddingPx * 2

        val xRange = if (nodes.isNotEmpty()) (nodes.minOf { it.x }..nodes.maxOf { it.x }) else 0.0..1.0
        val yRange = if (nodes.isNotEmpty()) (nodes.minOf { it.y }..nodes.maxOf { it.y }) else 0.0..1.0
        val xSpan = max(xRange.endInclusive - xRange.start, 1.0)
        val ySpan = max(yRange.endInclusive - yRange.start, 1.0)

        // CRITICAL FIX: Use 0.85 multiplier to use more of the canvas (was 1.0)
        val scale = (min(drawW / xSpan, drawH / ySpan) * 0.92f).toFloat()

        val offsetX = (paddingPx + (drawW - xSpan * scale) / 2 - xRange.start * scale).toFloat()
        val offsetY = (paddingPx + (drawH - ySpan * scale) / 2 + yRange.endInclusive * scale).toFloat()

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
                            var closestMember: Int? = null
                            var minDist = 30f
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
            when (viewMode) {
                0 -> drawFrameView(
                    nodes, members, memberLoads, nodalLoads, result, diagramType,
                    selectedMemberId, showDiagrams, toScreen, scale, offsetX, offsetY,
                    xRange, yRange, textMeasurer, colorScheme
                )
                1 -> drawLongitudinalSection(
                    nodes, members, result, scale, canvasW, canvasH, textMeasurer
                )
                2 -> drawCrossSection(
                    nodes, members, result, selectedMemberId, scale, canvasW, canvasH, textMeasurer
                )
                3 -> drawPlanView(
                    nodes, members, scale, canvasW, canvasH, textMeasurer
                )
            }
        }
    }
}

// ============================================================================
// View 0: Frame (Default 2D structural view with diagrams)
// ============================================================================
private fun DrawScope.drawFrameView(
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    memberLoads: List<MemberLoad>,
    nodalLoads: List<NodalLoad>,
    result: FrameAnalysisResult?,
    diagramType: DiagramType,
    selectedMemberId: Int?,
    showDiagrams: Boolean,
    toScreen: (Double, Double) -> Offset,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    xRange: ClosedFloatingPointRange<Double>,
    yRange: ClosedFloatingPointRange<Double>,
    textMeasurer: TextMeasurer,
    colorScheme: androidx.compose.material3.ColorScheme
) {
    if (nodes.isEmpty()) {
        drawText(
            textMeasurer = textMeasurer,
            text = "أضف عقد وأعضاء لبدء التحليل",
            topLeft = Offset(size.width / 2 - 120f, size.height / 2),
            style = TextStyle(color = Color.Gray, fontSize = 16.sp)
        )
        return
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
            strokeWidth = if (isSelected) 7.dp.toPx() else 5.dp.toPx(),
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
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            )
        }

        drawText(
            textMeasurer = textMeasurer,
            text = "#${member.id}",
            topLeft = Offset((labelX - 8).toFloat(), (labelY + 4).toFloat()),
            style = TextStyle(color = Color.Gray, fontSize = 9.sp)
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
        drawCircle(color = Color.White, radius = 10f, center = p)
        drawCircle(color = Color(0xFF333333), radius = 10f, center = p, style = Stroke(width = 2.5f))

        drawText(
            textMeasurer = textMeasurer,
            text = "${node.id}",
            topLeft = Offset(p.x - 4, p.y - 22),
            style = TextStyle(color = Color(0xFF1565C0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

// ============================================================================
// View 1: Longitudinal Section (قطاع طولي)
// Shows the elevation view of the frame along its main span
// ============================================================================
private fun DrawScope.drawLongitudinalSection(
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    result: FrameAnalysisResult?,
    scale: Float,
    canvasW: Float,
    canvasH: Float,
    textMeasurer: TextMeasurer
) {
    if (nodes.isEmpty()) {
        drawText(
            textMeasurer = textMeasurer,
            text = "Longitudinal Section — No data",
            topLeft = Offset(canvasW / 2 - 100f, canvasH / 2),
            style = TextStyle(color = Color.Gray, fontSize = 14.sp)
        )
        return
    }

    // Title
    drawText(
        textMeasurer = textMeasurer,
        text = "LONGITUDINAL SECTION (قطاع طولي)",
        topLeft = Offset(canvasW / 2 - 130f, 20f),
        style = TextStyle(color = Color(0xFF1565C0), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    )

    // Get X range (span)
    val xMin = nodes.minOf { it.x }
    val xMax = nodes.maxOf { it.x }
    val xSpan = max(xMax - xMin, 1.0)

    // Get Y range (height)
    val yMin = nodes.minOf { it.y }
    val yMax = nodes.maxOf { it.y }
    val ySpan = max(yMax - yMin, 1.0)

    // Drawing area
    val padLeft = 80f
    val padRight = 60f
    val padTop = 80f
    val padBottom = 80f
    val drawW = canvasW - padLeft - padRight
    val drawH = canvasH - padTop - padBottom

    val sx = drawW / xSpan.toFloat()
    val sy = drawH / ySpan.toFloat()
    val s = min(sx, sy) * 0.9f

    val originX = padLeft + (drawW - xSpan.toFloat() * s) / 2f
    val originY = padTop + ySpan.toFloat() * s + (drawH - ySpan.toFloat() * s) / 2f

    val toScreen: (Double, Double) -> Offset = { x, y ->
        Offset(
            ((x - xMin).toFloat() * s + originX),
            (-(y - yMin).toFloat() * s + originY)
        )
    }

    // Ground line with hatching
    val groundY = originY
    drawLine(Color(0xFF555555), Offset(originX - 20f, groundY), Offset(originX + xSpan.toFloat() * s + 20f, groundY), strokeWidth = 3f)
    for (i in 0..30) {
        val x = originX - 20f + i * (xSpan.toFloat() * s + 40f) / 30
        drawLine(Color(0xFF555555), Offset(x, groundY), Offset(x - 8f, groundY + 10f), strokeWidth = 1f)
    }

    // Draw members with thick lines (sections)
    for (member in members) {
        val ni = nodes.find { it.id == member.nodeI } ?: continue
        val nj = nodes.find { it.id == member.nodeJ } ?: continue
        val p1 = toScreen(ni.x, ni.y)
        val p2 = toScreen(nj.x, nj.y)

        val memberColor = when (member.materialType) {
            FrameMaterialType.Concrete -> Color(0xFF42A5F5)
            FrameMaterialType.Steel -> Color(0xFFFF9800)
        }

        // Outer thick line (member outline)
        drawLine(memberColor.copy(alpha = 0.3f), p1, p2, strokeWidth = 14f)
        // Inner main line
        drawLine(memberColor, p1, p2, strokeWidth = 6f, cap = StrokeCap.Round)
    }

    // Draw supports (with proper foundation symbols)
    for (node in nodes) {
        if (node.support != SupportType.Free) {
            val p = toScreen(node.x, node.y)
            drawSupportLarge(p, node.support, textMeasurer, node.id)
        }
    }

    // Draw nodes as larger circles for section view
    for (node in nodes) {
        val p = toScreen(node.x, node.y)
        drawCircle(Color.White, 8f, p)
        drawCircle(Color(0xFF333333), 8f, p, style = Stroke(width = 2f))
    }

    // === Dimensions ===
    val dimColor = Color(0xFF8E24AA)
    val dimTextPaint = android.graphics.Paint().apply {
        color = dimColor.toArgb()
        textSize = 24f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    // Horizontal dimension (span)
    val dimY = originY + 40f
    val leftX = toScreen(xMin, 0.0).x
    val rightX = toScreen(xMax, 0.0).x
    drawLine(dimColor, Offset(leftX, groundY + 8f), Offset(leftX, dimY + 5f), strokeWidth = 1f)
    drawLine(dimColor, Offset(rightX, groundY + 8f), Offset(rightX, dimY + 5f), strokeWidth = 1f)
    drawLine(dimColor, Offset(leftX, dimY), Offset(rightX, dimY), strokeWidth = 1.5f)
    drawContext.canvas.nativeCanvas.drawText(
        "Span = ${String.format("%.2f", xSpan)} m",
        (leftX + rightX) / 2, dimY - 8f, dimTextPaint
    )

    // Vertical dimension (height)
    val dimX = originX - 50f
    val bottomY = toScreen(xMin, yMin).y
    val topY = toScreen(xMin, yMax).y
    drawLine(dimColor, Offset(originX - 8f, bottomY), Offset(dimX - 5f, bottomY), strokeWidth = 1f)
    drawLine(dimColor, Offset(originX - 8f, topY), Offset(dimX - 5f, topY), strokeWidth = 1f)
    drawLine(dimColor, Offset(dimX, bottomY), Offset(dimX, topY), strokeWidth = 1.5f)
    drawContext.canvas.nativeCanvas.save()
    drawContext.canvas.nativeCanvas.rotate(-90f, dimX - 12f, (bottomY + topY) / 2)
    drawContext.canvas.nativeCanvas.drawText(
        "Height = ${String.format("%.2f", ySpan)} m",
        dimX - 12f, (bottomY + topY) / 2 + 8f, dimTextPaint
    )
    drawContext.canvas.nativeCanvas.restore()

    // Result info (if available)
    result?.let { res ->
        val infoX = canvasW - 280f
        val infoY = 60f
        drawRect(Color(0x22000000), Offset(infoX, infoY), Size(260f, 80f))
        drawRect(Color(0xFF4A90D9), Offset(infoX, infoY), Size(260f, 80f), style = Stroke(1.5f))
        val infoPaint = android.graphics.Paint().apply {
            color = Color.White.toArgb(); textSize = 18f; isFakeBoldText = true
        }
        val infoPaintSmall = android.graphics.Paint().apply {
            color = Color.White.toArgb(); textSize = 16f
        }
        drawContext.canvas.nativeCanvas.drawText("Analysis Results", infoX + 10f, infoY + 22f, infoPaint)
        val maxM = res.memberDiagrams.maxOfOrNull { md -> md.momentDiagram.maxOfOrNull { it.value } ?: 0.0 } ?: 0.0
        val maxV = res.memberDiagrams.maxOfOrNull { md -> md.shearDiagram.maxOfOrNull { it.value } ?: 0.0 } ?: 0.0
        drawContext.canvas.nativeCanvas.drawText("Max Moment: ${String.format("%.1f", maxM)} kN.m", infoX + 10f, infoY + 45f, infoPaintSmall)
        drawContext.canvas.nativeCanvas.drawText("Max Shear: ${String.format("%.1f", maxV)} kN", infoX + 10f, infoY + 65f, infoPaintSmall)
    }
}

// ============================================================================
// View 2: Cross Section (قطاع عرضي)
// Shows typical column and beam sections with reinforcement
// ============================================================================
private fun DrawScope.drawCrossSection(
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    result: FrameAnalysisResult?,
    selectedMemberId: Int?,
    scale: Float,
    canvasW: Float,
    canvasH: Float,
    textMeasurer: TextMeasurer
) {
    // Title
    drawText(
        textMeasurer = textMeasurer,
        text = "CROSS SECTION (قطاع عرضي)",
        topLeft = Offset(canvasW / 2 - 110f, 20f),
        style = TextStyle(color = Color(0xFF1565C0), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    )

    if (members.isEmpty()) {
        drawText(
            textMeasurer = textMeasurer,
            text = "No members to display",
            topLeft = Offset(canvasW / 2 - 80f, canvasH / 2),
            style = TextStyle(color = Color.Gray, fontSize = 14.sp)
        )
        return
    }

    // FIX: Use memberType enum instead of name heuristic — robust for all templates
    // (previously the name contains("كمرة") failed for template beam name "كمر سقف")
    val columnMembers = members.filter { it.memberType == FrameMemberType.Column }
    val beamMembers = members.filter { it.memberType == FrameMemberType.Beam }

    // Fallback: if memberType not set, classify by orientation (vertical = column, horizontal = beam)
    val (colMs, bmMs) = if (columnMembers.isEmpty() && beamMembers.isEmpty()) {
        val byOrientation = members.partition { m ->
            val ni = nodes.find { it.id == m.nodeI }
            val nj = nodes.find { it.id == m.nodeJ }
            if (ni != null && nj != null) {
                // vertical if Y difference is much larger than X difference
                abs(nj.y - ni.y) > abs(nj.x - ni.x) * 1.5
            } else false
        }
        byOrientation
    } else {
        Pair(columnMembers, beamMembers)
    }

    // Draw column section on left
    val colCenterX = canvasW * 0.25f
    val colCenterY = canvasH * 0.5f
    drawColumnSection(colCenterX, colCenterY, colMs.firstOrNull(), result, textMeasurer)

    // Draw beam section on right
    val beamCenterX = canvasW * 0.75f
    val beamCenterY = canvasH * 0.5f
    drawBeamSection(beamCenterX, beamCenterY, bmMs.firstOrNull(), result, textMeasurer)
}

// Draw column cross section with reinforcement — uses ACTUAL member section & design result
private fun DrawScope.drawColumnSection(
    cx: Float, cy: Float,
    member: FrameMember?,
    result: FrameAnalysisResult?,
    textMeasurer: TextMeasurer
) {
    // Pull actual section dimensions from member.concreteSection (fallback to 300x300 if missing)
    val cs = member?.concreteSection
    val colW = cs?.width?.toInt() ?: 300
    val colD = cs?.depth?.toInt() ?: 300
    val cover = cs?.cover?.toInt() ?: 40

    // Find the design result for this member — provides actual bars/diameters/stirrups
    val designRes = result?.concreteDesignResults?.firstOrNull { it.memberId == member?.id }
    val numBarsBot = designRes?.numBarsBot?.takeIf { it > 0 } ?: 4
    val numBarsTop = designRes?.numBarsTop?.takeIf { it > 0 } ?: 2
    val numBars = numBarsBot + numBarsTop
    val barDia = designRes?.barDia?.takeIf { it > 0 }?.toInt() ?: 16
    val tieDia = designRes?.stirrupDia?.takeIf { it > 0 }?.toInt() ?: 8
    val stirrupSpacing = designRes?.stirrupSpacing?.takeIf { it > 0 }?.toInt() ?: 150

    // Section drawing — scale based on largest dimension so wide/tall columns fit
    val maxMm = maxOf(colW, colD)
    val sectionSize = 220f
    val drawW = if (colW >= colD) sectionSize else (colW.toFloat() / colD.toFloat() * sectionSize)
    val drawH = if (colD >= colW) sectionSize else (colD.toFloat() / colW.toFloat() * sectionSize)
    val left = cx - drawW / 2
    val top = cy - drawH / 2

    // Title
    drawText(
        textMeasurer = textMeasurer,
        text = "COLUMN SECTION — ${member?.name?.ifEmpty { "Column" } ?: "Column"}",
        topLeft = Offset(cx - 100f, top - 30f),
        style = TextStyle(color = Color(0xFF1565C0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    )

    // Concrete outline
    drawRect(Color(0xFFE0E0E0), Offset(left, top), Size(drawW, drawH))
    drawRect(Color(0xFF555555), Offset(left, top), Size(drawW, drawH), style = Stroke(width = 2.5f))

    // Cover boundary (dashed) — proportional to actual cover
    val coverInset = (cover.toFloat() / maxMm.toFloat()) * sectionSize * 0.6f
    drawRect(
        Color(0xFF888888),
        Offset(left + coverInset, top + coverInset),
        Size(drawW - 2 * coverInset, drawH - 2 * coverInset),
        style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
    )

    // Stirrup (tie) - inside cover
    val tieInset = coverInset + 4f
    drawRect(
        Color(0xFF4CAF50),
        Offset(left + tieInset, top + tieInset),
        Size(drawW - 2 * tieInset, drawH - 2 * tieInset),
        style = Stroke(width = 2f)
    )

    // Longitudinal bars: distribute actual count around perimeter (corners + evenly along faces)
    val barR = (barDia.toFloat() / maxMm.toFloat() * sectionSize * 0.5f).coerceIn(3.5f, 8f)
    val barPositions = mutableListOf<Offset>()
    // 4 corner bars first
    barPositions.add(Offset(left + coverInset + 8f, top + coverInset + 8f))
    barPositions.add(Offset(left + drawW - coverInset - 8f, top + coverInset + 8f))
    barPositions.add(Offset(left + coverInset + 8f, top + drawH - coverInset - 8f))
    barPositions.add(Offset(left + drawW - coverInset - 8f, top + drawH - coverInset - 8f))
    // Distribute remaining bars (if any) on the longer faces
    val remaining = (numBars - 4).coerceAtLeast(0)
    if (remaining > 0) {
        val alongW = maxOf(1, remaining / 2)
        val alongH = remaining - alongW
        for (i in 1..alongW) {
            val x = left + drawW * (i.toFloat() / (alongW + 1))
            barPositions.add(Offset(x, top + coverInset + 8f))
            barPositions.add(Offset(x, top + drawH - coverInset - 8f))
        }
        for (i in 1..alongH) {
            val y = top + drawH * (i.toFloat() / (alongH + 1))
            barPositions.add(Offset(left + coverInset + 8f, y))
            barPositions.add(Offset(left + drawW - coverInset - 8f, y))
        }
    }
    barPositions.take(numBars).forEach { p ->
        drawCircle(Color(0xFF1565C0), barR + 2f, p)
        drawCircle(Color(0xFF42A5F5), barR, p)
    }

    // Dimensions
    val dimColor = Color(0xFF8E24AA)
    val dimPaint = android.graphics.Paint().apply {
        color = dimColor.toArgb(); textSize = 18f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER
    }
    val dimYTop = top - 20f
    drawLine(dimColor, Offset(left, top - 5f), Offset(left, dimYTop - 3f), strokeWidth = 1f)
    drawLine(dimColor, Offset(left + drawW, top - 5f), Offset(left + drawW, dimYTop - 3f), strokeWidth = 1f)
    drawLine(dimColor, Offset(left, dimYTop), Offset(left + drawW, dimYTop), strokeWidth = 1.2f)
    drawContext.canvas.nativeCanvas.drawText("b = $colW mm", left + drawW / 2, dimYTop - 4f, dimPaint)

    val dimXRight = left + drawW + 20f
    drawLine(dimColor, Offset(left + drawW + 5f, top), Offset(dimXRight + 3f, top), strokeWidth = 1f)
    drawLine(dimColor, Offset(left + drawW + 5f, top + drawH), Offset(dimXRight + 3f, top + drawH), strokeWidth = 1f)
    drawLine(dimColor, Offset(dimXRight, top), Offset(dimXRight, top + drawH), strokeWidth = 1.2f)
    drawContext.canvas.nativeCanvas.save()
    drawContext.canvas.nativeCanvas.rotate(-90f, dimXRight + 14f, top + drawH / 2)
    drawContext.canvas.nativeCanvas.drawText("h = $colD mm", dimXRight + 14f, top + drawH / 2 + 6f, dimPaint)
    drawContext.canvas.nativeCanvas.restore()

    // Label — show ACTUAL reinforcement schedule
    val labelPaint = android.graphics.Paint().apply {
        color = Color(0xFF333333).toArgb(); textSize = 16f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(
        "$numBars Ø$barDia mm + Ø$tieDia/@${stirrupSpacing}mm",
        cx, top + drawH + 35f, labelPaint
    )

    // As provided info if available
    designRes?.let { dr ->
        val asProvText = "As provided: ${String.format("%.0f", dr.asProvided)} mm² | As req: ${String.format("%.0f", dr.asRequired)} mm²"
        drawContext.canvas.nativeCanvas.drawText(
            asProvText,
            cx, top + drawH + 55f, android.graphics.Paint().apply {
                color = Color(0xFF666666).toArgb(); textSize = 13f; textAlign = android.graphics.Paint.Align.CENTER
            }
        )
    }
}

// Draw beam cross section with reinforcement — uses ACTUAL member section & design result
private fun DrawScope.drawBeamSection(
    cx: Float, cy: Float,
    member: FrameMember?,
    result: FrameAnalysisResult?,
    textMeasurer: TextMeasurer
) {
    // Pull actual section dimensions from member.concreteSection (fallback to 250x500 if missing)
    val cs = member?.concreteSection
    val beamW = cs?.width?.toInt() ?: 250
    val beamD = cs?.depth?.toInt() ?: 500
    val cover = cs?.cover?.toInt() ?: 25

    // Find the design result for this member — provides actual bars/diameters/stirrups
    val designRes = result?.concreteDesignResults?.firstOrNull { it.memberId == member?.id }
    val numBarsBottom = designRes?.numBarsBot?.takeIf { it > 0 } ?: 3
    val numBarsTop = designRes?.numBarsTop?.takeIf { it > 0 } ?: 2
    val barDia = designRes?.barDia?.takeIf { it > 0 }?.toInt() ?: 16
    val stirrupDia = designRes?.stirrupDia?.takeIf { it > 0 }?.toInt() ?: 8
    val stirrupSpacing = designRes?.stirrupSpacing?.takeIf { it > 0 }?.toInt() ?: 150

    // Drawing area — proportional to actual b:h ratio (default ~250:500 = 1:2)
    val maxMm = maxOf(beamW, beamD)
    val drawSize = 220f
    val drawW = (beamW.toFloat() / maxMm.toFloat()) * drawSize
    val drawH = (beamD.toFloat() / maxMm.toFloat()) * drawSize
    val left = cx - drawW / 2
    val top = cy - drawH / 2

    // Title
    drawText(
        textMeasurer = textMeasurer,
        text = "BEAM SECTION — ${member?.name?.ifEmpty { "Beam" } ?: "Beam"}",
        topLeft = Offset(cx - 100f, top - 30f),
        style = TextStyle(color = Color(0xFF1565C0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    )

    // Concrete outline
    drawRect(Color(0xFFE0E0E0), Offset(left, top), Size(drawW, drawH))
    drawRect(Color(0xFF555555), Offset(left, top), Size(drawW, drawH), style = Stroke(width = 2.5f))

    // Cover boundary (dashed) — proportional to actual cover
    val coverInset = (cover.toFloat() / maxMm.toFloat()) * drawSize * 0.6f
    drawRect(
        Color(0xFF888888),
        Offset(left + coverInset, top + coverInset),
        Size(drawW - 2 * coverInset, drawH - 2 * coverInset),
        style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
    )

    // Stirrup
    val tieInset = coverInset + 4f
    drawRect(
        Color(0xFF4CAF50),
        Offset(left + tieInset, top + tieInset),
        Size(drawW - 2 * tieInset, drawH - 2 * tieInset),
        style = Stroke(width = 2f)
    )

    val barR = (barDia.toFloat() / maxMm.toFloat() * drawSize * 0.5f).coerceIn(3.5f, 8f)

    // Bottom bars (tension steel for simply-supported beam)
    val bottomY = top + drawH - coverInset - 8f
    if (numBarsBottom > 0) {
        val bottomSpacing = if (numBarsBottom > 1)
            (drawW - 2 * (coverInset + 8f)) / (numBarsBottom - 1)
        else 0f
        for (i in 0 until numBarsBottom) {
            val bx = left + coverInset + 8f + i * bottomSpacing
            drawCircle(Color(0xFF1565C0), barR + 2f, Offset(bx, bottomY))
            drawCircle(Color(0xFF42A5F5), barR, Offset(bx, bottomY))
        }
    }

    // Top bars (compression steel)
    val topY = top + coverInset + 8f
    if (numBarsTop > 0) {
        val topSpacing = if (numBarsTop > 1)
            (drawW - 2 * (coverInset + 8f)) / (numBarsTop - 1)
        else 0f
        for (i in 0 until numBarsTop) {
            val bx = left + coverInset + 8f + i * topSpacing
            drawCircle(Color(0xFFE74C3C), barR + 2f, Offset(bx, topY))
            drawCircle(Color(0xFFFF7043), barR, Offset(bx, topY))
        }
    }

    // Dimensions
    val dimColor = Color(0xFF8E24AA)
    val dimPaint = android.graphics.Paint().apply {
        color = dimColor.toArgb(); textSize = 18f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER
    }
    val dimYTop = top - 20f
    drawLine(dimColor, Offset(left, top - 5f), Offset(left, dimYTop - 3f), strokeWidth = 1f)
    drawLine(dimColor, Offset(left + drawW, top - 5f), Offset(left + drawW, dimYTop - 3f), strokeWidth = 1f)
    drawLine(dimColor, Offset(left, dimYTop), Offset(left + drawW, dimYTop), strokeWidth = 1.2f)
    drawContext.canvas.nativeCanvas.drawText("b = $beamW mm", left + drawW / 2, dimYTop - 4f, dimPaint)

    val dimXRight = left + drawW + 20f
    drawLine(dimColor, Offset(left + drawW + 5f, top), Offset(dimXRight + 3f, top), strokeWidth = 1f)
    drawLine(dimColor, Offset(left + drawW + 5f, top + drawH), Offset(dimXRight + 3f, top + drawH), strokeWidth = 1f)
    drawLine(dimColor, Offset(dimXRight, top), Offset(dimXRight, top + drawH), strokeWidth = 1.2f)
    drawContext.canvas.nativeCanvas.save()
    drawContext.canvas.nativeCanvas.rotate(-90f, dimXRight + 14f, top + drawH / 2)
    drawContext.canvas.nativeCanvas.drawText("h = $beamD mm", dimXRight + 14f, top + drawH / 2 + 6f, dimPaint)
    drawContext.canvas.nativeCanvas.restore()

    // Label — show ACTUAL reinforcement schedule
    val labelPaint = android.graphics.Paint().apply {
        color = Color(0xFF333333).toArgb(); textSize = 16f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(
        "$numBarsBottom Ø$barDia (bot) + $numBarsTop Ø$barDia (top)",
        cx, top + drawH + 35f, labelPaint
    )
    drawContext.canvas.nativeCanvas.drawText(
        "+ Ø$stirrupDia/@$stirrupSpacing mm",
        cx, top + drawH + 55f, labelPaint
    )

    // As provided info if available
    designRes?.let { dr ->
        val asProvText = "As provided: ${String.format("%.0f", dr.asProvided)} mm² | As req: ${String.format("%.0f", dr.asRequired)} mm² | Mu: ${String.format("%.1f", dr.maxMoment)} kN.m"
        drawContext.canvas.nativeCanvas.drawText(
            asProvText,
            cx, top + drawH + 75f, android.graphics.Paint().apply {
                color = Color(0xFF666666).toArgb(); textSize = 13f; textAlign = android.graphics.Paint.Align.CENTER
            }
        )
    }
}

// ============================================================================
// View 3: Plan View (مسقط أفقي)
// Shows the top-down view of the frame layout
// ============================================================================
private fun DrawScope.drawPlanView(
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    scale: Float,
    canvasW: Float,
    canvasH: Float,
    textMeasurer: TextMeasurer
) {
    // Title
    drawText(
        textMeasurer = textMeasurer,
        text = "PLAN VIEW (مسقط أفقي)",
        topLeft = Offset(canvasW / 2 - 100f, 20f),
        style = TextStyle(color = Color(0xFF1565C0), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    )

    if (nodes.isEmpty()) {
        drawText(
            textMeasurer = textMeasurer,
            text = "No nodes to display",
            topLeft = Offset(canvasW / 2 - 80f, canvasH / 2),
            style = TextStyle(color = Color.Gray, fontSize = 14.sp)
        )
        return
    }

    // FIX: Plan view now uses ACTUAL node geometry instead of synthetic 4m depth.
    // For each unique Y (floor level), project onto plan showing:
    //   - All columns at their X positions (sized by actual column section)
    //   - All beams at that level as horizontal lines
    //   - Dimension lines between extreme X coordinates
    val xMin = nodes.minOf { it.x }
    val xMax = nodes.maxOf { it.x }
    val xSpan = max(xMax - xMin, 1.0)

    // Get unique Y levels (floor elevations) — each represents a different plan level
    val yLevels = nodes.map { it.y }.distinct().sorted()
    val nLevels = yLevels.size
    // Depth axis: project each floor level as a separate horizontal band
    val planDepth = max(nLevels * 3.0, 4.0)  // each floor gets ~3m of plan space

    // Drawing area
    val padLeft = 80f
    val padRight = 80f
    val padTop = 80f
    val padBottom = 80f
    val drawW = canvasW - padLeft - padRight
    val drawH = canvasH - padTop - padBottom

    val sx = drawW / xSpan.toFloat()
    val sy = drawH / planDepth.toFloat()
    val s = min(sx, sy) * 0.85f

    val originX = padLeft + (drawW - xSpan.toFloat() * s) / 2f
    val originY = padTop + planDepth.toFloat() * s + (drawH - planDepth.toFloat() * s) / 2f

    // Map (x_meters, depth_index) to screen — depth_index is the floor's plan band
    val toScreen: (Double, Double) -> Offset = { x, z ->
        Offset(
            ((x - xMin).toFloat() * s + originX),
            (-(z).toFloat() * s + originY)
        )
    }

    // For each floor level, draw a band showing the plan at that elevation
    yLevels.forEachIndexed { levelIdx, yLevel ->
        val bandZ = levelIdx * 3.0 + 1.5  // center of this floor's plan band
        val bandTop = levelIdx * 3.0 + 0.2
        val bandBot = (levelIdx + 1) * 3.0 - 0.2

        // Slab rectangle for this floor
        val slabColor = Color(0xFFBBDEFB).copy(alpha = 0.3f)
        val sl1 = toScreen(xMin, bandTop)
        val sl2 = toScreen(xMax, bandBot)
        drawRect(slabColor, Offset(sl1.x, sl2.y), Size(sl2.x - sl1.x, sl1.y - sl2.y))
        drawRect(
            Color(0xFF1565C0).copy(alpha = 0.4f),
            Offset(sl1.x, sl2.y), Size(sl2.x - sl1.x, sl1.y - sl2.y),
            style = Stroke(width = 1f)
        )

        // Floor label
        drawText(
            textMeasurer = textMeasurer,
            text = "L${levelIdx + 1} (y=${String.format("%.2f", yLevel)}m)",
            topLeft = Offset(sl1.x + 4f, sl2.y + 4f),
            style = TextStyle(color = Color(0xFF1565C0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        )

        // Beams at this floor level — use memberType for robust detection
        val beamsAtLevel = members.filter {
            it.memberType == FrameMemberType.Beam &&
                run {
                    val ni = nodes.find { n -> n.id == it.nodeI }
                    val nj = nodes.find { n -> n.id == it.nodeJ }
                    ni != null && nj != null &&
                        abs(ni.y - yLevel) < 0.01 && abs(nj.y - yLevel) < 0.01
                }
        }
        beamsAtLevel.forEach { beam ->
            val ni = nodes.find { it.id == beam.nodeI } ?: return@forEach
            val nj = nodes.find { it.id == beam.nodeJ } ?: return@forEach
            val p1 = toScreen(ni.x, bandZ)
            val p2 = toScreen(nj.x, bandZ)
            drawLine(Color(0xFF42A5F5), p1, p2, strokeWidth = 8f, cap = StrokeCap.Round)
        }

        // Columns at this floor level — draw as squares at each node X position
        // Use actual column section size if available
        val columnsAtLevel = members.filter { it.memberType == FrameMemberType.Column }
        // Get all nodes at this Y level that have a column member connected
        val nodesWithColumns = nodes.filter { n ->
            abs(n.y - yLevel) < 0.01 &&
                columnsAtLevel.any { it.nodeI == n.id || it.nodeJ == n.id }
        }
        nodesWithColumns.forEach { node ->
            val p = toScreen(node.x, bandZ)
            // Column size from section (fallback 300mm)
            val colMember = columnsAtLevel.firstOrNull { it.nodeI == node.id || it.nodeJ == node.id }
            val colW = colMember?.concreteSection?.width?.toInt() ?: 300
            // Scale column square: 300mm = 14f, 600mm = 24f
            val colSize = (14f + (colW - 300) / 30f).coerceIn(12f, 28f)
            drawRect(
                Color(0xFFFF9800),
                Offset(p.x - colSize / 2, p.y - colSize / 2),
                Size(colSize, colSize)
            )
            drawRect(
                Color(0xFFE65100),
                Offset(p.x - colSize / 2, p.y - colSize / 2),
                Size(colSize, colSize),
                style = Stroke(width = 2f)
            )
        }
    }

    // Dimensions — overall span at bottom
    val dimColor = Color(0xFF8E24AA)
    val dimPaint = android.graphics.Paint().apply {
        color = dimColor.toArgb(); textSize = 22f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER
    }

    val dimY = originY + 40f
    val leftX = toScreen(xMin, 0.0).x
    val rightX = toScreen(xMax, 0.0).x
    drawLine(dimColor, Offset(leftX, originY + 5f), Offset(leftX, dimY + 5f), strokeWidth = 1f)
    drawLine(dimColor, Offset(rightX, originY + 5f), Offset(rightX, dimY + 5f), strokeWidth = 1f)
    drawLine(dimColor, Offset(leftX, dimY), Offset(rightX, dimY), strokeWidth = 1.5f)
    drawContext.canvas.nativeCanvas.drawText(
        "L = ${String.format("%.2f", xSpan)} m  (${nLevels} floor${if (nLevels > 1) "s" else ""})",
        (leftX + rightX) / 2, dimY - 8f, dimPaint
    )

    // Legend
    val legX = canvasW - 200f
    val legY = canvasH - 120f
    drawRect(Color(0x22000000), Offset(legX, legY), Size(180f, 100f))
    drawRect(Color(0xFF4A90D9), Offset(legX, legY), Size(180f, 100f), style = Stroke(1f))
    val legTitle = android.graphics.Paint().apply { color = Color.White.toArgb(); textSize = 16f; isFakeBoldText = true }
    val legItem = android.graphics.Paint().apply { color = Color.White.toArgb(); textSize = 14f }
    drawContext.canvas.nativeCanvas.drawText("LEGEND", legX + 8f, legY + 20f, legTitle)
    drawRect(Color(0xFFBBDEFB), Offset(legX + 8f, legY + 30f), Size(16f, 12f))
    drawContext.canvas.nativeCanvas.drawText("Slab", legX + 32f, legY + 40f, legItem)
    drawLine(Color(0xFF42A5F5), Offset(legX + 8f, legY + 56f), Offset(legX + 24f, legY + 56f), strokeWidth = 6f)
    drawContext.canvas.nativeCanvas.drawText("Beam", legX + 32f, legY + 60f, legItem)
    drawRect(Color(0xFFFF9800), Offset(legX + 12f, legY + 72f), Size(8f, 8f))
    drawContext.canvas.nativeCanvas.drawText("Column", legX + 32f, legY + 80f, legItem)
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

    val gridColor = Color(0xFFEEEEEE)
    val axisColor = Color(0xFFD0D0D0)

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
            val path = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x - size, p.y + size * 1.2f)
                lineTo(p.x + size, p.y + size * 1.2f)
                close()
            }
            drawPath(path, color = Color(0xFF1565C0), style = Stroke(width = 2f))
            drawLine(Color(0xFF1565C0), Offset(p.x - size - 5, p.y + size * 1.2f), Offset(p.x + size + 5, p.y + size * 1.2f), strokeWidth = 2f)
            for (i in -2..2) {
                val hx = p.x + i * 8f
                drawLine(Color(0xFF1565C0), Offset(hx, p.y + size * 1.2f), Offset(hx - 5, p.y + size * 1.2f + 6), strokeWidth = 1f)
            }
        }
        SupportType.Roller -> {
            val path = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x - size * 0.7f, p.y + size * 0.8f)
                lineTo(p.x + size * 0.7f, p.y + size * 0.8f)
                close()
            }
            drawPath(path, color = Color(0xFF1565C0), style = Stroke(width = 2f))
            drawCircle(Color(0xFF1565C0), radius = 4f, center = Offset(p.x, p.y + size * 0.8f + 5f), style = Stroke(2f))
            drawLine(Color(0xFF1565C0), Offset(p.x - size, p.y + size * 0.8f + 12f), Offset(p.x + size, p.y + size * 0.8f + 12f), strokeWidth = 2f)
        }
        SupportType.Fixed -> {
            drawLine(Color(0xFF1565C0), Offset(p.x - 14, p.y + 14), Offset(p.x + 14, p.y - 14), strokeWidth = 3f)
            for (i in -2..2) {
                val hx = p.x - 14 + i * 7f
                drawLine(Color(0xFF1565C0), Offset(hx, p.y + 14 + (i - 2) * 3f), Offset(hx - 8, p.y + 6 + (i - 2) * 3f), strokeWidth = 1.5f)
            }
        }
        SupportType.VerticalRoller -> {
            val path = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x + size * 1.2f, p.y - size * 0.7f)
                lineTo(p.x + size * 1.2f, p.y + size * 0.7f)
                close()
            }
            drawPath(path, color = Color(0xFF1565C0), style = Stroke(width = 2f))
            drawCircle(Color(0xFF1565C0), radius = 4f, center = Offset(p.x + size * 1.2f + 5f, p.y), style = Stroke(2f))
        }
        SupportType.Free -> { /* nothing */ }
    }
}

private fun DrawScope.drawSupportLarge(p: Offset, supportType: SupportType, textMeasurer: TextMeasurer, nodeId: Int) {
    val size = 22f
    when (supportType) {
        SupportType.Pin -> {
            val path = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x - size, p.y + size * 1.2f)
                lineTo(p.x + size, p.y + size * 1.2f)
                close()
            }
            drawPath(path, color = Color(0xFF1565C0), style = Stroke(width = 2.5f))
            // Foundation
            drawRect(Color(0xFF888888), Offset(p.x - size - 5, p.y + size * 1.2f), Size(2 * size + 10, 6f))
            // Hatching
            for (i in -3..3) {
                val hx = p.x + i * 8f
                drawLine(Color(0xFF1565C0), Offset(hx, p.y + size * 1.2f + 6f), Offset(hx - 5, p.y + size * 1.2f + 12f), strokeWidth = 1f)
            }
        }
        SupportType.Roller -> {
            val path = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x - size * 0.7f, p.y + size * 0.8f)
                lineTo(p.x + size * 0.7f, p.y + size * 0.8f)
                close()
            }
            drawPath(path, color = Color(0xFF1565C0), style = Stroke(width = 2f))
            drawCircle(Color(0xFF1565C0), radius = 5f, center = Offset(p.x, p.y + size * 0.8f + 6f), style = Stroke(2f))
            drawRect(Color(0xFF888888), Offset(p.x - size - 5, p.y + size * 0.8f + 14f), Size(2 * size + 10, 6f))
        }
        SupportType.Fixed -> {
            drawRect(Color(0xFF888888), Offset(p.x - 18, p.y), Size(36f, 8f))
            for (i in -3..3) {
                val hx = p.x - 18 + i * 9f
                drawLine(Color(0xFF1565C0), Offset(hx, p.y + 8f), Offset(hx - 6, p.y + 14f), strokeWidth = 1.5f)
            }
        }
        SupportType.VerticalRoller -> {
            val path = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x + size * 1.2f, p.y - size * 0.7f)
                lineTo(p.x + size * 1.2f, p.y + size * 0.7f)
                close()
            }
            drawPath(path, color = Color(0xFF1565C0), style = Stroke(width = 2f))
            drawCircle(Color(0xFF1565C0), radius = 5f, center = Offset(p.x + size * 1.2f + 6f, p.y), style = Stroke(2f))
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
        val nx = -dy / L
        val ny = dx / L

        val arrowLen = 25f
        val arrowColor = Color(0xFFD32F2F)

        for (i in 1..numArrows) {
            val t = i.toFloat() / (numArrows + 1)
            val baseX = p1.x + dx * t
            val baseY = p1.y + dy * t
            val tipX = baseX + nx * arrowLen
            val tipY = baseY + ny * arrowLen

            drawLine(arrowColor, Offset(baseX, baseY), Offset(tipX, tipY), strokeWidth = 1.5f)
            val headSize = 4f
            val angle = atan2(ny, nx)
            drawLine(arrowColor, Offset(tipX, tipY), Offset(tipX - headSize * cos(angle - 0.5f), tipY - headSize * sin(angle - 0.5f)), strokeWidth = 1.5f)
            drawLine(arrowColor, Offset(tipX, tipY), Offset(tipX - headSize * cos(angle + 0.5f), tipY - headSize * sin(angle + 0.5f)), strokeWidth = 1.5f)
        }

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

        drawLine(Color(0xFFD32F2F), Offset(baseX, baseY), Offset(tipX, tipY), strokeWidth = 2f)
        val angle = atan2(ny, nx)
        drawLine(Color(0xFFD32F2F), Offset(tipX, tipY), Offset(tipX - 6f * cos(angle - 0.5f), tipY - 6f * sin(angle - 0.5f)), strokeWidth = 2f)
        drawLine(Color(0xFFD32F2F), Offset(tipX, tipY), Offset(tipX - 6f * cos(angle + 0.5f), tipY - 6f * sin(angle + 0.5f)), strokeWidth = 2f)

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

    if (abs(load.fy) > 0.01) {
        val dir = if (load.fy < 0) 1f else -1f
        val tipY = p.y + dir * arrowLen
        drawLine(arrowColor, start = p, end = Offset(p.x, tipY), strokeWidth = 2f)
        val headDir = if (load.fy < 0) -1f else 1f
        drawLine(arrowColor, start = Offset(p.x, tipY), end = Offset(p.x - 5f, tipY + headDir * 6f), strokeWidth = 2f)
        drawLine(arrowColor, start = Offset(p.x, tipY), end = Offset(p.x + 5f, tipY + headDir * 6f), strokeWidth = 2f)
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
        drawLine(arrowColor, start = p, end = Offset(tipX, p.y), strokeWidth = 2f)
        val headDir = if (load.fx > 0) -1f else 1f
        drawLine(arrowColor, start = Offset(tipX, p.y), end = Offset(tipX + headDir * 6f, p.y - 5f), strokeWidth = 2f)
        drawLine(arrowColor, start = Offset(tipX, p.y), end = Offset(tipX + headDir * 6f, p.y + 5f), strokeWidth = 2f)
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

        val dx = (p2.x - p1.x) / L
        val dy = (p2.y - p1.y) / L
        val nx = -dy
        val ny = dx

        val points = when (diagramType) {
            DiagramType.BMD -> diagram.momentDiagram
            DiagramType.SFD -> diagram.shearDiagram
            DiagramType.AFD -> diagram.axialDiagram
        }

        if (points.size < 2) continue

        val maxVal = points.maxOfOrNull { abs(it.value) } ?: 1.0
        if (maxVal < 0.001) continue
        val diagramScale = 40f / maxVal.toFloat()

        val diagramColor = when (diagramType) {
            DiagramType.BMD -> Color(0xFF2196F3)
            DiagramType.SFD -> Color(0xFF4CAF50)
            DiagramType.AFD -> Color(0xFFFF9800)
        }

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

        path.lineTo(p2.x, p2.y)
        path.lineTo(p1.x, p1.y)
        path.close()

        drawPath(path = path, color = diagramColor.copy(alpha = 0.2f))

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
    val maxDisp = nodeResults.maxOfOrNull { (max(abs(it.dx), abs(it.dy)) * scale).toFloat() } ?: 0f
    if (maxDisp < 0.5f) return
    val deformScale = 50f / maxDisp.coerceAtLeast(1f)

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
    val barLength_m = 1.0
    val barLength_px = (barLength_m * scale).toFloat()
    val x = 20f
    val y = size.height - 25f

    drawLine(Color.DarkGray, Offset(x, y), Offset(x + barLength_px, y), strokeWidth = 2f)
    drawLine(Color.DarkGray, Offset(x, y - 5), Offset(x, y + 5), strokeWidth = 2f)
    drawLine(Color.DarkGray, Offset(x + barLength_px, y - 5), Offset(x + barLength_px, y + 5), strokeWidth = 2f)
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
