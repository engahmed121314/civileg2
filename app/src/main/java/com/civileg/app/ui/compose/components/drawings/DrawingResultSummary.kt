package com.civileg.app.ui.compose.components.drawings

import com.civileg.app.domain.entities.GenericSafetyCheck

/**
 * Comprehensive summary of calculation results that drawings can render.
 * Bridges the architectural gap where drawings received only geometric primitives
 * and could not display safetyChecks, utilizationRatio, material quantities, or code notes.
 *
 * Every Professional*Drawing composable should accept an optional `resultSummary`
 * parameter of this type. Call sites should populate it from their *Result data class.
 *
 * Enhanced (Round 4) with:
 * - Punching shear ratio and status
 * - Deflection ratio and status
 * - Axial load ratio for columns
 * - Crack width for serviceability checks
 * - Formwork area for BOQ integration
 */
data class DrawingResultSummary(
    val isSafe: Boolean = true,
    val utilizationRatio: Double = 0.0,
    val concreteVolume: Double = 0.0,       // m³
    val steelWeight: Double = 0.0,          // kg
    val safetyChecks: List<GenericSafetyCheck> = emptyList(),
    val codeNotes: List<String> = emptyList(),
    val designCodeLabel: String = "",        // e.g. "ECP 203", "ACI 318", "SBC 304"
    // ── Round 4 additions ──
    val punchingShearRatio: Double = 0.0,   // V_ed / V_rd (should be ≤ 1.0)
    val deflectionRatio: Double = 0.0,      // actual / allowable (should be ≤ 1.0)
    val axialLoadRatio: Double = 0.0,       // P_u / φP_n for columns (should be ≤ 1.0)
    val crackWidth: Double = 0.0,           // mm (serviceability check)
    val formworkArea: Double = 0.0          // m² (for BOQ/costing)
) {
    /** Formatted utilization percentage, e.g. "65.3%" */
    val utilizationPercent: String get() = "%.1f%%".format(utilizationRatio * 100.0)

    /** PASS/FAIL label for safety status */
    val safetyLabel: String get() = if (isSafe) "PASS" else "FAIL"

    /** Number of failed safety checks */
    val failedCheckCount: Int get() = safetyChecks.count { !it.passed }

    /** Formatted punching shear status */
    val punchingShearStatus: String get() = when {
        punchingShearRatio <= 0.0 -> "N/A"
        punchingShearRatio <= 1.0 -> "OK (%.2f)".format(punchingShearRatio)
        else -> "FAIL (%.2f)".format(punchingShearRatio)
    }

    /** Formatted deflection status */
    val deflectionStatus: String get() = when {
        deflectionRatio <= 0.0 -> "N/A"
        deflectionRatio <= 1.0 -> "OK (%.2f)".format(deflectionRatio)
        else -> "FAIL (%.2f)".format(deflectionRatio)
    }

    /** Formatted axial load ratio status (for columns) */
    val axialLoadStatus: String get() = when {
        axialLoadRatio <= 0.0 -> "N/A"
        axialLoadRatio <= 1.0 -> "OK (%.2f)".format(axialLoadRatio)
        else -> "OVERSTRESSED (%.2f)".format(axialLoadRatio)
    }

    /** Formatted crack width status */
    val crackWidthStatus: String get() = when {
        crackWidth <= 0.0 -> "N/A"
        crackWidth <= 0.3 -> "OK (%.2f mm)".format(crackWidth)
        else -> "EXCESSIVE (%.2f mm)".format(crackWidth)
    }
}
