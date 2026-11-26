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

    @Test fun beamAndColumnGeometryGuards() {
        requireValidBeamGeometry(com.civileg.app.domain.entities.DesignCode.ECP, 250.0, 600.0)
        for ((w, d) in listOf(0.0 to 600.0, 250.0 to 0.0, 250.0 to 50.0)) {
            try { requireValidBeamGeometry(com.civileg.app.domain.entities.DesignCode.ECP, w, d); fail("accepted $w x $d") } catch (_: IllegalArgumentException) {}
        }
        requireValidBeamGeometry(com.civileg.app.domain.entities.DesignCode.ACI, 250.0, 50.0)
        try { requireValidColumnGeometry(0.0, 300.0); fail() } catch (_: IllegalArgumentException) {}
        requireValidColumnGeometry(300.0, 300.0)
    }
}
