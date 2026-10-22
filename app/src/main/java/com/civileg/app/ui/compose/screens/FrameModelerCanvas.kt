package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.MemberLoadType
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SlabPanel
import com.civileg.app.domain.entities.SupportType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class ModelerTool {
    Select, Pan, Move, Joint, Member, Support, Hinge, NodalLoad, MemberLoad, Slab, Erase
}

data class ModelerViewport(
    val scale: Float = 60f,
    val panX: Float = 0f,
    val panY: Float = 0f
) {
    fun worldToScreen(x: Double, y: Double): Offset =
        Offset(panX + x.toFloat() * scale, panY - y.toFloat() * scale)

    fun screenToWorld(offset: Offset): Pair<Double, Double> =
        Pair(((offset.x - panX) / scale).toDouble(), ((panY - offset.y) / scale).toDouble())

    fun transformed(centroid: Offset, pan: Offset, zoom: Float): ModelerViewport {
        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val world = screenToWorld(centroid)
        return ModelerViewport(
            scale = newScale,
            panX = centroid.x - world.first.toFloat() * newScale + pan.x,
            panY = centroid.y + world.second.toFloat() * newScale + pan.y
        )
    }
}

data class ModelerTap(
    val worldX: Double,
    val worldY: Double,
    val nodeId: Int? = null,
    val memberId: Int? = null,
    val panelId: Int? = null,
    val memberEndIsI: Boolean? = null
)

private const val MIN_SCALE = 2f
private const val MAX_SCALE = 30000f

