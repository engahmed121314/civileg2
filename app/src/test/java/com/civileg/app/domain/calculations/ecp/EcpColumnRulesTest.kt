package com.civileg.app.domain.calculations.ecp

import org.junit.Assert.assertEquals
import org.junit.Test

/** Hand-computed regression values. Clause cites: ECP 203-2025 scan PDF page / printed page. UNTESTED (not compiled in sandbox). */
class EcpColumnRulesTest {
    private val tol = 1e-9

    @Test fun eMin_eq411() { // PDF 91 p.4-11: max(0.05 t, 20)
        assertEquals(30.0, EcpColumnRules.minEccentricity(600.0), tol)  // 0.05*600=30
        assertEquals(20.0, EcpColumnRules.minEccentricity(300.0), tol)  // 15 < 20
        assertEquals(20.0, EcpColumnRules.minEccentricity(400.0), tol)  // 20 = 20
    }

    @Test fun radius_eq632() { // PDF 233 p.6-42
        assertEquals(120.0, EcpColumnRules.radiusRect(400.0), tol)       // 0.30*400
        assertEquals(125.0, EcpColumnRules.radiusCircular(500.0), tol)   // 0.25*500
    }

    @Test fun lambdaILimits_table67() { // PDF 234 p.6-43
        assertEquals(50.0, EcpColumnRules.shortLimitLambdaI(true), tol)
        assertEquals(35.0, EcpColumnRules.shortLimitLambdaI(false), tol)
    }

    @Test fun maxSteel_byPosition() { // PDF 246 p.6-55; 400x400 = 160000 mm2
        assertEquals(6400.0, 160000.0 * EcpColumnRules.maxSteelRatio(EcpColumnRules.Position.MIDDLE), 1e-6)
        assertEquals(8000.0, 160000.0 * EcpColumnRules.maxSteelRatio(EcpColumnRules.Position.EDGE), 1e-6)
        assertEquals(9600.0, 160000.0 * EcpColumnRules.maxSteelRatio(EcpColumnRules.Position.CORNER), 1e-6)
        assertEquals(12800.0, 160000.0 * EcpColumnRules.maxSteelRatio(EcpColumnRules.Position.CORNER, atLap = true), 1e-6)
    }

    @Test fun minSteel_short_slender_general() { // PDF 246 p.6-55
        assertEquals(1280.0, EcpColumnRules.minSteelArea(160000.0, 160000.0, false), 1e-6)            // 0.008*160000
        assertEquals(3728.0, EcpColumnRules.minSteelArea(160000.0, 160000.0, true, lambdaB = 40.0), 1e-6) // (0.25+0.052*40)=2.33%
        assertEquals(1840.0, EcpColumnRules.minSteelArea(160000.0, 160000.0, true, lambdaI = 60.0), 1e-6) // (0.25+0.015*60)=1.15%
        assertEquals(960.0, EcpColumnRules.minSteelArea(80000.0, 160000.0, false), 1e-6)  // 0.008*80000=640 < 0.006*160000=960 floor
    }

    @Test fun ties() { // PDF 246 p.6-55
        assertEquals(200.0, EcpColumnRules.tieMaxSpacing(20.0), tol)  // 15*20=300 -> 200
        assertEquals(180.0, EcpColumnRules.tieMaxSpacing(12.0), tol)  // 15*12=180
        assertEquals(8.0, EcpColumnRules.tieMinDia(25.0), tol)        // 6.25 -> 8
        assertEquals(10.0, EcpColumnRules.tieMinDia(40.0), tol)       // 40/4
    }

    @Test fun alphaN_table612c() { // PDF 245 p.6-54; interpolation between points is an assumption
        assertEquals(1.20, EcpColumnRules.alphaN(0.3), tol)
        assertEquals(1.25, EcpColumnRules.alphaN(0.6), 1e-12)
        assertEquals(1.225, EcpColumnRules.alphaN(0.5), 1e-12)
        assertEquals(1.275, EcpColumnRules.alphaN(0.7), 1e-12)
        assertEquals(1.35, EcpColumnRules.alphaN(0.9), 1e-12)
        assertEquals(1.40, EcpColumnRules.alphaN(1.0), 1e-12)
    }
}

/** CalculatorEngine hunk hand values: 400x400 ECP column, clear height 3000 mm: i = 0.30*400 = 120, lambda_i = 3000/120 = 25 <= 50, so not slender.
 *  Old rule: r = 400/sqrt12 = 115.47, lambda = 25.98 > 15 (was flagged slender). Circular D=500: i = 125, lambda_i = 3000/125 = 24. */
class EcpColumnSlendernessHandValuesTest {
    @Test fun lambdaI_rect_and_circ() {
        assertEquals(25.0, 3000.0 / EcpColumnRules.radiusRect(400.0), 1e-9)
        assertEquals(24.0, 3000.0 / EcpColumnRules.radiusCircular(500.0), 1e-9)
    }
}
