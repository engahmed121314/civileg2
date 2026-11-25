package com.civileg.app.utils

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

/** Display format for converted values. Keeps 8 significant digits so small results are not shown as 0. */
object UnitConversionFormat {
    private const val SIGNIFICANT_DIGITS = 8
    private const val PLAIN_MIN = 1e-4
    private const val PLAIN_MAX = 1e9

    fun format(value: Double): String {
        if (!value.isFinite()) return "-"
        if (value == 0.0) return "0"
        val a = Math.abs(value)
        return if (a >= PLAIN_MIN && a < PLAIN_MAX) {
            BigDecimal(value).round(MathContext(SIGNIFICANT_DIGITS)).stripTrailingZeros().toPlainString()
        } else {
            String.format(Locale.ROOT, "%.7e", value)
        }
    }
}
