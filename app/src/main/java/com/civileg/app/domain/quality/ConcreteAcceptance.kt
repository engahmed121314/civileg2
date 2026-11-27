package com.civileg.app.domain.quality

import kotlin.math.ceil

/**
 * Concrete strength acceptance and sampling frequency.
 *
 * Source: SBC 304-CR-18 (2018 edition), Chapter 26, printed p.224-225:
 *  - 26.12.3.1(b)(1): every arithmetic average of any three consecutive strength tests equals or exceeds f'c.
 *  - 26.12.3.1(b)(2): no strength test falls below f'c by more than 3.5 MPa if f'c <= 35 MPa,
 *    or by more than 0.10 f'c if f'c > 35 MPa.
 *  - 26.12.2.1(a): samples at least once a day, once per 110 m3 of concrete, and once per 460 m2 of slab or wall surface.
 *  - 26.12.2.1(b): when that frequency gives fewer than five tests for a mixture, at least five randomly selected batches
 *    (or each batch if fewer than five batches).
 * ECP 203-2025 equivalent clauses are NOT ASSESSED here (the ECP scan was not searched for them).
 * A "strength test" is the value the user enters for one sample; no cube/cylinder conversion is applied.
 */
object ConcreteAcceptance {
    const val SOURCE = "SBC 304-CR-18, 26.12.3.1(b), p.225"
    const val FREQUENCY_SOURCE = "SBC 304-CR-18, 26.12.2.1, p.225"

    data class Result(
        val tests: Int,
        val movingAverageOk: Boolean?,   // null when fewer than three tests exist
        val worstMovingAverage: Double?,
        val individualOk: Boolean,
        val lowestTest: Double?,
        val individualFloor: Double,
        val accepted: Boolean?,          // null = cannot be assessed (fewer than three tests)
    )

    fun individualFloor(fc: Double): Double = if (fc <= 35.0) fc - 3.5 else fc * 0.90

    fun evaluate(fc: Double, results: List<Double>): Result {
        require(fc > 0.0) { "specified strength must be positive" }
        val floor = individualFloor(fc)
        val indOk = results.all { it >= floor }
        val avgs = if (results.size >= 3) results.windowed(3).map { it.average() } else emptyList()
        val avgOk = if (avgs.isEmpty()) null else avgs.all { it >= fc }
        val accepted = when {
            !indOk -> false          // a failure outranks incompleteness
            avgOk == null -> null
            else -> avgOk
        }
        return Result(results.size, avgOk, avgs.minOrNull(), indOk, results.minOrNull(), floor, accepted)
    }

    /** Minimum number of sampling events: max of daily, per 110 m3 and per 460 m2, never fewer than five. */
    fun minimumSamples(volumeM3: Double, surfaceM2: Double?, pourDays: Int): Int {
        val byVolume = ceil(volumeM3 / 110.0).toInt()
        val bySurface = if (surfaceM2 != null && surfaceM2 > 0) ceil(surfaceM2 / 460.0).toInt() else 0
        return maxOf(pourDays, byVolume, bySurface, 5)
    }

    fun parseSeries(text: String): List<Double> =
        text.split(',', ';', ' ', '\n', '\t', '،').mapNotNull { it.trim().toDoubleOrNull() }.filter { it > 0.0 }
}
