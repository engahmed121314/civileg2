package com.civileg.app.ui.compose.screens

/**
 * Locale-aware presentation for hordi/joist result fields that can be
 * NOT EVALUATED (blocked clauses, e.g. SBC SJ-7 shear). UI follows the app
 * language; exports are always English (ADR-005/009) via the export helpers.
 */
object HordiResultPresentation {

    fun shearCapacityText(shearCapacityKn: Double?, arabic: Boolean): String =
        shearCapacityKn?.let { String.format("%.1f kN", it) }
            ?: if (arabic) "غير مقيّمة — فحص القص معلّق" else "NOT EVALUATED — shear check blocked"

    fun utilizationText(utilization: Double?, arabic: Boolean): String =
        utilization?.let { String.format("%.0f%%", it * 100) }
            ?: if (arabic) "غير مقيّمة" else "N/E"

    fun statusText(isSafe: Boolean, evaluated: Boolean, arabic: Boolean): String = when {
        !evaluated -> if (arabic) "غير مكتمل ✗" else "INCOMPLETE ✗"
        isSafe -> if (arabic) "آمن ✓" else "PASS ✓"
        else -> if (arabic) "غير آمن ✗" else "FAIL ✗"
    }

    // ── Export (always English) ──
    fun exportShearCapacity(shearCapacityKn: Double?): String =
        shearCapacityKn?.let { String.format("%.1f kN", it) } ?: "NOT EVALUATED (shear blocked)"

    fun exportUtilization(utilization: Double?): String =
        utilization?.let { String.format("%.0f%%", it * 100) } ?: "N/E"
}
