package com.civileg.app.db

import com.civileg.app.domain.entities.DesignCode
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Non-destructive read policy. A historical SAFE bit is not method verification. */
object StoredDesignSafety {
    enum class Status { UNCHANGED, ECP_HORDI_HOLD, HORDI_CODE_UNRESOLVED, EXPLICIT_METHOD_HOLD }

    private fun json(raw: String): JsonObject? = runCatching {
        JsonParser.parseString(raw).takeIf { it.isJsonObject }?.asJsonObject
    }.getOrNull()

    private fun text(json: JsonObject?, key: String): String =
        runCatching { json?.get(key)?.asString.orEmpty() }.getOrDefault("")

    private fun codes(value: String): Set<DesignCode> = DesignCode.entries.filter { code ->
        value.equals(code.displayName, ignoreCase = true) ||
            Regex("\\b${code.name}\\b", RegexOption.IGNORE_CASE).containsMatchIn(value)
    }.toSet()

    fun status(design: Design): Status {
        val result = json(design.results)
        val input = json(design.inputData)
        if (runCatching { result?.get("safetyAssessmentBlocked")?.asBoolean == true }.getOrDefault(false)) {
            return Status.EXPLICIT_METHOD_HOLD
        }
        val hordi = design.type == DesignType.HORDI_SLAB ||
            (design.type == DesignType.SLAB &&
                (text(result, "type") == "HOLLOW_BLOCK" || text(input, "type") == "HOLLOW_BLOCK"))
        if (!hordi) return Status.UNCHANGED

        // Actual writers store enum code in shared results, displayName in
        // codeUsed, and some legacy dedicated records only say ECP/ACI.
        // Intersect compatible evidence; do not guess from a name or date.
        val evidence = listOf(
            codes(text(result, "code")), codes(text(input, "code")),
            codes(text(input, "designCode")), codes(design.codeUsed)
        ).filter { it.isNotEmpty() }
        val resolved = evidence.reduceOrNull { a, b -> a intersect b }.orEmpty()
        return when {
            resolved.size != 1 -> Status.HORDI_CODE_UNRESOLVED
            resolved.single() == DesignCode.ECP -> Status.ECP_HORDI_HOLD
            else -> Status.UNCHANGED
        }
    }

    fun isBlocked(design: Design): Boolean = status(design) != Status.UNCHANGED

    /** Keep the stored JSON and original database row intact. */
    fun forDisplay(design: Design): Design =
        if (isBlocked(design) && design.isSafe) design.copy(isSafe = false) else design
}
