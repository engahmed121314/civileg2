package com.civileg.app.domain.calculations.utils

/** A result is readable only for the exact inputs that produced it.
 * A ticket invalidated during a calculation cannot publish a late result.
 * The caller confines this state to one thread (the UI thread in production).
 */
class CalculatedCaseState<I, R> {
    data class Snapshot<I, R>(val input: I, val result: R)
    private var generation = 0L
    var snapshot: Snapshot<I, R>? = null
        private set

    fun invalidate() {
        generation++
        snapshot = null
    }

    fun begin(): Long {
        invalidate()
        return generation
    }

    fun complete(ticket: Long, input: I, result: R): Boolean {
        if (ticket != generation) return false
        snapshot = Snapshot(input, result)
        return true
    }

    fun isCurrent(ticket: Long): Boolean = ticket == generation

    fun matches(input: I): Boolean = snapshot?.input == input
}
