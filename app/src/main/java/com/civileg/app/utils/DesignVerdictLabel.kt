package com.civileg.app.utils

import com.civileg.app.db.DesignType

/**
 * Verdict text for a stored design in exports.
 *
 * Column, beam and slab rows store one Boolean. It does not say whether false meant "a check
 * failed" or "the design is incomplete", and a true does not mean the whole design was verified.
 * So for those types the export never prints SAFE or UNSAFE. Other types keep a plain pass/fail
 * because their single flag is a real component result.
 */
object DesignVerdictLabel {
    private val incompleteCapable = setOf(
        DesignType.COLUMN, DesignType.BEAM, DesignType.SLAB, DesignType.HORDI_SLAB,
        DesignType.FLAT_SLAB, DesignType.WAFFLE_SLAB, DesignType.FOOTING, DesignType.COMBINED_FOOTING
    )

    fun label(type: DesignType, storedFlag: Boolean): String =
        if (type in incompleteCapable) {
            if (storedFlag) "Preliminary: stored component checks passed, overall design not confirmed"
            else "Not confirmed: stored flag is false and the record does not say whether a check failed or the design was incomplete"
        } else {
            if (storedFlag) "Checks passed" else "A check did not pass"
        }
}
