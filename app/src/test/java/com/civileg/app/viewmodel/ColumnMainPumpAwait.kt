package com.civileg.app.viewmodel

import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Test-only bounded wait that pumps the Android main looper while waiting.
 *
 * Why: the leading HYPOTHESIS for the full-PDF export waits is that their
 * real IO completes on a background dispatcher and posts the completion
 * callback to the Android Main dispatcher, which `compose.waitUntil` then
 * starves by occupying the main thread while polling - the 8a witnesses show
 * export in flight / a pending main continuation, not a proven exporter hang.
 * This remains a hypothesis pending the witness runs; this helper only makes
 * the wait compatible with a pending main-thread continuation, it does not
 * assume or force that outcome.
 *
 * Contract (unchanged from the original waits):
 *  - SAME caller-supplied deadline (30s); a timeout still fails the test.
 *  - Deadline must be positive; it is enforced monotonically (nanoTime), and
 *    success is only accepted while the deadline has NOT expired - a
 *    condition observed after the deadline is a failure, never a late pass.
 *  - Bounded advances: each loop iteration advances the main looper by a
 *    fixed small quantum and then yields real time briefly, so wall-clock
 *    progress is interleaved with looper progress and the loop cannot spin.
 *  - NO change to production code, real IO, reporter, callback or export state.
 *  - The exact original condition is polled; all downstream assertions
 *    (nonnull capture, revision match, image checks) remain as written.
 *  - This helper waits only; it cannot make a failing export pass.
 */
fun awaitMainPumped(deadlineMs: Long, condition: () -> Boolean) {
    require(deadlineMs > 0) { "deadline must be positive, was $deadlineMs" }
    val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(deadlineMs)
    while (true) {
        // Monotonic deadline check BEFORE accepting success: a condition that
        // becomes true after the deadline is reported as a timeout.
        if (System.nanoTime() - deadlineNanos > 0) {
            throw AssertionError(
                "Condition not met within ${deadlineMs}ms while pumping Android main looper"
            )
        }
        if (condition()) return
        // Bounded looper advance, then a brief REAL-time yield so the loop
        // cannot spin without wall-clock progress between advances.
        ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
        Thread.sleep(10)
    }
}
