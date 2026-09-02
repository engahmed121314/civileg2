package com.civileg.app.utils

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * §93/§94 FINAL ENGINEERING PACKAGE GENERATOR.
 *
 * Assembles every generated artifact of a project (calculation/drawing PDFs,
 * DXF sheets, BBS, BOQ exports) into one structured delivery folder and writes
 * a MANIFEST.json so any engineer can answer: which project, which revision,
 * which code/engine produced these files, and what checks passed or failed.
 *
 * Traceability contract (spec §40): no orphan output — every file lands in a
 * categorized subfolder and appears exactly once in the manifest.
 *
 * Pure java.io — fully unit-testable on the JVM.
 */
object CompletePackageGenerator {

    const val ENGINE_VERSION = "CivilEG Professional"

    data class PackageResult(
        val rootDir: File,
        val manifestFile: File,
        /** Relative paths of every file inside the package (incl. manifest). */
        val files: List<String>,
        /** Requested sources that could not be packaged (missing on disk). */
        val missingSources: List<String>
    )

    /**
     * Category subfolder for an artifact, derived from extension + name.
     * Rules (first match wins), mirroring spec §93 layout:
     *   .dxf                     → DXF
     *   *.pdf containing "bbs"   → BBS
     *   *.pdf containing "boq"   → BOQ
     *   .xlsx / .csv             → Excel
     *   other .pdf               → PDF
     *   anything else            → PROJECT
     */
    internal fun categoryFor(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".dxf") -> "DXF"
            lower.endsWith(".pdf") && lower.contains("bbs") -> "BBS"
            lower.endsWith(".pdf") && lower.contains("boq") -> "BOQ"
            lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv") -> "Excel"
            lower.endsWith(".pdf") -> "PDF"
            else -> "PROJECT"
        }
    }

    /** Sanitize a file name: strip path separators and timestamp-colliding chars. */
    internal fun safeName(fileName: String): String =
        fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "unnamed" }

    /**
     * §42 bridge — build the package directly from an audit report so QA and
     * delivery can never disagree: manifest checks/warnings/failures come from
     * the SAME [com.civileg.app.domain.audit.AuditReport] that gates readiness.
     */
    fun generatePackage(
        targetRoot: File,
        projectName: String,
        sources: List<File>,
        codeVersion: String,
        audit: com.civileg.app.domain.audit.AuditReport,
        revision: String = "R0"
    ): PackageResult = generatePackage(
        targetRoot = targetRoot,
        projectName = projectName,
        sources = sources,
        codeVersion = codeVersion,
        revision = revision,
        passedChecks = audit.passedChecksForManifest,
        warnings = audit.warnings,
        failures = audit.failures
    )

    /**
     * Build the package under [targetRoot]/<sanitized projectName>_Package/.
     * Sources that don't exist are reported in [PackageResult.missingSources]
     * and silently skipped from copying — never fabricated (no fake outputs).
     */
    fun generatePackage(
        targetRoot: File,
        projectName: String,
        sources: List<File>,
        codeVersion: String,
        revision: String = "R0",
        passedChecks: Int = 0,
        warnings: List<String> = emptyList(),
        failures: List<String> = emptyList()
    ): PackageResult {
        require(projectName.isNotBlank()) { "Project name must not be blank" }

        val packageDir = File(targetRoot, safeName(projectName) + "_Package")
        val existing = sources.filter { it.exists() && it.isFile }
        val missing = sources.filterNot { it.exists() && it.isFile }
            .map { it.absolutePath }

        val copied = mutableListOf<String>()
        existing.forEach { src ->
            val folder = categoryFor(src.name)
            val destDir = File(packageDir, folder).apply { mkdirs() }
            var dest = File(destDir, safeName(src.name))
            // Avoid silent overwrite — disambiguate instead (traceability)
            var i = 1
            while (dest.exists()) {
                val base = safeName(src.nameWithoutExtension)
                dest = File(destDir, "${base}_$i.${src.extension.ifEmpty { "dat" }}")
                i++
            }
            src.copyTo(dest, overwrite = false)
            copied.add(dest.relativeTo(packageDir).path.replace('\\', '/'))
        }

        val manifest = buildManifest(
            projectName = projectName,
            codeVersion = codeVersion,
            engineVersion = ENGINE_VERSION,
            revision = revision,
            date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            files = copied.sorted(),
            passedChecks = passedChecks,
            warnings = warnings,
            failures = failures,
            missingSources = missing
        )
        val manifestFile = File(packageDir, "MANIFEST.json")
        manifestFile.writeText(manifest)
        copied.add("MANIFEST.json")

        return PackageResult(
            rootDir = packageDir,
            manifestFile = manifestFile,
            files = copied.sorted(),
            missingSources = missing
        )
    }

    /**
     * §94 manifest as compact JSON. Hand-built (no serialization dependency):
     * strings escaped RFC-8259 style.
     */
    internal fun buildManifest(
        projectName: String,
        codeVersion: String,
        engineVersion: String,
        revision: String,
        date: String,
        files: List<String>,
        passedChecks: Int,
        warnings: List<String>,
        failures: List<String>,
        missingSources: List<String>
    ): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "").replace("\t", "\\t")

        fun arr(items: List<String>) =
            items.joinToString(",", "[", "]") { "\"${esc(it)}\"" }

        val isSafe = failures.isEmpty() && missingSources.isEmpty()
        return buildString {
            append("{\n")
            append("  \"project\": \"${esc(projectName)}\",\n")
            append("  \"revision\": \"${esc(revision)}\",\n")
            append("  \"code\": \"${esc(codeVersion)}\",\n")
            append("  \"engineVersion\": \"${esc(engineVersion)}\",\n")
            append("  \"generatedAt\": \"${esc(date)}\",\n")
            append("  \"passedChecks\": $passedChecks,\n")
            append("  \"isSafe\": $isSafe,\n")
            append("  \"warnings\": ${arr(warnings)},\n")
            append("  \"failures\": ${arr(failures)},\n")
            append("  \"unverifiedItems\": ${arr(missingSources)},\n")
            append("  \"files\": ${arr(files)}\n")
            append("}\n")
        }
    }
}
