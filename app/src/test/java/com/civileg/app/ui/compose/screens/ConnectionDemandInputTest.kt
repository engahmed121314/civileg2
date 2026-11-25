package com.civileg.app.ui.compose.screens

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionDemandInputTest {
    private fun err(bolt: Boolean = true, tension: Boolean = true, weld: Boolean = false, v: String = "100", t: String = "0", f: String = "100") =
        ConnectionDemandInput.error(bolt, tension, weld, v, t, f)

    @Test fun prefilledValuesAreAccepted() { assertNull(err()); assertNull(err(weld = true)) }
    @Test fun clearedShearIsAnError() = assertNotNull(err(v = ""))
    @Test fun clearedTensionIsAnErrorOnlyWhenShown() {
        assertNotNull(err(t = "", tension = true))
        assertNull(err(t = "", tension = false))
    }
    @Test fun clearedOrZeroWeldForceIsAnError() {
        assertNotNull(err(bolt = false, weld = true, f = ""))
        assertNotNull(err(bolt = false, weld = true, f = "0"))
        assertNotNull(err(bolt = false, weld = true, f = "abc"))
    }
    @Test fun negativeAndZeroBoltLoadAreErrors() {
        assertNotNull(err(v = "-5"))
        assertNotNull(err(v = "0", t = "0"))
    }
    @Test fun commaDecimalIsAccepted() = assertNull(err(v = "12,5"))
    @Test fun inactiveWeldIgnoresItsField() = assertNull(err(weld = false, f = ""))
}
