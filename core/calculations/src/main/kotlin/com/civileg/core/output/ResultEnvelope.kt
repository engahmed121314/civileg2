package com.civileg.core.output

import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.ElementType

/**
 * Single safety check item.
 */
data class DesignCheck(
    val name: String,
    val demand: Double,
    val capacity: Double,
    val unit: String,
    val utilization: Double = if (capacity != 0.0) demand / capacity else 1.0,
    val passed: Boolean = utilization <= 1.0,
    val clause: String? = null
)

/**
 * A single calculation step with formula and substitution.
 */
data class CalcStep(
    val symbol: String,
    val formula: String,
    val substitution: String,
    val value: Double,
    val unit: String,
    val clause: String? = null
)

/**
 * Design warning message.
 */
data class DesignWarning(
    val code: String,
    val message: String,
    val isCritical: Boolean = false
)

/**
 * Single Source of Truth result container for all structural design calculations (§7.2).
 */
data class ResultEnvelope<T>(
    val element: ElementType,
    val code: DesignCode,
    val codeVersion: String = code.version,
    val inputs: Map<String, String>,
    val result: T,
    val checks: List<DesignCheck>,
    val assumptions: List<String> = emptyList(),
    val warnings: List<DesignWarning> = emptyList(),
    val trace: List<CalcStep> = emptyList()
) {
    val isSafe: Boolean
        get() = checks.all { it.passed } && warnings.none { it.isCritical }

    val maxUtilization: Double
        get() = checks.maxOfOrNull { it.utilization } ?: 0.0
}
