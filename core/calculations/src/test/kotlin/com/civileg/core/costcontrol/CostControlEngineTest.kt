package com.civileg.core.costcontrol

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CostControlEngineTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val cutoff = LocalDate.of(2026, 2, 1)
    private fun dec(text: String) = CostDecimal(text)
    private fun line(id: String = "A", qty: String = "1", rate: String = "4000000") =
        BaselineLine(id, "Work $id", "m3", dec(qty), dec(rate), start, "Entered quotation")
    private fun baseline(lines: List<BaselineLine> = listOf(line()), revision: String = "B0") =
        CostBaseline(revision, "project-1", "EGP", start, "Initial budget", "Accepted work and received goods",
            dec("4500000"), lines)
    private fun record(id: String, kind: CostRecordKind, amount: String, date: LocalDate = cutoff,
                       lineId: String = "A", currency: String = "EGP") =
        CostRecord(id, "project-1", lineId, date, kind, dec(amount), currency, "Source $id")
    private fun estimate(amount: String, lineId: String = "A", currency: String = "EGP", date: LocalDate = cutoff) =
        LineForecastInput("project-1", "B0", baseline().fingerprint, lineId, currency, date, RemainingCost.Manual(dec(amount), "Reviewed remaining scope"))
    private fun equal(expected: String, actual: BigDecimal) =
        assertEquals("Expected $expected, actual $actual", 0, BigDecimal(expected).compareTo(actual))
    private fun refused(action: () -> Unit) {
        try { action(); throw AssertionError("Invalid input accepted") }
        catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test fun acceptanceExampleFirstCutoff() {
        val result = CostControlEngine.forecast(baseline(), cutoff,
            listOf(record("inc1", CostRecordKind.INCURRED, "1900000")), listOf(estimate("2000000")))
        equal("4000000", result.budget)
        equal("500000", result.plannedMargin)
        equal("3900000", result.estimateAtCompletion)
        equal("600000", result.forecastMargin)
        equal("-100000", result.forecastOverBudget)
    }

    @Test fun acceptanceExampleSecondCutoffUsesCumulativeIncurred() {
        val result = CostControlEngine.forecast(baseline(), cutoff, listOf(
            record("inc1", CostRecordKind.INCURRED, "1900000", start),
            record("inc2", CostRecordKind.INCURRED, "1200000")), listOf(estimate("1500000")))
        equal("3100000", result.incurred)
        equal("4600000", result.estimateAtCompletion)
        equal("-100000", result.forecastMargin)
    }

    @Test fun invoicesAndCashNeverBecomeIncurred() {
        val result = CostControlEngine.forecast(baseline(), cutoff, listOf(
            record("1", CostRecordKind.INCURRED, "10"),
            record("1", CostRecordKind.INVOICE, "30"),
            record("1", CostRecordKind.PAYMENT, "50")), listOf(estimate("7")))
        equal("10", result.incurred); equal("30", result.invoiced); equal("50", result.paid)
        equal("17", result.estimateAtCompletion)
        assertEquals("Accepted work and received goods", result.incurredBasis)
    }

    @Test fun cutoffIncludesSameDayAndExcludesFuture() {
        val result = CostControlEngine.forecast(baseline(), cutoff, listOf(
            record("1", CostRecordKind.INCURRED, "2"),
            record("2", CostRecordKind.INCURRED, "3", cutoff.plusDays(1))), listOf(estimate("0")))
        equal("2", result.incurred)
    }

    @Test fun decimalMultiplicationAndSumAreExact() {
        val result = CostControlEngine.forecast(baseline(listOf(line("A", "0.1", "0.2"), line("B", "0.1", "0.1"))),
            cutoff, emptyList(), listOf(estimate("0.01").copy(baselineFingerprint = baseline(listOf(line("A", "0.1", "0.2"), line("B", "0.1", "0.1"))).fingerprint), estimate("0.02", "B").copy(baselineFingerprint = baseline(listOf(line("A", "0.1", "0.2"), line("B", "0.1", "0.1"))).fingerprint)))
        equal("0.03", result.budget); equal("0.03", result.remaining)
        equal(result.lines.fold(BigDecimal.ZERO) { sum, item -> sum.add(item.estimateAtCompletion) }.toPlainString(),
            result.estimateAtCompletion)
        assertEquals("001.2300", dec("001.2300").original)
    }

    @Test fun quantityRateRequiresExactUnitAndKeepsBasisDate() {
        val input = LineForecastInput("project-1", "B0", baseline().fingerprint, "A", "EGP", cutoff,
            RemainingCost.QuantityRate(dec("2.5"), dec("3.3"), "m3", "Current quotation"))
        val result = CostControlEngine.forecast(baseline(), cutoff, emptyList(), listOf(input))
        equal("8.25", result.remaining)
        assertEquals(cutoff, result.lines.single().estimateDate)
        refused { CostControlEngine.forecast(baseline(), cutoff, emptyList(),
            listOf(input.copy(remaining = RemainingCost.QuantityRate(dec("1"), dec("1"), "ton", "Quote")))) }
    }

    @Test fun openCommitmentIsAddedOnceAndNotReducedByInvoicesAgain() {
        val input = LineForecastInput("project-1", "B0", baseline().fingerprint, "A", "EGP", cutoff,
            RemainingCost.OpenCommitment(dec("40"), dec("15"), "Open balance reconciled to incurred"))
        val result = CostControlEngine.forecast(baseline(), cutoff,
            listOf(record("i", CostRecordKind.INCURRED, "60"), record("f", CostRecordKind.INVOICE, "50")), listOf(input))
        equal("55", result.remaining); equal("115", result.estimateAtCompletion)
    }

    @Test fun completeEstimatesAreRequiredEvenWhenRemainingIsZero() {
        refused { CostControlEngine.forecast(baseline(), cutoff, emptyList(), emptyList()) }
        refused { CostControlEngine.forecast(baseline(), cutoff, emptyList(), listOf(estimate("0"), estimate("0"))) }
        refused { CostControlEngine.forecast(baseline(), cutoff, emptyList(), listOf(estimate("0", "unknown"))) }
        equal("0", CostControlEngine.forecast(baseline(), cutoff, emptyList(), listOf(estimate("0"))).remaining)
    }

    @Test fun duplicateEntriesUnknownLinesAndMixedCurrenciesAreRefused() {
        val r = record("same", CostRecordKind.INCURRED, "1")
        refused { CostControlEngine.forecast(baseline(), cutoff, listOf(r, r), listOf(estimate("0"))) }
        refused { CostControlEngine.forecast(baseline(), cutoff, listOf(r.copy(lineId = "missing")), listOf(estimate("0"))) }
        refused { CostControlEngine.forecast(baseline(), cutoff, listOf(r.copy(currency = "USD")), listOf(estimate("0"))) }
        refused { CostControlEngine.forecast(baseline(), cutoff, emptyList(), listOf(estimate("0", currency = "USD"))) }
    }

    @Test fun crossProjectRecordsAndStaleBaselineEstimatesAreRefused() {
        val r = record("1", CostRecordKind.INCURRED, "1")
        refused { CostControlEngine.forecast(baseline(), cutoff, listOf(r.copy(projectId = "other")), listOf(estimate("0"))) }
        refused { CostControlEngine.forecast(baseline(), cutoff, emptyList(), listOf(estimate("0").copy(projectId = "other"))) }
        refused { CostControlEngine.forecast(baseline(), cutoff, emptyList(), listOf(estimate("0").copy(baselineRevision = "B1"))) }
    }

    @Test fun sameRevisionLabelWithDifferentContentCannotReuseEstimates() {
        val original = baseline()
        val changed = baseline(listOf(line(rate = "4100000")))
        assertEquals(original.revision, changed.revision)
        assertTrue(original.fingerprint != changed.fingerprint)
        refused { CostControlEngine.forecast(changed, cutoff, emptyList(), listOf(estimate("0"))) }
        equal("4100000", CostControlEngine.forecast(changed, cutoff, emptyList(),
            listOf(estimate("0").copy(baselineFingerprint = changed.fingerprint))).budget)
        assertEquals(original.fingerprint, baseline().fingerprint)
    }

    @Test fun invalidDatesAreRefused() {
        refused { CostControlEngine.forecast(baseline(), start.minusDays(1), emptyList(), listOf(estimate("0"))) }
        refused { CostControlEngine.forecast(baseline(), cutoff, emptyList(), listOf(estimate("0", date = cutoff.plusDays(1)))) }
        refused { CostControlEngine.forecast(baseline(), cutoff, emptyList(), listOf(estimate("0", date = start.minusDays(1)))) }
        refused { baseline(listOf(line().copy(priceDate = start.plusDays(1)))) }
    }

    @Test fun staleEtcCannotBeAddedToNewerIncurredCosts() {
        refused { CostControlEngine.forecast(baseline(), cutoff,
            listOf(record("inc", CostRecordKind.INCURRED, "10")),
            listOf(estimate("20", date = cutoff.minusDays(1)))) }
    }

    @Test fun malformedNegativeAndNonFiniteDecimalsAreRefused() {
        listOf("", "-1", "NaN", "Infinity", "1e3", "1,000", " 1", ".5", "1.", "9".repeat(129)).forEach { text ->
            refused { dec(text) }
        }
    }

    @Test fun baselineIsDefensivelyCopiedAndNewRevisionDoesNotMutateOld() {
        val source = mutableListOf(line())
        val old = baseline(source)
        source.clear()
        equal("4000000", old.budget)
        val returned = old.lines as MutableList<BaselineLine>
        // A one-element JVM List may refuse mutation. Neither behavior may affect the baseline.
        try { returned.clear() } catch (_: UnsupportedOperationException) { }
        equal("4000000", old.budget)
        val next = baseline(listOf(line(rate = "4100000")), "B1")
        equal("4000000", old.budget); equal("4100000", next.budget)
        assertTrue(old.revision != next.revision)
    }

    @Test fun fingerprintIncludesUnicodeSourceFieldsAndOriginalPrecision() {
        val original = baseline()
        val arabic = baseline(listOf(line().copy(description = "خرسانة", priceSource = "عرض سعر")))
        assertTrue(original.fingerprint != arabic.fingerprint)
        assertEquals(arabic.fingerprint, baseline(listOf(line().copy(description = "خرسانة", priceSource = "عرض سعر"))).fingerprint)
        assertTrue(original.fingerprint != baseline(listOf(line().copy(quantity = dec("1.0")))).fingerprint)
        assertTrue(original.fingerprint != baseline(listOf(line().copy(unit = "ton"))).fingerprint)
    }

    @Test fun invalidIsoCurrencyIsRefused() {
        refused { CostBaseline("B0", "project-1", "AAA", start, "Initial", "Received", dec("1"), listOf(line())) }
    }

    @Test fun ambiguousBaselineAndBlankEvidenceAreRefused() {
        refused { baseline(emptyList()) }
        refused { baseline(listOf(line(), line())) }
        refused { line().copy(unit = "") }
        refused { line().copy(priceSource = "") }
        refused { record("", CostRecordKind.INCURRED, "1") }
        refused { RemainingCost.Manual(dec("1"), " ") }
    }
}
