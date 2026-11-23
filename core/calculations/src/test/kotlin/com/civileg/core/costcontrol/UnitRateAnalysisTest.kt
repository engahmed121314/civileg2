package com.civileg.core.costcontrol

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Manually reviewed fixture mappings, not an arbitrary XLSX formula interpreter/importer. */
class UnitRateAnalysisTest {
    private val workbook = "06a92282339f9b339d8ddf84fdec8d1ab76672bb9190d0a3d2302733ead35899"
    private val ceilingSheet = "بند 49 - اسقف معلقة بلاطات جبسي"
    private val cells = mapOf("tile" to "F4", "board" to "F5", "frame" to "F6", "wasteIncluded" to "F7",
        "sourceVat" to "F8", "materials" to "F9", "labor" to "F11", "direct" to "F12",
        "admin" to "F13", "sourceDeductions" to "F14", "sourceMarkup" to "F15", "rate" to "F16")
    private fun source(cell: String, formula: String? = null, label: String = "Source row") =
        RateAnalysisSource(BoqSourceCell(workbook, ceilingSheet, cell), formula, label)
    private fun resource(id: String, amount: String) = RateAnalysisNode.Resource(id, source(cells[id] ?: "F4"), id, "m2", CostDecimal("1"), CostDecimal(amount), RatePriceEvidence("Workbook example, not current market", null, amount))
    private fun sum(id: String, refs: List<String>, factor: String = "1") =
        RateAnalysisNode.ScaledSum(id, source(cells[id] ?: "F7"), refs, CostDecimal(factor))
    private fun equal(value: String, actual: BigDecimal) = assertEquals(0, BigDecimal(value).compareTo(actual))
    private fun refused(action: () -> Unit) {
        try { action(); throw AssertionError("Invalid analysis accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun item49PreservesQuoted557AndExactAnalysis556Point9479() {
        val nodes = listOf(resource("tile", "170"), resource("board", "75"), resource("frame", "37"),
            sum("wasteIncluded", listOf("tile", "board", "frame"), "1.05"),
            sum("sourceVat", listOf("wasteIncluded"), "0.14"),
            sum("materials", listOf("wasteIncluded", "sourceVat")), resource("labor", "75"),
            sum("direct", listOf("materials", "labor")), sum("admin", listOf("direct"), "0.05"),
            sum("sourceDeductions", listOf("direct"), "0.2"), sum("sourceMarkup", listOf("direct"), "0.1"),
            sum("rate", listOf("direct", "admin", "sourceDeductions", "sourceMarkup")))
        val analysis = UnitRateAnalysis(nodes, "rate")
        val item = AnalysedBoqItem("item49-B0", null, "Imported source example", BoqSourceCell(workbook, "بند 49 - اسقف معلقة بلاطات جبسي", "A2"), "49",
            "Suspended ceiling source item", "m2", CostDecimal("830"), CostDecimal("557"), RatePriceEvidence("Workbook quote, date not recorded", null, "557"), null, analysis)
        equal("556.9479", item.analysedUnitRate)
        equal("462310", item.quotedAmount)
        equal("0.0521", item.unitRateDifference)
        equal("462266.757", item.analysedAmount)
        val result = UnitRateAnalysisEngine.calculate(analysis)
        assertTrue(result.lines.fold(BigDecimal.ZERO) { sum, row -> sum.add(row.amount) } != result.unitRate)
        assertEquals(null, item.currency)
    }

    @Test fun excavationSourceSubtotalAndAddonsDoNotRoundBeforeTotal() {
        // Source F25=192.5, F26=F25*5%, F27=F25*20%, F28=F25*10%, F29=SUM(F25:F28).
        val direct = RateAnalysisNode.Resource("direct", RateAnalysisSource(
            BoqSourceCell(workbook, "حفر وخرسانات ", "F25"), "=SUM(F3:F24)", "Cached direct subtotal fixture, not a full decomposition"),
            "Reviewed source subtotal", "m3", CostDecimal("1"), CostDecimal("192.5"), RatePriceEvidence("Workbook source subtotal, not current", null, "192.5"))
        fun excavation(id: String, cell: String, refs: List<String>, factor: String, formula: String) =
            RateAnalysisNode.ScaledSum(id, RateAnalysisSource(BoqSourceCell(workbook, "حفر وخرسانات ", cell), formula, id), refs, CostDecimal(factor))
        val analysis = UnitRateAnalysis(listOf(direct,
            excavation("admin", "F26", listOf("direct"), "0.05", "=F25*5%"),
            excavation("sourceDeductions", "F27", listOf("direct"), "0.2", "=F25*20%"),
            excavation("sourceMarkup", "F28", listOf("direct"), "0.1", "=F25*10%"),
            excavation("rate", "F29", listOf("direct", "admin", "sourceDeductions", "sourceMarkup"), "1", "=SUM(F25:F28)")), "rate")
        equal("259.875", UnitRateAnalysisEngine.calculate(analysis).unitRate)
    }

    @Test fun repricingCreatesNewAnalysisWithoutChangingOriginalQuotedRateOrOldResources() {
        val originalResource = resource("tile", "170")
        val oldAnalysis = UnitRateAnalysis(listOf(originalResource), "tile")
        val old = AnalysedBoqItem("B0", null, "Original example", BoqSourceCell(workbook, ceilingSheet, "A2"),
            "49", "Example", "m2", CostDecimal("2"), CostDecimal("557"),
            RatePriceEvidence("Original quote, date unknown", null, "557"), null, oldAnalysis)
        val revisedResource = originalResource.copy(resourceRate = CostDecimal("200"),
            priceEvidence = RatePriceEvidence("Manual current quotation", java.time.LocalDate.of(2026, 11, 23), "200"))
        val revised = old.reprice("B1", "Explicit repricing", UnitRateAnalysis(listOf(revisedResource), "tile"))
        refused { old.reprice("B0", "Invalid same identity", UnitRateAnalysis(listOf(revisedResource), "tile")) }
        refused { old.requirePricedImportReady() }
        equal("170", old.analysedUnitRate)
        equal("200", revised.analysedUnitRate)
        equal("557", old.quotedUnitRate.value)
        equal("557", revised.quotedUnitRate.value)
        assertEquals("170", originalResource.priceEvidence.originalText)
        assertEquals(java.time.LocalDate.of(2026, 11, 23), revisedResource.priceEvidence.asOf)
        assertEquals(null, old.quotedPriceEvidence.asOf)
    }

    @Test fun importGateRejectsUnknownAndMixedResourceCurrencies() {
        val date = java.time.LocalDate.of(2026, 11, 23)
        val a = resource("tile", "170").copy(priceEvidence = RatePriceEvidence("Quote", date, "170", "EGP"))
        fun item(currency: String?, evidence: RatePriceEvidence, analysis: UnitRateAnalysis) = AnalysedBoqItem(
            "new", null, "Reviewed", BoqSourceCell(workbook, ceilingSheet, "A2"), "49", "Example", "m2",
            CostDecimal("1"), CostDecimal("557"), evidence, currency, analysis)
        val quote = RatePriceEvidence("Quote", date, "557", "EGP")
        item("EGP", quote, UnitRateAnalysis(listOf(a), "tile")).requirePricedImportReady()
        refused { item("EGP", quote, UnitRateAnalysis(listOf(sum("missingGraph", listOf("unknown"))), "missingGraph")).requirePricedImportReady() }
        refused { item(null, quote, UnitRateAnalysis(listOf(a), "tile")).requirePricedImportReady() }
        refused { item("EGP", quote, UnitRateAnalysis(listOf(a.copy(priceEvidence = a.priceEvidence.copy(currency = "USD"))), "tile")).requirePricedImportReady() }
        refused { item("EGP", quote.copy(asOf = null), UnitRateAnalysis(listOf(a), "tile")).requirePricedImportReady() }
    }

    @Test fun sameDisplayedItemNumberDoesNotBecomeSourceIdentity() {
        val a = BoqSourceCell(workbook, "sheet64", "A2")
        val b = BoqSourceCell(workbook, "sheet67", "A2")
        assertTrue(a != b)
    }

    @Test fun missingForwardSelfAndDuplicateReferencesAreRefused() {
        refused { UnitRateAnalysisEngine.calculate(UnitRateAnalysis(listOf(sum("rate", listOf("missing"))), "rate")) }
        refused { UnitRateAnalysisEngine.calculate(UnitRateAnalysis(listOf(sum("rate", listOf("rate"))), "rate")) }
        refused { UnitRateAnalysisEngine.calculate(UnitRateAnalysis(listOf(sum("rate", listOf("later")), resource("later", "1")), "rate")) }
        refused { sum("rate", listOf("a", "a")) }
        refused { UnitRateAnalysis(listOf(resource("same", "1"), resource("same", "2")), "same") }
        refused { UnitRateAnalysis(listOf(resource("a", "1")), "unknown") }
    }

    @Test fun changingCallerListsDoesNotChangeGraph() {
        val refs = mutableListOf("a")
        val nodes = mutableListOf<RateAnalysisNode>(resource("a", "0.1"), sum("rate", refs, "0.2"))
        val analysis = UnitRateAnalysis(nodes, "rate")
        nodes.clear(); refs.clear()
        equal("0.02", UnitRateAnalysisEngine.calculate(analysis).unitRate)
    }
}
