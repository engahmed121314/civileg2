package com.civileg.app.ui.compose.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class AdvancedDesignInputParsingTest {
    @Test fun parsesPlainAndArabicDigits() {
        assertEquals(2.5, "2.5".num("x"), 0.0)
        assertEquals(2.5, "2,5".num("x"), 0.0)
        assertEquals(25.0, "\u0662\u0665".num("x"), 0.0)
        assertEquals(2.5, "\u0662\u066B\u0665".num("x"), 0.0)
        assertEquals(1250.0, "1,250.0".num("x"), 0.0)
    }

    @Test fun rejectsNonFiniteAndGarbage() {
        for (bad in listOf("NaN", "Infinity", "-Infinity", "", "abc", "1,250", "2.5f", "1d", "1e5", "1.250,5", "1,25.0")) {
            try { bad.num("x"); fail("accepted '$bad'") } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun studCountBlankOrZeroIsNotProvidedButNegativeIsRejected() {
        assertNull("".intOrNull("studs"))
        assertNull("0".intOrNull("studs"))
        assertEquals(12, "12".intOrNull("studs"))
        try { "-3".intOrNull("studs"); fail("accepted negative") } catch (_: IllegalArgumentException) {}
    }
}
