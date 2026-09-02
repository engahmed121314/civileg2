package com.civileg.app.utils.detailing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** §26 gate — bend/hook/cut/mass mathematics vs verified IS 2502 practice. */
class BarBendingMathTest {

    @Test
    fun `stirrup cut length matches hand calculation`() {
        // 250x500 outer, cover 40, Ø8, seismic hooks:
        // a=250-80-8=162 ; b=500-80-8=412 ; perim=2(162+412)=1148
        // corners: 3×2×8=48 ; hooks: 2×12×8=192 → CL = 1148-48+192 = 1292
        assertEquals(1292.0, BarBendingEngine.stirrupCutLength(250.0, 500.0, 40.0, 8), 0.5)
    }

    @Test
    fun `straight and L and U bars`() {
        assertEquals(4000.0, BarBendingEngine.straightCutLength(4000.0), 0.01)
        // L-bar 300+400 −2d(16) = 668
        assertEquals(668.0, BarBendingEngine.lBarCutLength(300.0, 400.0, 16), 0.5)
        // U-bar legs 350 ×2 + base 250 − 2×2d(12)=48 → 902
        assertEquals(902.0, BarBendingEngine.uBarCutLength(250.0, 350.0, 12), 0.5)
    }

    @Test
    fun `unit mass follows d2 over 162`() {
        assertEquals(1.580, BarBendingEngine.unitMassKgPerM(16), 0.005)   // 16²/162 = 1.5802
        assertEquals(0.395, BarBendingEngine.unitMassKgPerM(8), 0.003)    // 8²/162 = 0.3951
    }

    @Test
    fun `tie count is inclusive of both ends`() {
        assertEquals(21, BarBendingEngine.tieCount(0.0, 2000.0, 100.0))
    }

    @Test
    fun `registry rejects duplicate marks`() {
        val r = BarMarkRegistry()
        val m1 = r.next("B", "bottom")
        r.register("X9", "manual")
        try {
            r.register("X9", "again")
            throw AssertionError("duplicate mark accepted")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Duplicate"))
        }
        assertTrue(r.allMarks.contains(m1) && r.allMarks.contains("X9"))
        assertTrue(r.describe(m1) == "bottom")
    }

    @Test
    fun `code lengths follow ECP formula`() {
        // Ld = 0.25*400*16/(sqrt(25)*1.25) = 1600/6.25 = 256 mm ; lap = 1.3Ld = 332.8
        val ld = CodeLengths.development(fyMPa = 400.0, barDiaMm = 16.0, fcuMPa = 25.0)
        assertEquals(256.0, ld.value, 0.1)
        assertEquals(332.8, CodeLengths.tensionLap(ld).value, 0.1)
        assertEquals(96.0, CodeLengths.stirrupHook135(8).value, 0.01) // 12Ø = 96
    }
}
