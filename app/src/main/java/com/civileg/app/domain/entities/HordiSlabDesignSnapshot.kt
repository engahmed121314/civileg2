package com.civileg.app.domain.entities

import com.civileg.core.engine.rc.ecp.Ecp203RibbedSection
import com.google.gson.Gson

/** Entered section inputs. Lengths are mm, strengths MPa, forces kN/rib and kN.m/rib. */
data class HordiSlabInputs(
    val fcu: Double,
    val fy: Double,
    val ribWidth: Double,
    val ribSpacing: Double,
    val totalThickness: Double,
    val toppingThickness: Double,
    val span: Double,
    val designMoment: Double,
    val designShear: Double,
    /** Retained legacy selector; never refactors actions in the source ECP path. */
    val loadCombination: LoadCombination,
    val designCode: String,
    val sectionContext: Ecp203RibbedSection.Context?,
    val actionBasis: String = if (designCode == "ECP" && sectionContext != null)
        "SUPPLIED_FACTORED_SECTION_ACTIONS_NO_REFACTOR" else "LEGACY_UNVERIFIED_INPUT_CONVENTION",
    /** Null for historical/unrelated layouts. Explicit economy is never inferred. */
    val fourBarArrangementPolicy: com.civileg.core.engine.rc.ecp.Ecp203FourBarArrangement.Policy? = null
)

/**
 * Immutable completed input/result pair. Consumers decode fresh copies, never the
 * current form. The fingerprint identifies the exact pair, not a member name.
 * No transverse panel width, bar length or whole-member quantity is inferred.
 * Persistence must represent absent quantities as unknown, not measured zero.
 */
class HordiSlabDesignSnapshot private constructor(val record: FrozenDesignRecord) {
    val schemaVersion: Int get() = record.schemaVersion
    val revisionSha256: String get() = record.revisionSha256
    val inputJson: String get() = record.inputJson
    val resultJson: String get() = record.resultJson

    fun input(): HordiSlabInputs = gson.fromJson(inputJson, HordiSlabInputs::class.java)
    fun result(): SlabDesignResult = gson.fromJson(resultJson, SlabDesignResult::class.java)
    fun hasValidFingerprint(): Boolean = record.hasValidFingerprint()

    companion object {
        private val gson = Gson()

        fun capture(input: HordiSlabInputs, result: SlabDesignResult): HordiSlabDesignSnapshot {
            // Serializing now also freezes mutable lists supplied by a caller.
            return HordiSlabDesignSnapshot(FrozenDesignRecord.capture(gson.toJson(input), gson.toJson(result)))
        }
    }
}
