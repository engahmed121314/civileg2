package com.civileg.core.calculations.entities

/**
 * بيانات هندسية موحدة للرسم (Canvas/PDF)
 */
data class DrawingGeometry(
    val mainPolygons: List<List<Point2D>>, // أشكال الخرسانة
    val rebarLines: List<RebarLine>,        // خطوط الحديد
    val dimensions: List<DimensionLine>    // خطوط الأبعاد
)

data class Point2D(val x: Double, val y: Double)

data class RebarLine(
    val start: Point2D,
    val end: Point2D,
    val diameter: Double,
    val label: String
)

data class DimensionLine(
    val start: Point2D,
    val end: Point2D,
    val value: String,
    val id: String? = null,
    val unit: String = "mm",
    val codeReference: String? = null,
    val associatedBar: String? = null
)
