package com.civileg.core.calculations.entities

/**
 * Represent a single step in the engineering calculation process.
 * [Phase 3] Calculation Transparency.
 */
data class CalculationStep(
    val title: String,               // e.g. "Effective Depth"
    val formula: String,             // e.g. "d = h - cover - phi/2"
    val substitution: String,        // e.g. "500 - 40 - 16/2"
    val result: Double,              // e.g. 452.0
    val unit: String,                // e.g. "mm"
    val limit: Double? = null,       // e.g. 200.0 (optional)
    val limitType: LimitType? = null,
    val isSafe: Boolean = true
)

enum class LimitType { MIN, MAX, EXACT }

/**
 * A collection of steps forming a complete design trace.
 */
data class DesignTrace(
    val steps: List<CalculationStep> = emptyList()
) {
    /** Helper to find the numeric result of a step by its title. */
    fun getValue(title: String): Double? = steps.find { it.title.contains(title, ignoreCase = true) }?.result
}
