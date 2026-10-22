package com.civileg.app.ui.compose.components.editor

import androidx.compose.ui.geometry.Offset
import kotlin.math.round

/**
 * Bidirectional coordinate transformation between world (meters) and screen (pixels)
 * with zoom, pan, and grid snapping support.
 */
class CoordinateMapper(
    val pixelsPerMeter: Float,     // Base pixels per meter at scale 1.0
    val canvasWidth: Float,        // Canvas width in pixels
    val canvasHeight: Float,       // Canvas height in pixels
    val cameraOffsetX: Float,      // Camera horizontal pan offset in pixels
    val cameraOffsetY: Float,      // Camera vertical pan offset in pixels
    val cameraScale: Float,        // Zoom scale (1.0 = default)
    val gridSpacing: Double        // Active grid spacing in meters
) {
    // Screen center
    private val centerX = canvasWidth / 2f
    private val centerY = canvasHeight / 2f

    /** World (meters) to Screen (pixels) */
    fun toScreen(worldX: Double, worldY: Double): Offset {
        val sx = (worldX * pixelsPerMeter * cameraScale + centerX + cameraOffsetX).toFloat()
        val sy = (-worldY * pixelsPerMeter * cameraScale + centerY + cameraOffsetY).toFloat()
        return Offset(sx, sy)
    }

    /** Screen (pixels) to World (meters) */
    fun toWorld(screenX: Float, screenY: Float): Pair<Double, Double> {
        val wx = (screenX - centerX - cameraOffsetX) / (pixelsPerMeter * cameraScale)
        val wy = -(screenY - centerY - cameraOffsetY) / (pixelsPerMeter * cameraScale)
        return Pair(wx.toDouble(), wy.toDouble())
    }

    /** Snap single value to nearest grid spacing */
    fun snapToGrid(worldVal: Double): Double {
        if (gridSpacing <= 0) return worldVal
        return round(worldVal / gridSpacing) * gridSpacing
    }

    /** Snap 2D coordinates to grid */
    fun snapToGrid(worldX: Double, worldY: Double): Pair<Double, Double> {
        return Pair(snapToGrid(worldX), snapToGrid(worldY))
    }

    companion object {
        fun computePixelsPerMeter(
            nodesX: List<Double>, nodesY: List<Double>,
            canvasW: Float, canvasH: Float, paddingFactor: Float = 0.92f
        ): Float {
            if (nodesX.isEmpty() || nodesY.isEmpty()) return 100f // 100 px/m default
            val xMin = nodesX.minOrNull() ?: 0.0
            val xMax = nodesX.maxOrNull() ?: 0.0
            val yMin = nodesY.minOrNull() ?: 0.0
            val yMax = nodesY.maxOrNull() ?: 0.0
            val xSpan = maxOf(xMax - xMin, 1.0)
            val ySpan = maxOf(yMax - yMin, 1.0)
            return minOf(canvasW / xSpan.toFloat(), canvasH / ySpan.toFloat()) * paddingFactor
        }

        /** Determine active grid spacing based on visible pixel size (between 8px and 80px) */
        fun computeActiveGridSpacing(pixelsPerMeter: Float, cameraScale: Float): Double {
            val spacings = doubleArrayOf(0.001, 0.01, 0.05, 0.1, 0.5, 1.0, 5.0, 10.0)
            for (spacing in spacings) {
                val visiblePx = spacing * pixelsPerMeter * cameraScale
                if (visiblePx in 8.0..80.0) return spacing
            }
            return 1.0
        }
    }
}
