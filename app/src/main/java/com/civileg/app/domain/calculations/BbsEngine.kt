package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import kotlin.math.ceil

/**
 * Pure Kotlin engine for generating complete, verified Bar Bending Schedule (BBS) rows,
 * diameter summaries, and weight totals from an ElementReinforcementLayout or structural inputs.
 */
object BbsEngine {

    /**
     * Generates a BbsRow from a BarGroup and element context.
     */
    fun generateRow(
        memberMark: String,
        group: BarGroup,
        membersCount: Int = 1
    ): BbsRow {
        InputGuard.notBlank("memberMark", memberMark)
        InputGuard.positive("diameterMm", group.diameterMm.toDouble())
        InputGuard.positive("count", group.count.toDouble())
        InputGuard.positive("membersCount", membersCount.toDouble())

        val cutLen = group.shapeParams.calculateCutLength(group.shapeCode, group.diameterMm)
        val totalCount = membersCount * group.count

        return BbsRow(
            memberMark = memberMark,
            barMark = group.mark,
            role = group.role,
            diameterMm = group.diameterMm,
            shapeCode = group.shapeCode,
            shapeParams = group.shapeParams,
            membersCount = membersCount,
            barsPerMember = group.count,
            totalBarCount = totalCount,
            cutLengthMm = cutLen
        )
    }

    /**
     * Computes steel totals and diameter summaries for a list of BBS rows.
     */
    fun computeTotals(rows: List<BbsRow>, concreteVolumeM3: Double = 0.0): SteelTotals {
        val totalWeight = rows.sumOf { it.totalWeightKg }

        val grouped = rows.groupBy { it.diameterMm }
        val summaries = grouped.map { (dia, diaRows) ->
            val totalBars = diaRows.sumOf { it.totalBarCount }
            val totalLen = diaRows.sumOf { it.totalLengthM }
            val totalWt = diaRows.sumOf { it.totalWeightKg }
            val stockBars = ceil(totalLen / RebarCatalog.STOCK_BAR_LENGTH_M).toInt()

            DiameterSummary(
                diameterMm = dia,
                totalBarsCount = totalBars,
                totalLengthM = totalLen,
                totalWeightKg = totalWt,
                stock12mBarsCount = stockBars
            )
        }.sortedBy { it.diameterMm }

        val ratio = if (concreteVolumeM3 > 0) totalWeight / concreteVolumeM3 else 0.0

        return SteelTotals(
            totalWeightKg = totalWeight,
            concreteVolumeM3 = concreteVolumeM3,
            steelRatioKgPerM3 = ratio,
            diameterSummaries = summaries
        )
    }

    /**
     * Builds a complete, verified ElementReinforcementLayout object from bar groups, cuts, details, and context.
     */
    fun buildLayout(
        elementTitle: String,
        memberMark: String,
        caseKey: String,
        coverMm: Double,
        membersCount: Int = 1,
        concreteVolumeM3: Double = 0.0,
        groups: List<BarGroup>,
        cuts: List<SectionCut>,
        details: List<DetailSpec>,
        codeVersion: String = "ECP 203 / ACI 318 / SBC 304"
    ): ElementReinforcementLayout {
        InputGuard.notBlank("elementTitle", elementTitle)
        InputGuard.notBlank("memberMark", memberMark)
        InputGuard.positive("coverMm", coverMm)

        val rows = groups.map { generateRow(memberMark, it, membersCount) }
        val totals = computeTotals(rows, concreteVolumeM3)

        val layout = ElementReinforcementLayout(
            elementTitle = elementTitle,
            memberMark = memberMark,
            caseKey = caseKey,
            coverMm = coverMm,
            membersCount = membersCount,
            concreteVolumeM3 = concreteVolumeM3,
            groups = groups,
            cuts = cuts,
            details = details,
            schedule = rows,
            totals = totals,
            codeVersion = codeVersion
        )

        // Validate G1, G2, G4 invariants
        require(layout.validateG1()) { "ElementReinforcementLayout must contain exactly 3 cuts (1-1, 2-2, 3-3)" }
        require(layout.validateG2()) { "All bar marks must be present in schedule" }
        require(layout.validateG4()) { "Total weight must match sum of row weights" }

        return layout
    }
}
