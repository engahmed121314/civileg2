package com.civileg.app.domain.entities

import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Selection intent is distinct from whether the legacy engine uses automatic sizing. */
enum class ColumnReinforcementIntent {
    @SerializedName("AUTOMATIC_ENGINE_LAYOUT") AUTOMATIC_ENGINE_LAYOUT,
    @SerializedName("REQUESTED_LAYOUT") REQUESTED_LAYOUT
}

/** Actual calculation arguments and their input provenance, never the later form values.
 * Dimensions are mm except heightM, forces are kN and moments are kN.m.
 * Freezing a legacy action convention records it; it does not verify that convention.
 */
data class ColumnCalculationInputs(
    @field:SerializedName("widthMm") val widthMm: Double,
    @field:SerializedName("depthMm") val depthMm: Double,
    @field:SerializedName("heightM") val heightM: Double,
    @field:SerializedName("fcuMpa") val fcuMpa: Double,
    @field:SerializedName("fyMpa") val fyMpa: Double,
    @field:SerializedName("enteredAxialLoadKn") val enteredAxialLoadKn: Double,
    @field:SerializedName("axialLoadPassedToEngineKn") val axialLoadPassedToEngineKn: Double,
    @field:SerializedName("momentXKnM") val momentXKnM: Double,
    @field:SerializedName("momentYKnM") val momentYKnM: Double,
    @field:SerializedName("code") val code: DesignCode,
    @field:SerializedName("loadCombination") val loadCombination: LoadCombination?,
    @field:SerializedName("actionBasis") val actionBasis: String,
    @field:SerializedName("appliedAggregateLoadMultiplier") val appliedAggregateLoadMultiplier: Double,
    @field:SerializedName("isCircular") val isCircular: Boolean,
    @field:SerializedName("connectedSlab") val connectedSlab: String,
    @field:SerializedName("hasCap") val hasCap: Boolean,
    @field:SerializedName("preferredDiameterMm") val preferredDiameterMm: Int,
    @field:SerializedName("autoOptimize") val autoOptimize: Boolean,
    @field:SerializedName("manualBarCount") val manualBarCount: Int?,
    @field:SerializedName("isSeismic") val isSeismic: Boolean,
    @field:SerializedName("autoIncludeSelfWeight") val autoIncludeSelfWeight: Boolean,
    @field:SerializedName("reinforcementIntent") val reinforcementIntent: ColumnReinforcementIntent =
        if (autoOptimize && manualBarCount == null) ColumnReinforcementIntent.AUTOMATIC_ENGINE_LAYOUT
        else ColumnReinforcementIntent.REQUESTED_LAYOUT
)

/** One immutable completed column input/output revision. This is not method approval.
 * JSON capture freezes nested mutable collections; each read returns a fresh copy.
 * All UI, capture, save, PDF, BBS, BOQ and DXF consumers must use this same revision.
 */
class ColumnDesignSnapshot private constructor(
    @field:SerializedName("schemaVersion") val schemaVersion: Int,
    @field:SerializedName("revisionSha256") val revisionSha256: String,
    @field:SerializedName("inputJson") val inputJson: String,
    @field:SerializedName("resultJson") val resultJson: String
) {
    fun input(): ColumnCalculationInputs {
        require(hasValidFingerprint()) { "Unsupported or inconsistent completed Column revision" }
        return gson.fromJson(inputJson, ColumnCalculationInputs::class.java)
    }
    fun result(): CalculatorEngine.ColumnResult {
        require(hasValidFingerprint()) { "Unsupported or inconsistent completed Column revision" }
        return gson.fromJson(resultJson, CalculatorEngine.ColumnResult::class.java)
    }
    fun hasValidFingerprint(): Boolean = runCatching {
        schemaVersion == VERSION && revisionSha256 == fingerprint(inputJson, resultJson)
    }.getOrDefault(false)

    companion object {
        private const val VERSION = 1
        private val gson = Gson()
        fun restoreStored(schemaVersion: Int, revisionSha256: String, inputJson: String, resultJson: String): ColumnDesignSnapshot =
            ColumnDesignSnapshot(schemaVersion, revisionSha256, inputJson, resultJson).also {
                require(it.hasValidFingerprint()) { "Unsupported or inconsistent completed Column revision" }
            }
        fun capture(input: ColumnCalculationInputs, result: CalculatorEngine.ColumnResult): ColumnDesignSnapshot {
            require(input.code == result.code) { "Column input and output code must match" }
            val i = gson.toJson(input)
            val r = gson.toJson(result)
            return ColumnDesignSnapshot(VERSION, fingerprint(i, r), i, r)
        }
        private fun fingerprint(i: String, r: String): String {
            val bytes = "civileg/column-completed/$VERSION\n${i.length}:$i${r.length}:$r"
                .toByteArray(StandardCharsets.UTF_8)
            return MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}
