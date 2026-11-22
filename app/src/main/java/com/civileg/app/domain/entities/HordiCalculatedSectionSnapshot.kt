package com.civileg.app.domain.entities

import java.util.Collections

/** Immutable rendering handoff for one completed revision, not a member approval. */
class HordiCalculatedSectionSnapshot internal constructor(
    val revisionId: String,
    section: SlabCalculatedSection,
    val requiredSteelMm2: Double,
    val providedSteelMm2: Double,
    val methodReference: String,
    incompleteChecks: List<String>
) {
    val section: SlabCalculatedSection = section.copy(
        bars = section.bars?.let { Collections.unmodifiableList(it.toList()) },
        incompleteGeometry = Collections.unmodifiableList(section.incompleteGeometry.toList())
    )
    val incompleteChecks: List<String> = Collections.unmodifiableList(incompleteChecks.toList())
    companion object
}
