package com.civileg.app.domain.calculations.utils

import com.civileg.app.domain.entities.RebarCatalog

/** Physical bars adopted by a design, not defaults inferred from a drawing.
 * Every end extension is explicit. A missing code/edition/clause keeps a group
 * unverified; a numerical schedule alone is never an engineering approval.
 */
data class AdoptedBarGroup(
    val mark: String,
    val role: String,
    val diameterMm: Int,
    val barsPerMember: Int,
    val membersCount: Int,
    val bodyLengthMm: Double,
    val startExtensionMm: Double,
    val endExtensionMm: Double,
    val startStationMm: Double,
    val endStationMm: Double,
    val code: String,
    val edition: String?,
    val clause: String?,
    val shapeDescription: String,
    val verified: Boolean = false
) {
    init {
        require(mark.isNotBlank() && role.isNotBlank() && code.isNotBlank())
        require(RebarCatalog.isValidDiameter(diameterMm))
        require(barsPerMember > 0 && membersCount > 0)
        require(barsPerMember.toLong() * membersCount <= Int.MAX_VALUE)
        require(bodyLengthMm.isFinite() && bodyLengthMm > 0)
        require(startExtensionMm.isFinite() && startExtensionMm >= 0)
        require(endExtensionMm.isFinite() && endExtensionMm >= 0)
        require(startStationMm.isFinite() && endStationMm.isFinite() && endStationMm > startStationMm)
        require((bodyLengthMm + startExtensionMm + endExtensionMm).isFinite())
        require((endStationMm - startStationMm).isFinite())
        require(shapeDescription.isNotBlank())
        require(!verified || (!edition.isNullOrBlank() && !clause.isNullOrBlank()))
    }
    val totalCount: Int get() = barsPerMember * membersCount
    val cutLengthMm: Double get() = bodyLengthMm + startExtensionMm + endExtensionMm
    val totalLengthM: Double get() = cutLengthMm / 1000 * totalCount
    val totalWeightKg: Double get() = totalLengthM * RebarCatalog.unitWeightKgPerM(diameterMm)
    val areaPerMemberMm2: Double get() = RebarCatalog.totalAreaMm2(barsPerMember, diameterMm)
}

/** One case, one code, one schedule. Never mix a group from another code. */
class AdoptedReinforcementSchedule(val code: String, groups: List<AdoptedBarGroup>) {
    // Defensive snapshot: later changes to a caller's MutableList must not
    // change code identity, quantities, duplicate marks or readiness.
    val groups: List<AdoptedBarGroup> = java.util.Collections.unmodifiableList(groups.toList())
    init {
        require(code.isNotBlank())
        require(groups.all { it.code == code })
        require(groups.map { it.mark }.distinct().size == groups.size)
    }
    val totalWeightKg: Double get() = groups.sumOf { it.totalWeightKg }
    val executionReady: Boolean get() = groups.isNotEmpty() && groups.all { it.verified }
}
