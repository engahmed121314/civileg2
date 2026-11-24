package com.civileg.app.utils.tools

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Pure math for the sensor spirit level (flat, screen-up mode).
 *
 * Input is the gravity-direction vector as Android reports it: accelerometer or TYPE_GRAVITY,
 * device axes, +z out of the screen. Lying flat and face-up it reads about (0, 0, +9.81).
 * Sign convention of the results: tiltX > 0 means the right edge is higher, tiltY > 0 means the top edge is higher.
 */
object SpiritLevelMath {

    data class Tilt(val xDeg: Double, val yDeg: Double)

    /** Rotate device-axis components into screen axes. [rotationDegrees] is the display rotation: 0, 90, 180 or 270. */
    fun toScreenAxes(ax: Double, ay: Double, rotationDegrees: Int): Pair<Double, Double> = when (((rotationDegrees % 360) + 360) % 360) {
        0 -> ax to ay
        90 -> -ay to ax
        180 -> -ax to -ay
        270 -> ay to -ax
        else -> throw IllegalArgumentException("rotation must be a multiple of 90, got $rotationDegrees")
    }

    /**
     * Tilt angles in degrees, or null when the vector has no usable direction (zero length, non-finite,
     * or no component along gravity because the device is not roughly flat).
     */
    fun tilt(ax: Double, ay: Double, az: Double): Tilt? {
        if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return null
        val g = sqrt(ax * ax + ay * ay + az * az)
        if (g == 0.0) return null
        // asin(component / |g|) is the exact angle of each device axis from the horizontal plane.
        fun deg(c: Double) = Math.toDegrees(asin((c / g).coerceIn(-1.0, 1.0)))
        return Tilt(deg(ax), deg(ay))
    }

    /** True when the device is closer to face-up than face-down. A face-down device cannot be read as a flat level. */
    fun isFaceUp(az: Double): Boolean = az > 0.0

    /** Apply a stored zero offset (degrees) measured on a surface the user treats as level. */
    fun withZero(t: Tilt, zeroX: Double, zeroY: Double) = Tilt(t.xDeg - zeroX, t.yDeg - zeroY)

    /** Slope in percent (rise per 100 run) for a tilt angle. */
    fun slopePercent(deg: Double): Double = tan(Math.toRadians(deg)) * 100.0

    /**
     * Exponential smoothing used only for display steadiness. [alpha] in (0, 1]; 1 = no smoothing.
     * It does not change where the reading settles.
     */
    fun smooth(previous: Double?, sample: Double, alpha: Double): Double {
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0,1]" }
        return if (previous == null) sample else previous + alpha * (sample - previous)
    }

    /** Bubble offset in [-1, 1] of the dial radius. The bubble moves to the high side. [fullScaleDeg] is the tilt that pins it to the rim. */
    fun bubbleOffset(tiltDeg: Double, fullScaleDeg: Double): Double {
        require(fullScaleDeg > 0.0) { "fullScaleDeg must be > 0" }
        return (tiltDeg / fullScaleDeg).coerceIn(-1.0, 1.0)
    }

    /** The readout the user sees: one decimal. "Level" is true only when that readout is 0.0 on both axes, no hidden tolerance. */
    fun displayedLevel(t: Tilt): Boolean = abs(round1(t.xDeg)) == 0.0 && abs(round1(t.yDeg)) == 0.0

    fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
}
