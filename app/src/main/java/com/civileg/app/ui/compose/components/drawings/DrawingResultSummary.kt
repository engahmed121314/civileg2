package com.civileg.app.ui.compose.components.drawings

import com.civileg.app.domain.entities.GenericSafetyCheck

/**
 * Lightweight summary of calculation results that drawings can optionally render.
 * This bridges the architectural gap where drawings received only geometric primitives
 * and could not display safetyChecks, utilizationRatio, material quantities, or code notes.
 *
 * Every Professional*Drawing composable should accept an optional `resultSummary`
 * parameter of this type. Call sites should populate it from their *Result data class.
 */
data class DrawingResultSummary(
    val isSafe: Boolean = true,
    val utilizationRatio: Double = 0.0,
    val concreteVolume: Double = 0.0,       // m³
    val steelWeight: Double = 0.0,          // kg
    val safetyChecks: List<GenericSafetyCheck> = emptyList(),
    val codeNotes: List<String> = emptyList(),
    val designCodeLabel: String = ""        // e.g. "ECP 203", "ACI 318", "SBC 304"
) {
    /** Formatted utilization percentage, e.g. "65.3%" */
    val utilizationPercent: String get() = "%.1f%%".format(utilizationRatio * 100.0)

    /** PASS/FAIL label for safety status */
    val safetyLabel: String get() = if (isSafe) "PASS" else "FAIL"

    /** Number of failed safety checks */
    val failedCheckCount: Int get() = safetyChecks.count { !it.passed }
}
