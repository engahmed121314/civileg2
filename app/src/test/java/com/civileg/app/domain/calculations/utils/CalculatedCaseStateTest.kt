package com.civileg.app.domain.calculations.utils

import org.junit.Assert.*
import org.junit.Test

class CalculatedCaseStateTest {
    private data class Input(val span: Double, val code: String, val cover: Double = 25.0)

    @Test fun `only a completed calculation has a snapshot`() {
        val state = CalculatedCaseState<Input, String>()
        assertNull(state.snapshot)
        val ticket = state.begin()
        val input = Input(6000.0, "ECP")
        assertTrue(state.complete(ticket, input, "result"))
        assertEquals(input, state.snapshot!!.input)
        assertEquals("result", state.snapshot!!.result)
    }

    @Test fun `edits invalidate result and reject late completion`() {
        val state = CalculatedCaseState<Input, String>()
        val ticket = state.begin()
        state.invalidate()
        assertFalse(state.complete(ticket, Input(6000.0, "ECP"), "old"))
        assertNull(state.snapshot)
    }

    @Test fun `new calculation wins over previous calculation`() {
        val state = CalculatedCaseState<Input, String>()
        val old = state.begin()
        val current = state.begin()
        assertTrue(state.complete(current, Input(9000.0, "SBC"), "new"))
        assertFalse(state.complete(old, Input(6000.0, "ECP"), "old"))
        assertEquals("new", state.snapshot!!.result)
    }

    @Test fun `code and cover are part of case identity`() {
        val state = CalculatedCaseState<Input, String>()
        val input = Input(6000.0, "ACI")
        state.complete(state.begin(), input, "result")
        assertTrue(state.matches(input))
        assertFalse(state.matches(input.copy(code = "SBC")))
        assertFalse(state.matches(input.copy(cover = 40.0)))
        assertFalse(state.matches(input.copy(span = 9000.0)))
        state.invalidate()
        assertFalse(state.matches(input))
    }
}
