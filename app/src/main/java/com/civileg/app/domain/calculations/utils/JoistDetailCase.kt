package com.civileg.app.domain.calculations.utils

/** Information that a detailing workflow must collect or explicitly mark
 * not applicable. Missing values stay missing, never become defaults.
 * This is an input/readiness contract, not code compliance or permission to
 * issue a drawing. Each code strategy must still validate applicability.
 */
data class JoistDetailCase(
    val actualRibCount: Int? = null,
    val clearSpanMm: Double? = null,
    val effectiveFlangeWidthMm: Double? = null,
    val leftSupport: Support? = null,
    val rightSupport: Support? = null,
    val loadBasis: LoadBasis? = null,
    val serviceability: Serviceability? = null,
    val detailing: Detailing? = null
) {
    enum class Restraint { SIMPLE, CONTINUOUS, FIXED, FREE }
    enum class SupportMaterial { CONCRETE, MASONRY, OTHER }
    enum class Exposure { PROTECTED_INTERIOR, UNPROTECTED, HARMFUL, CORROSIVE }
    data class Support(val restraint: Restraint, val material: SupportMaterial, val widthMm: Double,
        val ultimateNegativeMomentKnM: Double, val availableAnchorageMm: Double)
    data class LoadBasis(val deadKnM2: Double, val liveKnM2: Double,
        val ultimatePositiveMomentKnMPerRib: Double, val ultimateShearKnPerRib: Double,
        val servicePositiveMomentKnMPerRib: Double, val uniformlyDistributed: Boolean)
    data class Serviceability(val exposure: Exposure, val sustainedLoadFraction: Double,
        val durationMonths: Double, val partitionInstallationMonths: Double?,
        val deflectionSensitivePartitions: Boolean)
    data class Detailing(val coverToLinkOutsideMm: Double, val linkDiameterMm: Double,
        val aggregateSizeMm: Double, val mainBarDiameterMm: Int, val barsPerRib: Int,
        val stockLengthMm: Double, val seismicDetailingRequired: Boolean,
        val solidZoneLeftMm: Double, val solidZoneRightMm: Double)

    fun unresolved(): List<String> = buildList {
        if (actualRibCount == null) add("Actual panel rib count")
        else if (actualRibCount <= 0) add("Actual panel rib count must be positive")
        if (clearSpanMm == null || !clearSpanMm.isFinite() || clearSpanMm <= 0) add("Positive clear span")
        if (effectiveFlangeWidthMm == null || !effectiveFlangeWidthMm.isFinite() || effectiveFlangeWidthMm <= 0) add("Effective flange width and its code basis")
        fun support(label: String, value: Support?) {
            if (value == null) { add("$label support condition, material, width, moment and anchorage space"); return }
            if (!value.widthMm.isFinite() || value.widthMm <= 0) add("$label support width")
            if (!value.ultimateNegativeMomentKnM.isFinite() || value.ultimateNegativeMomentKnM < 0) add("$label negative moment magnitude")
            if (!value.availableAnchorageMm.isFinite() || value.availableAnchorageMm < 0) add("$label available anchorage")
        }
        support("Left", leftSupport); support("Right", rightSupport)
        val loads = loadBasis
        if (loads == null) add("Dead/live load decomposition and ultimate/service demand")
        else if (listOf(loads.deadKnM2, loads.liveKnM2, loads.ultimatePositiveMomentKnMPerRib,
                loads.ultimateShearKnPerRib, loads.servicePositiveMomentKnMPerRib).any { !it.isFinite() || it < 0 })
            add("Finite nonnegative dead/live loads and ultimate/service demand")
        val service = serviceability
        if (service == null) add("Exposure, sustained loading, duration and partition sensitivity")
        else {
            if (!service.sustainedLoadFraction.isFinite() || service.sustainedLoadFraction !in 0.0..1.0) add("Sustained load fraction")
            if (!service.durationMonths.isFinite() || service.durationMonths <= 0) add("Load duration")
            if (service.deflectionSensitivePartitions && service.partitionInstallationMonths == null) add("Partition installation time")
            service.partitionInstallationMonths?.let { if (!it.isFinite() || it < 0 || it > service.durationMonths) add("Valid partition installation time") }
        }
        val detail = detailing
        if (detail == null) add("Actual cover, link/main bars, aggregate, stock length, seismic and solid zones")
        else {
            if (listOf(detail.coverToLinkOutsideMm, detail.linkDiameterMm, detail.solidZoneLeftMm, detail.solidZoneRightMm).any { !it.isFinite() || it < 0 }) add("Nonnegative finite cover, link diameter and solid zones")
            if (!detail.aggregateSizeMm.isFinite() || detail.aggregateSizeMm <= 0) add("Maximum aggregate size")
            if (detail.mainBarDiameterMm !in com.civileg.app.domain.entities.RebarCatalog.COMMERCIAL_DIAMETERS_MM || detail.barsPerRib <= 0) add("Commercial main-bar diameter and positive count")
            if (actualRibCount != null && actualRibCount > 0 && detail.barsPerRib > 0 &&
                actualRibCount.toLong() * detail.barsPerRib > Int.MAX_VALUE) add("Panel bar count exceeds supported range")
            if (!detail.stockLengthMm.isFinite() || detail.stockLengthMm <= 0) add("Stock-bar length")
        }
    }
}
