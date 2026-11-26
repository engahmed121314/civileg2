package com.civileg.app.domain.calculations.beam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Expected values are hand-evaluated from the printed formulas (see RcDevelopmentLength KDoc). They were not run
 * by the author. ECP Table 4-9 is a rounded design table: the formula is only required to land near it.
 */
class RcDevelopmentLengthTest {

    @Test
    fun ecpEq466_fcu30_fy400_phi16_bottomBar() {
        // fbu = 0.30*sqrt(30/1.5) = 1.34164; Ld/phi = 0.75*(400/1.15)/(4*1.34164) = 48.61; Ld = 777.8 mm
        val r = RcDevelopmentLength.ecpTension(30.0, 400.0, 16.0)
        assertEquals(0.30 * sqrt(20.0), 1.34164, 1e-4)
        assertEquals(48.61 * 16.0, r.formulaLength, 1.0)
        assertEquals(640.0, r.minimum, 1e-9) // 40 phi governs over 300 mm
        assertEquals(r.formulaLength, r.length, 1e-9)
    }

    @Test
    fun ecpTopBarUsesEta130() {
        val bottom = RcDevelopmentLength.ecpTension(30.0, 400.0, 16.0)
        val top = RcDevelopmentLength.ecpTension(30.0, 400.0, 16.0, topBarWithMoreThan300mmBelow = true)
        assertEquals(1.3, top.formulaLength / bottom.formulaLength, 1e-9)
    }

    @Test
    fun ecpFormulaIsCloseToTable49Tension_deformed_fy400() {
        // Table 4-9, tension, high-grade deformed fy 400/420: fcu 20->60, 25->55, 30->50, 35->45, 40->42 (x phi).
        val table = mapOf(20.0 to 60.0, 25.0 to 55.0, 30.0 to 50.0, 35.0 to 45.0, 40.0 to 42.0)
        for ((fcu, mult) in table) {
            val perPhi = RcDevelopmentLength.ecpTension(fcu, 400.0, 1.0).formulaLength
            assertTrue("fcu=$fcu formula ${perPhi}phi vs table ${mult}phi", kotlin.math.abs(perPhi - mult) <= 2.5)
        }
    }

    @Test
    fun ecpMinimumGovernsForShortLengthsAndIsNeverBelow300() {
        val r = RcDevelopmentLength.ecpTension(60.0, 240.0, 10.0)
        assertTrue(r.length >= 400.0 - 1e-9) // 40 phi = 400 mm
        assertTrue(RcDevelopmentLength.ecpTension(60.0, 240.0, 6.0).length >= 300.0)
    }

    @Test
    fun ecpLapIs1p0LdOnlyWhenAreaRatioAndFractionAreKnownAndSatisfied() {
        assertEquals(1000.0, RcDevelopmentLength.ecpTensionLap(1000.0, 2.0, 0.5), 1e-9)
        assertEquals(1300.0, RcDevelopmentLength.ecpTensionLap(1000.0, 1.9, 0.5), 1e-9)
        assertEquals(1300.0, RcDevelopmentLength.ecpTensionLap(1000.0, 2.5, 0.6), 1e-9)
        assertEquals(1300.0, RcDevelopmentLength.ecpTensionLap(1000.0, null, null), 1e-9)
    }

    @Test
    fun sbcTable25422_largeBar_otherCases() {
        // fc' 30, fy 420, db 25, other cases large bar k=1.1, psi_t=psi_e=psi_s=1:
        // ld = 420/(1.1*sqrt(30))*25 = 420/(1.1*5.4772)*25 = 1742.7 mm
        val r = RcDevelopmentLength.aciSbcTension(30.0, 420.0, 25.0, smallBarLimitMm = 20.0)
        assertEquals(1742.7, r.formulaLength, 1.0)
        assertEquals(r.formulaLength, r.length, 1e-9)
    }

    @Test
    fun sbcTable25422_smallBar_metSpacing_usesPsiS08() {
        // fc' 30, fy 420, db 16 (small), spacing+cover met k=2.1, psi_s=0.8:
        // ld = 420*0.8/(2.1*5.4772)*16 = 467.4 mm -> above 300
        val r = RcDevelopmentLength.aciSbcTension(30.0, 420.0, 16.0, 20.0, spacingAndCoverMet = true)
        assertEquals(467.4, r.formulaLength, 1.0)
    }

    @Test
    fun aci19mmLimitTreatsA20mmBarAsLarge_sbcTreatsItAsSmall() {
        val aci = RcDevelopmentLength.aciSbcTension(30.0, 420.0, 20.0, smallBarLimitMm = 19.0)
        val sbc = RcDevelopmentLength.aciSbcTension(30.0, 420.0, 20.0, smallBarLimitMm = 20.0)
        assertTrue(aci.formulaLength > sbc.formulaLength)
    }

    @Test
    fun aciSbcMinimumIs300AndLapIsClassBByDefault() {
        val r = RcDevelopmentLength.aciSbcTension(60.0, 240.0, 6.0, 20.0, spacingAndCoverMet = true)
        assertEquals(300.0, r.length, 1e-9)
        assertEquals(1.3 * 1000.0, RcDevelopmentLength.aciSbcTensionLap(1000.0), 1e-9)
        assertEquals(1000.0, RcDevelopmentLength.aciSbcTensionLap(1000.0, classB = false), 1e-9)
        assertEquals(300.0, RcDevelopmentLength.aciSbcTensionLap(100.0, classB = false), 1e-9)
    }

    @Test
    fun sbcHook_25431() {
        // fc' 30, fy 420, db 16: 0.24*420/5.4772*16 = 294.4 mm; 8db=128; 150 -> 294.4
        assertEquals(294.4, RcDevelopmentLength.sbcHookTension(30.0, 420.0, 16.0), 1.0)
        // small stress: 8 db or 150 governs
        assertEquals(150.0, RcDevelopmentLength.sbcHookTension(60.0, 100.0, 10.0), 1e-9)
    }
}
