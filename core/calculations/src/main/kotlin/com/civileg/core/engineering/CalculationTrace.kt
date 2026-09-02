package com.civileg.core.engineering

/**
 * Calculation trace (spec §15, §62) — every check reports
 * INPUT → FORMULA → SUBSTITUTION → RESULT → LIMIT → UTILIZATION → STATUS.
 *
 * NOT_CHECKED is distinct from PASS: a check that never ran must never be
 * reported as passing (spec rule "no PASS without a real check").
 */
enum class CheckStatus { PASS, WARNING, FAIL, NOT_CHECKED }

data class TraceEntry(
    val title: String,
    val formula: String,
    val substitution: String,
    val result: String,
    val limit: String,
    val utilization: Double?,
    val status: CheckStatus,
    val codeReference: String? = null
)

class CalculationTrace {
    private val entries = mutableListOf<TraceEntry>()

    fun add(
        title: String,
        formula: String,
        substitution: String,
        result: String,
        limit: String,
        status: CheckStatus,
        utilization: Double? = null,
        codeReference: String? = null
    ): TraceEntry {
        val e = TraceEntry(title, formula, substitution, result, limit, utilization, status, codeReference)
        entries += e
        return e
    }

    val all: List<TraceEntry> get() = entries.toList()

    /** Worst-case overall status: FAIL > WARNING > NOT_CHECKED > PASS. */
    val overall: CheckStatus
        get() = when {
            entries.any { it.status == CheckStatus.FAIL } -> CheckStatus.FAIL
            entries.any { it.status == CheckStatus.WARNING } -> CheckStatus.WARNING
            entries.any { it.status == CheckStatus.NOT_CHECKED } -> CheckStatus.NOT_CHECKED
            else -> CheckStatus.PASS
        }

    fun count(status: CheckStatus) = entries.count { it.status == status }
}
