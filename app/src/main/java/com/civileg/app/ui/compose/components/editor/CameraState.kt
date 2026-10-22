package com.civileg.app.ui.compose.components.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.civileg.app.domain.entities.FrameNode
import kotlin.math.min

/**
 * Camera state tracking zoom scale and pan offset.
 */
class CameraState {
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)
    var scale by mutableFloatStateOf(1.0f)

    companion object {
        const val MIN_SCALE = 0.01f
        const val MAX_SCALE = 100.0f
    }

    /** Apply zoom keeping pivot point fixed */
    fun zoom(zoomFactor: Float, pivotX: Float, pivotY: Float) {
        val newScale = (scale * zoomFactor).coerceIn(MIN_SCALE, MAX_SCALE)
        val scaleRatio = newScale / scale
        offsetX = pivotX - (pivotX - offsetX) * scaleRatio
        offsetY = pivotY - (pivotY - offsetY) * scaleRatio
        scale = newScale
    }

    /** Apply translation pan */
    fun pan(dx: Float, dy: Float) {
        offsetX += dx
        offsetY += dy
    }

    /** Fit all frame nodes into canvas */
    fun fitAll(
        nodes: List<FrameNode>,
        pixelsPerMeter: Float,
        canvasW: Float, canvasH: Float
    ) {
        if (nodes.isEmpty()) {
            offsetX = 0f
            offsetY = 0f
            scale = 1f
            return
        }
        val xMin = nodes.minOf { it.x }
        val xMax = nodes.maxOf { it.x }
        val yMin = nodes.minOf { it.y }
        val yMax = nodes.maxOf { it.y }
        val xSpan = maxOf(xMax - xMin, 1.0)
        val ySpan = maxOf(yMax - yMin, 1.0)
        val fitScale = min(
            (canvasW / (xSpan * pixelsPerMeter)).toFloat(),
            (canvasH / (ySpan * pixelsPerMeter)).toFloat()
        ) * 0.85f
        scale = fitScale.coerceIn(MIN_SCALE, MAX_SCALE)
        val centerX = (xMin + xMax) / 2.0
        val centerY = (yMin + yMax) / 2.0
        offsetX = (-centerX * pixelsPerMeter * scale).toFloat()
        offsetY = (centerY * pixelsPerMeter * scale).toFloat()
    }

    /** Reset camera */
    fun reset() {
        offsetX = 0f
        offsetY = 0f
        scale = 1f
    }
}
