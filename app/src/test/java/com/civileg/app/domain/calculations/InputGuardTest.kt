package com.civileg.app.domain.calculations

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for InputGuard — the unified input guardian (Rule 1.4: loud failures).
 * Every entry point of a calculation engine must call InputGuard first.
 * Zero / negative / NaN / Infinity values must raise IllegalArgumentException
 * instead of silently producing bogus results.
 */
class InputGuardTest {

    // ─── positive(Double) ────────────────────────────────────────────────

    @Test
    fun `positive should pass for valid positive double`() {
        val result = InputGuard.positive("width", 5.0)
        assertEquals("Should return the same positive value", 5.0, result, 1e-12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `positive should throw for zero double`() {
        InputGuard.positive("width", 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `positive should throw for negative double`() {
        InputGuard.positive("width", -3.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `positive should throw for NaN double`() {
        InputGuard.positive("width", Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `positive should throw for positive Infinity double`() {
        InputGuard.positive("width", Double.POSITIVE_INFINITY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `positive should throw for negative Infinity double`() {
        InputGuard.positive("width", Double.NEGATIVE_INFINITY)
    }

    // ─── positive(Int) ───────────────────────────────────────────────────

    @Test
    fun `positive should pass for valid positive int`() {
        val result = InputGuard.positive("numBars", 4)
        assertEquals("Should return the same positive int", 4, result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `positive int should throw for zero`() {
        InputGuard.positive("numBars", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `positive int should throw for negative`() {
        InputGuard.positive("numBars", -2)
    }

    // ─── positive(Float) ─────────────────────────────────────────────────

    @Test
    fun `positive should pass for valid positive float`() {
        val result = InputGuard.positive("spacing", 200.0f)
        assertEquals("Should return the same positive float", 200.0f, result, 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `positive float should throw for zero`() {
        InputGuard.positive("spacing", 0.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `positive float should throw for negative`() {
        InputGuard.positive("spacing", -10.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `positive float should throw for NaN`() {
        InputGuard.positive("spacing", Float.NaN)
    }

    // ─── nonNegative(Double) ─────────────────────────────────────────────

    @Test
    fun `nonNegative should pass for zero`() {
        val result = InputGuard.nonNegative("offset", 0.0)
        assertEquals("Zero is non-negative", 0.0, result, 1e-12)
    }

    @Test
    fun `nonNegative should pass for positive value`() {
        val result = InputGuard.nonNegative("offset", 7.5)
        assertEquals("Positive is non-negative", 7.5, result, 1e-12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonNegative should throw for negative value`() {
        InputGuard.nonNegative("offset", -1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonNegative should throw for NaN`() {
        InputGuard.nonNegative("offset", Double.NaN)
    }

    // ─── nonNegative(Int) ────────────────────────────────────────────────

    @Test
    fun `nonNegative int should pass for zero`() {
        val result = InputGuard.nonNegative("count", 0)
        assertEquals("Zero is non-negative int", 0, result)
    }

    @Test
    fun `nonNegative int should pass for positive value`() {
        val result = InputGuard.nonNegative("count", 5)
        assertEquals("Positive int is non-negative", 5, result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonNegative int should throw for negative`() {
        InputGuard.nonNegative("count", -3)
    }

    // ─── inRange(Double) ─────────────────────────────────────────────────

    @Test
    fun `inRange should pass for value within range`() {
        val result = InputGuard.inRange("storyHeight", 3000.0, 1000.0, 20000.0)
        assertEquals("Should return value within range", 3000.0, result, 1e-12)
    }

    @Test
    fun `inRange should pass for value at min boundary`() {
        val result = InputGuard.inRange("storyHeight", 1000.0, 1000.0, 20000.0)
        assertEquals("Min boundary is inclusive", 1000.0, result, 1e-12)
    }

    @Test
    fun `inRange should pass for value at max boundary`() {
        val result = InputGuard.inRange("storyHeight", 20000.0, 1000.0, 20000.0)
        assertEquals("Max boundary is inclusive", 20000.0, result, 1e-12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `inRange should throw for value below range`() {
        InputGuard.inRange("storyHeight", 500.0, 1000.0, 20000.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `inRange should throw for value above range`() {
        InputGuard.inRange("storyHeight", 25000.0, 1000.0, 20000.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `inRange should throw for NaN`() {
        InputGuard.inRange("storyHeight", Double.NaN, 1000.0, 20000.0)
    }

    // ─── inRange(Int) ────────────────────────────────────────────────────

    @Test
    fun `inRange int should pass for value within range`() {
        val result = InputGuard.inRange("floor", 3, 1, 50)
        assertEquals("Int within range", 3, result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `inRange int should throw for value outside range`() {
        InputGuard.inRange("floor", 100, 1, 50)
    }

    // ─── notBlank ────────────────────────────────────────────────────────

    @Test
    fun `notBlank should pass for non-blank string`() {
        val result = InputGuard.notBlank("mark", "B1")
        assertEquals("Should return the same non-blank string", "B1", result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `notBlank should throw for empty string`() {
        InputGuard.notBlank("mark", "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `notBlank should throw for whitespace-only string`() {
        InputGuard.notBlank("mark", "   ")
    }

    // ─── notEmpty ────────────────────────────────────────────────────────

    @Test
    fun `notEmpty should pass for non-empty collection`() {
        val list = listOf(1, 2, 3)
        val result = InputGuard.notEmpty("zones", list)
        assertEquals("Should return the same non-empty collection", list, result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `notEmpty should throw for empty collection`() {
        InputGuard.notEmpty("zones", emptyList<Any>())
    }

    // ─── notNull ─────────────────────────────────────────────────────────

    @Test
    fun `notNull should pass for non-null value`() {
        val result = InputGuard.notNull("design", "hello")
        assertEquals("Should return the same non-null value", "hello", result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `notNull should throw for null value`() {
        InputGuard.notNull("design", null as String?)
    }

    // ─── require ─────────────────────────────────────────────────────────

    @Test
    fun `require should pass for true condition`() {
        // Should not throw
        InputGuard.require(true, "This condition is true")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `require should throw for false condition`() {
        InputGuard.require(false, "This condition is false")
    }

    // ─── finite ──────────────────────────────────────────────────────────

    @Test
    fun `finite should pass for finite value`() {
        val result = InputGuard.finite("moment", 42.5)
        assertEquals("Should return the same finite value", 42.5, result, 1e-12)
    }

    @Test
    fun `finite should pass for zero`() {
        val result = InputGuard.finite("moment", 0.0)
        assertEquals("Zero is finite", 0.0, result, 1e-12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `finite should throw for NaN`() {
        InputGuard.finite("moment", Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `finite should throw for positive Infinity`() {
        InputGuard.finite("moment", Double.POSITIVE_INFINITY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `finite should throw for negative Infinity`() {
        InputGuard.finite("moment", Double.NEGATIVE_INFINITY)
    }

    // ─── atLeastOne ──────────────────────────────────────────────────────

    @Test
    fun `atLeastOne should pass when one value is positive`() {
        val result = InputGuard.atLeastOne("a" to -1.0, "b" to 5.0, "c" to 0.0)
        assertEquals("Should return list of positive values", listOf(5.0), result)
    }

    @Test
    fun `atLeastOne should pass when all values are positive`() {
        val result = InputGuard.atLeastOne("a" to 1.0, "b" to 2.0)
        assertEquals("Should return all positive values", listOf(1.0, 2.0), result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `atLeastOne should throw when no value is positive`() {
        InputGuard.atLeastOne("a" to -1.0, "b" to 0.0)
    }
}
