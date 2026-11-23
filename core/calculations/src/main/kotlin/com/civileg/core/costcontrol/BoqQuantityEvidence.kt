package com.civileg.core.costcontrol

/** Provenance is required before a quantity can be presented as a measured/design-derived BOQ input. */
enum class QuantityOrigin { MANUAL, DECLARED_GEOMETRY, VERIFIED_DESIGN, ASSUMED, MISSING_INPUT }

class BoqQuantityEvidence(
    val origin: QuantityOrigin,
    val quantity: CostDecimal?,
    val unit: String,
    val sourceReference: String,
    val sourceRevision: String?,
    val method: String,
    missingInputs: List<String>,
    val reviewerConfirmed: Boolean
) {
    private val frozenMissing = missingInputs.toList()
    val missingInputs: List<String> get() = frozenMissing.toList()
    init {
        require(unit.isNotBlank() && sourceReference.isNotBlank() && method.isNotBlank())
        require(frozenMissing.all { it.isNotBlank() } && frozenMissing.distinct().size == frozenMissing.size)
        require(origin != QuantityOrigin.MISSING_INPUT || quantity == null)
        require(origin != QuantityOrigin.VERIFIED_DESIGN || !sourceRevision.isNullOrBlank())
    }
    /** Conditional completeness of ONE item only. Never certifies full-building scope or field accuracy. */
    val readyForNonEstimatedItem: Boolean get() = quantity != null && frozenMissing.isEmpty() &&
        origin != QuantityOrigin.ASSUMED && origin != QuantityOrigin.MISSING_INPUT && reviewerConfirmed

    fun requireNonEstimatedItemReady() {
        require(readyForNonEstimatedItem) { "Missing/assumed/unreviewed quantity cannot be represented as a non-estimated BOQ item" }
    }
}
