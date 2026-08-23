package com.civileg.core.calculations.utils

import com.civileg.core.calculations.entities.*
import kotlin.math.*

/**
 * المحرك الموحد لهندسة الكمرات - مصدر الحقيقة الوحيد للرسم.
 */
object BeamGeometryEngine {

    fun buildBeamSection(width: Double, depth: Double, reinforcement: ReinforcementResult): DrawingGeometry {
        val polygons = mutableListOf<List<Point2D>>()
        val rebarLines = mutableListOf<RebarLine>()
        val dimensions = mutableListOf<DimensionLine>()

        // 1. رسم مقطع الخرسانة
        polygons.add(listOf(
            Point2D(0.0, 0.0),
            Point2D(width, 0.0),
            Point2D(width, depth),
            Point2D(0.0, depth)
        ))

        // 2. توزيع الأسياخ (توزيع تلقائي بسيط للمثال)
        val cover = 40.0
        val effectiveW = width - 2 * cover
        val spacing = if (reinforcement.numberOfBars > 1) effectiveW / (reinforcement.numberOfBars - 1) else 0.0
        
        for (i in 0 until reinforcement.numberOfBars) {
            val posX = cover + i * spacing
            val posY = depth - cover
            rebarLines.add(RebarLine(
                start = Point2D(posX, posY),
                end = Point2D(posX, posY), // Point rebar
                diameter = reinforcement.barDiameter,
                label = "${reinforcement.barDiameter}"
            ))
        }

        // 3. إضافة الأبعاد
        dimensions.add(DimensionLine(Point2D(0.0, -20.0), Point2D(width, -20.0), "$width mm"))

        return DrawingGeometry(polygons, rebarLines, dimensions)
    }
}
