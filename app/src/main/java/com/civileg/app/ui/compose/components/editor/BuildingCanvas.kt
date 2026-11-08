package com.civileg.app.ui.compose.components.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.civileg.app.domain.entities.BuildingColumnCategory
import com.civileg.app.domain.entities.BuildingModel
import com.civileg.app.domain.entities.BuildingNode
import kotlin.math.hypot

@Composable
fun BuildingCanvas(
    model: BuildingModel,
    activeTool: String,
    onNodeClicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(40f) } // pixels per meter
    var panX by remember { mutableStateOf(200f) }
    var panY by remember { mutableStateOf(300f) }

    Box(modifier = modifier.fillMaxSize().pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
            scale = (scale * zoom).coerceIn(10f, 200f)
            panX += pan.x
            panY += pan.y
        }
    }.pointerInput(activeTool) {
        detectTapGestures { offset ->
            // Convert screen offset to world coordinates (meters)
            val wx = (offset.x - panX) / scale
            val wy = (panY - offset.y) / scale
            val snappedX = Math.round(wx / model.gridSpacing) * model.gridSpacing
            val snappedY = Math.round(wy / model.gridSpacing) * model.gridSpacing
            onNodeClicked(snappedX, snappedY)
        }
    }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Draw Grid Lines
            val gridStep = model.gridSpacing * scale
            val startX = panX % gridStep
            var currentX = startX
            while (currentX < width) {
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = Offset(currentX.toFloat(), 0f),
                    end = Offset(currentX.toFloat(), height),
                    strokeWidth = 1f
                )
                currentX += gridStep.toFloat()
            }

            val startY = panY % gridStep
            var currentY = startY
            while (currentY < height) {
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = Offset(0f, currentY.toFloat()),
                    end = Offset(width, currentY.toFloat()),
                    strokeWidth = 1f
                )
                currentY += gridStep.toFloat()
            }

            // 2. Draw Slabs
            val nodeMap = model.nodes.associateBy { it.id }
            model.slabs.forEach { slab ->
                val path = Path()
                val slabNodes = slab.nodeIds.mapNotNull { nodeMap[it] }
                if (slabNodes.isNotEmpty()) {
                    slabNodes.forEachIndexed { idx, node ->
                        val screen = worldToScreen(node.x, node.y, panX, panY, scale)
                        if (idx == 0) path.moveTo(screen.x, screen.y)
                        else path.lineTo(screen.x, screen.y)
                    }
                    path.close()
                    drawPath(path, color = Color.Blue.copy(alpha = 0.15f))
                    drawPath(path, color = Color.Blue, style = Stroke(width = 2f))
                }
            }

            // 3. Draw Footings
            model.footings.forEach { footing ->
                val screen = worldToScreen(footing.x, footing.y, panX, panY, scale)
                val wPx = (footing.width * scale).toFloat()
                val hPx = (footing.length * scale).toFloat()
                drawRect(
                    color = Color(0xFF795548).copy(alpha = 0.6f),
                    topLeft = Offset(screen.x - wPx / 2f, screen.y - hPx / 2f),
                    size = Size(wPx, hPx)
                )
                drawRect(
                    color = Color(0xFF4E342E),
                    topLeft = Offset(screen.x - wPx / 2f, screen.y - hPx / 2f),
                    size = Size(wPx, hPx),
                    style = Stroke(width = 2f)
                )
            }

            // 4. Draw Columns & Steel Members
            model.columns.forEach { col ->
                val screen = worldToScreen(col.x, col.y, panX, panY, scale)
                val sizePx = 0.3 * scale
                val color = if (col.category == BuildingColumnCategory.STEEL_IPE) Color(0xFF607D8B) else Color(0xFFE91E63)
                drawRect(
                    color = color,
                    topLeft = Offset(screen.x - sizePx.toFloat() / 2f, screen.y - sizePx.toFloat() / 2f),
                    size = Size(sizePx.toFloat(), sizePx.toFloat())
                )
            }

            // 5. Draw Nodes
            model.nodes.forEach { node ->
                val screen = worldToScreen(node.x, node.y, panX, panY, scale)
                drawCircle(color = Color.Black, radius = 4f, center = screen)
            }
        }
    }
}

private fun worldToScreen(wx: Double, wy: Double, panX: Float, panY: Float, scale: Float): Offset {
    val sx = panX + (wx * scale).toFloat()
    val sy = panY - (wy * scale).toFloat()
    return Offset(sx, sy)
}
