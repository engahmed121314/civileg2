package com.civileg.app.domain.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Renders DrawingScene primitives on a Jetpack Compose Canvas.
 */
object ComposeRenderer {
    private fun DrawScope.acvColor(layer: DrawingLayer): Color = when (layer.name) {
        DrawingLayer.OUTLINE.name -> Color(0xFF1F2937)
        DrawingLayer.REBAR_BOTTOM.name -> Color(0xFF059669)
        DrawingLayer.REBAR_TOP.name -> Color(0xFF2563EB)
        DrawingLayer.REBAR_STIRRUP.name -> Color(0xFFDC2626)
        DrawingLayer.DIMENSION.name -> Color(0xFF0891B2)
        DrawingLayer.TEXT.name -> Color(0xFF111827)
        DrawingLayer.HATCH.name -> Color(0xFF9CA3AF)
        DrawingLayer.CENTERLINE.name -> Color(0xFF6B7280)
        DrawingLayer.TITLE_BLOCK.name -> Color(0xFF111827)
        else -> Color(0xFF111827)
    }

    @Composable
    fun render(
        scene: DrawingScene,
        modifier: Modifier = Modifier
    ) {
        Canvas(modifier = modifier) {
            drawScene(scene)
        }
    }

    private fun DrawScope.drawScene(scene: DrawingScene) {
        for (p in scene.primitives) {
            when (p) {
                is Primitive.Line -> {
                    drawLine(
                        color = acvColor(p.layer),
                        start = Offset(p.p1.x.toFloat(), p.p1.y.toFloat()),
                        end = Offset(p.p2.x.toFloat(), p.p2.y.toFloat()),
                        strokeWidth = p.layer.lineweightMm.toFloat() * 2f
                    )
                }
                is Primitive.Polyline -> {
                    for (i in 0 until p.points.size - 1) {
                        val pt1 = p.points[i]
                        val pt2 = p.points[i + 1]
                        drawLine(
                            color = acvColor(p.layer),
                            start = Offset(pt1.x.toFloat(), pt1.y.toFloat()),
                            end = Offset(pt2.x.toFloat(), pt2.y.toFloat()),
                            strokeWidth = p.layer.lineweightMm.toFloat() * 2f
                        )
                    }
                    if (p.isClosed && p.points.size > 2) {
                        val first = p.points.first()
                        val last = p.points.last()
                        drawLine(
                            color = acvColor(p.layer),
                            start = Offset(last.x.toFloat(), last.y.toFloat()),
                            end = Offset(first.x.toFloat(), first.y.toFloat()),
                            strokeWidth = p.layer.lineweightMm.toFloat() * 2f
                        )
                    }
                }
                is Primitive.Circle -> {
                    drawCircle(
                        color = acvColor(p.layer),
                        radius = p.radius.toFloat(),
                        center = Offset(p.center.x.toFloat(), p.center.y.toFloat()),
                        style = Stroke(width = p.layer.lineweightMm.toFloat() * 2f)
                    )
                }
                is Primitive.RebarDot -> {
                    drawCircle(
                        color = acvColor(p.layer),
                        radius = (p.diameterMm / 2.0).toFloat(),
                        center = Offset(p.center.x.toFloat(), p.center.y.toFloat())
                    )
                }
                is Primitive.Text -> {
                    // Text rendering handled via Compose text or canvas basic draw
                }
                is Primitive.Dimension -> {
                    drawLine(
                        color = acvColor(p.layer),
                        start = Offset(p.p1.x.toFloat(), p.p1.y.toFloat()),
                        end = Offset(p.p2.x.toFloat(), p.p2.y.toFloat()),
                        strokeWidth = 1f
                    )
                }
                is Primitive.Leader -> {
                    for (i in 0 until p.points.size - 1) {
                        val pt1 = p.points[i]
                        val pt2 = p.points[i + 1]
                        drawLine(
                            color = acvColor(p.layer),
                            start = Offset(pt1.x.toFloat(), pt1.y.toFloat()),
                            end = Offset(pt2.x.toFloat(), pt2.y.toFloat()),
                            strokeWidth = 1f
                        )
                    }
                }
                else -> {}
            }
        }
    }
}
