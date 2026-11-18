package com.civileg.core.output.drawing

import com.civileg.core.output.dxf.DxfEngine

/**
 * Renderer for rendering a DrawingScene into a complete AC1027 DXF document (§6.1).
 * Pure Kotlin — zero UI dependencies.
 */
object DrawingSceneDxfRenderer {

    fun renderToDxf(scene: DrawingScene): String {
        val sb = StringBuilder()
        sb.append(DxfEngine.headerSection())

        val layerNames = scene.layers.map { it.name }.distinct()
        sb.append(DxfEngine.layerTable(if (layerNames.isEmpty()) listOf("0") else layerNames))

        sb.append("0\nSECTION\n2\nENTITIES\n")

        for (view in scene.views) {
            for (primitive in view.primitives) {
                renderPrimitive(sb, primitive)
            }
        }

        sb.append("0\nENDSEC\n0\nEOF\n")
        return sb.toString()
    }

    private fun renderPrimitive(sb: StringBuilder, primitive: DrawingPrimitive) {
        val layerName = primitive.layer.name
        when (primitive) {
            is DrawingPrimitive.Line -> {
                sb.append(DxfEngine.lineEntity(primitive.x1, primitive.y1, primitive.x2, primitive.y2, layerName))
            }
            is DrawingPrimitive.Circle -> {
                sb.append(DxfEngine.circleEntity(primitive.cx, primitive.cy, primitive.radius, layerName))
            }
            is DrawingPrimitive.RebarDot -> {
                sb.append(DxfEngine.circleEntity(primitive.cx, primitive.cy, primitive.diameter / 2.0, layerName))
            }
            is DrawingPrimitive.Text -> {
                sb.append(DxfEngine.textEntity(primitive.text, primitive.x, primitive.y, primitive.height, layerName))
            }
            is DrawingPrimitive.Dimension -> {
                sb.append(DxfEngine.lineEntity(primitive.x1, primitive.y1, primitive.x2, primitive.y2, layerName))
                val midX = (primitive.x1 + primitive.x2) / 2.0
                val midY = (primitive.y1 + primitive.y2) / 2.0 + 5.0
                sb.append(DxfEngine.textEntity(primitive.text, midX, midY, 15.0, layerName))
            }
            is DrawingPrimitive.Polyline -> {
                if (primitive.points.size >= 2) {
                    for (i in 0 until primitive.points.size - 1) {
                        val p1 = primitive.points[i]
                        val p2 = primitive.points[i + 1]
                        sb.append(DxfEngine.lineEntity(p1.first, p1.second, p2.first, p2.second, layerName))
                    }
                    if (primitive.isClosed) {
                        val pFirst = primitive.points.first()
                        val pLast = primitive.points.last()
                        sb.append(DxfEngine.lineEntity(pLast.first, pLast.second, pFirst.first, pFirst.second, layerName))
                    }
                }
            }
            is DrawingPrimitive.Stirrup -> {
                val p1 = Pair(primitive.x1, primitive.y1)
                val p2 = Pair(primitive.x2, primitive.y1)
                val p3 = Pair(primitive.x2, primitive.y2)
                val p4 = Pair(primitive.x1, primitive.y2)
                sb.append(DxfEngine.lineEntity(p1.first, p1.second, p2.first, p2.second, layerName))
                sb.append(DxfEngine.lineEntity(p2.first, p2.second, p3.first, p3.second, layerName))
                sb.append(DxfEngine.lineEntity(p3.first, p3.second, p4.first, p4.second, layerName))
                sb.append(DxfEngine.lineEntity(p4.first, p4.second, p1.first, p1.second, layerName))
            }
            is DrawingPrimitive.Arc -> {
                sb.append(DxfEngine.circleEntity(primitive.cx, primitive.cy, primitive.radius, layerName))
            }
            is DrawingPrimitive.Hatch -> {
                if (primitive.boundary.size >= 2) {
                    for (i in 0 until primitive.boundary.size - 1) {
                        val p1 = primitive.boundary[i]
                        val p2 = primitive.boundary[i + 1]
                        sb.append(DxfEngine.lineEntity(p1.first, p1.second, p2.first, p2.second, layerName))
                    }
                }
            }
            is DrawingPrimitive.Table -> {
                var currentY = primitive.y
                for (row in primitive.rows) {
                    val rowText = row.joinToString(" | ")
                    sb.append(DxfEngine.textEntity(rowText, primitive.x, currentY, 15.0, layerName))
                    currentY -= 25.0
                }
            }
        }
    }
}
