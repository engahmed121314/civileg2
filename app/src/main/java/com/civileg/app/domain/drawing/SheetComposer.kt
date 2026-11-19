package com.civileg.app.domain.drawing

/**
 * Arranges structural views, sections, details, BBS, legend, and title block onto an A3 Landscape sheet (420 x 297 mm).
 */
object SheetComposer {
    const val SHEET_WIDTH_MM = 420.0
    const val SHEET_HEIGHT_MM = 297.0
    const val MARGIN_MM = 10.0

    val STANDARD_SCALES = listOf(10.0, 20.0, 25.0, 50.0, 100.0, 200.0)

    /**
     * Determines the optimal standard scale factor (1:N) for a view box to fit within a target rectangle.
     */
    fun selectScale(viewWidthMm: Double, viewHeightMm: Double, targetWidthMm: Double, targetHeightMm: Double): Double {
        val scaleX = viewWidthMm / targetWidthMm
        val scaleY = viewHeightMm / targetHeightMm
        val maxScale = maxOf(scaleX, scaleY)

        return STANDARD_SCALES.firstOrNull { it >= maxScale } ?: 200.0
    }

    /**
     * Creates a title block primitive set for A3 Landscape.
     */
    fun buildTitleBlock(
        projectTitle: String,
        elementTitle: String,
        memberMark: String,
        codeVersion: String,
        scaleText: String,
        date: String = "2026-11-18",
        appVersion: String = "v1.7.0"
    ): List<Primitive> {
        val primitives = mutableListOf<Primitive>()
        val tbMinX = SHEET_WIDTH_MM - 140.0
        val tbMinY = MARGIN_MM
        val tbMaxX = SHEET_WIDTH_MM - MARGIN_MM
        val tbMaxY = MARGIN_MM + 45.0

        primitives.add(Primitive.Polyline(
            points = listOf(
                Point2D(tbMinX, tbMinY), Point2D(tbMaxX, tbMinY),
                Point2D(tbMaxX, tbMaxY), Point2D(tbMinX, tbMaxY)
            ),
            isClosed = true,
            layer = DrawingLayer.TITLE_BLOCK
        ))

        primitives.add(Primitive.Text("PROJECT: $projectTitle", Point2D(tbMinX + 5, tbMaxY - 8), 3.5, DrawingLayer.TITLE_BLOCK))
        primitives.add(Primitive.Text("ELEMENT: $elementTitle ($memberMark)", Point2D(tbMinX + 5, tbMaxY - 18), 3.5, DrawingLayer.TITLE_BLOCK))
        primitives.add(Primitive.Text("CODE: $codeVersion | SCALE: $scaleText", Point2D(tbMinX + 5, tbMaxY - 28), 3.0, DrawingLayer.TITLE_BLOCK))
        primitives.add(Primitive.Text("DATE: $date | CIVIL ENGINEER PRO $appVersion", Point2D(tbMinX + 5, tbMaxY - 38), 2.5, DrawingLayer.TITLE_BLOCK))

        return primitives
    }
}
