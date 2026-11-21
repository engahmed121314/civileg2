package com.civileg.app.domain.entities

import kotlin.math.abs

/**
 * Bar Role in structural elements.
 */
enum class BarRole(val titleEn: String, val titleAr: String) {
    MAIN_BOTTOM("Main Bottom", "سفلي رئيسي"),
    MAIN_TOP("Main Top", "علوي رئيسي"),
    EXTRA_TOP("Extra Top (Support)", "علوي إضافي"),
    EXTRA_BOTTOM("Extra Bottom", "سفلي إضافي"),
    BENT_UP("Bent-up / Cranked", "مكسح"),
    STIRRUP("Stirrup / Link", "كانة رئيسية"),
    TIE("Column Tie", "كانة عمود"),
    CROSS_TIE("Cross Tie", "كانة حابسة"),
    SPIRAL("Spiral Hoop", "كانة حلزونية"),
    DISTRIBUTION("Distribution / Temperature", "فرش / غطاء / توزيع"),
    SKIN("Skin / Side Bar", "حديد انكماش جانبي"),
    HANGER("Stirrup Hanger", "تعليق كانات"),
    DOWEL("Dowel / Starter Bar", "أشاير"),
    PUNCHING("Punching Stud / Shear Bar", "حديد قص ثاقب"),
    MESH("Fabric Mesh", "شبكة تسليح")
}

/**
 * Single Group of identical bars within a structural member.
 */
data class BarGroup(
    val mark: String,
    val role: BarRole,
    val diameterMm: Int,
    val count: Int,
    val spacingMm: Double? = null,
    val layer: Int = 1,
    val shapeCode: BbsShapeCode = BbsShapeCode.STRAIGHT,
    val shapeParams: BbsShapeParams = BbsShapeParams(),
    val startLocationMm: Double = 0.0,
    val endLocationMm: Double = 0.0,
    val clause: String? = null
) {
    val totalAreaMm2: Double
        get() = RebarCatalog.totalAreaMm2(count, diameterMm)
}

/**
 * Station Demand and Capacity comparison at a specific cross-section.
 */
data class StationDemand(
    val stationMm: Double,
    val momentDemandKnM: Double = 0.0,
    val shearDemandKn: Double = 0.0,
    val axialDemandKn: Double = 0.0,
    val momentCapacityKnM: Double = 0.0,
    val shearCapacityKn: Double = 0.0,
    val axialCapacityKn: Double = 0.0
)

/**
 * Section Cut specification (Exactly 3 cuts per element: 1-1, 2-2, 3-3).
 */
data class SectionCut(
    val id: String, // "1-1", "2-2", "3-3"
    val stationMm: Double,
    val titleEn: String,
    val titleAr: String,
    val widthMm: Double,
    val depthMm: Double,
    val groupMarks: List<String>,
    val demand: StationDemand = StationDemand(stationMm),
    val providedAsMm2: Double,
    val requiredAsMm2: Double
) {
    val isSafe: Boolean
        get() = providedAsMm2 >= requiredAsMm2
}

/**
 * Case-specific detail view specification.
 */
data class DetailSpec(
    val id: String,
    val titleEn: String,
    val titleAr: String,
    val triggerCondition: String,
    val groupMarks: List<String>
)

/**
 * Bar Bending Schedule (BBS) Table Row.
 */
data class BbsRow(
    val memberMark: String,
    val barMark: String,
    val role: BarRole,
    val diameterMm: Int,
    val shapeCode: BbsShapeCode,
    val shapeParams: BbsShapeParams,
    val membersCount: Int = 1,
    val barsPerMember: Int,
    val totalBarCount: Int = membersCount * barsPerMember,
    val cutLengthMm: Double,
    val totalLengthM: Double = (totalBarCount * cutLengthMm) / 1000.0,
    val unitWeightKgM: Double = RebarCatalog.unitWeightKgPerM(diameterMm),
    val totalWeightKg: Double = totalLengthM * unitWeightKgM
)

/**
 * Per-diameter steel summary item.
 */
data class DiameterSummary(
    val diameterMm: Int,
    val totalBarsCount: Int,
    val totalLengthM: Double,
    val totalWeightKg: Double,
    val stock12mBarsCount: Int
)

/**
 * Complete Steel Totals for a layout/element.
 */
data class SteelTotals(
    val totalWeightKg: Double,
    val concreteVolumeM3: Double,
    val steelRatioKgPerM3: Double = if (concreteVolumeM3 > 0) totalWeightKg / concreteVolumeM3 else 0.0,
    val diameterSummaries: List<DiameterSummary> = emptyList()
)

/**
 * Complete Element Reinforcement Layout contract for a physical element (R15).
 */
data class ElementReinforcementLayout(
    val elementTitle: String,
    val memberMark: String,
    val caseKey: String,
    val coverMm: Double,
    val membersCount: Int = 1,
    val concreteVolumeM3: Double = 0.0,
    val groups: List<BarGroup>,
    val cuts: List<SectionCut>, // Must have size == 3 (G1)
    val details: List<DetailSpec>,
    val schedule: List<BbsRow>,
    val totals: SteelTotals,
    val codeVersion: String = "ECP 203 / ACI 318 / SBC 304"
) {
    /**
     * Validates G1 invariant: Exactly 3 cuts with ids 1-1, 2-2, 3-3.
     */
    fun validateG1(): Boolean {
        if (cuts.size != 3) return false
        val ids = cuts.map { it.id }.toSet()
        return ids.contains("1-1") && ids.contains("2-2") && ids.contains("3-3")
    }

    /**
     * Validates G2 invariant: Every mark referenced in groups/cuts exists in schedule.
     */
    fun validateG2(): Boolean {
        val scheduleMarks = schedule.map { it.barMark }.toSet()
        val groupMarks = groups.map { it.mark }.toSet()
        val cutMarks = cuts.flatMap { it.groupMarks }.toSet()
        return scheduleMarks.containsAll(groupMarks) && scheduleMarks.containsAll(cutMarks)
    }

    /**
     * Validates G3 invariant: Provided As >= Required As at all 3 cut stations.
     */
    fun validateG3(): Boolean {
        return cuts.all { it.isSafe }
    }

    /**
     * Validates G4 invariant: Total schedule weight matches sum of individual rows.
     */
    fun validateG4(): Boolean {
        val sumRows = schedule.sumOf { it.totalWeightKg }
        return abs(totals.totalWeightKg - sumRows) < 0.01
    }
}
