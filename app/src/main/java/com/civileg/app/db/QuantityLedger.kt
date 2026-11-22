package com.civileg.app.db

import com.civileg.app.domain.entities.ProjectSummary

/**
 * Basis-aware project totals. Quantities are never promoted between bases:
 * the headline totals are MEASURED rows only, NOMINAL and LEGACY_UNVERIFIED rows are
 * separate subtotals, and UNKNOWN rows (placeholder zeros) add to no figure.
 */
object QuantityLedger {
    fun summarize(designs: List<Design>): ProjectSummary {
        fun rows(basis: QuantityBasis) = designs.filter { it.quantityBasis == basis }
        val measured = rows(QuantityBasis.MEASURED)
        val nominal = rows(QuantityBasis.NOMINAL_ESTIMATE)
        val legacy = rows(QuantityBasis.LEGACY_UNVERIFIED)
        val unknown = rows(QuantityBasis.UNKNOWN)
        val counted = designs.filter { it.quantityBasis != QuantityBasis.UNKNOWN }
        return ProjectSummary(
            totalConcrete = measured.sumOf { it.concreteVolume },
            totalSteel = measured.sumOf { it.steelWeight },
            totalCost = measured.sumOf { it.totalCost },
            designCount = designs.size,
            costEfficiencyIndex = 1.0,
            costBreakdown = counted.groupBy { it.type.name }.mapValues { e -> e.value.sumOf { it.totalCost } },
            methodBlockedDesignCount = designs.count(StoredDesignSafety::isBlocked),
            hasMeasuredQuantities = measured.isNotEmpty(),
            nominalConcrete = nominal.sumOf { it.concreteVolume },
            nominalSteel = nominal.sumOf { it.steelWeight },
            nominalCost = nominal.sumOf { it.totalCost },
            legacyConcrete = legacy.sumOf { it.concreteVolume },
            legacySteel = legacy.sumOf { it.steelWeight },
            legacyCost = legacy.sumOf { it.totalCost },
            measuredCount = measured.size,
            nominalCount = nominal.size,
            legacyCount = legacy.size,
            unknownQuantityCount = unknown.size
        )
    }
}
