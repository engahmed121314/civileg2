package com.civileg.app.utils.tools

/**
 * Pure math for the on-screen ruler.
 *
 * The device-reported density (DisplayMetrics.ydpi) is not reliable on every phone, so a stored calibration
 * factor corrects it: factor = real length / length shown by the ruler for the same reference object.
 */
object RulerMath {
    const val MM_PER_INCH = 25.4

    /** ISO/IEC 7810 ID-1 card (bank card), mm. Used as a calibration reference. */
    const val ID1_CARD_LONG_MM = 85.60
    const val ID1_CARD_SHORT_MM = 53.98

    enum class TickKind { MM, HALF_CM, CM }

    data class Tick(val mm: Int, val kind: TickKind)

    fun pxPerMm(dpi: Float, calibration: Double): Double {
        require(dpi.isFinite() && dpi > 0f) { "dpi must be positive" }
        require(calibration.isFinite() && calibration > 0.0) { "calibration must be positive" }
        return dpi / MM_PER_INCH * calibration
    }

    /**
     * New calibration factor. The ruler currently draws a reference object [shownMm] long (measured with the
     * current factor), but the object is really [realMm]. Since shownMm = px / pxPerMm and pxPerMm is proportional
     * to the factor, the new factor is current * shownMm / realMm: shown > real means the ruler reads too long,
     * so px per mm must grow.
     * Example: ruler says the card is 88.0 mm, it is 85.6: factor = 88.0 / 85.6 times the current factor.
     */
    fun recalibrate(current: Double, realMm: Double, shownMm: Double): Double {
        require(realMm > 0.0 && shownMm > 0.0) { "lengths must be positive" }
        require(current > 0.0) { "current factor must be positive" }
        return current * (shownMm / realMm)
    }

    /** Ticks for 0..[lengthMm] inclusive of whole millimetres. */
    fun ticks(lengthMm: Double): List<Tick> {
        if (!lengthMm.isFinite() || lengthMm < 0.0) return emptyList()
        val n = lengthMm.toInt()
        return (0..n).map { mm ->
            Tick(mm, when {
                mm % 10 == 0 -> TickKind.CM
                mm % 5 == 0 -> TickKind.HALF_CM
                else -> TickKind.MM
            })
        }
    }

    fun mmToPx(mm: Double, pxPerMm: Double) = mm * pxPerMm
    fun pxToMm(px: Double, pxPerMm: Double) = px / pxPerMm

    /** A stored factor is used only if it is a positive finite number; otherwise the ruler is "uncalibrated" (factor 1.0, with a visible notice). No range is assumed. */
    fun validStoredFactor(raw: Float): Double? = if (raw.isFinite() && raw > 0f) raw.toDouble() else null
}
