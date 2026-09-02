package com.civileg.core.engineering

/**
 * Flat-slab strip reinforcement, fed to the DrawingModel flat-slab builder.
 *
 * A flat slab designed by DDM/EFM carries FOUR strip groups (column/middle ×
 * top/bottom), each with its own (count, Ø) selection and centre-to-centre
 * spacing — mirroring [SlabReinforcementResult]'s "slim directional carrier"
 * ethos. Purely a passthrough carrier: the app's live adapter fills it from
 * the domain [com.civileg.app.domain.FlatSlabResult] (no recomputation).
 */
data class FlatSlabStripReinforcement(
    /** (number of bars, Ø mm) decided by the flat-slab flexure engine. */
    val barSelection: Pair<Int, Double>,
    /** Centre-to-centre spacing (mm) of the strip bars. */
    val spacingMm: Double
)

data class FlatSlabReinforcementResult(
    val columnStripTop: FlatSlabStripReinforcement,
    val columnStripBottom: FlatSlabStripReinforcement,
    val middleStripTop: FlatSlabStripReinforcement,
    val middleStripBottom: FlatSlabStripReinforcement,
    val isSafe: Boolean,
    val warnings: List<String>
)