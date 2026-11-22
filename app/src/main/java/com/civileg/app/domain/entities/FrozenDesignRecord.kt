package com.civileg.app.domain.entities

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Immutable persistence handoff for one completed input/result pair.
 * The fingerprint detects a changed pair; it is not a method approval, a signature,
 * or evidence that quantities or drawings have been checked.
 * Producers serialize actual completed inputs/results before capture.
 */
class FrozenDesignRecord private constructor(
    val schemaVersion: Int,
    val revisionSha256: String,
    val inputJson: String,
    val resultJson: String
) {
    fun hasValidFingerprint(): Boolean = runCatching {
        schemaVersion == SCHEMA_VERSION && inputJson.isNotBlank() && resultJson.isNotBlank() &&
            revisionSha256 == fingerprint(inputJson, resultJson)
    }.getOrDefault(false)

    companion object {
        private const val SCHEMA_VERSION = 1

        fun capture(inputJson: String, resultJson: String): FrozenDesignRecord {
            require(inputJson.isNotBlank() && resultJson.isNotBlank())
            return FrozenDesignRecord(SCHEMA_VERSION, fingerprint(inputJson, resultJson), inputJson, resultJson)
        }

        private fun fingerprint(inputs: String, outputs: String): String {
            val content = "civileg/completed-design/$SCHEMA_VERSION\n${inputs.length}:$inputs${outputs.length}:$outputs"
            return MessageDigest.getInstance("SHA-256")
                .digest(content.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        }
    }
}
