package com.civileg.app.domain.drawing

import kotlin.math.roundToInt

object DxfRenderer {
    private const val DXF_EOL = "\n"

    fun layerCode(layer: DrawingLayer): Int = when (layer.name) {
        DrawingLayer.OUTLINE.name -> 7
        DrawingLayer.REBAR_BOTTOM.name -> 3
        DrawingLayer.REBAR_TOP.name -> 5
        DrawingLayer.REBAR_STIRRUP.name -> 1
        DrawingLayer.DIMENSION.name -> 2
        DrawingLayer.TEXT.name -> 7
        DrawingLayer.HATCH.name -> 8
        DrawingLayer.CENTERLINE.name -> 1
        DrawingLayer.TITLE_BLOCK.name -> 7
        else -> 7
    }

    fun renderToDxf(scene: DrawingScene): String {
        val sb = StringBuilder()
        sb.appendLine("0", "SECTION")
        sb.appendLine("2", "HEADER")
        sb.appendLine("0", "ENDSEC")
        sb.appendLine()
        sb.appendLine("0", "SECTION")
        sb.appendLine("2", "TABLES")
        sb.appendLine("0", "LAYER")
        sb.appendLine("2", "LTI")
        sb.appendLine("70", scene.layers.size.toString())
        for (layer in scene.layers) {
            sb.appendLine("0", "LAYER")
            sb.appendLine("2", layer.name)
            sb.appendLine("70", "0")
            sb.appendLine("62", layer.aciColor.toString())
            sb.appendLine("6", layer.linetype)
            sb.appendLine("48", layer.lineweightMm.toString())
        }
        sb.appendLine("0", "ENDTAB")
        sb.appendLine("0", "ENDSEC")
        sb.appendLine()
        sb.appendLine("0", "SECTION")
        sb.appendLine("2", "ENTITIES")
        sb.appendEntities(scene.primitives)
        sb.appendLine("0", "ENDSEC")
        sb.appendLine("0", "EOF")
        return sb.toString()
    }

    private fun StringBuilder.appendLine(code: String, value: String) {
        append(code).append(DXF_EOL).append(value).append(DXF_EOL)
    }

    private fun StringBuilder.appendEntities(primitives: List<Primitive>): StringBuilder {
        for (p in primitives) {
            when (p) {
                is Primitive.Line -> {
                    appendLine("0", "LINE")
                    appendLine("8", layerCode(p.layer).toString())
                    appendLine("10", formatMm(p.p1.x))
                    appendLine("20", formatMm(p.p1.y))
                    appendLine("30", "0.0")
                    appendLine("11", formatMm(p.p2.x))
                    appendLine("21", formatMm(p.p2.y))
                    appendLine("31", "0.0")
                }
                is Primitive.Polyline -> {
                    appendLine("0", "LWPOLYLINE")
                    appendLine("8", layerCode(p.layer).toString())
                    appendLine("90", p.points.size.toString())
                    appendLine("70", if (p.isClosed) "1" else "0")
                    for (pt in p.points) {
                        appendLine("10", formatMm(pt.x))
                        appendLine("20", formatMm(pt.y))
                    }
                }
                is Primitive.Circle -> {
                    appendLine("0", "CIRCLE")
                    appendLine("8", layerCode(p.layer).toString())
                    appendLine("10", formatMm(p.center.x))
                    appendLine("20", formatMm(p.center.y))
                    appendLine("30", "0.0")
                    appendLine("40", formatMm(p.radius))
                }
                is Primitive.Arc -> {
                    appendLine("0", "ARC")
                    appendLine("8", layerCode(p.layer).toString())
                    appendLine("10", formatMm(p.center.x))
                    appendLine("20", formatMm(p.center.y))
                    appendLine("30", "0.0")
                    appendLine("40", formatMm(p.radius))
                    appendLine("50", formatMm(p.startAngleDeg))
                    appendLine("51", formatMm(p.endAngleDeg))
                }
                is Primitive.Text -> {
                    appendLine("0", "TEXT")
                    appendLine("8", layerCode(p.layer).toString())
                    appendLine("10", formatMm(p.position.x))
                    appendLine("20", formatMm(p.position.y))
                    appendLine("30", "0.0")
                    appendLine("40", formatMm(p.heightMm))
                    appendLine("1", escapeDxfText(p.text))
                    appendLine("50", formatMm(p.rotationDeg))
                }
                is Primitive.Dimension -> {
                    appendLine("0", "LINE")
                    appendLine("8", layerCode(DrawingLayer.DIMENSION).toString())
                    appendLine("10", formatMm(p.p1.x))
                    appendLine("20", formatMm(p.p1.y))
                    appendLine("30", "0.0")
                    appendLine("11", formatMm(p.p2.x))
                    appendLine("21", formatMm(p.p2.y))
                    appendLine("31", "0.0")
                    appendLine("0", "TEXT")
                    appendLine("8", layerCode(DrawingLayer.TEXT).toString())
                    appendLine("10", formatMm(p.textPosition.x))
                    appendLine("20", formatMm(p.textPosition.y))
                    appendLine("30", "0.0")
                    appendLine("40", formatMm(2.5))
                    appendLine("1", escapeDxfText(p.text))
                }
                is Primitive.RebarDot -> {
                    val r = p.diameterMm / 2.0
                    appendLine("0", "CIRCLE")
                    appendLine("8", layerCode(p.layer).toString())
                    appendLine("10", formatMm(p.center.x))
                    appendLine("20", formatMm(p.center.y))
                    appendLine("30", "0.0")
                    appendLine("40", formatMm(r))
                }
                is Primitive.Leader -> {
                    val pts = p.points
                    for (i in pts.indices) {
                        if (i + 1 < pts.size) {
                            appendLine("0", "LINE")
                            appendLine("8", layerCode(p.layer).toString())
                            appendLine("10", formatMm(pts[i].x))
                            appendLine("20", formatMm(pts[i].y))
                            appendLine("30", "0.0")
                            appendLine("11", formatMm(pts[i + 1].x))
                            appendLine("21", formatMm(pts[i + 1].y))
                            appendLine("31", "0.0")
                        }
                    }
                    val last = pts.lastOrNull()
                    if (last != null) {
                        appendLine("0", "TEXT")
                        appendLine("8", layerCode(p.layer).toString())
                        appendLine("10", formatMm(last.x))
                        appendLine("20", formatMm(last.y))
                        appendLine("30", "0.0")
                        appendLine("40", "2.5")
                        appendLine("1", escapeDxfText(p.text))
                    }
                }
            }
        }
        return this
    }

    private fun formatMm(v: Double): String = "%.3f".format(v)

    private fun escapeDxfText(text: String): String =
        text.replace("\r", " ").replace("\n", " ").replace("\t", " ")
            .replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}")
}
