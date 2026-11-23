package com.civileg.core.costcontrol

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CostForecastCsvTest {
    private fun forecast(projectId: String = "P1", lineId: String = "L1", explanation: String = "Reviewed remainder"): CostForecast {
        val date = LocalDate.of(2026, 1, 1)
        val baseline = CostBaseline("B0", projectId, "SAR", date, "Original", "Received goods",
            CostDecimal("1"), listOf(BaselineLine(lineId, "Concrete", "m3", CostDecimal("0.1"),
                CostDecimal("0.2"), date, "Quotation")))
        return CostControlEngine.forecast(baseline, date, emptyList(), listOf(
            LineForecastInput(projectId, "B0", baseline.fingerprint, lineId, "SAR", date, RemainingCost.Manual(CostDecimal("1.2300"), explanation))))
    }

    @Test fun reportKeepsCurrencyExactDecimalsAndForecastDisclaimer() {
        val text = CostForecastCsv.render(forecast())
        assertTrue(text.startsWith("\uFEFF"))
        assertTrue(text.contains("\"Currency\",\"SAR\"\r\n"))
        assertTrue(text.contains("\"Baseline budget\",\"0.02\""))
        assertTrue(text.contains("\"Remaining estimate\",\"1.2300\""))
        assertTrue(text.contains("\"Forecast margin (not recognized profit)\",\"-0.2300\""))
        assertFalse(text.contains("EGP"))
        assertFalse(text.contains("E-"))
        assertTrue(text.contains("not recognized accounting profit"))
    }

    @Test fun textCellsNeutralizeFormulaPrefixesAndEscapeQuotesAndNewlines() {
        listOf("=SUM(1,2)", "+CMD", "-CMD", "@SUM(1)", "  =1+1", "\t=1", "\r=1", "\n=1").forEach { hostile ->
            val text = CostForecastCsv.render(forecast(projectId = hostile))
            assertTrue(text.contains("\"Project\",\"'"))
        }
        val text = CostForecastCsv.render(forecast(lineId = "ID,\"A\"", explanation = "first\nsecond"))
        assertTrue(text.contains("\"ID,\"\"A\"\"\""))
        assertTrue(text.contains("\"first\nsecond\""))
    }

    @Test fun numericLookingIdentifiersStayTypedTextNotNumericAmount() {
        val text = CostForecastCsv.render(forecast(projectId = "001234567890123456789", lineId = "0007"))
        assertTrue(text.contains("\"Project\",\"'001234567890123456789\""))
        assertTrue(text.contains("\"'0007\""))
        assertTrue(text.contains("\"Remaining estimate\",\"1.2300\""))
        assertTrue(text.contains("SUMMARY"))
        assertTrue(text.contains("Baseline fingerprint"))
        assertTrue(text.contains("Baseline effective date"))
        assertTrue(text.contains("Baseline reason"))
    }

    @Test fun renderingDoesNotChangeSnapshotAndIsRepeatable() {
        val source = forecast()
        val before = source.estimateAtCompletion
        assertEquals(CostForecastCsv.render(source), CostForecastCsv.render(source))
        assertEquals(0, before.compareTo(source.estimateAtCompletion))
        assertEquals(0, BigDecimal("1.2300").compareTo(source.remaining))
    }
}
