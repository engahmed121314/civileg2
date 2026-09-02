package com.civileg.core.math

import kotlin.math.PI
import kotlin.math.abs

/**
 * 2D geometry & section properties kernel (PHASE 03, spec §9).
 *
 * Polygon properties use the standard shoelace / signed-area formulas:
 *   A   = |Σ (xᵢ·yᵢ₊₁ − xᵢ₊₁·yᵢ)| / 2
 *   Cx  = Σ (xᵢ + xᵢ₊₁)(xᵢ·yᵢ₊₁ − xᵢ₊₁·yᵢ) / (6A)
 *   Ix  = Σ (yᵢ² + yᵢ·yᵢ₊₁ + yᵢ₊₁²)·cross / 12      (global axes)
 *   Ixy = Σ (xᵢ·yᵢ₊₁ + 2xᵢ·yᵢ + 2xᵢ₊₁·yᵢ₊₁ + xᵢ₊₁·yᵢ)·cross / 24
 * Centroidal values via parallel-axis theorem. Vertices must form a simple,
 * consistently-wound polygon; degenerate area is an error, not zero.
 */
data class Point2D(val x: Double, val y: Double)

class PolygonSection(private val vertices: List<Point2D>) {

    init {
        require(vertices.size >= 3) { "PolygonSection: needs >=3 vertices" }
        require(abs(signedAreaTwice()) > SafeMath.EPS) { "PolygonSection: degenerate (zero) area" }
    }

    private fun signedAreaTwice(): Double {
        var s = 0.0
        for (i in vertices.indices) {
            val a = vertices[i]
            val b = vertices[(i + 1) % vertices.size]
            s += a.x * b.y - b.x * a.y
        }
        return s
    }

    /** Absolute geometric area (mm² when vertices are mm). */
    val area: Double get() = abs(signedAreaTwice()) / 2.0

    private data class Crossed(val index: Int, val cross: Double)

    private inline fun sumByCross(block: (i: Int, cross: Double) -> Double): Double {
        var s = 0.0
        for (i in vertices.indices) {
            val a = vertices[i]
            val b = vertices[(i + 1) % vertices.size]
            s += block(i, a.x * b.y - b.x * a.y)
        }
        return s
    }

    val centroidX: Double get() = sumByCross { i, cr -> (vertices[i].x + at(i + 1).x) * cr } / (6.0 * signedArea() )
    val centroidY: Double get() = sumByCross { i, cr -> (vertices[i].y + at(i + 1).y) * cr } / (6.0 * signedArea() )

    private fun signedArea(): Double = signedAreaTwice() / 2.0

    private fun at(i: Int) = vertices[i % vertices.size]

    /** Second moment about the global X axis (winding-independent). */
    val ixGlobal: Double = abs(
        sumByCross { i, cr ->
            val y1 = vertices[i].y; val y2 = at(i + 1).y
            (y1 * y1 + y1 * y2 + y2 * y2) * cr
        } / 12.0
    )

    /** Second moment about the global Y axis (winding-independent). */
    val iyGlobal: Double = abs(
        sumByCross { i, cr ->
            val x1 = vertices[i].x; val x2 = at(i + 1).x
            (x1 * x1 + x1 * x2 + x2 * x2) * cr
        } / 12.0
    )

    /** Product of inertia about global axes (sign follows orientation). */
    val ixyGlobal: Double =
        sumByCross { i, cr ->
            val a = vertices[i]; val b2 = at(i + 1)
            (a.x * b2.y + 2.0 * a.x * a.y + 2.0 * b2.x * b2.y + b2.x * a.y) * cr
        } / 24.0

    val ixCentroidal: Double get() = ixGlobal - area * centroidY * centroidY
    val iyCentroidal: Double get() = iyGlobal - area * centroidX * centroidX
    val ixyCentroidal: Double get() = ixyGlobal - area * centroidX * centroidY
}

/** Exact closed-form rectangular section (width b × depth h, mm). */
data class RectangleSection(val b: Double, val h: Double) {
    init { SafeMath.requirePositive(b, "b"); SafeMath.requirePositive(h, "h") }
    val area: Double = b * h
    val ix: Double = b * h * h * h / 12.0
    val iy: Double = h * b * b * b / 12.0
    /** Elastic section moduli. */
    val sx: Double = b * h * h / 6.0
    val sy: Double = h * b * b / 6.0

    fun toPolygon(origin: Point2D = Point2D(0.0, 0.0)) = PolygonSection(listOf(
        Point2D(origin.x, origin.y),
        Point2D(origin.x + b, origin.y),
        Point2D(origin.x + b, origin.y + h),
        Point2D(origin.x, origin.y + h)
    ))
}

/** Exact closed-form circular section (diameter d, mm). */
data class CircleSection(val d: Double) {
    init { SafeMath.requirePositive(d, "d") }
    val area: Double = PI * d * d / 4.0
    val ix: Double = PI * d * d * d * d / 64.0
    val iy: Double = ix
    val sx: Double = PI * d * d * d / 32.0
}
