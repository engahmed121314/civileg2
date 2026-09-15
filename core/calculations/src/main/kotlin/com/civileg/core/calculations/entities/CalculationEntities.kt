package com.civileg.core.calculations.entities

import com.civileg.core.sanity.SanityReport

enum class CheckStatus { PASS, FAIL, WARNING }

class CalculationTrace {
    val items = mutableListOf<TraceItem>()
    var overall: CheckStatus = CheckStatus.PASS

    fun add(name: String, formula: String, value: String, result: String, limit: String, status: CheckStatus, utilization: Double? = null, codeReference: String = "") {
        items.add(TraceItem(name, formula, value, result, limit, status, utilization, codeReference))
        if (status == CheckStatus.FAIL) overall = CheckStatus.FAIL
        else if (status == CheckStatus.WARNING && overall == CheckStatus.PASS) overall = CheckStatus.WARNING
    }
}

data class TraceItem(
    val name: String,
    val formula: String,
    val value: String,
    val result: String,
    val limit: String,
    val status: CheckStatus,
    val utilization: Double?,
    val codeReference: String
)
