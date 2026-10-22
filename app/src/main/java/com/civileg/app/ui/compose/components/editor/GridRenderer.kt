package com.civileg.app.ui.compose.components.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Multi-level dynamic grid renderer with viewport culling.
 */
object GridRenderer {
    private val gridLevels = listOf(
        GridLevel(0.001, Color(0xFFF5F5F5), 0.5f),  // 1mm
        GridLevel(0.01,  Color(0xFFEEEEEE), 0.5f),  // 10mm
        GridLevel(0.05,  Color(0xFFE0E0E0), 0.5f),  // 50mm
        GridLevel(0.1,   Color(0xFFBDBDBD), 1.0f),  // 100mm
        GridLevel(0.5,   Color(0xFFB0B0B0), 1.0f),  // 500mm
        GridLevel(1.0,   Color(0xFF9E9E9E), 1.5f),  // 1m
        GridLevel(5.0,   Color(0xFF888888), 1.5f),  // 5m
        GridLevel(10.0,  Color(0xFF757575), 2.0f)   // 10m
    )
    private val axisColor = Color(0xFF1565C0) // Blue for origin axes
    private const val AXIS_WIDTH = 2.0f

    private data class GridLevel(val spacing: Double, val color: Color, val strokeWidth: Float)

    fun DrawScope.drawGrid(mapper: CoordinateMapper) {
        val ppm = mapper.pixelsPerMeter
        val camScale = mapper.cameraScale

        // Compute viewport bounds in world coordinates (meters)
        val (worldLeft, worldTop) = mapper.toWorld(0f, 0f)
        val (worldRight, worldBottom) = mapper.toWorld(size.width, size.height)

        val visibleMinX = minOf(worldLeft, worldRight) - 1.0
        val visibleMaxX = maxOf(worldLeft, worldRight) + 1.0
        val visibleMinY = minOf(worldBottom, worldTop) - 1.0
        val visibleMaxY = maxOf(worldBottom, worldTop) + 1.0

        for (level in gridLevels) {
            val visiblePx = level.spacing * ppm * camScale
            if (visiblePx < 8.0 || visiblePx > 80.0) continue // Dynamic level visibility filter

            // Vertical lines (X = const)
            val xStart = floor(visibleMinX / level.spacing).toInt()
            val xEnd = ceil(visibleMaxX / level.spacing).toInt()
            for (i in xStart..xEnd) {
                val x = i * level.spacing
                val start = mapper.toScreen(x, visibleMaxY)
                val end = mapper.toScreen(x, visibleMinY)
                val isAxis = (i == 0)
                drawLine(
                    color = if (isAxis) axisColor else level.color,
                    start = start,
                    end = end,
                    strokeWidth = if (isAxis) AXIS_WIDTH else level.strokeWidth
                )
            }

            // Horizontal lines (Y = const)
            val yStart = floor(visibleMinY / level.spacing).toInt()
            val yEnd = ceil(visibleMaxY / level.spacing).toInt()
            for (i in yStart..yEnd) {
                val y = i * level.spacing
                val start = mapper.toScreen(visibleMinX, y)
                val end = mapper.toScreen(visibleMaxX, y)
                val isAxis = (i == 0)
                drawLine(
                    color = if (isAxis) axisColor else level.color,
                    start = start,
                    end = end,
                    strokeWidth = if (isAxis) AXIS_WIDTH else level.strokeWidth
                )
            }
        }
    }
}
