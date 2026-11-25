package com.civileg.app.utils

import java.util.Locale

/** Display formatting that never prints Infinity, NaN or an Int overflow as an engineering value. */
object DesignNumberFormat {
    const val NOT_DETERMINED = "not determined (section inadequate)"

    /** Steel area in mm2; non-finite means no section can satisfy the check as modelled. */
    fun areaMm2(value: Double, decimals: Int = 1): String =
        if (value.isFinite()) String.format(Locale.US, "%.${decimals}f mm²", value) else NOT_DETERMINED

    /** Utilization ratio as a percentage; non-finite is shown as exceeding capacity, not as a number. */
    fun percent(ratio: Double, decimals: Int = 0): String =
        if (ratio.isFinite()) String.format(Locale.US, "%.${decimals}f%%", ratio * 100.0) else "exceeds capacity (not computable)"

    /** Progress fraction for bars and rings, clamped to 0..1 and safe for non-finite input. */
    fun progress(ratio: Double): Float = if (ratio.isFinite()) ratio.coerceIn(0.0, 1.0).toFloat() else 1f
}
