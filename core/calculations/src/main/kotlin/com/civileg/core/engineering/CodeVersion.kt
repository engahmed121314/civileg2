package com.civileg.core.engineering

import com.civileg.core.calculations.entities.DesignCode

/**
 * A specific edition of a design code (Roadmap §1.2 — Code Version Control).
 *
 * Decouples "which family" ([DesignCode]) from "which edition's parameter
 * set" ([ConcreteCodeParams]). Engines never hardcode a code edition; the
 * edition is selected at the boundary (ViewModel / DI) and resolved to the
 * matching [ConcreteCodeParams] via [CodeVersionRegistry].
 *
 * @param code      the code family this edition belongs to
 * @param edition   short edition tag, e.g. "203-2020", "318-19"
 * @param label     human label, e.g. "ECP 203-2020"
 * @param isDefault whether this is the active edition for its family
 */
data class CodeVersion(
    val code: DesignCode,
    val edition: String,
    val label: String,
    val isDefault: Boolean = false
) {
    /** Stable lookup key, e.g. "ECP:203-2020". */
    val key: String get() = "${code.name}:$edition"
}
