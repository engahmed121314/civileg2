package com.civileg.app.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Renders the same DXF geometry that is exported as the .dxf file into a white-background bitmap for the PDF page,
 * so the PDF drawing is the DXF drawing (plan, section, notes), at the page proportions.
 * Supports the entities DxfExporter writes for element sheets: LINE, CIRCLE, ARC, TEXT, LWPOLYLINE.
 */
object DxfBitmapRenderer {

    sealed class Prim {
        abstract val layer: String
        data class Line(override val layer: String, val x1: Double, val y1: Double, val x2: Double, val y2: Double) : Prim()
        data class Circle(override val layer: String, val x: Double, val y: Double, val r: Double) : Prim()
        data class Text(override val layer: String, val x: Double, val y: Double, val h: Double, val s: String) : Prim()
        data class Poly(override val layer: String, val pts: List<Pair<Double, Double>>, val closed: Boolean) : Prim()
    }

    /** Pure parser of the DXF ENTITIES section (group code / value pairs). */
    fun parse(dxf: String): List<Prim> {
        val lines = dxf.split('\n').map { it.trim() }
        val pairs = ArrayList<Pair<String, String>>()
        var i = 0
        while (i + 1 < lines.size) { pairs.add(lines[i] to lines[i + 1]); i += 2 }
        val out = ArrayList<Prim>()
        var inEntities = false
        var k = 0
        while (k < pairs.size) {
            val (c, v) = pairs[k]
            if (c == "2" && k > 0 && pairs[k - 1] == ("0" to "SECTION")) inEntities = v == "ENTITIES"
            if (c == "0" && v == "ENDSEC") inEntities = false
            if (inEntities && c == "0" && v in setOf("LINE", "CIRCLE", "TEXT", "LWPOLYLINE")) {
                var j = k + 1
                val codes = ArrayList<Pair<String, String>>()
                while (j < pairs.size && pairs[j].first != "0") { codes.add(pairs[j]); j++ }
                fun d(code: String) = codes.firstOrNull { it.first == code }?.second?.toDoubleOrNull()
                val layer = codes.firstOrNull { it.first == "8" }?.second ?: "0"
                when (v) {
                    "LINE" -> {
                        val a = d("10"); val b = d("20"); val e = d("11"); val f = d("21")
                        if (a != null && b != null && e != null && f != null) out.add(Prim.Line(layer, a, b, e, f))
                    }
                    "CIRCLE" -> {
                        val a = d("10"); val b = d("20"); val r = d("40")
                        if (a != null && b != null && r != null) out.add(Prim.Circle(layer, a, b, r))
                    }
                    "TEXT" -> {
                        val a = d("10"); val b = d("20"); val h = d("40")
                        val s = codes.firstOrNull { it.first == "1" }?.second
                        if (a != null && b != null && h != null && s != null) out.add(Prim.Text(layer, a, b, h, s))
                    }
                    "LWPOLYLINE" -> {
                        val pts = ArrayList<Pair<String, Double>>()
                        val xs = codes.filter { it.first == "10" }.mapNotNull { it.second.toDoubleOrNull() }
                        val ys = codes.filter { it.first == "20" }.mapNotNull { it.second.toDoubleOrNull() }
                        val closed = ((codes.firstOrNull { it.first == "70" }?.second?.toIntOrNull() ?: 0) and 1) == 1
                        if (xs.size == ys.size && xs.size >= 2) out.add(Prim.Poly(layer, xs.zip(ys), closed))
                    }
                }
                k = j
            } else k++
        }
        return out
    }

    private fun bounds(p: List<Prim>): DoubleArray? {
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        fun pt(x: Double, y: Double) { minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y) }
        p.forEach {
            when (it) {
                is Prim.Line -> { pt(it.x1, it.y1); pt(it.x2, it.y2) }
                is Prim.Circle -> { pt(it.x - it.r, it.y - it.r); pt(it.x + it.r, it.y + it.r) }
                is Prim.Text -> { pt(it.x, it.y); pt(it.x + it.h * 0.6 * it.s.length, it.y + it.h) }
                is Prim.Poly -> it.pts.forEach { q -> pt(q.first, q.second) }
            }
        }
        return if (minX > maxX) null else doubleArrayOf(minX, minY, maxX, maxY)
    }

    /** Returns null when the drawing has no geometry or cannot be rendered. */
    fun render(drawing: DxfDrawable?, widthPx: Int = 2000): Bitmap? = try {
        if (drawing == null) null else renderPrims(parse(DxfExporter().renderToString(drawing)), widthPx)
    } catch (e: Throwable) { null }

    fun renderPrims(prims: List<Prim>, widthPx: Int): Bitmap? {
        val b = bounds(prims) ?: return null
        val margin = 40.0
        val w = b[2] - b[0]; val h = b[3] - b[1]
        if (w <= 0 || h <= 0) return null
        val scale = (widthPx - 2 * margin) / w
        val heightPx = (h * scale + 2 * margin).toInt().coerceAtLeast(200)
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(Color.WHITE)
        fun sx(x: Double) = ((x - b[0]) * scale + margin).toFloat()
        fun sy(y: Double) = (heightPx - ((y - b[1]) * scale + margin)).toFloat()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.BLACK }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = Typeface.SANS_SERIF }
        fun colorFor(layer: String) = when {
            layer.contains("REBAR") -> Color.rgb(0, 70, 190)
            layer == "SOIL" -> Color.rgb(140, 100, 40)
            layer == "SHEET" || layer == "TITLE" -> Color.DKGRAY
            else -> Color.BLACK
        }
        prims.forEach {
            stroke.color = colorFor(it.layer)
            when (it) {
                is Prim.Line -> cv.drawLine(sx(it.x1), sy(it.y1), sx(it.x2), sy(it.y2), stroke)
                is Prim.Circle -> cv.drawCircle(sx(it.x), sy(it.y), (it.r * scale).toFloat().coerceAtLeast(1.5f), stroke)
                is Prim.Poly -> {
                    for (n in 0 until it.pts.size - 1) cv.drawLine(sx(it.pts[n].first), sy(it.pts[n].second), sx(it.pts[n + 1].first), sy(it.pts[n + 1].second), stroke)
                    if (it.closed) cv.drawLine(sx(it.pts.last().first), sy(it.pts.last().second), sx(it.pts[0].first), sy(it.pts[0].second), stroke)
                }
                is Prim.Text -> {
                    text.color = colorFor(it.layer)
                    text.textSize = (it.h * scale).toFloat().coerceAtLeast(16f)
                    cv.drawText(it.s, sx(it.x), sy(it.y), text)
                }
            }
        }
        return bmp
    }
}
