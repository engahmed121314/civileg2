package com.civileg.app.domain.drawing

import kotlin.math.cos
import kotlin.math.sin

/**
 * Label placement request specifying anchor, dimensions, priority, and text.
 */
data class LabelRequest(
    val id: String,
    val text: String,
    val widthMm: Double,
    val heightMm: Double,
    val anchor: Point2D,
    val priority: Int, // Lower number = higher priority
    val allowLeader: Boolean = true
)

/**
 * Result of label placement algorithm.
 */
data class PlacedLabel(
    val id: String,
    val text: String,
    val rect: Rect2D,
    val leaderPoints: List<Point2D>? = null,
    val isLegendFallback: Boolean = false
)

/**
 * Pure Kotlin label collision detection and placement engine (§12.5).
 */
class LabelPlacer(
    private val obstacles: List<Rect2D>,
    private val bounds: Rect2D
) {
    /**
     * Places labels avoiding obstacles and previously placed labels.
     */
    fun placeLabels(requests: List<LabelRequest>): List<PlacedLabel> {
        val placed = mutableListOf<PlacedLabel>()
        val occupiedRects = obstacles.toMutableList()

        val sortedRequests = requests.sortedBy { it.priority }

        val anglesDeg = listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)
        val radiiMm = listOf(0.0, 6.0, 12.0, 24.0)

        for (req in sortedRequests) {
            var bestRect: Rect2D? = null
            var bestLeader: List<Point2D>? = null

            for (r in radiiMm) {
                if (bestRect != null) break
                for (angle in anglesDeg) {
                    val rad = Math.toRadians(angle)
                    val offsetX = r * cos(rad)
                    val offsetY = r * sin(rad)
                    val candidateCenter = Point2D(req.anchor.x + offsetX, req.anchor.y + offsetY)
                    val candidateRect = Rect2D(
                        minX = candidateCenter.x - req.widthMm / 2.0,
                        minY = candidateCenter.y - req.heightMm / 2.0,
                        maxX = candidateCenter.x + req.widthMm / 2.0,
                        maxY = candidateCenter.y + req.heightMm / 2.0
                    )

                    if (candidateRect.minX >= bounds.minX && candidateRect.maxX <= bounds.maxX &&
                        candidateRect.minY >= bounds.minY && candidateRect.maxY <= bounds.maxY) {
                        val hasCollision = occupiedRects.any { it.intersects(candidateRect) }
                        if (!hasCollision) {
                            bestRect = candidateRect
                            if (r > 0.0 && req.allowLeader) {
                                bestLeader = listOf(req.anchor, candidateCenter)
                            }
                            break
                        }
                    }
                }
            }

            if (bestRect != null) {
                occupiedRects.add(bestRect)
                placed.add(PlacedLabel(req.id, req.text, bestRect, bestLeader))
            } else {
                val fallbackRect = Rect2D(req.anchor.x, req.anchor.y, req.anchor.x + req.widthMm, req.anchor.y + req.heightMm)
                placed.add(PlacedLabel(req.id, req.text, fallbackRect, isLegendFallback = true))
            }
        }

        return placed
    }
}
