package com.civileg.app.domain.audit

/**
 * §42 QA/QC ENGINE — EngineeringAuditEngine data model.
 *
 * Governing rules (spec: ممنوع False PASS):
 *  • A single FAIL anywhere ⇒ overall FAIL.
 *  • [AuditStatus.NOT_CHECKED] is NEVER promoted to PASS — an unaudited
 *    pipeline yields overall NOT_CHECKED, which downstream consumers
 *    (manifests, health bars) treat as "not trustworthy yet".
 *  • Empty input ⇒ NOT_CHECKED (auditing nothing proves nothing).
 */
enum class AuditStatus(val label: String) {
    PASS("PASS"),
    WARNING("WARNING"),
    FAIL("FAIL"),
    NOT_CHECKED("NOT CHECKED")
}

enum class AuditStage(val label: String) {
    INPUT("Input"),
    UNITS("Units"),
    GEOMETRY("Geometry"),
    LOADS("Loads"),
    ANALYSIS("Analysis"),
    DESIGN("Design"),
    DETAILING("Detailing"),
    DRAWING("Drawing"),
    PDF("PDF"),
    DXF("DXF"),
    BBS("BBS"),
    BOQ("BOQ")
}

/** One atomic audit finding. */
data class AuditCheck(
    val stage: AuditStage,
    val name: String,
    val status: AuditStatus,
    val message: String = "",
    val codeReference: String? = null
)

/**
 * Immutable result of one audit run over a project.
 * Health convention (documented, deterministic):
 *   health = round(100 × (pass + 0.5×warn + 0.5×notChecked) / total)
 * i.e., warnings and unchecked items earn HALF credit — they degrade the
 * score but never masquerade as success; FAIL earns zero and drags it down.
 */
data class AuditReport(
    val projectName: String,
    val checks: List<AuditCheck>
) {
    val status: AuditStatus by lazy {
        when {
            checks.isEmpty() -> AuditStatus.NOT_CHECKED
            checks.any { it.status == AuditStatus.FAIL } -> AuditStatus.FAIL
            checks.any { it.status == AuditStatus.NOT_CHECKED } -> AuditStatus.NOT_CHECKED
            checks.any { it.status == AuditStatus.WARNING } -> AuditStatus.WARNING
            else -> AuditStatus.PASS
        }
    }

    val passedCount: Int get() = checks.count { it.status == AuditStatus.PASS }
    val failedChecks: List<AuditCheck> get() = checks.filter { it.status == AuditStatus.FAIL }
    val warnedChecks: List<AuditCheck>
        get() = checks.filter { it.status == AuditStatus.WARNING || it.status == AuditStatus.NOT_CHECKED }

    /** Formatted failure lines ready for MANIFEST.json / PDF QA section. */
    val failures: List<String>
        get() = failedChecks.map { "${it.stage.label}/${it.name}: ${it.message}".trimEnd(':') }

    /** Formatted warning lines (warnings AND not-checked — both are doubts). */
    val warnings: List<String>
        get() = warnedChecks.map {
            val prefix = if (it.status == AuditStatus.NOT_CHECKED) "NOT CHECKED" else "WARNING"
            "${it.stage.label}/$prefix/${it.name}: ${it.message}".trimEnd(':', ' ')
        }

    /** Number of clean checks — feeds MANIFEST "passedChecks". */
    val passedChecksForManifest: Int get() = passedCount

    val healthPercent: Int by lazy {
        if (checks.isEmpty()) return@lazy 0
        val credit = checks.sumOf {
            when (it.status) {
                AuditStatus.PASS -> 1.0
                AuditStatus.WARNING, AuditStatus.NOT_CHECKED -> 0.5
                AuditStatus.FAIL -> 0.0
            }
        }
        (credit * 100.0 / checks.size).toInt().coerceIn(0, 100)
    }
}

/**
 * §42 engine entry points. Stateless — every call produces a fresh report.
 */
object EngineeringAuditEngine {

    /** Build a report from raw checks; rejects duplicate check identities so
     *  a stage can't silently shadow another's finding. */
    fun report(projectName: String, checks: List<AuditCheck>): AuditReport {
        val keys = checks.map { it.stage to it.name }
        require(keys.size == keys.distinct().size) {
            "Duplicate audit check identity: ${keys.groupBy { it }.filterValues { c -> c.size > 1 }.keys}"
        }
        return AuditReport(projectName, checks)
    }

    /**
     * Convenience probe builders for common structural gates.
     * Each returns NOT_CHECKED when its precondition is absent — loud absence,
     * not silent success (rule 1.4).
     */
    fun capacityCheck(stage: AuditStage, name: String, utilization: Double?, allowOver: Boolean = false): AuditCheck {
        val status = when {
            utilization == null || !utilization.isFinite() -> AuditStatus.NOT_CHECKED
            utilization > 1.0 && !allowOver -> AuditStatus.FAIL
            utilization >= 0.9 -> AuditStatus.WARNING
            else -> AuditStatus.PASS
        }
        val msg = if (utilization?.isFinite() == true) "utilization = ${"%.2f".format(utilization)}" else "value unavailable"
        return AuditCheck(stage, name, status, msg)
    }

    fun artifactExistsCheck(stage: AuditStage, name: String, file: java.io.File?): AuditCheck =
        if (file != null && file.exists() && file.isFile && file.length() > 0) {
            AuditCheck(stage, name, AuditStatus.PASS, file.absolutePath)
        } else {
            AuditCheck(stage, name, AuditStatus.NOT_CHECKED, "artifact missing")
        }
}
