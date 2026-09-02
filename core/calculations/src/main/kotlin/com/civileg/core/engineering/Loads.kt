package com.civileg.core.engineering

/**
 * Load types and code combination engine (PHASE 06–07 seed, spec §16–17).
 *
 * SINGLE SOURCE of combination factors: no calculator may invent its own
 * multipliers (spec §3). Sets implemented here are the citable gravity sets;
 * seismic combinations arrive with the Seismic Engine (PHASE 18).
 */
enum class LoadType { DEAD, LIVE, ROOF_LIVE, SNOW, WIND, SEISMIC }

/** One factored load combination: U = Σ (factor × type). */
data class FactoredCombination(
    val name: String,
    val factors: Map<LoadType, Double>,
    val reference: String
) {
    fun demand(dead: Double, live: Double = 0.0, roofLive: Double = 0.0): Double =
        (factors[LoadType.DEAD] ?: 0.0) * dead +
        (factors[LoadType.LIVE] ?: 0.0) * live +
        (factors[LoadType.ROOF_LIVE] ?: 0.0) * roofLive

    /** True when the combination reduces dead load (uplift/stability side). */
    val isDeadReducing: Boolean get() = (factors[LoadType.DEAD] ?: 1.0) < 1.0
}

object LoadCombinations {

    /**
     * ECP 203 basic gravity combinations:
     *   U = 1.4 G          (§2-3-1-1 a)
     *   U = 1.2 G + 1.6 Q  (§2-3-1-1 b)
     */
    fun ecp203Gravity(): List<FactoredCombination> = listOf(
        FactoredCombination("1.4G", mapOf(LoadType.DEAD to 1.4), "ECP 203 §2-3-1-1(a)"),
        FactoredCombination("1.2G+1.6Q", mapOf(LoadType.DEAD to 1.2, LoadType.LIVE to 1.6), "ECP 203 §2-3-1-1(b)")
    )

    /**
     * ACI 318-19 §5.3.1 basic strength combinations (gravity subset):
     *   (a) 1.4D            (b) 1.2D + 1.6L         (c) 1.2D + 1.6L + 0.5Lr
     *   (f) 0.9D + 1.0W     — wind placeholder kept for envelope symmetry until
     *   the Wind Engine lands; W=0 makes it identical to 0.9G.
     */
    fun aci318Gravity(): List<FactoredCombination> = listOf(
        FactoredCombination("5.3.1a: 1.4D", mapOf(LoadType.DEAD to 1.4), "ACI 318-19 §5.3.1(a)"),
        FactoredCombination("5.3.1b: 1.2D+1.6L", mapOf(LoadType.DEAD to 1.2, LoadType.LIVE to 1.6), "ACI 318-19 §5.3.1(b)"),
        FactoredCombination("5.3.1c: 1.2D+1.6L+0.5Lr", mapOf(LoadType.DEAD to 1.2, LoadType.LIVE to 1.6, LoadType.ROOF_LIVE to 0.5), "ACI 318-19 §5.3.1(c)"),
        FactoredCombination("5.3.1f: 0.9D+W", mapOf(LoadType.DEAD to 0.9, LoadType.WIND to 1.0), "ACI 318-19 §5.3.1(f)")
    )

    /** Governing (maximum) demand across all combinations. */
    fun envelopeMax(combos: List<FactoredCombination>, dead: Double, live: Double = 0.0, roofLive: Double = 0.0): EnvelopeResult =
        combos.map { it to it.demand(dead, live, roofLive) }.maxByOrNull { it.second }
            ?.let { EnvelopeResult(it.first.name, it.first.reference, it.second) }
            ?: error("empty combination set")

    data class EnvelopeResult(val combinationName: String, val reference: String, val demand: Double)
}
