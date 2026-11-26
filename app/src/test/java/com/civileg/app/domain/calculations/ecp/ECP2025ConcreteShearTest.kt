package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.ecp.ECP2025ConcreteShear.ColumnPosition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Hand values for ECP 203-2025 slab/footing shear (PDF p.105, 107-108; printed 4-25, 4-27, 4-28).
 * fcu=30, gc=1.5 -> sqrt(fcu/gc)=sqrt(20)=4.472136.
 * Only formulas read clearly on the page images are tested; legibility-flagged items are not.
 */
class ECP2025ConcreteShearTest {
    private val root = 4.472136

    @Test fun oneWayIs016Not024() {
        assertEquals(0.16 * root, ECP2025ConcreteShear.oneWayQcu(30.0), 1e-6)   // 0.715542
        assertEquals(0.715542, ECP2025ConcreteShear.oneWayQcu(30.0), 1e-5)
    }

    @Test fun lambdaSCappedAtOne() {
        assertEquals(1.0, ECP2025ConcreteShear.lambdaS(500.0), 1e-9)
        assertEquals(0.840896, ECP2025ConcreteShear.lambdaS(2000.0), 1e-5)  // 0.5^0.25
    }

    @Test fun punchingEq446cGoverns() {
        // d=500, col 400x400 interior: b0=3600; (a)=2.7031, (b)=2.1197, (c)=1.41322 -> (c)
        assertEquals(0.316 * root, ECP2025ConcreteShear.punchingQcup(30.0, 500.0, 3600.0, 400.0, 400.0), 1e-5)
    }

    @Test fun punchingEq446aGovernsForLargeColumnInterior() {
        // d=200, col 1000x1000: b0=4800; (a)=0.8*(4*200/4800+0.2)*root=1.31182 < (c)=1.41322
        assertEquals(1.31182, ECP2025ConcreteShear.punchingQcup(30.0, 200.0, 4800.0, 1000.0, 1000.0), 1e-4)
    }

    @Test fun cornerAlphaTwoIsLower() {
        // alpha=2: 0.8*(2*200/4800+0.2)*root = 1.01369
        assertEquals(1.01369, ECP2025ConcreteShear.punchingQcup(30.0, 200.0, 4800.0, 1000.0, 1000.0, ColumnPosition.CORNER), 1e-4)
        assertEquals(1.16275, ECP2025ConcreteShear.punchingQcup(30.0, 200.0, 4800.0, 1000.0, 1000.0, ColumnPosition.EDGE), 1e-4)
    }

    @Test fun lambdaSReducesDeepSlab() {
        // d=2000, col 400x400: b0=9600; (c)=1.41322*0.840896=1.18838; (a)=3.109 larger
        assertEquals(1.18838, ECP2025ConcreteShear.punchingQcup(30.0, 2000.0, 9600.0, 400.0, 400.0), 1e-4)
    }

    @Test fun capOf170() {
        // fcu=60 -> (c)=0.316*6.324555=1.99857 > 1.70
        assertEquals(1.70, ECP2025ConcreteShear.punchingQcup(60.0, 500.0, 3600.0, 400.0, 400.0), 1e-9)
    }

    @Test fun sideOrderDoesNotMatter() {
        assertEquals(
            ECP2025ConcreteShear.punchingQcup(30.0, 300.0, 4000.0, 300.0, 600.0),
            ECP2025ConcreteShear.punchingQcup(30.0, 300.0, 4000.0, 600.0, 300.0), 1e-12)
    }

    @Test fun invalidGeometryGivesZeroCapacity() {
        assertEquals(0.0, ECP2025ConcreteShear.punchingQcup(30.0, 0.0, 3600.0, 400.0, 400.0), 0.0)
    }
}
