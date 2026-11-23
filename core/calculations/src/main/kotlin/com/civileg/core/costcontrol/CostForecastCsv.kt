package com.civileg.core.costcontrol

import java.math.BigDecimal

/**
 * A summary report, not a reproducible ledger snapshot, original workbook or accounting statement.
 * Pure formatter: no disk writes, Android dependency or assumed EGP currency.
 * User text is formula-neutralized before RFC-4180 quoting. Decimal amounts are never rounded.
 */
object CostForecastCsv {
    fun render(forecast: CostForecast): String = buildString {
        append('\uFEFF')
        row("Report", "Operational forecast SUMMARY, not recognized accounting profit or a reproducible ledger snapshot")
        row("Project", forecast.projectId)
        row("Baseline revision", forecast.baselineRevision)
        row("Baseline fingerprint", forecast.baselineFingerprint)
        row("Baseline effective date", forecast.baselineEffectiveDate.toString())
        row("Baseline reason", forecast.baselineReason)
        row("Currency", forecast.currency)
        row("As of", forecast.asOf.toString())
        row("Incurred basis", forecast.incurredBasis)
        row("Contract value", forecast.contractValue)
        row("Baseline budget", forecast.budget)
        row("Planned margin", forecast.plannedMargin)
        row("Incurred", forecast.incurred)
        row("Invoiced (separate)", forecast.invoiced)
        row("Paid (separate)", forecast.paid)
        row("Remaining estimate", forecast.remaining)
        row("Estimate at completion", forecast.estimateAtCompletion)
        row("Forecast over budget (+ = over)", forecast.forecastOverBudget)
        row("Forecast margin (not recognized profit)", forecast.forecastMargin)
        append("\r\n")
        row("Line ID", "Description", "Unit", "Baseline quantity (entered)", "Baseline rate (entered)",
            "Price date", "Price source", "Budget", "Incurred", "Invoiced", "Paid", "Remaining estimate",
            "Estimate at completion", "Forecast over budget", "Estimate date", "Remaining method", "Explanation")
        forecast.lines.forEach { line ->
            row(line.lineId, line.baselineLine.description, line.baselineLine.unit,
                line.baselineLine.quantity.original, line.baselineLine.unitRate.original,
                line.baselineLine.priceDate.toString(), line.baselineLine.priceSource,
                line.budget, line.incurred, line.invoiced, line.paid,
                line.remaining, line.estimateAtCompletion, line.forecastOverBudget,
                line.estimateDate.toString(), when (line.remainingBasis) {
                    is RemainingCost.Manual -> "Manual estimate"
                    is RemainingCost.QuantityRate -> "Remaining quantity x current rate"
                    is RemainingCost.OpenCommitment -> "Reconciled open commitment + uncontracted estimate"
                }, line.remainingBasis.explanation)
        }
    }

    private fun StringBuilder.row(vararg cells: Any) {
        append(cells.joinToString(",") { cell ->
            when (cell) {
                is BigDecimal -> "\"${cell.toPlainString()}\""
                is String -> quoteText(cell)
                else -> error("Unsupported CSV cell type")
            }
        })
        append("\r\n")
    }

    private fun quoteText(value: String): String {
        val firstMeaningful = value.firstOrNull { !it.isWhitespace() && it != '\uFEFF' }
        val formulaLike = firstMeaningful in listOf('=', '+', '-', '@')
        val controlPrefix = value.firstOrNull()?.let { it == '\t' || it == '\r' || it == '\n' } == true
        // Numeric-looking TEXT identifiers are text, even if Excel would guess a number.
        // Apostrophe prefixes are a CSV safety convention, not a lossless spreadsheet import promise.
        val numericText = value.matches(Regex("[0-9]+(\\.[0-9]+)?"))
        val safe = if (formulaLike || controlPrefix || numericText) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
