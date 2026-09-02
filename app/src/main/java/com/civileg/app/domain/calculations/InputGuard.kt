package com.civileg.app.domain.calculations

/**
 * Uniform loud-failure guard for engineering inputs (governance rule 1.4).
 *
 * Every engine entry point validates its geometry/materials through [positive]
 * before any arithmetic — a zero/negative/NaN dimension now aborts the design
 * with an explicit bilingual message that surfaces in the UI error state,
 * instead of producing Infinity/NaN and a plausible-looking (or false-SAFE) result.
 */
object InputGuard {

    /**
     * Require every named value to be finite and > 0.
     * @throws IllegalArgumentException listing ALL offending fields at once.
     */
    fun positive(vararg namedValues: Pair<String, Double>) {
        val bad = namedValues.filter { (name, v) -> v.isNaN() || v.isInfinite() || v <= 0.0 }
        require(bad.isEmpty()) {
            val list = bad.joinToString("; ") { "${it.first} = ${it.second}" }
            "Invalid input → قيم غير صالحة: $list (all dimensions/materials must be greater than 0)"
        }
    }

    /** Require count-type inputs to be ≥ 1. */
    fun atLeastOne(name: String, value: Int) {
        require(value >= 1) {
            "Invalid input → $name must be at least 1 | $name يجب أن يكون 1 على الأقل"
        }
    }
}
