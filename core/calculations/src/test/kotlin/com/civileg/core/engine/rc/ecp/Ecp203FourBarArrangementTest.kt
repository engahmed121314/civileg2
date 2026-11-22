package com.civileg.core.engine.rc.ecp

import org.junit.Assert.*
import org.junit.Test

class Ecp203FourBarArrangementTest {
    private fun arrange(width: Double = 200.0, height: Double = 350.0,
        policy: Ecp203FourBarArrangement.Policy = Ecp203FourBarArrangement.Policy.STANDARD_PAIRED_UPPER) =
        Ecp203FourBarArrangement.arrange(width, height, 25.0, 8.0, 20.0, 20.0, policy)

    @Test fun `explicit paired candidate uses three plus two when four do not fit`() {
        assertEquals(listOf(3, 2), arrange().map { it.count })
    }
    @Test fun `explicit economy retains four bars as two plus two`() {
        assertEquals(listOf(2, 2), arrange(policy = Ecp203FourBarArrangement.Policy.EXPLICIT_ECONOMY_FOUR_BARS).map { it.count })
    }
    @Test fun `four fitting bars stay in one row without adding steel`() {
        assertEquals(listOf(4), arrange(width = 236.0).map { it.count })
        assertEquals(listOf(3, 2), arrange(width = 235.999).map { it.count })
    }
    @Test(expected = IllegalArgumentException::class)
    fun `explicit paired candidate fails where the three-bar row does not fit`() { arrange(width = 150.0) }
    @Test(expected = IllegalArgumentException::class)
    fun `no unrequested third layer when vertical spacing fails`() { arrange(height = 130.0) }
    @Test fun `explicit paired choice is not changed to a fitting economic candidate`() {
        assertEquals(listOf(2, 2), arrange(width = 150.0, policy = Ecp203FourBarArrangement.Policy.EXPLICIT_ECONOMY_FOUR_BARS).map { it.count })
        try { arrange(width = 150.0); fail("Must not silently select economy") }
        catch (_: IllegalArgumentException) { }
    }
}
