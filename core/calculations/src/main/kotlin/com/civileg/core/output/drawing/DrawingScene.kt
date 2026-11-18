package com.civileg.core.output.drawing

/**
 * CAD Layer definition for multi-backend rendering.
 */
data class DrawingLayer(
    val name: String,
    val aciColor: Int = 7,         // AutoCAD color index (1=Red, 2=Yellow, 3=Green, 4=Cyan, 5=Blue, 7=White)
    val lineweight: Double = 0.25, // mm
    val linetype: String = "CONTINUOUS"
) {
    companion object {
        val CONCRETE = DrawingLayer("0_CONCRETE", aciColor = 7, lineweight = 0.50)
        val REBAR_MAIN = DrawingLayer("0_REBAR_MAIN", aciColor = 1, lineweight = 0.35)
        val REBAR_STIRRUP = DrawingLayer("0_REBAR_STIRRUP", aciColor = 3, lineweight = 0.25)
        val DIMENSION = DrawingLayer("0_DIMENSION", aciColor = 2, lineweight = 0.18)
        val TEXT = DrawingLayer("0_TEXT", aciColor = 4, lineweight = 0.25)
        val HATCH = DrawingLayer("0_HATCH", aciColor = 8, lineweight = 0.13)
        val DEFAULT = DrawingLayer("0_DEFAULT", aciColor = 7, lineweight = 0.25)
    }
}

/**
 * Geometric primitives for DrawingScene.
 */
sealed interface DrawingPrimitive {
    val layer: DrawingLayer

    data class Line(
        val x1: Double, val y1: Double,
        val x2: Double, val y2: Double,
        override val layer: DrawingLayer = DrawingLayer.CONCRETE
    ) : DrawingPrimitive

    data class Polyline(
        val points: List<Pair<Double, Double>>,
        val isClosed: Boolean = false,
        override val layer: DrawingLayer = DrawingLayer.CONCRETE
    ) : DrawingPrimitive

    data class Circle(
        val cx: Double, val cy: Double,
        val radius: Double,
        override val layer: DrawingLayer = DrawingLayer.REBAR_MAIN
    ) : DrawingPrimitive

    data class Arc(
        val cx: Double, val cy: Double,
        val radius: Double,
        val startAngle: Double,
        val endAngle: Double,
        override val layer: DrawingLayer = DrawingLayer.CONCRETE
    ) : DrawingPrimitive

    data class Text(
        val text: String,
        val x: Double, val y: Double,
        val height: Double = 2.5,
        override val layer: DrawingLayer = DrawingLayer.TEXT,
        val alignment: String = "LEFT"
    ) : DrawingPrimitive

    data class Hatch(
        val boundary: List<Pair<Double, Double>>,
        val pattern: String = "ANSI31",
        override val layer: DrawingLayer = DrawingLayer.HATCH
    ) : DrawingPrimitive

    data class Dimension(
        val x1: Double, val y1: Double,
        val x2: Double, val y2: Double,
        val text: String,
        override val layer: DrawingLayer = DrawingLayer.DIMENSION
    ) : DrawingPrimitive

    data class RebarDot(
        val cx: Double, val cy: Double,
        val diameter: Double = 16.0,
        override val layer: DrawingLayer = DrawingLayer.REBAR_MAIN
    ) : DrawingPrimitive

    data class Stirrup(
        val x1: Double, val y1: Double,
        val x2: Double, val y2: Double,
        val diameter: Double = 8.0,
        val hookLength: Double = 75.0,
        override val layer: DrawingLayer = DrawingLayer.REBAR_STIRRUP
    ) : DrawingPrimitive

    data class Table(
        val x: Double, val y: Double,
        val rows: List<List<String>>,
        override val layer: DrawingLayer = DrawingLayer.TEXT
    ) : DrawingPrimitive
}

/**
 * A single view section (Plan, Elevation, Cross Section) in a drawing sheet.
 */
data class DrawingView(
    val name: String,
    val scale: String = "1:50",
    val primitives: List<DrawingPrimitive> = emptyList()
)

/**
 * Single Source of Truth drawing scene for screen Compose rendering, PDF generation, and DXF export (§6.1).
 */
data class DrawingScene(
    val sheetTitle: String = "STRUCTURAL DRAWING",
    val codeName: String = "ECP 203",
    val dateText: String = "2026",
    val scaleText: String = "1:50",
    val views: List<DrawingView> = emptyList(),
    val layers: List<DrawingLayer> = listOf(
        DrawingLayer.CONCRETE,
        DrawingLayer.REBAR_MAIN,
        DrawingLayer.REBAR_STIRRUP,
        DrawingLayer.DIMENSION,
        DrawingLayer.TEXT,
        DrawingLayer.HATCH
    )
) {
    val totalPrimitivesCount: Int
        get() = views.sumOf { it.primitives.size }
}
