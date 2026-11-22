package com.civileg.app.domain.drawing

import kotlin.math.*

/** A uniform, centered viewport. Both backends use mm coordinates with y pointing up. */
data class DrawingViewport(val scale: Double, val offsetX: Double, val offsetY: Double) {
    fun map(point: Point2D): Point2D = Point2D(offsetX + point.x * scale, offsetY + point.y * scale)
}

object DrawingGeometry {
    fun fit(bounds: Rect2D, width: Double, height: Double, padding: Double = 0.0): DrawingViewport {
        require(listOf(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY, width, height, padding).all { it.isFinite() })
        require(bounds.width > 0 && bounds.height > 0 && padding >= 0)
        require(width > 2 * padding && height > 2 * padding)
        val scale = min((width - 2 * padding) / bounds.width, (height - 2 * padding) / bounds.height)
        return DrawingViewport(scale, (width - bounds.width * scale) / 2 - bounds.minX * scale,
            (height - bounds.height * scale) / 2 - bounds.minY * scale)
    }

    /** DXF convention: arc runs counterclockwise, including wrap across zero degrees. */
    fun arcPoints(arc: Primitive.Arc): List<Point2D> {
        val raw = arc.endAngleDeg - arc.startAngleDeg
        val sweep = if (abs(raw) >= 360.0) 360.0 else ((raw % 360.0) + 360.0) % 360.0
        val count = max(1, ceil(sweep / 3.0).toInt())
        return (0..count).map {
            val angle = Math.toRadians(arc.startAngleDeg + sweep * it / count)
            Point2D(arc.center.x + arc.radius * cos(angle), arc.center.y + arc.radius * sin(angle))
        }
    }

    /** Reject invalid numerical input rather than silently omit a primitive. */
    fun validate(scene: DrawingScene) {
        fit(scene.bounds, 100.0, 100.0)
        fun point(p: Point2D) {
            require(p.x.isFinite() && p.y.isFinite()) { "Invalid drawing coordinate" }
            require(p.x >= scene.bounds.minX && p.x <= scene.bounds.maxX &&
                p.y >= scene.bounds.minY && p.y <= scene.bounds.maxY) { "Drawing geometry exceeds declared bounds" }
        }
        fun positive(n: Double) { require(n.isFinite() && n > 0) { "Invalid drawing size" } }
        scene.primitives.forEach { p ->
            positive(p.layer.lineweightMm)
            when (p) {
                is Primitive.Line -> { point(p.p1); point(p.p2) }
                is Primitive.Polyline -> { require(p.points.size >= 2); p.points.forEach(::point) }
                is Primitive.Circle -> {
                    positive(p.radius); point(Point2D(p.center.x - p.radius, p.center.y - p.radius))
                    point(Point2D(p.center.x + p.radius, p.center.y + p.radius))
                }
                is Primitive.Arc -> {
                    positive(p.radius); require(p.startAngleDeg.isFinite() && p.endAngleDeg.isFinite())
                    arcPoints(p).forEach(::point)
                }
                is Primitive.Text -> { point(p.position); positive(p.heightMm); require(p.rotationDeg.isFinite()) }
                is Primitive.Dimension -> { point(p.p1); point(p.p2); point(p.textPosition) }
                is Primitive.RebarDot -> {
                    positive(p.diameterMm)
                    point(Point2D(p.center.x - p.diameterMm / 2, p.center.y - p.diameterMm / 2))
                    point(Point2D(p.center.x + p.diameterMm / 2, p.center.y + p.diameterMm / 2))
                }
                is Primitive.Leader -> { require(p.points.size >= 2); p.points.forEach(::point) }
            }
        }
    }

    fun color(layer: DrawingLayer): Int = when (layer.aciColor) {
        1 -> 0xB3261E
        2 -> 0x705500
        3 -> 0x087F5B
        4 -> 0x006879
        5 -> 0x1959A6
        8 -> 0x6B7280
        else -> 0x1F2937
    }

}
