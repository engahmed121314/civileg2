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

    /** English export input rows; SBC cylinder is required, never silently converted (SJ-8). */
    fun exportInputsMap(
        fcu: Double, fy: Double, ribWidth: Double, ribSpacing: Double,
        totalThickness: Double, toppingThickness: Double, span: Double,
        designMoment: Double, designShear: Double, cylinderStrength: Double?,
        loadCombinationDescription: String, designCode: String
    ): Map<String, String> = mapOf(
        "Rib Width" to "$ribWidth mm",
        "Rib Spacing" to "$ribSpacing mm",
        "Total Thickness" to "$totalThickness mm",
        "Topping Thickness" to "$toppingThickness mm",
        "Span" to "${span / 1000.0} m",
        "Design Moment" to "${"%.2f".format(designMoment)} kN.m/rib",
        "Design Shear" to "${"%.2f".format(designShear)} kN/rib",
        (if (designCode == "SBC") "f'c (cylinder)" else "f'cu") to
            "${if (designCode == "SBC") cylinderStrength?.toString() ?: "MISSING" else fcu.toString()} MPa",
        "fy" to "$fy MPa",
        "Load Combination" to loadCombinationDescription,
        // Full edition label: cover/design basis must name the implemented edition,
        // never a bare "SBC" (2018 copy is the implemented basis; no 2024 claim).
        "Design Code" to if (designCode == "SBC") "SBC 304-CR-2018" else designCode
    )

    /** English export result rows; not-evaluated fields stay explicit, never 0 or a fake pass. */
    fun exportResultsMap(
        requiredReinforcement: Double, providedReinforcement: Double,
        barDiameter: Double, barSpacing: Double, minThickness: Double,
        shearCapacity: Double?, utilizationRatio: Double?, isSafe: Boolean
    ): Map<String, String> = mapOf(
        "Required As" to "${"%.2f".format(requiredReinforcement)} mm²",
        "Provided As" to "${"%.2f".format(providedReinforcement)} mm²",
        "Bar Diameter" to "$barDiameter mm",
        "Rib Spacing" to "$barSpacing mm",
        "Adopted Total Thickness" to "$minThickness mm",
        "Shear Capacity" to exportShearCapacity(shearCapacity),
        "Utilization" to exportUtilization(utilizationRatio),
        "Is Safe" to if (isSafe) "YES" else "NO"
    )
}
