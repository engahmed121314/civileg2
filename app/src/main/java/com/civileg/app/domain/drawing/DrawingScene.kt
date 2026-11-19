package com.civileg.app.domain.drawing

/**
 * Standard DXF Layer definitions with ACI color index, line weight, and line type.
 */
data class DrawingLayer(
    val name: String,
    val aciColor: Int, // 1=Red, 2=Yellow, 3=Green, 4=Cyan, 5=Blue, 7=White/Black, 8=Gray
    val lineweightMm: Double = 0.25,
    val linetype: String = "CONTINUOUS"
) {
    companion object {
        val OUTLINE = DrawingLayer("0_OUTLINE", 7, 0.50)
        val REBAR_BOTTOM = DrawingLayer("0_REBAR_BOTTOM", 3, 0.40)
        val REBAR_TOP = DrawingLayer("0_REBAR_TOP", 5, 0.40)
        val REBAR_STIRRUP = DrawingLayer("0_REBAR_STIRRUP", 1, 0.30)
        val DIMENSION = DrawingLayer("0_DIMENSION", 2, 0.18)
        val TEXT = DrawingLayer("0_TEXT", 7, 0.25)
        val HATCH = DrawingLayer("0_HATCH", 8, 0.13)
        val CENTERLINE = DrawingLayer("0_CENTERLINE", 1, 0.18, "CENTER")
        val TITLE_BLOCK = DrawingLayer("0_TITLE_BLOCK", 7, 0.50)
    }
}

/**
 * 2D Point in drawing coordinate space.
 */
data class Point2D(val x: Double, val y: Double)

/**
 * 2D Bounding Rectangle.
 */
data class Rect2D(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
    val centerX: Double get() = (minX + maxX) / 2.0
    val centerY: Double get() = (minY + maxY) / 2.0

    /**
     * Checks if two rectangles intersect.
     */
    fun intersects(other: Rect2D): Boolean {
        return !(minX >= other.maxX || maxX <= other.minX || minY >= other.maxY || maxY <= other.minY)
    }
}

/**
 * Primitive drawing elements for DrawingScene.
 */
sealed class Primitive {
    abstract val layer: DrawingLayer

    /**
     * 2D Line primitive.
     */
    data class Line(val p1: Point2D, val p2: Point2D, override val layer: DrawingLayer) : Primitive()

    /**
     * 2D Polyline primitive.
     */
    data class Polyline(val points: List<Point2D>, val isClosed: Boolean, override val layer: DrawingLayer) : Primitive()

    /**
     * 2D Circle primitive.
     */
    data class Circle(val center: Point2D, val radius: Double, override val layer: DrawingLayer) : Primitive()

    /**
     * 2D Arc primitive.
     */
    data class Arc(val center: Point2D, val radius: Double, val startAngleDeg: Double, val endAngleDeg: Double, override val layer: DrawingLayer) : Primitive()

    /**
     * Text callout primitive.
     */
    data class Text(
        val text: String,
        val position: Point2D,
        val heightMm: Double,
        override val layer: DrawingLayer,
        val halo: Boolean = true,
        val pillBackground: Boolean = false,
        val rotationDeg: Double = 0.0
    ) : Primitive()

    /**
     * Dimension line primitive.
     */
    data class Dimension(
        val p1: Point2D,
        val p2: Point2D,
        val textPosition: Point2D,
        val text: String,
        override val layer: DrawingLayer = DrawingLayer.DIMENSION
    ) : Primitive()

    /**
     * Rebar dot cross-section primitive.
     */
    data class RebarDot(
        val center: Point2D,
        val diameterMm: Double,
        override val layer: DrawingLayer
    ) : Primitive()

    /**
     * Leader callout primitive.
     */
    data class Leader(
        val points: List<Point2D>,
        val text: String,
        override val layer: DrawingLayer = DrawingLayer.TEXT
    ) : Primitive()
}

/**
 * Immutable DrawingScene containing primitives, bounds, layers, and title metadata.
 */
data class DrawingScene(
    val title: String,
    val bounds: Rect2D,
    val primitives: List<Primitive> = emptyList(),
    val layers: List<DrawingLayer> = listOf(
        DrawingLayer.OUTLINE, DrawingLayer.REBAR_BOTTOM, DrawingLayer.REBAR_TOP,
        DrawingLayer.REBAR_STIRRUP, DrawingLayer.DIMENSION, DrawingLayer.TEXT,
        DrawingLayer.HATCH, DrawingLayer.CENTERLINE, DrawingLayer.TITLE_BLOCK
    )
) {
    fun primitiveCount(): Int = primitives.size
    fun layerNames(): Set<String> = primitives.map { it.layer.name }.toSet()
}
