package com.civileg.app.utils

object CsvEncoder {
    fun row(values: Iterable<Any?>): String =
        values.joinToString(",") { cell(it?.toString().orEmpty()) }

    fun cell(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""
}
