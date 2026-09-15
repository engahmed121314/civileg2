package com.civileg.core.sanity

object EngineeringSanityEngine {
    fun check(any: Any): SanityReport = SanityReport("General", emptyList())
}

data class SanityReport(
    val category: String,
    val items: List<SanityItem>,
    val hasError: Boolean = false,
    val hasWarning: Boolean = false
)

data class SanityItem(val level: SanityLevel, val message: String)

enum class SanityLevel { ERROR, WARNING, INFO }
