package com.civileg.core.calculations.entities

/**
 * Unified verdict for any design result (governance rule 1.4 — no silent failure).
 *
 * PASS    → all checks safe, no warnings
 * WARNING → safe but warnings present (e.g. minimum steel applied, high utilization)
 * FAIL    → at least one strength/stability check not satisfied
 * ERROR   → calculation aborted (invalid input / solver failure) — result numbers are NOT trustworthy
 */
enum class DesignStatus {
    PASS,
    WARNING,
    FAIL,
    ERROR;

    val isAcceptable: Boolean get() = this == PASS || this == WARNING

    companion object {
        /** Derive the verdict from the common result fields used across engines. */
        fun of(isSafe: Boolean?, warnings: List<String>? = null, errorMessage: String? = null): DesignStatus =
            when {
                errorMessage != null -> ERROR
                isSafe == false -> FAIL
                !warnings.isNullOrEmpty() -> WARNING
                else -> PASS
            }
    }
}

/** Unified accessor for the app-wide [ReinforcementResult] contract. */
val ReinforcementResult.designStatus: DesignStatus
    get() = DesignStatus.of(isSafe = isSafe, warnings = warnings)
