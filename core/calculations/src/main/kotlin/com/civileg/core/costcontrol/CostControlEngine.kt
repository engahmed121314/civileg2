package com.civileg.core.costcontrol

import java.math.BigDecimal
import java.time.LocalDate
import java.security.MessageDigest

/** Exact non-negative decimal input. Original text is retained; no rounding or FX is performed. */
data class CostDecimal(val original: String) {
    init {
        require(original.length <= 128 && original.matches(Regex("[0-9]+(\\.[0-9]+)?"))) {
            "Use a non-negative plain decimal with at most 128 characters"
        }
    }
    val value: BigDecimal get() = BigDecimal(original)
}

data class BaselineLine(
    val id: String,
    val description: String,
    val unit: String,
    val quantity: CostDecimal,
    val unitRate: CostDecimal,
    val priceDate: LocalDate,
    val priceSource: String
) {
    init {
        require(id.isNotBlank() && description.isNotBlank() && unit.isNotBlank())
        require(priceSource.isNotBlank())
    }
    val amount: BigDecimal get() = quantity.value.multiply(unitRate.value)
}

/** A separate snapshot, never an update of a legacy Design or MaterialItem row. */
class CostBaseline(
    val revision: String,
    val projectId: String,
    val currency: String,
    val effectiveDate: LocalDate,
    val reason: String,
    /** Company-confirmed basis for incurred entries; this engine never chooses recognition policy. */
    val incurredBasis: String,
    val contractValue: CostDecimal,
    lines: List<BaselineLine>
) {
    private val frozenLines = lines.toList()
    val lines: List<BaselineLine> get() = frozenLines.toList()
    init {
        require(revision.isNotBlank() && projectId.isNotBlank() && reason.isNotBlank() && incurredBasis.isNotBlank())
        require(currency.matches(Regex("[A-Z]{3}"))) { "Explicit currency code required" }
        java.util.Currency.getInstance(currency) // ISO currency validity, not market-price availability.
        require(frozenLines.isNotEmpty())
        require(frozenLines.map { it.id }.distinct().size == frozenLines.size) { "Duplicate baseline line" }
        require(frozenLines.all { !it.priceDate.isAfter(effectiveDate) }) { "Future price in baseline" }
    }
    /** Content-bound identity, distinct from the human revision label. Includes original input precision. */
    val fingerprint: String get() {
        val digest = MessageDigest.getInstance("SHA-256")
        fun field(text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        listOf("cost-baseline-v1", revision, projectId, currency, effectiveDate.toString(), reason,
            incurredBasis, contractValue.original).forEach(::field)
        frozenLines.forEach { line ->
            listOf(line.id, line.description, line.unit, line.quantity.original, line.unitRate.original,
                line.priceDate.toString(), line.priceSource).forEach(::field)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    val budget: BigDecimal get() = frozenLines.fold(BigDecimal.ZERO) { sum, line -> sum.add(line.amount) }
}

/** Incurred work/received goods, an invoice and a payment are deliberately different records. */
enum class CostRecordKind { INCURRED, INVOICE, PAYMENT }

data class CostRecord(
    val id: String,
    val projectId: String,
    val lineId: String,
    val date: LocalDate,
    val kind: CostRecordKind,
    val amount: CostDecimal,
    val currency: String,
    val reference: String
) {
    init { require(id.isNotBlank() && projectId.isNotBlank() && lineId.isNotBlank() && reference.isNotBlank()) }
}

/** Remaining cost is an explicit estimate, never inferred from paid cash or an invoice. */
sealed class RemainingCost {
    abstract val explanation: String
    abstract val amount: BigDecimal

    data class Manual(val estimate: CostDecimal, override val explanation: String) : RemainingCost() {
        init { require(explanation.isNotBlank()) }
        override val amount: BigDecimal get() = estimate.value
    }

    data class QuantityRate(
        val remainingQuantity: CostDecimal,
        val currentRate: CostDecimal,
        val unit: String,
        override val explanation: String
    ) : RemainingCost() {
        init { require(unit.isNotBlank() && explanation.isNotBlank()) }
        override val amount: BigDecimal get() = remainingQuantity.value.multiply(currentRate.value)
    }

    /** Caller supplies reconciled OPEN commitment, not original contract value. No invoice subtraction. */
    data class OpenCommitment(
        val remainingCommitment: CostDecimal,
        val uncontractedEstimate: CostDecimal,
        override val explanation: String
    ) : RemainingCost() {
        init { require(explanation.isNotBlank()) }
        override val amount: BigDecimal get() = remainingCommitment.value.add(uncontractedEstimate.value)
    }
}

data class LineForecastInput(
    val projectId: String,
    val baselineRevision: String,
    val baselineFingerprint: String,
    val lineId: String,
    val currency: String,
    val estimateDate: LocalDate,
    val remaining: RemainingCost
)

data class LineCostForecast(
    val lineId: String,
    val baselineLine: BaselineLine,
    val budget: BigDecimal,
    val incurred: BigDecimal,
    val invoiced: BigDecimal,
    val paid: BigDecimal,
    val remaining: BigDecimal,
    val estimateAtCompletion: BigDecimal,
    /** Positive means forecast over budget. */
    val forecastOverBudget: BigDecimal,
    val estimateDate: LocalDate,
    val remainingBasis: RemainingCost
)

class CostForecast internal constructor(
    val baselineRevision: String,
    val baselineFingerprint: String,
    val baselineEffectiveDate: LocalDate,
    val baselineReason: String,
    val projectId: String,
    val currency: String,
    val asOf: LocalDate,
    val contractValue: BigDecimal,
    val incurredBasis: String,
    lines: List<LineCostForecast>
) {
    private val frozenLines = lines.toList()
    val lines: List<LineCostForecast> get() = frozenLines.toList()
    private fun sum(selector: (LineCostForecast) -> BigDecimal): BigDecimal =
        frozenLines.fold(BigDecimal.ZERO) { total, line -> total.add(selector(line)) }
    val budget: BigDecimal get() = sum { it.budget }
    val incurred: BigDecimal get() = sum { it.incurred }
    val invoiced: BigDecimal get() = sum { it.invoiced }
    val paid: BigDecimal get() = sum { it.paid }
    val remaining: BigDecimal get() = sum { it.remaining }
    val estimateAtCompletion: BigDecimal get() = sum { it.estimateAtCompletion }
    val forecastOverBudget: BigDecimal get() = estimateAtCompletion.subtract(budget)
    val plannedMargin: BigDecimal get() = contractValue.subtract(budget)
    /** Operational forecast only. This is NOT accounting profit or earned revenue. */
    val forecastMargin: BigDecimal get() = contractValue.subtract(estimateAtCompletion)
}

object CostControlEngine {
    /**
     * Records are cumulative to the cutoff. A complete remaining-cost estimate at that same cutoff
     * is required per line, so an older ETC is not silently added to newer incurred costs.
     * Future records are excluded, never future-dated estimates. Currency mixing, unknown line IDs,
     * duplicate record IDs, and silent unit conversion are refused.
     * ACCEPTED-INPUT CONTRACT: incurred entries must already be reconciled to one company basis,
     * net of corrections and without duplicate economic events. IDs alone cannot detect an accrual
     * later re-entered under an invoice ID. Caller must gate entry/import on source reconciliation.
     * This engine does not reconcile ledger origins, supersession, reversals or contract allocations. No tax/FX/retention/accrual policy
     * is inferred. Credits/reversals are not supported by this first slice.
     */
    fun forecast(
        baseline: CostBaseline,
        asOf: LocalDate,
        records: List<CostRecord>,
        estimates: List<LineForecastInput>
    ): CostForecast {
        require(!asOf.isBefore(baseline.effectiveDate)) { "Cutoff precedes baseline" }
        val baselineFingerprint = baseline.fingerprint
        val baselineLines = baseline.lines
        val ids = baselineLines.map { it.id }.toSet()
        require(records.map { it.kind to it.id }.distinct().size == records.size) { "Duplicate cost record" }
        require(records.all { it.projectId == baseline.projectId && it.lineId in ids && it.currency == baseline.currency }) {
            "Unknown cost line or currency mismatch"
        }
        require(estimates.map { it.lineId }.distinct().size == estimates.size) { "Duplicate estimate" }
        require(estimates.map { it.lineId }.toSet() == ids) { "Every baseline line needs an explicit remaining estimate" }
        require(estimates.all { it.projectId == baseline.projectId && it.baselineRevision == baseline.revision && it.baselineFingerprint == baselineFingerprint && it.currency == baseline.currency && it.estimateDate == asOf }) {
            "Estimate currency/date mismatch"
        }
        val byLine = estimates.associateBy { it.lineId }
        val eligible = records.filter { !it.date.isAfter(asOf) }.groupBy { it.lineId }
        val output = baselineLines.map { line ->
            val input = byLine.getValue(line.id)
            val basis = input.remaining
            if (basis is RemainingCost.QuantityRate) require(basis.unit == line.unit) { "Unit mismatch" }
            fun total(kind: CostRecordKind) = eligible[line.id].orEmpty()
                .filter { it.kind == kind }
                .fold(BigDecimal.ZERO) { sum, record -> sum.add(record.amount.value) }
            val incurred = total(CostRecordKind.INCURRED)
            val eac = incurred.add(basis.amount)
            LineCostForecast(line.id, line, line.amount, incurred, total(CostRecordKind.INVOICE),
                total(CostRecordKind.PAYMENT), basis.amount, eac, eac.subtract(line.amount), input.estimateDate, basis)
        }
        return CostForecast(baseline.revision, baselineFingerprint, baseline.effectiveDate, baseline.reason, baseline.projectId, baseline.currency, asOf,
            baseline.contractValue.value, baseline.incurredBasis, output)
    }
}
