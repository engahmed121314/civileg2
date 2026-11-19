package com.civileg.app.domain.drawing

/**
 * Status of an element's drawing package (§13.11, §13.12).
 * Statuses may only move toward PACKAGE.
 */
enum class PackageStatus {
    TODO,          // Not started
    LEGACY,        // Old drawing path still in use
    PACKAGE,       // Full R15 package: 3 cuts + details + BBS + DXF + PDF
}

data class PackageEntry(
    val screenName: String,
    val element: String,
    val caseKeys: List<String>,
    val status: PackageStatus
)

/**
 * Registry of every calculation screen and its drawing-package status.
 * A JVM test fails when a calculation screen is unregistered or when a PACKAGE
 * element violates G1-G3.
 */
object DrawingPackageRegistry {
    private val entries = mutableListOf<PackageEntry>()

    fun register(entry: PackageEntry) {
        entries.add(entry)
    }

    fun allEntries(): List<PackageEntry> = entries.toList()

    fun findByScreen(screenName: String): PackageEntry? =
        entries.find { it.screenName == screenName }

    fun packageElements(): List<PackageEntry> =
        entries.filter { it.status == PackageStatus.PACKAGE }

    fun legacyElements(): List<PackageEntry> =
        entries.filter { it.status == PackageStatus.LEGACY }

    fun todoElements(): List<PackageEntry> =
        entries.filter { it.status == PackageStatus.TODO }

    /** Returns true when every non-TODO registered calculation screen is at PACKAGE. */
    fun allPackage(): Boolean = entries.filter { it.status != PackageStatus.TODO }.all { it.status == PackageStatus.PACKAGE }

    /** Count of screens by status. */
    fun statusCounts(): Map<PackageStatus, Int> =
        entries.groupBy { it.status }.mapValues { it.value.size }
}
