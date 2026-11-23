package com.civileg.core.costcontrol

import java.math.BigDecimal
import java.time.LocalDate

/** Workbook coordinates are evidence, not a unique business item number. No formula evaluation. */
data class BoqSourceCell(val workbookFingerprint: String, val sheet: String, val cell: String) {
    init { require(workbookFingerprint.isNotBlank() && sheet.isNotBlank() && cell.matches(Regex("[A-Z]+[1-9][0-9]*"))) }
}

data class RateAnalysisSource(
    val source: BoqSourceCell,
    val originalFormula: String?,
    val originalLabel: String
)

/** A price observation is editable by making a new snapshot, never by overwriting old quotes. */
data class RatePriceEvidence(
    val sourceDescription: String,
    val asOf: LocalDate?,
    val originalText: String,
    val currency: String? = null
) {
    init {
        require(sourceDescription.isNotBlank())
        currency?.let { java.util.Currency.getInstance(it) }
    }
}

/** Explicit arithmetic graph supplied by a reviewed mapping, never inferred from a label such as tax. */
sealed class RateAnalysisNode {
    abstract val id: String
    abstract val source: RateAnalysisSource
    data class Resource(
        override val id: String,
        override val source: RateAnalysisSource,
        val description: String,
        val resourceUnit: String,
        val coefficient: CostDecimal,
        val resourceRate: CostDecimal,
        val priceEvidence: RatePriceEvidence
    ) : RateAnalysisNode()

    /** Sum named prior values and multiply by an EXPLICIT factor, e.g.1.05,0.14,0.05. No defaults. */
    class ScaledSum(
        override val id: String,
        override val source: RateAnalysisSource,
        references: List<String>,
        val factor: CostDecimal
    ) : RateAnalysisNode() {
        private val frozen = references.toList()
        val references: List<String> get() = frozen.toList()
        init { require(frozen.isNotEmpty() && frozen.distinct().size == frozen.size) }
    }
}

class UnitRateAnalysis(nodes: List<RateAnalysisNode>, val resultNodeId: String) {
    private val frozen = nodes.toList()
    val nodes: List<RateAnalysisNode> get() = frozen.toList()
    init {
        require(frozen.isNotEmpty() && frozen.all { it.id.isNotBlank() })
        require(frozen.map { it.id }.distinct().size == frozen.size)
        require(resultNodeId in frozen.map { it.id })
    }
}

data class AnalysedRateLine(val id: String, val source: RateAnalysisSource, val amount: BigDecimal)
class UnitRateResult(lines: List<AnalysedRateLine>, val resultNodeId: String) {
    private val frozen = lines.toList()
    val lines: List<AnalysedRateLine> get() = frozen.toList()
    /** Only the caller-selected node is a unit rate. It need not be last; unused nodes are allowed.
     * Mapping review, not this graph, must exclude unintended subtotal/resource scope overlap. */
    val unitRate: BigDecimal get() = frozen.single { it.id == resultNodeId }.amount
}

object UnitRateAnalysisEngine {
    /** Ordered acyclic graph; missing, forward and duplicate references are rejected. No rounding. */
    fun calculate(analysis: UnitRateAnalysis): UnitRateResult {
        val values = linkedMapOf<String, BigDecimal>()
        val output = analysis.nodes.map { node ->
            val amount = when (node) {
                is RateAnalysisNode.Resource -> {
                    require(node.description.isNotBlank() && node.resourceUnit.isNotBlank())
                    node.coefficient.value.multiply(node.resourceRate.value)
                }
                is RateAnalysisNode.ScaledSum -> {
                    require(node.references.all { it in values }) { "Unknown or forward analysis reference" }
                    node.references.fold(BigDecimal.ZERO) { sum, id -> sum.add(values.getValue(id)) }
                        .multiply(node.factor.value)
                }
            }
            values[node.id] = amount
            AnalysedRateLine(node.id, node.source, amount)
        }
        return UnitRateResult(output, analysis.resultNodeId)
    }
}

/** Parent BOQ item; analysis children are rate components, NEVER independent project quantities. */
class AnalysedBoqItem(
    val snapshotId: String,
    val previousSnapshotId: String?,
    val revisionReason: String,
    val source: BoqSourceCell,
    val displayedItemNumber: String,
    val description: String,
    val unit: String,
    val quantity: CostDecimal,
    val quotedUnitRate: CostDecimal,
    val quotedPriceEvidence: RatePriceEvidence,
    /** Null means not recorded. Do not borrow currency from current settings. */
    val currency: String?,
    val analysis: UnitRateAnalysis
) {
    init {
        require(snapshotId.isNotBlank() && revisionReason.isNotBlank() && snapshotId != previousSnapshotId)
        require(description.isNotBlank() && unit.isNotBlank())
        currency?.let { java.util.Currency.getInstance(it) }
    }
    /** Make a separate candidate revision. Persistent uniqueness/parent ownership still belongs to the store. */
    fun reprice(newSnapshotId: String, reason: String, newAnalysis: UnitRateAnalysis,
                newQuotedRate: CostDecimal = quotedUnitRate,
                newQuotedEvidence: RatePriceEvidence = quotedPriceEvidence): AnalysedBoqItem {
        require(newSnapshotId.isNotBlank() && newSnapshotId != snapshotId) { "Repricing requires a new identity" }
        return AnalysedBoqItem(newSnapshotId, snapshotId, reason, source, displayedItemNumber, description,
            unit, quantity, newQuotedRate, newQuotedEvidence, currency, newAnalysis)
    }

    /** A required explicit gate before any project-cost/baseline import, not called by source preview. */
    fun requirePricedImportReady() {
        UnitRateAnalysisEngine.calculate(analysis) // Reject malformed graph even when it has no resources.
        require(currency != null && quotedPriceEvidence.currency == currency && quotedPriceEvidence.asOf != null) {
            "Quoted currency/date must be confirmed before import"
        }
        require(analysis.nodes.filterIsInstance<RateAnalysisNode.Resource>().all {
            it.priceEvidence.currency == currency && it.priceEvidence.asOf != null
        }) { "Every resource price needs the same confirmed currency and a price date" }
    }

    val quotedAmount: BigDecimal get() = quantity.value.multiply(quotedUnitRate.value)
    val analysedUnitRate: BigDecimal get() = UnitRateAnalysisEngine.calculate(analysis).unitRate
    val analysedAmount: BigDecimal get() = quantity.value.multiply(analysedUnitRate)
    /** A difference is evidence to review, not automatic replacement of the quoted rate. */
    val unitRateDifference: BigDecimal get() = quotedUnitRate.value.subtract(analysedUnitRate)
}
