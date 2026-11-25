package com.civileg.app.ui.compose.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.civileg.app.ui.compose.screens.ConnectionInputProblem as P

class ConnectionDemandInputTest {
    private fun p(
        bolt: Boolean = true, geom: Boolean = true, tension: Boolean = true, weld: Boolean = false,
        v: String = "100", t: String = "0", f: String = "100",
        d: String = "20", n: String = "4", pt: String = "10", fu: String = "400", ws: String = "6", wl: String = "200"
    ) = ConnectionDemandInput.problem(bolt, geom, tension, weld, v, t, f, d, n, pt, fu, ws, wl)

    @Test fun prefilledValuesAreAccepted() { assertNull(p()); assertNull(p(weld = true)) }
    @Test fun clearedShearIsAnError() = assertEquals(P.BOLT_SHEAR, p(v = ""))
    @Test fun clearedTensionIsAnErrorOnlyWhenShown() {
        assertEquals(P.BOLT_TENSION, p(t = "", tension = true))
        assertNull(p(t = "", tension = false))
    }
    @Test fun weldForceMustBePositive() {
        assertEquals(P.WELD_FORCE, p(bolt = false, weld = true, f = ""))
        assertEquals(P.WELD_FORCE, p(bolt = false, weld = true, f = "0"))
        assertEquals(P.WELD_FORCE, p(bolt = false, weld = true, f = "abc"))
    }
    @Test fun negativeAndZeroBoltLoadAreErrors() {
        assertEquals(P.BOLT_SHEAR, p(v = "-5"))
        assertEquals(P.BOLT_ZERO_LOAD, p(v = "0", t = "0"))
    }
    @Test fun clearedGeometryIsAnErrorWhenShown() {
        assertEquals(P.BOLT_DIAMETER, p(d = ""))
        assertEquals(P.BOLT_COUNT, p(n = ""))
        assertEquals(P.BOLT_COUNT, p(n = "0"))
        assertEquals(P.BOLT_COUNT, p(n = "2.5"))
        assertEquals(P.PLATE_THICKNESS, p(pt = ""))
        assertEquals(P.FU_PLATE, p(fu = ""))
        assertEquals(P.WELD_SIZE, p(bolt = false, weld = true, ws = ""))
        assertEquals(P.WELD_LENGTH, p(bolt = false, weld = true, wl = "0"))
    }
    @Test fun hiddenBoltGeometryIsNotRequired() = assertNull(p(geom = false, d = "", n = "", pt = "", fu = ""))
    @Test fun inactiveWeldIgnoresItsFields() = assertNull(p(weld = false, f = "", ws = "", wl = ""))
    @Test fun commaDecimalIsAccepted() = assertNull(p(v = "12,5", d = "22,5"))

    @Test fun weldOnlyNeedsPlateThickness() {
        assertEquals(P.PLATE_THICKNESS, p(bolt = false, geom = false, weld = true, pt = ""))
        assertNull(p(bolt = false, geom = false, weld = true))
    }
    @Test fun slipCriticalValidatesItsBoltFields() {
        // type 4: bolt active and its geometry card is now shown
        assertEquals(P.BOLT_DIAMETER, p(bolt = true, geom = true, tension = true, d = ""))
        assertEquals(P.FU_PLATE, p(bolt = true, geom = true, tension = true, fu = "0"))
    }
    @Test fun quickToolParsers() {
        assertNull(ConnectionDemandInput.positiveOrNull(""))
        assertNull(ConnectionDemandInput.positiveOrNull("0"))
        assertEquals(6.5, ConnectionDemandInput.positiveOrNull("6,5")!!, 1e-9)
        assertNull(ConnectionDemandInput.countOrNull("0"))
        assertNull(ConnectionDemandInput.countOrNull("2.5"))
        assertEquals(4, ConnectionDemandInput.countOrNull(" 4 "))
    }
}
