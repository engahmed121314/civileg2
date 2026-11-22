package com.civileg.app.domain.drawing

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp

/** All geometry and labels come from the same immutable scene consumed by PdfRenderer. */
object ComposeRenderer {
    @Composable
    fun render(scene: DrawingScene, modifier: Modifier = Modifier) {
        DrawingGeometry.validate(scene)
        Canvas(modifier = modifier) {
            val canvas = drawContext.canvas.nativeCanvas
            val viewport = DrawingGeometry.fit(scene.bounds, size.width.toDouble(), size.height.toDouble(), 6.0)
            fun mapped(p: Point2D): Point2D = viewport.map(p).let { Point2D(it.x, size.height - it.y) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val minimumAnnotationPx = 12.sp.toPx()
            fun line(a: Point2D, b: Point2D) {
                val p1 = mapped(a); val p2 = mapped(b)
                canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), paint)
            }
            fun path(points: List<Point2D>, closed: Boolean = false) {
                val first = mapped(points.first())
                val path = Path().apply {
                    moveTo(first.x.toFloat(), first.y.toFloat())
                    points.drop(1).forEach { val p = mapped(it); lineTo(p.x.toFloat(), p.y.toFloat()) }
                    if (closed) close()
                }
                canvas.drawPath(path, paint)
            }
            fun label(value: String, position: Point2D, height: Double, angle: Double = 0.0, halo: Boolean = true) {
                val p = mapped(position)
                val text = value
                val textPaint = Paint(paint).apply {
                    style = Paint.Style.FILL
                    pathEffect = null
                    // Model fit must not shrink annotations below readable UI type.
                    // Geometry stays at the shared uniform scale; font scale is respected.
                    textSize = DrawingGeometry.annotationSize(height, viewport.scale, minimumAnnotationPx.toDouble()).toFloat()
                    typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                }
                canvas.save()
                try {
                    canvas.translate(p.x.toFloat(), p.y.toFloat())
                    canvas.rotate(-angle.toFloat())
                    if (halo) {
                        val background = Paint().apply { color = android.graphics.Color.WHITE }
                        canvas.drawRect(RectF(-1f, -textPaint.textSize, textPaint.measureText(text) + 1f, textPaint.textSize * 0.25f), background)
                    }
                    canvas.drawText(text, 0f, 0f, textPaint)
                } finally { canvas.restore() }
            }
            scene.primitives.forEach { p ->
                paint.color = 0xFF000000.toInt() or DrawingGeometry.color(p.layer)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (p.layer.lineweightMm * 72.0 / 25.4).toFloat()
                paint.pathEffect = if (p.layer.linetype == "CENTER") DashPathEffect(floatArrayOf(7f, 2f, 1f, 2f), 0f) else null
                when (p) {
                    is Primitive.Line -> line(p.p1, p.p2)
                    is Primitive.Polyline -> path(p.points, p.isClosed)
                    is Primitive.Circle -> { val c = mapped(p.center); canvas.drawCircle(c.x.toFloat(), c.y.toFloat(), (p.radius * viewport.scale).toFloat(), paint) }
                    is Primitive.Arc -> path(DrawingGeometry.arcPoints(p))
                    is Primitive.Text -> label(p.text, p.position, p.heightMm, p.rotationDeg, p.halo || p.pillBackground)
                    is Primitive.Dimension -> {
                        line(p.p1, p.p2)
                        val tick = 2.5 / viewport.scale
                        listOf(p.p1, p.p2).forEach { line(Point2D(it.x - tick, it.y - tick), Point2D(it.x + tick, it.y + tick)) }
                        label(p.text, p.textPosition, 9.0 / viewport.scale)
                    }
                    is Primitive.RebarDot -> {
                        val c = mapped(p.center); paint.style = Paint.Style.FILL
                        canvas.drawCircle(c.x.toFloat(), c.y.toFloat(), (p.diameterMm / 2 * viewport.scale).toFloat(), paint)
                    }
                    is Primitive.Leader -> { path(p.points); label(p.text, p.points.last(), 9.0 / viewport.scale) }
                }
            }
        }
    }
}
