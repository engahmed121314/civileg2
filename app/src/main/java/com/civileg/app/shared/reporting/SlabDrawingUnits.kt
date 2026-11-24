package com.civileg.app.shared.reporting

/**
 * Unit normalisation for the slab fallback drawings.
 *
 * The type-aware slab drawings (flat, hollow-block, waffle) size columns, drop
 * panels and ribs in millimetres, while the dispatcher historically received spans
 * in metres from some callers and in millimetres from others. Every span handed to
 * those drawings is converted here, in one place, so dimension labels and geometry
 * use the same unit.
 */
internal object SlabDrawingUnits {
    /** Returns the span in millimetres. [spanIsMm] says which unit the caller supplied. */
    fun spanToMm(span: Double, spanIsMm: Boolean): Double {
        require(span.isFinite() && span > 0.0) { "span must be a positive finite number" }
        return if (spanIsMm) span else span * 1000.0
    }

    /** Dimension label for a length in millimetres, for example "6000 mm". */
    fun mmLabel(valueMm: Double): String = "${valueMm.toInt()} mm"
}