@Composable
fun FrameModelerCanvas(
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    nodalLoads: List<NodalLoad>,
    memberLoads: List<MemberLoad>,
    slabs: List<SlabPanel>,
    selectedNodeId: Int?,
    selectedMemberId: Int?,
    tool: ModelerTool,
    pendingStartNodeId: Int?,
    pendingSlabNodeIds: List<Int>,
    memberDragPreview: Pair<Double, Double>? = null,
    gridSizeMm: Int,
    snapMm: Int,
    viewport: ModelerViewport,
    onViewportChange: (ModelerViewport) -> Unit,
    onTap: (ModelerTap) -> Unit,
    onNodeDragStart: (Int) -> Unit = {},
    onNodeDrag: (Int, Double, Double) -> Unit = { _, _, _ -> },
    onNodeDragEnd: () -> Unit = {},
    onNodeDragCancel: () -> Unit = {},
    onMemberDragStart: (Int) -> Unit = {},
    onMemberDragUpdate: (Double, Double) -> Unit = { _, _ -> },
    onMemberDragEnd: (Int?, Double, Double) -> Unit = { _, _, _ -> },
    onMemberDragCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
    fitRequest: Int = 0
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val currentViewport by rememberUpdatedState(viewport)
    val currentNodes by rememberUpdatedState(nodes)
    val currentMembers by rememberUpdatedState(members)
    val currentSlabs by rememberUpdatedState(slabs)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnViewport by rememberUpdatedState(onViewportChange)
    val currentOnNodeDragStart by rememberUpdatedState(onNodeDragStart)
    val currentOnNodeDrag by rememberUpdatedState(onNodeDrag)
    val currentOnNodeDragEnd by rememberUpdatedState(onNodeDragEnd)
    val currentOnNodeDragCancel by rememberUpdatedState(onNodeDragCancel)
    val currentOnMemberDragStart by rememberUpdatedState(onMemberDragStart)
    val currentOnMemberDragUpdate by rememberUpdatedState(onMemberDragUpdate)
    val currentOnMemberDragEnd by rememberUpdatedState(onMemberDragEnd)
    val currentOnMemberDragCancel by rememberUpdatedState(onMemberDragCancel)
    val background = Color(0xFFFCFCFA)
    val textColor = Color(0xFF263238)
    var pointerPosition by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasW = constraints.maxWidth.toFloat()
        val canvasH = constraints.maxHeight.toFloat()

        LaunchedEffect(fitRequest, canvasW, canvasH) {
            if (canvasW > 0f && canvasH > 0f) {
                currentOnViewport(fitViewport(nodes, canvasW, canvasH))
            }
        }

        // Pre-measure all text layouts in composition phase to avoid measuring during draw phase
        val gridTextLayouts = remember(viewport, canvasW, canvasH, gridSizeMm, textColor) {
            val layouts = mutableListOf<Triple<Double, Boolean, TextLayoutResult>>()
            if (canvasW > 0f && canvasH > 0f) {
                var step = gridSizeMm / 1000.0
                if (step <= 0.0) step = 1.0
                while (step * viewport.scale < 18f) step *= 10.0
                val left = (0f - viewport.panX) / viewport.scale
                val right = (canvasW - viewport.panX) / viewport.scale
                val bottom = (viewport.panY - canvasH) / viewport.scale
                val top = viewport.panY / viewport.scale

                val startX = floor(left / step) * step
                val startY = floor(bottom / step) * step
                val style = TextStyle(color = textColor.copy(alpha = 0.5f), fontSize = 8.sp)

                var i = 0
                var x = startX
                while (x <= right && i < 1200) {
                    val major = Math.round(x / (step * 10.0)) * (step * 10.0)
                    val isMajor = abs(x - major) < step * 0.5
                    val isAxis = abs(x) < step * 0.5
                    if (isMajor && !isAxis) {
                        val text = formatGridLabel(x)
                        layouts.add(Triple(x, true, textMeasurer.measure(text, style)))
                    }
                    x += step
                    i++
                }

                i = 0
                var y = startY
                while (y <= top && i < 1200) {
                    val major = Math.round(y / (step * 10.0)) * (step * 10.0)
                    val isMajor = abs(y - major) < step * 0.5
                    val isAxis = abs(y) < step * 0.5
                    if (isMajor && !isAxis) {
                        val text = formatGridLabel(y)
                        layouts.add(Triple(y, false, textMeasurer.measure(text, style)))
                    }
                    y += step
                    i++
                }
            }
            layouts
        }

        val slabTextLayouts = remember(slabs) {
            slabs.associate { slab ->
                val text = "SLAB-${slab.id}  q=${slab.loadKNm2} kN/m2"
                val layout = textMeasurer.measure(
                    text = text,
                    style = TextStyle(color = Color(0xFF0D47A1), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
                slab.id to layout
            }
        }

        val memberLengthLayouts = remember(members, nodes, viewport.scale) {
            if (viewport.scale > 25f) {
                members.associate { member ->
                    val a = nodes.find { it.id == member.nodeI }
                    val b = nodes.find { it.id == member.nodeJ }
                    val length = if (a != null && b != null) hypot(b.x - a.x, b.y - a.y) else 0.0
                    val text = String.format(java.util.Locale.ROOT, "%.2f m", length)
                    val layout = textMeasurer.measure(
                        text = text,
                        style = TextStyle(color = Color(0xFF37474F), fontSize = 9.sp)
                    )
                    member.id to layout
                }
            } else {
                emptyMap()
            }
        }

        val memberLoadLayouts = remember(memberLoads, members, nodes) {
            memberLoads.associateWith { load ->
                val text = when (load.loadType) {
                    MemberLoadType.UDL -> String.format(java.util.Locale.ROOT, "w=%.1f kN/m", load.value)
                    MemberLoadType.PointLoad -> "P=${load.value} kN"
                    MemberLoadType.Moment -> "M=${load.value}"
                    MemberLoadType.LinearVarying -> String.format(java.util.Locale.ROOT, "w=%.1f kN/m (varies)", load.value)
                }
                val color = loadCaseColor(load.loadCase)
                val style = if (load.loadType == MemberLoadType.LinearVarying) {
                    TextStyle(color = color, fontSize = 9.sp)
                } else {
                    TextStyle(color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                textMeasurer.measure(text = text, style = style)
            }
        }

        val nodalLoadLayouts = remember(nodalLoads) {
            nodalLoads.associateWith { load ->
                val color = loadCaseColor(load.loadCase)
                val mag = hypot(load.fx, load.fy)
                val forceLayout = if (mag > 1e-9) {
                    textMeasurer.measure(
                        text = "(${load.fx}, ${load.fy}) kN",
                        style = TextStyle(color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    )
                } else null
                val momentLayout = if (abs(load.mz) > 1e-9) {
                    textMeasurer.measure(
                        text = "M=${load.mz}",
                        style = TextStyle(color = color, fontSize = 9.sp)
                    )
                } else null
                forceLayout to momentLayout
            }
        }

        val nodeLayouts = remember(nodes, selectedNodeId) {
            nodes.associate { node ->
                val selected = node.id == selectedNodeId
                val color = if (selected) Color(0xFFEF6C00) else Color(0xFF263238)
                val layout = textMeasurer.measure(
                    text = "${node.id}",
                    style = TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
                node.id to layout
            }
        }

        val pendingLayout = remember(pendingSlabNodeIds.size) {
            if (pendingSlabNodeIds.isNotEmpty()) {
                textMeasurer.measure(
                    text = "Slab joints: ${pendingSlabNodeIds.size}/4",
                    style = TextStyle(color = Color(0xFF00695C), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
            } else null
        }

        val viewportGesture = when (tool) {
            ModelerTool.Move -> {
                Modifier.pointerInput(tool) {
                    var draggedNodeId: Int? = null
                    detectItemDragOrTransform(
                        onStart = { offset ->
                            val node = findNodeAt(
                                offset = offset,
                                nodes = currentNodes,
                                viewport = currentViewport,
                                density = density.density
                            )
                            draggedNodeId = node?.id
                            if (node != null) currentOnNodeDragStart(node.id)
                            node != null
                        },
                        onDrag = { offset ->
                            draggedNodeId?.let { id ->
                                val (x, y) = snappedWorld(offset, currentViewport, snapMm)
                                currentOnNodeDrag(id, x, y)
                            }
                        },
                        onEnd = { offset ->
                            draggedNodeId?.let { id ->
                                val (x, y) = snappedWorld(offset, currentViewport, snapMm)
                                currentOnNodeDrag(id, x, y)
                                currentOnNodeDragEnd()
                            }
                            draggedNodeId = null
                        },
                        onCancel = {
                            if (draggedNodeId != null) currentOnNodeDragCancel()
                            draggedNodeId = null
                        },
                        onTransform = { centroid, pan, zoom ->
                            currentOnViewport(currentViewport.transformed(centroid, pan, zoom))
                        }
                    )
                }
            }
            ModelerTool.Member -> {
                Modifier.pointerInput(tool) {
                    var draggingFromNode = false
                    detectItemDragOrTransform(
                        onStart = { offset ->
                            val node = findNodeAt(
                                offset = offset,
                                nodes = currentNodes,
                                viewport = currentViewport,
                                density = density.density
                            )
                            draggingFromNode = node != null
                            if (node != null) currentOnMemberDragStart(node.id)
                            node != null
                        },
                        onDrag = { offset ->
                            if (draggingFromNode) {
                                val (x, y) = snappedWorld(offset, currentViewport, snapMm)
                                currentOnMemberDragUpdate(x, y)
                            }
                        },
                        onEnd = { offset ->
                            if (draggingFromNode) {
                                val (x, y) = snappedWorld(offset, currentViewport, snapMm)
                                val endNode = findNodeAt(
                                    offset = offset,
                                    nodes = currentNodes,
                                    viewport = currentViewport,
                                    density = density.density
                                )
                                currentOnMemberDragEnd(endNode?.id, x, y)
                            }
                            draggingFromNode = false
                        },
                        onCancel = {
                            if (draggingFromNode) currentOnMemberDragCancel()
                            draggingFromNode = false
                        },
                        onTransform = { centroid, pan, zoom ->
                            currentOnViewport(currentViewport.transformed(centroid, pan, zoom))
                        }
                    )
                }
            }
            else -> {
                Modifier.pointerInput(tool) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        currentOnViewport(currentViewport.transformed(centroid, pan, zoom))
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(viewportGesture)
                .pointerInput(tool) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            pointerPosition = if (pressed.size == 1) pressed.first().position else null
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (offset.x.isFinite() && offset.y.isFinite()) {
                            val vp = currentViewport
                            val (wx, wy) = vp.screenToWorld(offset)
                            val tap = buildTap(
                                offset = offset,
                                worldX = wx,
                                worldY = wy,
                                nodes = currentNodes,
                                members = currentMembers,
                                slabs = currentSlabs,
                                scale = vp.scale,
                                snapMm = snapMm,
                                density = density.density
                            )
                            currentOnTap(tap)
                        }
                    }
                }
        ) {
            drawRect(background)
            val vp = viewport
            val scale = vp.scale
            val snapTap = pointerPosition
                ?.takeIf { tool != ModelerTool.Pan && tool != ModelerTool.Move }
                ?.let { position ->
                    val (worldX, worldY) = vp.screenToWorld(position)
                    buildTap(
                        offset = position,
                        worldX = worldX,
                        worldY = worldY,
                        nodes = currentNodes,
                        members = currentMembers,
                        slabs = currentSlabs,
                        scale = vp.scale,
                        snapMm = snapMm,
                        density = density.density
                    )
                }

            drawGrid(
                vp = vp,
                canvasW = canvasW,
                canvasH = canvasH,
                gridSizeMm = gridSizeMm,
                gridTextLayouts = gridTextLayouts,
                textColor = textColor
            )

            drawSlabs(slabs, nodes, vp, slabTextLayouts)
            drawMembers(members, nodes, selectedMemberId, vp, memberLengthLayouts, scale)
            drawSupports(nodes, vp)
            drawMemberLoads(members, nodes, memberLoads, vp, memberLoadLayouts)
            drawNodalLoads(nodes, nodalLoads, vp, nodalLoadLayouts)
            drawNodes(nodes, selectedNodeId, vp, nodeLayouts)
            drawMemberDragPreview(nodes, pendingStartNodeId, memberDragPreview, vp)
            drawPending(
                nodes = nodes,
                pendingStartNodeId = pendingStartNodeId,
                pendingSlabNodeIds = pendingSlabNodeIds,
                vp = vp,
                pendingLayout = pendingLayout
            )
            drawSnapIndicator(pointerPosition, snapTap, vp)
        }
    }
}

private fun DrawScope.drawSnapIndicator(
    pointer: Offset?,
    tap: ModelerTap?,
    vp: ModelerViewport
) {
    if (pointer == null) return
    val crossColor = Color(0x6600897B)
    drawLine(crossColor, Offset(pointer.x - 7f, pointer.y), Offset(pointer.x + 7f, pointer.y), 1f)
    drawLine(crossColor, Offset(pointer.x, pointer.y - 7f), Offset(pointer.x, pointer.y + 7f), 1f)
    if (tap == null) return
    val point = vp.worldToScreen(tap.worldX, tap.worldY)
    val color = when {
        tap.nodeId != null -> Color(0xFFEF6C00)
        tap.memberId != null -> Color(0xFF1565C0)
        else -> Color(0xFF00897B)
    }
    drawCircle(color, 8f, point, style = Stroke(width = 2f))
    drawCircle(Color.White, 2.5f, point)
}

private fun DrawScope.drawMemberDragPreview(
    nodes: List<FrameNode>,
    pendingStartNodeId: Int?,
    preview: Pair<Double, Double>?,
    vp: ModelerViewport
) {
    val start = nodes.find { it.id == pendingStartNodeId } ?: return
    val end = preview ?: return
    val startScreen = vp.worldToScreen(start.x, start.y)
    val endScreen = vp.worldToScreen(end.first, end.second)
    drawLine(
        color = Color(0xFF1565C0),
        start = startScreen,
        end = endScreen,
        strokeWidth = 2.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
    )
    drawCircle(Color(0xFF1565C0), radius = 6f, center = endScreen)
    drawCircle(Color.White, radius = 2.5f, center = endScreen)
}

private fun fitViewport(nodes: List<FrameNode>, canvasW: Float, canvasH: Float): ModelerViewport {
    if (nodes.isEmpty()) {
        return ModelerViewport(scale = 80f, panX = canvasW / 2f, panY = canvasH * 0.7f)
    }
    val minX = nodes.minOf { it.x }
    val maxX = nodes.maxOf { it.x }
    val minY = nodes.minOf { it.y }
    val maxY = nodes.maxOf { it.y }
    val spanX = max(maxX - minX, 1.0)
    val spanY = max(maxY - minY, 1.0)
    val scale = min(canvasW / spanX.toFloat(), canvasH / spanY.toFloat()) * 0.72f
    val clamped = scale.coerceIn(MIN_SCALE, MAX_SCALE)
    val panX = canvasW / 2f - ((minX + maxX) / 2.0).toFloat() * clamped
    val panY = canvasH / 2f + ((minY + maxY) / 2.0).toFloat() * clamped
    return ModelerViewport(clamped, panX, panY)
}

private fun findNodeAt(
    offset: Offset,
    nodes: List<FrameNode>,
    viewport: ModelerViewport,
    density: Float
): FrameNode? {
    val node = nodes.minByOrNull { n ->
        hypot(offset.x - viewport.worldToScreen(n.x, n.y).x, offset.y - viewport.worldToScreen(n.x, n.y).y)
    } ?: return null
    val distance = hypot(
        offset.x - viewport.worldToScreen(node.x, node.y).x,
        offset.y - viewport.worldToScreen(node.x, node.y).y
    )
    return if (distance <= 20f * density) node else null
}

private fun snappedWorld(
    offset: Offset,
    viewport: ModelerViewport,
    snapMm: Int
): Pair<Double, Double> {
    val (rawX, rawY) = viewport.screenToWorld(offset)
    val snap = snapMm / 1000.0
    val x = if (snap > 0) Math.round(rawX / snap) * snap else rawX
    val y = if (snap > 0) Math.round(rawY / snap) * snap else rawY
    return x to y
}

private suspend fun PointerInputScope.detectItemDragOrTransform(
    onStart: (Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onEnd: (Offset) -> Unit,
    onCancel: () -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val touchSlop = viewConfiguration.touchSlop
        var decisionMade = false
        var dragging = false
        var transforming = false
        var lastPosition = down.position

        try {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }

                if (pressed.size >= 2) {
                    if (!transforming) {
                        transforming = true
                        decisionMade = true
                        if (dragging) {
                            onCancel()
                            dragging = false
                        }
                    }
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val pan = event.calculatePan()
                    val zoom = event.calculateZoom()
                    if (centroid.x.isFinite() && centroid.y.isFinite() &&
                        (zoom != 1f || pan != Offset.Zero)
                    ) {
                        onTransform(centroid, pan, zoom)
                    }
                    event.changes.forEach { it.consume() }
                } else if (pressed.size == 1) {
                    val change = pressed.first()
                    lastPosition = change.position
                    if (transforming) {
                        val pan = change.position - change.previousPosition
                        if (pan != Offset.Zero) onTransform(change.position, pan, 1f)
                        change.consume()
                    } else {
                        if (!decisionMade &&
                            (lastPosition - down.position).getDistance() > touchSlop
                        ) {
                            decisionMade = true
                            dragging = onStart(down.position)
                        }
                        if (dragging) {
                            change.consume()
                            onDrag(lastPosition)
                        }
                    }
                }

                if (event.changes.none { it.pressed }) break
            }
        } catch (t: Throwable) {
            if (dragging) onCancel()
            throw t
        }

        if (dragging) onEnd(lastPosition)
    }
}

private fun buildTap(
    offset: Offset,
    worldX: Double,
    worldY: Double,
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    slabs: List<SlabPanel>,
    scale: Float,
    snapMm: Int,
    density: Float
): ModelerTap {
    val nodeTolerance = 16f * density
    val snapM = snapMm / 1000.0
    val nearestNode = nodes.minByOrNull { node ->
        hypot(
            (node.x - worldX).toFloat() * scale,
            (node.y - worldY).toFloat() * scale
        )
    }
    val nodeDistance = nearestNode?.let {
        hypot((it.x - worldX).toFloat() * scale, (it.y - worldY).toFloat() * scale)
    } ?: Float.MAX_VALUE

    if (nearestNode != null && nodeDistance <= nodeTolerance) {
        return ModelerTap(nearestNode.x, nearestNode.y, nodeId = nearestNode.id)
    }

    val worldTolerance = nodeTolerance / scale
    var bestMember: FrameMember? = null
    var bestDist = Float.MAX_VALUE
    for (m in members) {
        val a = nodes.find { it.id == m.nodeI } ?: continue
        val b = nodes.find { it.id == m.nodeJ } ?: continue
        val d = pointToSegmentDistance(
            worldX.toFloat(), worldY.toFloat(),
            a.x.toFloat(), a.y.toFloat(),
            b.x.toFloat(), b.y.toFloat()
        )
        if (d < bestDist) {
            bestDist = d
            bestMember = m
        }
    }

    if (bestMember != null && bestDist <= worldTolerance) {
        val a = nodes.find { it.id == bestMember.nodeI }
        val b = nodes.find { it.id == bestMember.nodeJ }
        val endIsI = if (a != null && b != null) {
            val da = hypot((a.x - worldX).toFloat(), (a.y - worldY).toFloat())
            val db = hypot((b.x - worldX).toFloat(), (b.y - worldY).toFloat())
            da <= db
        } else null
        val projected = if (a != null && b != null) {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val denominator = dx * dx + dy * dy
            val t = if (denominator > 1e-12) {
                (((worldX - a.x) * dx + (worldY - a.y) * dy) / denominator).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            a.x + t * dx to a.y + t * dy
        } else {
            worldX to worldY
        }
        return ModelerTap(
            projected.first,
            projected.second,
            memberId = bestMember.id,
            memberEndIsI = endIsI
        )
    }

    val panel = slabs.firstOrNull { p ->
        val pts = p.nodeIds.mapNotNull { id -> nodes.find { it.id == id } }
        pts.size == 4 && pointInPolygon(worldX, worldY, pts)
    }
    if (panel != null) {
        return ModelerTap(worldX, worldY, panelId = panel.id)
    }

    val snappedX = if (snapM > 0) (worldX / snapM).let { Math.round(it).toDouble() * snapM } else worldX
    val snappedY = if (snapM > 0) (worldY / snapM).let { Math.round(it).toDouble() * snapM } else worldY
    return ModelerTap(snappedX, snappedY)
}

private fun DrawScope.drawGrid(
    vp: ModelerViewport,
    canvasW: Float,
    canvasH: Float,
    gridSizeMm: Int,
    gridTextLayouts: List<Triple<Double, Boolean, TextLayoutResult>>,
    textColor: Color
) {
    var step = gridSizeMm / 1000.0
    if (step <= 0.0) step = 1.0
    while (step * vp.scale < 18f) step *= 10.0
    val left = (0f - vp.panX) / vp.scale
    val right = (canvasW - vp.panX) / vp.scale
    val bottom = (vp.panY - canvasH) / vp.scale
    val top = vp.panY / vp.scale

    val startX = floor(left / step) * step
    val startY = floor(bottom / step) * step
    val minorColor = Color(0xFFB0BEC5).copy(alpha = 0.22f)
    val majorColor = Color(0xFF90A4AE).copy(alpha = 0.45f)
    val axisColor = Color(0xFF1565C0).copy(alpha = 0.75f)

    var i = 0
    var x = startX
    while (x <= right && i < 1200) {
        val sx = vp.worldToScreen(x, 0.0).x
        val major = Math.round(x / (step * 10.0)) * (step * 10.0)
        val isMajor = abs(x - major) < step * 0.5
        val isAxis = abs(x) < step * 0.5
        drawLine(
            color = if (isAxis) axisColor else if (isMajor) majorColor else minorColor,
            start = Offset(sx, 0f),
            end = Offset(sx, canvasH),
            strokeWidth = if (isAxis) 2f else if (isMajor) 1.2f else 0.7f
        )
        x += step
        i++
    }

    i = 0
    var y = startY
    while (y <= top && i < 1200) {
        val sy = vp.worldToScreen(0.0, y).y
        val major = Math.round(y / (step * 10.0)) * (step * 10.0)
        val isMajor = abs(y - major) < step * 0.5
        val isAxis = abs(y) < step * 0.5
        drawLine(
            color = if (isAxis) axisColor else if (isMajor) majorColor else minorColor,
            start = Offset(0f, sy),
            end = Offset(canvasW, sy),
            strokeWidth = if (isAxis) 2f else if (isMajor) 1.2f else 0.7f
        )
        y += step
        i++
    }

    for ((valCoord, isX, layout) in gridTextLayouts) {
        if (isX) {
            val sx = vp.worldToScreen(valCoord, 0.0).x
            drawText(layout, topLeft = Offset(sx + 3f, canvasH - 18f))
        } else {
            val sy = vp.worldToScreen(0.0, valCoord).y
            drawText(layout, topLeft = Offset(4f, sy - 12f))
        }
    }
}

private fun formatGridLabel(value: Double): String {
    val absV = abs(value)
    return when {
        absV < 1e-9 -> "0"
        absV >= 1.0 -> String.format(java.util.Locale.ROOT, "%.1f m", value)
        else -> String.format(java.util.Locale.ROOT, "%.0f mm", value * 1000.0)
    }
}

private fun DrawScope.drawSlabs(
    slabs: List<SlabPanel>,
    nodes: List<FrameNode>,
    vp: ModelerViewport,
    slabTextLayouts: Map<Int, TextLayoutResult>
) {
    for (slab in slabs) {
        val pts = slab.nodeIds.mapNotNull { id -> nodes.find { it.id == id } }
        if (pts.size != 4) continue
        val path = Path()
        pts.forEachIndexed { index, node ->
            val p = vp.worldToScreen(node.x, node.y)
            if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, Color(0xFF64B5F6).copy(alpha = 0.14f))
        drawPath(path, Color(0xFF1976D2).copy(alpha = 0.55f), style = Stroke(width = 1.5f))
        val c = pts.map { vp.worldToScreen(it.x, it.y) }
        val cx = c.sumOf { it.x.toDouble() }.toFloat() / 4f
        val cy = c.sumOf { it.y.toDouble() }.toFloat() / 4f
        slabTextLayouts[slab.id]?.let { layout ->
            drawText(layout, topLeft = Offset(cx - 45f, cy - 8f))
        }
    }
}

private fun DrawScope.drawMembers(
    members: List<FrameMember>,
    nodes: List<FrameNode>,
    selectedMemberId: Int?,
    vp: ModelerViewport,
    memberLengthLayouts: Map<Int, TextLayoutResult>,
    scale: Float
) {
    for (member in members) {
        val a = nodes.find { it.id == member.nodeI } ?: continue
        val b = nodes.find { it.id == member.nodeJ } ?: continue
        val p1 = vp.worldToScreen(a.x, a.y)
        val p2 = vp.worldToScreen(b.x, b.y)
        val selected = member.id == selectedMemberId
        drawLine(
            color = if (selected) Color(0xFFFF8F00) else Color(0xFF1565C0),
            start = p1,
            end = p2,
            strokeWidth = if (selected) 8f else 5f,
            cap = StrokeCap.Round
        )
        if (scale > 25f) {
            val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
            memberLengthLayouts[member.id]?.let { layout ->
                drawText(layout, topLeft = Offset(mid.x + 4f, mid.y - 14f))
            }
        }
        if (member.releaseI || member.releaseJ) {
            val angle = atan2(p2.y - p1.y, p2.x - p1.x)
            if (member.releaseI) drawHingeAt(p1, angle)
            if (member.releaseJ) drawHingeAt(p2, angle)
        }
    }
}

private fun DrawScope.drawHingeAt(p: Offset, angle: Float) {
    val r = 7f
    drawCircle(Color.White, r, p)
    drawCircle(Color(0xFFD84315), r, p, style = Stroke(width = 2f))
    val dx = cos(angle + Math.PI.toFloat() / 2f) * (r + 3f)
    val dy = sin(angle + Math.PI.toFloat() / 2f) * (r + 3f)
    drawLine(Color(0xFFD84315), Offset(p.x - dx, p.y - dy), Offset(p.x + dx, p.y + dy), 2f)
}

private fun DrawScope.drawSupports(nodes: List<FrameNode>, vp: ModelerViewport) {
    val color = Color(0xFF2E7D32)
    for (node in nodes) {
        if (node.support == SupportType.Free) continue
        val p = vp.worldToScreen(node.x, node.y)
        val baseY = p.y + 16f
        val half = 13f
        drawLine(color, Offset(p.x, p.y), Offset(p.x, baseY), 2.5f)
        when (node.support) {
            SupportType.Fixed -> {
                drawLine(color, Offset(p.x - half, baseY), Offset(p.x + half, baseY), 3f)
                for (i in -3..3) {
                    val x = p.x + i * 4f
                    drawLine(color, Offset(x, baseY), Offset(x - 4f, baseY + 8f), 1.5f)
                }
            }
            SupportType.Pin -> {
                val path = Path().apply {
                    moveTo(p.x, p.y + 2f)
                    lineTo(p.x - half, baseY)
                    lineTo(p.x + half, baseY)
                    close()
                }
                drawPath(path, color.copy(alpha = 0.12f))
                drawPath(path, color, style = Stroke(width = 2f))
                drawLine(color, Offset(p.x - half, baseY), Offset(p.x + half, baseY), 2f)
            }
            SupportType.Roller -> {
                val path = Path().apply {
                    moveTo(p.x, p.y + 2f)
                    lineTo(p.x - half, p.y + 14f)
                    lineTo(p.x + half, p.y + 14f)
                    close()
                }
                drawPath(path, color, style = Stroke(width = 2f))
                drawCircle(color, 3.5f, Offset(p.x - 7f, p.y + 20f))
                drawCircle(color, 3.5f, Offset(p.x + 7f, p.y + 20f))
                drawLine(color, Offset(p.x - half, p.y + 24f), Offset(p.x + half, p.y + 24f), 2f)
            }
            SupportType.VerticalRoller -> {
                drawLine(color, Offset(p.x - 8f, p.y - 8f), Offset(p.x + 8f, p.y - 8f), 2f)
                drawCircle(color, 3.5f, Offset(p.x - 20f, p.y))
                drawCircle(color, 3.5f, Offset(p.x - 20f, p.y + 10f))
                drawLine(color, Offset(p.x - 25f, p.y - 8f), Offset(p.x - 25f, p.y + 18f), 2f)
            }
            SupportType.Free -> Unit
        }
    }
}

private fun loadCaseColor(loadCase: String): Color =
    when (loadCase.trim().uppercase(java.util.Locale.ROOT)) {
        "DL" -> Color(0xFFC62828)
        "LL" -> Color(0xFF1565C0)
        "WL" -> Color(0xFF00838F)
        else -> Color(0xFF6A1B9A)
    }

private fun DrawScope.drawMemberLoads(
    members: List<FrameMember>,
    nodes: List<FrameNode>,
    loads: List<MemberLoad>,
    vp: ModelerViewport,
    memberLoadLayouts: Map<MemberLoad, TextLayoutResult>
) {
    for (load in loads) {
        val member = members.find { it.id == load.memberId } ?: continue
        val a = nodes.find { it.id == member.nodeI } ?: continue
        val b = nodes.find { it.id == member.nodeJ } ?: continue
        val p1 = vp.worldToScreen(a.x, a.y)
        val p2 = vp.worldToScreen(b.x, b.y)
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val len = hypot(dx, dy)
        if (len < 1e-3f) continue
        val nx = -dy / len
        val ny = dx / len
        val offset = 18f
        val color = loadCaseColor(load.loadCase)
        val layout = memberLoadLayouts[load]
        when (load.loadType) {
            MemberLoadType.UDL -> {
                drawLine(color, Offset(p1.x + nx * offset, p1.y + ny * offset), Offset(p2.x + nx * offset, p2.y + ny * offset), 1.5f)
                val steps = 6
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val x = p1.x + dx * t + nx * offset
                    val y = p1.y + dy * t + ny * offset
                    drawLine(color, Offset(x, y), Offset(x - nx * 10f, y - ny * 10f), 1.2f)
                }
                layout?.let {
                    drawText(
                        it,
                        topLeft = Offset((p1.x + p2.x) / 2f + 6f, (p1.y + p2.y) / 2f + ny * (offset + 4f) - 6f)
                    )
                }
            }
            MemberLoadType.PointLoad -> {
                val t = (load.position / max(member.getLength(nodes), 1e-9)).toFloat().coerceIn(0f, 1f)
                val x = p1.x + dx * t
                val y = p1.y + dy * t
                drawLine(color, Offset(x - nx * 26f, y - ny * 26f), Offset(x, y), 2.5f)
                drawArrowHead(Offset(x, y), Offset(x - nx * 26f, y - ny * 26f), color)
                layout?.let {
                    drawText(it, topLeft = Offset(x + 4f, y - 18f))
                }
            }
            MemberLoadType.Moment -> {
                val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                drawCircle(color, 13f, mid, style = Stroke(width = 2f))
                layout?.let {
                    drawText(it, topLeft = Offset(mid.x + 14f, mid.y - 14f))
                }
            }
            MemberLoadType.LinearVarying -> {
                drawLine(color, Offset(p1.x + nx * offset, p1.y + ny * offset), Offset(p2.x + nx * offset, p2.y + ny * offset), 1.5f)
                layout?.let {
                    drawText(it, topLeft = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f - 22f))
                }
            }
        }
    }
}

private fun DrawScope.drawNodalLoads(
    nodes: List<FrameNode>,
    loads: List<NodalLoad>,
    vp: ModelerViewport,
    nodalLoadLayouts: Map<NodalLoad, Pair<TextLayoutResult?, TextLayoutResult?>>
) {
    for (load in loads) {
        val node = nodes.find { it.id == load.nodeId } ?: continue
        val p = vp.worldToScreen(node.x, node.y)
        val mag = hypot(load.fx, load.fy)
        val color = loadCaseColor(load.loadCase)
        val (forceLayout, momentLayout) = nodalLoadLayouts[load] ?: (null to null)

        if (mag > 1e-9) {
            val ux = (load.fx / mag).toFloat()
            val uy = (-load.fy / mag).toFloat()
            val start = Offset(p.x - ux * 36f, p.y - uy * 36f)
            drawLine(color, start, p, 2.5f)
            drawArrowHead(p, start, color)
            forceLayout?.let {
                drawText(it, topLeft = Offset(p.x + 8f, p.y + 8f))
            }
        }
        if (abs(load.mz) > 1e-9) {
            drawCircle(color, 12f, Offset(p.x + 20f, p.y - 20f), style = Stroke(width = 2f))
            momentLayout?.let {
                drawText(it, topLeft = Offset(p.x + 34f, p.y - 28f))
            }
        }
    }
}

private fun DrawScope.drawArrowHead(tip: Offset, from: Offset, color: Color) {
    val angle = atan2(tip.y - from.y, tip.x - from.x)
    val len = 9f
    val a1 = angle + Math.PI.toFloat() * 0.85f
    val a2 = angle - Math.PI.toFloat() * 0.85f
    drawLine(color, tip, Offset(tip.x + cos(a1) * len, tip.y + sin(a1) * len), 2f)
    drawLine(color, tip, Offset(tip.x + cos(a2) * len, tip.y + sin(a2) * len), 2f)
}

private fun DrawScope.drawNodes(
    nodes: List<FrameNode>,
    selectedNodeId: Int?,
    vp: ModelerViewport,
    nodeLayouts: Map<Int, TextLayoutResult>
) {
    for (node in nodes) {
        val p = vp.worldToScreen(node.x, node.y)
        val selected = node.id == selectedNodeId
        val r = if (selected) 9f else 7f
        drawCircle(if (selected) Color(0xFFFFF3E0) else Color.White, r, p)
        drawCircle(
            if (selected) Color(0xFFEF6C00) else Color(0xFF263238),
            r,
            p,
            style = Stroke(width = if (selected) 3f else 2f)
        )
        nodeLayouts[node.id]?.let { layout ->
            drawText(layout, topLeft = Offset(p.x + 9f, p.y - 20f))
        }
    }
}

private fun DrawScope.drawPending(
    nodes: List<FrameNode>,
    pendingStartNodeId: Int?,
    pendingSlabNodeIds: List<Int>,
    vp: ModelerViewport,
    pendingLayout: TextLayoutResult?
) {
    val start = nodes.find { it.id == pendingStartNodeId }
    if (start != null) {
        val p = vp.worldToScreen(start.x, start.y)
        drawCircle(Color(0xFFFF8F00), 12f, p, style = Stroke(width = 2f))
    }
    if (pendingSlabNodeIds.isNotEmpty()) {
        val pts = pendingSlabNodeIds.mapNotNull { id -> nodes.find { it.id == id } }
        if (pts.size >= 2) {
            for (i in 0 until pts.size - 1) {
                val a = vp.worldToScreen(pts[i].x, pts[i].y)
                val b = vp.worldToScreen(pts[i + 1].x, pts[i + 1].y)
                drawLine(
                    Color(0xFF00897B), a, b, 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                )
            }
        }
        pts.lastOrNull()?.let {
            val p = vp.worldToScreen(it.x, it.y)
            drawCircle(Color(0xFF00897B), 11f, p, style = Stroke(width = 2f))
        }
        pendingLayout?.let {
            drawText(it, topLeft = Offset(12f, 12f))
        }
    }
}

private fun pointToSegmentDistance(
    px: Float, py: Float,
    x1: Float, y1: Float,
    x2: Float, y2: Float
): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    if (abs(dx) < 1e-9f && abs(dy) < 1e-9f) return hypot(px - x1, py - y1)
    var t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
    t = t.coerceIn(0f, 1f)
    val cx = x1 + t * dx
    val cy = y1 + t * dy
    return hypot(px - cx, py - cy)
}

private fun pointInPolygon(x: Double, y: Double, points: List<FrameNode>): Boolean {
    var inside = false
    var j = points.lastIndex
    for (i in points.indices) {
        val xi = points[i].x
        val yi = points[i].y
        val xj = points[j].x
        val yj = points[j].y
        val intersects = ((yi > y) != (yj > y)) &&
            (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { abs(it) > 1e-12 } ?: 1e-12) + xi)
        if (intersects) inside = !inside
        j = i
    }
    return inside
}