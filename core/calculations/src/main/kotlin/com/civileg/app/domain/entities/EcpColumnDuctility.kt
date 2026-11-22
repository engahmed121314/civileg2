package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * ECP 203-2020 Ch6 §6-8 ductility levels for seismic-resistant columns.
 * Terminology is the code's own (§6-8-1-3(ج), printed 6-86): beyond ordinary
 * detailing there are two ductility levels — محدودة (limited) and كافية
 * (adequate). The three-mode selector is a code-derived expansion of the
 * owner's binary ordinary/special ask; no ACI category mapping is implied.
 * Clause log: docs/CLAUSE_LOG.md E-SC-1..E-SC-9 (owner's ECP 203-2020 scan).
 */
@Parcelize
enum class EcpColumnDuctilityMode(val displayNameAr: String, val displayNameEn: String) : Parcelable {
    ORDINARY("عادي", "Ordinary (non-seismic detailing)"),
    LIMITED("ممطولية محدودة", "Limited ductility (ECP 203 §6-8-2-2)"),
    ADEQUATE("ممطولية كافية", "Adequate ductility (ECP 203 §6-8-2-3)")
}

/**
 * A code check that cannot be closed from single-column inputs alone.
 * [key] is a stable identifier so the UI can show localized name/reason while
 * the engine data and PDF exports stay English; unknown keys fall back to the
 * English [name]/[reason].
 */
@Parcelize
data class BlockedSeismicCheck(
    val name: String,
    val clause: String,
    val reason: String,
    val key: String = ""
) : Parcelable

/**
 * One computed ductile-detailing check with its clause citation. [key] serves
 * the same UI-localization role as in [BlockedSeismicCheck].
 */
@Parcelize
data class EcpColumnSeismicCheck(
    val name: String,
    val clause: String,
    val demand: String,
    val limit: String,
    val satisfied: Boolean,
    val key: String = ""
) : Parcelable

/**
 * Ductile detailing result for one ECP column (§6-8). `isComplete` is always
 * false: limited/adequate modes carry frame-context checks (capacity shear,
 * strong-column/weak-beam) that a single-column tool cannot close, so a
 * ductile-mode result must never be presented as an overall design pass or
 * exported as a compliant ductile design.
 */
@Parcelize
data class EcpColumnSeismicDetailing(
    val mode: EcpColumnDuctilityMode,
    /** Lo — critical region length at each column end, §6-8-1-3(و) + fig 6-35 (printed 6-87..6-88). */
    val criticalRegionLengthMm: Double,
    /** S0 — tie spacing inside the critical region, §6-8-2-2-3 (printed 6-92) / §6-8-2-3-2(ي) (printed 6-97). */
    val criticalTieSpacingMm: Double,
    /** First tie from the beam face: ≤ S0/2 (printed 6-92). */
    val firstTieMaxOffsetMm: Double,
    /** Tie spacing outside the critical region: ≤ min(2·S0, 200 mm) (printed 6-92). */
    val elsewhereTieSpacingMm: Double,
    val checks: List<EcpColumnSeismicCheck> = emptyList(),
    val blockedChecks: List<BlockedSeismicCheck> = emptyList(),
    val clauseReferences: List<String> = emptyList(),
    val isComplete: Boolean = false
) : Parcelable
