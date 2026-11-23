package com.civileg.app.viewmodel

import android.os.Looper
import java.io.File

/** Failure-only observations. Never drains loopers, changes timeouts or replaces the original error. */
internal fun retainColumnUiTimeout(
    output: File,
    documents: File?,
    before: Set<String>,
    label: String,
    state: ColumnUiState,
    callbackObserved: String,
    failure: Throwable
) {
    try {
        val directory = File(output, "timeout-$label").apply { mkdirs() }
        val log = StringBuilder()
        fun line(value: String) { log.appendLine(value); println(value) }
        line("COLUMN_UI_TIMEOUT label=$label type=${failure.javaClass.name} " +
            "isExporting=${state.isExporting} errors=${state.errors} callback=$callbackObserved")
        line("PDF files below are failure-time snapshots, NOT completed-export acceptance.")
        val candidates = documents?.listFiles().orEmpty().filter {
            it.name.startsWith("Column_Report_") && it.extension == "pdf" && it.name !in before
        }.sortedBy { it.name }
        var budget = 32L * 1024 * 1024
        for (file in candidates.take(8)) {
            val size = file.length()
            line("COLUMN_EXPORT_FILE name=${file.name} bytes=$size modified=${file.lastModified()}")
            if (size > budget) {
                line("COLUMN_EXPORT_FILE_NOT_COPIED reason=diagnostic-budget")
                continue
            }
            budget -= size
            runCatching {
                // Bound the copy to the observed length, even if an IO writer is still active.
                var remaining = size
                file.inputStream().use { input ->
                    File(directory, "snapshot-${file.name}").outputStream().use { target ->
                        val buffer = ByteArray(8192)
                        while (remaining > 0) {
                            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (count < 0) break
                            target.write(buffer, 0, count)
                            remaining -= count
                        }
                    }
                }
                line("COLUMN_EXPORT_FILE_SNAPSHOT copied=${size - remaining} sourceBytesAfter=${file.length()}")
            }.onFailure { line("COLUMN_EXPORT_FILE_COPY_FAILED type=${it.javaClass.name}") }
        }
        if (candidates.isEmpty()) line(if (documents == null) "COLUMN_EXPORT_FILE not-an-export-check" else "COLUMN_EXPORT_FILE none-created-since-call")
        if (candidates.size > 8) line("COLUMN_EXPORT_FILE remaining-count=${candidates.size - 8}")
        val main = Looper.getMainLooper()
        line("COLUMN_EXPORT_MAIN thread=${main.thread.name} state=${main.thread.state}")
        var dumpLines = 0
        main.dump({ value -> if (dumpLines++ < 80) line("COLUMN_EXPORT_MAIN_QUEUE $value") }, "")
        Thread.getAllStackTraces().entries.sortedBy { it.key.name }.take(64).forEach { (thread, stack) ->
            line("COLUMN_EXPORT_THREAD name=${thread.name} state=${thread.state}")
            stack.take(28).forEach { line("  at $it") }
        }
        File(directory, "failure-observations.txt").writeText(log.toString())
    } catch (diagnosticFailure: Throwable) {
        println("COLUMN_EXPORT_DIAGNOSTIC_FAILED type=${diagnosticFailure.javaClass.name}")
    }
}
