package com.civileg.core.sanity

import kotlin.math.abs

/**
 * Engineering sanity checker — validates calculation results for physical plausibility.
 * Previously a no-op stub that returned empty reports.
 */
object EngineeringSanityEngine {

    /** Check a generic result map for engineering sanity violations. */
    fun check(results: Map<String, Double>, category: String = "General"): SanityReport {
        val items = mutableListOf<SanityItem>()

        // Check for NaN or Infinity in all values
        for ((key, value) in results) {
            if (value.isNaN()) {
                items.add(SanityItem(SanityLevel.ERROR, "$key is NaN — calculation produced invalid result"))
            } else if (value.isInfinite()) {
                items.add(SanityItem(SanityLevel.ERROR, "$key is Infinite — possible division by zero"))
            }
        }

        // Utilization ratio checks (if present)
        val utilizationRatio = results["utilizationRatio"]
        if (utilizationRatio != null && utilizationRatio > 1.0) {
            items.add(SanityItem(SanityLevel.ERROR,
                "Utilization ratio $utilizationRatio > 1.0 — element is OVER-STRESSED"))
        } else if (utilizationRatio != null && utilizationRatio > 0.9) {
            items.add(SanityItem(SanityLevel.WARNING,
                "Utilization ratio $utilizationRatio > 0.9 — element is near capacity"))
        }

        // Reinforcement ratio checks
        val requiredReinf = results["requiredReinforcement"]
        val providedReinf = results["providedReinforcement"]
        if (requiredReinf != null && providedReinf != null && requiredReinf > 0 && providedReinf < requiredReinf) {
            items.add(SanityItem(SanityLevel.ERROR,
                "Provided reinforcement ($providedReinf) < required ($requiredReinf) — UNDER-REINFORCED"))
        }

        // Negative dimension checks
        for ((key, value) in results) {
            if (key.contains("thickness", ignoreCase = true) ||
                key.contains("width", ignoreCase = true) ||
                key.contains("depth", ignoreCase = true) ||
                key.contains("diameter", ignoreCase = true) ||
                key.contains("area", ignoreCase = true)) {
                if (value < 0) {
                    items.add(SanityItem(SanityLevel.ERROR, "$key = $value is negative — invalid geometry"))
                }
            }
        }

        val hasError = items.any { it.level == SanityLevel.ERROR }
        val hasWarning = items.any { it.level == SanityLevel.WARNING }

        return SanityReport(category, items, hasError, hasWarning)
    }

    /** Legacy overload for backward compatibility. */
    fun check(any: Any): SanityReport = SanityReport("General", emptyList())
}

data class SanityReport(
    val category: String,
    val items: List<SanityItem>,
    val hasError: Boolean = false,
    val hasWarning: Boolean = false
)

data class SanityItem(val level: SanityLevel, val message: String)

enum class SanityLevel { ERROR, WARNING, INFO }
