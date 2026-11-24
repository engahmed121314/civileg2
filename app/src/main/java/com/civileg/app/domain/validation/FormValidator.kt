package com.civileg.app.domain.validation

/**
 * Pure (Android-free) form validation used by every design page.
 *
 * Rules:
 *  - A field is never replaced by a silent default. Blank or unparsable input is an issue.
 *  - Only physical rules live here (finite number, > 0, >= 0, whole number). Code limits
 *    (fcu/fy ranges, riser/tread limits, ...) must come from a cited source and are passed
 *    in by the caller as [FieldSpec.min]/[FieldSpec.max] or [CrossRule], never invented here.
 *  - Fields that belong to an optional group are validated and returned ONLY when that group
 *    is active. Values typed earlier into an inactive group are ignored, not contributed.
 */
enum class FieldKind { POSITIVE, NON_NEGATIVE, POSITIVE_INT, FINITE }

enum class IssueCode { BLANK, NOT_A_NUMBER, NOT_POSITIVE, NEGATIVE, NOT_WHOLE, OUT_OF_RANGE, CROSS_FIELD }

/** [group] null = mandatory field. [min]/[max] are inclusive and optional. */
data class FieldSpec(
    val id: String,
    val raw: String,
    val kind: FieldKind,
    val group: String? = null,
    val min: Double? = null,
    val max: Double? = null
)

/** Relation between fields. Evaluated only when every field in [dependsOn] is valid and active. */
class CrossRule(
    val fieldId: String,
    val dependsOn: Set<String>,
    val messageKey: String,
    val holds: (Map<String, Double>) -> Boolean
)

data class FieldIssue(
    val fieldId: String,
    val code: IssueCode,
    val min: Double? = null,
    val max: Double? = null,
    val messageKey: String? = null
)

data class ValidationResult(
    val values: Map<String, Double>,
    val issues: List<FieldIssue>
) {
    val isValid: Boolean get() = issues.isEmpty()
    /** Issues are in spec order, so this is the first invalid field as laid out on screen. */
    val firstInvalidId: String? get() = issues.firstOrNull()?.fieldId

    fun value(id: String): Double =
        values[id] ?: throw IllegalStateException("No validated value for '$id'")

    fun valueOrNull(id: String): Double? = values[id]
}

object FormValidator {

    private val NUMBER = Regex("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?$")

    /**
     * Strict decimal parse. Accepts Western, Arabic-Indic and Extended Arabic-Indic digits,
     * the Arabic decimal separator, and a single comma as decimal mark. Rejects NaN, Infinity,
     * hex floats, type suffixes ("5d"), thousands separators and anything non-finite.
     */
    fun parseNumber(raw: String): Double? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when (ch) {
                    in '\u0660'..'\u0669' -> ('0' + (ch - '\u0660'))
                    in '\u06F0'..'\u06F9' -> ('0' + (ch - '\u06F0'))
                    '\u066B' -> '.'
                    else -> ch
                }
            )
        }
        s = sb.toString()
        if (s.count { it == ',' } == 1 && !s.contains('.')) s = s.replace(',', '.')
        if (!NUMBER.matches(s)) return null
        val v = s.toDoubleOrNull() ?: return null
        return if (v.isFinite()) v else null
    }

    fun validate(
        specs: List<FieldSpec>,
        activeGroups: Set<String> = emptySet(),
        crossRules: List<CrossRule> = emptyList()
    ): ValidationResult {
        val values = LinkedHashMap<String, Double>()
        val issues = ArrayList<FieldIssue>()
        val order = specs.map { it.id }

        for (spec in specs) {
            if (spec.group != null && spec.group !in activeGroups) continue // inactive: excluded
            if (spec.raw.isBlank()) {
                issues += FieldIssue(spec.id, IssueCode.BLANK); continue
            }
            val v = parseNumber(spec.raw)
            if (v == null) {
                issues += FieldIssue(spec.id, IssueCode.NOT_A_NUMBER); continue
            }
            val kindIssue = when (spec.kind) {
                FieldKind.POSITIVE -> if (v > 0.0) null else IssueCode.NOT_POSITIVE
                FieldKind.NON_NEGATIVE -> if (v >= 0.0) null else IssueCode.NEGATIVE
                FieldKind.POSITIVE_INT ->
                    if (v != Math.floor(v)) IssueCode.NOT_WHOLE
                    else if (v <= 0.0) IssueCode.NOT_POSITIVE else null
                FieldKind.FINITE -> null
            }
            if (kindIssue != null) {
                issues += FieldIssue(spec.id, kindIssue); continue
            }
            if ((spec.min != null && v < spec.min) || (spec.max != null && v > spec.max)) {
                issues += FieldIssue(spec.id, IssueCode.OUT_OF_RANGE, spec.min, spec.max); continue
            }
            values[spec.id] = v
        }

        for (rule in crossRules) {
            if (!rule.dependsOn.all { it in values }) continue
            if (!rule.holds(values)) {
                issues += FieldIssue(rule.fieldId, IssueCode.CROSS_FIELD, messageKey = rule.messageKey)
            }
        }
        // One issue per field (first wins), reported in on-screen order.
        val firstPerField = LinkedHashMap<String, FieldIssue>()
        for (i in issues) firstPerField.putIfAbsent(i.fieldId, i)
        val sorted = firstPerField.values.sortedBy { order.indexOf(it.fieldId).let { ix -> if (ix < 0) Int.MAX_VALUE else ix } }
        // A field that failed must not leak a value into the result.
        val clean = values.filterKeys { it !in firstPerField }
        return ValidationResult(clean, sorted)
    }
}
