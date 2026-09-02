package com.civileg.core.sanity

/**
 * Severity of a sanity finding produced by [EngineeringSanityEngine].
 * - ERROR   = physically impossible / code-violating output that must not be trusted.
 * - WARNING = suspicious but not necessarily wrong (e.g. near a code limit).
 * - INFO    = informational note.
 */
enum class SanitySeverity { ERROR, WARNING, INFO }

/**
 * A single engineering-sanity finding: an independent re-check that an *output*
 * is physically plausible and code-consistent. These are deliberately separate
 * from the calculation trace — they catch gross errors the trace can miss
 * (NaN, negative area, utilization > 1 that the engine still marked safe,
 * reinforcement ratio outside code limits, demand > capacity).
 */
data class SanityFinding(
    val code: String,
    val severity: SanitySeverity,
    val message: String,
    val value: Double? = null,
    val limit: Double? = null,
    val codeReference: String? = null
)

/**
 * Aggregated sanity report for one calculation result.
 */
data class SanityReport(
    val source: String,
    val findings: List<SanityFinding>
) {
    val hasError: Boolean get() = findings.any { it.severity == SanitySeverity.ERROR }
    val hasWarning: Boolean get() = findings.any { it.severity == SanitySeverity.WARNING }
    val ok: Boolean get() = !hasError

    /** Map onto the existing [com.civileg.core.engineering.CheckStatus] vocabulary. */
    val status: com.civileg.core.engineering.CheckStatus
        get() = when {
            hasError -> com.civileg.core.engineering.CheckStatus.FAIL
            hasWarning -> com.civileg.core.engineering.CheckStatus.WARNING
            else -> com.civileg.core.engineering.CheckStatus.PASS
        }

    /** Human-readable warnings/errors for UI/logging (excludes INFO). */
    val warnings: List<String>
        get() = findings.filter { it.severity != SanitySeverity.INFO }
            .map { "[${it.code}] ${it.message}" }
}
